package se.sundsvall.accessloader.integration.supportmanagement.configuration;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
		final var configuration = new SupportManagementConfiguration();

		when(clientRegistrationRepositoryMock.findByRegistrationId(any())).thenReturn(clientRegistrationMock);
		when(propertiesMock.connectTimeout()).thenReturn(1);
		when(propertiesMock.readTimeout()).thenReturn(2);
		when(propertiesMock.sentBy()).thenReturn("svc01acc");
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

			// The configured identity replaces any identity of a calling user
			final var template = new RequestTemplate().header(HEADER_NAME, "joe01doe; type=adAccount");
			requestInterceptorCaptor.getValue().apply(template);
			assertThat(template.headers().get(HEADER_NAME)).containsExactly("svc01acc; type=adAccount");
		}
	}
}
