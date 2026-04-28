package org.sainm.schemapilot;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.sainm.schemapilot.project.ProjectRepository;

@SpringBootTest(properties = {
		"spring.flyway.enabled=false",
		"spring.autoconfigure.exclude=org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration,org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration,org.springframework.boot.jooq.autoconfigure.JooqAutoConfiguration"
})
class BackendApplicationTests {
	@MockitoBean
	ProjectRepository projectRepository;

	@Test
	void contextLoads() {
	}

}
