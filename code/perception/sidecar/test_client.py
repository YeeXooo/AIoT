"""gRPC test client — connect to DMS perception and print feature frames."""
import sys, time
sys.path.insert(0, ".")
import grpc
import dms_perception_pb2 as pb2
import dms_perception_pb2_grpc as pb2_grpc

channel = grpc.insecure_channel("localhost:50051")
stub = pb2_grpc.DmsPerceptionStub(channel)

health = stub.Health(pb2.HealthRequest(), timeout=3)
print(f"Server: alive, uptime={health.start_time_ms}, fps={health.current_fps}")

print(f"\n{'#':>4} {'perclos':>8} {'yawn':>6} {'nod':>7} {'gaze':>7} {'handsOff':>9} {'conf':>6}")
print("-" * 65)

def ctrl():
    yield pb2.ControlSignal()
    while True:
        time.sleep(1)

count = 0
for frame in stub.StreamFeatures(ctrl()):
    count += 1
    print(f"{count:>4} {frame.perclos:>8.3f} {frame.yawn_freq:>6.1f} {frame.head_nod_freq:>7.1f} "
          f"{frame.gaze_deviation_cumulative:>7.1f} {frame.hands_off_wheel:>9.3f} {frame.confidence:>6.2f}")
    if count >= 15:
        break

print(f"\nDone. {count} frames.")
channel.close()
