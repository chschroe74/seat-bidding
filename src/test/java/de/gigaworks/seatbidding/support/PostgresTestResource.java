package de.gigaworks.seatbidding.support;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import java.util.Map;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresTestResource implements QuarkusTestResourceLifecycleManager {
    private PostgreSQLContainer<?> postgres;

    @Override
    public Map<String, String> start() {
        postgres = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("seat_bidding_test")
                .withUsername("seat_bidding")
                .withPassword("seat_bidding");
        postgres.start();
        return Map.of(
                "db.username", postgres.getUsername(),
                "quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword());
    }

    @Override
    public void stop() {
        if (postgres != null) postgres.stop();
    }
}
