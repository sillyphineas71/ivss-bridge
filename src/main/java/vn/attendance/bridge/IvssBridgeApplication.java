package vn.attendance.bridge;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestTemplate;

/**
 * IVSS Bridge — a thin Dahua NetSDK sidecar for SMRMPTS.
 *
 * Responsibilities (and ONLY these — all domain logic lives in capstone-be/NestJS):
 *   1) Hold a NetSDK login session to the IVSS (port 37777, native SDK, not HTTP).
 *   2) Expose a small REST control API so NestJS can sync faces + query cameras/status.
 *   3) Subscribe to IVSS face-recognition events and forward them to NestJS as JSON.
 *
 * Stateless: no database. Everything persistent is owned by NestJS.
 *
 * DataSourceAutoConfiguration is excluded because we deliberately pulled out the
 * MySQL/JPA layer from the original sep490_ams_be project.
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
public class IvssBridgeApplication {

    public static void main(String[] args) {
        SpringApplication.run(IvssBridgeApplication.class, args);
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }
}
