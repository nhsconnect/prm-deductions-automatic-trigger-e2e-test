package uk.nhs.prm.e2etests.performance;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import uk.nhs.prm.e2etests.TestConfiguration;
import uk.nhs.prm.e2etests.TestData;
import uk.nhs.prm.e2etests.configuration.QueuePropertySource;
import uk.nhs.prm.e2etests.ehr_transfer.RepoIncomingQueue;
import uk.nhs.prm.e2etests.ehr_transfer.TransferCompleteQueue;
import uk.nhs.prm.e2etests.model.Gp2GpSystem;
import uk.nhs.prm.e2etests.model.RepoIncomingMessageBuilder;
import uk.nhs.prm.e2etests.performance.awsauth.AssumeRoleCredentialsProviderFactory;
import uk.nhs.prm.e2etests.performance.awsauth.AutoRefreshingRoleAssumingSqsClient;
import uk.nhs.prm.e2etests.performance.reporting.RepoInPerformanceChartGenerator;
import uk.nhs.prm.e2etests.queue.ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient;
import uk.nhs.prm.e2etests.queue.SimpleAmqpQueue;
import uk.nhs.prm.e2etests.queue.ThinlyWrappedSqsClient;
import uk.nhs.prm.e2etests.queue.SqsMessage;
import uk.nhs.prm.e2etests.timing.Sleeper;
import uk.nhs.prm.e2etests.transfer_tracker_db.TrackerDb;
import uk.nhs.prm.e2etests.transfer_tracker_db.TransferTrackerDbClient;
import uk.nhs.prm.e2etests.utility.Resources;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;
import static java.lang.System.getenv;
import static java.lang.System.out;
import static java.time.LocalDateTime.now;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = {
        AutoRefreshingRoleAssumingSqsClient.class,
        AssumeRoleCredentialsProviderFactory.class,
        TransferTrackerDbClient.class,
        RepoInPerformanceTest.class,
        RepoIncomingQueue.class,
        Sleeper.class,
        ThinlyWrappedSqsClient.class,
        TestConfiguration.class,
        TrackerDb.class,
        TransferCompleteQueue.class,
})
@ExtendWith(ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepoInPerformanceTest {
    @Autowired
    RepoIncomingQueue repoIncomingQueue;

    @Autowired
    Sleeper sleeper;

    @Autowired
    TestConfiguration config;

    @Autowired
    TrackerDb trackerDb;

    @Autowired
    TransferCompleteQueue transferCompleteQueue;

    @Autowired
    ApplicationContext context;

    @Autowired
    QueuePropertySource queuePropertySource;

    @BeforeAll
    void init() {
        transferCompleteQueue = new TransferCompleteQueue(new ThinlyWrappedSqsClient(appropriateAuthenticationSqsClient()), queuePropertySource);
        transferCompleteQueue.deleteAllMessages();
    }

    @Test
    public void trackBehaviourOfHighNumberOfMessagesSentToEhrTransferService() {
        System.out.println("BEGINNING PROBLEMATIC TEST");
        var numberOfMessagesToBeProcessed = getNumberOfMessagesToBeProcessed();
        var messagesToBeProcessed = setupMessagesToBeProcessed(numberOfMessagesToBeProcessed);

        System.out.println("Setup completed. About to send messages to mq...");
        sendMessagesToMq(messagesToBeProcessed);

        System.out.println("All messages sent. Ensuring they reached transfer complete queue...");
        assertMessagesAreInTransferCompleteQueue(numberOfMessagesToBeProcessed, messagesToBeProcessed);
    }

    private void assertMessagesAreInTransferCompleteQueue(int numberOfMessagesToBeProcessed, List<RepoInPerfMessageWrapper> messagesToBeProcessed) {
        var messagesReadFromQueueEveryMinute = 100;
        var additionalMinutesBuffer = 5;
        var timeoutInMinutes = Math.round(numberOfMessagesToBeProcessed / messagesReadFromQueueEveryMinute) + additionalMinutesBuffer;
        System.out.println("Polling messages from transfer complete queue, timeout for this operation set to " + timeoutInMinutes + " minutes.");

        var messagesProcessed = new ArrayList<RepoInPerfMessageWrapper>();
        var timeout = now().plusMinutes(timeoutInMinutes);
        while (now().isBefore(timeout) && messagesToBeProcessed.size() > 0) {
            for (SqsMessage sqsMessage : transferCompleteQueue.getNextMessages(timeout)) {
                var conversationId = sqsMessage.attributes().get("conversationId").stringValue();
                messagesToBeProcessed.removeIf(message -> {
                    if (message.getMessage().conversationId().equals(conversationId)) {
                        message.finish(sqsMessage.queuedAt());
                        transferCompleteQueue.deleteMessage(sqsMessage);
                        System.out.println("Found in transfer complete queue message with conversationId "
                                + conversationId
                                + " which took "
                                + message.getProcessingTimeInSeconds()
                                + " seconds to be processed");
                        messagesProcessed.add(message);
                        return true;
                    }
                    return false;
                });
                var numberOfMessagesProcessed = numberOfMessagesToBeProcessed - messagesToBeProcessed.size();
                System.out.println("Processed " + numberOfMessagesProcessed + " messages out of " + numberOfMessagesToBeProcessed);
            }
        }

        RepoInPerformanceChartGenerator.generateThroughputPlot(
                messagesProcessed.stream()
                        .sorted(Comparator.comparing(RepoInPerfMessageWrapper::getFinishedAt))
                        .collect(Collectors.toList()));

        assertTrue(messagesToBeProcessed.isEmpty());
    }

    private void sendMessagesToMq(List<RepoInPerfMessageWrapper> messagesToBeProcessed) {
        var intervalBetweenMessagesSentToMq = getIntervalBetweenMessagesSentToMq();
        try {
            var inboundQueueFromMhs = new SimpleAmqpQueue(queuePropertySource, config);
            var messageTemplate = Resources.readTestResourceFileFromEhrDirectory("small-ehr-4MB");
            var counter = new AtomicInteger(0);
            String smallEhr;

            for (int i = 0; i < messagesToBeProcessed.size(); i++) {
                counter.updateAndGet(v -> v + 1);
                String conversationId = messagesToBeProcessed.get(i).getMessage().conversationId();

                smallEhr = getSmallMessageWithUniqueConversationIdAndMessageId(messageTemplate, conversationId);
                messagesToBeProcessed.get(i).start();

                System.out.println("Item " + counter.get() + " - sending to mq conversationId " + conversationId);
                inboundQueueFromMhs.sendMessage(smallEhr, conversationId);

                sleeper.sleep(intervalBetweenMessagesSentToMq);
            }
//            messagesToBeProcessed.forEach(message -> {
//                counter.updateAndGet(v -> v + 1);
//                String conversationId = message.getMessage().conversationId();
//
//                smallEhr = getSmallMessageWithUniqueConversationIdAndMessageId(messageTemplate, conversationId);
//                message.start();
//
//                System.out.println("Item " + counter.get() + " - sending to mq conversationId " + conversationId);
//                inboundQueueFromMhs.sendMessage(smallEhr, conversationId);
//
//                sleeper.sleep(intervalBetweenMessagesSentToMq);
//            });
//            for (RepoInPerfMessageWrapper message : messagesToBeProcessed) {
//                counter.updateAndGet(v -> v + 1);
//                String conversationId = message.getMessage().conversationId();
//
//                String smallEhr = getSmallMessageWithUniqueConversationIdAndMessageId(messageTemplate, conversationId);
//                message.start();
//
//                System.out.println("Item " + counter.get() + " - sending to mq conversationId " + conversationId);
//                inboundQueueFromMhs.sendMessage(smallEhr, conversationId);
//
//                sleeper.sleep(intervalBetweenMessagesSentToMq);
//            }

//            messagesToBeProcessed.forEach(message -> processMessage(message, messageTemplate, counter, inboundQueueFromMhs));

            System.out.println("All messages sent, about to close mhs producer...");
            inboundQueueFromMhs.close();
        } catch (OutOfMemoryError outOfMemoryError) {
            System.out.println("Whoops, mq client went out of memory again!");
            System.exit(1);
        }
    }

    private synchronized void processMessage(
            RepoInPerfMessageWrapper message,
            String messageTemplate,
            AtomicInteger counter,
            SimpleAmqpQueue inboundQueueFromMhs
    ) {
        counter.incrementAndGet();
        var conversationId = message.getMessage().conversationId();

        var smallEhr = getSmallMessageWithUniqueConversationIdAndMessageId(messageTemplate, conversationId);
        message.start();

        System.out.println("Item " + counter.get() + " - sending to mq conversationId " + conversationId);
        inboundQueueFromMhs.sendMessage(smallEhr, conversationId);


        sleeper.sleep(100);
        message.finish(LocalDateTime.now());
    }

    private List<RepoInPerfMessageWrapper> setupMessagesToBeProcessed(int numberOfMessagesToBeProcessed) {
        var messagesToBeProcessed = new ArrayList<RepoInPerfMessageWrapper>();

        for (int i = 0; i < numberOfMessagesToBeProcessed ; i++) {
            var message = new RepoIncomingMessageBuilder()
                    .withNhsNumber(TestData.generateRandomNhsNumber())
                    .withEhrSourceGp(Gp2GpSystem.EMIS_PTL_INT)
                    .build();
            messagesToBeProcessed.add(new RepoInPerfMessageWrapper(message));
        }

        messagesToBeProcessed.forEach(message -> repoIncomingQueue.send(message.getMessage()));
        return messagesToBeProcessed;
    }

    private int getNumberOfMessagesToBeProcessed() {
        var result = getenv("NUMBER_OF_MESSAGES_TO_BE_PROCESSED");
        return result == null ? 500 : parseInt(result);
    }

    private int getIntervalBetweenMessagesSentToMq() {
        var result = getenv("INTERVAL_BETWEEN_MESSAGES_SENT_TO_MQ");
        return result == null ? 100 : parseInt(result);
    }

    private String getSmallMessageWithUniqueConversationIdAndMessageId(String message, String conversationId) {
        var messageId = randomUUID().toString();
        message = message.replaceAll("__CONVERSATION_ID__", conversationId);
        message = message.replaceAll("__MESSAGE_ID__", messageId);
        return message;
    }

    private AutoRefreshingRoleAssumingSqsClient appropriateAuthenticationSqsClient() {
        if (config.performanceTestTimeout() > TestConfiguration.SECONDS_IN_AN_HOUR * 0.9) {
            var authStrategyWarning = "Performance test timeout is approaching an hour, getting where this will not work if " +
                    "using temporary credentials (such as obtained by user using MFA) if it exceeds the expiration time. " +
                    "Longer runs will need to be done in pipeline where refresh can be made from the AWS instance's " +
                    "metadata credentials lookup.";
            System.err.println(authStrategyWarning);
        }
        out.println("AUTH STRATEGY: using auto-refresh, role-assuming sqs client");
        return context.getBean(AutoRefreshingRoleAssumingSqsClient.class);
    }
}