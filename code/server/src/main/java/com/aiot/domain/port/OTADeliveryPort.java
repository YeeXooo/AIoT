package com.aiot.domain.port;

import com.aiot.domain.shared.VehicleId;

import java.util.Optional;

/**
 * OTA 升级包下发端口。
 * <p>
 * 负责将升级包通过 IoTDA 下发至车载终端，支持断点续传。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md 决策 19 端口 4
 * </p>
 */
public interface OTADeliveryPort {

    /**
     * 下发升级包。
     *
     * @param vehicleId  目标车辆标识
     * @param pkg        升级包信息
     * @param resumeFrom 断点续传偏移量（Optional.empty() 表示全新下发）
     * @return 传输进度
     * @throws OTADeliveryException 下发异常
     */
    DeliveryProgress deliverPackage(VehicleId vehicleId, OTAPackage pkg,
                                    Optional<Long> resumeFrom) throws OTADeliveryException;

    /**
     * 取消进行中的升级包下发。
     *
     * @param vehicleId 目标车辆标识
     */
    void cancelDelivery(VehicleId vehicleId);

    /**
     * 升级包信息。
     *
     * @param version    目标版本号
     * @param size       升级包大小（字节）
     * @param checksum   校验和
     * @param totalShards 总分片数
     */
    record OTAPackage(
            String version,
            long size,
            String checksum,
            int totalShards
    ) { }

    /**
     * 传输进度。
     *
     * @param transferredBytes 已传输字节数
     * @param totalBytes       总字节数
     * @param completed        是否完成
     */
    record DeliveryProgress(
            long transferredBytes,
            long totalBytes,
            boolean completed
    ) { }

    /**
     * OTA 下发异常。
     */
    sealed class OTADeliveryException extends Exception permits
            OTADeliveryException.TransmissionFailedException,
            OTADeliveryException.ChecksumMismatchException {

        public OTADeliveryException(String message) {
            super(message);
        }

        /**
         * 传输中断。
         */
        public static final class TransmissionFailedException extends OTADeliveryException {
            public TransmissionFailedException(String message) {
                super(message);
            }
        }

        /**
         * 校验失败。
         */
        public static final class ChecksumMismatchException extends OTADeliveryException {
            public ChecksumMismatchException(String message) {
                super(message);
            }
        }
    }
}
