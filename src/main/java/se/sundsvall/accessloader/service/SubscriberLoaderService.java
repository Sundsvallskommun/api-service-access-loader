package se.sundsvall.accessloader.service;

import generated.se.sundsvall.accessmapper.Access;
import generated.se.sundsvall.accessmapper.AccessGroup;
import generated.se.sundsvall.accessmapper.AccessType;
import generated.se.sundsvall.accessmapper.AccessUser;
import generated.se.sundsvall.supportmanagement.Identifier;
import generated.se.sundsvall.supportmanagement.SubscriptionProfile;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import se.sundsvall.accessloader.integration.accessmapper.AccessMapperClient;
import se.sundsvall.accessloader.integration.supportmanagement.SupportManagementClient;

@Service
public class SubscriberLoaderService {

	private static final Logger LOG = LoggerFactory.getLogger(SubscriberLoaderService.class);

	private static final String ACCESS_TYPE_ROLE = "role";
	private static final String IDENTIFIER_TYPE_AD_ACCOUNT = "adAccount";

	private final AccessMapperClient accessMapperClient;
	private final SupportManagementClient supportManagementClient;

	SubscriberLoaderService(final AccessMapperClient accessMapperClient, final SupportManagementClient supportManagementClient) {
		this.accessMapperClient = accessMapperClient;
		this.supportManagementClient = supportManagementClient;
	}

	/**
	 * Makes the users holding a configured role the members of the subscription profile the role is mapped to. Users who
	 * no longer hold the role lose their membership. Any failure aborts the sync before members are replaced, so that
	 * users are never removed from a profile because their roles could not be read.
	 * <p>
	 * The roleProfiles act as an allowlist: a role that is not configured is ignored even if a profile is named as it, and
	 * a profile that no configured role is mapped to is never touched.
	 *
	 * @param municipalityId the municipality id
	 * @param namespace      the namespace, the same in access-mapper and SupportManagement
	 * @param roleProfiles   role name mapped to subscription profile name, for every role to sync
	 */
	public void syncSubscribers(final String municipalityId, final String namespace, final Map<String, String> roleProfiles) {
		if (roleProfiles.isEmpty()) {
			LOG.info("No roles configured for municipalityId: {}, namespace: {}", municipalityId, namespace);
			return;
		}

		final var profileIdsByName = getProfileIdsByName(municipalityId, namespace);
		final var profileNameByRole = resolveRoleProfiles(municipalityId, namespace, roleProfiles, profileIdsByName);
		if (profileNameByRole.isEmpty()) {
			return;
		}

		final var membersByProfileName = new LinkedHashMap<String, Set<String>>();
		profileNameByRole.values().forEach(profileName -> membersByProfileName.putIfAbsent(profileName, new TreeSet<>()));

		getUserIds(municipalityId, namespace).forEach(userId -> getRoles(municipalityId, namespace, userId).stream()
			.map(profileNameByRole::get)
			.filter(Objects::nonNull)
			.forEach(profileName -> membersByProfileName.get(profileName).add(userId)));

		membersByProfileName.forEach((profileName, userIds) -> {
			LOG.info("Replacing members of subscription profile '{}' with {} user(s)", profileName, userIds.size());
			supportManagementClient.replaceSubscriptionProfileMembers(municipalityId, namespace, profileIdsByName.get(profileName), toIdentifiers(userIds));
		});
	}

	private Map<String, String> getProfileIdsByName(final String municipalityId, final String namespace) {
		return supportManagementClient.getSubscriptionProfiles(municipalityId, namespace).stream()
			.filter(profile -> profile.getName() != null && profile.getId() != null)
			.collect(Collectors.toMap(SubscriptionProfile::getName, SubscriptionProfile::getId, (first, _) -> first));
	}

	/**
	 * The profile name of every configured role whose profile exists, in role order.
	 */
	private Map<String, String> resolveRoleProfiles(final String municipalityId, final String namespace, final Map<String, String> roleProfiles,
		final Map<String, String> profileIdsByName) {

		final var result = new LinkedHashMap<String, String>();
		new TreeMap<>(roleProfiles).forEach((role, profileName) -> {
			if (profileIdsByName.containsKey(profileName)) {
				result.put(role, profileName);
			} else {
				LOG.warn("Subscription profile '{}' for role '{}' not found in municipalityId: {}, namespace: {}", profileName, role, municipalityId, namespace);
			}
		});

		return result;
	}

	private Set<String> getUserIds(final String municipalityId, final String namespace) {
		return accessMapperClient.getAccessUsers(municipalityId, namespace, null).stream()
			.map(AccessUser::getUserId)
			.filter(Objects::nonNull)
			.collect(Collectors.toCollection(TreeSet::new));
	}

	/**
	 * The role names held by the user - the patterns of the accesses of type role in the user's access groups.
	 */
	private Set<String> getRoles(final String municipalityId, final String namespace, final String userId) {
		return Optional.ofNullable(accessMapperClient.getAccessDetails(municipalityId, namespace, userId, ACCESS_TYPE_ROLE))
			.orElse(List.of())
			.stream()
			.map(AccessGroup::getAccessByType)
			.filter(Objects::nonNull)
			.flatMap(List::stream)
			.filter(accessType -> ACCESS_TYPE_ROLE.equals(accessType.getType()))
			.map(AccessType::getAccess)
			.filter(Objects::nonNull)
			.flatMap(List::stream)
			.map(Access::getPattern)
			.filter(Objects::nonNull)
			.collect(Collectors.toSet());
	}

	private static List<Identifier> toIdentifiers(final Set<String> userIds) {
		return userIds.stream()
			.map(userId -> new Identifier().type(IDENTIFIER_TYPE_AD_ACCOUNT).value(userId))
			.toList();
	}
}
