package com.aiot.interfaces.rest;

import com.aiot.application.AlertApplicationService;
import com.aiot.application.TripApplicationService;
import com.aiot.domain.model.SafetyAlertEvent;
import com.aiot.domain.model.Trip;
import com.aiot.domain.model.Vehicle;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/safety")
public class SafetyController {

    private final TripApplicationService tripService;
    private final AlertApplicationService alertService;

    public SafetyController(TripApplicationService tripService, AlertApplicationService alertService) {
        this.tripService = tripService;
        this.alertService = alertService;
    }

    @GetMapping("/trip/list")
    public List<Trip> listTrips(
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) Boolean active) {
        return tripService.listTrips(driverId, active);
    }

    @GetMapping("/alert/list")
    public List<SafetyAlertEvent> listAlerts(
            @RequestParam(required = false) String driverId,
            @RequestParam(required = false) String riskLevel,
            @RequestParam(required = false) String alertType) {
        return alertService.listAlerts(driverId, riskLevel, alertType);
    }

    @GetMapping("/vehicle/list")
    public List<Vehicle> listVehicles(
            @RequestParam(required = false) String fleetId,
            @RequestParam(required = false) String keyword) {
        return tripService.listVehicles(fleetId, keyword);
    }
}
