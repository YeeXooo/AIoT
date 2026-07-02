"""
DMS 数据集准备工具。

支持：
1. 从公开数据集下载并转换为 YOLO 格式
2. 结构：
   data/
     raw/           原始下载数据
     yolo/
       images/train/  训练图像
       images/val/    验证图像
       labels/train/  YOLO 标签
       labels/val/
     dataset.yaml   YOLO 数据集配置

DMS 专用类别（COCO 超集）：
  0: steering_wheel   方向盘
  1: hand_on_wheel    手扶方向盘
  2: hand_off_wheel   手离方向盘
  3: cell_phone       手机
  4: cigarette        香烟
  5: face             人脸
  6: seatbelt         安全带

来源（本期可验收）：
  - 录制视频 + manual/semi-auto annotation (LabelImg/Labelme)
  - Roboflow Universe DMS 公开数据集
  - State Farm Distracted Driver 数据集（Kaggle，转为方向盘/手机检测）
"""

import argparse
import json
import os
import shutil
import sys
import zipfile
from pathlib import Path
from typing import List, Tuple

import cv2
import numpy as np


# ── 项目根路径 ──
ROOT = Path(__file__).resolve().parent
PERCEPTION_ROOT = ROOT.parent
DATA = PERCEPTION_ROOT / "data"
RAW = DATA / "raw"
YOLO_DIR = DATA / "yolo"
YOLO_IMAGES_TRAIN = YOLO_DIR / "images" / "train"
YOLO_IMAGES_VAL = YOLO_DIR / "images" / "val"
YOLO_LABELS_TRAIN = YOLO_DIR / "labels" / "train"
YOLO_LABELS_VAL = YOLO_DIR / "labels" / "val"

CLASSES = [
    "steering_wheel",
    "hand_on_wheel",
    "hand_off_wheel",
    "cell_phone",
    "cigarette",
    "face",
    "seatbelt",
]

CLASS_TO_ID = {name: i for i, name in enumerate(CLASSES)}


def ensure_dirs():
    for d in [RAW, YOLO_IMAGES_TRAIN, YOLO_IMAGES_VAL,
              YOLO_LABELS_TRAIN, YOLO_LABELS_VAL]:
        d.mkdir(parents=True, exist_ok=True)


def write_dataset_yaml():
    """生成 dataset.yaml 供 YOLO 训练使用。"""
    content = f"""# DMS 驾驶监测数据集配置
path: {YOLO_DIR}
train: images/train
val: images/val
nc: {len(CLASSES)}
names: {CLASSES}
"""
    (YOLO_DIR / "dataset.yaml").write_text(content)
    print(f"[OK] Written: {YOLO_DIR / 'dataset.yaml'}")


# ── 从 Roboflow 下载公开 DMS 数据集 ──────────

def download_roboflow(api_key: str, workspace: str, project: str, version: int):
    """
    从 Roboflow Universe 下载公开 DMS 数据集。
    示例：
      workspace="dms-project", project="driver-monitoring", version=1

    也可手动：https://universe.roboflow.com/ 搜索 "driver monitoring" "distracted driver"
    下载 YOLOv8 格式，解压到 data/raw/ 后再拆分。
    """
    try:
        from roboflow import Roboflow
    except ImportError:
        print("[ERROR] roboflow package not installed. Run: pip install roboflow")
        return

    rf = Roboflow(api_key=api_key)
    project = rf.workspace(workspace).project(project)
    dataset = project.version(version).download("yolov8")
    print(f"[OK] Downloaded YOLOv8 dataset to: {dataset.location}")

    # 复制到本地 YOLO 目录
    src = Path(dataset.location)
    for subset, img_dir, lbl_dir in [("train", YOLO_IMAGES_TRAIN, YOLO_LABELS_TRAIN),
                                      ("valid", YOLO_IMAGES_VAL, YOLO_LABELS_VAL),
                                      ("test", YOLO_IMAGES_VAL, YOLO_LABELS_VAL)]:
        s_img = src / subset / "images"
        s_lbl = src / subset / "labels"
        if s_img.exists():
            for f in s_img.iterdir():
                shutil.copy2(f, img_dir / f.name)
            for f in s_lbl.iterdir():
                shutil.copy2(f, lbl_dir / f.name)
    print("[OK] Dataset copied to yolo/ structure")


