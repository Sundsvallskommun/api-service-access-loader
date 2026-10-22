package se.sundsvall.accessloader.scheduler;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.accessloader.configuration.SubscriberLoaderProperties;
import se.sundsvall.accessloader.configuration.SubscriberLoaderProperties.MunicipalityConfig;
import se.sundsvall.accessloader.configuration.SubscriberLoaderProperties.NamespaceConfig;
import se.sundsvall.accessloader.service.SubscriberLoaderService;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriberLoaderSchedulerTest {

	@Mock
	private SubscriberLoaderService subscriberLoaderService;

	@Mock
	private SubscriberLoaderProperties subscriberLoaderProperties;

	@InjectMocks
	private SubscriberLoaderScheduler scheduler;

	@Test
	void execute() {
		final var roleProfiles = Map.of("ENHETSCHEF", "Chefer");

		when(subscriberLoaderProperties.municipalities()).thenReturn(Map.of(
			"2281", new MunicipalityConfig(Map.of("NS_A", new NamespaceConfig(roleProfiles))),
			"2262", new MunicipalityConfig(Map.of("NS_B", new NamespaceConfig(null)))));

		scheduler.execute();

		verify(subscriberLoaderProperties).municipalities();
		verify(subscriberLoaderService).syncSubscribers("2281", "NS_A", roleProfiles);
		verify(subscriberLoaderService).syncSubscribers("2262", "NS_B", Map.of());
		verifyNoMoreInteractions(subscriberLoaderService, subscriberLoaderProperties);
	}

	@Test
	void executeContinuesWhenNamespaceFails() {
		final var roleProfilesA = Map.of("ENHETSCHEF", "Chefer");
		final var roleProfilesB = Map.of("HANDLAGGARE", "Handläggare");

		when(subscriberLoaderProperties.municipalities()).thenReturn(Map.of(
			"2281", new MunicipalityConfig(Map.of("NS_A", new NamespaceConfig(roleProfilesA))),
			"2262", new MunicipalityConfig(Map.of("NS_B", new NamespaceConfig(roleProfilesB)))));
		lenient().doThrow(new RuntimeException("error")).when(subscriberLoaderService).syncSubscribers("2281", "NS_A", roleProfilesA);

		scheduler.execute();

		verify(subscriberLoaderService).syncSubscribers("2281", "NS_A", roleProfilesA);
		verify(subscriberLoaderService).syncSubscribers("2262", "NS_B", roleProfilesB);
		verifyNoMoreInteractions(subscriberLoaderService);
	}

	@Test
	void executeWithoutMunicipalities() {
		when(subscriberLoaderProperties.municipalities()).thenReturn(null);

		scheduler.execute();

		verify(subscriberLoaderProperties).municipalities();
		verifyNoInteractions(subscriberLoaderService);
	}
}
