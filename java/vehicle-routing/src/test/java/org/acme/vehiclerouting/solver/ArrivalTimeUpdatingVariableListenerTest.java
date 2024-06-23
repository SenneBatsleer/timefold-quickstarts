package org.acme.vehiclerouting.solver;

import static io.restassured.RestAssured.get;
import static io.restassured.RestAssured.given;
import io.restassured.http.ContentType;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Arrays;

import jakarta.inject.Inject;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import ai.timefold.solver.core.api.solver.SolverStatus;

import org.acme.vehiclerouting.domain.Location;
import org.acme.vehiclerouting.domain.Vehicle;
import org.acme.vehiclerouting.domain.VehicleRoutePlan;
import org.acme.vehiclerouting.domain.Visit;
import org.acme.vehiclerouting.domain.geo.HaversineDrivingTimeCalculator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.quarkus.test.junit.QuarkusTest;


@QuarkusTest
class ArrivalTimeUpdatingVariableListenerTest {

    /*
     * LOCATION_1 to LOCATION_2 is approx. 11713 m ~843 seconds of driving time
     * LOCATION_2 to LOCATION_3 is approx. 8880 m ~639 seconds of driving time
     * LOCATION_1 to LOCATION_3 is approx. 13075 m ~941 seconds of driving time
     */
    private static final Location LOCATION_1 = new Location(49.288087, 16.562172);
    private static final Location LOCATION_2 = new Location(49.190922, 16.624466);
    private static final Location LOCATION_3 = new Location(49.1767533245638, 16.50422914190477);

    private static final LocalDate TOMORROW = LocalDate.now().plusDays(1);
    @Inject
    ConstraintVerifier<VehicleRoutingConstraintProvider, VehicleRoutePlan> constraintVerifier;

    @BeforeAll
    static void initDrivingTimeMaps() {
        HaversineDrivingTimeCalculator.getInstance().initDrivingTimeMaps(Arrays.asList(LOCATION_1, LOCATION_2, LOCATION_3));
    }

    @Test
    void floatingLunchBreakScheduled() {
        LocalDateTime tomorrow_07_00 = LocalDateTime.of(TOMORROW, LocalTime.of(7, 0));
        LocalDateTime tomorrow_08_00 = LocalDateTime.of(TOMORROW, LocalTime.of(8, 0));
        LocalDateTime tomorrow_08_30 = LocalDateTime.of(TOMORROW, LocalTime.of(8, 30));
        LocalDateTime tomorrow_09_00 = LocalDateTime.of(TOMORROW, LocalTime.of(9, 0));
        LocalDateTime tomorrow_10_00 = LocalDateTime.of(TOMORROW, LocalTime.of(10, 0));
        LocalDateTime tomorrow_18_00 = LocalDateTime.of(TOMORROW, LocalTime.of(18, 0));
        Vehicle vehicleA = new Vehicle("1", 100, LOCATION_1, tomorrow_07_00, tomorrow_18_00, Duration.ofMinutes(30L), tomorrow_08_30);

        Visit visit1 = new Visit("2", "John", LOCATION_2, 80, tomorrow_08_00, tomorrow_08_30, Duration.ofMinutes(30L));
        visit1.setArrivalTime(tomorrow_18_00);
        Visit visit2 = new Visit("3", "Paul", LOCATION_3, 10, tomorrow_08_30, tomorrow_18_00, Duration.ofMinutes(30L));
        visit2.setArrivalTime(tomorrow_18_00.plus(visit1.getServiceDuration()).plus(Duration.ofSeconds(639L)));

        connect(vehicleA, visit1, visit2);

        VehicleRoutePlan vehicleRoutePlan = new VehicleRoutePlan(
            "lunchBreakPlan", new Location(49, 16), new Location(50, 17), tomorrow_07_00, tomorrow_18_00, Arrays.asList(vehicleA), Arrays.asList(visit1, visit2)
        );

        String jobId = given()
                .contentType(ContentType.JSON)
                .body(vehicleRoutePlan)
                .expect().contentType(ContentType.TEXT)
                .when().post("/route-plans")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        await()
                .atMost(Duration.ofMinutes(1))
                .pollInterval(Duration.ofMillis(500L))
                .until(() -> SolverStatus.NOT_SOLVING.name().equals(
                        get("/route-plans/" + jobId + "/status")
                                .jsonPath().get("solverStatus")));

        VehicleRoutePlan solution = get("/route-plans/" + jobId).then().extract().as(VehicleRoutePlan.class);
        assertEquals(SolverStatus.NOT_SOLVING, solution.getSolverStatus());
        assertNotNull(solution.getVehicles());
        assertNotNull(solution.getVisits());
        assertNotNull(solution.getVehicles().get(0).getVisits());
        Vehicle vehicle = solution.getVehicles().get(0);
        assertEquals(vehicle.getVisits().get(1).getStartServiceTime(), 
            tomorrow_08_00.plus(visit1.getServiceDuration()).plus(Duration.ofSeconds(639L)).plus(vehicle.getFloatingBreakDuration()));
        assertEquals(vehicle.getFloatingBreakStartTime(),
            tomorrow_08_00.plus(visit1.getServiceDuration()).plus(Duration.ofSeconds(639L)));
    }

    static void connect(Vehicle vehicle, Visit... visits) {
        vehicle.setVisits(Arrays.asList(visits));
        for (int i = 0; i < visits.length; i++) {
            Visit visit = visits[i];
            visit.setVehicle(vehicle);
            if (i > 0) {
                visit.setPreviousVisit(visits[i - 1]);
            }
            if (i < visits.length - 1) {
                visit.setNextVisit(visits[i + 1]);
            }
        }
    }

}