# ── State Farm Distracted Driver 转换 ──────────

SFDD_CLASSES = [
    "c0_safe_driving",
    "c1_texting_right",
    "c2_talking_phone_right",
    "c3_texting_left",
    "c4_talking_phone_left",
    "c5_operating_radio",
    "c6_drinking",
    "c7_reaching_behind",
    "c8_hair_makeup",
    "c9_talking_passenger",
]

def convert_state_farm(sfdd_dir: str, val_ratio: float = 0.2):
    """
    将 State Farm Distracted Driver 数据集（分类→检测）转换为 DMS 检测格式。

    SFDD 每个类别目录下是整张图片，只标注是否分心。
    我们将其转为弱监督：每张图标注一个 phone detected 框（全图框，在检测时作为弱标签）。

    真实部署建议：用 LabelImg/Labelme 对 SFDD 子集做精确 bbox 标注。
    """
    sfdd_path = Path(sfdd_dir)
    if not sfdd_path.exists():
        print(f"[ERROR] {sfdd_dir} not found. "
              "Download from: https://www.kaggle.com/c/state-farm-distracted-driver-detection")
        return

    ensure_dirs()
    phone_classes = {"c1", "c2", "c3", "c4"}

    all_images = []
    for cls_dir in sfdd_path.iterdir():
        if not cls_dir.is_dir():
            continue
        cls_name = cls_dir.name
        has_phone = cls_name in phone_classes
        for img_file in cls_dir.glob("*.jpg"):
            all_images.append((img_file, has_phone))

    np.random.seed(42)
    np.random.shuffle(all_images)
    split = int(len(all_images) * (1 - val_ratio))

    for subset, items in [("train", all_images[:split]), ("val", all_images[split:])]:
        img_dir = YOLO_DIR / "images" / subset
        lbl_dir = YOLO_DIR / "labels" / subset
        img_dir.mkdir(parents=True, exist_ok=True)
        lbl_dir.mkdir(parents=True, exist_ok=True)

        for i, (img_path, has_phone) in enumerate(items):
            new_name = f"sfdd_{subset}_{i:06d}.jpg"
            img = cv2.imread(str(img_path))
            if img is None:
                continue
            h, w = img.shape[:2]
            cv2.imwrite(str(img_dir / new_name), img)

            if has_phone:
                # 弱标签：全图框标注为 cell_phone (class 3)
                # 真实标注应为精确 bbox
                label = f"{CLASS_TO_ID['cell_phone']} 0.5 0.5 0.4 0.4\n"
                (lbl_dir / f"sfdd_{subset}_{i:06d}.txt").write_text(label)
            else:
                (lbl_dir / f"sfdd_{subset}_{i:06d}.txt").touch()

    print(f"[OK] Converted {len(all_images)} images from State Farm dataset")
    write_dataset_yaml()


# ── 合成数据生成（开发阶段快速验证）────────────────

