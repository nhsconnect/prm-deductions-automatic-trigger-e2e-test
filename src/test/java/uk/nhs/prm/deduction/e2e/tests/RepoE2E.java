package uk.nhs.prm.deduction.e2e.tests;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.ehr_transfer.LargeEhrQueue;
import uk.nhs.prm.deduction.e2e.ehr_transfer.RepoIncomingQueue;
import uk.nhs.prm.deduction.e2e.ehr_transfer.SmallEhrQueue;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AssumeRoleCredentialsProviderFactory;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AutoRefreshingRoleAssumingSqsClient;
import uk.nhs.prm.deduction.e2e.queue.ActiveMqClient;
import uk.nhs.prm.deduction.e2e.queue.BasicSqsClient;
import uk.nhs.prm.deduction.e2e.queue.SqsQueue;
import uk.nhs.prm.deduction.e2e.transfer_tracker_db.DbClient;
import uk.nhs.prm.deduction.e2e.transfer_tracker_db.TrackerDb;
import uk.nhs.prm.deduction.e2e.utility.Resources;

import javax.jms.JMSException;
import java.util.UUID;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;


@SpringBootTest(classes = {
        RepoE2E.class,
        RepoIncomingQueue.class,
        TestConfiguration.class,
        SqsQueue.class, BasicSqsClient.class,
        AssumeRoleCredentialsProviderFactory.class,
        AutoRefreshingRoleAssumingSqsClient.class,
        Resources.class,
        ActiveMqClient.class,
        TrackerDb.class,
        SmallEhrQueue.class,
        LargeEhrQueue.class,
        DbClient.class
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RepoE2E {

    @Autowired
    RepoIncomingQueue repoIncomingQueue;

    @Autowired
    ActiveMqClient mqClient;

    @Autowired
    TrackerDb trackerDb;
    @Autowired
    SmallEhrQueue smallEhrQueue;
    @Autowired
    LargeEhrQueue largeEhrQueue;


    @Test
    void shouldTestThatMessagesAreReadCorrectlyFromRepoIncomingQueueAndAnEhrRequestIsMadeAndTheDbIsUpdatedWithExpectedStatus() {  //this test would expand and change as progress
        String nhsNumber = "9693795989";
        String nemsMessageId = UUID.randomUUID().toString();
        String conversationId = UUID.randomUUID().toString();
        String message = "{\"nhsNumber\":\"" + nhsNumber + "\",\"nemsMessageId\":\"" + nemsMessageId + "\", \"sourceGp\":\"N82668\",\"destinationGp\":\"B85002\",\"conversationId\":\"" + conversationId + "\"}";
        repoIncomingQueue.postAMessage(message);
        assertTrue(trackerDb.conversationIdExists(conversationId));
        assertTrue(trackerDb.statusForConversationIdIs(conversationId, "ACTION:EHR_REQUEST_SENT"));
    }

    @Test
    void shouldPutSmallEhrOnActiveMQAndObserveItOnSmallEhrObservabilityQueue() throws JMSException {  //this test would expand and change as progress
        String conversationId = UUID.randomUUID().toString();
        System.out.println("conversation Id " + conversationId);
        String message = Resources.readTestResourceFileFromEhrDirectory("unsanitized_small_ehr").replaceAll("__conversationId__", conversationId);
        mqClient.postAMessageToAQueue("inbound", message);
        assertThat(smallEhrQueue.getMessageContaining(conversationId));
    }

    @Test
    void shouldPutLargeEhrOnActiveMQAndObserveItOnLargeEhrObservabilityQueue() throws JMSException {  //this test would expand and change as progress
        String conversationId = UUID.randomUUID().toString();
        System.out.println("conversation Id " + conversationId);
        String message = Resources.readTestResourceFileFromEhrDirectory("unsanitized_large_ehr").replaceAll("__conversationId__", conversationId);
        mqClient.postAMessageToAQueue("inbound", message);
        assertThat(largeEhrQueue.getMessageContainingAttribute("conversationId",conversationId));
    }
}
