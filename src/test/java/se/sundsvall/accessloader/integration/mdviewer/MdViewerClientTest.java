package se.sundsvall.accessloader.integration.mdviewer;

import generated.se.sundsvall.mdviewer.MDVEmployee;
import generated.se.sundsvall.mdviewer.OrganizationTree;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MdViewerClientTest {

	@Mock
	private MdViewerClient mdViewerClientMock;

	@Test
	void getOrgTree() {
		final var orgId = 123;
		final var view = 1;
		final var organizationTree = new OrganizationTree();

		when(mdViewerClientMock.getOrgTree(orgId, view)).thenReturn(organizationTree);

		final var result = mdViewerClientMock.getOrgTree(orgId, view);

		assertThat(result).isSameAs(organizationTree);
		verify(mdViewerClientMock).getOrgTree(orgId, view);
	}

	@Test
	void getEmployees() {
		final var orgId = 456;
		final var employees = List.of(new MDVEmployee());

		when(mdViewerClientMock.getEmployees(orgId)).thenReturn(employees);

		final var result = mdViewerClientMock.getEmployees(orgId);

		assertThat(result).isSameAs(employees);
		verify(mdViewerClientMock).getEmployees(orgId);
	}

	@Test
	void getEmployeeDetails() {
		final var personId = UUID.randomUUID().toString();
		final var employee = new MDVEmployee();

		when(mdViewerClientMock.getEmployeeDetails(personId)).thenReturn(employee);

		final var result = mdViewerClientMock.getEmployeeDetails(personId);

		assertThat(result).isSameAs(employee);
		verify(mdViewerClientMock).getEmployeeDetails(personId);
	}
}
