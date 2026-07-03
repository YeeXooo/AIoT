#!/usr/bin/env python3
"""
性能测试——测试数据预置脚本。
通过 REST API 批量创建 Driver 和 Health Profile 数据。

用法:
  python3 performance-test/seed_data.py [--count N] [--token TOKEN]
"""
import requests, sys, os, json, uuid

COUNT = int(sys.argv[sys.argv.index("--count")+1]) if "--count" in sys.argv else 100
BASE = "http://localhost:8080"

def get_token():
    token = sys.argv[sys.argv.index("--token")+1] if "--token" in sys.argv else os.environ.get("BEARER_TOKEN")
    if token:
        return token

    # Auto-generate token
    import subprocess
    repo = os.path.expanduser("~/.m2/repository")
    jars = ":".join([
        "/tmp/jwt-runner", "code/server/target/classes",
        f"{repo}/io/jsonwebtoken/jjwt-api/0.12.5/jjwt-api-0.12.5.jar",
        f"{repo}/io/jsonwebtoken/jjwt-impl/0.12.5/jjwt-impl-0.12.5.jar",
        f"{repo}/io/jsonwebtoken/jjwt-jackson/0.12.5/jjwt-jackson-0.12.5.jar",
        f"{repo}/com/fasterxml/jackson/core/jackson-core/2.15.4/jackson-core-2.15.4.jar",
        f"{repo}/com/fasterxml/jackson/core/jackson-databind/2.15.4/jackson-databind-2.15.4.jar",
        f"{repo}/com/fasterxml/jackson/core/jackson-annotations/2.15.4/jackson-annotations-2.15.4.jar",
    ])
    r = subprocess.run(
        ["java", "-cp", jars, "TokenGenerator",
         "/tmp/aiot-ci-keystore.p12", "aiot-keystore-change-me",
         "aiot-master-key", "aiot-master-key-pwd",
         "perf-seed", "FAMILY"],
        capture_output=True, text=True,
        cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    for line in r.stdout.splitlines():
        if line.startswith("eyJ") and "ACCESS" not in r.stdout[:r.stdout.index(line)]:
            pass
        if line.startswith("eyJ"):
            return line.strip()
    raise RuntimeError(f"Token generation failed:\n{r.stdout}\n{r.stderr}")

TOKEN = get_token()
H = {"Authorization": f"Bearer {TOKEN}", "Content-Type": "application/json"}
SURNAMES = ["张","李","王","刘","陈","杨","赵","黄","周","吴","徐","孙","马","朱","胡","郭","何","高","林","郑"]
GIVENS  = ["伟","芳","娜","敏","静","强","磊","洋","涛","军","勇","杰","丽","艳","秀英","刚"]

driver_ids = []
print(f"=== Seed {COUNT} Drivers ===")
for i in range(1, COUNT + 1):
    name = SURNAMES[i % len(SURNAMES)] + GIVENS[(i * 7) % len(GIVENS)]
    phone = f"138{i:08d}"
    r = requests.post(f"{BASE}/api/v1/driver", headers=H, json={"name": name, "phone": phone})
    if r.status_code == 200:
        did = str(uuid.uuid4())
        try:
            body = r.json()
            did_val = body.get("driverId", {})
            if isinstance(did_val, dict):
                did = did_val.get("id", did)
            elif isinstance(did_val, str):
                did = did_val
        except:
            pass
        driver_ids.append(did)
    else:
        print(f"  ERROR [{i}] {r.status_code}: {r.text[:80]}")
    if i % 20 == 0:
        print(f"  ... {i}/{COUNT}")

print(f"  Created: {len(driver_ids)} drivers")

# Create Health Profiles
print(f"\n=== Seed {len(driver_ids)} Health Profiles ===")
blood_types = ["A", "B", "AB", "O"]
for i, did in enumerate(driver_ids):
    r = requests.put(f"{BASE}/api/v1/health/{did}", headers=H, json={
        "bloodType": blood_types[i % 4],
        "chronicHistory": "{}",
        "allergyHistory": "{}",
        "medicationHistory": "{}",
        "emergencyContact": "{}",
        "baselineVitals": "{}"
    })
    if r.status_code != 200:
        print(f"  ERROR health[{did[:8]}]: {r.status_code}")
    if (i+1) % 20 == 0:
        print(f"  ... {i+1}/{len(driver_ids)}")

# Verify
print(f"\n=== Verify ===")
r = requests.get(f"{BASE}/api/v1/driver/list", headers=H)
print(f"  Drivers: {len(r.json()) if r.status_code == 200 else f'ERROR {r.status_code}'}")
r = requests.get(f"{BASE}/api/v1/safety/vehicle/list", headers=H)
print(f"  Vehicles: {len(r.json()) if r.status_code == 200 else f'ERROR {r.status_code}'}")

print(f"\n=== Done === Token (1h): {TOKEN}")
