# Git Push 失败排查（GitHub HTTPS 被墙）

> 适用于 `TLS connect error` / `unexpected eof while reading` 等 HTTPS 连接问题

## 症状

```bash
$ git push
fatal: unable to access 'https://github.com/...': TLS connect error: error:0A000126:SSL routines::unexpected eof while reading
```

## 原因

中国大陆网络环境下，GitHub HTTPS (443) 偶发被干扰，TLS 握手断开。

## 解决方案：切 SSH 走 443 端口

### 1. 生成 SSH 密钥（如果还没有）

```bash
ssh-keygen -t ed25519 -f ~/.ssh/github -N ''
cat ~/.ssh/github.pub
```

### 2. 添加公钥到 GitHub

1. 复制上一步输出的整行 `ssh-ed25519 AAAAC3NzaC...`
2. 打开 https://github.com/settings/ssh/new
3. Title 随便填，Key 粘贴，点 Add SSH Key

### 3. 验证连接

```bash
ssh -i ~/.ssh/github -T -p 443 git@ssh.github.com
# 应输出: Hi xxx! You've successfully authenticated...
```

### 4. 切换 remote 并配置 Git 用这个 key

```bash
git remote set-url origin ssh://git@ssh.github.com:443/deardeerground/aigc_deerlu.git
git config --local core.sshCommand "ssh -i ~/.ssh/github -p 443"
git push
```

> 之后该仓库 push/pull 都会自动走 SSH，不需要再单独配置。
