package se.sundsvall.accessloader.integration.supportmanagement.configuration;

import java.util.Optional;
import org.springframework.cloud.openfeign.FeignBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import se.sundsvall.dept44.configuration.feign.FeignConfiguration;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.decoder.ProblemErrorDecoder;
import se.sundsvall.dept44.support.Identifier;

import static java.util.function.Predicate.not;
import static se.sundsvall.dept44.support.Identifier.HEADER_NAME;
import static se.sundsvall.dept44.support.Identifier.Type.AD_ACCOUNT;

@Import(FeignConfiguration.class)
public class SupportManagementConfiguration {

	public static final String CLIENT_ID = "support-management";

	@Bean
	FeignBuilderCustomizer feignBuilderCustomizer(final SupportManagementProperties properties, final ClientRegistrationRepository clientRegistrationRepository) {
		final var sentBy = Optional.ofNullable(properties.sentBy())
			.filter(not(String::isBlank))
			.map(value -> Identifier.create()
				.withType(AD_ACCOUNT)
				.withValue(value)
				.toHeaderValue());

		return FeignMultiCustomizer.create()
			.withErrorDecoder(new ProblemErrorDecoder(CLIENT_ID))
			// SupportManagement authorizes by the X-Sent-By header, which a scheduled job has none of. A calling user keeps
			// its own identity, otherwise the service acts as the configured one
			.withRequestInterceptor(template -> sentBy
				.filter(value -> !template.headers().containsKey(HEADER_NAME))
				.ifPresent(value -> template.header(HEADER_NAME, value)))
			.withRetryableOAuth2InterceptorForClientRegistration(clientRegistrationRepository.findByRegistrationId(CLIENT_ID))
			.withRequestTimeoutsInSeconds(properties.connectTimeout(), properties.readTimeout())
			.composeCustomizersToOne();
	}
}
