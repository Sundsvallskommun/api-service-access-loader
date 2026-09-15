package se.sundsvall.accessloader.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import se.sundsvall.accessloader.scheduler.AccessLoaderScheduler;

import static org.springframework.http.ResponseEntity.noContent;

@RestController
@Tag(name = "Access Loader", description = "Triggers the access loader sync job")
class AccessLoaderResource {

	private final AccessLoaderScheduler accessLoaderScheduler;

	AccessLoaderResource(final AccessLoaderScheduler accessLoaderScheduler) {
		this.accessLoaderScheduler = accessLoaderScheduler;
	}

	@PostMapping("/sync")
	@Operation(summary = "Trigger access loader sync", description = "Triggers the access loader sync job manually")
	@ApiResponse(responseCode = "204", description = "Sync triggered successfully")
	ResponseEntity<Void> sync() {
		accessLoaderScheduler.execute();
		return noContent().build();
	}
}
