package se.sundsvall.accessloader.service;

import generated.se.sundsvall.accessmapper.Access;
import generated.se.sundsvall.accessmapper.AccessType;
import generated.se.sundsvall.accessmapper.AccessUser;
import generated.se.sundsvall.employee.Employeev2;
import generated.se.sundsvall.employee.Employment;
import generated.se.sundsvall.employee.Manager;
import generated.se.sundsvall.mdviewer.OrganizationTree;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.accessloader.configuration.AccessLoaderProperties;
import se.sundsvall.accessloader.integration.accessmapper.AccessMapperClient;
import se.sundsvall.accessloader.integration.employee.EmployeeClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccessLoaderServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "MY_NAMESPACE";

	@Mock
	private MdViewerService mdViewerServiceMock;

	@Mock
	private EmployeeClient employeeClientMock;

	@Mock
	private AccessMapperClient accessMapperClientMock;

	@Mock
	private AccessLoaderProperties accessLoaderPropertiesMock;

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

		final var manager = new Manager().personId(UUID.randomUUID()).givenname("Boss").lastname("Person");
		final var employment = new Employment().isMainEmployment(true).manager(manager);
		final var employee = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employment));

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId)).thenReturn(List.of(personId1, personId2));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId1)).thenReturn(List.of(employee));

		final var result = accessLoaderService.loadManagersByOrg(MUNICIPALITY_ID, rootOrgId);

		assertThat(result).hasSize(1);
		assertThat(result.get(leafOrgId).orgPath()).isEqualTo("1/50/100");
		assertThat(result.get(leafOrgId).managers()).containsExactly(manager);

		verify(employeeClientMock).getEmployments(MUNICIPALITY_ID, personId1);
		verifyNoMoreInteractions(employeeClientMock);
	}

	@Test
	void loadManagersByOrgSkipsOrgWithNoManagers() {
		final var rootOrgId = 1;
		final var leafOrgId = 100;
		final var personId = "person-1";

		final var leafNode = new OrganizationTree().orgId(leafOrgId).treeLevel(6);
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(2).organizations(List.of(leafNode));

		final var employmentWithoutManager = new Employment().isMainEmployment(true).isManager(false);
		final var employee = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employmentWithoutManager));

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId)).thenReturn(List.of(personId));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId)).thenReturn(List.of(employee));

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

	@Test
	void resolveManagerHierarchy() {
		final var rootOrgId = 1;
		final var middleOrgId = 50;
		final var leafOrgId = 100;
		final var personId = "person-1";

		final var leafNode = new OrganizationTree().orgId(leafOrgId).treeLevel(6);
		final var middleNode = new OrganizationTree().orgId(middleOrgId).treeLevel(4).organizations(List.of(leafNode));
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(2).organizations(List.of(middleNode));

		final var managerId = UUID.randomUUID();
		final var manager = new Manager().personId(managerId).givenname("Boss").lastname("Person");
		final var employment = new Employment().isMainEmployment(true).manager(manager);
		final var employee = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employment));

		final var upperManagerId = UUID.randomUUID();
		final var upperManager = new Manager().personId(upperManagerId).givenname("Big").lastname("Boss");
		final var managerEmployment = new Employment().isMainEmployment(true).manager(upperManager);
		final var managerAsEmployee = new Employeev2().personId(managerId).employments(List.of(managerEmployment));

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId)).thenReturn(List.of(personId));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId)).thenReturn(List.of(employee));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerId.toString())).thenReturn(List.of(managerAsEmployee));

		final var result = accessLoaderService.resolveManagerHierarchy(MUNICIPALITY_ID, rootOrgId);

		assertThat(result).hasSize(2);
		assertThat(result.get(managerId).manager()).isEqualTo(manager);
		assertThat(result.get(managerId).paths()).containsExactly("1/50/100");
		assertThat(result.get(upperManagerId).manager()).isEqualTo(upperManager);
		assertThat(result.get(upperManagerId).paths()).containsExactly("1/50/100");

		verify(employeeClientMock).getEmployments(MUNICIPALITY_ID, personId);
		verify(employeeClientMock).getEmployments(MUNICIPALITY_ID, managerId.toString());
		verifyNoMoreInteractions(employeeClientMock);
	}

	@Test
	void resolveManagerHierarchyWithMultipleOrgsUnderSameManager() {
		final var rootOrgId = 1;
		final var leafOrgId1 = 100;
		final var leafOrgId2 = 200;
		final var personId1 = "person-1";
		final var personId2 = "person-2";

		final var leafNode1 = new OrganizationTree().orgId(leafOrgId1).treeLevel(6);
		final var leafNode2 = new OrganizationTree().orgId(leafOrgId2).treeLevel(6);
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(2).organizations(List.of(leafNode1, leafNode2));

		final var managerId = UUID.randomUUID();
		final var manager = new Manager().personId(managerId).givenname("Boss").lastname("Person");

		final var employment1 = new Employment().isMainEmployment(true).manager(manager);
		final var employee1 = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employment1));

		final var employment2 = new Employment().isMainEmployment(true).manager(manager);
		final var employee2 = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employment2));

		final var upperManagerId = UUID.randomUUID();
		final var upperManager = new Manager().personId(upperManagerId).givenname("Big").lastname("Boss");
		final var managerEmployment = new Employment().isMainEmployment(true).manager(upperManager);
		final var managerAsEmployee = new Employeev2().personId(managerId).employments(List.of(managerEmployment));

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId1)).thenReturn(List.of(personId1));
		when(mdViewerServiceMock.getPersonIds(leafOrgId2)).thenReturn(List.of(personId2));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId1)).thenReturn(List.of(employee1));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId2)).thenReturn(List.of(employee2));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerId.toString())).thenReturn(List.of(managerAsEmployee));

		final var result = accessLoaderService.resolveManagerHierarchy(MUNICIPALITY_ID, rootOrgId);

		assertThat(result).hasSize(2);
		assertThat(result.get(managerId).manager()).isEqualTo(manager);
		assertThat(result.get(managerId).paths()).containsExactly("1/100", "1/200");
		assertThat(result.get(upperManagerId).manager()).isEqualTo(upperManager);
		assertThat(result.get(upperManagerId).paths()).containsExactly("1/100", "1/200");
	}

	@Test
	void resolveManagerHierarchyWhenManagerHasNoManager() {
		final var rootOrgId = 1;
		final var leafOrgId = 100;
		final var personId = "person-1";

		final var leafNode = new OrganizationTree().orgId(leafOrgId).treeLevel(6);
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(2).organizations(List.of(leafNode));

		final var managerId = UUID.randomUUID();
		final var manager = new Manager().personId(managerId).givenname("Boss").lastname("Person");
		final var employment = new Employment().isMainEmployment(true).manager(manager);
		final var employee = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employment));

		final var managerEmployment = new Employment().isMainEmployment(true);
		final var managerAsEmployee = new Employeev2().personId(managerId).employments(List.of(managerEmployment));

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId)).thenReturn(List.of(personId));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId)).thenReturn(List.of(employee));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerId.toString())).thenReturn(List.of(managerAsEmployee));

		final var result = accessLoaderService.resolveManagerHierarchy(MUNICIPALITY_ID, rootOrgId);

		assertThat(result).hasSize(1);
		assertThat(result.get(managerId).manager()).isEqualTo(manager);
		assertThat(result.get(managerId).paths()).containsExactly("1/100");
	}

	@Test
	void resolveManagerHierarchyWhenUpperManagerIsAlsoDirectManager() {
		final var rootOrgId = 8603;
		final var leafOrgId1 = 7221;
		final var leafOrgId2 = 8615;
		final var personId1 = "person-1";
		final var personId2 = "person-2";

		final var leafNode1 = new OrganizationTree().orgId(leafOrgId1).treeLevel(6);
		final var midNode1 = new OrganizationTree().orgId(10958).treeLevel(5).organizations(List.of(leafNode1));
		final var leafNode2 = new OrganizationTree().orgId(leafOrgId2).treeLevel(6);
		final var midNode2 = new OrganizationTree().orgId(8556).treeLevel(5).organizations(List.of(leafNode2));
		final var rootNode = new OrganizationTree().orgId(rootOrgId).treeLevel(4).organizations(List.of(midNode1, midNode2));

		final var managerBId = UUID.randomUUID();
		final var managerB = new Manager().personId(managerBId).givenname("ManagerB").lastname("B");
		final var employmentB = new Employment().isMainEmployment(true).manager(managerB);
		final var employee1 = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employmentB));

		final var managerAId = UUID.randomUUID();
		final var managerA = new Manager().personId(managerAId).givenname("ManagerA").lastname("A");
		final var employmentA = new Employment().isMainEmployment(true).manager(managerA);
		final var employee2 = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employmentA));

		final var managerBEmployment = new Employment().isMainEmployment(true).manager(managerA);
		final var managerBAsEmployee = new Employeev2().personId(managerBId).employments(List.of(managerBEmployment));

		final var managerAEmployment = new Employment().isMainEmployment(true);
		final var managerAAsEmployee = new Employeev2().personId(managerAId).employments(List.of(managerAEmployment));

		when(mdViewerServiceMock.getOrgTree(rootOrgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId1)).thenReturn(List.of(personId1));
		when(mdViewerServiceMock.getPersonIds(leafOrgId2)).thenReturn(List.of(personId2));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId1)).thenReturn(List.of(employee1));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, personId2)).thenReturn(List.of(employee2));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerBId.toString())).thenReturn(List.of(managerBAsEmployee));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerAId.toString())).thenReturn(List.of(managerAAsEmployee));

		final var result = accessLoaderService.resolveManagerHierarchy(MUNICIPALITY_ID, rootOrgId);

		assertThat(result).hasSize(2);
		assertThat(result.get(managerBId).manager()).isEqualTo(managerB);
		assertThat(result.get(managerBId).paths()).containsExactly("8603/10958/7221");
		assertThat(result.get(managerAId).manager()).isEqualTo(managerA);
		assertThat(result.get(managerAId).paths()).containsExactlyInAnyOrder("8603/8556/8615", "8603/10958/7221");

		verify(employeeClientMock).getEmployments(MUNICIPALITY_ID, personId1);
		verify(employeeClientMock).getEmployments(MUNICIPALITY_ID, personId2);
		verify(employeeClientMock).getEmployments(MUNICIPALITY_ID, managerBId.toString());
		verify(employeeClientMock).getEmployments(MUNICIPALITY_ID, managerAId.toString());
		verifyNoMoreInteractions(employeeClientMock);
	}

	@Test
	void resolveManagerHierarchyUpperManagerDirectlyManagesTwoLeaves() {
		final var leaf7221 = new OrganizationTree().orgId(7221).treeLevel(6);
		final var leaf11028 = new OrganizationTree().orgId(11028).treeLevel(6);
		final var leaf7490 = new OrganizationTree().orgId(7490).treeLevel(6);
		final var leaf8615 = new OrganizationTree().orgId(8615).treeLevel(6);
		final var leaf8576 = new OrganizationTree().orgId(8576).treeLevel(6);
		final var leaf7259 = new OrganizationTree().orgId(7259).treeLevel(6);

		final var rootNode = new OrganizationTree().orgId(8603).treeLevel(4).organizations(List.of(
			new OrganizationTree().orgId(10958).treeLevel(5).organizations(List.of(leaf7221)),
			new OrganizationTree().orgId(11027).treeLevel(5).organizations(List.of(leaf11028)),
			new OrganizationTree().orgId(11009).treeLevel(5).organizations(List.of(leaf7490)),
			new OrganizationTree().orgId(8556).treeLevel(5).organizations(List.of(leaf8615)),
			new OrganizationTree().orgId(10983).treeLevel(5).organizations(List.of(leaf8576)),
			new OrganizationTree().orgId(10971).treeLevel(5).organizations(List.of(leaf7259))));

		final var managerAId = UUID.randomUUID();
		final var managerA = new Manager().personId(managerAId).givenname("A").lastname("Boss");
		final var managerBId = UUID.randomUUID();
		final var managerB = new Manager().personId(managerBId).givenname("B").lastname("Sub");
		final var managerCId = UUID.randomUUID();
		final var managerC = new Manager().personId(managerCId).givenname("C").lastname("Sub");
		final var managerDId = UUID.randomUUID();
		final var managerD = new Manager().personId(managerDId).givenname("D").lastname("Sub");
		final var managerEId = UUID.randomUUID();
		final var managerE = new Manager().personId(managerEId).givenname("E").lastname("Sub");

		when(mdViewerServiceMock.getOrgTree(8603)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(7221)).thenReturn(List.of("p1"));
		when(mdViewerServiceMock.getPersonIds(11028)).thenReturn(List.of("p2"));
		when(mdViewerServiceMock.getPersonIds(7490)).thenReturn(List.of("p3"));
		when(mdViewerServiceMock.getPersonIds(8615)).thenReturn(List.of("p4"));
		when(mdViewerServiceMock.getPersonIds(8576)).thenReturn(List.of("p5"));
		when(mdViewerServiceMock.getPersonIds(7259)).thenReturn(List.of("p6"));

		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, "p1"))
			.thenReturn(List.of(new Employeev2().employments(List.of(new Employment().isMainEmployment(true).manager(managerB)))));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, "p2"))
			.thenReturn(List.of(new Employeev2().employments(List.of(new Employment().isMainEmployment(true).manager(managerC)))));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, "p3"))
			.thenReturn(List.of(new Employeev2().employments(List.of(new Employment().isMainEmployment(true).manager(managerD)))));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, "p4"))
			.thenReturn(List.of(new Employeev2().employments(List.of(new Employment().isMainEmployment(true).manager(managerA)))));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, "p5"))
			.thenReturn(List.of(new Employeev2().employments(List.of(new Employment().isMainEmployment(true).manager(managerE)))));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, "p6"))
			.thenReturn(List.of(new Employeev2().employments(List.of(new Employment().isMainEmployment(true).manager(managerA)))));

		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerBId.toString()))
			.thenReturn(List.of(new Employeev2().personId(managerBId).employments(List.of(new Employment().isMainEmployment(true).manager(managerA)))));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerCId.toString()))
			.thenReturn(List.of(new Employeev2().personId(managerCId).employments(List.of(new Employment().isMainEmployment(true).manager(managerA)))));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerDId.toString()))
			.thenReturn(List.of(new Employeev2().personId(managerDId).employments(List.of(new Employment().isMainEmployment(true).manager(managerA)))));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerEId.toString()))
			.thenReturn(List.of(new Employeev2().personId(managerEId).employments(List.of(new Employment().isMainEmployment(true).manager(managerA)))));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerAId.toString()))
			.thenReturn(List.of(new Employeev2().personId(managerAId).employments(List.of(new Employment().isMainEmployment(true)))));

		final var result = accessLoaderService.resolveManagerHierarchy(MUNICIPALITY_ID, 8603);

		assertThat(result).hasSize(5);
		assertThat(result.get(managerBId).paths()).containsExactly("8603/10958/7221");
		assertThat(result.get(managerCId).paths()).containsExactly("8603/11027/11028");
		assertThat(result.get(managerDId).paths()).containsExactly("8603/11009/7490");
		assertThat(result.get(managerEId).paths()).containsExactly("8603/10983/8576");
		assertThat(result.get(managerAId).paths()).containsExactlyInAnyOrder(
			"8603/8556/8615",
			"8603/10971/7259",
			"8603/10958/7221",
			"8603/11027/11028",
			"8603/11009/7490",
			"8603/10983/8576");
	}

	@Test
	void toAccessPattern() {
		assertThat(AccessLoaderService.toAccessPattern("1/31/500/8603/10958/7221")).isEqualTo("LOCATION/31/500/8603/10958/7221/**");
		assertThat(AccessLoaderService.toAccessPattern("8603/10958/7221")).isEqualTo("LOCATION/10958/7221/**");
		assertThat(AccessLoaderService.toAccessPattern("100")).isEqualTo("LOCATION/100/**");
	}

	@Test
	void syncAccessUsersCreatesNewUser() {
		final var orgId = 1;
		final var leafOrgId = 100;

		final var managerId = UUID.randomUUID();
		final var manager = new Manager().personId(managerId).givenname("Boss").lastname("Person").loginname("boss01per");
		final var employment = new Employment().isMainEmployment(true).manager(manager);
		final var employee = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employment));

		final var leafNode = new OrganizationTree().orgId(leafOrgId).treeLevel(6);
		final var rootNode = new OrganizationTree().orgId(orgId).treeLevel(2).organizations(List.of(leafNode));

		final var managerEmployment = new Employment().isMainEmployment(true);
		final var managerAsEmployee = new Employeev2().personId(managerId).employments(List.of(managerEmployment));

		when(mdViewerServiceMock.getOrgTree(orgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId)).thenReturn(List.of("person-1"));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, "person-1")).thenReturn(List.of(employee));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerId.toString())).thenReturn(List.of(managerAsEmployee));
		when(accessMapperClientMock.getAccessUsers(MUNICIPALITY_ID, NAMESPACE, "AUTOMATIC")).thenReturn(List.of());

		when(accessLoaderPropertiesMock.accessLevel()).thenReturn("R");

		accessLoaderService.syncAccessUsers(MUNICIPALITY_ID, NAMESPACE, List.of(orgId));

		verify(accessMapperClientMock).createAccessUser(eq(MUNICIPALITY_ID), eq(NAMESPACE), any(AccessUser.class));
		verify(accessMapperClientMock, never()).deleteAccessUser(any(), any(), any());
		verify(accessMapperClientMock, never()).updateAccessUser(any(), any(), any(), any());
	}

	@Test
	void syncAccessUsersDeletesStaleUser() {
		final var orgId = 1;
		final var rootNode = new OrganizationTree().orgId(orgId).treeLevel(2);

		final var existingUser = new AccessUser()
			.id("existing-id")
			.userId("stale01user")
			.origin("AUTOMATIC")
			.accessByType(List.of(new AccessType().type("label").access(List.of(new Access().pattern("LOCATION/50/100").accessLevel(Access.AccessLevelEnum.R)))));

		when(mdViewerServiceMock.getOrgTree(orgId)).thenReturn(rootNode);
		when(accessMapperClientMock.getAccessUsers(MUNICIPALITY_ID, NAMESPACE, "AUTOMATIC")).thenReturn(List.of(existingUser));

		accessLoaderService.syncAccessUsers(MUNICIPALITY_ID, NAMESPACE, List.of(orgId));

		verify(accessMapperClientMock).deleteAccessUser(MUNICIPALITY_ID, NAMESPACE, "existing-id");
		verify(accessMapperClientMock, never()).createAccessUser(any(), any(), any());
		verify(accessMapperClientMock, never()).updateAccessUser(any(), any(), any(), any());
	}

	@Test
	void syncAccessUsersUpdatesChangedUser() {
		final var orgId = 1;
		final var leafOrgId = 100;

		final var managerId = UUID.randomUUID();
		final var manager = new Manager().personId(managerId).givenname("Boss").lastname("Person").loginname("boss01per");
		final var employment = new Employment().isMainEmployment(true).manager(manager);
		final var employee = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employment));

		final var leafNode = new OrganizationTree().orgId(leafOrgId).treeLevel(6);
		final var rootNode = new OrganizationTree().orgId(orgId).treeLevel(2).organizations(List.of(leafNode));

		final var managerEmployment = new Employment().isMainEmployment(true);
		final var managerAsEmployee = new Employeev2().personId(managerId).employments(List.of(managerEmployment));

		// Existing user has a different pattern
		final var existingUser = new AccessUser()
			.id("existing-id")
			.userId("boss01per")
			.origin("AUTOMATIC")
			.accessByType(List.of(new AccessType().type("label").access(List.of(new Access().pattern("LOCATION/old/path").accessLevel(Access.AccessLevelEnum.R)))));

		when(mdViewerServiceMock.getOrgTree(orgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId)).thenReturn(List.of("person-1"));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, "person-1")).thenReturn(List.of(employee));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerId.toString())).thenReturn(List.of(managerAsEmployee));
		when(accessMapperClientMock.getAccessUsers(MUNICIPALITY_ID, NAMESPACE, "AUTOMATIC")).thenReturn(List.of(existingUser));

		when(accessLoaderPropertiesMock.accessLevel()).thenReturn("R");

		accessLoaderService.syncAccessUsers(MUNICIPALITY_ID, NAMESPACE, List.of(orgId));

		verify(accessMapperClientMock).updateAccessUser(eq(MUNICIPALITY_ID), eq(NAMESPACE), eq("existing-id"), any(AccessUser.class));
		verify(accessMapperClientMock, never()).createAccessUser(any(), any(), any());
		verify(accessMapperClientMock, never()).deleteAccessUser(any(), any(), any());
	}

	@Test
	void syncAccessUsersNoOpWhenUnchanged() {
		final var orgId = 1;
		final var leafOrgId = 100;

		final var managerId = UUID.randomUUID();
		final var manager = new Manager().personId(managerId).givenname("Boss").lastname("Person").loginname("boss01per");
		final var employment = new Employment().isMainEmployment(true).manager(manager);
		final var employee = new Employeev2().personId(UUID.randomUUID()).employments(List.of(employment));

		final var leafNode = new OrganizationTree().orgId(leafOrgId).treeLevel(6);
		final var rootNode = new OrganizationTree().orgId(orgId).treeLevel(2).organizations(List.of(leafNode));

		final var managerEmployment = new Employment().isMainEmployment(true);
		final var managerAsEmployee = new Employeev2().personId(managerId).employments(List.of(managerEmployment));

		// Existing user matches desired state: path "1/100" -> drop first -> "100" -> "LOCATION/100/**"
		final var existingUser = new AccessUser()
			.id("existing-id")
			.userId("boss01per")
			.origin("AUTOMATIC")
			.accessByType(List.of(new AccessType().type("label").access(List.of(new Access().pattern("LOCATION/100/**").accessLevel(Access.AccessLevelEnum.R)))));

		when(mdViewerServiceMock.getOrgTree(orgId)).thenReturn(rootNode);
		when(mdViewerServiceMock.getPersonIds(leafOrgId)).thenReturn(List.of("person-1"));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, "person-1")).thenReturn(List.of(employee));
		when(employeeClientMock.getEmployments(MUNICIPALITY_ID, managerId.toString())).thenReturn(List.of(managerAsEmployee));
		when(accessMapperClientMock.getAccessUsers(MUNICIPALITY_ID, NAMESPACE, "AUTOMATIC")).thenReturn(List.of(existingUser));

		when(accessLoaderPropertiesMock.accessLevel()).thenReturn("R");

		accessLoaderService.syncAccessUsers(MUNICIPALITY_ID, NAMESPACE, List.of(orgId));

		verify(accessMapperClientMock).getAccessUsers(MUNICIPALITY_ID, NAMESPACE, "AUTOMATIC");
		verify(accessMapperClientMock, never()).createAccessUser(any(), any(), any());
		verify(accessMapperClientMock, never()).deleteAccessUser(any(), any(), any());
		verify(accessMapperClientMock, never()).updateAccessUser(any(), any(), any(), any());
	}

	@Test
	void syncAccessUsersIgnoresManualOrigin() {
		final var orgId = 1;
		final var rootNode = new OrganizationTree().orgId(orgId).treeLevel(2);

		// Server-side filter ensures only AUTOMATIC users are returned, so manual users are never touched
		when(mdViewerServiceMock.getOrgTree(orgId)).thenReturn(rootNode);
		when(accessMapperClientMock.getAccessUsers(MUNICIPALITY_ID, NAMESPACE, "AUTOMATIC")).thenReturn(List.of());

		accessLoaderService.syncAccessUsers(MUNICIPALITY_ID, NAMESPACE, List.of(orgId));

		verify(accessMapperClientMock).getAccessUsers(MUNICIPALITY_ID, NAMESPACE, "AUTOMATIC");
		verify(accessMapperClientMock, never()).deleteAccessUser(any(), any(), any());
		verify(accessMapperClientMock, never()).createAccessUser(any(), any(), any());
		verify(accessMapperClientMock, never()).updateAccessUser(any(), any(), any(), any());
	}
}
