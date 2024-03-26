package uk.nhs.prm.e2etests.property;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Getter
@Component
public class DynamoDbProperties {
    /**
     * @deprecated PRMT-4670 old Transfer Tracker DB logic - to be removed
     */
    @Deprecated(since="2.0.0", forRemoval = true)
    @Value("${aws.configuration.databaseNames.oldTransferTrackerDb}")
    private String oldTransferTrackerDbName;

    @Value("${aws.configuration.databaseNames.transferTrackerDb}")
    private String transferTrackerDbName;

    @Value("${aws.configuration.databaseNames.activeSuspensionsDb}")
    private String activeSuspensionsDbName;
}
