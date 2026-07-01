# 活页夹后端服务部署教程（阿里云 · 零基础版）

本教程从头带你：买服务器 → 连上去 → 装环境 → 传代码 → 启动服务 → 手机 App 连上。

每一步都写了具体命令，直接复制粘贴即可。

---

## 第 1 步：已有阿里云 ECS 的确认事项

你已经有一台 ECS，确认以下信息：

- **公网 IP**：阿里云控制台 → ECS → 实例列表 → 公网 IP 那一列就是
- **登录方式**：密钥对（现在就在用）
- **用户名**：`ecs-user`

如果你还没买 ECS，买一台配置如下：
- 实例规格：2核4G（ecs.c7.large）
- 系统镜像：Ubuntu 22.04 或 Alibaba Cloud Linux 3
- 带宽：5 Mbps（按量）
- 登录凭证：选"密钥对"（下载 `.pem` 文件保存好）

---

## 第 2 步：配置安全组（开放端口）

服务器默认只开了 22 端口（SSH），需要手动开放 8000 端口给 App 用。

1. 打开阿里云控制台 → **云服务器 ECS** → **安全组**
2. 点你的实例对应的安全组 → **入方向** → **手动添加**
3. 添加一条规则：

| 授权策略 | 优先级 | 协议类型 | 端口范围 | 授权对象 | 描述 |
|---|---|---|---|---|---|
| 允许 | 1 | 自定义 TCP | 8000 | 0.0.0.0/0 | API端口 |

4. 点 **保存**

---

## 第 3 步：在 Windows 上打开终端连接服务器

### 3.1 用 PowerShell 连接

打开 PowerShell（Win + R → 输入 `powershell` → 回车）：

```powershell
ssh ecs-user@你的公网IP
```

> 如果是首次连接，会提示 `Are you sure you want to continue connecting (yes/no)?`，输入 `yes` 回车。

连上后你会看到类似 `ecs-user@iZ0jl57d8drk3hzlufd4irZ:~$` 的提示符。

### 3.2 （可选）用 VS Code 连接更便利

在 VS Code 里装插件 `Remote - SSH`，然后左下角点绿色图标 → Connect to Host → 输入 `ecs-user@你的公网IP`，之后可以在 VS Code 里直接操作服务器文件和终端。

---

## 第 4 步：安装 Docker

在服务器终端里（已 SSH 连上的那个窗口），逐条粘贴执行：

```bash
# 1. 用阿里云镜像安装 Docker（这会自动识别系统）
curl -fsSL https://get.docker.com | bash -s docker --mirror Aliyun

# 2. 配置国内镜像加速器（中国大陆必需，否则拉取 Docker Hub 镜像会超时）
sudo mkdir -p /etc/docker
sudo tee /etc/docker/daemon.json <<'EOF'
{
  "registry-mirrors": ["https://docker.m.daocloud.io"]
}
EOF

# 3. 启动 Docker 并设为开机自启
sudo systemctl enable docker --now

# 4. 让 ecs-user 免 sudo 使用 docker
sudo usermod -aG docker $USER

# 5. 让上面的权限改动立即生效（或退出重新 SSH 一次也行）
newgrp docker

# 6. 验证安装
docker --version
# 输出示例: Docker version 29.6.1

docker compose version
# 输出示例: Docker Compose version v2.x.x
```

---

## 第 5 步：上传后端代码到服务器

### 5.1 在 Windows 上打开一个新的 PowerShell 窗口

保持服务器 SSH 窗口别关，新开一个 PowerShell：

```powershell
# 确认当前在项目根目录（应该能看到 server/ 文件夹）
Get-ChildItem
# 输出里应该有 server 目录

# 上传 server 目录到服务器的 /opt/huoyejia/
# 替换 "你的公网IP" 为实际 IP，替换密钥路径为你的 .pem 文件路径
scp -i "你的密钥文件路径.pem" -r server\ ecs-user@你的公网IP:/opt/huoyejia/
```

> 如果提示 `Permission denied`，先 SSH 到服务器确认 `/opt/` 目录有写入权限：
> ```bash
> # 在服务器上执行
> sudo mkdir -p /opt/huoyejia
> sudo chown ecs-user:ecs-user /opt/huoyejia
> ```

### 5.2 确认上传成功

切回服务器 SSH 窗口：

```bash
ls /opt/huoyejia/server/
# 应该看到 main.py, Dockerfile, docker-compose.yml 等文件
```

---

## 第 6 步：配置 LLM API 密钥

在服务器上创建并编辑配置文件：

```bash
cd /opt/huoyejia/server
cp .env.example .env
vim .env
```

**修改以下 5 行为你的真实密钥**（其他行不用动）：

```ini
# 数据库密码（随便设一个，比如 Huoyejia2026!）
DB_PASSWORD=Huoyejia2026!

# Chat 模型 — 用 DeepSeek 就填这个
LLM_CHAT_API_KEY=sk-你的DeepSeek密钥
# 如果要改用阿里百炼，把上一行的 BASE_URL 和 MODEL 换掉：
# LLM_CHAT_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
# LLM_CHAT_MODEL=qwen-plus

# Embedding 模型 — 用 OpenAI 就填这个
LLM_EMBEDDING_API_KEY=sk-你的OpenAI密钥
```

