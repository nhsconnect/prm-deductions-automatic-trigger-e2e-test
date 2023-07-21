package uk.nhs.prm.e2etests.model;

import uk.nhs.prm.e2etests.TestConfiguration;
import uk.nhs.prm.e2etests.exception.InvalidNhsEnvironmentException;

public enum Gp2GpSystem {
    REPO_DEV("B85002"),
    REPO_TEST("B86041"),
    EMIS_PTL_INT("N82668"),
    TPP_PTL_INT("M85019");

    private final String odsCode;

    Gp2GpSystem(String odsCode) {
        this.odsCode = odsCode;
    }

    public String odsCode() {
        return odsCode;
    }

    public static Gp2GpSystem repoInEnv(String nhsEnvironment) {
        switch (nhsEnvironment) {
            case "dev" -> { return REPO_DEV; }
            case "test" -> { return REPO_TEST; }
            default -> throw new InvalidNhsEnvironmentException();
        }
    }
}