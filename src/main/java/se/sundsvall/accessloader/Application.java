package se.sundsvall.accessloader;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.openfeign.EnableFeignClients;
import se.sundsvall.accessloader.configuration.AccessLoaderProperties;
import se.sundsvall.dept44.ServiceApplication;

import static org.springframework.boot.SpringApplication.run;

@EnableFeignClients
@EnableConfigurationProperties(AccessLoaderProperties.class)
@ServiceApplication
public class Application {
	public static void main(final String... args) {
		run(Application.class, args);
	}
}
