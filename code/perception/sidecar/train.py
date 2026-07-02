"""
YOLO 微调训练管线。

在 DMS 专用数据集上微调 YOLOv8，产出含方向盘/手部/手机/香烟等
类别的定制模型，替换默认启发式检测。

训练完成后，将 best.pt 设为 YoloDetector 的 model_path。

用法：
  python train.py                     # 训练（需要 data/yolo/dataset.yaml）
  python train.py --epochs 50         # 自定义 epoch
  python train.py --export onnx       # 训练后导出 ONNX
  python train.py --validate-only     # 仅验证已有模型
"""

import argparse
import json
import os
import sys
from pathlib import Path
from datetime import datetime

import yaml

ROOT = Path(__file__).resolve().parent
DATA_YAML = ROOT.parent / "data" / "yolo" / "dataset.yaml"
RUNS_DIR = ROOT.parent / "runs"


def train(
    data_yaml: str = str(DATA_YAML),
    model: str = None,
    epochs: int = 50,
    imgsz: int = 640,
    batch: int = 8,
    device: str = "cpu",
    workers: int = 2,
    patience: int = 10,
    lr0: float = 0.001,
    name: str = None,
):
    """
    YOLOv8 DMS 微调训练。

    Args:
        model: 基础模型路径，默认按以下优先级：
               1. ./yolov8n.pt（本地已缓存）
               2. dms_best.pt（之前训练的最佳模型，继续微调用）
               3. ultralytics 自动下载 yolov8n.pt（需要网络）
    """
    try:
        from ultralytics import YOLO
    except ImportError:
        print("[ERROR] ultralytics not installed. Run: pip install ultralytics")
        sys.exit(1)

    # 解析 model 路径
    if model is None:
        local = ROOT / "yolov8n.pt"
        dms_best = ROOT / "dms_best.pt"
        if local.exists():
            model = str(local)
            print(f"[INFO] Using local model: {model}")
        elif dms_best.exists():
            model = str(dms_best)
            print(f"[INFO] Using previously trained model: {model}")
        else:
            model = "yolov8n.pt"
            print("[INFO] yolo model not cached locally, will download from ultralytics (requires network)")

    if not Path(data_yaml).exists():
        print(f"[ERROR] Dataset config not found: {data_yaml}")
        print("  Run: python data_prep.py synthetic  (to generate synthetic data)")
        print("  or manually prepare data/yolo/dataset.yaml")
        sys.exit(1)

    # 验证数据集
    with open(data_yaml) as f:
        ds_cfg = yaml.safe_load(f)
    train_dir = ds_cfg.get("train", "")
    val_dir = ds_cfg.get("val", "")
    if isinstance(train_dir, str) and not (Path(data_yaml).parent / train_dir).exists():
        print(f"[WARN] Train images dir not found at relative path. "
              f"Dataset yaml 'path' should be absolute or correct relative.")
    print(f"  Classes: {ds_cfg.get('nc', '?')} → {ds_cfg.get('names', [])}")
    print(f"  Train: {train_dir}, Val: {val_dir}")

    if name is None:
        name = f"dms_train_{datetime.now().strftime('%Y%m%d_%H%M')}"

    output_dir = RUNS_DIR / name
    output_dir.mkdir(parents=True, exist_ok=True)

    print(f"\n── Training ────────────────────────────────")
    print(f"  Model:       {model}")
    print(f"  Data:        {data_yaml}")
    print(f"  Epochs:      {epochs}")
    print(f"  Image size:  {imgsz}")
    print(f"  Batch:       {batch}")
    print(f"  Device:      {device}")
    print(f"  Output:      {output_dir}")
    print(f"───────────────────────────────────────────\n")

    model = YOLO(model)

    results = model.train(
        data=data_yaml,
        epochs=epochs,
        imgsz=imgsz,
        batch=batch,
        device=device,
        workers=workers,
        patience=patience,
        lr0=lr0,
        name=name,
        project=str(RUNS_DIR),
        exist_ok=True,
        # 数据增强（DMS 场景适度）
        hsv_h=0.01,
        hsv_s=0.5,
        hsv_v=0.3,
        degrees=5.0,       # 小角度旋转（车载视角稳定）
        translate=0.05,
        scale=0.3,
        fliplr=0.5,
        mosaic=0.5,
        mixup=0.1,
        # 验证
        val=True,
        save=True,
        save_period=10,
    )

    best_pt = Path(results.save_dir) / "weights" / "best.pt"
    print(f"\n[OK] Training complete. Best model: {best_pt}")

    # 复制到 sidecar 目录供推理使用
    dest = ROOT / "dms_best.pt"
    if best_pt.exists():
        import shutil
        shutil.copy2(best_pt, dest)
        print(f"[OK] Model copied to: {dest}")
        print(f"  Usage: python server.py --model dms_best.pt")

    return results


