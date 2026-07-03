#!/bin/bash
# ───────────────────────────────────────────────────────────
# AIoT 性能测试——一键环境准备脚本
# 供 Leming WebRunner 压测前执行
# ───────────────────────────────────────────────────────────
set -euo pipefail
PROJECT="$(cd "$(dirname "$0")/.." && pwd)"
PERF_DIR="$PROJECT/performance-test"
SERVER_LOG="/tmp/aiot-perf-server.log"
BASE_URL="${BASE_URL:-http://localhost:8080}"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

log()  { echo -e "${GREEN}[INFO]${NC}  $*"; }
warn() { echo -e "${YELLOW}[WARN]${NC}  $*"; }
err()  { echo -e "${RED}[ERROR]${NC} $*"; }

# ── 1. 启动后端服务 ──────────────────────────────
start_server() {
    log "检查服务状态..."
    if curl -s "$BASE_URL/api/v1/driver/list" > /dev/null 2>&1; then
        log "服务已在运行: $BASE_URL"
        return 0
    fi

    log "启动 Spring Boot 服务 (ci profile, H2 内存数据库)..."
    cd "$PROJECT/code/server"
    setsid mvn spring-boot:run -Dspring-boot.run.profiles=ci -q > "$SERVER_LOG" 2>&1 &
    disown
    local pid=$!

    log "等待服务就绪 (PID=$pid)..."
    for i in $(seq 1 30); do
        if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/v1/driver/list" 2>/dev/null | grep -q "403\|200"; then
            log "服务已就绪 (耗时 ${i}s)"
            return 0
        fi
        sleep 1
    done
    err "服务启动超时，查看日志: tail -f $SERVER_LOG"
    return 1
}

# ── 2. 编译 Token 生成器 ─────────────────────────
build_tokens() {
    log "编译 JWT Token 生成器..."
    local repo="$HOME/.m2/repository"
    local cp="$PROJECT/code/server/target/classes"
    cp="$cp:$repo/io/jsonwebtoken/jjwt-api/0.12.5/jjwt-api-0.12.5.jar"
    cp="$cp:$repo/io/jsonwebtoken/jjwt-impl/0.12.5/jjwt-impl-0.12.5.jar"
    cp="$cp:$repo/io/jsonwebtoken/jjwt-jackson/0.12.5/jjwt-jackson-0.12.5.jar"
    cp="$cp:$repo/com/fasterxml/jackson/core/jackson-core/2.15.4/jackson-core-2.15.4.jar"
    cp="$cp:$repo/com/fasterxml/jackson/core/jackson-databind/2.15.4/jackson-databind-2.15.4.jar"
    cp="$cp:$repo/com/fasterxml/jackson/core/jackson-annotations/2.15.4/jackson-annotations-2.15.4.jar"
    mkdir -p /tmp/jwt-runner
    javac -cp "$cp" -d /tmp/jwt-runner "$PERF_DIR/TokenGenerator.java" 2>/dev/null && \
        log "Token 生成器编译成功" || log "Token 生成器已存在，跳过编译"
}

# ── 3. 批量生成 Token CSV ────────────────────────
gen_tokens() {
    local count="${1:-50}"
    log "批量生成 Token (FAMILY×${count} MANAGER×${count} RESCUE×${count})..."
    python3 "$PERF_DIR/gen_tokens.py" --count "$count" --output "$PERF_DIR/tokens.csv"
    log "Token 文件: $PERF_DIR/tokens.csv"
}

# ── 4. 获取单个 Token ───────────────────────────
get_token() {
    local account="${1:-perf-family-001}"
    local role="${2:-FAMILY}"
    local repo="$HOME/.m2/repository"
    local cp="/tmp/jwt-runner:$PROJECT/code/server/target/classes"
    cp="$cp:$repo/io/jsonwebtoken/jjwt-api/0.12.5/jjwt-api-0.12.5.jar"
    cp="$cp:$repo/io/jsonwebtoken/jjwt-impl/0.12.5/jjwt-impl-0.12.5.jar"
    cp="$cp:$repo/io/jsonwebtoken/jjwt-jackson/0.12.5/jjwt-jackson-0.12.5.jar"
    cp="$cp:$repo/com/fasterxml/jackson/core/jackson-core/2.15.4/jackson-core-2.15.4.jar"
    cp="$cp:$repo/com/fasterxml/jackson/core/jackson-databind/2.15.4/jackson-databind-2.15.4.jar"
    cp="$cp:$repo/com/fasterxml/jackson/core/jackson-annotations/2.15.4/jackson-annotations-2.15.4.jar"

    java -cp "$cp" TokenGenerator \
        /tmp/aiot-ci-keystore.p12 \
        aiot-keystore-change-me \
        aiot-master-key \
        aiot-master-key-pwd \
        "$account" "$role" 2>&1 | grep '^eyJ' | head -1
}

