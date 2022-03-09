package uk.nhs.prm.deduction.e2e.tests;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.deadletter.NemsEventProcessorDeadLetterQueue;
import uk.nhs.prm.deduction.e2e.mesh.MeshMailbox;
import uk.nhs.prm.deduction.e2e.models.DeceasedPatientMessage;
import uk.nhs.prm.deduction.e2e.models.MofUpdatedMessage;
import uk.nhs.prm.deduction.e2e.models.NoLongerSuspendedMessage;
import uk.nhs.prm.deduction.e2e.models.NonSensitiveDataMessage;
import uk.nhs.prm.deduction.e2e.nems.MeshForwarderQueue;
import uk.nhs.prm.deduction.e2e.nems.NemsEventMessage;
import uk.nhs.prm.deduction.e2e.nems.NemsEventProcessorUnhandledQueue;
import uk.nhs.prm.deduction.e2e.pdsadaptor.PdsAdaptorClient;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AssumeRoleCredentialsProviderFactory;
import uk.nhs.prm.deduction.e2e.performance.awsauth.AutoRefreshingRoleAssumingSqsClient;
import uk.nhs.prm.deduction.e2e.queue.BasicSqsClient;
import uk.nhs.prm.deduction.e2e.queue.SqsQueue;
import uk.nhs.prm.deduction.e2e.suspensions.*;
import uk.nhs.prm.deduction.e2e.utility.NemsEventFactory;
import uk.nhs.prm.deduction.e2e.utility.QueueHelper;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.Map;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static uk.nhs.prm.deduction.e2e.nhs.NhsIdentityGenerator.*;
import static uk.nhs.prm.deduction.e2e.utility.NemsEventFactory.createNemsEventFromTemplate;


