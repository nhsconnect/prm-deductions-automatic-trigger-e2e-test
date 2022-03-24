package uk.nhs.prm.deduction.e2e.live.day1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AssumeRoleCredentialsProviderFactory;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AutoRefreshingRoleAssumingSqsClient;
import uk.nhs.prm.deduction.e2e.queue.SqsQueue;
import uk.nhs.prm.deduction.e2e.suspensions.SuspensionMessageObservabilityQueue;
import uk.nhs.prm.deduction.e2e.utility.Files;

import static org.assertj.core.api.Assertions.assertThat;


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ChangeOfGPMessageReceivedTest {

    private SuspensionMessageObservabilityQueue suspensionMessageObservabilityQueue;

    @BeforeEach
    public void setUp() {
        var sqsClient = new AutoRefreshingRoleAssumingSqsClient(new AssumeRoleCredentialsProviderFactory());
        suspensionMessageObservabilityQueue = new SuspensionMessageObservabilityQueue(new SqsQueue(sqsClient), new TestConfiguration());
    }

    @Test
    public void shouldHaveReceivedSingleSuspensionChangeOfGpMessageRelatedToTestPatient() {
        var testPatientNhsNumber = System.getenv("LIVE_TECHNICAL_TEST_NHS_NUMBER");
        var testPatientPreviousGp = System.getenv("LIVE_TECHNICAL_TEST_PREVIOUS_GP");
        System.out.println("expecting test nhs number and previous gp of: " + testPatientNhsNumber + ", " + testPatientPreviousGp);

        var suspensionMessage = suspensionMessageObservabilityQueue.getMessageContaining(testPatientNhsNumber);

        System.out.println("got message related to test patient");
        Files.outputTestData("live_technical_test_nems_message_id", suspensionMessage.nemsMessageId());

        assertThat(suspensionMessage.previousGp()).isEqualTo(testPatientPreviousGp);
    }

}
