package com.aiot.domain.model;

import jakarta.persistence.Embeddable;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * 地理位置（VO-04）
 * GPS/北斗坐标对，用于告警定位、救援定位、热力图
 */
@Embeddable
@Getter
@EqualsAndHashCode
public final class GeoLocation {
    private final double longitude;
    private final double latitude;

    private GeoLocation(double longitude, double latitude) {
        if (longitude < -180 || longitude > 180) throw new IllegalArgumentException("经度范围不合法");
        if (latitude < -90 || latitude > 90) throw new IllegalArgumentException("纬度范围不合法");
        this.longitude = longitude;
        this.latitude = latitude;
    }

    public static GeoLocation of(double longitude, double latitude) {
        return new GeoLocation(longitude, latitude);
    }

    protected GeoLocation() {
        this.longitude = 0;
        this.latitude = 0;
    }
}