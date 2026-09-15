package se.sundsvall.accessloader.api;

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
import se.sundsvall.accessloader.scheduler.AccessLoaderScheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("junit")
class AccessLoaderResourceTest {

	private static final String SYNC_PATH = "/sync";

	@MockitoBean
	private AccessLoaderScheduler accessLoaderScheduler;

	@MockitoBean
	private AccessMapperClient accessMapperClient;

	@MockitoBean
	private MdViewerClient mdViewerClient;

	@MockitoBean
	private EmployeeClient employeeClient;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void sync() {
		webTestClient.post()
			.uri(SYNC_PATH)
			.exchange()
			.expectStatus().isNoContent();

		verify(accessLoaderScheduler).execute();
		verifyNoMoreInteractions(accessLoaderScheduler);
	}
}
