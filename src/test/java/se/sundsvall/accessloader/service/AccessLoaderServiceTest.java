package se.sundsvall.accessloader.service;

import generated.se.sundsvall.employee.Employeev2;
import generated.se.sundsvall.employee.Employment;
import generated.se.sundsvall.mdviewer.OrganizationTree;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.accessloader.integration.employee.EmployeeClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessLoaderServiceTest {

	private static final String MUNICIPALITY_ID = "2281";

	@Mock
	private MdViewerService mdViewerServiceMock;

	@Mock
	private EmployeeClient employeeClientMock;

	@InjectMocks
	private AccessLoaderService accessLoaderService;

	@Test
	void loadPersonIdsByOrg() {
		final var rootOrgId = 1;
		final var leafOrgId1 = 100;
		final var leafOrgId2 = 200;
		final var personIds1 = List.of("person-1", "person-2");
		final var personIds2 = List.of("person-3");

		final var leafNode1 = new OrganizationTree().orgId(leafOrgId1).treeLevel(6);
		final var leafNode2 = new OrganizationTree().orgId(leafOrgId2).treeLevel(6);
		final var middleNode = new OrganizationTree().orgId(50).treeLevel(4).organizations(List.of(leafNode1, leafNode2));
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(2).organizations(List.of(middleNode));

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId1)).thenReturn(personIds1);
		when(mdViewerServiceMock.getPersonIds(leafOrgId2)).thenReturn(personIds2);

		final var result = accessLoaderService.loadPersonIdsByOrg(rootOrgId);

		assertThat(result).hasSize(2);
		assertThat(result.get(leafOrgId1)).containsExactly("person-1", "person-2");
		assertThat(result.get(leafOrgId2)).containsExactly("person-3");

		verify(mdViewerServiceMock).getOrgTree(rootOrgId);
		verify(mdViewerServiceMock).getPersonIds(leafOrgId1);
		verify(mdViewerServiceMock).getPersonIds(leafOrgId2);
		verifyNoMoreInteractions(mdViewerServiceMock);
	}

	@Test
	void loadPersonIdsByOrgSkipsEmptyPersonIds() {
		final var rootOrgId = 1;
		final var leafOrgId1 = 100;
		final var leafOrgId2 = 200;

		final var leafNode1 = new OrganizationTree().orgId(leafOrgId1).treeLevel(6);
		final var leafNode2 = new OrganizationTree().orgId(leafOrgId2).treeLevel(6);
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(2).organizations(List.of(leafNode1, leafNode2));

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId1)).thenReturn(List.of("person-1"));
		when(mdViewerServiceMock.getPersonIds(leafOrgId2)).thenReturn(List.of());

		final var result = accessLoaderService.loadPersonIdsByOrg(rootOrgId);

		assertThat(result).hasSize(1);
		assertThat(result.get(leafOrgId1)).containsExactly("person-1");
		assertThat(result).doesNotContainKey(leafOrgId2);

		verify(mdViewerServiceMock).getOrgTree(rootOrgId);
		verify(mdViewerServiceMock).getPersonIds(leafOrgId1);
		verify(mdViewerServiceMock).getPersonIds(leafOrgId2);
		verifyNoMoreInteractions(mdViewerServiceMock);
	}

	@Test
	void loadPersonIdsByOrgNoLeafNodes() {
		final var rootOrgId = 1;
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(2);

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);

		final var result = accessLoaderService.loadPersonIdsByOrg(rootOrgId);

		assertThat(result).isEmpty();

		verify(mdViewerServiceMock).getOrgTree(rootOrgId);
		verifyNoMoreInteractions(mdViewerServiceMock);
	}

	@Test
	void loadManagersByOrg() {
		final var rootOrgId = 1;
		final var middleOrgId = 50;
		final var leafOrgId = 100;
		final var personId1 = "person-1";
		final var personId2 = "person-2";

		final var leafNode = new OrganizationTree().orgId(leafOrgId).treeLevel(6);
		final var middleNode = new OrganizationTree().orgId(middleOrgId).treeLevel(4).organizations(List.of(leafNode));
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(2).organizations(List.of(middleNode));

		final var managerEmployment = new Employment().isManager(true);
		final var nonManagerEmployment = new Employment().isManager(false);
		final var manager = new Employeev2().personId(java.util.UUID.randomUUID()).employments(List.of(managerEmployment));
		final var nonManager = new Employeev2().personId(java.util.UUID.randomUUID()).employments(List.of(nonManagerEmployment));

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId)).thenReturn(List.of(personId1, personId2));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId1)).thenReturn(List.of(manager));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId2)).thenReturn(List.of(nonManager));

		final var result = accessLoaderService.loadManagersByOrg(MUNICIPALITY_ID, rootOrgId);

		assertThat(result).hasSize(1);
		assertThat(result.get(leafOrgId).orgPath()).isEqualTo("1/50/100");
		assertThat(result.get(leafOrgId).managers()).containsExactly(manager);

		verify(employeeClientMock).getEmployments(MUNICIPALITY_ID, personId1);
		verify(employeeClientMock).getEmployments(MUNICIPALITY_ID, personId2);
	}

	@Test
	void loadManagersByOrgSkipsOrgWithNoManagers() {
		final var rootOrgId = 1;
		final var leafOrgId = 100;
		final var personId = "person-1";

		final var leafNode = new OrganizationTree().orgId(leafOrgId).treeLevel(6);
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(2).organizations(List.of(leafNode));

		final var nonManager = new Employeev2().personId(java.util.UUID.randomUUID()).employments(List.of(new Employment().isManager(false)));

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId)).thenReturn(List.of(personId));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId)).thenReturn(List.of(nonManager));

		final var result = accessLoaderService.loadManagersByOrg(MUNICIPALITY_ID, rootOrgId);

		assertThat(result).isEmpty();
	}

	@Test
	void loadManagersByOrgWithNoEmployees() {
		final var rootOrgId = 1;
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(2);

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);

		final var result = accessLoaderService.loadManagersByOrg(MUNICIPALITY_ID, rootOrgId);

		assertThat(result).isEmpty();
		verifyNoInteractions(employeeClientMock);
	}
}
