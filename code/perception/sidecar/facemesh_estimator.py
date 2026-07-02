"""
MediaPipe FaceMesh 关键点估计模块。

职责：
- 计算眼部 EAR (Eye Aspect Ratio) → PERCLOS 原子信号
- 计算嘴部 MAR (Mouth Aspect Ratio) → 哈欠原子信号
- 计算头部姿态 → 点头/视线偏离原子信号

依据：docs/ood_perception_yolo.md §2.2, §7.2#4
模型：MediaPipe FaceMesh (478 点)
"""

from dataclasses import dataclass, field
from typing import Optional, Tuple

import cv2
import mediapipe as mp
import numpy as np


# ── MediaPipe FaceMesh 关键点索引 ──
# 左眼
LEFT_EYE_IDX = [33, 160, 158, 133, 153, 144]  # 眼眶
LEFT_EYE_INNER = [362, 385, 387, 263, 373, 380]
# 右眼 (实际为 MediaPipe 的 "right"，屏幕视角左眼)
RIGHT_EYE_IDX = [362, 385, 387, 263, 373, 380]
RIGHT_EYE_INNER = [33, 160, 158, 133, 153, 144]
# 嘴部外轮廓
MOUTH_OUTER = [61, 146, 91, 181, 84, 17, 314, 405, 321, 375,
               291, 409, 270, 269, 267, 0, 37, 39, 40, 185]
# 头部姿态参考点
NOSE_TIP = 4
NOSE_BRIDGE = 6
LEFT_EYE_CORNER = 33
RIGHT_EYE_CORNER = 263
CHIN = 152
FOREHEAD = 10

# 3D 参考点 (世界坐标系，用于 solvePnP 头部姿态)
MODEL_POINTS_3D = np.array([
    [0.0, 0.0, 0.0],       # 鼻尖
    [0.0, -330.0, -65.0],  # 下巴
    [-225.0, 170.0, -135.0],  # 左眼左角
    [225.0, 170.0, -135.0],  # 右眼右角
    [-150.0, -150.0, -125.0],  # 左嘴角
    [150.0, -150.0, -125.0],  # 右嘴角
], dtype=np.float64)


@dataclass
class FaceMeshFeatures:
    """单帧 FaceMesh 估计结果。"""

    ear: float  # 双眼平均 EAR，眼睑闭合 → EAR ↓ [0, ~0.45]
    mar: float  # 嘴部 MAR，哈欠 → MAR ↑
    head_pitch: float  # 俯仰角（度），点头行为
    head_yaw: float    # 偏航角（度），视线偏离
    head_roll: float   # 翻滚角（度）
    face_detected: bool = True
    landmarks_raw: Optional[np.ndarray] = None  # 原始关键点（仅用于本帧计算，用后即弃）


