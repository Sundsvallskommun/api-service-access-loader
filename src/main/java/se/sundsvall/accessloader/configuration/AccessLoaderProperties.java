package se.sundsvall.accessloader.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("accessloader")
public record AccessLoaderProperties(String accessLevel) {
}
