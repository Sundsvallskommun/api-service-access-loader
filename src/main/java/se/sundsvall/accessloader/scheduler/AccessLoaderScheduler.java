package se.sundsvall.accessloader.scheduler;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.accessloader.configuration.SchedulerProperties;
import se.sundsvall.accessloader.configuration.SchedulerProperties.MunicipalityConfig;
import se.sundsvall.accessloader.configuration.SchedulerProperties.NamespaceConfig;
import se.sundsvall.accessloader.service.AccessLoaderService;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

@Component
public class AccessLoaderScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(AccessLoaderScheduler.class);

	private static final String FAILED_NAMESPACES_MESSAGE = "Syncing access users failed for namespace(s): %s";

	private final AccessLoaderService accessLoaderService;
	private final SchedulerProperties schedulerProperties;

	AccessLoaderScheduler(final AccessLoaderService accessLoaderService, final SchedulerProperties schedulerProperties) {
		this.accessLoaderService = accessLoaderService;
		this.schedulerProperties = schedulerProperties;
	}

	/**
	 * Syncs every configured namespace. A failing namespace does not stop the others, but the run fails once all have been
	 * synced, so that the scheduler health reports the failure.
	 */
	@Dept44Scheduled(
		cron = "${scheduler.accessloader.cron}",
		name = "${scheduler.accessloader.name}",
		lockAtMostFor = "${scheduler.accessloader.lock-at-most-for}",
		maximumExecutionTime = "${scheduler.accessloader.maximum-execution-time}")
	public void execute() {
		LOG.info("Starting access loader job");

		final var failedNamespaces = new ArrayList<String>();

		Optional.ofNullable(schedulerProperties.municipalities()).orElse(Map.of()).forEach((municipalityId, municipalityConfig) -> {
			LOG.info("Processing municipalityId: {}", municipalityId);

			Optional.ofNullable(municipalityConfig).map(MunicipalityConfig::namespaces).orElse(Map.of()).forEach((namespace, namespaceConfig) -> {
				// A namespace without orgIds is skipped rather than synced with none, which would remove its automatic users
				final var orgIds = Optional.ofNullable(namespaceConfig).map(NamespaceConfig::orgIds).orElse(List.of());
				if (orgIds.isEmpty()) {
					LOG.warn("No orgIds configured for municipalityId: {}, namespace: {}", municipalityId, namespace);
					return;
				}

				try {
					RequestId.init();
					LOG.info("Syncing access users for municipalityId: {}, namespace: {}", municipalityId, namespace);
					accessLoaderService.syncAccessUsers(municipalityId, namespace, orgIds);
				} catch (final Exception e) {
					LOG.error("Error syncing access users for municipalityId: {}, namespace: {}", municipalityId, namespace, e);
					failedNamespaces.add(municipalityId + "/" + namespace);
				} finally {
					RequestId.reset();
				}
			});
		});

		if (!failedNamespaces.isEmpty()) {
			throw new IllegalStateException(FAILED_NAMESPACES_MESSAGE.formatted(String.join(", ", failedNamespaces)));
		}

		LOG.info("Access loader job completed");
	}
}
