package com.aiot.infra.adapter;

import com.aiot.domain.port.OTADeliveryPort;
import com.aiot.domain.shared.VehicleId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * OTA 升级包下发适配器（打桩实现）。
 * <p>
 * 记录日志，模拟传输进度。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.4.5
 * </p>
 */
@Component
public class OTADeliveryAdapter implements OTADeliveryPort {

    private static final Logger log = LoggerFactory.getLogger(OTADeliveryAdapter.class);

    @Override
    public DeliveryProgress deliverPackage(VehicleId vehicleId, OTAPackage pkg,
                                           Optional<Long> resumeFrom) throws OTADeliveryException {
        log.info("OTA delivery started: vehicle={}, version={}, size={} bytes, resumeFrom={}",
                vehicleId.id(), pkg.version(), pkg.size(),
                resumeFrom.map(Object::toString).orElse("beginning"));

        // 模拟传输完成
        DeliveryProgress progress = new DeliveryProgress(
                pkg.size(),
                pkg.size(),
                true
        );

        log.info("OTA delivery completed: vehicle={}, progress={}/{}",
                vehicleId.id(), progress.transferredBytes(), progress.totalBytes());

        return progress;
    }

    @Override
    public void cancelDelivery(VehicleId vehicleId) {
        log.info("OTA delivery cancelled: vehicle={}", vehicleId.id());
    }
}
