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
import se.sundsvall.accessloader.integration.supportmanagement.SupportManagementClient;
import se.sundsvall.accessloader.scheduler.SubscriberLoaderScheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("junit")
class SubscriberLoaderResourceTest {

	private static final String SYNC_SUBSCRIBERS_PATH = "/sync-subscribers";

	@MockitoBean
	private SubscriberLoaderScheduler subscriberLoaderScheduler;

	@MockitoBean
	private AccessMapperClient accessMapperClient;

	@MockitoBean
	private MdViewerClient mdViewerClient;

	@MockitoBean
	private EmployeeClient employeeClient;

	@MockitoBean
	private SupportManagementClient supportManagementClient;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void syncSubscribers() {
		webTestClient.post()
			.uri(SYNC_SUBSCRIBERS_PATH)
			.exchange()
			.expectStatus().isNoContent();

		verify(subscriberLoaderScheduler).execute();
		verifyNoMoreInteractions(subscriberLoaderScheduler);
	}
}
