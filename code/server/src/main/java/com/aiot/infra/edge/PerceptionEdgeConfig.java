package com.aiot.infra.edge;

import com.aiot.domain.port.CameraOcclusionDetectionPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 边缘侧感知适配器 Spring 配置。
 * <p>
 * yolo 模式下创建 DmsPerceptionAdapter bean，自动连接 Python sidecar。
 * mock 模式下该 bean 不创建，由既有的模拟源负责 DMS 通道。
 * </p>
 * <p>
 * CameraOcclusionDetectionPort 由 infra.adapter 包提供，此处不重复定义。
 * </p>
 * <p>
 * 设计依据：docs/ood_perception_yolo.md §6.1, §J.1
 * </p>
 */
@Configuration
public class PerceptionEdgeConfig {

    private static final Logger log = LoggerFactory.getLogger(PerceptionEdgeConfig.class);

    @Bean
    @ConditionalOnProperty(name = "aiot.perception.dms.mode", havingValue = "yolo", matchIfMissing = false)
    public DmsPerceptionAdapter dmsPerceptionAdapter(
            DmsPerceptionProperties props,
            CameraOcclusionDetectionPort occlusionPort) {
        log.info("Creating DmsPerceptionAdapter (yolo mode)");
        return new DmsPerceptionAdapter(props, occlusionPort);
    }
}