# ── 5. 预置测试数据 ─────────────────────────────
seed_data() {
    local token
    token=$(get_token "perf-seed" "FAMILY")
    log "预置测试数据 (使用 FAMILY Token)..."

    local h="-H \"Authorization: Bearer $token\" -H \"Content-Type: application/json\""

    # 批量创建 Driver
    log "创建 Drivers..."
    for i in $(seq 1 50); do
        local name="driver_$(printf %03d $i)"
        local phone="138$(printf %08d $((10000000 + i)))"
        curl -s -X POST "$BASE_URL/api/v1/driver" \
            -H "Authorization: Bearer $token" \
            -H "Content-Type: application/json" \
            -d "{\"name\":\"$name\",\"phone\":\"$phone\"}" > /dev/null
        [ $((i % 10)) -eq 0 ] && echo -n "."
    done
    echo ""

    # 获取已创建的 driver IDs
    local drivers
    drivers=$(curl -s -H "Authorization: Bearer $token" "$BASE_URL/api/v1/driver/list")
    local count
    count=$(echo "$drivers" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d))" 2>/dev/null || echo "?")
    log "已创建 $count 条 Driver 记录"

    # 创建 Health Profile
    log "创建 Health Profiles..."
    local driver_ids
    driver_ids=$(curl -s -H "Authorization: Bearer $token" "$BASE_URL/api/v1/driver/list" | \
        python3 -c "
import sys, json
drivers = json.load(sys.stdin)
for d in drivers:
    did = d.get('driverId', {})
    if isinstance(did, dict):
        print(did.get('id', ''))
    else:
        print(did)
" 2>/dev/null)

    local h_count=0
    for did in $driver_ids; do
        [ -z "$did" ] && continue
        curl -s -X PUT "$BASE_URL/api/v1/health/$did" \
            -H "Authorization: Bearer $token" \
            -H "Content-Type: application/json" \
            -d "{\"bloodType\":\"A\",\"chronicHistory\":\"{}\",\"allergyHistory\":\"{}\",\"medicationHistory\":\"{}\",\"emergencyContact\":\"{}\",\"baselineVitals\":\"{}\"}" > /dev/null
        h_count=$((h_count + 1))
        [ $((h_count % 10)) -eq 0 ] && echo -n "."
    done
    echo ""
    log "已创建 $h_count 条 Health Profile 记录"

    log "数据预置完成"
}

# ── 6. 输出 Token 供 WebRunner 使用 ─────────────
print_tokens() {
    echo ""
    echo "=========================================="
    echo "  供 WebRunner 脚本录制使用的 Token"
    echo "=========================================="
    echo "FAMILY:  $(get_token "perf-family-001" "FAMILY")"
    echo "MANAGER: $(get_token "perf-manager-001" "MANAGER")"
    echo "RESCUE:  $(get_token "perf-rescue-001" "RESCUE")"
    echo "=========================================="
}

# ── Main ────────────────────────────────────────
case "${1:-all}" in
    start)   start_server ;;
    tokens)  build_tokens && gen_tokens "${2:-50}" ;;
    seed)    seed_data ;;
    all)
        start_server
        build_tokens
        gen_tokens 50
        seed_data
        print_tokens
        log "环境准备完成！Token 文件: $PERF_DIR/tokens.csv"
        ;;
    *)
        echo "用法: $0 {start|tokens|seed|all}"
        echo "  start   启动后端服务"
        echo "  tokens  生成测试 Token (默认 50 个/角色)"
        echo "  seed    预置测试数据（需先启动服务）"
        echo "  all     一键完成全部准备"
        ;;
esac
