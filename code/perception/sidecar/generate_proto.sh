#!/bin/bash
# Generate Python gRPC stubs from proto
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROTO_DIR="$SCRIPT_DIR/../proto"
OUT_DIR="$SCRIPT_DIR"

python -m grpc_tools.protoc \
    -I "$PROTO_DIR" \
    --python_out="$OUT_DIR" \
    --grpc_python_out="$OUT_DIR" \
    "$PROTO_DIR/dms_perception.proto"

echo "Generated: $OUT_DIR/dms_perception_pb2.py"
echo "Generated: $OUT_DIR/dms_perception_pb2_grpc.py"
