package se.sundsvall.accessloader.integration.mdviewer.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("integration.mdviewer")
public record MdViewerProperties(int connectTimeout, int readTimeout) {
}
