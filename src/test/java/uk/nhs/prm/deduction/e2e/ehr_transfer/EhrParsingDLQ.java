package uk.nhs.prm.deduction.e2e.ehr_transfer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.queue.QueueMessageHelper;
import uk.nhs.prm.deduction.e2e.queue.ThinlyWrappedSqsClient;

@Component
public class EhrParsingDLQ extends QueueMessageHelper {

    @Autowired
    public EhrParsingDLQ(ThinlyWrappedSqsClient thinlyWrappedSqsClient, TestConfiguration configuration) {
        super(thinlyWrappedSqsClient, configuration.parsingDLQ());
    }
}
