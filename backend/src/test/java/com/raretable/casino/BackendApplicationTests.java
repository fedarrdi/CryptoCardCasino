package com.raretable.casino;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestConfiguration.class)
class BackendApplicationTests {

	@Test
	void contextLoads() {
	}

}
