package se.sundsvall.accessloader.configuration;

import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("scheduler.subscriberloader")
public record SubscriberLoaderProperties(Map<String, MunicipalityConfig> municipalities) {

	public record MunicipalityConfig(Map<String, NamespaceConfig> namespaces) {
	}

	/**
	 * @param roleProfiles allowlist of the roles to sync. Maps a role name (the access pattern of an
	 *                     access-mapper access of type "role") to the name of the SupportManagement
	 *                     subscription profile that users holding the role are members of. Roles and
	 *                     profiles not listed here are never touched
	 */
	public record NamespaceConfig(Map<String, String> roleProfiles) {
	}
}
