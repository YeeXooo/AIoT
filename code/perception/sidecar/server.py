"""
DMS 感知 gRPC 服务端（Python sidecar 入口）。

功能：
- 启动帧输入源（摄像头/视频文件）
- 初始化 YOLO + MediaPipe FaceMesh 双模型
- 启动 gRPC server，Java client 通过 StreamFeatures RPC 获取特征帧流
- BR-04：原始帧仅在内存中流转，gRPC 推送仅含数值特征
- 支持帧率调整控制信令

用法：
    python server.py --source /path/to/video.mp4
    python server.py --source camera --device 0
"""

import argparse
import logging
import signal
import sys
import threading
import time
from concurrent import futures
from typing import Optional

import cv2
import grpc
import numpy as np

sys.path.insert(0, ".")
from yolo_detector import YoloDetector
from facemesh_estimator import FaceMeshEstimator
from feature_fusion import FeatureFusion
from frame_source import CameraSource, VideoFileSource, FrameSource

try:
    import dms_perception_pb2 as pb2
    import dms_perception_pb2_grpc as pb2_grpc
except ImportError:
    print("ERROR: gRPC stubs not generated. Run generate_proto.sh first.")
    sys.exit(1)

logger = logging.getLogger("dms_perception")


class DmsPerceptionServicer(pb2_grpc.DmsPerceptionServicer):
    """gRPC DmsPerception 服务实现。

    StreamFeatures: 接收 Java 端的 ControlSignal 流，
    持续产出 DmsFeatureFrame 流推送至 Java。
    """

    def __init__(self, fusion: FeatureFusion, frame_source: FrameSource):
        self._fusion = fusion
        self._source = frame_source
        self._start_time_ms = int(time.time() * 1000)
        self._last_frame_ms: int = 0
        self._frame_count: int = 0
        self._target_fps: float = 10.0
        self._shutdown = threading.Event()

    def StreamFeatures(self, request_iterator, context):
        """双向流 RPC。

        request_iterator: Java → Python ControlSignal 流
        yield: Python → Java DmsFeatureFrame 流
        """
        peer = context.peer()
        logger.info("StreamFeatures started, client=%s", peer)

        # 在后台线程消费控制信令（非阻塞）
        def consume_controls():
            try:
                for ctrl in request_iterator:
                    sig = ctrl.WhichOneof("signal")
                    if sig == "frame_rate_adjust":
                        new_fps = ctrl.frame_rate_adjust.target_fps
                        logger.info("Frame rate adjusted: %.1f -> %.1f",
                                    self._target_fps, new_fps)
                        self._target_fps = new_fps
                    elif sig == "shutdown":
                        logger.info("Shutdown signal received from Java")
                        self._shutdown.set()
            except Exception as e:
                logger.debug("Control stream ended: %s", e)

        ctrl_thread = threading.Thread(target=consume_controls, daemon=True)
        ctrl_thread.start()

        # 主循环：推送特征帧
        try:
            while not self._shutdown.is_set() and context.is_active():
                frame = self._source.grab()
                if frame is None:
                    time.sleep(0.05)
                    continue

                feature = self._fusion.process_frame(frame)
                # 原始帧引用在 process_frame 返回后释放

                if feature is not None:
                    proto_msg = self._fusion.to_proto(feature)
                    yield proto_msg
                    self._last_frame_ms = feature["timestamp_ms"]
                    self._frame_count += 1

                frame_delay = 1.0 / max(self._target_fps, 1.0)
                time.sleep(frame_delay)

        except Exception as e:
            logger.error("StreamFeatures error: %s", e)
        finally:
            logger.info("StreamFeatures ended, frames=%d", self._frame_count)

    def Health(self, request, context):
        elapsed = (time.time() * 1000 - self._start_time_ms) / 1000.0
        fps = self._frame_count / elapsed if elapsed > 0 else 0.0
        return pb2.HealthResponse(
            alive=True,
            start_time_ms=self._start_time_ms,
            current_fps=round(fps, 1),
            last_frame_ms=self._last_frame_ms,
        )

    def shutdown(self):
        self._shutdown.set()


def main():
    parser = argparse.ArgumentParser(description="DMS Perception YOLO Inference Sidecar")
    parser.add_argument("--source", default="camera",
                        help="输入源：camera 或视频文件路径")
    parser.add_argument("--device", type=int, default=0,
                        help="摄像头设备 ID（--source=camera 时有效）")
    parser.add_argument("--fps", type=float, default=10.0,
                        help="目标推理帧率 (Hz)")
    parser.add_argument("--model", default="yolov8n.pt",
                        help="YOLO 模型路径")
    parser.add_argument("--port", type=int, default=50051,
                        help="gRPC 服务端口")
    parser.add_argument("--conf", type=float, default=0.4,
                        help="YOLO 置信度阈值")
    parser.add_argument("--sensor-id", default="dms_camera_0",
                        help="传感器标识")
    parser.add_argument("--no-loop", action="store_true",
                        help="视频播放不循环")
    parser.add_argument("--workers", type=int, default=4,
                        help="gRPC 线程池大小")
    args = parser.parse_args()

    logging.basicConfig(level=logging.INFO,
                        format="%(asctime)s [%(name)s] %(levelname)s: %(message)s")

    # 1. 初始化帧输入源
    if args.source == "camera":
        source = CameraSource(device_id=args.device, target_fps=args.fps)
        logger.info("Using camera source, device=%d, fps=%.1f", args.device, args.fps)
    else:
        source = VideoFileSource(args.source, loop=not args.no_loop, target_fps=args.fps)
        logger.info("Using video file source: %s, fps=%.1f", args.source, args.fps)

    if not source.open():
        logger.error("Failed to open input source: %s", args.source)
        sys.exit(1)

    # 2. 初始化模型
    yolo = YoloDetector(model_path=args.model, confidence_threshold=args.conf)
    fm = FaceMeshEstimator()
    fusion = FeatureFusion(yolo, fm, sensor_id=args.sensor_id)
    logger.info("Models loaded: YOLO + MediaPipe FaceMesh")

    # 3. 启动 gRPC server
    server = grpc.server(futures.ThreadPoolExecutor(max_workers=args.workers))
    servicer = DmsPerceptionServicer(fusion, source)
    pb2_grpc.add_DmsPerceptionServicer_to_server(servicer, server)
    server.add_insecure_port(f"[::]:{args.port}")
    server.start()
    logger.info("gRPC server listening on port %d", args.port)

    # 4. 优雅退出
    def handle_signal(sig, frame):
        logger.info("Received signal %s, shutting down...", sig)
        servicer.shutdown()
        source.close()
        server.stop(grace=5)
        sys.exit(0)

    signal.signal(signal.SIGINT, handle_signal)
    signal.signal(signal.SIGTERM, handle_signal)

    logger.info("DMS Perception sidecar running. Press Ctrl+C to stop.")
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        handle_signal(None, None)


if __name__ == "__main__":
    main()
