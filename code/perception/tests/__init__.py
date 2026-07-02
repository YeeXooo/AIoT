# DMS 视觉感知层测试
#
# 测试覆盖：
#   - 帧源（摄像头/视频文件）
#   - YOLO 检测（合成图像验证）
#   - FaceMesh 估计（合成人脸验证）
#   - 特征融合管道
#   - gRPC 契约
#   - BR-04 隐私断言
#   - mock/yolo 切换兼容性
#
# 用法：
#   python -m tests.test_perception
#   python -m tests.test_perception --test yolo -v
