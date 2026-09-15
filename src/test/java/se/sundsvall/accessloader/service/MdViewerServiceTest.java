package se.sundsvall.accessloader.service;

import generated.se.sundsvall.mdviewer.MDVEmployee;
import generated.se.sundsvall.mdviewer.OrganizationTree;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.accessloader.integration.mdviewer.MdViewerClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MdViewerServiceTest {

	@Mock
	private MdViewerClient mdViewerClientMock;

	@InjectMocks
	private MdViewerService mdViewerService;

	@Test
	void getOrgTree() {
		final var orgId = 123;
		final var organizationTree = new OrganizationTree();

		when(mdViewerClientMock.getOrgTree(orgId, null)).thenReturn(organizationTree);

		final var result = mdViewerService.getOrgTree(orgId);

		assertThat(result).isSameAs(organizationTree);
		verify(mdViewerClientMock).getOrgTree(orgId, null);
	}

	@Test
	void getPersonIds() {
		final var orgId = 456;
		final var personId1 = UUID.randomUUID();
		final var personId2 = UUID.randomUUID();
		final var employees = List.of(
			new MDVEmployee().personId(personId1),
			new MDVEmployee().personId(null),
			new MDVEmployee().personId(personId2));

		when(mdViewerClientMock.getEmployees(orgId)).thenReturn(employees);

		final var result = mdViewerService.getPersonIds(orgId);

		assertThat(result).containsExactly(personId1.toString(), personId2.toString());
		verify(mdViewerClientMock).getEmployees(orgId);
	}
}
