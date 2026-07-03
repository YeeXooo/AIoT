#!/usr/bin/env python3
"""
性能测试——Token 批量生成器 (通过 Java TokenGenerator 批量模式)。
用法:
  python3 performance-test/gen_tokens.py [--families N] [--managers N] [--rescues N] [--output FILE]
"""
import subprocess, sys, os

FAMILIES = 0; MANAGERS = 0; RESCUES = 0
OUTPUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "tokens.csv")

for a in sys.argv:
    if a.startswith("--families="): FAMILIES = int(a.split("=")[1])
    if a.startswith("--managers="): MANAGERS = int(a.split("=")[1])
    if a.startswith("--rescues="):  RESCUES  = int(a.split("=")[1])
    if a.startswith("--output="):   OUTPUT   = a.split("=", 1)[1]

# Support legacy --count N
for i, a in enumerate(sys.argv):
    if a == "--count" and i+1 < len(sys.argv):
        FAMILIES = MANAGERS = RESCUES = int(sys.argv[i+1])

if FAMILIES + MANAGERS + RESCUES == 0:
    FAMILIES = MANAGERS = RESCUES = 50  # default

REPO = os.path.expanduser("~/.m2/repository")
CP = ":".join([
    "/tmp/jwt-runner", "code/server/target/classes",
    f"{REPO}/io/jsonwebtoken/jjwt-api/0.12.5/jjwt-api-0.12.5.jar",
    f"{REPO}/io/jsonwebtoken/jjwt-impl/0.12.5/jjwt-impl-0.12.5.jar",
    f"{REPO}/io/jsonwebtoken/jjwt-jackson/0.12.5/jjwt-jackson-0.12.5.jar",
    f"{REPO}/com/fasterxml/jackson/core/jackson-core/2.15.4/jackson-core-2.15.4.jar",
    f"{REPO}/com/fasterxml/jackson/core/jackson-databind/2.15.4/jackson-databind-2.15.4.jar",
    f"{REPO}/com/fasterxml/jackson/core/jackson-annotations/2.15.4/jackson-annotations-2.15.4.jar",
])

args = ["java", "-cp", CP, "TokenGenerator",
        "/tmp/aiot-ci-keystore.p12", "aiot-keystore-change-me",
        "aiot-master-key", "aiot-master-key-pwd"]
if FAMILIES: args += ["FAMILY", str(FAMILIES)]
if MANAGERS: args += ["MANAGER", str(MANAGERS)]
if RESCUES:  args += ["RESCUE", str(RESCUES)]

r = subprocess.run(args,
    capture_output=True, text=True,
    cwd=os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

with open(OUTPUT, "w") as f:
    f.write(r.stdout)

lines = r.stdout.strip().split("\n")
print(f"Generated {len(lines)-1} tokens -> {OUTPUT}")
print(f"  FAMILY: {FAMILIES}, MANAGER: {MANAGERS}, RESCUE: {RESCUES}")