@SpringBootTest(classes = {
        EndToEndTest.class,
        MeshMailbox.class,
        SqsQueue.class,
        TestConfiguration.class,
        MeshForwarderQueue.class,
        NemsEventProcessorUnhandledQueue.class,
        NemsEventProcessorSuspensionsMessageQueue.class,
        SuspensionServiceNotReallySuspensionsMessageQueue.class,
        NemsEventProcessorDeadLetterQueue.class,
        MeshForwarderQueue.class,
        QueueHelper.class,
        MofUpdatedMessageQueue.class,
        DeceasedPatientQueue.class,
        MofNotUpdatedMessageQueue.class,
        BasicSqsClient.class,
        AssumeRoleCredentialsProviderFactory.class,
        AutoRefreshingRoleAssumingSqsClient.class
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class EndToEndTest {

    public static String SYNTHETIC_PATIENT_WHICH_HAS_CURRENT_GP_NHS_NUMBER;
    public static String SYNTHETIC_PATIENT_WHICH_IS_DECEASED ;
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
    private DeceasedPatientQueue deceasedPatientQueue;
    @Autowired
    private MeshMailbox meshMailbox;


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
        SYNTHETIC_PATIENT_WHICH_HAS_CURRENT_GP_NHS_NUMBER = nhsEnvironment.equals("dev") ? "9693796284" : "9694179254";
        SYNTHETIC_PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER = nhsEnvironment.equals("dev") ? "9693795997" : "9694179343";
        SYNTHETIC_PATIENT_WHICH_IS_DECEASED = nhsEnvironment.equals("dev") ? "9693797264" : "9694179394";
        NON_SYNTHETIC_PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER = "9692295400";
    }

    @Test
    @Order(1)
    public void shouldMoveSuspensionMessageFromNemsToMofUpdatedQueue() {
        String nemsMessageId = randomNemsMessageId();
        String suspendedPatientNhsNumber = SYNTHETIC_PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER;
        var now = ZonedDateTime.now(ZoneOffset.ofHours(0)).toString();
        String previousGp = generateRandomOdsCode();
        System.out.printf("Generated random ods code for previous gp: %s%n", previousGp);

        NemsEventMessage nemsSuspension = createNemsEventFromTemplate("change-of-gp-suspension.xml", suspendedPatientNhsNumber, nemsMessageId, previousGp, now);
        meshMailbox.postMessage(nemsSuspension);
        MofUpdatedMessage expectedMessageOnQueue = new MofUpdatedMessage(nemsMessageId, "ACTION:UPDATED_MANAGING_ORGANISATION");

        assertThat(meshForwarderQueue.hasMessage(nemsSuspension.body()));
        assertThat(mofUpdatedMessageQueue.hasMessage(expectedMessageOnQueue));
    }


    @Test
    @Order(2)
    public void shouldMoveSuspensionMessageWherePatientIsNoLongerSuspendedToNotSuspendedQueue() {
        String nemsMessageId = randomNemsMessageId();
        String previousGp = generateRandomOdsCode();
        var now = ZonedDateTime.now(ZoneOffset.ofHours(0)).toString();
        String currentlyRegisteredPatientNhsNumber = SYNTHETIC_PATIENT_WHICH_HAS_CURRENT_GP_NHS_NUMBER;

        NemsEventMessage nemsSuspension = createNemsEventFromTemplate("change-of-gp-suspension.xml", currentlyRegisteredPatientNhsNumber, nemsMessageId, previousGp,now);

        NoLongerSuspendedMessage expectedMessageOnQueue = new NoLongerSuspendedMessage(nemsMessageId, "NO_ACTION:NO_LONGER_SUSPENDED_ON_PDS");

        meshMailbox.postMessage(nemsSuspension);
        assertThat(notReallySuspensionsMessageQueue.hasMessage(expectedMessageOnQueue));

    }

    @Test
    @Order(5)
    public void shouldMoveNonSuspensionMessageFromNemsToUnhandledQueue() throws Exception {
        String nemsMessageId = randomNemsMessageId();
        String nhsNumber = randomNhsNumber();
        var now = ZonedDateTime.now(ZoneOffset.ofHours(0)).toString();
        NemsEventMessage nemsNonSuspension = createNemsEventFromTemplate("change-of-gp-non-suspension.xml", nhsNumber, nemsMessageId,now);
        meshMailbox.postMessage(nemsNonSuspension);
        assertThat(nemsEventProcessorUnhandledQueue.hasMessage("{\"nemsMessageId\":\"" + nemsMessageId + "\",\"messageStatus\":\"NO_ACTION:NON_SUSPENSION\"}"));
    }


    @Test
    @Order(6)
    public void shouldSendUnprocessableMessagesToDlQ() throws Exception {
        Map<String, NemsEventMessage> dlqMessages = NemsEventFactory.getDLQNemsEventMessages();
        log("Posting DLQ messages");
        for (Map.Entry<String, NemsEventMessage> message : dlqMessages.entrySet()) {
            meshMailbox.postMessage(message.getValue());
            log("Posted " + message.getKey() + " messages");
            assertThat(nemsEventProcessorDeadLetterQueue.hasMessage(message.getValue().body()));
        }
    }

    @Test
    @Order(4)
    public void shouldMoveNonSyntheticPatientSuspensionMessageFromNemsToMofNotUpdatedQueueWhenToggleOn() {
        String nemsMessageId = randomNemsMessageId();
        String previousGp = generateRandomOdsCode();
        var now = ZonedDateTime.now(ZoneOffset.ofHours(0)).toString();
        NemsEventMessage nemsSuspension = createNemsEventFromTemplate("change-of-gp-suspension.xml", NON_SYNTHETIC_PATIENT_WHICH_HAS_NO_CURRENT_GP_NHS_NUMBER, nemsMessageId, previousGp,now);
        meshMailbox.postMessage(nemsSuspension);

        NonSensitiveDataMessage expectedMessageOnQueue = new NonSensitiveDataMessage(nemsMessageId, "NO_ACTION:NOT_SYNTHETIC");

        assertThat(mofNotUpdatedMessageQueue.hasMessage(expectedMessageOnQueue));
    }

    @Test
    @Order(3)
    public void shouldMoveDeceasedPatientToDeceasedQueue() {
        String nemsMessageId = randomNemsMessageId();
        String suspendedPatientNhsNumber = SYNTHETIC_PATIENT_WHICH_IS_DECEASED;
        var now = ZonedDateTime.now(ZoneOffset.ofHours(0)).toString();
        String previousGp = generateRandomOdsCode();
        System.out.printf("Generated random ods code for previous gp: %s%n", previousGp);

        NemsEventMessage nemsSuspension = createNemsEventFromTemplate("change-of-gp-suspension.xml", suspendedPatientNhsNumber, nemsMessageId, previousGp,now);
        meshMailbox.postMessage(nemsSuspension);
        DeceasedPatientMessage expectedMessageOnQueue = new DeceasedPatientMessage(nemsMessageId, "NO_ACTION:DECEASED_PATIENT");

        assertThat(deceasedPatientQueue.hasMessage(expectedMessageOnQueue));
    }
    public void log(String message) {
        System.out.println(message);
    }
}
