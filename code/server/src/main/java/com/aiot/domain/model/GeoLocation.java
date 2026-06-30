package com.aiot.domain.model;

/**
 * 地理位置值对象。
 * <p>
 * GPS/北斗坐标信息，用于告警定位、热力图展示和救援定位。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md VO-04
 * </p>
 */
public record GeoLocation(
        double latitude,
        double longitude
) {
    public GeoLocation {
        if (latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }
}