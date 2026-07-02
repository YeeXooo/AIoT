package com.aiot.infra.edge;

import com.aiot.domain.model.SensorReading;
import com.aiot.domain.model.SensorReading.SensorType;
import com.aiot.domain.port.CameraOcclusionDetectionPort;
import com.aiot.domain.port.CameraOcclusionDetectionPort.OcclusionDetectedSignal;
import com.aiot.domain.port.CameraOcclusionDetectionPort.OcclusionType;
import com.aiot.domain.shared.TripId;
import com.aiot.infra.edge.grpc.DmsPerceptionGrpc;
import com.aiot.infra.edge.grpc.DmsPerceptionGrpc.DmsPerceptionStub;
import com.aiot.infra.edge.grpc.DmsPerceptionOuterClass.ControlSignal;
import com.aiot.infra.edge.grpc.DmsPerceptionOuterClass.DmsFeatureFrame;
import com.aiot.infra.edge.grpc.DmsPerceptionOuterClass.FrameRateAdjust;
import com.aiot.infra.edge.grpc.DmsPerceptionOuterClass.HealthRequest;
import com.aiot.infra.edge.grpc.DmsPerceptionOuterClass.HealthResponse;
import com.aiot.infra.edge.grpc.DmsPerceptionOuterClass.Shutdown;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * DMS 视觉感知适配器。
 * <p>
 * 衔接 Python YOLO sidecar 与 Java 领域层：
 * <ol>
 *   <li>通过 gRPC 消费 Python sidecar 产出的 DmsFeatureFrame 流</li>
 *   <li>校验 → 组装 SensorReading(DMS_CAMERA)</li>
 *   <li>sidecar 断连 → DMS 通道不可用，判定返回 InputInvalid</li>
 *   <li>遮挡经 CameraOcclusionDetectionPort 回调</li>
 * </ol>
 * 设计依据：docs/ood_perception_yolo.md §J.1–J.5
 * </p>
 */
