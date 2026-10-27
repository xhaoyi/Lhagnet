# 风车 数据迁移工具

把「聊天记录」和「记忆」从一台设备搬到另一台设备 / 另一台电脑。

---

## 快速开始

### 情况一：手机已 root（最省事）

在**旧手机**上：
```powershell
cd D:\A5\Lhagnet\tools
.\Export-LhagnetData.ps1
```

在**新手机**上（先 USB 连接）：
```powershell
.\Import-LhagnetData.ps1
```

### 情况二：手机没 root，但 App 是 debug 构建

同上，两条命令一样可用（debuggable 应用 adb 可以直接读写自己的 `/data/data`）。

### 情况三：手机没 root，App 是 release 构建 —— 用 App 自带的导出/导入

这是**普通用户唯一可行的路径**，不需要任何工具：

**旧手机导出：**
1. 打开风车 → 「记忆」页 → 「导出备份」
2. 会弹窗显示保存路径，类似：
   ```
   /sdcard/Android/data/io.lhagnet.app/files/lhagnet-backup-20261027-101500.json
   ```
3. 用数据线 / 网盘 / 微信传把这个 `.json` 拷到电脑，放进 `D:\A5\Lhagnet\data\`

**新手机导入：**
1. 把那个 `.json` 的**全部内容**复制到剪贴板
2. 新手机打开风车 → 「记忆」页 → 「导入备份」→ 粘贴 → 「导入」
3. 顶部提示「导入了 N 条内容」即成功

> ⚠️ 导出内容 = 记忆 + 技能。**聊天记录不在旧版导出里**（聊天记录只在设备本地的 `files/chat/chat.json`）。
> 想要聊天记录一起搬，必须用情况一 / 情况二，或者让开发者在 App 里补上聊天记录的导出。

---

## 数据存在哪

设备上的真实路径（包名 `io.lhagnet.app`）：

| 内容 | 设备路径 | 作用 |
|---|---|---|
| **聊天记录** | `/data/data/io.lhagnet.app/files/chat/chat.json` | 一行一条 `{role, text, ts}`，最多 300 条 |
| **记忆** | `/data/data/io.lhagnet.app/files/memory/memory.json` | `{"tasks":[...], "sites":[...]}`，各最多 200 条 |
| 技能 | `/data/data/io.lhagnet.app/files/skills/skills.json` | 自建 + 内置技能 |
| 配置 | `/data/data/io.lhagnet.app/shared_prefs/lhagnet_prefs.xml` | API Key / 后端地址 / 账号 |

> 这些目录都是**应用私有目录**，别的 App 读不到；`allowBackup=false` 也挡住了 ADB 备份提取。

---

## 工具文件

| 文件 | 作用 |
|---|---|
| `Export-LhagnetData.ps1` | 从设备拉数据 → 生成 `lhagnet-data-<时间戳>.json` |
| `Import-LhagnetData.ps1` | 把 `lhagnet-data-*.json` 推回设备 |

两个脚本都用 **adb**（Android Platform Tools）。找不到时会自动去这几个位置找：

```
%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
%ANDROID_HOME%\platform-tools\adb.exe
%ANDROID_SDK_ROOT%\platform-tools\adb.exe
D:\A1\build-tools\android-sdk\platform-tools\adb.exe
C:\Program Files (x86)\TC综合开发工具\android\adb.exe
```

也可以手动指定：`.\Export-LhagnetData.ps1 -Adb "D:\path\to\adb.exe"`

---

## 常用参数

```powershell
# 指定设备（连了多台时）
.\Export-LhagnetData.ps1 -Serial R5CT30XXXXX

# 指定输出目录（默认是 scripts 所在目录）
.\Export-LhagnetData.ps1 -OutDir D:\A5\Lhagnet\data

# 导入指定文件（默认取最新的 lhagnet-data-*.json）
.\Import-LhagnetData.ps1 -File D:\A5\Lhagnet\data\lhagnet-data-20261027-101500.json
```

导入脚本会**自动备份**设备上原有的同名文件（变成 `*.bak-时间戳`），导错了可以改回去。

---

## 常见问题

**Q: 提示「读不到 /data/data/...」**
A: 没 root，且 App 是 release 构建。走「情况三」用 App 自带的导出/导入。

**Q: 提示 Permission denied**
A: 同上。release 构建的 App 只有 root 能读写它的私有目录。

**Q: 导入后看不到数据**
A: 脚本已经自动 `am force-stop` 了 App，手动重新打开一次即可。

**Q: 聊天记录能一起搬吗？**
A: 情况一 / 情况二可以。情况三（旧版 App 内导出）**不含聊天记录**。

**Q: 会覆盖我新手机上的数据吗？**
A: 会覆盖同名文件，但原文件已被备份成 `*.bak-时间戳`，在设备上用文件管理器（需 root）可以找回。

---

## ⚠️ 隐私提醒

导出的 `.json` 里**含你的任务目标、结论摘要、站点经验、聊天内容**。
任务目标里可能有手机号、地址、订单号。

- 别把它传到公开的地方
- GitHub 仓库的 `.gitignore` 已经排除了 `lhagnet-backup-*.json` 和 `*backup*.json`，但 `lhagnet-data-*.json` **没被排除** —— 别把 `data/` 目录里的文件直接 commit 上去
- 建议存放位置：`D:\A5\Lhagnet\data\`（本机，不上传）
