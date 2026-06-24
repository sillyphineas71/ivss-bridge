package vn.attendance.bridge.forward;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import vn.attendance.bridge.dto.FaceEventDto;

import java.util.concurrent.CompletableFuture;

/**
 * Forwards face events to the NestJS capstone-be webhook.
 * Fire-and-forget (best-effort) so a slow/down NestJS never blocks the SDK callback thread.
 * NestJS authenticates the call via the shared X-Internal-Token (InternalTokenGuard pattern).
 */
@Service
public class NestForwarder {

    @Value("${nestjs.webhook-url}") private String webhookUrl;
    @Value("${nestjs.api-key}")     private String apiKey;

    @Autowired private RestTemplate restTemplate;

    public void forwardAsync(FaceEventDto event) {
        CompletableFuture.runAsync(() -> {
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                if (apiKey != null && !apiKey.isEmpty()) {
                    headers.set("X-Internal-Token", apiKey);
                }
                restTemplate.postForEntity(webhookUrl, new HttpEntity<>(event, headers), String.class);
            } catch (Exception ex) {
                // best-effort: log and drop. NestJS can also poll AI Report as a fallback if needed.
                System.err.println("[NestForwarder] forward failed (" + event.getType() + "): " + ex.getMessage());
            }
        });
    }
}
