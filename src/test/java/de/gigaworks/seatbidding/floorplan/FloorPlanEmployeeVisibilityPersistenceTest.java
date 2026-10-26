package de.gigaworks.seatbidding.floorplan;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;

import de.gigaworks.seatbidding.auth.PasswordHasher;
import de.gigaworks.seatbidding.persistence.BiddingRoundRepository;
import de.gigaworks.seatbidding.persistence.CapacitySource;
import de.gigaworks.seatbidding.persistence.EmployeeEntity;
import de.gigaworks.seatbidding.persistence.EmployeeRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanEntity;
import de.gigaworks.seatbidding.persistence.FloorPlanRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanSeatState;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionEntity;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionSeatEntity;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionSeatRepository;
import de.gigaworks.seatbidding.persistence.FloorPlanVersionStatus;
import de.gigaworks.seatbidding.persistence.PhysicalSeatEntity;
import de.gigaworks.seatbidding.persistence.PhysicalSeatRepository;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import java.time.Instant;
import org.junit.jupiter.api.Test;

@QuarkusTest
@QuarkusTestResource(value = de.gigaworks.seatbidding.support.PostgresTestResource.class,
        restrictToAnnotatedClass = true)
class FloorPlanEmployeeVisibilityPersistenceTest {

    private static final String EMAIL = "floor-plan-viewer@example.com";
    private static final String PASSWORD = "a valid long password with spaces";

    @Inject
    EmployeeRepository employees;
    @Inject
    PasswordHasher passwordHasher;
    @Inject
    BiddingRoundRepository rounds;
    @Inject
    FloorPlanRepository plans;
    @Inject
    FloorPlanVersionRepository versions;
    @Inject
    PhysicalSeatRepository physicalSeats;
    @Inject
    FloorPlanVersionSeatRepository markers;

    @Test
    void employeeWithoutPreferencesCanReadTheCurrentPlanAndItsActiveSvg() {
        QuarkusTransaction.requiringNew().run(this::createActiveFloorPlan);

        String session = given().contentType(ContentType.URLENC)
                .formParam("j_username", EMAIL).formParam("j_password", PASSWORD)
                .header("Origin", "https://seat.test")
                .header("X-Forwarded-Proto", "https")
                .header("X-Forwarded-For", "203.0.113.55")
                .redirects().follow(false)
                .post("/j_security_check").then().statusCode(200)
                .extract().detailedCookie("seat_session").getValue();

        String svgUrl = given().cookie("seat_session", session)
                .get("/api/floor-plan/current").then().statusCode(200)
                .body("preferenceRevision", equalTo(0))
                .body("seats[0].stars", equalTo(0))
                .extract().jsonPath().getString("svgUrl");

        given().cookie("seat_session", session).get(svgUrl).then().statusCode(200)
                .contentType("image/svg+xml");
    }

    private void createActiveFloorPlan() {
        var employee = new EmployeeEntity();
        employee.email = EMAIL;
        employee.firstName = "Floor";
        employee.lastName = "Plan Viewer";
        employee.enabled = true;
        employee.passwordHash = passwordHasher.hash(PASSWORD);
        employee.passwordSetAt = Instant.now();
        employees.persistAndFlush(employee);

        var plan = new FloorPlanEntity();
        plan.permanentNumberWatermark = 1;
        plans.persistAndFlush(plan);

        var version = new FloorPlanVersionEntity();
        version.floorPlan = plan;
        version.versionNo = 1;
        version.status = FloorPlanVersionStatus.PUBLISHED;
        version.sanitizedSvg = "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 100 100\"/>";
        version.contentSha256 = "a".repeat(64);
        version.viewBoxMinX = BigDecimal.ZERO;
        version.viewBoxMinY = BigDecimal.ZERO;
        version.viewBoxWidth = BigDecimal.valueOf(100);
        version.viewBoxHeight = BigDecimal.valueOf(100);
        version.publishedAt = Instant.now();
        version.createdBy = employee;
        versions.persistAndFlush(version);

        plan.activeVersion = version;
        var seat = new PhysicalSeatEntity();
        seat.floorPlan = plan;
        seat.seatNumber = 1;
        seat.firstPublishedVersion = version;
        physicalSeats.persistAndFlush(seat);

        var marker = new FloorPlanVersionSeatEntity();
        marker.version = version;
        marker.physicalSeat = seat;
        marker.seatNumber = 1;
        marker.normalizedX = BigDecimal.valueOf(0.5);
        marker.normalizedY = BigDecimal.valueOf(0.5);
        marker.state = FloorPlanSeatState.ACTIVE;
        markers.persist(marker);

        var round = rounds.findOpen().orElseThrow();
        round.capacitySource = CapacitySource.FLOOR_PLAN;
        round.floorPlanVersion = version;
        round.seatCapacity = 1;
    }
}