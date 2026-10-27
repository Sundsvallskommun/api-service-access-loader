package se.sundsvall.accessloader.apptest;

import org.junit.jupiter.api.Test;
import se.sundsvall.accessloader.Application;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.NO_CONTENT;

/**
 * Tests of the subscriber loader job, run for the fictive namespaces configured in application-it.yml (MY_NAMESPACE and
 * MY_OTHER_NAMESPACE, syncing UNIT_MANAGER and HEAD_OF_OPERATIONS).
 * <p>
 * The WireMock stubs verify the outcome: every stub must be called, and a call matching no stub - such as a replace of
 * members with another body, or without the X-Sent-By header - fails the test.
 */
@WireMockAppTestSuite(files = "classpath:/SubscriberLoaderIT/", classes = Application.class)
class SubscriberLoaderIT extends AbstractAppTest {

	private static final String PATH = "/sync-subscribers";

	/**
	 * MY_NAMESPACE: manager01 (UNIT_MANAGER) and manager02 (HEAD_OF_OPERATIONS) are made members, manager03 holds no role.
	 * The manually created manual01 holding UNIT_MANAGER is kept, while the manually created manual02 no longer holding it
	 * and the former manager formermanager01 are removed. LEX_INVESTIGATOR and the REPORTER profile are not configured and
	 * never touched. MY_OTHER_NAMESPACE has no profiles.
	 */
	@Test
	void test01_syncSubscribers() {
		setupCall()
			.withServicePath(PATH)
			.withHttpMethod(POST)
			.withExpectedResponseStatus(NO_CONTENT)
			.withExpectedResponseBodyIsNull()
			.sendRequestAndVerifyResponse();
	}

	/**
	 * None of the configured profiles exists in SupportManagement, so nothing else is read or replaced.
	 */
	@Test
	void test02_syncSubscribersWithoutProfiles() {
		setupCall()
			.withServicePath(PATH)
			.withHttpMethod(POST)
			.withExpectedResponseStatus(NO_CONTENT)
			.withExpectedResponseBodyIsNull()
			.sendRequestAndVerifyResponse();
	}

	/**
	 * The roles of manager01 in MY_NAMESPACE cannot be read, so no members are replaced there - no replace is stubbed. The
	 * job still goes on with MY_OTHER_NAMESPACE.
	 */
	@Test
	void test03_syncSubscribersWhenRolesCannotBeRead() {
		setupCall()
			.withServicePath(PATH)
			.withHttpMethod(POST)
			.withExpectedResponseStatus(NO_CONTENT)
			.withExpectedResponseBodyIsNull()
			.sendRequestAndVerifyResponse();
	}
}
