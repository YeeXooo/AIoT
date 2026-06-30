package com.aiot.domain.shared;

public record AlertId(String id) {
    // AlertId 不是聚合根标识，但为了兼容性保留 generate 方法
    public static AlertId generate() { return new AlertId(java.util.UUID.randomUUID().toString()); }
}
