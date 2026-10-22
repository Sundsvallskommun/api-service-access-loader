package se.sundsvall.accessloader.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import se.sundsvall.accessloader.scheduler.SubscriberLoaderScheduler;

import static org.springframework.http.ResponseEntity.noContent;

@RestController
@Tag(name = "Subscriber Loader", description = "Triggers the subscriber loader sync job")
class SubscriberLoaderResource {

	private final SubscriberLoaderScheduler subscriberLoaderScheduler;

	SubscriberLoaderResource(final SubscriberLoaderScheduler subscriberLoaderScheduler) {
		this.subscriberLoaderScheduler = subscriberLoaderScheduler;
	}

	@PostMapping("/sync-subscribers")
	@Operation(summary = "Trigger subscriber loader sync", description = "Triggers the subscriber loader sync job manually")
	@ApiResponse(responseCode = "204", description = "Sync triggered successfully")
	ResponseEntity<Void> syncSubscribers() {
		subscriberLoaderScheduler.execute();
		return noContent().build();
	}
}
