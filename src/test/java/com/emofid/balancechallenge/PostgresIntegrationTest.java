package com.emofid.balancechallenge;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.annotation.DirtiesContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
public abstract class PostgresIntegrationTest {

	@Container
	@ServiceConnection
	static final PostgreSQLContainer POSTGRES =
			new PostgreSQLContainer(DockerImageName.parse("postgres:17-alpine"));

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void cleanDatabase() {
		jdbcTemplate.update("DELETE FROM balance_operation");
		jdbcTemplate.update("DELETE FROM accounts");
	}

	protected long operationCount(String transactionId) {
		Long count = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM balance_operation WHERE transaction_id = ?",
				Long.class,
				transactionId
		);
		return count == null ? 0 : count;
	}
}
