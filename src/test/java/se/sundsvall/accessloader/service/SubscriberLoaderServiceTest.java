package se.sundsvall.accessloader.service;

import generated.se.sundsvall.accessmapper.Access;
import generated.se.sundsvall.accessmapper.AccessGroup;
import generated.se.sundsvall.accessmapper.AccessType;
import generated.se.sundsvall.accessmapper.AccessUser;
import generated.se.sundsvall.supportmanagement.Identifier;
import generated.se.sundsvall.supportmanagement.SubscriptionProfile;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import se.sundsvall.accessloader.integration.accessmapper.AccessMapperClient;
import se.sundsvall.accessloader.integration.supportmanagement.SupportManagementClient;
import se.sundsvall.dept44.problem.Problem;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

@ExtendWith(MockitoExtension.class)
class SubscriberLoaderServiceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String NAMESPACE = "MY_NAMESPACE";
	private static final String ROLE = "role";

	@Mock
	private AccessMapperClient accessMapperClientMock;

	@Mock
	private SupportManagementClient supportManagementClientMock;

	@InjectMocks
	private SubscriberLoaderService subscriberLoaderService;

	@Test
	void syncSubscribers() {
		final var roleProfiles = Map.of(
			"UNIT_MANAGER", "Chefer",
			"HEAD_OF_OPERATIONS", "Chefer",
			"MAR_MAS", "MAR_MAS");

		// LEX_INVESTIGATOR and REPORTER have profiles but are not configured, so they are never touched
		when(supportManagementClientMock.getSubscriptionProfiles(MUNICIPALITY_ID, NAMESPACE)).thenReturn(List.of(
			new SubscriptionProfile().id("profile-1").name("Chefer"),
			new SubscriptionProfile().id("profile-2").name("MAR_MAS"),
			new SubscriptionProfile().id("profile-3").name("LEX_INVESTIGATOR"),
			new SubscriptionProfile().id("profile-4").name("REPORTER"),
			new SubscriptionProfile().name("Utan id")));

		when(accessMapperClientMock.getAccessUsers(MUNICIPALITY_ID, NAMESPACE, null)).thenReturn(List.of(
			new AccessUser().userId("user1"),
			new AccessUser().userId("user1"),
			new AccessUser().userId("user2"),
			new AccessUser().userId("user3"),
			new AccessUser()));

		when(accessMapperClientMock.getAccessDetails(MUNICIPALITY_ID, NAMESPACE, "user1", ROLE)).thenReturn(List.of(
			accessGroup(ROLE, "UNIT_MANAGER"),
			accessGroup(ROLE, "HEAD_OF_OPERATIONS", "MAR_MAS")));
		when(accessMapperClientMock.getAccessDetails(MUNICIPALITY_ID, NAMESPACE, "user2", ROLE)).thenReturn(List.of(
			accessGroup(ROLE, "HEAD_OF_OPERATIONS", "LEX_INVESTIGATOR"),
			accessGroup("label", "MAR_MAS"),
			new AccessGroup(),
			new AccessGroup().accessByType(List.of(new AccessType().type(ROLE))),
			new AccessGroup().accessByType(List.of(new AccessType().type(ROLE).access(List.of(new Access()))))));
		when(accessMapperClientMock.getAccessDetails(MUNICIPALITY_ID, NAMESPACE, "user3", ROLE)).thenReturn(null);

		subscriberLoaderService.syncSubscribers(MUNICIPALITY_ID, NAMESPACE, roleProfiles);

		verify(supportManagementClientMock).getSubscriptionProfiles(MUNICIPALITY_ID, NAMESPACE);
		verify(accessMapperClientMock).getAccessUsers(MUNICIPALITY_ID, NAMESPACE, null);
		verify(accessMapperClientMock).getAccessDetails(MUNICIPALITY_ID, NAMESPACE, "user1", ROLE);
		verify(accessMapperClientMock).getAccessDetails(MUNICIPALITY_ID, NAMESPACE, "user2", ROLE);
		verify(accessMapperClientMock).getAccessDetails(MUNICIPALITY_ID, NAMESPACE, "user3", ROLE);
		verify(supportManagementClientMock).replaceSubscriptionProfileMembers(MUNICIPALITY_ID, NAMESPACE, "profile-1", List.of(adAccount("user1"), adAccount("user2")));
		verify(supportManagementClientMock).replaceSubscriptionProfileMembers(MUNICIPALITY_ID, NAMESPACE, "profile-2", List.of(adAccount("user1")));
		verifyNoMoreInteractions(accessMapperClientMock, supportManagementClientMock);
	}

	@Test
	void syncSubscribersRemovesAllMembersWhenNoUserHoldsRole() {
		when(supportManagementClientMock.getSubscriptionProfiles(MUNICIPALITY_ID, NAMESPACE)).thenReturn(List.of(
			new SubscriptionProfile().id("profile-1").name("UNIT_MANAGER")));
		when(accessMapperClientMock.getAccessUsers(MUNICIPALITY_ID, NAMESPACE, null)).thenReturn(List.of(new AccessUser().userId("user1")));
		when(accessMapperClientMock.getAccessDetails(MUNICIPALITY_ID, NAMESPACE, "user1", ROLE)).thenReturn(List.of());

		subscriberLoaderService.syncSubscribers(MUNICIPALITY_ID, NAMESPACE, Map.of("UNIT_MANAGER", "UNIT_MANAGER"));

		verify(supportManagementClientMock).getSubscriptionProfiles(MUNICIPALITY_ID, NAMESPACE);
		verify(accessMapperClientMock).getAccessUsers(MUNICIPALITY_ID, NAMESPACE, null);
		verify(accessMapperClientMock).getAccessDetails(MUNICIPALITY_ID, NAMESPACE, "user1", ROLE);
		verify(supportManagementClientMock).replaceSubscriptionProfileMembers(MUNICIPALITY_ID, NAMESPACE, "profile-1", List.of());
		verifyNoMoreInteractions(accessMapperClientMock, supportManagementClientMock);
	}

	@Test
	void syncSubscribersWithoutConfiguredRoles() {
		subscriberLoaderService.syncSubscribers(MUNICIPALITY_ID, NAMESPACE, Map.of());

		verifyNoInteractions(accessMapperClientMock, supportManagementClientMock);
	}

	@Test
	void syncSubscribersWhenNoConfiguredProfileExists() {
		when(supportManagementClientMock.getSubscriptionProfiles(MUNICIPALITY_ID, NAMESPACE)).thenReturn(List.of(
			new SubscriptionProfile().id("profile-1").name("REPORTER")));

		subscriberLoaderService.syncSubscribers(MUNICIPALITY_ID, NAMESPACE, Map.of("UNIT_MANAGER", "UNIT_MANAGER"));

		verify(supportManagementClientMock).getSubscriptionProfiles(MUNICIPALITY_ID, NAMESPACE);
		verifyNoMoreInteractions(supportManagementClientMock);
		verifyNoInteractions(accessMapperClientMock);
	}

	@Test
	void syncSubscribersAbortsWhenRolesCannotBeRead() {
		when(supportManagementClientMock.getSubscriptionProfiles(MUNICIPALITY_ID, NAMESPACE)).thenReturn(List.of(
			new SubscriptionProfile().id("profile-1").name("UNIT_MANAGER")));
		when(accessMapperClientMock.getAccessUsers(MUNICIPALITY_ID, NAMESPACE, null)).thenReturn(List.of(new AccessUser().userId("user1")));
		when(accessMapperClientMock.getAccessDetails(MUNICIPALITY_ID, NAMESPACE, "user1", ROLE)).thenThrow(Problem.valueOf(BAD_GATEWAY, "error"));

		assertThatThrownBy(() -> subscriberLoaderService.syncSubscribers(MUNICIPALITY_ID, NAMESPACE, Map.of("UNIT_MANAGER", "UNIT_MANAGER")))
			.isInstanceOf(Problem.class);

		verify(supportManagementClientMock, never()).replaceSubscriptionProfileMembers(anyString(), anyString(), anyString(), any());
	}

	private static AccessGroup accessGroup(final String type, final String... patterns) {
		final var accesses = new ArrayList<Access>();
		for (final var pattern : patterns) {
			accesses.add(new Access().pattern(pattern));
		}
		return new AccessGroup().accessByType(List.of(new AccessType().type(type).access(accesses)));
	}

	private static Identifier adAccount(final String userId) {
		return new Identifier().type("adAccount").value(userId);
	}
}
