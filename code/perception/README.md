# DMS 视觉感知层（YOLO）

> 车载 DMS 视觉通道的真实感知实现，以 Python sidecar 形态与 Java 边缘侧协同。
> 原始帧仅在 Python 进程内存中流转，不落盘、不上云（BR-04）。
>
> 设计依据：`docs/ood_perception_yolo.md`

## 目录

```
code/perception/
├── proto/                    # gRPC 契约（Java/Python 共享）
│   └── dms_perception.proto
├── sidecar/                  # Python 推理微服务
│   ├── server.py             # gRPC 服务端入口
│   ├── frame_source.py       # 帧输入抽象（摄像头/视频文件）
│   ├── yolo_detector.py      # YOLO 检出（优先 dms_best.pt → 退避 yolov8n.pt）
│   ├── facemesh_estimator.py # MediaPipe FaceMesh（EAR/MAR/头姿）
│   ├── feature_fusion.py     # 双模型融合 → DmsFeatureFrame
│   ├── train.py              # YOLO 微调训练管线
│   ├── data_prep.py          # 数据集准备（合成/公开集转换）
│   ├── dms-perception.service # systemd 进程守护
│   ├── generate_proto.sh     # 生成 Python gRPC stubs
│   └── requirements.txt
├── data/                     # 训练数据
│   └── yolo/
│       ├── dataset.yaml      # YOLO 数据集配置
│       ├── images/train/     # 训练图像
│       ├── images/val/       # 验证图像
│       ├── labels/train/     # YOLO 标签
│       └── labels/val/
├── tests/
│   └── test_perception.py    # 单元测试（YOLO/FaceMesh/融合/隐私/切换）
└── setup.sh                  # 一键环境搭建
```

## 快速开始

### 1. 环境搭建

```bash
bash code/perception/setup.sh
```

自动完成：创建 Python 3.11 虚拟环境 → 安装依赖 → 生成 300 张合成训练数据。

### 2. 训练

```bash
conda activate aiot-perception   # 或 source sidecar/venv/bin/activate
cd code/perception/sidecar

# CPU 快速验证（约 5 分钟）
python train.py --epochs 5 --device cpu

# GPU 完整训练
python train.py --epochs 100 --device 0 --batch 16 --model yolov8s.pt
```

训练完成后 `best.pt` 自动复制为 `sidecar/dms_best.pt`，推理时优先使用。

### 3. 推理

```bash
# 摄像头实时推理
python server.py --source camera --device 0 --model dms_best.pt

# 视频文件推理（开发/演示用）
python server.py --source /path/to/video.mp4 --model dms_best.pt
```

gRPC 服务默认监听 `[::]:50051`，Java 端配置 `aiot.perception.dms.mode=yolo` 即自动连接。

### 4. 切换 mock / yolo

在 `application.yml` 中：

```yaml
aiot:
  perception:
    dms:
      mode: yolo          # mock | yolo
      grpc-host: localhost
      grpc-port: 50051
```

`mode=mock`（默认）：Java 模拟源，不创建 gRPC 适配器。
`mode=yolo`：启动时自动连接 Python sidecar，消费特征帧流。

## 数据集

### 合成数据（内置，0 依赖）

```bash
python data_prep.py synthetic -n 500
```

生成含方向盘、手部、人脸 bbox 标注的合成图像，用于管线验证。

### 公开数据集

```bash
# State Farm Distracted Driver（需手动下载到本地目录）
python data_prep.py state-farm --dir /path/to/state-farm-dataset

# Roboflow Universe（需 API key）
python data_prep.py roboflow --api-key YOUR_KEY --workspace xxx --project yyy
```

### 自定义数据

用 LabelImg / Labelme 标注自己的 DMS 图像，导出 YOLO 格式放入 `data/yolo/`。

## DMS 类别定义

| ID | 名称 | 说明 |
|----|------|------|
| 0 | `steering_wheel` | 方向盘 |
| 1 | `hand_on_wheel` | 手扶方向盘 |
| 2 | `hand_off_wheel` | 手离方向盘 |
| 3 | `cell_phone` | 手机 |
| 4 | `cigarette` | 香烟 |
| 5 | `face` | 人脸 |
| 6 | `seatbelt` | 安全带 |

## gRPC 契约

`DmsFeatureFrame` 包含 7 个核心字段（与 `SensorReading(DMS_CAMERA)` 对齐）：

| 字段 | 范围 | 模型来源 |
|------|------|---------|
| `perclos` | [0, 1] | FaceMesh EAR |
| `yawn_freq` | ≥0 | FaceMesh MAR |
| `head_nod_freq` | ≥0 | FaceMesh 头部姿态 |
| `gaze_deviation_cumulative` | ≥0 | FaceMesh 视线偏离 |
| `hands_off_wheel` | [0, 1] | YOLO 检出 |
| `phone_detected` | [0, 1] 或 -1 | YOLO 检出（可选） |
| `smoking_detected` | [0, 1] 或 -1 | YOLO 检出（可选） |

## 测试

```bash
cd code/perception/tests
python test_perception.py          # 全部
python test_perception.py --test yolo -v   # 单项
```

覆盖：YOLO 检测、FaceMesh 估计、特征融合、BR-04 隐私、mock/yolo 契约一致性。
