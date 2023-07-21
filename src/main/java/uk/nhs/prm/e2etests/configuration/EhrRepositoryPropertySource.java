package uk.nhs.prm.e2etests.configuration;

import lombok.AccessLevel;
import lombok.Getter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import uk.nhs.prm.e2etests.services.SsmService;

@Getter
@Component
public class EhrRepositoryPropertySource extends AbstractSsmRetriever {

    @Value("${nhs.services.gp2gp.odsCodes.repoDev}")
    private String repoDevOdsCode;

    @Value("${nhs.services.gp2gp.odsCodes.repoTest}")
    private String repoTestOdsCode;

    @Getter(AccessLevel.NONE)
    @Value("${aws.configuration.ssm.parameters.ehrRepository.liveTestApiKey}")
    private String liveTestApiKey;

    @Getter(AccessLevel.NONE)
    @Value("${aws.configuration.ssm.parameters.ehrRepository.e2eTestApiKey}")
    private String e2eTestApiKey;

    @Value("${aws.configuration.serviceUrls.ehrRepository}")
    private String ehrRepositoryUrl;

    @Autowired
    public EhrRepositoryPropertySource(SsmService ssmService) {
        super(ssmService);
    }

    public String getLiveTestApiKey() {
        return super.getAwsSsmParameterValue(this.liveTestApiKey);
    }

    public String getE2eTestApiKey() {
        return super.getAwsSsmParameterValue(this.e2eTestApiKey);
    }
}