package searchengine.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class Response {
    private boolean result;
    private String error;

    public Response(boolean result) {
        this.result = result;
        this.error = null;
    }
}
