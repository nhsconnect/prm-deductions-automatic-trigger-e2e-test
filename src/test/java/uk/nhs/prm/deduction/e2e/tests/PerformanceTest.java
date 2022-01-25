package uk.nhs.prm.deduction.e2e.tests;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.auth.AuthTokenGenerator;
import uk.nhs.prm.deduction.e2e.deadletter.NemsEventProcessorDeadLetterQueue;
import uk.nhs.prm.deduction.e2e.mesh.MeshClient;
import uk.nhs.prm.deduction.e2e.mesh.MeshMailbox;
import uk.nhs.prm.deduction.e2e.nems.MeshForwarderQueue;
import uk.nhs.prm.deduction.e2e.nems.NemsEventMessage;
import uk.nhs.prm.deduction.e2e.nems.NemsEventProcessorUnhandledQueue;
import uk.nhs.prm.deduction.e2e.pdsadaptor.PdsAdaptorClient;
import uk.nhs.prm.deduction.e2e.pdsadaptor.PdsAdaptorResponse;
import uk.nhs.prm.deduction.e2e.queue.SqsQueue;
import uk.nhs.prm.deduction.e2e.suspensions.MofNotUpdatedMessageQueue;
import uk.nhs.prm.deduction.e2e.suspensions.MofUpdatedMessageQueue;
import uk.nhs.prm.deduction.e2e.suspensions.NemsEventProcessorSuspensionsMessageQueue;
import uk.nhs.prm.deduction.e2e.suspensions.SuspensionServiceNotReallySuspensionsMessageQueue;
import uk.nhs.prm.deduction.e2e.utility.Helper;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;


@SpringBootTest(classes = {
        PerformanceTest.class,
        MeshMailbox.class,
        SqsQueue.class,
        MeshClient.class,
        TestConfiguration.class,
        AuthTokenGenerator.class,
        MeshForwarderQueue.class,
        NemsEventProcessorUnhandledQueue.class,
        NemsEventProcessorSuspensionsMessageQueue.class,
        SuspensionServiceNotReallySuspensionsMessageQueue.class,
        NemsEventProcessorDeadLetterQueue.class,
        MeshForwarderQueue.class,
        Helper.class,
        MofUpdatedMessageQueue.class,
        MofNotUpdatedMessageQueue.class,
        PdsAdaptorClient.class
})


@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class PerformanceTest {

    public static String SYNTHETIC_PATIENT_WHICH_HAS_CURRENT_GP_NHS_NUMBER;
    public static String SYNTHETIC_PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER;
    public static String NON_SYNTHETIC_PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER;
    @Autowired
    private MeshForwarderQueue meshForwarderQueue;
    @Autowired
    private NemsEventProcessorUnhandledQueue nemsEventProcessorUnhandledQueue;
    @Autowired
    private NemsEventProcessorSuspensionsMessageQueue suspensionsMessageQueue;
    @Autowired
    private SuspensionServiceNotReallySuspensionsMessageQueue notReallySuspensionsMessageQueue;
    @Autowired
    private MofUpdatedMessageQueue mofUpdatedMessageQueue;
    @Autowired
    private MofNotUpdatedMessageQueue mofNotUpdatedMessageQueue;
    @Autowired
    private NemsEventProcessorDeadLetterQueue nemsEventProcessorDeadLetterQueue;
    @Autowired
    private MeshMailbox meshMailbox;
    @Autowired
    private Helper helper;

    @BeforeAll
    void init() {
        initializeNhsNumberBasedOnEnvironment();
        meshForwarderQueue.deleteAllMessages();
        nemsEventProcessorDeadLetterQueue.deleteAllMessages();
        suspensionsMessageQueue.deleteAllMessages();
        nemsEventProcessorUnhandledQueue.deleteAllMessages();
        notReallySuspensionsMessageQueue.deleteAllMessages();
    }

    private void initializeNhsNumberBasedOnEnvironment() {
        // NHS Number needs to be different in each env as the synthetic patient prefix is different
        String nhsEnvironment = System.getenv("NHS_ENVIRONMENT");
        SYNTHETIC_PATIENT_WHICH_HAS_CURRENT_GP_NHS_NUMBER = nhsEnvironment.equals("dev") ? "9693795997" : "9694179254";
        SYNTHETIC_PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER = nhsEnvironment.equals("dev") ? "9693797396" : "9694179262";
        NON_SYNTHETIC_PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER = "9692295400";
    }

    @Disabled("WIP")
    @Test
    public void shouldMoveSuspensionMessageFromNemsToMofUpdatedQueue() throws Exception {
        String suspendedPatientNhsNumber = SYNTHETIC_PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER;

        PdsAdaptorClient pdsAdaptorClient = new PdsAdaptorClient(suspendedPatientNhsNumber);

        PdsAdaptorResponse pdsAdaptorResponse = pdsAdaptorClient.getSuspendedPatientStatus();

        pdsAdaptorClient.updateManagingOrganisation(PdsAdaptorTest.generateRandomOdsCode(), pdsAdaptorResponse.getRecordETag());

        NemsEventMessage nemsSuspension = helper.createNemsEventFromTemplate("change-of-gp-suspension.xml", suspendedPatientNhsNumber);
        meshMailbox.postMessage(nemsSuspension);
        assertThat(meshForwarderQueue.hasMessage(nemsSuspension.body()));
        assertThat(suspensionsMessageQueue.hasMessage(suspendedPatientNhsNumber));
        assertThat(mofUpdatedMessageQueue.hasMessage(suspendedPatientNhsNumber));
    }
}