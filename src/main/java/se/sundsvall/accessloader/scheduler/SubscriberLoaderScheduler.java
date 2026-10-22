package se.sundsvall.accessloader.scheduler;

import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.accessloader.configuration.SubscriberLoaderProperties;
import se.sundsvall.accessloader.service.SubscriberLoaderService;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

@Component
public class SubscriberLoaderScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(SubscriberLoaderScheduler.class);

	private final SubscriberLoaderService subscriberLoaderService;
	private final SubscriberLoaderProperties subscriberLoaderProperties;

	SubscriberLoaderScheduler(final SubscriberLoaderService subscriberLoaderService, final SubscriberLoaderProperties subscriberLoaderProperties) {
		this.subscriberLoaderService = subscriberLoaderService;
		this.subscriberLoaderProperties = subscriberLoaderProperties;
	}

	@Dept44Scheduled(
		cron = "${scheduler.subscriberloader.cron}",
		name = "${scheduler.subscriberloader.name}",
		lockAtMostFor = "${scheduler.subscriberloader.lock-at-most-for}",
		maximumExecutionTime = "${scheduler.subscriberloader.maximum-execution-time}")
	public void execute() {
		LOG.info("Starting subscriber loader job");

		Optional.ofNullable(subscriberLoaderProperties.municipalities()).orElse(Map.of()).forEach((municipalityId, municipalityConfig) -> {
			LOG.info("Processing municipalityId: {}", municipalityId);

			municipalityConfig.namespaces().forEach((namespace, namespaceConfig) -> {
				try {
					RequestId.init();
					LOG.info("Syncing subscribers for municipalityId: {}, namespace: {}", municipalityId, namespace);
					subscriberLoaderService.syncSubscribers(municipalityId, namespace, Optional.ofNullable(namespaceConfig.roleProfiles()).orElse(Map.of()));
				} catch (final Exception e) {
					LOG.error("Error syncing subscribers for municipalityId: {}, namespace: {}", municipalityId, namespace, e);
				} finally {
					RequestId.reset();
				}
			});
		});

		LOG.info("Subscriber loader job completed");
	}
}
