package se.sundsvall.accessloader.integration.supportmanagement.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param connectTimeout connect timeout in seconds
 * @param readTimeout    read timeout in seconds
 * @param sentBy         the AD account the service acts as towards SupportManagement, sent in the X-Sent-By header when
 *                       no calling user is present. Set as a secret per environment, not in application.yml. Optional,
 *                       so that a missing secret does not stop the service from starting - SupportManagement then
 *                       rejects the calls and the subscriber loader reports the failure in its health
 */
@ConfigurationProperties("integration.support-management")
public record SupportManagementProperties(int connectTimeout, int readTimeout, String sentBy) {
}
