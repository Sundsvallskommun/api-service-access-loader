package se.sundsvall.accessloader.configuration;

import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.accessloader.configuration.SubscriberLoaderProperties.MunicipalityConfig;
import se.sundsvall.accessloader.configuration.SubscriberLoaderProperties.NamespaceConfig;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriberLoaderPropertiesTest {

	@Test
	void testRecordProperties() {
		final var namespaceConfig = new NamespaceConfig(Map.of("ENHETSCHEF", "Chefer"));
		final var municipalityConfig = new MunicipalityConfig(Map.of("MY_NAMESPACE", namespaceConfig));
		final var properties = new SubscriberLoaderProperties(Map.of("2281", municipalityConfig));

		assertThat(properties.municipalities()).hasSize(1);
		assertThat(properties.municipalities().get("2281").namespaces()).hasSize(1);

		final var ns = properties.municipalities().get("2281").namespaces().get("MY_NAMESPACE");
		assertThat(ns.roleProfiles()).containsExactly(Map.entry("ENHETSCHEF", "Chefer"));
	}
}
