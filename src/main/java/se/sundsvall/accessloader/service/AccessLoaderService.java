package se.sundsvall.accessloader.service;

import generated.se.sundsvall.accessmapper.Access;
import generated.se.sundsvall.accessmapper.AccessType;
import generated.se.sundsvall.accessmapper.AccessUser;
import generated.se.sundsvall.employee.Employment;
import generated.se.sundsvall.mdviewer.OrganizationTree;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.accessloader.configuration.AccessLoaderProperties;
import se.sundsvall.accessloader.integration.accessmapper.AccessMapperClient;
import se.sundsvall.accessloader.integration.employee.EmployeeClient;
import se.sundsvall.accessloader.service.model.ManagerWithInheritedPaths;
import se.sundsvall.accessloader.service.model.ManagersWithPath;

@Service
public class AccessLoaderService {

	private static final Logger LOG = LoggerFactory.getLogger(AccessLoaderService.class);

	private static final int LEAF_TREE_LEVEL = 6;
	private static final String PATH_DELIMITER = "/";
	private static final String LOCATION_PREFIX = "LOCATION/";
	private static final String WILDCARD_SUFFIX = "/**";
	private static final String ACCESS_TYPE_LABEL = "label";
	private static final String ORIGIN_AUTOMATIC = "AUTOMATIC";

	private final MdViewerService mdViewerService;
	private final EmployeeClient employeeClient;
	private final AccessMapperClient accessMapperClient;
	private final AccessLoaderProperties accessLoaderProperties;

	AccessLoaderService(final MdViewerService mdViewerService, final EmployeeClient employeeClient, final AccessMapperClient accessMapperClient, final AccessLoaderProperties accessLoaderProperties) {
		this.mdViewerService = mdViewerService;
		this.employeeClient = employeeClient;
		this.accessMapperClient = accessMapperClient;
		this.accessLoaderProperties = accessLoaderProperties;
	}

	public void syncAccessUsers(final String municipalityId, final String namespace, final List<Integer> orgIds) {
		// 1. Resolve managers across all orgIds and merge
		final var allManagers = new LinkedHashMap<UUID, ManagerWithInheritedPaths>();
		for (final var orgId : orgIds) {
			final var managers = resolveManagerHierarchy(municipalityId, orgId);
			managers.forEach((id, entry) -> allManagers.merge(id, entry, (existing, incoming) -> {
				incoming.paths().stream()
					.filter(path -> !existing.paths().contains(path))
					.forEach(existing.paths()::add);
				return existing;
			}));
		}

		// 2. Build desired state
		final var desiredByUserId = allManagers.values().stream()
			.filter(entry -> entry.manager().getLoginname() != null)
			.collect(Collectors.toMap(
				entry -> entry.manager().getLoginname(),
										this::buildAccessUser,
				(a, b) -> {
					Optional.ofNullable(b.getAccessByType())
						.stream()
						.flatMap(List::stream)
						.findFirst()
						.ifPresent(incomingType -> {
							final var existingType = Optional.ofNullable(a.getAccessByType())
								.stream()
								.flatMap(List::stream)
								.findFirst()
								.orElse(null);
							if (existingType != null && incomingType.getAccess() != null) {
								incomingType.getAccess().stream()
									.filter(access -> !existingType.getAccess().contains(access))
									.forEach(existingType.getAccess()::add);
							}
						});
					return a;
				}));

		// 3. Fetch current AUTOMATIC users
		final var currentAutomaticByUserId = accessMapperClient.getAccessUsers(municipalityId, namespace, ORIGIN_AUTOMATIC).stream()
			.collect(Collectors.toMap(AccessUser::getUserId, Function.identity()));

		// 4. Diff and sync
		// Create: in desired but not in current
		desiredByUserId.forEach((userId, desired) -> {
			if (!currentAutomaticByUserId.containsKey(userId)) {
				LOG.info("Creating access user: {}", userId);
				accessMapperClient.createAccessUser(municipalityId, namespace, desired);
			}
		});

		// Delete: in current (AUTOMATIC) but not in desired
		currentAutomaticByUserId.forEach((userId, current) -> {
			if (!desiredByUserId.containsKey(userId)) {
				LOG.info("Deleting access user: {} (id: {})", userId, current.getId());
				accessMapperClient.deleteAccessUser(municipalityId, namespace, current.getId());
			}
		});

		// Update: in both but accesses differ
		desiredByUserId.forEach((userId, desired) -> {
			final var current = currentAutomaticByUserId.get(userId);
			if (current != null && !accessesEqual(desired, current)) {
				LOG.info("Updating access user: {} (id: {})", userId, current.getId());
				accessMapperClient.updateAccessUser(municipalityId, namespace, current.getId(), desired);
			}
		});
	}

