# 前端联调配置

> 后端已部署在 `172.22.103.50:8080`（WSL 端口转发），前端只需改 3 处 URL。

## 一、后端信息

| 项目 | 值 |
|------|-----|
| 地址 | `http://172.22.103.50:8080` |
| WebSocket | `ws://172.22.103.50:8080` |
| API 前缀 | `/api/v1` |

## 二、前端需修改 3 处

| # | 文件 | 行 | 当前值 | 改为 |
|---|------|------|--------|------|
| 1 | `api/ApiClient.ts` | L62 | `'/api/v1'` | `'http://172.22.103.50:8080/api/v1'` |
| 2 | `api/GuardianshipWebSocket.ts` | L72 | `wss://api.example.com/ws/guardianship` | `ws://172.22.103.50:8080/ws/guardianship` |
| 3 | `api/FleetWebSocket.ts` | L63 | `wss://api.example.com/ws/fleet` | `ws://172.22.103.50:8080/ws/fleet` |

## 三、测试账号

| 账号 | 角色 | 登录 payload |
|------|------|-------------|
| 家属 | FAMILY | `{"authMethod":"PASSWORD","credential":"13900000001","secret":"pass123"}` |
| 管理员 | MANAGER | `{"authMethod":"PASSWORD","credential":"18800000001","secret":"pass123"}` |

## 四、验证

用 HarmonyOS 跑起前端后，先测登录：

```bash
# 或用 curl 快速验证后端可达
curl -X POST http://172.22.103.50:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"authMethod":"PASSWORD","credential":"13900000001","secret":"pass123"}'
```

应返回 `{"accessToken":"eyJ...","refreshToken":"eyJ...",...}`。

## 五、注意事项

- **开发阶段用 HTTP/WS**（不用 HTTPS/WSS），因此无需证书配置
- 后端每次启动后保持运行即可，前端连接时无需额外操作
- 如果 WSL 重启导致 IP 变化，需在 Windows 上重新执行端口转发
