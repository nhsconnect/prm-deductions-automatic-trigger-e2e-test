package uk.nhs.prm.e2etests.deadletter;


import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.nhs.prm.e2etests.configuration.QueuePropertySource;
import uk.nhs.prm.e2etests.queue.ThinlyWrappedSqsClient;
import uk.nhs.prm.e2etests.nems.NemsEventMessageQueue;

@Component
public class NemsEventProcessorDeadLetterQueue extends NemsEventMessageQueue {

    @Autowired
    public NemsEventProcessorDeadLetterQueue(
            ThinlyWrappedSqsClient thinlyWrappedSqsClient,
            QueuePropertySource queuePropertySource
    ) {
        super(thinlyWrappedSqsClient,
              queuePropertySource.getNemsEventProcessorDlqQueueUrl());
    }
}
