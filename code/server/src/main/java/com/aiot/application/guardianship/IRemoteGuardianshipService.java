package com.aiot.application.guardianship;

import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.AppError;
import com.aiot.domain.shared.DriverId;
import com.aiot.domain.shared.Result;

import java.util.List;

public interface IRemoteGuardianshipService {

    Result<SubscribeResponse, AppError> subscribeDriverStatus(AccountId accountId, DriverId driverId);

    Result<Void, AppError> unsubscribeDriverStatus(AccountId accountId, DriverId driverId);

    Result<MediaSessionResponse, AppError> requestMediaSession(
            AccountId accountId, DriverId driverId, String sessionType);

    Result<Void, AppError> endMediaSession(String sessionHandle);

    Result<Void, AppError> updateNotificationPreference(
            AccountId accountId, DriverId driverId, List<String> riskLevels);

    Result<TriggerRescueResponse, AppError> triggerManualRescue(
            AccountId accountId, DriverId driverId);

    Result<GuardianshipPermissionsResponse, AppError> queryGuardianshipPermissions(
            AccountId accountId, DriverId driverId);

    Result<VehicleWindowResponse, AppError> controlVehicleWindow(
            AccountId accountId, DriverId driverId, String windowPosition);

    Result<WindowStatusResponse, AppError> queryWindowStatus(
            AccountId accountId, DriverId driverId);

    Result<SparkRTCTokenResponse, AppError> issueSparkRTCToken(
            AccountId accountId, DriverId driverId, String channelName);

    record SubscribeResponse(String accountId, String driverId, String status) { }

    record MediaSessionResponse(String sessionHandle, String sessionType, String driverId, String status) { }

    record TriggerRescueResponse(String rescueReportId, String status) { }

    record GuardianshipPermissionsResponse(
            String accountId, String driverId,
            List<String> permissions, boolean isRevoked
    ) { }

    record VehicleWindowResponse(String driverId, String windowPosition, String status) { }

    record WindowStatusResponse(String driverId, String windowPosition, boolean isClosed) { }

    record SparkRTCTokenResponse(String token, String channelName, long expireTime) { }
}
