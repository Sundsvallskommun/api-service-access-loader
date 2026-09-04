package se.sundsvall.accessloader.scheduler;

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

import static org.mockito.Mockito.verify;
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
		final var ns1 = new NamespaceConfig(orgIds1, "R");
		final var ns2 = new NamespaceConfig(orgIds2, "RW");

		when(schedulerProperties.municipalities()).thenReturn(Map.of(
			"2281", new MunicipalityConfig(Map.of("NS_A", ns1)),
			"2262", new MunicipalityConfig(Map.of("NS_B", ns2))));

		scheduler.execute();

		verify(schedulerProperties).municipalities();
		verify(accessLoaderService).syncAccessUsers("2281", "NS_A", orgIds1, "R");
		verify(accessLoaderService).syncAccessUsers("2262", "NS_B", orgIds2, "RW");
		verifyNoMoreInteractions(accessLoaderService, schedulerProperties);
	}
}
