package se.sundsvall.accessloader.integration.accessmapper;

import generated.se.sundsvall.accessmapper.AccessUser;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import se.sundsvall.accessloader.integration.accessmapper.configuration.AccessMapperConfiguration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.accessloader.integration.accessmapper.configuration.AccessMapperConfiguration.CLIENT_ID;

@FeignClient(
	name = CLIENT_ID,
	configuration = AccessMapperConfiguration.class,
	url = "${integration.access-mapper.url}")
@CircuitBreaker(name = CLIENT_ID)
public interface AccessMapperClient {

	@GetMapping(path = "/{municipalityId}/{namespace}/access-config/user", produces = APPLICATION_JSON_VALUE)
	List<AccessUser> getAccessUsers(
		@PathVariable("municipalityId") final String municipalityId,
		@PathVariable("namespace") final String namespace,
		@RequestParam(value = "origin", required = false) final String origin);

	@PostMapping(path = "/{municipalityId}/{namespace}/access-config/user", consumes = APPLICATION_JSON_VALUE)
	void createAccessUser(
		@PathVariable("municipalityId") final String municipalityId,
		@PathVariable("namespace") final String namespace,
		@RequestBody final AccessUser accessUser);

	@PutMapping(path = "/{municipalityId}/{namespace}/access-config/user/{id}", consumes = APPLICATION_JSON_VALUE)
	void updateAccessUser(
		@PathVariable("municipalityId") final String municipalityId,
		@PathVariable("namespace") final String namespace,
		@PathVariable("id") final String id,
		@RequestBody final AccessUser accessUser);

	@DeleteMapping(path = "/{municipalityId}/{namespace}/access-config/user/{id}")
	void deleteAccessUser(
		@PathVariable("municipalityId") final String municipalityId,
		@PathVariable("namespace") final String namespace,
		@PathVariable("id") final String id);
}
