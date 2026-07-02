package com.aiot.domain.guardianship;

import com.aiot.domain.model.DriverStatusSnapshot;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

public interface DriverStatusBroadcastService {
    DriverStatusSnapshot sampleDriverStatus(String driverId);

    Result<Void, AppError> broadcastStatus(String driverId);
}
