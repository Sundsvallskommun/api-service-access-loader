package se.sundsvall.accessloader.integration.supportmanagement.configuration;

import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.dept44.configuration.feign.FeignConfiguration;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;
import se.sundsvall.dept44.support.Identifier;

import static se.sundsvall.dept44.support.Identifier.HEADER_NAME;
import static se.sundsvall.dept44.support.Identifier.Type.AD_ACCOUNT;

@Import(FeignConfiguration.class)
public class SupportManagementConfiguration {

	public static final String CLIENT_ID = "support-management";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(final SupportManagementProperties properties, final ClientRegistrationRepository clientRegistrationRepository) {
		final var sentBy = Identifier.create()
			.withType(AD_ACCOUNT)
			.withValue(properties.sentBy())
			.toHeaderValue();

		return FeignMultiCustomizer.create()
			.withErrorDecoder(new ProblemErrorDecoder(CLIENT_ID))
			// SupportManagement authorizes by the X-Sent-By header, which a scheduled job has none of. The service always acts
			// as the configured identity, replacing any identity of a calling user
			.withRequestInterceptor(template -> template.removeHeader(HEADER_NAME).header(HEADER_NAME, sentBy))
			.withRetryableOAuth2InterceptorForClientRegistration(clientRegistrationRepository.findByRegistrationId(CLIENT_ID))
			.withRequestTimeoutsInSeconds(properties.connectTimeout(), properties.readTimeout())
			.composeCustomizersToOne();
	}
}