class FaceMeshEstimator:
    """
    人脸关键点估计器。

    单帧计算，不跨帧维护状态。每帧产出独立的 EAR/MAR/头姿原子信号，
    跨帧滑动窗口聚合由 Java 侧 RiskDeterminationService 门面负责。
    """

    def __init__(self, static_image_mode: bool = False, max_num_faces: int = 1):
        self._face_mesh = mp.solutions.face_mesh.FaceMesh(
            static_image_mode=static_image_mode,
            max_num_faces=max_num_faces,
            refine_landmarks=True,
            min_detection_confidence=0.5,
            min_tracking_confidence=0.5,
        )

    def estimate(self, frame: np.ndarray) -> FaceMeshFeatures:
        """
        估计单帧人脸特征。原始帧仅在内存中使用。

        返回 FaceMeshFeatures；无人脸时 face_detected=False。
        """
        h, w = frame.shape[:2]
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = self._face_mesh.process(rgb)

        if not results.multi_face_landmarks:
            return FaceMeshFeatures(
                ear=0.0, mar=0.0,
                head_pitch=0.0, head_yaw=0.0, head_roll=0.0,
                face_detected=False,
            )

        landmarks = results.multi_face_landmarks[0]
        pts = np.array([[lm.x * w, lm.y * h, lm.z * w] for lm in landmarks.landmark])

        ear = self._compute_ear(pts)
        mar = self._compute_mar(pts)
        pitch, yaw, roll = self._compute_head_pose(pts, w, h)

        return FaceMeshFeatures(
            ear=ear, mar=mar,
            head_pitch=pitch, head_yaw=yaw, head_roll=roll,
            face_detected=True,
            landmarks_raw=pts,  # 调用方用后即弃
        )

    def _compute_ear(self, pts: np.ndarray) -> float:
        """计算双眼平均 Eye Aspect Ratio。"""
        left = self._eye_aspect_ratio(pts, LEFT_EYE_IDX)
        right = self._eye_aspect_ratio(pts, RIGHT_EYE_IDX)
        ear = (left + right) / 2.0
        return float(np.clip(ear, 0.0, 1.0))

    def _compute_mar(self, pts: np.ndarray) -> float:
        """计算 Mouth Aspect Ratio（哈欠指标）。"""
        # 嘴部垂直距离 / 水平距离
        v1 = np.linalg.norm(pts[13] - pts[14])   # 上下唇中点
        v2 = np.linalg.norm(pts[78] - pts[308])  # 嘴角
        h = np.linalg.norm(pts[61] - pts[291])   # 嘴角间距
        if h < 1e-6:
            return 0.0
        mar = (v1 + v2) / (2.0 * h)
        return float(np.clip(mar, 0.0, 2.0))

    @staticmethod
    def _eye_aspect_ratio(pts: np.ndarray, indices) -> float:
        """单眼 EAR 计算。"""
        p = pts[indices]
        # 垂直距离
        v1 = np.linalg.norm(p[1] - p[5])
        v2 = np.linalg.norm(p[2] - p[4])
        # 水平距离
        h = np.linalg.norm(p[0] - p[3])
        if h < 1e-6:
            return 0.0
        return (v1 + v2) / (2.0 * h)

    def _compute_head_pose(self, pts: np.ndarray, w: int, h: int) -> Tuple[float, float, float]:
        """
        计算头部姿态角（solvePnP）。

        返回 (pitch, yaw, roll) 角度制。
        pitch > 0 → 低头, yaw > 0 → 右转, roll > 0 → 右倾
        """
        # 相机内参（近似）
        focal_length = w
        center = (w / 2.0, h / 2.0)
        camera_matrix = np.array([
            [focal_length, 0, center[0]],
            [0, focal_length, center[1]],
            [0, 0, 1],
        ], dtype=np.float64)
        dist_coeffs = np.zeros((4, 1))

        # 选取 6 个 2D-3D 对应点
        image_points = np.array([
            pts[NOSE_TIP][:2],
            pts[CHIN][:2],
            pts[LEFT_EYE_CORNER][:2],
            pts[RIGHT_EYE_CORNER][:2],
            pts[61][:2],   # 左嘴角
            pts[291][:2],  # 右嘴角
        ], dtype=np.float64)

        success, rvec, tvec = cv2.solvePnP(
            MODEL_POINTS_3D, image_points, camera_matrix, dist_coeffs,
            flags=cv2.SOLVEPNP_ITERATIVE
        )
        if not success:
            return 0.0, 0.0, 0.0

        rmat, _ = cv2.Rodrigues(rvec)
        # 欧拉角提取
        sy = np.sqrt(rmat[0, 0] ** 2 + rmat[1, 0] ** 2)
        singular = sy < 1e-6

        if not singular:
            pitch = np.arctan2(-rmat[2, 0], sy)
            yaw = np.arctan2(rmat[1, 0], rmat[0, 0])
            roll = np.arctan2(rmat[2, 1], rmat[2, 2])
        else:
            pitch = np.arctan2(-rmat[2, 0], sy)
            yaw = np.arctan2(-rmat[0, 1], rmat[1, 1])
            roll = 0.0

        return float(np.degrees(pitch)), float(np.degrees(yaw)), float(np.degrees(roll))
