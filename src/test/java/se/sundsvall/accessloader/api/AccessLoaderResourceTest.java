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

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("junit")
class AccessLoaderResourceTest {

	private static final String MUNICIPALITY_ID = "2281";
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
	void loadManagers() {
		webTestClient.post()
			.uri(builder -> builder.path(PATH)
				.queryParam("orgIds", 1, 2, 3)
				.build(Map.of("municipalityId", MUNICIPALITY_ID)))
			.exchange()
			.expectStatus().isOk();

		verify(accessLoaderService).resolveManagerHierarchy(MUNICIPALITY_ID, 1);
		verify(accessLoaderService).resolveManagerHierarchy(MUNICIPALITY_ID, 2);
		verify(accessLoaderService).resolveManagerHierarchy(MUNICIPALITY_ID, 3);
		verifyNoMoreInteractions(accessLoaderService);
	}
}
