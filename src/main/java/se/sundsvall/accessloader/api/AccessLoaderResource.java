package se.sundsvall.accessloader.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.sundsvall.accessloader.service.AccessLoaderService;
import se.sundsvall.accessloader.service.model.ManagerWithInheritedPaths;
import se.sundsvall.dept44.common.validators.annotation.ValidMunicipalityId;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.http.MediaType.APPLICATION_PROBLEM_JSON_VALUE;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@Validated
@RequestMapping("/{municipalityId}/access-loader")
@Tag(name = "Access Loader", description = "Access loader operations")
@ApiResponses(value = {
	@ApiResponse(responseCode = "400", description = "Bad request", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(oneOf = {
		Problem.class, ConstraintViolationProblem.class
	}))),
	@ApiResponse(responseCode = "500", description = "Internal server error", content = @Content(mediaType = APPLICATION_PROBLEM_JSON_VALUE, schema = @Schema(implementation = Problem.class)))
})
class AccessLoaderResource {

	private final AccessLoaderService accessLoaderService;

	AccessLoaderResource(final AccessLoaderService accessLoaderService) {
		this.accessLoaderService = accessLoaderService;
	}

	@PostMapping(produces = APPLICATION_JSON_VALUE)
	@Operation(summary = "Trigger access loader job", description = "Triggers the access loader job for the given municipality and org IDs")
	@ApiResponse(responseCode = "200", description = "Successful operation")
	ResponseEntity<Map<UUID, ManagerWithInheritedPaths>> loadManagers(
		@PathVariable @ValidMunicipalityId final String municipalityId,
		@RequestParam final List<Integer> orgIds) {

		final var result = new LinkedHashMap<UUID, ManagerWithInheritedPaths>();
		orgIds.forEach(orgId -> result.putAll(accessLoaderService.resolveManagerHierarchy(municipalityId, orgId)));

		return ok(result);
	}
}
