package de.gigaworks.seatbidding.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.concurrent.Executors;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = de.gigaworks.seatbidding.support.PostgresTestResource.class,
        restrictToAnnotatedClass = true)
class ReminderDispatchPersistenceTest {

    private static final Instant WEDNESDAY = Instant.parse("2026-08-12T08:00:00Z");

    @Inject DataSource dataSource;
    @Inject ReminderDispatchService dispatches;
    @Inject FakeWebPushTransport transport;

    @BeforeEach
    void setUp() throws Exception {
        transport.reset(WebPushTransport.SendResult.accepted(201), WebPushTransport.SendResult.permanent(410));
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM web_push_delivery_attempt; DELETE FROM bid_reminder_dispatch; "
                    + "DELETE FROM bid_reminder_suppression; DELETE FROM web_push_subscription; "
                    + "DELETE FROM employee_notification_settings; DELETE FROM bid; DELETE FROM round_date; "
                    + "DELETE FROM round_participation; DELETE FROM bidding_round; DELETE FROM employee");
            statement.executeUpdate("""
                    INSERT INTO employee(id,email,first_name,last_name)
                    VALUES (1001,'eligible@example.com','Eligible','Employee');
                    INSERT INTO bidding_round(id,status,sequence_no,bidding_opens_at,cutoff_at,schedule_zone,
                                              tokens_granted,carry_over_cap,seat_capacity)
                    VALUES (2001,'OPEN',2001,'2026-08-08T00:00:00Z','2026-08-14T20:00:00Z',
                            'Europe/Berlin',60,24,10);
                    INSERT INTO employee_notification_settings(employee_id,bid_reminders_enabled,
                                                               bid_reminder_start_weekday)
                    VALUES (1001,true,'MONDAY');
                    INSERT INTO web_push_subscription(employee_id,endpoint_hash,endpoint,p256dh_key,auth_key,
                                                      device_label,status,last_seen_at)
                    VALUES
                      (1001,repeat('a',64),'https://push.example.test/one','public-one','auth-one',
                       'Browser one','ACTIVE','2026-08-12T08:00:00Z'),
                      (1001,repeat('b',64),'https://push.example.test/two','public-two','auth-two',
                       'Browser two','ACTIVE','2026-08-12T08:00:00Z')
                    """);
        }
    }

    @Test
    void dispatchIsClaimedOnceSendsEverySnapshotAndInvalidatesOnlyPermanentFailures() throws Exception {
        var result = dispatches.dispatch(WEDNESDAY);
        assertEquals(new ReminderDispatchService.Summary(1, 2, 1, 1), result);
        assertEquals(2, transport.messages().size());
        assertEquals(2001, transport.messages().getFirst().roundId());
        assertFalse(transport.messages().getFirst().payload().contains("eligible@example.com"));

        try (var connection = dataSource.getConnection()) {
            assertEquals(1, count(connection, "bid_reminder_dispatch"));
            assertEquals(2, count(connection, "web_push_delivery_attempt"));
            assertEquals("PARTIAL", scalar(connection, "SELECT status FROM bid_reminder_dispatch"));
            assertEquals(1, scalarInt(connection,
                    "SELECT count(*) FROM web_push_subscription WHERE status='ACTIVE'"));
            assertEquals(1, scalarInt(connection,
                    "SELECT count(*) FROM web_push_subscription WHERE status='INVALID' "
                            + "AND endpoint IS NULL AND p256dh_key IS NULL AND auth_key IS NULL"));
        }
        assertEquals(new ReminderDispatchService.Summary(0, 0, 0, 0), dispatches.dispatch(WEDNESDAY));
    }

    @Test
    void positiveBidAndSuppressionControlEligibilityWhileRemovingLastBidRestoresIt() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO round_date(id,round_id,target_date,ordinal) VALUES (3001,2001,'2026-08-17',1);
                    INSERT INTO round_participation(id,round_id,employee_id,grant_tokens,carried_in_tokens,
                                                    starting_balance)
                    VALUES (4001,2001,1001,60,0,60);
                    INSERT INTO bid(id,round_date_id,participation_id,tokens,attendance_period)
                    VALUES (5001,3001,4001,1,'FULL_DAY')
                    """);
        }
        assertEquals(0, dispatches.dispatch(WEDNESDAY).dispatches());

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM bid WHERE id=5001");
        }
        transport.reset(WebPushTransport.SendResult.accepted(201), WebPushTransport.SendResult.accepted(201));
        assertEquals(1, dispatches.dispatch(WEDNESDAY).dispatches());

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM web_push_delivery_attempt; DELETE FROM bid_reminder_dispatch");
            statement.executeUpdate("INSERT INTO bid_reminder_suppression(round_id,employee_id) VALUES (2001,1001)");
            statement.executeUpdate("UPDATE employee_notification_settings SET bid_reminders_enabled=false "
                    + "WHERE employee_id=1001; UPDATE employee_notification_settings SET bid_reminders_enabled=true "
                    + "WHERE employee_id=1001");
        }
        assertEquals(0, dispatches.dispatch(WEDNESDAY).dispatches());

        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE bidding_round SET status='COMPLETED', "
                    + "processing_started_at='2026-08-14T20:00:00Z', processed_at='2026-08-14T20:00:01Z' "
                    + "WHERE id=2001");
            statement.executeUpdate("""
                    INSERT INTO bidding_round(id,status,sequence_no,bidding_opens_at,cutoff_at,schedule_zone,
                                              tokens_granted,carry_over_cap,seat_capacity,predecessor_round_id)
                    VALUES (2002,'OPEN',2002,'2026-08-15T00:00:00Z','2026-08-21T20:00:00Z',
                            'Europe/Berlin',60,24,10,2001)
                    """);
        }
        transport.reset(WebPushTransport.SendResult.accepted(201), WebPushTransport.SendResult.accepted(201));
        assertEquals(1, dispatches.dispatch(Instant.parse("2026-08-17T08:00:00Z")).dispatches());
    }

    @Test
    void concurrentExecutionsStillCreateOneLogicalDispatch() throws Exception {
        transport.reset(WebPushTransport.SendResult.accepted(201), WebPushTransport.SendResult.accepted(201));
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> dispatches.dispatch(WEDNESDAY));
            var second = executor.submit(() -> dispatches.dispatch(WEDNESDAY));
            assertEquals(1, first.get().dispatches() + second.get().dispatches());
        }
        try (var connection = dataSource.getConnection()) {
            assertEquals(1, count(connection, "bid_reminder_dispatch"));
            assertEquals(2, count(connection, "web_push_delivery_attempt"));
        }
    }

    @Test
    void disabledPreferenceFutureWeekdayAndNoActiveDeviceAreIneligible() throws Exception {
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE employee_notification_settings SET bid_reminders_enabled=false");
        }
        assertEquals(0, dispatches.dispatch(WEDNESDAY).dispatches());
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE employee_notification_settings SET bid_reminders_enabled=true, "
                    + "bid_reminder_start_weekday='FRIDAY'");
        }
        assertEquals(0, dispatches.dispatch(WEDNESDAY).dispatches());
        try (var connection = dataSource.getConnection(); var statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE employee_notification_settings SET bid_reminder_start_weekday='MONDAY'");
            statement.executeUpdate("UPDATE web_push_subscription SET status='USER_REMOVED', endpoint=NULL, "
                    + "p256dh_key=NULL, auth_key=NULL, invalidated_at='2026-08-12T08:00:00Z'");
        }
        assertEquals(0, dispatches.dispatch(WEDNESDAY).dispatches());
    }

    private static int count(Connection connection, String table) throws SQLException {
        return scalarInt(connection, "SELECT count(*) FROM " + table);
    }

    private static int scalarInt(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private static String scalar(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getString(1);
        }
    }

}