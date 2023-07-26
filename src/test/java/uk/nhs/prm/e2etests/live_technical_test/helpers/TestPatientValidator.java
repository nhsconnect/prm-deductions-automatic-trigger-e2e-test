package uk.nhs.prm.e2etests.live_technical_test.helpers;

import org.springframework.beans.factory.annotation.Autowired;
import uk.nhs.prm.e2etests.property.NhsProperties;
import org.springframework.stereotype.Component;
import lombok.extern.log4j.Log4j2;

import java.util.List;

@Log4j2
@Component
public class TestPatientValidator {
    private final List<String> safeListedNhsNumbers;
    private final String syntheticPatientPrefix;

    @Autowired
    public TestPatientValidator(NhsProperties nhsProperties) {
        safeListedNhsNumbers = nhsProperties.getSafeListedPatientList();
        syntheticPatientPrefix = nhsProperties.getSyntheticPatientPrefix();
    }

    public boolean isIncludedInTheTest(String nhsNumber) {
        log.info("Checking if nhs number is safe listed or synthetic");
        return (safeListedNhsNumbers.contains(nhsNumber) || nhsNumber.startsWith(syntheticPatientPrefix));
    }
}
