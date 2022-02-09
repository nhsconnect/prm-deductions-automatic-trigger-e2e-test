package uk.nhs.prm.deduction.e2e.queue;

import software.amazon.awssdk.services.sqs.model.*;
import uk.nhs.prm.deduction.e2e.performance.AssumeRoleAuthAutoRefreshSqsClientFactory;

import java.util.List;
import java.util.stream.Collectors;

public class SqsClient {
    private final AssumeRoleAuthAutoRefreshSqsClientFactory autoRefreshClientFactory = new AssumeRoleAuthAutoRefreshSqsClientFactory();
    private final software.amazon.awssdk.services.sqs.SqsClient sqsClient = autoRefreshClientFactory.createAssumeRoleAutoRefreshSqsClient();

    public List<SqsMessage> readMessagesFrom(String queueUrl) {
        var receiveMessageRequest = ReceiveMessageRequest.builder()
                .visibilityTimeout(0)
                .queueUrl(queueUrl)
                .waitTimeSeconds(5)
                .maxNumberOfMessages(10)
                .attributeNames(QueueAttributeName.ALL)
                .build();

        return receiveMessages(receiveMessageRequest)
                .messages()
                .stream()
                .map(SqsMessage::new)
                .collect(Collectors.toList());
    }

    public List<SqsMessage> readThroughMessages(String queueUrl, int visibilityTimeout) {
        var receiveMessageRequest = ReceiveMessageRequest.builder()
            .visibilityTimeout(visibilityTimeout)
            .queueUrl(queueUrl)
            .waitTimeSeconds(5)
            .maxNumberOfMessages(10)
            .attributeNames(QueueAttributeName.ALL)
            .build();

        return receiveMessages(receiveMessageRequest).messages()
                .stream()
                .map(SqsMessage::new)
                .collect(Collectors.toList());
    }

    public void deleteMessageFrom(String queueUrl, Message message) {
        sqsClient.deleteMessage(DeleteMessageRequest.builder().queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
    }

    public void deleteAllMessageFrom(String queueUrl) {
        sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
    }

    private ReceiveMessageResponse receiveMessages(ReceiveMessageRequest receiveMessageRequest) {
        return sqsClient.receiveMessage(receiveMessageRequest);
    }

}
