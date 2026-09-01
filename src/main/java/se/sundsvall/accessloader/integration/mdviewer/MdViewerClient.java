package se.sundsvall.accessloader.integration.mdviewer;

import generated.se.sundsvall.mdviewer.MDVEmployee;
import generated.se.sundsvall.mdviewer.OrganizationTree;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import se.sundsvall.accessloader.integration.mdviewer.configuration.MdViewerConfiguration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.accessloader.integration.mdviewer.configuration.MdViewerConfiguration.CLIENT_ID;

@FeignClient(
	name = CLIENT_ID,
	configuration = MdViewerConfiguration.class,
	url = "${integration.mdviewer.url}")
@CircuitBreaker(name = CLIENT_ID)
public interface MdViewerClient {

	@GetMapping(path = "/{orgId}/orgtree", produces = APPLICATION_JSON_VALUE)
	OrganizationTree getOrgTree(
		@PathVariable("orgId") final int orgId,
		@RequestParam(value = "view", required = false) final Integer view);

	@GetMapping(path = "/{orgId}/employees", produces = APPLICATION_JSON_VALUE)
	List<MDVEmployee> getEmployees(@PathVariable("orgId") final int orgId);

	@GetMapping(path = "/{personId}/employeedetails", produces = APPLICATION_JSON_VALUE)
	MDVEmployee getEmployeeDetails(@PathVariable("personId") final String personId);
}
