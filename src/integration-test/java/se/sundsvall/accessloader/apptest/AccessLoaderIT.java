package se.sundsvall.accessloader.apptest;

import org.junit.jupiter.api.Test;
import se.sundsvall.accessloader.Application;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpStatus.NO_CONTENT;

/**
 * Tests of the access loader job, run for the fictive namespace configured in application-it.yml (MY_NAMESPACE, scoped
 * to orgId 1).
 * <p>
 * The org tree of orgId 1 holds the units 11 and 12. The managers of the units get read access to the location path of
 * their unit, and the upper manager of a manager inherits the paths.
 * <p>
 * The WireMock stubs verify the outcome: every stub must be called, and a call matching no stub - such as a create or
 * update with another body - fails the test.
 */
@WireMockAppTestSuite(files = "classpath:/AccessLoaderIT/", classes = Application.class)
class AccessLoaderIT extends AbstractAppTest {

	private static final String PATH = "/sync";

	/**
	 * No automatic users exist. manager01 (unit 11) and manager02 (unit 12) are created, as is their upper manager boss01
	 * with the paths of both units.
	 */
	@Test
	void test01_syncCreatesAccessUsers() {
		setupCall()
			.withServicePath(PATH)
			.withHttpMethod(POST)
			.withExpectedResponseStatus(NO_CONTENT)
			.withExpectedResponseBodyIsNull()
			.sendRequestAndVerifyResponse();
	}

	/**
	 * Only unit 11 has a manager, manager01, who has no upper manager. The existing manager01 has an outdated path and is
	 * updated. stale01 is no manager any more and holds only paths in scope, so it is deleted. mixed01 is no manager any
	 * more either, but also holds a path outside the scope of the job, which is all it keeps.
	 */
	@Test
	void test02_syncUpdatesAndDeletesAccessUsers() {
		setupCall()
			.withServicePath(PATH)
			.withHttpMethod(POST)
			.withExpectedResponseStatus(NO_CONTENT)
			.withExpectedResponseBodyIsNull()
			.sendRequestAndVerifyResponse();
	}
}
