# 项目测试缺口记录

> 日期：2026-07-03  |  目标：记录 AIoT 项目各模块尚未覆盖的测试项及实现建议

## 一、总览

| 模块 | 语言 | 测试用例数 | 测试文件数 | 状态 |
|------|------|:--------:|:--------:|:----:|
| server | Java 17 | 875 | 76 | 基本完成 |
| perception | Python 3 | ~30 | 1 | 需补充 |
| frontend/entry | ArkTS | 0 | 4 | 全部骨架 |
| frontend/front | ArkTS | 0 | 4 | 全部骨架 |
| HMI | ArkTS | 0 | 4 | 全部骨架 |

---

## 二、Python 感知模块（perception）

### 现状

- 位置：`code/perception/`
- 源文件：8 个 Python 文件（FrameSource、YoloDetector、FaceMeshEstimator、FeatureFusion 等）
- 现有测试：`code/perception/tests/test_perception.py`（6 个 TestCase，约 30 个方法）
- 测试框架：Python stdlib `unittest`
- CI 覆盖：无

### 需补充

| 优先级 | 测试项 | 说明 |
|:------:|--------|------|
| 高 | gRPC server/client 集成测试 | 目前无端到端测试，感知模块以 gRPC sidecar 模式运行 |
| 高 | 多帧连续推理的一致性测试 | 验证 YOLO 检测 + FaceMesh 在多帧序列上的 ID 稳定性 |
| 中 | 极端输入测试 | 全黑/全白/噪声帧、0x0 尺寸、超大分辨率帧 |
| 中 | 性能回归测试 | 给定帧序列的推理耗时基准 |
| 低 | mock 模式切换测试 | 已有部分覆盖，可补充切换边界条件 |
| 低 | 模型文件缺失时的降级行为 | 验证模型文件不存在时的错误提示和容错 |

### CI 接入建议

```yaml
# .github/workflows/ci.yml 新增 job
perception:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/setup-python@v5
      with:
        python-version: '3.10'
    - run: pip install -r code/perception/requirements.txt
    - run: python -m pytest code/perception/tests/ -v --cov=code/perception/
```

### 前置条件

- 需要在 CI runner 上安装 OpenCV、ONNX Runtime 等 C++ 依赖
- YOLO/FaceMesh 模型文件（.onnx）需要作为 CI artifact 预加载或从对象存储下拉

---

## 三、ArkTS 前端模块（frontend + HMI）

### 现状

三个 HarmonyOS 子应用各有 4 个测试文件，全部为 DevEco Studio 自动生成的模板骨架：

```
code/frontend/entry/src/
  test/LocalUnit.test.ets          ← 空白骨架
  test/List.test.ets               ← 测试入口
  ohosTest/ets/test/List.test.ets  ← 设备测试入口
  ohosTest/ets/test/Ability.test.ets ← 空白骨架

code/frontend/front/entry/src/
  test/LocalUnit.test.ets          ← 同上
  test/List.test.ets
  ohosTest/ets/test/List.test.ets
  ohosTest/ets/test/Ability.test.ets

code/HMI/entry/src/
  test/LocalUnit.test.ets          ← 同上
  test/List.test.ets
  ohosTest/ets/test/List.test.ets
  ohosTest/ets/test/Ability.test.ets
```

**所有骨架文件仅包含一行 `assertContain('hello', 'hello world')` 的 trivial 断言，零业务逻辑覆盖。**

### 需补充

| 优先级 | 子应用 | 测试项 | 说明 |
|:------:|--------|--------|------|
| 高 | 全部 | 数据模型序列化/反序列化 | 验证 WebSocket JSON payload 的 parse 和 format |
| 高 | entry（家属APP） | 监护人关系管理逻辑 | 绑定/解绑、状态订阅、告警接收 |
| 高 | front（车队大屏） | 仪表盘数据聚合逻辑 | 告警统计、车辆状态汇总、疲劳分布 |
| 高 | HMI（车载） | 感知数据展示逻辑 | 生理信号、DMS 状态、干预提示 |
| 中 | 全部 | 网络异常处理 | WebSocket 断连重试、HTTP 超时、离线消息队列 |
| 中 | entry | 媒体会话状态机 | SparkRTC 创建/结束/续期的 UI 状态变迁 |
| 中 | front | 实时告警通知逻辑 | L3 告警弹窗、声音告警、告警已读/未读 |
| 低 | 全部 | 边界场景 | 空列表/空数据/超长文本/特殊字符 |
| 低 | HMI | 语音交互测试 | 路怒检测后语音播报触发逻辑 |

### 测试框架

ArkTS 使用 `@ohos/hypium`（HarmonyOS 原厂测试框架）。测试示例：

```typescript
import { describe, it, expect } from '@ohos/hypium';

describe('GuardianshipService', () => {
  it('should subscribe to driver status', () => {
    // arrange
    // act
    // expect(...).assertContain('...');
  });
});
```

### 前置条件

- 本地单元测试（`src/test/`）：无需设备，可在 DevEco Studio 中直接运行
- 设备测试（`src/ohosTest/`）：需要 HarmonyOS 真机或模拟器
- 当前 CI（`.github/workflows/ci.yml`）仅执行 `tsc --noEmit` 类型检查，无测试运行
- 要跑 ArkTS 测试需要 HarmonyOS SDK 环境（目前在 GitHub Actions 上不易配置）

---

## 四、Java 后端已跳过项（非缺口）

| 组件 | 跳过原因 |
|------|----------|
| `KeyStoreKeyManager` | 需要真实 PKCS12 keystore 文件 |
| `MqttClientManager` | 内联创建 Eclipse Paho MQTT 客户端 |
| `EdgeMqttClient` | 同上 |
| `MqttDeviceGateway`（IoTDA 路径） | 依赖华为 IoTDA 平台 |
| `WebSocketConfig` | Spring 配置注册类，无业务逻辑 |

---

## 五、建议执行顺序

| 阶段 | 模块 | 工作量 | 前置条件 |
|:----:|------|:------:|----------|
| 1 | Python perception 补充 | 1-2 天 | 本地 Python 环境 |
| 2 | Python perception CI 接入 | 半天 | CI runner 安装 OpenCV |
| 3 | ArkTS 数据模型测试 | 1 天 | DevEco Studio |
| 4 | ArkTS 业务逻辑测试 | 2-3 天 | DevEco Studio + 模拟器 |
| 5 | ArkTS 设备集成测试 | 1 天 | HarmonyOS 真机 |

---

## 六、相关文件

| 文件 | 用途 |
|------|------|
| `docs/server_unit_test_report.md` | Java 后端测试覆盖报告 |
| `docs/kingbase_integration_test_report.md` | 金仓集成测试专项报告 |
| `code/perception/tests/test_perception.py` | Python 感知模块现有测试 |
| `.github/workflows/ci.yml` | GitHub Actions CI 配置 |