public class DmsPerceptionAdapter implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DmsPerceptionAdapter.class);

    private final DmsPerceptionProperties props;
    private final CameraOcclusionDetectionPort occlusionPort;
    private final AtomicBoolean dmsAvailable = new AtomicBoolean(false);
    private final AtomicLong lastFrameMs = new AtomicLong(0);
    private final AtomicLong lastFrameSeq = new AtomicLong(-1);
    private final AtomicLong lostFrameCount = new AtomicLong(0);

    private ManagedChannel channel;
    private DmsPerceptionStub asyncStub;
    private StreamObserver<ControlSignal> controlStream;
    private Consumer<SensorReading> readingConsumer;
    private Consumer<OcclusionDetectedSignal> occlusionCallback;

    private TripId activeTripId;

    public DmsPerceptionAdapter(DmsPerceptionProperties props,
                                CameraOcclusionDetectionPort occlusionPort) {
        this.props = Objects.requireNonNull(props);
        this.occlusionPort = Objects.requireNonNull(occlusionPort);
    }

    public void setReadingConsumer(Consumer<SensorReading> consumer) {
        this.readingConsumer = Objects.requireNonNull(consumer);
    }

    public void setActiveTripId(TripId tripId) {
        this.activeTripId = tripId;
    }

    // ── 生命周期 ────────────────────────────────────────

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!props.isYoloMode()) {
            log.info("DMS perception mode=mock, adapter not started");
            return;
        }
        log.info("DMS perception mode=yolo, connecting to {}:{}",
                props.getGrpcHost(), props.getGrpcPort());
        connect();
    }

    private void connect() {
        channel = ManagedChannelBuilder
                .forAddress(props.getGrpcHost(), props.getGrpcPort())
                .usePlaintext()
                .keepAliveTime(30, TimeUnit.SECONDS)
                .keepAliveTimeout(10, TimeUnit.SECONDS)
                .build();
        startStreaming();
    }

    @Override
    public void destroy() {
        disconnect();
    }

    private void disconnect() {
        dmsAvailable.set(false);
        if (controlStream != null) {
            try {
                controlStream.onCompleted();
            } catch (Exception ignored) { }
            controlStream = null;
        }
        if (channel != null && !channel.isShutdown()) {
            channel.shutdown();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ── gRPC 流式消费 ──────────────────────────────────

    private void startStreaming() {
        asyncStub = DmsPerceptionGrpc.newStub(channel);

        StreamObserver<DmsFeatureFrame> responseObserver = new StreamObserver<>() {
            @Override
            public void onNext(DmsFeatureFrame frame) {
                handleFeatureFrame(frame);
            }

            @Override
            public void onError(Throwable t) {
                log.error("gRPC stream error: {}", t.getMessage());
                dmsAvailable.set(false);
                scheduleReconnect();
            }

            @Override
            public void onCompleted() {
                log.warn("gRPC stream completed by sidecar");
                dmsAvailable.set(false);
                scheduleReconnect();
            }
        };

        controlStream = asyncStub.streamFeatures(responseObserver);
        dmsAvailable.set(true);
        log.info("DMS feature stream established");
    }

    // ── 特征帧处理 ─────────────────────────────────────

    private void handleFeatureFrame(DmsFeatureFrame frame) {
        Instant ts = Instant.ofEpochMilli(frame.getTimestampMs());

        // 丢帧检测
        long seq = frame.getFrameSeq();
        long prev = lastFrameSeq.getAndSet(seq);
        if (prev >= 0 && seq > prev + 1 && Math.abs(seq - prev) < 1000) {
            lostFrameCount.addAndGet(seq - prev - 1);
            log.debug("Frame drop: {}→{}, total lost={}", prev, seq, lostFrameCount.get());
        }

        // 遮挡检测：置信度过低 → 可能遮挡
        if (frame.getConfidence() < 0.15) {
            try {
                occlusionPort.onOcclusionDetected(new OcclusionDetectedSignal(
                        ts, frame.getSensorId(), OcclusionType.UNKNOWN));
            } catch (Exception e) {
                log.warn("Occlusion callback failed: {}", e.getMessage());
            }
            return;
        }

        lastFrameMs.set(frame.getTimestampMs());

        // 组装 SensorReading(DMS_CAMERA) → 契约字段对齐 ood_interface.md §2.2
        Map<String, Double> values = new HashMap<>();
        values.put("PERCLOS", frame.getPerclos());
        values.put("yawnFreq", frame.getYawnFreq());
        values.put("headNodFreq", frame.getHeadNodFreq());
        values.put("gazeDeviationCumulative", frame.getGazeDeviationCumulative());
        values.put("handsOffWheel", frame.getHandsOffWheel());
        if (frame.getPhoneDetected() >= 0) {
            values.put("phoneDetected", frame.getPhoneDetected());
        }
        if (frame.getSmokingDetected() >= 0) {
            values.put("smokingDetected", frame.getSmokingDetected());
        }

        SensorReading reading = new SensorReading(
                SensorType.DMS_CAMERA, ts, activeTripId, values);

        // 送入判定门面
        if (readingConsumer != null) {
            try {
                readingConsumer.accept(reading);
            } catch (Exception e) {
                log.error("readingConsumer failed: {}", e.getMessage());
            }
        }
    }

    // ── 健康检查 ───────────────────────────────────────

    public boolean checkHealth() {
        if (channel == null || channel.isShutdown()) {
            dmsAvailable.set(false);
            return false;
        }
        try {
            HealthResponse resp = DmsPerceptionGrpc.newBlockingStub(channel)
                    .withDeadlineAfter(3, TimeUnit.SECONDS)
                    .health(HealthRequest.getDefaultInstance());
            boolean wasAvailable = dmsAvailable.getAndSet(resp.getAlive());
            if (resp.getAlive() && !wasAvailable) {
                log.info("DMS sidecar recovered: fps={}", resp.getCurrentFps());
            }
            return resp.getAlive();
        } catch (StatusRuntimeException e) {
            if (dmsAvailable.getAndSet(false)) {
                log.warn("DMS sidecar health check failed: {}", e.getStatus().getCode());
            }
            return false;
        }
    }

    // ── 重连 ───────────────────────────────────────────

    private void scheduleReconnect() {
        new Thread(() -> {
            try {
                Thread.sleep(5000);
                log.info("Reconnecting to DMS sidecar...");
                disconnect();
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "dms-reconnect").start();
    }

    // ── 帧率控制 ───────────────────────────────────────

    public void adjustFrameRate(double targetFps) {
        if (controlStream != null && dmsAvailable.get()) {
            controlStream.onNext(ControlSignal.newBuilder()
                    .setFrameRateAdjust(FrameRateAdjust.newBuilder()
                            .setTargetFps(targetFps))
                    .build());
        }
    }

    // ── 状态查询 ───────────────────────────────────────

    public boolean isAvailable() {
        return dmsAvailable.get();
    }

    public long lastFrameTimestampMs() {
        return lastFrameMs.get();
    }

    public long lostFrameCount() {
        return lostFrameCount.get();
    }
}
