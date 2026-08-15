package de.gigaworks.seatbidding.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = de.gigaworks.seatbidding.support.PostgresTestResource.class, restrictToAnnotatedClass = true)
class LiquibaseSchemaTest {

    @Inject DataSource dataSource;

    @Test
    void fullChangelogCreatesProductionSchema() throws Exception {
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "select count(*) from information_schema.tables where table_schema='public' and table_name in " +
                             "('employee','account_activation','bidding_round','round_date','round_participation','bid','seat_reservation','allocation_unit','seat_assignment','round_allocation_audit','token_ledger','employee_notification_settings','web_push_subscription','bid_reminder_suppression','bid_reminder_dispatch','web_push_delivery_attempt')")) {
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(16, result.getInt(1));
            }
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "select count(*) from information_schema.tables where table_schema='public' and table_name='auth_session'")) {
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement("select count(*) from databasechangelog")) {
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(7, result.getInt(1));
            }
        }
        try (var connection = dataSource.getConnection();
             var statement = connection.prepareStatement(
                     "select count(*) from pg_constraint where conrelid='seat_reservation'::regclass and contype='c'")) {
            try (var result = statement.executeQuery()) {
                assertTrue(result.next());
                assertEquals(3, result.getInt(1));
            }
        }
    }

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