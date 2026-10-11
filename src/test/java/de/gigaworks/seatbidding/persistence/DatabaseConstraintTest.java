package de.gigaworks.seatbidding.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = de.gigaworks.seatbidding.support.PostgresTestResource.class, restrictToAnnotatedClass = true)
class DatabaseConstraintTest {

    @Inject DataSource dataSource;

    @Test
    void postgresqlConstraintsRejectInvalidBusinessData() throws Exception {
        try (var connection = dataSource.getConnection()) {
            assertThrows(SQLException.class, () -> {
                try (var statement = connection.prepareStatement("insert into employee(email,first_name,last_name) values (?,?,?)")) {
                    statement.setString(1, "MixedCase@example.com");
                    statement.setString(2, "Mixed");
                    statement.setString(3, "Case");
                    statement.executeUpdate();
                }
            });
        }
        try (var connection = dataSource.getConnection()) {
            assertThrows(SQLException.class, () -> {
                try (var statement = connection.prepareStatement(
                        "insert into bidding_round(status,sequence_no,bidding_opens_at,cutoff_at,schedule_zone,tokens_granted,carry_over_cap,seat_capacity) " +
                                "values ('OPEN',1,now(),now()+interval '1 day','Europe/Berlin',50,20,0)")) {
                    statement.executeUpdate();
                }
            });
        }
    }

}