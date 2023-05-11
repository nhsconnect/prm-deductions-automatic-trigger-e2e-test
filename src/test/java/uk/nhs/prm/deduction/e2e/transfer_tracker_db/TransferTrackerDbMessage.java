package uk.nhs.prm.deduction.e2e.transfer_tracker_db;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.gson.GsonBuilder;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@Builder(toBuilder = true)
@JsonIgnoreProperties(ignoreUnknown = true)
public class TransferTrackerDbMessage {

    private String conversationId;
    private String largeEhrCoreMessageId;
    private String nemsMessageId;
    private String nhsNumber;
    private String sourceGp;
    private String state;
    private String nemsEventLastUpdated;
    private String createdAt;
    private String lastUpdatedAt;

    public TransferTrackerDbMessage(@JsonProperty("conversationId") String conversationId,
                                    @JsonProperty("largeEhrCoreMessageId") String largeEhrCoreMessageId,
                                    @JsonProperty("nemsMessageId") String nemsMessageId,
                                    @JsonProperty("nhsNumber") String nhsNumber,
                                    @JsonProperty("sourceGp") String sourceGp,
                                    @JsonProperty("state") String state,
                                    @JsonProperty("nemsEventLastUpdated") String nemsEventLastUpdated,
                                    @JsonProperty("createdAt") String createdAt,
                                    @JsonProperty("lastUpdatedAt") String lastUpdatedAt){
        this.conversationId = conversationId;
        this.largeEhrCoreMessageId = largeEhrCoreMessageId;
        this.nemsMessageId = nemsMessageId;
        this.nhsNumber = nhsNumber;
        this.sourceGp = sourceGp;
        this.state = state;
        this.nemsEventLastUpdated = nemsEventLastUpdated;
        this.createdAt = createdAt;
        this.lastUpdatedAt = lastUpdatedAt;
    }

    public String toJsonString() {
        return new GsonBuilder().disableHtmlEscaping().create().toJson(this);
    }
}
