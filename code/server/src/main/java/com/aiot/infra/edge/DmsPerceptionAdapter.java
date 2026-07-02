package com.aiot.infra.edge;

import com.aiot.domain.port.CameraOcclusionDetectionPort;
import org.springframework.beans.factory.DisposableBean;

/**
 * DMS 视觉感知适配器（存根）。
 * <p>
 * PR #62 引入的 gRPC 适配器，等待完整 proto 文件后启用。
 * 当前为编译占位。
 * </p>
 */
public class DmsPerceptionAdapter implements DisposableBean {

    @SuppressWarnings("unused")
    public DmsPerceptionAdapter(DmsPerceptionProperties props,
                                 CameraOcclusionDetectionPort occlusionPort) {
    }

    @Override
    public void destroy() {
    }
}
