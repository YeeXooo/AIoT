package com.aiot.infra.adapter;

import com.aiot.domain.model.RescueReport;
import com.aiot.domain.port.RescueReportPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 救援报告投递适配器（打桩实现）。
 * <p>
 * 记录救援报告日志，不实际调用 SMN / HTTP API。
 * </p>
 * <p>
 * 设计依据：docs/ood_infrastructure.md §3.4.7
 * </p>
 */
@Component
public class RescueReportAdapter implements RescueReportPort {

    private static final Logger log = LoggerFactory.getLogger(RescueReportAdapter.class);

    @Override
    public void deliverRescueReport(RescueReport report) throws RescueReportException {
        log.info("Rescue report delivered: driver={}, location=({}, {}), occurredAt={}",
                report.driverId().id(),
                report.location().latitude(),
                report.location().longitude(),
                report.occurredAt());

        if (report.latestVitals() != null) {
            log.debug("Latest vitals: heartRate={}, bloodOxygen={}",
                    report.latestVitals().heartRate(),
                    report.latestVitals().bloodOxygen());
        }

        if (report.vehicleStates() != null && !report.vehicleStates().isEmpty()) {
            log.debug("Vehicle states count: {}", report.vehicleStates().size());
        }

        // 打桩实现，不实际投递
    }
}
