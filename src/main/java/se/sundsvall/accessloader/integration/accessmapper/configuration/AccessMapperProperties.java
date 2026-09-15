package se.sundsvall.accessloader.integration.accessmapper.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.access-mapper")
public record AccessMapperProperties(int connectTimeout, int readTimeout) {
}
