package uk.nhs.prm.e2etests.model.templatecontext;

import jakarta.validation.constraints.Pattern;
import lombok.Builder;
import lombok.Getter;

import static uk.nhs.prm.e2etests.utility.ValidationUtility.ODS_CODE_REGEX;
import static uk.nhs.prm.e2etests.utility.ValidationUtility.UUID_REGEX;

@Getter
@Builder
public class LargeEhrFragmentOneContext implements TemplateContext {
    @Pattern(regexp = UUID_REGEX, message = "An invalid Inbound Conversation ID was provided.")
    private String inboundConversationId;
    @Pattern(regexp = UUID_REGEX, message = "An invalid Fragment Message ID was provided.")
    private String fragmentMessageId;
    @Pattern(regexp = UUID_REGEX, message = "An invalid Fragment Two Message ID was provided.")
    private String fragmentTwoMessageId;
    @Pattern(regexp = ODS_CODE_REGEX, message = )
    private String recipientOdsCode;
    private String senderOdsCode;
}