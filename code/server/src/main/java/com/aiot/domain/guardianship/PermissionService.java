package com.aiot.domain.guardianship;

import com.aiot.domain.model.L3DurationTracker;
import com.aiot.domain.model.Permission;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.Result;

public interface PermissionService {
    Result<Permission, AppError> grantAccess(String driverId, String accountId, String reason);

    Result<Void, AppError> revokeAccess(String driverId, String accountId, String reason);

    Result<Permission, AppError> checkPermission(String driverId, String accountId);

    void onL3DurationReached(String driverId, L3DurationTracker tracker);
}