def generate_synthetic_dataset(num_images: int = 200, val_ratio: float = 0.2):
    """
    生成含位置随机性的合成方向盘 + 手部图像，用于训练管线验证。

    避免对真实图像的依赖，适合 CI 和快速迭代。
    """
    ensure_dirs()

    np.random.seed(42)

    for i in range(num_images):
        h, w = 480, 640
        img = np.random.randint(40, 80, (h, w, 3), dtype=np.uint8)

        # 方向盘：椭圆在下半部
        cx = int(w * 0.5 + np.random.randn() * w * 0.05)
        cy = int(h * 0.65 + np.random.randn() * 10)
        rx = int(w * 0.25 + np.random.rand() * 20)
        ry = int(h * 0.15 + np.random.rand() * 10)
        cv2.ellipse(img, (cx, cy), (rx, ry), 0, 0, 360,
                    (180, 180, 180), -1)
        cv2.ellipse(img, (cx, cy), (rx, ry), 0, 0, 360,
                    (100, 100, 100), 3)

        # 方向盘 bbox
        sw_x = (cx - rx) / w
        sw_y = (cy - ry) / h
        sw_w = (2 * rx) / w
        sw_h = (2 * ry) / h

        labels = []
        # 方向盘
        labels.append(f"{CLASS_TO_ID['steering_wheel']} "
                      f"{sw_x + sw_w/2:.4f} {sw_y + sw_h/2:.4f} "
                      f"{sw_w:.4f} {sw_h:.4f}")

        # 手部：随机位置，70% 概率在方向盘区域
        has_hand = np.random.random() < 0.9
        hand_class = CLASS_TO_ID['hand_on_wheel']

        if has_hand:
            if np.random.random() < 0.7:
                # 手在方向盘上
                hx = int(cx + np.random.randn() * rx * 0.6)
                hy = int(cy + np.random.randn() * ry * 0.6)
            else:
                # 手离方向盘
                hx = int(w * np.random.random())
                hy = int(h * np.random.random())
                hand_class = CLASS_TO_ID['hand_off_wheel']

            hr = int(15 + np.random.rand() * 15)
            cv2.circle(img, (hx, hy), hr, (220, 200, 180), -1)
            cv2.circle(img, (hx, hy), hr, (100, 80, 60), 2)

            hx_n = hx / w
            hy_n = hy / h
            hw_n = (2 * hr) / w
            hh_n = (2 * hr) / h
            labels.append(f"{hand_class} {hx_n:.4f} {hy_n:.4f} {hw_n:.4f} {hh_n:.4f}")

        # 人脸：随机在上半部分
        if np.random.random() < 0.8:
            fx = int(w * 0.5 + np.random.randn() * w * 0.1)
            fy = int(h * 0.25 + np.random.randn() * 20)
            fr = int(30 + np.random.rand() * 20)
            cv2.circle(img, (fx, fy), fr, (150, 180, 210), -1)
            fx_n = fx / w
            fy_n = fy / h
            fw_n = (2 * fr) / w
            fh_n = (2 * fr) / h
            labels.append(f"{CLASS_TO_ID['face']} {fx_n:.4f} {fy_n:.4f} {fw_n:.4f} {fh_n:.4f}")

        subset = "val" if i < int(num_images * val_ratio) else "train"
        img_dir = YOLO_DIR / "images" / subset
        lbl_dir = YOLO_DIR / "labels" / subset
        img_dir.mkdir(parents=True, exist_ok=True)
        lbl_dir.mkdir(parents=True, exist_ok=True)

        fname = f"synth_{i:06d}"
        cv2.imwrite(str(img_dir / f"{fname}.jpg"), img)
        (lbl_dir / f"{fname}.txt").write_text("\n".join(labels))

    print(f"[OK] Generated {num_images} synthetic images")
    write_dataset_yaml()


# ── CLI ──────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(description="DMS Dataset Preparation")
    sub = parser.add_subparsers(dest="cmd")

    # 合成数据
    p_synth = sub.add_parser("synthetic", help="Generate synthetic DMS dataset")
    p_synth.add_argument("-n", type=int, default=200, help="Number of images")
    p_synth.add_argument("--val-ratio", type=float, default=0.2)

    # State Farm 转换
    p_sf = sub.add_parser("state-farm", help="Convert State Farm dataset")
    p_sf.add_argument("--dir", required=True, help="Path to SFDD dataset")

    # Roboflow 下载
    p_rf = sub.add_parser("roboflow", help="Download from Roboflow Universe")
    p_rf.add_argument("--api-key", required=True)
    p_rf.add_argument("--workspace", required=True)
    p_rf.add_argument("--project", required=True)
    p_rf.add_argument("--version", type=int, default=1)

    args = parser.parse_args()

    if args.cmd == "synthetic":
        generate_synthetic_dataset(num_images=args.n, val_ratio=args.val_ratio)
    elif args.cmd == "state-farm":
        convert_state_farm(args.dir)
    elif args.cmd == "roboflow":
        download_roboflow(args.api_key, args.workspace, args.project, args.version)
    else:
        print("Generate synthetic dataset by default...")
        generate_synthetic_dataset()


if __name__ == "__main__":
    main()
