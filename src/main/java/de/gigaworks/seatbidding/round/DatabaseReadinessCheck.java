package de.gigaworks.seatbidding.round;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.sql.Connection;
import javax.sql.DataSource;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.Readiness;

@Readiness
@ApplicationScoped
public class DatabaseReadinessCheck implements HealthCheck {
    
    @Inject
    DataSource dataSource;
    
    @Override
    public HealthCheckResponse call() {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("select count(*) from databasechangelog");
             var result = statement.executeQuery()) {
            result.next();
            return HealthCheckResponse.named("database-and-liquibase").up()
                    .withData("appliedChangesets", result.getLong(1)).build();
        }
        catch (Exception _) {
            return HealthCheckResponse.named("database-and-liquibase").down().build();
        }
    }
    
}

