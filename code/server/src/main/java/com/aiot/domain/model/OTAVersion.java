package com.aiot.domain.model;

import com.aiot.domain.model.exception.BusinessException;
import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 固件版本（VO-08）
 * 车载终端版本号、车型范围、升级包摘要
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class OTAVersion {
    private final String versionNumber;
    private final String vehicleModelRange;
    private final String packageDigest;

    private OTAVersion(String versionNumber, String vehicleModelRange, String packageDigest) {
        if (versionNumber == null || versionNumber.isBlank()) {
            throw new BusinessException(
                    "MODEL_024",
                    "固件版本号不能为空",
                    "OTA_VERSION_VALIDATE"
            );
        }
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
<<<<<<< HEAD
=======

>>>>>>> d61a4a60204c7e68e9b5b3ec725a630abc2e642a
}