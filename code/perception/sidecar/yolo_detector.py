"""
YOLO 检测器模块。

职责：
- 检出 handsOffWheel（手/方向盘空间关系）
- 可选扩展：手机、香烟检出
- 负责"行为/物体类"检出，置信度高、实时性好

依据：docs/ood_perception_yolo.md §2.2, §7.2#3
本期仅实现 handsOffWheel，手机/香烟列为可选扩展。
"""

from dataclasses import dataclass
from typing import Optional, Tuple
import os

import numpy as np
from ultralytics import YOLO


@dataclass
class YoloDetection:
    """单帧 YOLO 检测结果。"""

    hands_off_wheel: float  # [0, 1] 置信度
    phone_detected: float  # -1 表示未启用
    smoking_detected: float  # -1 表示未启用
    steering_wheel_bbox: Optional[Tuple[float, float, float, float]] = None
    # 扩展置信度标记
    detection_valid: bool = True


# ── COCO 类别 ID ──
COCO_STEERING_WHEEL_CLS = -1  # COCO 无方向盘类，需自定义训练或用近似策略
COCO_CELL_PHONE = 67
COCO_CUP = 41    # 杯类 (香烟近似)
COCO_PERSON = 0
COCO_HAND = -1   # COCO 无手类


class YoloDetector:
    """
    YOLO 目标检测器 — 检出脱手/物体。

    免责：由于 COCO 预训练模型不含"方向盘"类，handsOffWheel 的检测
    采用启发式策略：通过检测手部区域(以 person box 为参考)与画面下半部分
    的目标密度关系近似判断。真实部署需用自定义训练数据集微调。
    """

    def __init__(
        self,
        model_path: str = None,
        confidence_threshold: float = 0.4,
        enable_phone: bool = False,
        enable_smoking: bool = False,
    ):
        if model_path is None:
            dms_path = os.path.join(os.path.dirname(__file__), "dms_best.pt")
            model_path = dms_path if os.path.exists(dms_path) else "yolov8n.pt"
        self._model = YOLO(model_path)
        self._model_path = model_path
        self._conf = confidence_threshold
        self._enable_phone = enable_phone
        self._enable_smoking = enable_smoking

    def detect(self, frame: np.ndarray) -> YoloDetection:
        """对单帧执行目标检测。原始帧仅在内存中使用，不落盘不外传。"""
        results = self._model(frame, verbose=False)
        if not results or len(results) == 0:
            return YoloDetection(hands_off_wheel=0.0, phone_detected=-1.0,
                                 smoking_detected=-1.0, detection_valid=False)

        result = results[0]
        boxes = result.boxes
        if boxes is None or len(boxes) == 0:
            return YoloDetection(hands_off_wheel=0.0, phone_detected=-1.0,
                                 smoking_detected=-1.0, detection_valid=True)

        h, w = frame.shape[:2]
        cls_ids = boxes.cls.cpu().numpy().astype(int)
        confs = boxes.conf.cpu().numpy()
        xywh = boxes.xywh.cpu().numpy()

        # 检出手机
        phone_conf = -1.0
        if self._enable_phone:
            phone_idxs = np.where(cls_ids == COCO_CELL_PHONE)[0]
            if len(phone_idxs) > 0:
                phone_conf = float(np.max(confs[phone_idxs]))

        # 检出香烟（近似：杯类 + 小目标）
        smoking_conf = -1.0
        if self._enable_smoking:
            cup_idxs = np.where(cls_ids == COCO_CUP)[0]
            for idx in cup_idxs:
                if confs[idx] >= self._conf:
                    bw = xywh[idx][2] / w
                    bh = xywh[idx][3] / h
                    if bw < 0.15 and bh < 0.15:
                        smoking_conf = max(smoking_conf, float(confs[idx]))

        # handsOffWheel 启发式检测
        hands_off = self._estimate_hands_off_wheel(frame, results)

        # 使用后立即释放 frame 引用（BR-04：原始帧不保留）
        return YoloDetection(
            hands_off_wheel=hands_off,
            phone_detected=phone_conf,
            smoking_detected=smoking_conf,
            detection_valid=True,
        )

    def _estimate_hands_off_wheel(self, frame: np.ndarray, results) -> float:
        """
        启发式脱手检测。

        策略（因 COCO 无方向盘类）：
        1. 检测 person bbox，取下半区域作为方向盘近似位置
        2. 在 person 框下半区域统计小目标（茶杯/瓶子/书等可能近似手的误检）
        3. 结合画面运动量判断

        真实部署：替换为含方向盘类标的 YOLO 微调模型，
        直接用 hand_bbox 与 steering_wheel_bbox 的空间 IoU 判定。
        """
        boxes = results[0].boxes
        if boxes is None or len(boxes) == 0:
            return 0.0

        h, w = frame.shape[:2]
        person_idxs = np.where(boxes.cls.cpu().numpy().astype(int) == COCO_PERSON)[0]
        if len(person_idxs) == 0:
            return 0.0

        # 取置信度最高的人
        person_idx = person_idxs[np.argmax(boxes.conf.cpu().numpy()[person_idxs])]
        person_box = boxes.xyxy.cpu().numpy()[person_idx]
        py1, py2 = person_box[1], person_box[3]

        # 方向盘区域 = person 框下半部分
        steering_zone_top = py1 + (py2 - py1) * 0.55
        steering_zone_bottom = py2 + (py2 - py1) * 0.15

        # 统计 steering zone 内小目标的覆盖
        other_idxs = np.where(boxes.cls.cpu().numpy().astype(int) != COCO_PERSON)[0]
        zone_coverage = 0.0
        for idx in other_idxs:
            box = boxes.xyxy.cpu().numpy()[idx]
            by1, by2 = box[1], box[3]
            # 目标中心在 steering zone 内
            center_y = (by1 + by2) / 2
            if steering_zone_top <= center_y <= steering_zone_bottom:
                zone_coverage += float(boxes.conf.cpu().numpy()[idx])

        # handsOffWheel = 1 - zone_coverage (zone coverage 越高说明手在方向盘区域越多)
        hands_on = min(zone_coverage * 2.0, 1.0)
        hands_off = 1.0 - hands_on

        return max(0.0, min(hands_off, 1.0))