def validate(model_path: str, data_yaml: str = str(DATA_YAML),
             device: str = "cpu", imgsz: int = 640):
    """
    验证训练好的模型。
    """
    from ultralytics import YOLO

    if not Path(model_path).exists():
        print(f"[ERROR] Model not found: {model_path}")
        sys.exit(1)
    if not Path(data_yaml).exists():
        print(f"[ERROR] Dataset not found: {data_yaml}")
        sys.exit(1)

    model = YOLO(model_path)
    metrics = model.val(data=data_yaml, device=device, imgsz=imgsz)

    print(f"\n── Validation Results ───────────────────────")
    print(f"  mAP@0.5:       {metrics.box.map50:.4f}")
    print(f"  mAP@0.5:0.95:  {metrics.box.map:.4f}")
    print(f"  Precision:     {metrics.box.mp:.4f}")
    print(f"  Recall:        {metrics.box.mr:.4f}")

    # 按类别输出
    cls_names = model.names
    if hasattr(metrics.box, 'ap_class_index') and metrics.box.ap is not None:
        for cls_id, ap50 in zip(metrics.box.ap_class_index, metrics.box.ap):
            name = cls_names.get(int(cls_id), f"class_{cls_id}")
            print(f"  {name:20s}  AP@0.5={ap50:.4f}")

    return metrics


def export(model_path: str, format: str = "onnx", device: str = "cpu"):
    """
    导出模型为 ONNX / TensorRT / OpenVINO 格式。
    """
    from ultralytics import YOLO

    model = YOLO(model_path)
    exported = model.export(format=format, device=device)
    print(f"[OK] Exported to: {exported}")
    return exported


# ── CLI ──────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="YOLO DMS Training Pipeline")
    parser.add_argument("--data", default=str(DATA_YAML), help="dataset.yaml path")
    parser.add_argument("--model", default="yolov8n.pt",
                        help="Base model (yolov8n.pt / yolov8s.pt)")
    parser.add_argument("--epochs", type=int, default=50)
    parser.add_argument("--imgsz", type=int, default=640)
    parser.add_argument("--batch", type=int, default=8)
    parser.add_argument("--device", default="cpu", help="cpu / 0 / cuda:0")
    parser.add_argument("--workers", type=int, default=2)
    parser.add_argument("--patience", type=int, default=10)
    parser.add_argument("--lr0", type=float, default=0.001)
    parser.add_argument("--name", default=None)
    parser.add_argument("--validate-only", action="store_true",
                        help="Only validate existing model")
    parser.add_argument("--export", default=None,
                        help="Export format: onnx, openvino, tensorrt")
    parser.add_argument("--weights", default=None,
                        help="Model weights path for validate/export")

    args = parser.parse_args()

    if args.validate_only:
        if not args.weights:
            print("--weights required for validation")
            sys.exit(1)
        validate(args.weights, args.data, args.device, args.imgsz)
    elif args.export:
        if not args.weights:
            args.weights = str(ROOT / "dms_best.pt")
        export(args.weights, args.export, args.device)
    else:
        train(
            data_yaml=args.data,
            model=args.model,
            epochs=args.epochs,
            imgsz=args.imgsz,
            batch=args.batch,
            device=args.device,
            workers=args.workers,
            patience=args.patience,
            lr0=args.lr0,
            name=args.name,
        )


if __name__ == "__main__":
    main()
