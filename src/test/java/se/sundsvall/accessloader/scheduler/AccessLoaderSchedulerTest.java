package se.sundsvall.accessloader.scheduler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.accessloader.configuration.SchedulerProperties;
import se.sundsvall.accessloader.configuration.SchedulerProperties.MunicipalityConfig;
import se.sundsvall.accessloader.configuration.SchedulerProperties.NamespaceConfig;
import se.sundsvall.accessloader.service.AccessLoaderService;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessLoaderSchedulerTest {

	@Mock
	private AccessLoaderService accessLoaderService;

	@Mock
	private SchedulerProperties schedulerProperties;

	@InjectMocks
	private AccessLoaderScheduler scheduler;

	@Test
	void execute() {
		final var orgIds1 = List.of(1, 2);
		final var orgIds2 = List.of(3);
		final var ns1 = new NamespaceConfig(orgIds1);
		final var ns2 = new NamespaceConfig(orgIds2);

		when(schedulerProperties.municipalities()).thenReturn(Map.of(
			"2281", new MunicipalityConfig(Map.of("NS_A", ns1)),
			"2262", new MunicipalityConfig(Map.of("NS_B", ns2))));

		scheduler.execute();

		verify(schedulerProperties).municipalities();
		verify(accessLoaderService).syncAccessUsers("2281", "NS_A", orgIds1);
		verify(accessLoaderService).syncAccessUsers("2262", "NS_B", orgIds2);
		verifyNoMoreInteractions(accessLoaderService, schedulerProperties);
	}

	@Test
	void executeContinuesWhenNamespaceFails() {
		final var orgIds1 = List.of(1, 2);
		final var orgIds2 = List.of(3);

		when(schedulerProperties.municipalities()).thenReturn(Map.of(
			"2281", new MunicipalityConfig(Map.of("NS_A", new NamespaceConfig(orgIds1))),
			"2262", new MunicipalityConfig(Map.of("NS_B", new NamespaceConfig(orgIds2)))));
		lenient().doThrow(new RuntimeException("error")).when(accessLoaderService).syncAccessUsers("2281", "NS_A", orgIds1);

		// The run fails after all namespaces are synced, so that the scheduler health reports it
		assertThatThrownBy(scheduler::execute)
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("Syncing access users failed for namespace(s): 2281/NS_A");

		verify(accessLoaderService).syncAccessUsers("2281", "NS_A", orgIds1);
		verify(accessLoaderService).syncAccessUsers("2262", "NS_B", orgIds2);
		verifyNoMoreInteractions(accessLoaderService);
	}

	@Test
	void executeWithIncompleteConfig() {
		final var orgIds = List.of(1);
		final var namespaces = new HashMap<String, NamespaceConfig>();
		namespaces.put("NS_A", new NamespaceConfig(orgIds));
		namespaces.put("NS_WITHOUT_CONFIG", null);
		namespaces.put("NS_WITHOUT_ORG_IDS", new NamespaceConfig(null));
		namespaces.put("NS_WITH_EMPTY_ORG_IDS", new NamespaceConfig(List.of()));
		final var municipalities = new HashMap<String, MunicipalityConfig>();
		municipalities.put("2281", new MunicipalityConfig(namespaces));
		municipalities.put("2262", new MunicipalityConfig(null));
		municipalities.put("2260", null);

		when(schedulerProperties.municipalities()).thenReturn(municipalities);

		scheduler.execute();

		// Only the namespace with orgIds is synced - syncing one without would remove its automatic users
		verify(schedulerProperties).municipalities();
		verify(accessLoaderService).syncAccessUsers("2281", "NS_A", orgIds);
		verifyNoMoreInteractions(accessLoaderService, schedulerProperties);
	}

	@Test
	void executeWithoutMunicipalities() {
		when(schedulerProperties.municipalities()).thenReturn(null);

		scheduler.execute();

		verify(schedulerProperties).municipalities();
		verifyNoInteractions(accessLoaderService);
	}
}
