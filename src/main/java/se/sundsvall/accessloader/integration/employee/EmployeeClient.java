package se.sundsvall.accessloader.integration.employee;

import generated.se.sundsvall.employee.Employeev2;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import java.util.List;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import se.sundsvall.accessloader.integration.employee.configuration.EmployeeConfiguration;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static se.sundsvall.accessloader.integration.employee.configuration.EmployeeConfiguration.CLIENT_ID;

@FeignClient(
	name = CLIENT_ID,
	configuration = EmployeeConfiguration.class,
	url = "${integration.employee.url}")
@CircuitBreaker(name = CLIENT_ID)
public interface EmployeeClient {

	@GetMapping(path = "/{municipalityId}/employments", produces = APPLICATION_JSON_VALUE)
	List<Employeev2> getEmployments(
		@PathVariable("municipalityId") final String municipalityId,
		@RequestParam(value = "PersonId", required = false) final String personId);
}
