package uk.nhs.prm.e2etests.test.repositorye2etest;

import lombok.extern.log4j.Log4j2;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import uk.nhs.prm.e2etests.enumeration.ConversationTransferStatus;
import uk.nhs.prm.e2etests.enumeration.TemplateVariant;
import uk.nhs.prm.e2etests.model.SqsMessage;
import uk.nhs.prm.e2etests.model.database.ConversationRecord;
import uk.nhs.prm.e2etests.model.templatecontext.*;
import uk.nhs.prm.e2etests.property.NhsProperties;
import uk.nhs.prm.e2etests.model.templatecontext.EhrRequestTemplateContext;
import uk.nhs.prm.e2etests.property.TestConstants;
import uk.nhs.prm.e2etests.queue.SimpleAmqpQueue;
import uk.nhs.prm.e2etests.queue.ehrtransfer.EhrTransferServiceParsingDeadLetterQueue;
import uk.nhs.prm.e2etests.queue.ehrtransfer.observability.*;
import uk.nhs.prm.e2etests.queue.gp2gpmessenger.observability.Gp2GpMessengerOQ;
import uk.nhs.prm.e2etests.service.TemplatingService;
import uk.nhs.prm.e2etests.service.TransferTrackerService;
import uk.nhs.prm.e2etests.test.ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.nhs.prm.e2etests.enumeration.ConversationTransferStatus.*;
import static uk.nhs.prm.e2etests.enumeration.Gp2GpSystem.TPP_PTL_INT;
import static uk.nhs.prm.e2etests.enumeration.Patient.PATIENT_WITH_SMALL_EHR_IN_REPO_AND_MOF_SET_TO_TPP;
import static uk.nhs.prm.e2etests.property.TestConstants.*;
import static uk.nhs.prm.e2etests.property.TestConstants.asidCode;
import static uk.nhs.prm.e2etests.enumeration.ConversationTransferStatus.INBOUND_COMPLETE;
import static uk.nhs.prm.e2etests.enumeration.TemplateVariant.EHR_REQUEST;


