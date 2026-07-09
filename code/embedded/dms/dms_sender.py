"""
PC端DMS数据发送器 — 电脑摄像头 → AI视觉 → 串口 → WS63

依赖: pip install opencv-python mediapipe pyserial
用法: python dms_sender.py --port COM3 --baud 115200

工作流程:
  1. 打开电脑摄像头
  2. MediaPipe Face Mesh检测面部468个关键点
  3. 计算左眼/右眼纵横比(EAR) → PERCLOS
  4. 检测哈欠(嘴部纵横比MAR)
  5. 通过串口发送结构化数据到WS63

协议格式: "DMS,PERCLOS:0.xx,YAWN:n,HAT:0,PHONE:0,LVL:n\n"
"""

import cv2
import mediapipe as mp
import serial
import time
import argparse
import numpy as np

# ==================== 配置 ====================
EAR_THRESHOLD = 0.21      # 眼睛纵横比阈值(低于此值视为闭眼)
MAR_THRESHOLD = 0.65      # 嘴部纵横比阈值(高于此值视为打哈欠)
PERCLOS_WINDOW = 60       # PERCLOS计算窗口(帧数, 约2秒@30fps)
SEND_INTERVAL = 0.05      # 串口发送间隔(秒) — 匹配WS63 50ms tick

# ==================== MediaPipe初始化 ====================
mp_face_mesh = mp.solutions.face_mesh
face_mesh = mp_face_mesh.FaceMesh(
    max_num_faces=1,
    refine_landmarks=True,
    min_detection_confidence=0.5,
    min_tracking_confidence=0.5)

# 面部关键点索引 (MediaPipe Face Mesh)
# 左眼: 33, 160, 158, 133, 153, 144  (上下眼睑)
# 右眼: 362, 385, 387, 263, 373, 380
# 嘴唇: 61, 291, 13, 14  (上下嘴唇)
LEFT_EYE  = [33, 160, 158, 133, 153, 144]
RIGHT_EYE = [362, 385, 387, 263, 373, 380]
MOUTH_TOP    = 13
MOUTH_BOTTOM = 14
MOUTH_LEFT   = 61
MOUTH_RIGHT  = 291

# PERCLOS滑动窗口
perclos_history = []

# ==================== 计算函数 ====================
def eye_aspect_ratio(eye_landmarks, landmarks):
    """计算眼睛纵横比 EAR = (|p2-p6|+|p3-p5|) / (2*|p1-p4|)"""
    def dist(p1, p2):
        return np.linalg.norm(np.array(p1) - np.array(p2))

    pts = [(int(landmarks[i].x * 640), int(landmarks[i].y * 480)) for i in eye_landmarks]
    ear = (dist(pts[1], pts[5]) + dist(pts[2], pts[4])) / (2.0 * dist(pts[0], pts[3]))
    return ear

def mouth_aspect_ratio(landmarks):
    """计算嘴部纵横比 MAR = |top-bottom| / |left-right|"""
    top    = (int(landmarks[MOUTH_TOP].x * 640), int(landmarks[MOUTH_TOP].y * 480))
    bottom = (int(landmarks[MOUTH_BOTTOM].x * 640), int(landmarks[MOUTH_BOTTOM].y * 480))
    left   = (int(landmarks[MOUTH_LEFT].x * 640), int(landmarks[MOUTH_LEFT].y * 480))
    right  = (int(landmarks[MOUTH_RIGHT].x * 640), int(landmarks[MOUTH_RIGHT].y * 480))
    mar = np.linalg.norm(np.array(top) - np.array(bottom)) / \
          np.linalg.norm(np.array(left) - np.array(right))
    return mar

# ==================== 主循环 ====================
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--port', default='COM3', help='串口号')
    parser.add_argument('--baud', type=int, default=115200, help='波特率')
    parser.add_argument('--camera', type=int, default=0, help='摄像头ID')
    args = parser.parse_args()

    # 打开串口
    try:
        ser = serial.Serial(args.port, args.baud, timeout=0.1)
        print(f"[DMS] Serial {args.port} {args.baud}bps opened")
    except Exception as e:
        print(f"[DMS] Serial error: {e}")
        print("[DMS] Continue without serial (debug mode)...")
        ser = None

    # 打开摄像头
    cap = cv2.VideoCapture(args.camera)
    if not cap.isOpened():
        print(f"[DMS] Camera {args.camera} not found!")
        return
    print(f"[DMS] Camera {args.camera} opened")

    yawn_count = 0
    frame_count = 0
    last_send = time.time()

    while True:
        ret, frame = cap.read()
        if not ret:
            continue

        frame = cv2.flip(frame, 1)  # 镜像
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        results = face_mesh.process(rgb)

        perclos = 0.0
        yawn = 0
        pc_lvl = 0
        ear_left = ear_right = 1.0
        mar = 0.0

        if results.multi_face_landmarks:
            lm = results.multi_face_landmarks[0].landmark

            # 计算EAR
            ear_left  = eye_aspect_ratio(LEFT_EYE, lm)
            ear_right = eye_aspect_ratio(RIGHT_EYE, lm)
            ear_avg = (ear_left + ear_right) / 2.0

            # 计算MAR
            mar = mouth_aspect_ratio(lm)

            # PERCLOS: 闭眼帧占比(滑动窗口)
            is_eye_closed = (ear_avg < EAR_THRESHOLD)
            perclos_history.append(is_eye_closed)
            if len(perclos_history) > PERCLOS_WINDOW:
                perclos_history.pop(0)
            perclos = sum(perclos_history) / len(perclos_history)

            # 哈欠检测
            if mar > MAR_THRESHOLD:
                yawn = 1
                yawn_count += 1

            # PC端预判疲劳等级
            # 实际产品可用机器学习模型, 此处用简单规则
            if perclos > 0.5:
                pc_lvl = 2   # 重度
            elif perclos > 0.3:
                pc_lvl = 1   # 轻度

            # 可视化
            cv2.putText(frame, f"EAR: {ear_avg:.2f}", (10, 30),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)
            cv2.putText(frame, f"PERCLOS: {perclos:.2f}", (10, 60),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7,
                       (0, 0, 255) if perclos > 0.3 else (0, 255, 0), 2)
            cv2.putText(frame, f"MAR: {mar:.2f} YAWN: {yawn}", (10, 90),
                       cv2.FONT_HERSHEY_SIMPLEX, 0.7, (0, 255, 0), 2)

        # 定时发送到WS63
        now = time.time()
        if now - last_send >= SEND_INTERVAL:
            last_send = now
            msg = f"DMS,PERCLOS:{perclos:.2f},YAWN:{yawn_count},HAT:0,PHONE:0,LVL:{pc_lvl}\n"
            if ser:
                ser.write(msg.encode())
            else:
                print(f"[DMS] {msg.strip()}")

        cv2.imshow("DMS - Vehicle Safety Monitor", frame)
        if cv2.waitKey(1) & 0xFF == ord('q'):
            break

        frame_count += 1

    cap.release()
    cv2.destroyAllWindows()
    if ser:
        ser.close()

if __name__ == '__main__':
    main()
