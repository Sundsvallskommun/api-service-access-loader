package se.sundsvall.accessloader.integration.supportmanagement;

import generated.se.sundsvall.supportmanagement.Identifier;
import generated.se.sundsvall.supportmanagement.SubscriptionProfile;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import se.sundsvall.accessloader.integration.supportmanagement.configuration.SupportManagementConfiguration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.accessloader.integration.supportmanagement.configuration.SupportManagementConfiguration.CLIENT_ID;

@FeignClient(
	name = CLIENT_ID,
	configuration = SupportManagementConfiguration.class,
	url = "${integration.support-management.url}")
@CircuitBreaker(name = CLIENT_ID)
public interface SupportManagementClient {

	@GetMapping(path = "/{municipalityId}/{namespace}/subscription-profiles", produces = APPLICATION_JSON_VALUE)
	List<SubscriptionProfile> getSubscriptionProfiles(
		@PathVariable("municipalityId") final String municipalityId,
		@PathVariable("namespace") final String namespace);

	@PutMapping(path = "/{municipalityId}/{namespace}/subscription-profiles/{profileId}/members", consumes = APPLICATION_JSON_VALUE)
	void replaceSubscriptionProfileMembers(
		@PathVariable("municipalityId") final String municipalityId,
		@PathVariable("namespace") final String namespace,
		@PathVariable("profileId") final String profileId,
		@RequestBody final List<Identifier> members);
}
