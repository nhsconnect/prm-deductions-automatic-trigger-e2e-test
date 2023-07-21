package uk.nhs.prm.e2etests.suspensions;

import org.springframework.beans.factory.annotation.Autowired;
import uk.nhs.prm.e2etests.configuration.QueuePropertySource;
import uk.nhs.prm.e2etests.queue.ThinlyWrappedSqsClient;
import uk.nhs.prm.e2etests.queue.QueueMessageHelper;
import org.springframework.stereotype.Component;

@Component
public class MofNotUpdatedMessageQueue extends QueueMessageHelper {
    @Autowired
    public MofNotUpdatedMessageQueue(
            ThinlyWrappedSqsClient thinlyWrappedSqsClient,
            QueuePropertySource queuePropertySource
            ) {
        super(thinlyWrappedSqsClient,
              queuePropertySource.getSuspensionsServiceMofNotUpdatedQueueUrl());
    }
}