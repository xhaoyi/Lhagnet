# Security Policy

## Supported Versions

本仓库是**纯 UI 层备份**，不构成可发布的完整应用。安全修复针对最新的 UI 层代码。

| 版本 | 状态 |
|---|---|
| main 分支 | ✅ 接受安全修复 |

## 报告漏洞

如果发现安全问题，请**不要**直接开公开 Issue。

请通过 GitHub 仓库的 **Security → Report a vulnerability**（私密报告）提交，或邮件联系维护者。
报告中请尽量包含：

- 受影响的文件与行号
- 复现步骤 / PoC
- 影响范围与严重程度

我们会在 7 个工作日内给出初步响应。

## 本项目固有的高敏感点

风车使用无障碍服务、悬浮窗与屏幕截图能力，天然属于高权限应用。以下约束**必须保持**，
否则会引入可被第三方利用的漏洞：

### 1. `MainActivity` 的调试入口必须保持门禁

`MainActivity` 是 `exported="true"`（LAUNCHER 入口），任何第三方 App 都能直接
`startActivity` 启动它。因此：

- ❌ **禁止**在 `handleIntentExtras()` 中直接接受 `cfgApiKey` / `cfgBaseUrl` /
  `cfgProvider` / `start` / `stop` / `tapXY` / `analyze` / `exportBackup` /
  `importBackup` / `clearSkills` / `selftest` 等有副作用的参数。
- ✅ 这些参数必须只经由 `handleDebugExtras()` 处理，并由 `isDebugBuild()` 门禁。
- ✅ release 构建下这些参数必须被**完全忽略**（不是"忽略错误"，是根本不执行）。

> **威胁模型**：恶意 App → `startActivity(io.lhagnet.app/.MainActivity)`
> → 改写 `cfgBaseUrl` / `cfgApiKey` → 用户的 App 变成向攻击者服务器转发
> 聊天记录与 API Key 的跳板；配合 `tapXY` + `analyze` 还能读屏并模拟点击
> （用户已把无障碍服务授权给了本 App）。

### 2. 只允许 HTTPS

- ✅ `android:usesCleartextTraffic="false"`
- ✅ `res/xml/network_security_config.xml` 中 `cleartextTrafficPermitted="false"`
- ❌ 不要为了让本地调试方便而全局放开明文；确需例外时只放行具体 IP，
  且仅限 debug 构建。

### 3. 日志不得写入外部存储

- ✅ 日志写入 `getFilesDir()/logs/`
- ❌ 不要改回 `getExternalFilesDir("logs")` —— 外部存储上的文件任何有存储权限的 App 都能读，
  而日志含手机号、任务目标、模型返回内容。

### 4. 权限收敛

- ✅ 不申请 `QUERY_ALL_PACKAGES`（Play 政策敏感权限）
- ✅ `LhA11y` 保留 `android:permission="BIND_ACCESSIBILITY_SERVICE"`
- ✅ 其余组件 `exported="false"`
- ✅ `android:allowBackup="false"`

### 5. 凭证与个人信息

- ❌ 不要把真实 API Key、Token、服务器地址、密码哈希实现提交进仓库
- ❌ 不要在日志或 `Bus.emit()` 里输出未脱敏的手机号（用 `maskPhone()`）
- ⚠️ IMEI / MEID 属敏感个人信息，若要采集必须**单独取得同意**，不能打包进通用同意项
- ⚠️ 「反馈 → 附上操作日志」会向服务器上传运行日志，隐私政策必须**明确披露**该行为

## 依赖说明

本 UI 层**不引入第三方依赖**，全部使用 Android 原生 API 与 `org.json`，因此没有
第三方库的供应链风险。
