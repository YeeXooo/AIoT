#!/bin/bash
# AIoT DMS 感知层一键环境搭建
# 用法: bash setup.sh

set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo "=== 创建 Python 3.11 虚拟环境 ==="
if command -v conda &> /dev/null; then
    conda create -n aiot-perception python=3.11 -y 2>/dev/null || true
    source "$(conda info --base)/etc/profile.d/conda.sh"
    conda activate aiot-perception
    echo "[OK] conda env: aiot-perception"
elif command -v python3.11 &> /dev/null; then
    python3.11 -m venv "$SCRIPT_DIR/venv"
    source "$SCRIPT_DIR/venv/bin/activate"
    echo "[OK] venv: $SCRIPT_DIR/venv"
else
    echo "[WARN] Python 3.11 not found, using system python"
fi

echo ""
echo "=== 安装依赖 ==="
pip install -r "$SCRIPT_DIR/requirements.txt" 2>&1 | tail -3
echo "[OK] dependencies installed"

echo ""
echo "=== 生成合成训练数据 ==="
python "$SCRIPT_DIR/data_prep.py" synthetic -n 300
echo "[OK] 300 张合成图已生成"

echo ""
echo "=== 环境就绪 ==="
echo ""
echo "  训练: cd code/perception/sidecar && python train.py --epochs 50"
echo "  推理: cd code/perception/sidecar && python server.py --source camera"
echo "  测试: cd code/perception/tests && python test_perception.py"
echo ""
echo "  GPU 训练: python train.py --epochs 100 --device 0 --batch 16"
