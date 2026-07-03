package com.aiot.interfaces.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/v1/vehicles")
public class VehiclesController {

    record WindowStatusEntry(String windowPosition, String state, String lastOperation,
                              String lastOperationResult, String updatedAt) {}
    record WindowStatusResponse(List<WindowStatusEntry> windowStatuses) {}

    @GetMapping("/{vehicleId}/windows")
    public ResponseEntity<WindowStatusResponse> queryWindowStatus(@PathVariable String vehicleId) {
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        List<WindowStatusEntry> statuses = List.of(
                new WindowStatusEntry("FRONT_LEFT", "CLOSED", "CLOSE", "SUCCESS", now),
                new WindowStatusEntry("FRONT_RIGHT", "CLOSED", "CLOSE", "SUCCESS", now),
                new WindowStatusEntry("REAR_LEFT", "OPEN", "OPEN", "SUCCESS", now),
                new WindowStatusEntry("REAR_RIGHT", "CLOSED", "CLOSE", "SUCCESS", now)
        );

        return ResponseEntity.ok(new WindowStatusResponse(statuses));
    }
}
