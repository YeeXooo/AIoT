# 金仓数据库环境配置

## 镜像构建

基于 `KingbaseES_V009R001C010B0004_Lin64_install.iso` 制作。

```bash
# 构建（≈8分钟，镜像约3-4GB）
docker build -t kingbase:V9 /tmp/kingbase_docker/
```

构建文件位置：`/tmp/kingbase_docker/`
- `Dockerfile` — 基于 ubuntu:22.04，静默安装 KingbaseES V9
- `silent.cfg` — 静默安装配置（安装路径 `/opt/Kingbase/ES/V9`，组件：Server + Interface）
- `entrypoint.sh` — 容器入口（initdb 初始化 + kingbase 前台运行）

## 启动容器

```bash
docker run -d --name kingbase -p 54321:54321 -v kingbase_data:/var/lib/kingbase/data kingbase:V9
```

## 连接信息

| 项目 | 值 |
|------|-----|
| 主机 | localhost |
| 端口 | 54321 |
| 数据库 | aiot |
| 用户 | kingbase |
| 密码 | kingbase123 |
| JDBC 驱动 | org.postgresql.Driver（金仓兼容 PostgreSQL 协议） |
| JPA 方言 | org.hibernate.dialect.PostgreSQLDialect |

## 项目配置

- `application.yml` — 默认激活 `dev` profile
- `application-dev.yml` — 金仓连接配置
- `application-ci.yml` — CI 环境，保留 H2 内存库

## 常用命令

```bash
# 查看容器状态
docker ps | grep kingbase

# 查看日志
docker logs kingbase

# 进入容器操作
docker exec -it kingbase bash

# 创建新数据库
docker exec kingbase createdb -U kingbase <dbname>

# 状态检查
docker exec kingbase sys_ctl -D /var/lib/kingbase/data status
```

## Spring Boot 启动验证结果

- ✅ HikariCP 连接池正常
- ✅ Flyway V1~V3 迁移全部执行成功
- ⚠️ `SystemAccountRepository` 缺少 JPA 实现（AccountController 注入失败，需补充 `infra.repository` 下的 Bean）
