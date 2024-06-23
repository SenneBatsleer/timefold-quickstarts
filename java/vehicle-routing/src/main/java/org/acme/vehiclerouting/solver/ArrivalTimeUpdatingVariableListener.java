package org.acme.vehiclerouting.solver;

import java.time.LocalDateTime;
import java.util.Objects;

import ai.timefold.solver.core.api.domain.variable.VariableListener;
import ai.timefold.solver.core.api.score.director.ScoreDirector;

import org.acme.vehiclerouting.domain.Visit;
import org.acme.vehiclerouting.domain.Vehicle;
import org.acme.vehiclerouting.domain.VehicleRoutePlan;

public class ArrivalTimeUpdatingVariableListener implements VariableListener<VehicleRoutePlan, Visit> {

    private static final String ARRIVAL_TIME_FIELD = "arrivalTime";

    @Override
    public void beforeVariableChanged(ScoreDirector<VehicleRoutePlan> scoreDirector, Visit visit) {

    }

    @Override
    public void afterVariableChanged(ScoreDirector<VehicleRoutePlan> scoreDirector, Visit visit) {
        if (visit.getVehicle() == null) {
            if (visit.getArrivalTime() != null) {
                scoreDirector.beforeVariableChanged(visit, ARRIVAL_TIME_FIELD);
                visit.setArrivalTime(null);
                scoreDirector.afterVariableChanged(visit, ARRIVAL_TIME_FIELD);
            }
            return;
        }
        
        Vehicle vehicle = visit.getVehicle();
        LocalDateTime floatingBreakTriggerTime = vehicle.getFloatingBreakTriggerTime();

        Visit previousVisit = visit.getPreviousVisit();
        LocalDateTime departureTime =
                previousVisit == null ? vehicle.getDepartureTime() : previousVisit.getDepartureTime();

        Visit nextVisit = visit;
        LocalDateTime arrivalTime = calculateArrivalTime(nextVisit, departureTime);

        boolean isFirstVisitAfterTriggerTime = 
            (floatingBreakTriggerTime == null || arrivalTime.isAfter(floatingBreakTriggerTime)) ? true : false;
        boolean firstVisitAfterTriggerTimeFound = isFirstVisitAfterTriggerTime;

        LocalDateTime effectiveArrivalTime = calculateEffectiveArrivalTime(nextVisit, arrivalTime, isFirstVisitAfterTriggerTime);

        while (nextVisit != null && !Objects.equals(nextVisit.getArrivalTime(), effectiveArrivalTime)) {
            scoreDirector.beforeVariableChanged(nextVisit, ARRIVAL_TIME_FIELD);
            nextVisit.setArrivalTime(effectiveArrivalTime);
            scoreDirector.afterVariableChanged(nextVisit, ARRIVAL_TIME_FIELD);
            departureTime = nextVisit.getDepartureTime();
            nextVisit = nextVisit.getNextVisit();
            arrivalTime = calculateArrivalTime(nextVisit, departureTime);
            if (firstVisitAfterTriggerTimeFound == true) {
                effectiveArrivalTime = arrivalTime;
            } else {
                isFirstVisitAfterTriggerTime = 
                    arrivalTime.isAfter(floatingBreakTriggerTime) ? true : false;
                firstVisitAfterTriggerTimeFound = isFirstVisitAfterTriggerTime;
                effectiveArrivalTime = calculateEffectiveArrivalTime(nextVisit, arrivalTime, isFirstVisitAfterTriggerTime);
            }
        }
    }

    @Override
    public void beforeEntityAdded(ScoreDirector<VehicleRoutePlan> scoreDirector, Visit visit) {

    }

    @Override
    public void afterEntityAdded(ScoreDirector<VehicleRoutePlan> scoreDirector, Visit visit) {

    }

    @Override
    public void beforeEntityRemoved(ScoreDirector<VehicleRoutePlan> scoreDirector, Visit visit) {

    }

    @Override
    public void afterEntityRemoved(ScoreDirector<VehicleRoutePlan> scoreDirector, Visit visit) {

    }

    private LocalDateTime calculateArrivalTime(Visit visit, LocalDateTime previousDepartureTime) {
        if (visit == null || previousDepartureTime == null) {
            return null;
        }
        return previousDepartureTime.plusSeconds(visit.getDrivingTimeSecondsFromPreviousStandstill());
    }

    private LocalDateTime calculateEffectiveArrivalTime(Visit visit, LocalDateTime arrivalTime, boolean firstArrivalAfterTriggerTime) {
        if (visit == null || arrivalTime == null) {
            return null;
        }
        if (firstArrivalAfterTriggerTime == false) {
            return arrivalTime;
        }
        return arrivalTime.plus(visit.getVehicle().getFloatingBreakDuration());
    }
}
