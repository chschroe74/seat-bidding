package de.gigaworks.seatbidding.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

class LiquibaseUpgradeTest {

    @Test
    void installsCompleteMasterChangelogIntoAnEmptyDatabase() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("seat_bidding_empty")
                .withUsername("seat_bidding")
                .withPassword("seat_bidding")) {
            postgres.start();
            update(postgres, "db/changelog/db.changelog-master.yaml", postgres.getUsername());
            try (var connection = connection(postgres)) {
                assertEquals(4, scalarInt(connection, "SELECT count(*) FROM databasechangelog"));
                assertEquals(1, scalarInt(connection, "SELECT count(*) FROM information_schema.tables "
                        + "WHERE table_schema='public' AND table_name='seat_reservation'"));
                assertEquals(1, scalarInt(connection, "SELECT count(*) FROM information_schema.columns "
                        + "WHERE table_schema='public' AND table_name='employee' AND column_name='is_admin'"));
            }
        }
    }

    @Test
    void upgradesDeployedSchemaWithoutReinterpretingLegacyDrawsAndReappliesPermissions() throws Exception {
        try (var postgres = new PostgreSQLContainer<>("postgres:18-alpine")
                .withDatabaseName("seat_bidding_upgrade")
                .withUsername("seat_bidding")
                .withPassword("seat_bidding")) {
            postgres.start();
            update(postgres, "db/changelog/db.changelog-deployed-baseline.yaml", postgres.getUsername());
            try (var connection = connection(postgres)) {
                insertLegacyCompletedRound(connection);
                try (var statement = connection.createStatement()) {
                    statement.execute("CREATE ROLE seat_bidding_application");
                }
            }
            update(postgres, "db/changelog/db.changelog-master.yaml", "seat_bidding_application");
            try (var connection = connection(postgres)) {
                assertLegacyData(connection);
                assertPermissions(connection);
            }
            update(postgres, "db/changelog/db.changelog-master.yaml", "seat_bidding_application");
            try (var connection = connection(postgres)) {
            assertEquals(4, scalarInt(connection, "SELECT count(*) FROM databasechangelog"));
            }
        }
    }

    private static void update(PostgreSQLContainer<?> postgres, String changelog, String applicationUser) throws Exception {
        try (var connection = connection(postgres)) {
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase(changelog, new ClassLoaderResourceAccessor(), database)) {
                liquibase.setChangeLogParameter("applicationUser", applicationUser);
                liquibase.update();
            }
        }
    }

    private static Connection connection(PostgreSQLContainer<?> postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    private static void insertLegacyCompletedRound(Connection connection) throws SQLException {
        try (var statement = connection.createStatement()) {
            statement.executeUpdate("""
                    INSERT INTO employee(id,email,first_name,last_name) VALUES
                      (1,'alice@example.com','Alice','Example'),
                      (2,'bob@example.com','Bob','Example'),
                      (3,'carol@example.com','Carol','Example')
                    """);
            statement.executeUpdate("""
                    INSERT INTO bidding_round(id,status,sequence_no,bidding_opens_at,cutoff_at,schedule_zone,
                                              tokens_granted,carry_over_cap,seat_capacity,processed_at)
                    VALUES (1,'COMPLETED',1,'2026-08-01T00:00:00Z','2026-08-07T20:00:00Z',
                            'Europe/Berlin',50,20,2,'2026-08-07T20:00:01Z')
                    """);
            statement.executeUpdate("""
                    INSERT INTO round_date(id,round_id,target_date,ordinal)
                    VALUES (1,1,'2026-08-10',1)
                    """);
            statement.executeUpdate("""
                    INSERT INTO round_participation(id,round_id,employee_id,grant_tokens,carried_in_tokens,
                                                    starting_balance,successful_bid_tokens,remaining_balance,carried_out_tokens)
                    VALUES
                      (1,1,1,50,0,50,20,30,20),
                      (2,1,2,50,0,50,10,40,20),
                      (3,1,3,50,0,50,0,50,20)
                    """);
            statement.executeUpdate("""
                    INSERT INTO bid(id,round_date_id,participation_id,tokens) VALUES
                      (1,1,1,20), (2,1,2,10), (3,1,3,10)
                    """);
            statement.executeUpdate("""
                    INSERT INTO seat_assignment(id,round_date_id,bid_id,assigned,token_rank,final_rank,resolution,
                                                boundary_tie_group,tie_group,draw_value,algorithm_version)
                    VALUES
                      (1,1,1,true,1,1,'FIXED_WINNER',null,null,null,'v2'),
                      (2,1,2,true,2,2,'GLOBAL_TIE_WINNER','date:1:tokens:10',null,null,'v2'),
                      (3,1,3,false,2,3,'GLOBAL_TIE_LOSER','date:1:tokens:10',null,null,'v2')
                    """);
            statement.executeUpdate("""
                    INSERT INTO round_allocation_audit(id,round_id,algorithm_version,input_fingerprint,
                                                       objective_summary,selected_solution_fingerprint,
                                                       random_selection_value)
                    VALUES (1,1,'v2','aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
                            '{"filledUnresolvedSlots":1,"distinctTieWinners":1,"sortedTieWinCounts":[0,1]}',
                            'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb','index:0')
                    """);
            statement.executeUpdate("""
                    INSERT INTO token_ledger(id,employee_id,round_id,bid_id,type,amount,idempotency_key,occurred_at)
                    VALUES
                      (1,1,1,1,'BID_SPEND',-20,'bid:1:spend','2026-08-07T20:00:01Z'),
                      (2,2,1,2,'BID_SPEND',-10,'bid:2:spend','2026-08-07T20:00:01Z')
                    """);
        }
    }

    private static void assertLegacyData(Connection connection) throws SQLException {
        try (var statement = connection.createStatement();
                var results = statement.executeQuery("""
                        SELECT bid_id, assigned, final_rank, token_rank, resolution, boundary_tie_group,
                               tie_group, draw_value, algorithm_version
                        FROM seat_assignment ORDER BY final_rank
                        """)) {
            assertRow(results, 1, true, 1, 1, "FIXED_WINNER", null, null, null);
            assertRow(results, 2, true, 2, 2, "GLOBAL_TIE_WINNER", "date:1:tokens:10", null, null);
            assertRow(results, 3, false, 3, 2, "GLOBAL_TIE_LOSER", "date:1:tokens:10", null, null);
            assertTrue(!results.next());
        }
        assertEquals(1, scalarInt(connection, "SELECT count(*) FROM round_allocation_audit"));
        assertEquals(2, scalarInt(connection, "SELECT count(*) FROM token_ledger"));
        assertEquals(3, scalarInt(connection, "SELECT count(*) FROM employee WHERE is_admin = false"));
        assertEquals(0, scalarInt(connection, "SELECT count(*) FROM seat_reservation"));
    }

    private static void assertRow(ResultSet results, int bidId, boolean assigned, int finalRank, int tokenRank,
            String resolution, String boundaryGroup, String legacyGroup, String legacyDraw) throws SQLException {
        assertTrue(results.next());
        assertEquals(bidId, results.getInt("bid_id"));
        assertEquals(assigned, results.getBoolean("assigned"));
        assertEquals(finalRank, results.getInt("final_rank"));
        assertEquals(tokenRank, results.getInt("token_rank"));
        assertEquals(resolution, results.getString("resolution"));
        assertEquals(boundaryGroup, results.getString("boundary_tie_group"));
        assertEquals(legacyGroup, results.getString("tie_group"));
        assertEquals(legacyDraw, results.getString("draw_value"));
        assertEquals("v2", results.getString("algorithm_version"));
    }

    private static void assertPermissions(Connection connection) throws SQLException {
        assertTrue(scalarBoolean(connection,
                "SELECT has_table_privilege('seat_bidding_application','round_allocation_audit','SELECT,INSERT,UPDATE,DELETE')"));
        assertTrue(scalarBoolean(connection,
                "SELECT has_sequence_privilege('seat_bidding_application','round_allocation_audit_id_seq','USAGE')"));
        assertTrue(scalarBoolean(connection,
                "SELECT has_table_privilege('seat_bidding_application','seat_reservation','SELECT,INSERT,UPDATE,DELETE')"));
        assertTrue(scalarBoolean(connection,
                "SELECT has_sequence_privilege('seat_bidding_application','seat_reservation_id_seq','USAGE')"));
    }

    private static int scalarInt(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static boolean scalarBoolean(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            assertTrue(result.next());
            return result.getBoolean(1);
        }
    }
}
