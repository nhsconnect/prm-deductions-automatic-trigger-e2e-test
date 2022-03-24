package uk.nhs.prm.deduction.e2e;

import org.springframework.stereotype.Component;
import software.amazon.awssdk.utils.ImmutableMap;
import uk.nhs.prm.deduction.e2e.client.AwsConfigurationClient;
import uk.nhs.prm.deduction.e2e.client.RoleAssumingAwsConfigurationClient;
import uk.nhs.prm.deduction.e2e.config.BootstrapConfiguration;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AssumeRoleCredentialsProviderFactory;
import uk.nhs.prm.deduction.e2e.performance.load.LoadPhase;
import uk.nhs.prm.deduction.e2e.performance.load.LoadSpecParser;

import java.util.List;

import static java.lang.Integer.parseInt;
import static java.lang.System.getenv;

@Component
public class TestConfiguration {

    public static final int SECONDS_IN_AN_HOUR = 3600;

    private final ImmutableMap<String, List<String>> suspendedNhsNumbersByEnv = ImmutableMap.of(
            "dev", TestData.dev(),
            "pre-prod", TestData.preProd(),
            "perf", TestData.perf(numberOfPerfNhsNumbers())
    );

    public String getNhsNumberForSyntheticPatientWithCurrentGp() {
        return getEnvironmentName().equals("dev") ? "9693796284" : "9694179254";
    }

    public String getNhsNumberForSyntheticPatientWithoutGp() {
        return getEnvironmentName().equals("dev") ? "9693795997" : "9694179343";
    }

    public String getNhsNumberForSyntheticDeceasedPatient() {
        return getEnvironmentName().equals("dev") ? "9693797264" : "9694179394";
    }

    public String getNhsNumberForNonSyntheticPatientWithoutGp() {
        return "9692295400";
    }

    private final AwsConfigurationClient awsConfigurationClient;

    private volatile String cachedAwsAccountNo;

    public TestConfiguration() {
        this.awsConfigurationClient = createAwsConfigurationClient();
    }

    public String getMeshMailBoxID() {
        return awsConfigurationClient.getParamValue(String.format("/repo/%s/user-input/external/mesh-mailbox-id", getEnvironmentName()));
    }

    public String getMeshMailBoxClientCert() {
        return awsConfigurationClient.getParamValue(String.format("/repo/%s/user-input/external/mesh-mailbox-client-cert", getEnvironmentName()));
    }

    public String getMeshMailBoxClientKey() {
        return awsConfigurationClient.getParamValue(String.format("/repo/%s/user-input/external/mesh-mailbox-client-key", getEnvironmentName()));
    }

    public String getMeshMailBoxPassword() {
        return awsConfigurationClient.getParamValue(String.format("/repo/%s/user-input/external/mesh-mailbox-password", getEnvironmentName()));
    }

    public String getPdsAdaptorPerformanceApiKey() {
        return awsConfigurationClient.getParamValue(String.format("/repo/%s/user-input/api-keys/pds-adaptor/performance-test", getEnvironmentName()));
    }

    public String meshForwarderObservabilityQueueUri() {
        return getQueueUri("mesh-forwarder-nems-events-observability-queue");
    }

    public String nemsEventProcesorUnhandledQueueUri() {
        return getQueueUri("nems-event-processor-unhandled-events-queue");
    }

    public String suspensionsObservabilityQueueUri() {
        return getQueueUri("nems-event-processor-suspensions-observability-queue");
    }

    public String notReallySuspendedObservabilityQueueUri() {
        return getQueueUri("suspension-service-not-suspended-observability-queue");
    }

    public String nemsEventProcessorDeadLetterQueue() {
        return getQueueUri("nems-event-processor-dlq");
    }

    public String mofUpdatedQueueUri() {
        return getQueueUri("suspension-service-mof-updated-queue");
    }

    public String mofNotUpdatedQueueUri() {
        return getQueueUri("suspension-service-mof-not-updated-queue");
    }

    public String deceasedQueueUri() {
        return getQueueUri("suspension-service-deceased-patient-queue");
    }

    private String getQueueUri(String name) {
        return String.format("https://sqs.eu-west-2.amazonaws.com/%s/%s-%s", getAwsAccountNo(), getEnvironmentName(), name);
    }

    public String getPdsAdaptorUrl() {
        return String.format("https://pds-adaptor.%s.non-prod.patient-deductions.nhs.uk/", getEnvironmentName());
    }

    private String getAwsAccountNo() {
        if (cachedAwsAccountNo == null) {
            cachedAwsAccountNo = fetchAwsAccountNo();
        }
        return cachedAwsAccountNo;
    }

    private String fetchAwsAccountNo() {
        return BootstrapConfiguration.exampleAssumeRoleArn().accountNo();
    }

    public List<String> suspendedNhsNumbers() {
        List<String> nhsNumbers = suspendedNhsNumbersByEnv.get(getEnvironmentName());
        if (nhsNumbers == null) {
            throw new RuntimeException("No NHS Numbers configured for " + getEnvironmentName() + " environment");
        }
        return nhsNumbers;
    }

    private int numberOfPerfNhsNumbers() {
        String perfPatientsRequested = getenv("NUMBER_OF_PERF_NHS_NUMBERS");
        if (perfPatientsRequested == null) {
            return 40;
        }
        return parseInt(perfPatientsRequested);
    }

    public List<LoadPhase> performanceTestLoadPhases(List<LoadPhase> defaultLoadPhases) {
        String loadSpec = getenv("PERFORMANCE_LOAD_SPEC");
        if (loadSpec == null) {
            return defaultLoadPhases;
        }
        return LoadSpecParser.parsePhases(loadSpec);
    }

    public int performanceTestTimeout() {
        String timeout = getenv("PERFORMANCE_TEST_TIMEOUT");
        if (timeout == null) {
            return 90;
        }
        return parseInt(timeout);
    }

    public String getEnvironmentName() {
        return getRequiredEnvVar("NHS_ENVIRONMENT");
    }

    public static String getRequiredEnvVar(String name) {
        String value = getenv(name);
        if (value == null) {
            throw new RuntimeException("Required environment variable has not been set: " + name);
        }
        return value;
    }

    private AwsConfigurationClient createAwsConfigurationClient() {
        return new RoleAssumingAwsConfigurationClient(new AssumeRoleCredentialsProviderFactory());
    }

}
