# DMS 视觉感知推理侧车（Python sidecar）
#
# 本模块与 Java 边缘侧 Spring Boot 应用通过 gRPC 协同：
#   Python 侧承担 CV 推理（YOLO + MediaPipe FaceMesh）
#   Java  侧承担特征封装、判定编排与 SensorReading 生成
#
# 双方共置同一车载终端，本机 IPC，满足 500ms 本地口径。
# 原始帧仅在 Python 进程内存中流转，不落盘、不上云（BR-04）。
#
# 依据：docs/ood_perception_yolo.md
