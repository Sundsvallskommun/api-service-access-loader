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
import java.util.Set;
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
		final var scopePrefixes = orgIds.stream()
			.map(orgId -> LOCATION_PREFIX + orgId + PATH_DELIMITER)
			.collect(Collectors.toSet());

		final var desiredByUserId = buildDesiredState(municipalityId, orgIds);

		final var currentAutomaticByUserId = accessMapperClient.getAccessUsers(municipalityId, namespace, ORIGIN_AUTOMATIC).stream()
			.collect(Collectors.toMap(AccessUser::getUserId, Function.identity()));

		createNewUsers(municipalityId, namespace, desiredByUserId, currentAutomaticByUserId);
		syncExistingUsers(municipalityId, namespace, desiredByUserId, currentAutomaticByUserId, scopePrefixes);
	}

	private Map<String, AccessUser> buildDesiredState(final String municipalityId, final List<Integer> orgIds) {
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

		return allManagers.values().stream()
			.filter(entry -> entry.manager().getLoginname() != null)
			.collect(Collectors.toMap(
				entry -> entry.manager().getLoginname(),
				this::buildAccessUser,
				this::mergeAccessUsers));
	}

	private AccessUser mergeAccessUsers(final AccessUser existing, final AccessUser incoming) {
		Optional.ofNullable(incoming.getAccessByType())
			.stream()
			.flatMap(List::stream)
			.findFirst()
			.ifPresent(incomingType -> {
				final var existingType = Optional.ofNullable(existing.getAccessByType())
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
		return existing;
	}

	private void createNewUsers(final String municipalityId, final String namespace,
		final Map<String, AccessUser> desiredByUserId, final Map<String, AccessUser> currentByUserId) {

		desiredByUserId.forEach((userId, desired) -> {
			if (!currentByUserId.containsKey(userId)) {
				LOG.info("Creating access user: {}", userId);
				accessMapperClient.createAccessUser(municipalityId, namespace, desired);
			}
		});
	}

	private void syncExistingUsers(final String municipalityId, final String namespace,
		final Map<String, AccessUser> desiredByUserId, final Map<String, AccessUser> currentByUserId, final Set<String> scopePrefixes) {

		currentByUserId.forEach((userId, current) -> {
			final var desired = desiredByUserId.get(userId);
			final var currentAccesses = extractAccessList(current);
			final var outOfScopeAccesses = currentAccesses.stream()
				.filter(access -> !isInScope(access.getPattern(), scopePrefixes))
				.toList();

			if (desired == null) {
				removeStaleAccesses(municipalityId, namespace, userId, current, currentAccesses, outOfScopeAccesses);
			} else {
				mergeAndUpdateAccesses(municipalityId, namespace, userId, current, currentAccesses, outOfScopeAccesses, desired);
			}
		});
	}

	private void removeStaleAccesses(final String municipalityId, final String namespace, final String userId,
		final AccessUser current, final List<Access> currentAccesses, final List<Access> outOfScopeAccesses) {

		if (outOfScopeAccesses.isEmpty()) {
			LOG.info("Deleting access user: {} (id: {})", userId, current.getId());
			accessMapperClient.deleteAccessUser(municipalityId, namespace, current.getId());
		} else if (outOfScopeAccesses.size() < currentAccesses.size()) {
			LOG.info("Removing in-scope accesses for user: {} (id: {})", userId, current.getId());
			accessMapperClient.updateAccessUser(municipalityId, namespace, current.getId(), rebuildAccessUser(current, outOfScopeAccesses));
		}
	}

	private void mergeAndUpdateAccesses(final String municipalityId, final String namespace, final String userId,
		final AccessUser current, final List<Access> currentAccesses, final List<Access> outOfScopeAccesses, final AccessUser desired) {

		final var desiredAccesses = extractAccessList(desired);
		final var mergedAccesses = new ArrayList<>(outOfScopeAccesses);
		desiredAccesses.stream()
			.filter(access -> mergedAccesses.stream().noneMatch(existing -> Objects.equals(existing.getPattern(), access.getPattern())))
			.forEach(mergedAccesses::add);

		if (!accessListEquals(currentAccesses, mergedAccesses)) {
			LOG.info("Updating access user: {} (id: {})", userId, current.getId());
			accessMapperClient.updateAccessUser(municipalityId, namespace, current.getId(), rebuildAccessUser(current, mergedAccesses));
		}
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
		return LOCATION_PREFIX + path + WILDCARD_SUFFIX;
	}

	private static boolean isInScope(final String pattern, final Set<String> scopePrefixes) {
		return pattern != null && scopePrefixes.stream().anyMatch(pattern::startsWith);
	}

	private List<Access> extractAccessList(final AccessUser user) {
		return Optional.ofNullable(user.getAccessByType())
			.orElse(List.of())
			.stream()
			.filter(at -> ACCESS_TYPE_LABEL.equals(at.getType()))
			.flatMap(at -> Optional.ofNullable(at.getAccess()).orElse(List.of()).stream())
			.toList();
	}

	private boolean accessListEquals(final List<Access> a, final List<Access> b) {
		final var toMap = (Function<List<Access>, Map<String, String>>) list -> list.stream()
			.collect(Collectors.toMap(
				Access::getPattern,
				access -> Optional.ofNullable(access.getAccessLevel()).map(Enum::name).orElse(""),
				(x, y) -> x));
		return toMap.apply(a).equals(toMap.apply(b));
	}

	private AccessUser rebuildAccessUser(final AccessUser original, final List<Access> accesses) {
		final var accessType = new AccessType()
			.type(ACCESS_TYPE_LABEL)
			.access(new ArrayList<>(accesses));

		return new AccessUser()
			.userId(original.getUserId())
			.origin(original.getOrigin())
			.accessByType(List.of(accessType));
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
