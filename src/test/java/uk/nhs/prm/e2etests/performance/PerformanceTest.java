package uk.nhs.prm.e2etests.performance;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.test.context.TestPropertySource;
import uk.nhs.prm.e2etests.configuration.TestConfiguration;
import uk.nhs.prm.e2etests.configuration.ResourceConfiguration;
import uk.nhs.prm.e2etests.model.NhsNumberTestData;
import uk.nhs.prm.e2etests.model.nems.NemsEventMessage;
import uk.nhs.prm.e2etests.model.response.PdsAdaptorResponse;
import uk.nhs.prm.e2etests.property.NhsProperties;
import uk.nhs.prm.e2etests.mesh.MeshMailbox;
import uk.nhs.prm.e2etests.service.PdsAdaptorService;
import uk.nhs.prm.e2etests.performance.load.*;
import uk.nhs.prm.e2etests.tests.ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient;
import uk.nhs.prm.e2etests.model.SqsMessage;
import uk.nhs.prm.e2etests.queue.suspensions.SuspensionServiceMofUpdatedQueue;

import java.time.LocalDateTime;
import java.util.List;

import static java.lang.System.out;
import static java.time.LocalDateTime.now;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.nhs.prm.e2etests.utility.NhsIdentityGenerator.randomNemsMessageId;
import static uk.nhs.prm.e2etests.utility.NhsIdentityGenerator.randomNhsNumber;
import static uk.nhs.prm.e2etests.performance.NemsTestEvent.nonSuspensionEvent;
import static uk.nhs.prm.e2etests.performance.load.LoadPhase.atFlatRate;
import static uk.nhs.prm.e2etests.performance.reporting.PerformanceChartGenerator.generateProcessingDurationScatterPlot;
import static uk.nhs.prm.e2etests.performance.reporting.PerformanceChartGenerator.generateThroughputPlot;

