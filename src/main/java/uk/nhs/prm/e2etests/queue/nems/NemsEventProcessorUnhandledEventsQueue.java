package uk.nhs.prm.e2etests.queue.nems;

import org.springframework.beans.factory.annotation.Autowired;
import uk.nhs.prm.e2etests.property.QueueProperties;
import org.springframework.stereotype.Component;
import uk.nhs.prm.e2etests.queue.AbstractNemsEventMessageQueue;
import uk.nhs.prm.e2etests.service.SqsService;

@Component
public class NemsEventProcessorUnhandledEventsQueue extends AbstractNemsEventMessageQueue {
    @Autowired
    public NemsEventProcessorUnhandledEventsQueue(
            SqsService sqsService,
            QueueProperties queueProperties
    ) {
        super(sqsService, queueProperties.getNemsEventProcessorUnhandledEventsQueueUrl());
    }
}