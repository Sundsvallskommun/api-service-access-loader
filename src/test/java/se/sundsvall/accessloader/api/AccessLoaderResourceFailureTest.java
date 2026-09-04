package se.sundsvall.accessloader.api;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.sundsvall.accessloader.Application;
import se.sundsvall.accessloader.integration.accessmapper.AccessMapperClient;
import se.sundsvall.accessloader.integration.employee.EmployeeClient;
import se.sundsvall.accessloader.integration.mdviewer.MdViewerClient;
import se.sundsvall.accessloader.service.AccessLoaderService;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;
import se.sundsvall.dept44.problem.violations.Violation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("junit")
class AccessLoaderResourceFailureTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String INVALID_MUNICIPALITY_ID = "not-valid";
	private static final String PATH = "/{municipalityId}/access-loader";

	@MockitoBean
	private AccessLoaderService accessLoaderService;

	@MockitoBean
	private AccessMapperClient accessMapperClient;

	@MockitoBean
	private MdViewerClient mdViewerClient;

	@MockitoBean
	private EmployeeClient employeeClient;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void loadManagersWithInvalidMunicipalityId() {
		final var response = webTestClient.post()
			.uri(builder -> builder.path(PATH)
				.queryParam("orgIds", 1)
				.build(Map.of("municipalityId", INVALID_MUNICIPALITY_ID)))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		assertThat(response).isNotNull();
		assertThat(response.getTitle()).isEqualTo("Constraint Violation");
		assertThat(response.getStatus()).isEqualTo(BAD_REQUEST);
		assertThat(response.getViolations())
			.extracting(Violation::field, Violation::message)
			.containsExactlyInAnyOrder(tuple("loadManagers.municipalityId", "not a valid municipality ID"));

		verifyNoInteractions(accessLoaderService);
	}

	@Test
	void loadManagersWithMissingOrgIds() {
		final var response = webTestClient.post()
			.uri(builder -> builder.path(PATH)
				.build(Map.of("municipalityId", MUNICIPALITY_ID)))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		assertThat(response).isNotNull();

		verifyNoInteractions(accessLoaderService);
	}
}
