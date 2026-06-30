package com.aiot.domain.shared;

public record AppError(String code, String message, Object... args) {

    public static AppError notFound(String resource, String id) {
        return new AppError("NotFound", resource + " not found: " + id);
    }

    public static AppError accessDenied(String reason) {
        return new AppError("AccessDenied", reason);
    }

    public static AppError invalidState(String message) {
        return new AppError("InvalidState", message);
    }

    public static AppError validationFailed(String message) {
        return new AppError("ValidationFailed", message);
    }

    public static AppError iotdaChannelFailure(String message) {
        return new AppError("IoTDAChannelFailure", message);
    }

    public static AppError upgradeTaskNotCancellable(String taskId, String stage) {
        return new AppError("UpgradeTaskNotCancellable",
                "Task " + taskId + " not cancellable in stage " + stage);
    }

    public static AppError upgradeInProgress(String vehicleId) {
        return new AppError("UpgradeInProgress", "Upgrade in progress for vehicle " + vehicleId);
    }

    public static AppError upgradeAlreadyFinished(String vehicleId, String status) {
        return new AppError("UpgradeAlreadyFinished",
                "Upgrade already finished for vehicle " + vehicleId + " with status " + status);
    }
}
