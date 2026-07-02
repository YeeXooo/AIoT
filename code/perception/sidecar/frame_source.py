"""
帧输入源抽象层。

支持两种输入源：
- 摄像头（cv2.VideoCapture，真实部署）
- 本地视频文件（开发/演示复现）

帧仅在内存中流转，不落盘、不跨进程外传（BR-04）。
"""

import abc
import time
from collections import deque
from typing import Optional

import cv2
import numpy as np


class FrameSource(abc.ABC):
    """帧输入源抽象基类。"""

    @abc.abstractmethod
    def open(self) -> bool:
        """打开输入源，返回是否成功。"""

    @abc.abstractmethod
    def grab(self) -> Optional[np.ndarray]:
        """抓取一帧 (BGR numpy array)，无帧时返回 None。"""

    @abc.abstractmethod
    def close(self):
        """关闭输入源，释放资源。"""

    @property
    @abc.abstractmethod
    def fps(self) -> float:
        """标称帧率。"""


class CameraSource(FrameSource):
    """USB/内置摄像头输入源。"""

    def __init__(self, device_id: int = 0, target_fps: float = 10.0):
        self._device_id = device_id
        self._target_fps = target_fps
        self._cap: Optional[cv2.VideoCapture] = None
        self._native_fps = 30.0

    def open(self) -> bool:
        self._cap = cv2.VideoCapture(self._device_id)
        if not self._cap.isOpened():
            return False
        self._native_fps = self._cap.get(cv2.CAP_PROP_FPS) or 30.0
        return True

    def grab(self) -> Optional[np.ndarray]:
        if self._cap is None:
            return None
        ret, frame = self._cap.read()
        if not ret or frame is None:
            return None
        time.sleep(1.0 / self._target_fps)
        return frame

    def close(self):
        if self._cap is not None:
            self._cap.release()
            self._cap = None

    @property
    def fps(self) -> float:
        return self._target_fps


class VideoFileSource(FrameSource):
    """本地视频文件输入源（开发/演示复现用）。"""

    def __init__(self, video_path: str, loop: bool = True, target_fps: float = 10.0):
        self._video_path = video_path
        self._loop = loop
        self._target_fps = target_fps
        self._cap: Optional[cv2.VideoCapture] = None
        self._native_fps = 30.0
        self._frame_delay: float = 1.0 / target_fps

    def open(self) -> bool:
        self._cap = cv2.VideoCapture(self._video_path)
        if not self._cap.isOpened():
            return False
        self._native_fps = self._cap.get(cv2.CAP_PROP_FPS) or 30.0
        self._frame_delay = 1.0 / self._target_fps
        return True

    def grab(self) -> Optional[np.ndarray]:
        if self._cap is None:
            return None
        ret, frame = self._cap.read()
        if not ret or frame is None:
            if self._loop:
                self._cap.set(cv2.CAP_PROP_POS_FRAMES, 0)
                ret, frame = self._cap.read()
                if not ret or frame is None:
                    return None
            else:
                return None
        time.sleep(self._frame_delay)
        return frame

    def close(self):
        if self._cap is not None:
            self._cap.release()
            self._cap = None

    @property
    def fps(self) -> float:
        return self._target_fps
