# 迭代第 1 轮

1. **问题描述**：GuardianshipWebSocket 心跳检测逻辑存在时序缺陷，双定时器各自递增 pingMissedCount 且 handlePing 不重置计数器，导致正常连接约 40s 后断开
   - 所在位置：code/frontend/api/GuardianshipWebSocket.ts:253-271
   - 严重程度：严重
   - 改进建议：在 handlePing() 中添加 this.pingMissedCount = 0，移除冗余 pongTimeout 逻辑

2. **问题描述**：WindowStatusEntry 在 types.ts 和 guardianship.ts 中重复定义
   - 所在位置：code/frontend/model/types.ts:128-134 和 code/frontend/model/guardianship.ts:84-90
   - 严重程度：一般
   - 改进建议：统一至 types.ts，guardianship.ts 通过 import 引用

3. **问题描述**：SecondaryVerifyRequest.otp 始终可选，未体现条件必需关系
   - 所在位置：code/frontend/model/auth.ts:67
   - 严重程度：轻微
   - 改进建议：使用 discriminated union 或 JSDoc 明确条件必填语义

4. **问题描述**：TokenType 类型导出但未被使用
   - 所在位置：code/frontend/model/types.ts:118-119
   - 严重程度：轻微
   - 改进建议：移除该导出或让 ApiClient.ts 引用

5. **问题描述**：FleetWebSocket.startPingTimer() 创建空操作定时器
   - 所在位置：code/frontend/api/FleetWebSocket.ts:160-165
   - 严重程度：轻微
   - 改进建议：添加 clearPingTimer() 防护或直接移除

6. **问题描述**：API 客户端层缺少 barrel 导出文件
   - 所在位置：code/frontend/api/
   - 严重程度：轻微
   - 改进建议：创建 index.ts 统一导出
