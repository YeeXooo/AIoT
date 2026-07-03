package com.aiot.interfaces.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MQTT 设备鉴权提供者。
 * <p>
 * 实现 IoTDA 设备 Token 认证和密码认证两种模式：
 * <ul>
 *   <li><b>Token 模式</b>：设备使用 IoTDA 签发的临时 Token 连接 Broker，
 *       由本服务负责生成 Token 并下发给设备。</li>
 *   <li><b>密码模式</b>：设备使用预设的 deviceId + secret 连接 Broker，
 *       由本服务负责校验设备的身份凭证。</li>
 * </ul>
 * </p>
 * <p>
 * 本期 mock 实现：使用 HMAC-SHA256 生成本地 Token，
 * 设备白名单通过配置注入（不连接真实 IoTDA 设备注册表）。
 * </p>
 * <p>
 * 设计依据：docs/ood_interface.md §5.4、docs/ood_infrastructure.md §3.6
 * </p>
 */
public class MqttDeviceAuthProvider {

    private static final Logger log = LoggerFactory.getLogger(MqttDeviceAuthProvider.class);

    private final MqttProperties properties;

    /** 已注册设备：deviceId → deviceSecret */
    private final Map<String, String> registeredDevices = new ConcurrentHashMap<>();

    /** 已签发的 Token：Token → DeviceTokenInfo */
    private final Map<String, DeviceTokenInfo> issuedTokens = new ConcurrentHashMap<>();

    private static final String HMAC_SHA256 = "HmacSHA256";

    public MqttDeviceAuthProvider(MqttProperties properties) {
        this.properties = properties;
    }

    /**
     * 注册一个设备（例如从配置文件或管理 API 注入）。
     */
    public void registerDevice(String deviceId, String deviceSecret) {
        registeredDevices.put(deviceId, deviceSecret);
        log.info("设备已注册: deviceId={}", deviceId);
    }

    /**
     * 移除设备注册。
     */
    public void unregisterDevice(String deviceId) {
        registeredDevices.remove(deviceId);
        log.info("设备已注销: deviceId={}", deviceId);
    }

    /**
     * 为设备签发 MQTT 连接 Token。
     * Token 格式：{deviceId}:{timestamp}:{signature}
     * 签名 = HMAC-SHA256(deviceId + ":" + timestamp, deviceSecret)
     *
     * @param deviceId 设备标识
     * @return 签发的 Token 字符串，失败时返回 null
     */
    public String issueDeviceToken(String deviceId) {
        String secret = registeredDevices.get(deviceId);
        if (secret == null) {
            log.warn("设备未注册，签发 Token 失败: deviceId={}", deviceId);
            return null;
        }

        String timestamp = String.valueOf(Instant.now().getEpochSecond());
        String signature = hmacSha256(deviceId + ":" + timestamp, secret);

        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(
                (deviceId + ":" + timestamp + ":" + signature).getBytes(StandardCharsets.UTF_8));

        DeviceTokenInfo info = new DeviceTokenInfo(
                deviceId,
                Instant.now(),
                Instant.now().plusSeconds(properties.getDeviceTokenTtlSec()),
                token
        );
        issuedTokens.put(token, info);

        log.info("设备 Token 已签发: deviceId={}, expiresAt={}", deviceId, info.expiresAt);
        return token;
    }

    /**
     * 校验设备 Token。
     *
     * @param token 设备提交的 Token
     * @return 如果 Token 有效且未过期，返回对应的 deviceId；否则返回 null
     */
    public String validateDeviceToken(String token) {
        // 检查签发记录
        DeviceTokenInfo info = issuedTokens.get(token);
        if (info != null) {
            if (info.expiresAt.isAfter(Instant.now())) {
                return info.deviceId;
            }
            issuedTokens.remove(token);
            log.debug("设备 Token 已过期: deviceId={}", info.deviceId);
            return null;
        }

        // 解析 Token 格式验证
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(token), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":", 3);
            if (parts.length != 3) return null;

            String deviceId = parts[0];
            long ts = Long.parseLong(parts[1]);
            Instant tokenTime = Instant.ofEpochSecond(ts);

            if (tokenTime.plusSeconds(properties.getDeviceTokenTtlSec()).isBefore(Instant.now())) {
                log.debug("设备 Token 已过期（解析验证）: deviceId={}", deviceId);
                return null;
            }

            String secret = registeredDevices.get(deviceId);
            if (secret == null) return null;

            String expectedSig = hmacSha256(deviceId + ":" + ts, secret);
            if (expectedSig.equals(parts[2])) {
                return deviceId;
            }
        } catch (Exception e) {
            log.debug("设备 Token 解析失败", e);
        }
        return null;
    }

    /**
     * 校验设备密码（用户名密码模式）。
     */
    public boolean validateDevicePassword(String deviceId, String password) {
        String secret = registeredDevices.get(deviceId);
        if (secret == null) return false;
        return secret.equals(password);
    }

    /**
     * 撤销设备 Token。
     */
    public void revokeToken(String token) {
        issuedTokens.remove(token);
    }

    /**
     * 清理过期 Token。
     */
    public int cleanupExpiredTokens() {
        int before = issuedTokens.size();
        issuedTokens.entrySet().removeIf(e -> e.getValue().expiresAt.isBefore(Instant.now()));
        int removed = before - issuedTokens.size();
        if (removed > 0) {
            log.info("清理过期设备 Token: {} 个", removed);
        }
        return removed;
    }

    /**
     * 是否已注册设备。
     */
    public boolean isDeviceRegistered(String deviceId) {
        return registeredDevices.containsKey(deviceId);
    }

    /**
     * 已注册设备数量。
     */
    public int registeredDeviceCount() {
        return registeredDevices.size();
    }

    private String hmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(keySpec);
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new RuntimeException("HMAC-SHA256 计算失败", e);
        }
    }

    // ── 内部类型 ──

    private record DeviceTokenInfo(
            String deviceId,
            Instant issuedAt,
            Instant expiresAt,
            String token
    ) {}
}
