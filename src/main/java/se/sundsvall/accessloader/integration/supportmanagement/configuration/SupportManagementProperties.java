package se.sundsvall.accessloader.integration.supportmanagement.configuration;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * @param connectTimeout connect timeout in seconds
 * @param readTimeout    read timeout in seconds
 * @param sentBy         the AD account the service acts as towards SupportManagement, sent in the X-Sent-By header.
 *                       Set as a secret per environment, not in application.yml
 */
@Validated
@ConfigurationProperties("integration.support-management")
public record SupportManagementProperties(int connectTimeout, int readTimeout, @NotBlank String sentBy) {
}
