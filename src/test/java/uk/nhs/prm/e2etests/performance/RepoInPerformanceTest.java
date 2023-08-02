package uk.nhs.prm.e2etests.performance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.nhs.prm.e2etests.configuration.TestData;
import uk.nhs.prm.e2etests.model.RepoIncomingMessage;
import uk.nhs.prm.e2etests.queue.ehrtransfer.EhrTransferServiceRepoIncomingQueue;
import uk.nhs.prm.e2etests.queue.ehrtransfer.observability.EhrTransferServiceTransferCompleteOQ;
import uk.nhs.prm.e2etests.enumeration.Gp2GpSystem;
import uk.nhs.prm.e2etests.model.RepoIncomingMessageBuilder;
import uk.nhs.prm.e2etests.performance.reporting.RepoInPerformanceChartGenerator;
import uk.nhs.prm.e2etests.tests.ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient;
import uk.nhs.prm.e2etests.queue.SimpleAmqpQueue;
import uk.nhs.prm.e2etests.model.SqsMessage;
import uk.nhs.prm.e2etests.utility.ResourceUtility;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.lang.Integer.parseInt;
import static java.lang.System.getenv;
import static java.time.LocalDateTime.now;
import static java.util.UUID.randomUUID;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.nhs.prm.e2etests.utility.ThreadUtility.sleepFor;

@SpringBootTest
@ExtendWith(ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepoInPerformanceTest {

    private final EhrTransferServiceRepoIncomingQueue ehrTransferServiceRepoIncomingQueue;
    private final SimpleAmqpQueue inboundQueueFromMhs;
    private final EhrTransferServiceTransferCompleteOQ ehrTransferServiceTransferCompleteOQ;

    @Autowired
    public RepoInPerformanceTest(
            EhrTransferServiceRepoIncomingQueue ehrTransferServiceRepoIncomingQueue,
            SimpleAmqpQueue inboundQueueFromMhs,
            EhrTransferServiceTransferCompleteOQ ehrTransferServiceCompleteOQ
    ) {
        this.ehrTransferServiceRepoIncomingQueue = ehrTransferServiceRepoIncomingQueue;
        this.inboundQueueFromMhs = inboundQueueFromMhs;
        this.ehrTransferServiceTransferCompleteOQ = ehrTransferServiceCompleteOQ;
    }

    @Test
    public void trackBehaviourOfHighNumberOfMessagesSentToEhrTransferService() {
        System.out.println("BEGINNING PROBLEMATIC TEST");
        int numberOfMessagesToBeProcessed = getNumberOfMessagesToBeProcessed();
        List<RepoInPerfMessageWrapper> messagesToBeProcessed = setupMessagesToBeProcessed(numberOfMessagesToBeProcessed);

        System.out.println("Setup completed. About to send messages to mq...");
        sendMessagesToMq(messagesToBeProcessed);

        System.out.println("All messages sent. Ensuring they reached transfer complete queue...");
        assertMessagesAreInTransferCompleteQueue(numberOfMessagesToBeProcessed, messagesToBeProcessed);
    }

    private void assertMessagesAreInTransferCompleteQueue(int numberOfMessagesToBeProcessed, List<RepoInPerfMessageWrapper> messagesToBeProcessed) {
        int messagesReadFromQueueEveryMinute = 100;
        int additionalMinutesBuffer = 5;
        int timeoutInMinutes = Math.round(numberOfMessagesToBeProcessed / messagesReadFromQueueEveryMinute) + additionalMinutesBuffer;
        System.out.println("Polling messages from transfer complete queue, timeout for this operation set to " + timeoutInMinutes + " minutes.");

        List<RepoInPerfMessageWrapper> messagesProcessed = new ArrayList<>();
        LocalDateTime timeout = now().plusMinutes(timeoutInMinutes);
        while (now().isBefore(timeout) && messagesToBeProcessed.size() > 0) {
            for (SqsMessage sqsMessage : ehrTransferServiceTransferCompleteOQ.getNextMessages(timeout)) {
                String conversationId = sqsMessage.getAttributes().get("conversationId").stringValue();
                messagesToBeProcessed.removeIf(message -> {
                    if (message.getMessage().conversationId().equals(conversationId)) {
                        message.finish(sqsMessage.getQueuedAt());
                        ehrTransferServiceTransferCompleteOQ.deleteMessage(sqsMessage);
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
                int numberOfMessagesProcessed = numberOfMessagesToBeProcessed - messagesToBeProcessed.size();
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
        int intervalBetweenMessagesSentToMq = getIntervalBetweenMessagesSentToMq();
        try {
            String messageTemplate = ResourceUtility.readTestResourceFileFromEhrDirectory("small-ehr-4MB");
            AtomicInteger counter = new AtomicInteger(0);
            String smallEhr;

            // TODO PRMT-3574 traditional for loop here that could be refactored
            for (int i = 0; i < messagesToBeProcessed.size(); i++) {
                counter.updateAndGet(v -> v + 1);
                String conversationId = messagesToBeProcessed.get(i).getMessage().conversationId();

                smallEhr = getSmallMessageWithUniqueConversationIdAndMessageId(messageTemplate, conversationId);
                messagesToBeProcessed.get(i).start();

                System.out.println("Item " + counter.get() + " - sending to mq conversationId " + conversationId);
                inboundQueueFromMhs.sendMessage(smallEhr, conversationId);

                sleepFor(intervalBetweenMessagesSentToMq);
            }

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
        String conversationId = message.getMessage().conversationId();

        String smallEhr = getSmallMessageWithUniqueConversationIdAndMessageId(messageTemplate, conversationId);
        message.start();

        System.out.println("Item " + counter.get() + " - sending to mq conversationId " + conversationId);
        inboundQueueFromMhs.sendMessage(smallEhr, conversationId);


        sleepFor(100);
        message.finish(LocalDateTime.now());
    }

    private List<RepoInPerfMessageWrapper> setupMessagesToBeProcessed(int numberOfMessagesToBeProcessed) {
        List<RepoInPerfMessageWrapper> messagesToBeProcessed = new ArrayList<>();

        for (int i = 0; i < numberOfMessagesToBeProcessed ; i++) {
            RepoIncomingMessage message = new RepoIncomingMessageBuilder()
                    .withNhsNumber(TestData.generateRandomNhsNumber())
                    .withEhrSourceGp(Gp2GpSystem.EMIS_PTL_INT)
                    .build();
            messagesToBeProcessed.add(new RepoInPerfMessageWrapper(message));
        }

        messagesToBeProcessed.forEach(message -> ehrTransferServiceRepoIncomingQueue.send(message.getMessage()));
        return messagesToBeProcessed;
    }

    private int getNumberOfMessagesToBeProcessed() {
        String result = getenv("NUMBER_OF_MESSAGES_TO_BE_PROCESSED");
        return result == null ? 500 : parseInt(result);
    }

    private int getIntervalBetweenMessagesSentToMq() {
        String result = getenv("INTERVAL_BETWEEN_MESSAGES_SENT_TO_MQ");
        return result == null ? 100 : parseInt(result);
    }

    private String getSmallMessageWithUniqueConversationIdAndMessageId(String message, String conversationId) {
        String messageId = randomUUID().toString();
        message = message.replaceAll("__CONVERSATION_ID__", conversationId);
        message = message.replaceAll("__MESSAGE_ID__", messageId);
        return message;
    }
}