package se.sundsvall.accessloader.configuration;

import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("scheduler.accessloader")
public record SchedulerProperties(Map<String, MunicipalityConfig> municipalities) {

	public record MunicipalityConfig(Map<String, NamespaceConfig> namespaces) {
	}

	public record NamespaceConfig(List<Integer> orgIds) {
	}
}
