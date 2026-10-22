package se.sundsvall.accessloader.scheduler;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.accessloader.configuration.SubscriberLoaderProperties;
import se.sundsvall.accessloader.configuration.SubscriberLoaderProperties.MunicipalityConfig;
import se.sundsvall.accessloader.configuration.SubscriberLoaderProperties.NamespaceConfig;
import se.sundsvall.accessloader.service.SubscriberLoaderService;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

@Component
public class SubscriberLoaderScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(SubscriberLoaderScheduler.class);

	private static final String FAILED_NAMESPACES_MESSAGE = "Syncing subscribers failed for namespace(s): %s";

	private final SubscriberLoaderService subscriberLoaderService;
	private final SubscriberLoaderProperties subscriberLoaderProperties;

	SubscriberLoaderScheduler(final SubscriberLoaderService subscriberLoaderService, final SubscriberLoaderProperties subscriberLoaderProperties) {
		this.subscriberLoaderService = subscriberLoaderService;
		this.subscriberLoaderProperties = subscriberLoaderProperties;
	}

	/**
	 * Syncs every configured namespace. A failing namespace does not stop the others, but the run fails once all have been
	 * synced, so that the scheduler health reports the failure.
	 */
	@Dept44Scheduled(
		cron = "${scheduler.subscriberloader.cron}",
		name = "${scheduler.subscriberloader.name}",
		lockAtMostFor = "${scheduler.subscriberloader.lock-at-most-for}",
		maximumExecutionTime = "${scheduler.subscriberloader.maximum-execution-time}")
	public void execute() {
		LOG.info("Starting subscriber loader job");

		final var failedNamespaces = new ArrayList<String>();

		Optional.ofNullable(subscriberLoaderProperties.municipalities()).orElse(Map.of()).forEach((municipalityId, municipalityConfig) -> {
			LOG.info("Processing municipalityId: {}", municipalityId);

			Optional.ofNullable(municipalityConfig).map(MunicipalityConfig::namespaces).orElse(Map.of()).forEach((namespace, namespaceConfig) -> {
				try {
					RequestId.init();
					LOG.info("Syncing subscribers for municipalityId: {}, namespace: {}", municipalityId, namespace);
					subscriberLoaderService.syncSubscribers(municipalityId, namespace, Optional.ofNullable(namespaceConfig).map(NamespaceConfig::roleProfiles).orElse(Map.of()));
				} catch (final Exception e) {
					LOG.error("Error syncing subscribers for municipalityId: {}, namespace: {}", municipalityId, namespace, e);
					failedNamespaces.add(municipalityId + "/" + namespace);
				} finally {
					RequestId.reset();
				}
			});
		});

		if (!failedNamespaces.isEmpty()) {
			throw new IllegalStateException(FAILED_NAMESPACES_MESSAGE.formatted(String.join(", ", failedNamespaces)));
		}

		LOG.info("Subscriber loader job completed");
	}
}
