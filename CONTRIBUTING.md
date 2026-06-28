# 贡献指南

> 面向从未使用过 Git/GitHub 的团队成员，从零到成功提 PR。

---

## 一、一次性准备

### 1.1 安装 Git

Linux/WSL 自带。Windows 去 [git-scm.com](https://git-scm.com) 下载。

```bash
git --version   # 确认安装成功
```

### 1.2 注册 GitHub 账号并 Fork 仓库

去 [github.com](https://github.com) 注册账号。

打开 [YeeXooo/AIoT](https://github.com/YeeXooo/AIoT)，点击右上角 **Fork** → Create fork，把仓库复制到你自己的账号下。

### 1.3 配置身份

```bash
git config --global user.name "你的名字"
git config --global user.email "你的GitHub邮箱"
```

### 1.4 配置 SSH Key（避免每次输密码）

```bash
ssh-keygen -t ed25519 -C "你的GitHub邮箱"   # 一路回车
cat ~/.ssh/id_ed25519.pub                    # 复制输出内容
```

打开 GitHub → Settings → SSH and GPG keys → New SSH key，粘贴进去。

---

## 二、工作流程

### 分支规范

```
main ────── 设计文档（冻结，受保护）
  └── develop ────── 开发主干（所有人的代码合到这里）
        └── feat/xxx ── 功能分支
        └── fix/xxx  ── 修复分支
```

**永远不要直接向 `main` 或 `develop` 提交代码。**

### 标准流程（Issue → Branch → Commit → PR → Merge）

#### Step 0：创建 Issue

在 GitHub 仓库 Issues 页新建 Issue，描述你要做什么。**一个 PR 对应一个 Issue，没有 Issue 不开工。**

#### Step 1：克隆你的 Fork

```bash
git clone git@github.com:你的用户名/AIoT.git   # 注意是你自己的地址
cd AIoT
```

#### Step 2：添加上游仓库（保持同步）

```bash
git remote add upstream git@github.com:YeeXooo/AIoT.git
git remote -v      # 确认：origin 指向你的 fork，upstream 指向主仓库
```

#### Step 3：从 upstream develop 切出功能分支

```bash
git fetch upstream                          # 拉取上游最新
git checkout -b feat/xxx upstream/develop   # 基于上游 develop 创建分支
```

> 分支名建议带 Issue 号，如 `feat/42-trip-aggregate`。

#### Step 4：写代码，提交

```bash
git status                        # 查看改了哪些文件
git add 文件路径                   # 加入暂存区
git commit -m "feat: 实现 AR-01 Trip 聚合根"
```

**提交信息前缀**：
| 前缀 | 用途 |
|------|------|
| `feat:` | 新功能 |
| `fix:` | 修复 bug |
| `docs:` | 文档变更 |
| `refactor:` | 重构 |
| `chore:` | 杂务 |
| `test:` | 测试 |

#### Step 5：推送并创建 PR

```bash
git push -u origin feat/xxx       # 推送到你自己的 fork
```

终端会输出一个创建 PR 的链接，点开填写。或去 GitHub 你 fork 的页面，点 "Compare & pull request"。

- **base repository**：`YeeXooo/AIoT`，**base**：`develop`
- **head repository**：你 fork 的仓库，**compare**：`feat/xxx`
- **关联 Issue**：`Closes #42`（必填，合入后自动关闭对应 Issue）
- **关联设计文档**：如 `docs/ood_domain.md §3.1 AR-01`（必填）

#### Step 6：等待 Review

- 上游管理员 Review 通过后在 GitHub 上点 Merge
- 如需修改：本地改 → `git add` → `git commit` → `git push`，PR 自动更新
- 合入后可删除本地功能分支：`git branch -d feat/xxx`

#### Step 7：同步上游并开始下一个任务

```bash
git fetch upstream                     # 拉取上游最新（含已合入的 PR）
git checkout upstream/develop          # 或基于最新 develop 切新分支
git checkout -b feat/下一个功能
```

---

## 三、常见操作速查

| 场景 | 命令 |
|------|------|
| 放弃未提交的改动 | `git restore 文件名` |
| 撤销 git add | `git restore --staged 文件名` |
| 查看提交历史 | `git log --oneline -10` |
| 查看当前分支 | `git branch` |
| 查看改动内容 | `git diff` |
| 查看改动的文件列表 | `git status` |
| 临时保存当前改动 | `git stash` |
| 恢复临时保存的改动 | `git stash pop` |
| 放弃所有本地改动（⚠️ 不可逆） | `git checkout . && git clean -fd` |

---

## 四、项目特有约定

- 后端语言 **Java**，框架 Spring Boot，数据库金仓（兼容 PostgreSQL）
- 代码实现必须与 `docs/ood_domain.md` / `docs/ood_application.md` 中的契约一致
- PR 模板位于 `.github/pull_request_template.md`，创建 PR 时自动填入
- CI 流水线见 `.github/workflows/ci.yml`，PR 合并前必须通过
