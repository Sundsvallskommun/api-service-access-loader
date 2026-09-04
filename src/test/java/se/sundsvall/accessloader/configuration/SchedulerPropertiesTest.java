package se.sundsvall.accessloader.configuration;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.accessloader.configuration.SchedulerProperties.MunicipalityConfig;
import se.sundsvall.accessloader.configuration.SchedulerProperties.NamespaceConfig;

import static org.assertj.core.api.Assertions.assertThat;

class SchedulerPropertiesTest {

	@Test
	void testRecordProperties() {
		final var namespaceConfig = new NamespaceConfig(List.of(1, 2), "R");
		final var municipalityConfig = new MunicipalityConfig(Map.of("MY_NAMESPACE", namespaceConfig));
		final var properties = new SchedulerProperties(Map.of("2281", municipalityConfig));

		assertThat(properties.municipalities()).hasSize(1);
		assertThat(properties.municipalities().get("2281").namespaces()).hasSize(1);

		final var ns = properties.municipalities().get("2281").namespaces().get("MY_NAMESPACE");
		assertThat(ns.orgIds()).containsExactly(1, 2);
		assertThat(ns.accessLevel()).isEqualTo("R");
	}

	@Test
	void testNamespaceConfig() {
		final var config = new NamespaceConfig(List.of(3), "RW");

		assertThat(config.orgIds()).containsExactly(3);
		assertThat(config.accessLevel()).isEqualTo("RW");
	}

	@Test
	void testMunicipalityConfig() {
		final var namespaceConfig = new NamespaceConfig(List.of(1), "LR");
		final var config = new MunicipalityConfig(Map.of("NS", namespaceConfig));

		assertThat(config.namespaces()).hasSize(1);
		assertThat(config.namespaces()).containsEntry("NS", namespaceConfig);
	}
}
