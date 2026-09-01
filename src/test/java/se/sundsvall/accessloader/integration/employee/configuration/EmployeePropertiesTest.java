package se.sundsvall.accessloader.integration.employee.configuration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import se.sundsvall.accessloader.Application;
import se.sundsvall.accessloader.integration.employee.EmployeeClient;
import se.sundsvall.accessloader.integration.mdviewer.MdViewerClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Application.class)
@ActiveProfiles("junit")
class EmployeePropertiesTest {

	@MockitoBean
	private MdViewerClient mdViewerClient;

	@MockitoBean
	private EmployeeClient employeeClient;

	@Autowired
	private EmployeeProperties properties;

	@Test
	void testProperties() {
		assertThat(properties.connectTimeout()).isEqualTo(5);
		assertThat(properties.readTimeout()).isEqualTo(30);
	}
}
