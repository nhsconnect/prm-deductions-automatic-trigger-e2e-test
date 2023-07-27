package uk.nhs.prm.e2etests.queue.suspensions;

import org.springframework.beans.factory.annotation.Autowired;
import uk.nhs.prm.e2etests.property.QueueProperties;
import uk.nhs.prm.e2etests.client.ThinlyWrappedSqsClient;
import uk.nhs.prm.e2etests.queue.QueueMessageHelper;
import org.springframework.stereotype.Component;

// TODO PRMT-3488 'real' queue? What's a 'fake' queue? Does this need a rename?
@Component
public class SuspensionMessageRealQueue extends QueueMessageHelper {

    @Autowired
    public SuspensionMessageRealQueue(
            ThinlyWrappedSqsClient thinlyWrappedSqsClient,
            QueueProperties queueProperties
    ) {
        super(thinlyWrappedSqsClient, queueProperties.getSuspensionsServiceSuspensionsQueueUrl());
    }
}