@Log4j2
@SpringBootTest
@ExtendWith(ForceXercesParserSoLogbackDoesNotBlowUpWhenUsingSwiftMqClient.class)
@TestPropertySource(properties = {"test.pds.username=e2e-test"})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RepositoryNackTests {
    private final TransferTrackerService transferTrackerService;
    private final TemplatingService templatingService;
    private final SimpleAmqpQueue mhsInboundQueue;
    private final Gp2GpMessengerOQ gp2gpMessengerOQ;
    private final EhrTransferServiceTransferCompleteOQ ehrTransferServiceTransferCompleteOQ;
    private final EhrTransferServiceUnhandledOQ ehrTransferServiceUnhandledOQ;
    private final EhrTransferServiceLargeEhrFragmentsOQ ehrTransferServiceLargeEhrFragmentsOQ;
    private final EhrTransferServiceSmallEhrOQ ehrTransferServiceSmallEhrOQ;
    private final EhrTransferServiceLargeEhrOQ ehrTransferServiceLargeEhrOQ;
    private final EhrTransferServiceNegativeAcknowledgementOQ ehrTransferServiceNegativeAcknowledgementOQ;
    private final EhrTransferServiceParsingDeadLetterQueue ehrTransferServiceParsingDeadLetterQueue;

    @Autowired
    public RepositoryNackTests(
            TransferTrackerService transferTrackerService,
            TemplatingService templatingService,
            SimpleAmqpQueue mhsInboundQueue,
            Gp2GpMessengerOQ gp2gpMessengerOQ,
            EhrTransferServiceTransferCompleteOQ ehrTransferServiceTransferCompleteOQ,
            EhrTransferServiceUnhandledOQ ehrTransferServiceUnhandledOQ,
            EhrTransferServiceLargeEhrFragmentsOQ ehrTransferServiceLargeEhrFragmentsOQ,
            EhrTransferServiceSmallEhrOQ ehrTransferServiceSmallEhrOQ,
            EhrTransferServiceLargeEhrOQ ehrTransferServiceLargeEhrOQ,
            EhrTransferServiceNegativeAcknowledgementOQ ehrTransferServiceNegativeAcknowledgementOQ,
            EhrTransferServiceParsingDeadLetterQueue ehrTransferServiceParsingDeadLetterQueue
    ) {
        this.transferTrackerService = transferTrackerService;
        this.templatingService = templatingService;
        this.mhsInboundQueue = mhsInboundQueue;
        this.gp2gpMessengerOQ = gp2gpMessengerOQ;
        this.ehrTransferServiceTransferCompleteOQ = ehrTransferServiceTransferCompleteOQ;
        this.ehrTransferServiceUnhandledOQ = ehrTransferServiceUnhandledOQ;
        this.ehrTransferServiceLargeEhrFragmentsOQ = ehrTransferServiceLargeEhrFragmentsOQ;
        this.ehrTransferServiceSmallEhrOQ = ehrTransferServiceSmallEhrOQ;
        this.ehrTransferServiceLargeEhrOQ = ehrTransferServiceLargeEhrOQ;
        this.ehrTransferServiceNegativeAcknowledgementOQ = ehrTransferServiceNegativeAcknowledgementOQ;
        this.ehrTransferServiceParsingDeadLetterQueue = ehrTransferServiceParsingDeadLetterQueue;
    }

    @BeforeAll
    void init() {
        ehrTransferServiceSmallEhrOQ.deleteAllMessages();
        ehrTransferServiceLargeEhrOQ.deleteAllMessages();
        ehrTransferServiceLargeEhrFragmentsOQ.deleteAllMessages();
        ehrTransferServiceParsingDeadLetterQueue.deleteAllMessages();
        ehrTransferServiceTransferCompleteOQ.deleteAllMessages();
        ehrTransferServiceUnhandledOQ.deleteAllMessages();
        ehrTransferServiceNegativeAcknowledgementOQ.deleteAllMessages();
        gp2gpMessengerOQ.deleteAllMessages();
    }

    @BeforeEach
    void beforeEach(TestInfo testInfo) {
        TestConstants.generateTestConstants(testInfo.getDisplayName());
    }

    @AfterEach
    void afterEach() throws InterruptedException {
        transferTrackerService.clearConversation(inboundConversationId);
        gp2gpMessengerOQ.deleteAllMessages();
        Thread.sleep(1000);
    }

    final String knownNackTypeCode = "typeCode=\\\"AR\\\"";
    final String unknownNackTypeCode = "typeCode=\\\"AE\\\"";

    private void assertNackMessageReceived(String nackCode){
        //find message with the outbound ID
        final SqsMessage outboundMessage = gp2gpMessengerOQ.getMessageContaining(outboundConversationId);
        log.info(outboundMessage.getBody());

        //assert the error code is correct
        assertTrue(outboundMessage.contains("code=\\\"" + nackCode + "\\\""));

        //assert the type code is correct dependent on error code
        if("99".equals(nackCode)){
            assertTrue(outboundMessage.contains(unknownNackTypeCode));
        }
        else {
            assertTrue(outboundMessage.contains(knownNackTypeCode));
        }
    }

    @Test
    void shouldSend06NackWhenNHSNumberNotFound() {
        final String nonExistentNhsNumber = "9729999999";

        //Given we have a message with an unrecognised NHS number
        EhrRequestTemplateContext ehrRequestContext = EhrRequestTemplateContext.builder()
                .outboundConversationId(outboundConversationId)
                .nhsNumber(nonExistentNhsNumber)
                .senderOdsCode(senderOdsCode)
                .build();

        String erroneousInboundMessage = templatingService.getTemplatedString(EHR_REQUEST, ehrRequestContext);

        // When the message is received via the mhsInboundQueue
        mhsInboundQueue.sendMessage(erroneousInboundMessage, outboundConversationId);

        assertNackMessageReceived("06");
    }

    @ParameterizedTest(name = "[Should send NACK 06 when transfer status is {0}")
    @EnumSource(value = ConversationTransferStatus.class, names = {"INBOUND_FAILED", "INBOUND_REQUEST_SENT", "INBOUND_TIMEOUT"})
    void shouldSend06NackWhenIncompleteInbound(ConversationTransferStatus transferStatus) {
        final String nhsNumber = PATIENT_WITH_SMALL_EHR_IN_REPO_AND_MOF_SET_TO_TPP.nhsNumber();

        //set up an 'inbound' EHR with an unsuccessful status
        transferTrackerService.saveConversation(ConversationRecord.builder()
                .inboundConversationId(inboundConversationId)
                .nhsNumber(nhsNumber)
                .transferStatus(transferStatus.name())
                .nemsMessageId(nemsMessageId)
                .sourceGp(senderOdsCode)
                .associatedTest(testName)
                .build()
        );

        // When an EHR out request is received
        EhrRequestTemplateContext ehrRequestTemplateContext = EhrRequestTemplateContext.builder()
                .outboundConversationId(outboundConversationId)
                .nhsNumber(nhsNumber)
                .senderOdsCode(senderOdsCode)
                .asidCode(asidCode)
                .build();
        String ehrRequestMessage = templatingService.getTemplatedString(EHR_REQUEST, ehrRequestTemplateContext);

        // Add EHR out request to mhsInboundQueue
        mhsInboundQueue.sendMessage(ehrRequestMessage, outboundConversationId);
        log.info("added EHR OUT request to mhsInboundQueue");

        assertNackMessageReceived("06");
    }

    @Test
    void shouldSend10NackWhenEHRNotFound() {
        final String nhsNumber = PATIENT_WITH_SMALL_EHR_IN_REPO_AND_MOF_SET_TO_TPP.nhsNumber();

        //set up a complete inbound transfer
        transferTrackerService.saveConversation(ConversationRecord.builder()
                .inboundConversationId(inboundConversationId)
                .nhsNumber(nhsNumber)
                .transferStatus(INBOUND_COMPLETE.name())
                .nemsMessageId(nemsMessageId)
                .sourceGp(senderOdsCode)
                .associatedTest(testName)
                .build()
        );

        //do not transfer the EHR, continue to set up the outbound request
        EhrRequestTemplateContext ehrRequestContext = EhrRequestTemplateContext.builder()
                .outboundConversationId(outboundConversationId)
                .nhsNumber(nhsNumber)
                .senderOdsCode(senderOdsCode)
                .build();

        String inboundMessage = templatingService.getTemplatedString(EHR_REQUEST, ehrRequestContext);

        // When the message is received via the mhsInboundQueue
        mhsInboundQueue.sendMessage(inboundMessage, outboundConversationId);

        assertNackMessageReceived("10");
    }

    @Test
    void shouldSend10NackWhenEHRNotFoundWithInboundId(){
        final String nhsNumber = PATIENT_WITH_SMALL_EHR_IN_REPO_AND_MOF_SET_TO_TPP.nhsNumber();
        final String senderOdsCode = TPP_PTL_INT.odsCode();
        final String asidCode = TPP_PTL_INT.asidCode();

        log.info("nhsNumber: " + nhsNumber);
        log.info("senderOdsCode: " + senderOdsCode);
        log.info("asidCode: " + asidCode);

        /*
        ORC-IN
         */

        // Given a small EHR is transferred into the repository
        // create entry in transfer tracker db with status INBOUND_REQUEST_SENT
        transferTrackerService.saveConversation(ConversationRecord.builder()
                .inboundConversationId(inboundConversationId)
                .nemsMessageId(nemsMessageId)
                .nhsNumber(nhsNumber)
                .sourceGp(senderOdsCode)
                .transferStatus(INBOUND_REQUEST_SENT.name())
                .associatedTest(testName)
                .build());

        // Construct small EHR message
        SmallEhrTemplateContext smallEhrTemplateContext = SmallEhrTemplateContext.builder()
                .inboundConversationId(inboundConversationId)
                .nhsNumber(nhsNumber)
                .build();

        String smallEhrMessage = this.templatingService.getTemplatedString(TemplateVariant.SMALL_EHR_WITHOUT_LINEBREAKS, smallEhrTemplateContext);

        // Put the patient EHR onto the mhsInboundQueue
        mhsInboundQueue.sendMessage(smallEhrMessage, inboundConversationId);

        // Wait until the patient EHR is successfully transferred to the repository
        log.info("conversationIdExists: {}", transferTrackerService.inboundConversationIdExists(inboundConversationId));
        String status = transferTrackerService.waitForConversationTransferStatusMatching(inboundConversationId, INBOUND_COMPLETE.name());
        log.info("tracker db status: {}", status);

        //edit the core inbound message ID so that the EHR cannot be found in S3
        transferTrackerService.editCoreInboundMessageId(inboundConversationId);

        // When an EHR OUT request is received

        // Construct an EHR request
        EhrRequestTemplateContext ehrRequestTemplateContext = EhrRequestTemplateContext.builder()
                .outboundConversationId(outboundConversationId)
                .nhsNumber(nhsNumber)
                .senderOdsCode(senderOdsCode)
                .asidCode(asidCode)
                .build();
        String ehrRequestMessage = this.templatingService.getTemplatedString(EHR_REQUEST, ehrRequestTemplateContext);

        // Add EHR request to mhsInboundQueue
        mhsInboundQueue.sendMessage(ehrRequestMessage, outboundConversationId);

        // Assert that a NACK 10 is sent out
        assertNackMessageReceived("10");

    }

    @Test
    void shouldSend10NackWhenCoreNotFound(){
        final String nhsNumber = PATIENT_WITH_SMALL_EHR_IN_REPO_AND_MOF_SET_TO_TPP.nhsNumber();
        final String senderOdsCode = TPP_PTL_INT.odsCode();
        final String asidCode = TPP_PTL_INT.asidCode();

        log.info("nhsNumber: " + nhsNumber);
        log.info("senderOdsCode: " + senderOdsCode);
        log.info("asidCode: " + asidCode);

        /*
        ORC-IN
         */

        // Given a small EHR is transferred into the repository
        // create entry in transfer tracker db with status INBOUND_REQUEST_SENT
        transferTrackerService.saveConversation(ConversationRecord.builder()
                .inboundConversationId(inboundConversationId)
                .nemsMessageId(nemsMessageId)
                .nhsNumber(nhsNumber)
                .sourceGp(senderOdsCode)
                .transferStatus(INBOUND_REQUEST_SENT.name())
                .associatedTest(testName)
                .build());

        // Construct small EHR message
        SmallEhrTemplateContext smallEhrTemplateContext = SmallEhrTemplateContext.builder()
                .inboundConversationId(inboundConversationId)
                .nhsNumber(nhsNumber)
                .build();

        String smallEhrMessage = this.templatingService.getTemplatedString(TemplateVariant.SMALL_EHR_WITHOUT_LINEBREAKS, smallEhrTemplateContext);

        // Put the patient EHR onto the mhsInboundQueue
        mhsInboundQueue.sendMessage(smallEhrMessage, inboundConversationId);

        // Wait until the patient EHR is successfully transferred to the repository
        log.info("conversationIdExists: {}", transferTrackerService.inboundConversationIdExists(inboundConversationId));
        String status = transferTrackerService.waitForConversationTransferStatusMatching(inboundConversationId, INBOUND_COMPLETE.name());
        log.info("tracker db status: {}", status);

        //delete the EHR core
        transferTrackerService.hardDeleteCore(inboundConversationId);

        // When an EHR OUT request is received

        // Construct an EHR request
        EhrRequestTemplateContext ehrRequestTemplateContext = EhrRequestTemplateContext.builder()
                .outboundConversationId(outboundConversationId)
                .nhsNumber(nhsNumber)
                .senderOdsCode(senderOdsCode)
                .asidCode(asidCode)
                .build();
        String ehrRequestMessage = this.templatingService.getTemplatedString(EHR_REQUEST, ehrRequestTemplateContext);

        // Add EHR request to mhsInboundQueue
        mhsInboundQueue.sendMessage(ehrRequestMessage, outboundConversationId);

        // Assert that a NACK 10 is sent out
        assertNackMessageReceived("10");
    }
}