	private AccessUser buildAccessUser(final ManagerWithInheritedPaths entry) {
		final var accesses = entry.paths().stream()
			.map(AccessLoaderService::toAccessPattern)
			.map(pattern -> new Access().pattern(pattern).accessLevel(Access.AccessLevelEnum.fromValue(accessLoaderProperties.accessLevel())))
			.toList();

		final var accessType = new AccessType()
			.type(ACCESS_TYPE_LABEL)
			.access(new ArrayList<>(accesses));

		return new AccessUser()
			.userId(entry.manager().getLoginname())
			.origin(ORIGIN_AUTOMATIC)
			.accessByType(List.of(accessType));
	}

	static String toAccessPattern(final String path) {
		final var segments = path.split(PATH_DELIMITER);
		if (segments.length <= 1) {
			return LOCATION_PREFIX + path + WILDCARD_SUFFIX;
		}
		// Drop first segment (level-2 root), prefix with LOCATION/
		final var withoutFirst = String.join(PATH_DELIMITER, java.util.Arrays.copyOfRange(segments, 1, segments.length));
		return LOCATION_PREFIX + withoutFirst + WILDCARD_SUFFIX;
	}

	private boolean accessesEqual(final AccessUser desired, final AccessUser current) {
		final var desiredAccesses = extractAccesses(desired);
		final var currentAccesses = extractAccesses(current);
		return desiredAccesses.equals(currentAccesses);
	}

	private Map<String, String> extractAccesses(final AccessUser user) {
		return Optional.ofNullable(user.getAccessByType())
			.orElse(List.of())
			.stream()
			.filter(at -> ACCESS_TYPE_LABEL.equals(at.getType()))
			.flatMap(at -> Optional.ofNullable(at.getAccess()).orElse(List.of()).stream())
			.collect(Collectors.toMap(
				Access::getPattern,
				access -> Optional.ofNullable(access.getAccessLevel()).map(Enum::name).orElse(""),
				(a, b) -> a));
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
				.findFirst()
				.map(personId -> employeeClient.getEmployments(municipalityId, personId))
				.orElse(List.of())
				.stream()
				.flatMap(employee -> Optional.ofNullable(employee.getEmployments()).orElse(List.of()).stream())
				.filter(employment -> Boolean.TRUE.equals(employment.getIsMainEmployment()))
				.map(Employment::getManager)
				.filter(Objects::nonNull)
				.distinct()
				.toList();

			if (!managers.isEmpty()) {
				result.put(currentOrgId, new ManagersWithPath(orgPaths.get(currentOrgId), managers));
			}
		});

		return result;
	}

	public Map<UUID, ManagerWithInheritedPaths> resolveManagerHierarchy(final String municipalityId, final int orgId) {
		final var managersByOrg = loadManagersByOrg(municipalityId, orgId);

		final var result = new LinkedHashMap<UUID, ManagerWithInheritedPaths>();

		// Collect all direct managers with their own paths
		managersByOrg.values().forEach(managersWithPath -> managersWithPath.managers().forEach(manager -> result.merge(
			manager.getPersonId(),
			new ManagerWithInheritedPaths(manager, new ArrayList<>(List.of(managersWithPath.orgPath()))),
			(existing, incoming) -> {
				existing.paths().addAll(incoming.paths());
				return existing;
			})));

		// Take a snapshot of the direct managers and their paths before resolving upper managers
		final var directManagers = new LinkedHashMap<UUID, List<String>>();
		result.forEach((managerId, entry) -> directManagers.put(managerId, List.copyOf(entry.paths())));

		// For each direct manager, resolve their upper manager and let them inherit the paths
		directManagers.forEach((managerId, paths) -> {
			final var upperManagers = employeeClient.getEmployments(municipalityId, managerId.toString())
				.stream()
				.flatMap(employee -> Optional.ofNullable(employee.getEmployments()).orElse(List.of()).stream())
				.filter(employment -> Boolean.TRUE.equals(employment.getIsMainEmployment()))
				.map(Employment::getManager)
				.filter(Objects::nonNull)
				.distinct()
				.toList();

			upperManagers.forEach(upperManager -> result.merge(
				upperManager.getPersonId(),
				new ManagerWithInheritedPaths(upperManager, new ArrayList<>(paths)),
				(existing, incoming) -> {
					incoming.paths().stream()
						.filter(path -> !existing.paths().contains(path))
						.forEach(existing.paths()::add);
					return existing;
				}));
		});

		return result;
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
