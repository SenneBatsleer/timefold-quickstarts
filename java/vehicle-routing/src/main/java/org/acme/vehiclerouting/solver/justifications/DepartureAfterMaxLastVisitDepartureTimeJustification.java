package org.acme.vehiclerouting.solver.justifications;

import ai.timefold.solver.core.api.score.stream.ConstraintJustification;

public record DepartureAfterMaxLastVisitDepartureTimeJustification(String vehicleId, long lastVisitDepartureTimeDelayInMinutes,
        String description) implements ConstraintJustification {

    public DepartureAfterMaxLastVisitDepartureTimeJustification(String vehicleId, long lastVisitDepartureTimeDelayInMinutes) {
        this(vehicleId, lastVisitDepartureTimeDelayInMinutes, "Vehicle '%s' departed %s minutes too late from its last visit."
                .formatted(vehicleId, lastVisitDepartureTimeDelayInMinutes));
    }
}
