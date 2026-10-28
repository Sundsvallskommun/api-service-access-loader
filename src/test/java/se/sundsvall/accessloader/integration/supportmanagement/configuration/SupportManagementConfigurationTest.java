package se.sundsvall.accessloader.integration.supportmanagement.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static se.sundsvall.accessloader.integration.supportmanagement.configuration.SupportManagementConfiguration.CLIENT_ID;
import static se.sundsvall.dept44.support.Identifier.HEADER_NAME;

@ExtendWith(MockitoExtension.class)
class SupportManagementConfigurationTest {

	@Mock
	private ClientRegistrationRepository clientRegistrationRepositoryMock;

	@Mock
	private ClientRegistration clientRegistrationMock;

	@Spy
	private FeignMultiCustomizer feignMultiCustomizerSpy;

	@Mock
	private FeignBuilderCustomizer feignBuilderCustomizerMock;

	@Mock
	private SupportManagementProperties propertiesMock;

	@Test
	void testFeignBuilderCustomizer() {
		final var requestInterceptor = createRequestInterceptor("svc01acc");

		// Without a calling user, the service acts as the configured identity
		final var templateWithoutCaller = new RequestTemplate();
		requestInterceptor.apply(templateWithoutCaller);
		assertThat(templateWithoutCaller.headers().get(HEADER_NAME)).containsExactly("svc01acc; type=adAccount");

		// A calling user keeps its own identity
		final var templateWithCaller = new RequestTemplate().header(HEADER_NAME, "joe01doe; type=adAccount");
		requestInterceptor.apply(templateWithCaller);
		assertThat(templateWithCaller.headers().get(HEADER_NAME)).containsExactly("joe01doe; type=adAccount");
	}

	@ParameterizedTest
	@NullAndEmptySource
	@ValueSource(strings = " ")
	void testFeignBuilderCustomizerWithoutSentBy(final String sentBy) {
		final var requestInterceptor = createRequestInterceptor(sentBy);

		final var template = new RequestTemplate();
		requestInterceptor.apply(template);
		assertThat(template.headers()).doesNotContainKey(HEADER_NAME);
	}

	private RequestInterceptor createRequestInterceptor(final String sentBy) {
		final var configuration = new SupportManagementConfiguration();

		when(clientRegistrationRepositoryMock.findByRegistrationId(any())).thenReturn(clientRegistrationMock);
		when(propertiesMock.connectTimeout()).thenReturn(1);
		when(propertiesMock.readTimeout()).thenReturn(2);
		when(propertiesMock.sentBy()).thenReturn(sentBy);
		when(feignMultiCustomizerSpy.composeCustomizersToOne()).thenReturn(feignBuilderCustomizerMock);

		try (final MockedStatic<FeignMultiCustomizer> feignMultiCustomizerMock = Mockito.mockStatic(FeignMultiCustomizer.class)) {
			feignMultiCustomizerMock.when(FeignMultiCustomizer::create).thenReturn(feignMultiCustomizerSpy);

			final var customizer = configuration.feignBuilderCustomizer(propertiesMock, clientRegistrationRepositoryMock);

			final ArgumentCaptor<ProblemErrorDecoder> errorDecoderCaptor = ArgumentCaptor.forClass(ProblemErrorDecoder.class);
			final ArgumentCaptor<RequestInterceptor> requestInterceptorCaptor = ArgumentCaptor.forClass(RequestInterceptor.class);

			verify(feignMultiCustomizerSpy).withErrorDecoder(errorDecoderCaptor.capture());
			verify(feignMultiCustomizerSpy).withRequestInterceptor(requestInterceptorCaptor.capture());
			verify(propertiesMock).sentBy();
			verify(clientRegistrationRepositoryMock).findByRegistrationId(CLIENT_ID);
			verify(feignMultiCustomizerSpy).withRetryableOAuth2InterceptorForClientRegistration(same(clientRegistrationMock));
			verify(propertiesMock).connectTimeout();
			verify(propertiesMock).readTimeout();
			verify(feignMultiCustomizerSpy).withRequestTimeoutsInSeconds(1, 2);
			verify(feignMultiCustomizerSpy).composeCustomizersToOne();

			assertThat(errorDecoderCaptor.getValue()).hasFieldOrPropertyWithValue("integrationName", CLIENT_ID);
			assertThat(customizer).isSameAs(feignBuilderCustomizerMock);

			return requestInterceptorCaptor.getValue();
		}
	}
}
