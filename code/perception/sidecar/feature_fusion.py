"""
特征融合模块。

将 YOLO 检出 + FaceMesh 特征融合为 DmsFeatureFrame，
== SensorReading(DMS_CAMERA) 契约字段。

产出的是单帧原子特征，跨帧滑动窗口聚合由 Java 侧门面负责
（docs/ood_perception_yolo.md §7.2#1）。

原始帧用后即弃，不落盘不外传（BR-04）。
"""

import time
from typing import Optional

from yolo_detector import YoloDetection, YoloDetector
from facemesh_estimator import FaceMeshFeatures, FaceMeshEstimator
import numpy as np

# 如果 proto 未生成，gRPC 导入会失败；使用 try/except 兼容直接运行
try:
    import dms_perception_pb2 as pb2
except ImportError:
    pb2 = None


class FeatureFusion:
    """
    双模型特征融合 → DmsFeatureFrame。

    组合 YoloDetector + FaceMeshEstimator，
    每帧产出 PERCLOS / yawnFreq / headNodFreq / gazeDeviationCumulative / handsOffWheel。
    """

    def __init__(
        self,
        yolo_detector: YoloDetector,
        facemesh_estimator: FaceMeshEstimator,
        sensor_id: str = "dms_camera_0",
    ):
        self._yolo = yolo_detector
        self._facemesh = facemesh_estimator
        self._sensor_id = sensor_id
        self._frame_seq: int = 0

    def process_frame(self, frame: np.ndarray, confidence: float = 1.0) -> Optional[dict]:
        """
        处理一帧，返回 DMS 特征字典。

        frame 在此函数返回后由调用方释放，不保留引用（BR-04）。
        返回 None 表示模型均无法检出有效特征。
        """
        self._frame_seq += 1
        timestamp_ms = int(time.time() * 1000)

        # 并行（串行）执行两个模型
        yolo_result = self._yolo.detect(frame)
        fm_result = self._facemesh.estimate(frame)

        # 融合
        if not yolo_result.detection_valid and not fm_result.face_detected:
            return None

        frame_conf = confidence
        if not fm_result.face_detected:
            frame_conf *= 0.3  # 无人脸则降置信

        feature = {
            "timestamp_ms": timestamp_ms,
            "sensor_id": self._sensor_id,
            "frame_seq": self._frame_seq,
            # FaceMesh 产出（疲劳/视线类原子信号）
            "perclos": float(fm_result.ear),         # EAR 反相关，需门面映射
            "yawn_freq": float(fm_result.mar),        # MAR 单帧值
            "head_nod_freq": float(fm_result.head_pitch),
            "gaze_deviation_cumulative": float(fm_result.head_yaw),
            # YOLO 产出（行为/物体类）
            "hands_off_wheel": float(yolo_result.hands_off_wheel),
            "phone_detected": float(yolo_result.phone_detected),
            "smoking_detected": float(yolo_result.smoking_detected),
            # 元数据
            "confidence": float(frame_conf),
            "face_detected": fm_result.face_detected,
        }
        return feature

    def to_proto(self, feature: dict):
        """将特征字典转为 gRPC protobuf message。"""
        if pb2 is None:
            raise RuntimeError("dms_perception_pb2 not available; run generate_proto.sh first")
        return pb2.DmsFeatureFrame(
            timestamp_ms=feature["timestamp_ms"],
            sensor_id=feature["sensor_id"],
            perclos=feature["perclos"],
            yawn_freq=feature["yawn_freq"],
            head_nod_freq=feature["head_nod_freq"],
            gaze_deviation_cumulative=feature["gaze_deviation_cumulative"],
            hands_off_wheel=feature["hands_off_wheel"],
            confidence=feature["confidence"],
            phone_detected=feature.get("phone_detected", -1.0),
            smoking_detected=feature.get("smoking_detected", -1.0),
            frame_seq=feature["frame_seq"],
        )