@Log4j2
@SpringBootTest
@ExtendWith(ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient.class)
@TestPropertySource(properties = {"test.pds.username=performance-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnableScheduling
public class PerformanceTest {
    // CONSTANTS
    public static final int TOTAL_MESSAGES_PER_DAY = 17000;
    public static final int SUSPENSION_MESSAGES_PER_DAY = 4600;
    public static final int NON_SUSPENSION_MESSAGES_PER_DAY = TOTAL_MESSAGES_PER_DAY - SUSPENSION_MESSAGES_PER_DAY;
    public static final int THROUGHPUT_BUCKET_SECONDS = 60;

    // BEANS
    private final MeshMailbox meshMailbox;
    private final TestConfiguration testConfiguration;
    private final NhsNumberTestData nhsNumbers;
    private final SuspensionServiceMofUpdatedQueue suspensionServiceMofUpdatedQueue;
    private final NhsProperties nhsProperties;

    private final PdsAdaptorService pdsAdaptorService;

    @Autowired
    public PerformanceTest(
            MeshMailbox meshMailbox,
            TestConfiguration testConfiguration,
            ResourceConfiguration resourceConfiguration,
            SuspensionServiceMofUpdatedQueue suspensionServiceMofUpdatedQueue,
            NhsProperties nhsProperties,
            PdsAdaptorService pdsAdaptorService
    ) {
        this.meshMailbox = meshMailbox;
        this.testConfiguration = testConfiguration;
        this.nhsNumbers = resourceConfiguration.nhsNumbers();
        this.suspensionServiceMofUpdatedQueue = suspensionServiceMofUpdatedQueue;
        this.nhsProperties = nhsProperties;
        this.pdsAdaptorService = pdsAdaptorService;
    }

    @Disabled("only used for perf test development not wanted on actual runs")
    @Test
    void shouldMoveSingleSuspensionMessageFromNemsToMofUpdatedQueue() {
        RoundRobinPool<String> nhsNumberPool = new RoundRobinPool<>(nhsNumbers.getNhsNumbers());
        SuspensionCreatorPool suspensions = new SuspensionCreatorPool(nhsNumberPool);

        NemsTestEvent nemsEvent = injectSingleNemsSuspension(new DoNothingTestEventListener(), suspensions.next());

        log.info("Attempting to find message containing: {}.", nemsEvent.nemsMessageId());

        SqsMessage successMessage = suspensionServiceMofUpdatedQueue.getMessageContaining(nemsEvent.nemsMessageId());

        assertThat(successMessage).isNotNull();

        nemsEvent.finished(successMessage);
    }

    @Test
    void testAllSuspensionMessagesAreProcessedWhenLoadedWithProfileOfRatesAndInjectedMessageCounts() {
        final int overallTimeout = testConfiguration.getPerformanceTestTimeout();
        final PerformanceTestRecorder recorder = new PerformanceTestRecorder();

        MixerPool<NemsTestEvent> eventSource = createMixedSuspensionsAndNonSuspensionsTestEventSource(SUSPENSION_MESSAGES_PER_DAY, NON_SUSPENSION_MESSAGES_PER_DAY);
        LoadRegulatingPool<NemsTestEvent> loadSource = new LoadRegulatingPool<>(eventSource, testConfiguration.performanceTestLoadPhases(List.<LoadPhase>of(
                atFlatRate(10, "1"),
                atFlatRate(10, "2"))));

        SuspensionsOnlyEventListener suspensionsOnlyRecorder = new SuspensionsOnlyEventListener(recorder);
        while (loadSource.unfinished()) {
            injectSingleNemsSuspension(suspensionsOnlyRecorder, loadSource.next());
        }

        loadSource.summariseTo(out);

        log.info("Checking the MOF updated message queue.");

        try {
            final LocalDateTime timeout = now().plusSeconds(overallTimeout);
            while (before(timeout) && recorder.hasUnfinishedEvents()) {
                for (SqsMessage nextMessage : suspensionServiceMofUpdatedQueue.getNextMessages(timeout)) {
                    recorder.finishMatchingMessage(nextMessage);
                }
            }
        }
        finally {
            recorder.summariseTo(out);

            generateProcessingDurationScatterPlot(recorder, "Suspension event processing durations vs start time (non-suspensions not shown)");
            generateThroughputPlot(recorder, THROUGHPUT_BUCKET_SECONDS, "Suspension event mean throughput per second in " + THROUGHPUT_BUCKET_SECONDS + " second buckets");
        }

        assertFalse(recorder.hasUnfinishedEvents());
    }

    private NemsTestEvent injectSingleNemsSuspension(NemsTestEventListener listener, NemsTestEvent testEvent) {
        NemsEventMessage nemsSuspension = testEvent.createMessage();

        listener.onStartingTestItem(testEvent);

        String meshMessageId = meshMailbox.postMessage(nemsSuspension);

        testEvent.started(meshMessageId);

        listener.onStartedTestItem(testEvent);

        return testEvent;
    }

    private MixerPool<NemsTestEvent> createMixedSuspensionsAndNonSuspensionsTestEventSource(int suspensionMessagesPerDay, int nonSuspensionMessagesPerDay) {
        SuspensionCreatorPool suspensionsSource = new SuspensionCreatorPool(suspendedNhsNumbers());
        NemsTestEventPool nonSuspensionsSource = new NemsTestEventPool(nonSuspensionEvent(randomNhsNumber(), randomNemsMessageId()));
        return new MixerPool<>(
                suspensionMessagesPerDay, suspensionsSource,
                nonSuspensionMessagesPerDay, nonSuspensionsSource);
    }

    private RoundRobinPool<String> suspendedNhsNumbers() {
        List<String> suspendedNhsNumbers = nhsNumbers.getNhsNumbers();
        checkSuspended(suspendedNhsNumbers);
        return new RoundRobinPool(suspendedNhsNumbers);
    }

    private void checkSuspended(List<String> suspendedNhsNumbers) {
        if (!nhsProperties.getNhsEnvironment().equals("perf")) {
            for (String nhsNumber: suspendedNhsNumbers) {
                PdsAdaptorResponse patientStatus = pdsAdaptorService.getSuspendedPatientStatus(nhsNumber);
                log.info("{}:{}", nhsNumber, patientStatus);
                assertTrue(patientStatus.getIsSuspended());
            }
        }
    }

    private boolean before(LocalDateTime timeout) {
        return now().isBefore(timeout);
    }
}
