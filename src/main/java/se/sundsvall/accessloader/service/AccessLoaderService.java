package se.sundsvall.accessloader.service;

import generated.se.sundsvall.employee.Employeev2;
import generated.se.sundsvall.mdviewer.OrganizationTree;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import se.sundsvall.accessloader.integration.employee.EmployeeClient;
import se.sundsvall.accessloader.service.model.ManagersWithPath;

@Service
public class AccessLoaderService {

	private static final int LEAF_TREE_LEVEL = 6;
	private static final String PATH_DELIMITER = "/";

	private final MdViewerService mdViewerService;
	private final EmployeeClient employeeClient;

	AccessLoaderService(final MdViewerService mdViewerService, final EmployeeClient employeeClient) {
		this.mdViewerService = mdViewerService;
		this.employeeClient = employeeClient;
	}

	public Map<Integer, List<String>> loadPersonIdsByOrg(final int orgId) {
		final var orgTree = mdViewerService.getOrgTree(orgId);
		final var result = new LinkedHashMap<Integer, List<String>>();
		collectPersonIds(orgTree, result);
		return result;
	}

	public Map<Integer, ManagersWithPath> loadManagersByOrg(final String municipalityId, final int orgId) {
		final var orgTree = mdViewerService.getOrgTree(orgId);
		final var personIdsByOrg = new LinkedHashMap<Integer, List<String>>();
		final var orgPaths = new LinkedHashMap<Integer, String>();

		collectPersonIdsWithPath(orgTree, new ArrayList<>(), personIdsByOrg, orgPaths);

		final var result = new LinkedHashMap<Integer, ManagersWithPath>();

		personIdsByOrg.forEach((currentOrgId, personIds) -> {
			final var managers = personIds.stream()
				.map(personId -> employeeClient.getEmployments(municipalityId, personId))
				.flatMap(List::stream)
				.filter(this::isManager)
				.toList();

			if (!managers.isEmpty()) {
				result.put(currentOrgId, new ManagersWithPath(orgPaths.get(currentOrgId), managers));
			}
		});

		return result;
	}

	private boolean isManager(final Employeev2 employee) {
		return Optional.ofNullable(employee.getEmployments())
			.orElse(List.of())
			.stream()
			.anyMatch(employment -> Boolean.TRUE.equals(employment.getIsManager()));
	}

	private void collectPersonIds(final OrganizationTree node, final Map<Integer, List<String>> result) {
		if (node == null) {
			return;
		}

		if (Integer.valueOf(LEAF_TREE_LEVEL).equals(node.getTreeLevel()) && node.getOrgId() != null) {
			final var personIds = mdViewerService.getPersonIds(node.getOrgId());
			if (!personIds.isEmpty()) {
				result.put(node.getOrgId(), personIds);
			}
		}

		Optional.ofNullable(node.getOrganizations())
			.orElse(List.of())
			.forEach(child -> collectPersonIds(child, result));
	}

	private void collectPersonIdsWithPath(final OrganizationTree node, final List<String> currentPath,
		final Map<Integer, List<String>> personIdsByOrg, final Map<Integer, String> orgPaths) {

		if (node == null) {
			return;
		}

		final var path = new ArrayList<>(currentPath);
		Optional.ofNullable(node.getOrgId()).map(String::valueOf).ifPresent(path::add);

		if (Integer.valueOf(LEAF_TREE_LEVEL).equals(node.getTreeLevel()) && node.getOrgId() != null) {
			final var personIds = mdViewerService.getPersonIds(node.getOrgId());
			if (!personIds.isEmpty()) {
				personIdsByOrg.put(node.getOrgId(), personIds);
				orgPaths.put(node.getOrgId(), String.join(PATH_DELIMITER, path));
			}
		}

		Optional.ofNullable(node.getOrganizations())
			.orElse(List.of())
			.forEach(child -> collectPersonIdsWithPath(child, path, personIdsByOrg, orgPaths));
	}
}
