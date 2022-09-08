package uk.nhs.prm.deduction.e2e.performance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.TestData;
import uk.nhs.prm.deduction.e2e.ehr_transfer.RepoIncomingQueue;
import uk.nhs.prm.deduction.e2e.ehr_transfer.TransferCompleteQueue;
import uk.nhs.prm.deduction.e2e.models.Gp2GpSystem;
import uk.nhs.prm.deduction.e2e.models.RepoIncomingMessage;
import uk.nhs.prm.deduction.e2e.models.RepoIncomingMessageBuilder;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AssumeRoleCredentialsProviderFactory;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AutoRefreshingRoleAssumingSqsClient;
import uk.nhs.prm.deduction.e2e.queue.SqsMessage;
import uk.nhs.prm.deduction.e2e.queue.SqsQueue;
import uk.nhs.prm.deduction.e2e.queue.activemq.ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient;
import uk.nhs.prm.deduction.e2e.queue.activemq.SimpleAmqpQueue;
import uk.nhs.prm.deduction.e2e.transfer_tracker_db.TransferTrackerDbClient;
import uk.nhs.prm.deduction.e2e.transfer_tracker_db.TrackerDb;
import uk.nhs.prm.deduction.e2e.utility.Resources;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import static java.time.LocalDateTime.now;
import static java.util.UUID.randomUUID;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {
        AutoRefreshingRoleAssumingSqsClient.class,
        AssumeRoleCredentialsProviderFactory.class,
        TransferTrackerDbClient.class,
        RepoInPerformanceTest.class,
        RepoIncomingQueue.class,
        SqsQueue.class,
        TestConfiguration.class,
        TrackerDb.class,
        TransferCompleteQueue.class,
})
@ExtendWith(ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class RepoInPerformanceTest {
    @Autowired
    RepoIncomingQueue repoIncomingQueue;

    @Autowired
    TestConfiguration config;

    @Autowired
    TrackerDb trackerDb;

    @Autowired
    TransferCompleteQueue transferCompleteQueue;

    @BeforeAll
    void init() {
        transferCompleteQueue.deleteAllMessages();
    }

    @Test
    public void trackBehaviourOfHighNumberOfMessagesSentToEhrTransferService() {
        var numberOfRecordToBeProcessed = 1000;
        var messagesToBeProcessed = new ArrayList<RepoIncomingMessage>();

        for (int i = 0; i < numberOfRecordToBeProcessed ; i++) {
            var message = new RepoIncomingMessageBuilder()
                    .withNhsNumber(TestData.generateRandomNhsNumber())
                    .withEhrSourceGp(Gp2GpSystem.EMIS_PTL_INT)
                    .build();
            messagesToBeProcessed.add(message);
        }

        messagesToBeProcessed.forEach(message -> repoIncomingQueue.send(message));

        // TODO: to be fixed in perf env (ok in dev)
//        System.out.println("Ensuring records are stored in tracker db...");
//        messagesToBeProcessed.forEach(message ->
//            assertTrue(trackerDb.conversationIdExists(message.conversationId()))
//        );

        System.out.println("DB setup completed. About to send messages to mq...");
        var inboundQueueFromMhs = new SimpleAmqpQueue(config);

        messagesToBeProcessed.forEach(message -> {
            var conversationId = message.conversationId();
            var smallEhr = getSmallMessageWithUniqueConversationIdAndMessageId(conversationId);
            inboundQueueFromMhs.sendMessage(smallEhr, conversationId);
        });
        inboundQueueFromMhs.close();

        var timeout = now().plusMinutes(10);
        while (now().isBefore(timeout) && messagesToBeProcessed.size() > 0) {
            for (SqsMessage nextMessage : transferCompleteQueue.getNextMessages(timeout)) {
                var conversationId = nextMessage.attributes().get("conversationId").stringValue();
                messagesToBeProcessed.removeIf(message -> {
                    if (message.conversationId().equals(conversationId)) {
                        System.out.println("Found in transfer complete queue message with conversationId " + conversationId);
                        return true;
                    }
                    return false;
                });
            }
        }

        assertTrue(messagesToBeProcessed.isEmpty());
    }

    private String getSmallMessageWithUniqueConversationIdAndMessageId(String conversationId) {
        var messageId = randomUUID().toString();
        var message = Resources.readTestResourceFileFromEhrDirectory("small-ehr");
        message = message.replaceAll("__CONVERSATION_ID__", conversationId);
        message = message.replaceAll("__MESSAGE_ID__", messageId);
        return message;
    }
}
