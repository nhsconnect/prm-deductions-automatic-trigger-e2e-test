package uk.nhs.prm.deduction.e2e.queue;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.sqs.model.Message;

import java.util.List;

@Component
public class SqsQueue {

    private SqsClient sqsClient;

    public SqsQueue() {
        this.sqsClient = new SqsClient();
    }

    public List<SqsMessage> readMessagesFrom(String queueUri) {
        return sqsClient.readMessagesFrom(queueUri);
    }

    public List<SqsMessage> readThroughMessages(String queueUri) {
        return sqsClient.readThroughMessages(queueUri);
    }

    public void deleteMessage(String queueUri,Message message) {
        sqsClient.deleteMessageFrom(queueUri,message);
    }
    public void deleteAllMessage(String queueUri) {
        sqsClient.deleteAllMessageFrom(queueUri);
    }
}
