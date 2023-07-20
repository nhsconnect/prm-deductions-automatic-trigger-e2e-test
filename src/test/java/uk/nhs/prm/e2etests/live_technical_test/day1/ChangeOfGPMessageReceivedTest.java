package uk.nhs.prm.e2etests.live_technical_test.day1;

import uk.nhs.prm.e2etests.configuration.Gp2gpMessengerPropertySource;
import uk.nhs.prm.e2etests.configuration.NhsPropertySource;
import uk.nhs.prm.e2etests.configuration.PdsAdaptorPropertySource;
import uk.nhs.prm.e2etests.configuration.QueuePropertySource;
import uk.nhs.prm.e2etests.performance.awsauth.AssumeRoleCredentialsProviderFactory;
import uk.nhs.prm.e2etests.performance.awsauth.AutoRefreshingRoleAssumingSqsClient;
import uk.nhs.prm.e2etests.live_technical_test.helpers.TestPatientValidator;
import uk.nhs.prm.e2etests.services.gp2gp_messenger.Gp2GpMessengerClient;
import uk.nhs.prm.e2etests.suspensions.SuspensionMessageRealQueue;
import uk.nhs.prm.e2etests.live_technical_test.TestParameters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.nhs.prm.e2etests.pdsadaptor.PdsAdaptorResponse;
import uk.nhs.prm.e2etests.queue.ThinlyWrappedSqsClient;
import uk.nhs.prm.e2etests.pdsadaptor.PdsAdaptorClient;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;


@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChangeOfGPMessageReceivedTest {
    private SuspensionMessageRealQueue suspensionMessageRealQueue;
    private TestPatientValidator patientValidator;
    private Gp2GpMessengerClient gp2GpMessengerClient;

    @Autowired
    public ChangeOfGPMessageReceivedTest(
            TestPatientValidator testPatientValidator,
            Gp2gpMessengerPropertySource gp2gpMessengerPropertySource,
            QueuePropertySource queuePropertySource,
            NhsPropertySource nhsPropertySource,
            PdsAdaptorPropertySource pdsAdaptorPropertySource
    ) {
        patientValidator = testPatientValidator;
        this.gp2gpMessengerPropertySource = gp2gpMessengerPropertySource;
        this.queuePropertySource = queuePropertySource;
        this.nhsPropertySource = nhsPropertySource;
        this.pdsAdaptorPropertySource = pdsAdaptorPropertySource;
    }

    @BeforeEach
    public void setUp() {
        var sqsClient = new AutoRefreshingRoleAssumingSqsClient(new AssumeRoleCredentialsProviderFactory());
        suspensionMessageRealQueue = new SuspensionMessageRealQueue(new ThinlyWrappedSqsClient(sqsClient), config);
        gp2GpMessengerClient = new Gp2GpMessengerClient(config.getGp2GpMessengerApiKey(), config.getGp2GpMessengerUrl());
    }

    @Test
    void shouldHaveReceivedSingleSuspensionChangeOfGpMessageRelatedToTestPatient() {
        var safeListPatients = nhsPropertySource.getSafeListedPatientList();

        if (safeListPatients.size() > 0) {

            System.out.println("Safe list patient has size " + safeListPatients.size());

            safeListPatients.forEach(nhsNumber -> {
                System.out.println("Checking if nhs number is synthetic");
                assertThat(patientValidator.isIncludedInTheTest(nhsNumber)).isTrue();

                var pdsResponse = getPatientStatusOnPDSForSyntheticPatient(nhsNumber);

                System.out.println("Patient suspended status is:" + pdsResponse.getIsSuspended());

                System.out.println("Checking patient status on HL7 PDs lookup - see gp2gp messenger logs for insights");
                gp2GpMessengerClient.getPdsRecordViaHlv7(nhsNumber);

                System.out.println("Finding related message for nhs number");

                var suspensionMessage = suspensionMessageRealQueue.getMessageContainingForTechnicalTestRun(nhsNumber);

                System.out.println("got message related to test patient");
                TestParameters.outputTestParameter("live_technical_test_nems_message_id", suspensionMessage.nemsMessageId());
            });


        } else {
            System.out.println("No safe list patients for test");
        }


    }

    private boolean isSafeListedOrSynthetic(String testPatientNhsNumber) {
        return patientValidator.isIncludedInTheTest(testPatientNhsNumber, config.getSafeListedPatientList(), config.getSyntheticPatientPrefix());
    }

    private PdsAdaptorResponse getPatientStatusOnPDSForSyntheticPatient(String testPatientNhsNumber) {
        String pdsAdaptorUsernameXXX = "live-test"; // TODO PRMT-3488 'XXX'? could do with a rename
        return fetchPdsPatientStatus(pdsAdaptorUsernameXXX, testPatientNhsNumber);
    }

    private PdsAdaptorResponse fetchPdsPatientStatus(String pdsAdaptorUsername, String testPatientNhsNumber) {

        var pds = new PdsAdaptorClient(pdsAdaptorUsername, config.getPdsAdaptorLiveTestApiKey(), config.getPdsAdaptorUrl());

        return pds.getSuspendedPatientStatus(testPatientNhsNumber);
    }
}
