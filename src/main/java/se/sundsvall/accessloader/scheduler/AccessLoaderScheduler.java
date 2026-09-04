package se.sundsvall.accessloader.scheduler;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import se.sundsvall.accessloader.configuration.SchedulerProperties;
import se.sundsvall.accessloader.service.AccessLoaderService;
import se.sundsvall.dept44.requestid.RequestId;
import se.sundsvall.dept44.scheduling.Dept44Scheduled;

@Component
class AccessLoaderScheduler {

	private static final Logger LOG = LoggerFactory.getLogger(AccessLoaderScheduler.class);

	private final AccessLoaderService accessLoaderService;
	private final SchedulerProperties schedulerProperties;

	AccessLoaderScheduler(final AccessLoaderService accessLoaderService, final SchedulerProperties schedulerProperties) {
		this.accessLoaderService = accessLoaderService;
		this.schedulerProperties = schedulerProperties;
	}

	@Dept44Scheduled(
		cron = "${scheduler.accessloader.cron}",
		name = "${scheduler.accessloader.name}",
		lockAtMostFor = "${scheduler.accessloader.lock-at-most-for}",
		maximumExecutionTime = "${scheduler.accessloader.maximum-execution-time}")
	void execute() {
		LOG.info("Starting access loader job");

		schedulerProperties.municipalities().forEach((municipalityId, municipalityConfig) -> {
			LOG.info("Processing municipalityId: {}", municipalityId);

			municipalityConfig.namespaces().forEach((namespace, namespaceConfig) -> {
				try {
					RequestId.init();
					LOG.info("Syncing access users for municipalityId: {}, namespace: {}", municipalityId, namespace);
					accessLoaderService.syncAccessUsers(municipalityId, namespace, namespaceConfig.orgIds(), namespaceConfig.accessLevel());
				} catch (final Exception e) {
					LOG.error("Error syncing access users for municipalityId: {}, namespace: {}", municipalityId, namespace, e);
				} finally {
					RequestId.reset();
				}
			});
		});

		LOG.info("Access loader job completed");
	}
}
