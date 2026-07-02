package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 固件版本（VO-08）
 * 车载终端版本号、车型范围、升级包摘要，用于版本比对与兼容性校验
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class OTAVersion {
    private final String versionNumber;
    private final String vehicleModelRange;
    private final String packageDigest;

    private OTAVersion(String versionNumber, String vehicleModelRange, String packageDigest) {
        if (versionNumber == null || versionNumber.isBlank()) { throw new IllegalArgumentException("版本号不能为空"); }
        this.versionNumber = versionNumber;
        this.vehicleModelRange = vehicleModelRange;
        this.packageDigest = packageDigest;
    }

    public static OTAVersion of(String versionNumber, String vehicleModelRange, String packageDigest) {
        return new OTAVersion(versionNumber, vehicleModelRange, packageDigest);
    }

    protected OTAVersion() {
        this.versionNumber = "";
        this.vehicleModelRange = "";
        this.packageDigest = "";
    }
}