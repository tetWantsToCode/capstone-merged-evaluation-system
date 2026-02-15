package group9.advisor_eval_system.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GoogleLinkStatusResponse {
    @JsonIgnore
    private boolean isLinked;
    private String googleEmail;
    private String message;

    @JsonProperty("isLinked")
    public boolean getIsLinked() {
        return isLinked;
    }

    @JsonProperty("isLinked")
    public void setIsLinked(boolean isLinked) {
        this.isLinked = isLinked;
    }
}
