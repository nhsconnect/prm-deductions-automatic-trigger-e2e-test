package uk.nhs.prm.deduction.e2e.tests;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import uk.nhs.prm.deduction.e2e.TestConfiguration;
import uk.nhs.prm.deduction.e2e.auth.AuthTokenGenerator;
import uk.nhs.prm.deduction.e2e.mesh.MeshClient;
import uk.nhs.prm.deduction.e2e.mesh.MeshMailbox;
import uk.nhs.prm.deduction.e2e.nems.NemsEventMessage;
import uk.nhs.prm.deduction.e2e.nems.NemsEventMessageQueue;
import uk.nhs.prm.deduction.e2e.queue.SqsQueue;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


@SpringBootTest(classes = {EndToEndTest.class,NemsEventMessageQueue.class,MeshMailbox.class, SqsQueue.class, MeshClient.class, TestConfiguration.class,AuthTokenGenerator.class})
public class EndToEndTest {


    @Autowired
    private NemsEventMessageQueue meshForwarderQueue;
    @Autowired
    private MeshMailbox meshMailbox;

    @Test
    public void theSystemShouldMoveMessagesFromOurMeshMailboxOntoAQueue() throws Exception {
        NemsEventMessage nemsEventMessage = someNemsEvent("1234567890");

        String postedMessageId = meshMailbox.postMessage(nemsEventMessage);

        await().atMost(60, TimeUnit.SECONDS).with().pollInterval(10, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(meshForwarderQueue.readEventMessage().body()).contains("1234567890");
            assertFalse(meshMailbox.hasMessageId(postedMessageId));
        });
//Todo delete messages on the queue once read
    }

    private NemsEventMessage someNemsEvent(String nhsNumber) {
        return new NemsEventMessage("dummy message for nhs number: " + nhsNumber);
    }

    public void log(String messageBody, String messageValue) {
        System.out.println(String.format(messageBody, messageValue));
    }
}