> 不会用 vim？用 `nano .env` 替代，编辑完 `Ctrl+O` 保存，`Ctrl+X` 退出。

**最小可运行配置**：只需要 Chat 和 Embedding 两组密钥即可，图片和视频留空不影响核心功能。

---

## 第 7 步：启动服务

先把 Docker 服务跑起来（如果未启动的话），然后构建启动容器：

```bash
# 确保 Docker 守护进程在运行（新开 SSH 窗口时先执行这一步）
sudo systemctl start docker

# 进入项目目录
cd /opt/huoyejia/server

# 构建并启动（首次需下载镜像，约 3-5 分钟）
docker compose up -d --build
```

看到类似输出表示成功：
```
[+] Running 3/3
 ✔ Network server_default    Created
 ✔ Container huoyejia-db     Started
 ✔ Container huoyejia-api    Started
```

查看是否在运行：

```bash
docker compose ps
# 应看到两个容器都是 Up 状态，db 后面还有 (healthy)
```

---

## 第 8 步：验证服务是否正常

```bash
# 在服务器上自检
curl http://localhost:8000/api/health
```

应该返回：
```json
{"status":"ok","service":"huoyejia"}
```

### 从外部（你的 Windows）验证

打开浏览器访问：
```
http://你的公网IP:8000/api/health
```

> 如果浏览器打不开，检查第 2 步的安全组是否添加了 8000 端口规则。

### 测试 AI 接口

```bash
# 测试嵌入（转向量），确认 LLM 密钥配置正确
curl -s -X POST http://localhost:8000/api/embed \
  -H "Content-Type: application/json" \
  -d '{"text":"测试"}' | python3 -m json.tool
```

成功的话会返回一个 1536 维或 4096 维的向量数组。

---

## 第 9 步：让 Android App 连上服务器

在你的 Windows 开发机上，打开项目中的 `local.properties`，改一行：

```properties
SERVER_BASE_URL=http://你的公网IP:8000
```

然后在 Android Studio 里重新 Build APK，安装到手机上。

> **如果暂时不填真实 IP**：保持 `http://YOUR_SERVER_IP:8000`，App 会自动降级到原来的直连 LLM API 方式运行，不影响使用。

---

## 第 10 步：查看 Swagger 文档（可选）

浏览器打开：
```
http://你的公网IP:8000/docs
```

可以直接在网页上测试所有 API 接口。

---

## 维护常用命令

```bash
# 回到项目目录
cd /opt/huoyejia/server

# 查看 API 日志（实时滚动）
docker compose logs -f api

# 查看最近 50 行日志
docker compose logs api --tail=50

# 修改配置后重启 API（数据库不会重启）
docker compose restart api

# 代码更新后重新构建
git pull
docker compose up -d --build api

# 全部停止
docker compose down

# 全部启动
docker compose up -d

# 数据库备份
mkdir -p ~/backups
docker exec huoyejia-db pg_dump -U huoyejia huoyejia > ~/backups/huoyejia_$(date +%Y%m%d_%H%M).sql
```

---

## 常见问题

**Q: 安全组加了端口但还是访问不了？**

检查阿里云控制台安全组页面，确认规则已保存且绑定到了正确的实例。另外有些新账号默认有"网络 ACL"额外的防火墙，也检查一下。

**Q: `docker compose up` 报错端口被占用？**

```bash
# 查看谁占用了 8000 端口
sudo ss -tlnp | grep 8000
# 先停掉占用的进程，或改 server/.env 里的 SERVER_PORT 为其他值
```

**Q: Docker Hub 镜像拉取失败 / 超时 / unexpected EOF？**

中国大陆直连 Docker Hub 不稳定。使用 DaoCloud 镜像加速器手动拉取：

```bash
# 如果 daemon.json 里的镜像加速器也回源超时，用 DaoCloud 直拉
docker pull docker.m.daocloud.io/pgvector/pgvector:pg16
docker tag docker.m.daocloud.io/pgvector/pgvector:pg16 pgvector/pgvector:pg16
```

> 如果 DaoCloud 也抽风，最终方案：在能科学上网的 Windows 上用 Docker Desktop 拉取后 `docker save` → `scp` 上传 → `docker load` 到服务器。

**Q: API 容器反复重启（Restarting）？**

```bash
# 查看错误日志
docker compose logs api --tail=50
```

常见原因及解决：
- `password authentication failed` → `.env` 里 `DB_PASSWORD` 与已创建数据库不一致 → 运行 `docker compose down -v && docker compose up -d --build` 重置数据卷
- `ImportError: cannot import name 'cosine_distance'` → 服务器代码版本旧 → 重新 `scp` 上传最新代码并 `docker compose up -d --build`
- `type "vector" does not exist` → 同上，重新上传最新代码再构建

**Q: 想给 API 加 HTTPS？**

在阿里云上申请免费 SSL 证书，然后用 Nginx 做反向代理，外面走 443，内网转发到 8000。这是可选的高级配置，现在不配也能正常用。

**Q: 数据库需要手动建表吗？**

不需要。FastAPI 启动时会自动建表（`init_db()` 在 `main.py` 里），Docker 启动后自动完成。
