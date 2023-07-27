package uk.nhs.prm.e2etests.enumeration;

import uk.nhs.prm.e2etests.configuration.TestConfiguration;

public enum Patient {
    SUSPENDED_WITH_EHR_AT_TPP("9693642937"),
    PATIENT_WITH_LARGE_EHR_AT_EMIS_WITH_MOF_SET_TO_REPO_DEV_ODS("9693643038"),
    PATIENT_WITH_LARGE_EHR_AT_EMIS_WITH_MOF_SET_TO_REPO_TEST_ODS("9694181372"),
    WITH_NO_9693795989_WHATEVER_THAT_MEANS("9693795989"), // TODO PRMT-3488 RENAME THIS
    WITH_SINGLE_FRAGMENT_LARGE_EHR("9727018076"),
    WITH_MULTIPLE_FRAGMENTS_LARGE_EHR("9693643038"),
    WITH_HIGH_FRAGMENT_COUNT_LARGE_EHR("9693796179"),
    WITH_SUPER_LARGE_EHR("9693796004"),
    WITH_LARGE_MEDICAL_HISTORY_EHR("9693796306");

    private String nhsNumber;

    Patient(String nhsNumber) {
        this.nhsNumber = nhsNumber;
    }

    public String nhsNumber() {
        return nhsNumber;
    }

    public static Patient largeEhrAtEmisWithRepoMof(TestConfiguration config) {
        var environmentName = config.getEnvironmentName();
        if ("dev".equals(environmentName)) {
            return PATIENT_WITH_LARGE_EHR_AT_EMIS_WITH_MOF_SET_TO_REPO_DEV_ODS;
        }
        if ("test".equals(environmentName)) {
            return PATIENT_WITH_LARGE_EHR_AT_EMIS_WITH_MOF_SET_TO_REPO_TEST_ODS;
        }
        throw new IllegalStateException("Don't know about large EHR EMIS patient with repo mof in env: " + environmentName);
    }

}
