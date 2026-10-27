# 风车 · Android 个人自动化 AI 助理

> 运行在安卓手机上的本地 + 云端协同 AI 助手，基于自然语言理解、界面识别、自动化操作能力，**代替人操作手机内各类 App**，用户只用提需求，AI 自动完成整套流程。

> 📦 本仓库是**纯 UI 层源码备份**（界面层），不是完整可编译工程。缺少 `Tools/Agent`、`LhA11y` 等业务类，以及 `Prefs`、`Net`、`LlmClient` 等基础设施类。完整的可编译工程与全量备份**不在本仓库内**。

## 📦 下载

最新版本：[**风车 v1.0.0**](https://github.com/xhaoyi/Lhagnet/releases/latest)

- APK 直接下载：<https://github.com/xhaoyi/Lhagnet/releases/download/v1.0.0/fengche-v1.0.0.apk>
- SHA-256：`2F9C474F126C0D1DAEE9183EB9B55BF119873532921EB184ED71D1F107C00C58`
- 安装步骤与系统要求见 [release/NOTES-v1.0.0.md](release/NOTES-v1.0.0.md)

## 核心能力清单

✅ **生活消费**：外卖筛选、选品、下单、提交订单；电商平台商品检索、多平台比价、筛选优惠、下单
✅ **出行服务**：自动规划导航、实时路线查询；火车票、机票查询、选班次、购票、改签；酒店查询、筛选条件、预订
✅ **扩展能力**：支持更多安卓场景自动化（查快递、挂号、查话费、打卡、填表等），可持续新增自动化任务模板
✅ **交互方式**：对话、指令，支持多轮沟通确认偏好（预算、时间、品牌、房型等）

## 关键特性

1. **安全可控**：所有高风险动作（支付、确认订单、提交身份证信息）**必须人工二次确认**，AI 不会私自付款、提交敏感信息；支持操作日志回看，随时终止自动化流程
2. **偏好记忆**：记住你的常用地址、口味偏好、预算区间、出行偏好，不用每次重复输入
3. **异常兜底**：页面加载失败、验证码、弹窗拦截、登录失效时，自动暂停并通知用户接手处理
4. **权限边界**：依托安卓无障碍 / 辅助服务实现界面识别与自动点击，清晰告知所需权限，不窃取隐私；支持本地大模型，部分指令离线理解
5. **任务可编排**：支持链式任务，例如：“帮我买明天下午高铁票，订目的地附近 300 以内的酒店”，AI 串行完成整套流程

## 使用流程

1. 用户发指令 → AI 理解需求，询问缺失信息
2. AI 打开对应 App，自动检索、筛选、对比信息
3. 预览结果给用户确认
4. 用户同意后执行下单 / 预订；涉及支付、证件提交强制等待用户确认
5. 任务完成，推送结果通知

## 目录

```
AndroidManifest.xml        界面入口：MainActivity / Island(悬浮球) / 各 Receiver / 主题
res/layout/activity_main.xml   全部页面都在这一个布局里（登录/注册/聊天/技能/记忆/设置/用户/任务/语言/字体 + 抽屉 + 灵动岛容器）
res/drawable/*.xml             13 个矢量图标（聊天/技能/记忆/设置/任务/语言/字体/用户/反馈/发送/停止/清空/桌面图标）
res/drawable-nodpi/app_logo.png  应用 logo（风车）
res/values/strings.xml         文案
res/xml/accessibility_service_config.xml   无障碍服务声明（能力清单）
res/xml/network_security_config.xml        网络安全策略（只允许 HTTPS）
java/MainActivity.java         界面代码主体（约 23 万字节：按页构建视图、主题刷新、抽屉、各种弹窗）★ 里面也混着业务逻辑，只当界面参考
java/Palette.java              明暗两套配色（白/深色主题的取色都在这里）
java/Island.java               灵动岛（顶部胶囊、进度气泡）
java/Confirm.java              悬浮确认框（下单前问用户那种）
java/ChatStore.java            聊天记录的数据结构（界面按它渲染）
java/Lang.java                 多语言文案表
java/Images.java               图片工具（头像/缩略图）
java/Bus.java                  界面事件总线（日志行、状态变化）
```

## 界面结构速查（activity_main.xml）

- 外层 `FrameLayout`
  - `loginLayer`：整屏登录页（品牌 + 手机号 + 密码 + 协议勾选 + 登录/注册/忘记密码）
  - `registerLayer`：整屏注册页（手机号 + 验证码 + 密码 ×2 + 用户名 + 两个找回密码问题）
  - 主界面 `LinearLayout`
    - 顶栏：`☰` 菜单按钮、`● 手` `● 眼` `● 模` 三个能力灯、标题
    - 页面容器：`pageChat` `pageSkills` `pageMemory` `pageConfig` `pageUser` `pageSched` `pageLang` `pageFont`
    - 底部输入区：`＋`（拍照/图片/文件）、输入框、运行/发送
  - `drawerLayer`：左侧抽屉（风车Beta 标题 + 聊天/技能/记忆/语言/字体/任务/设置/反馈/清空聊天/用户）
  - 灵动岛容器（`Island`）

## 主题与配色（Palette.java）

- 两套：浅色（白底 #FFFFFF / 卡片 #F5F6F8 / 主色 #2563EB）与深色（#0B0F14 底 / #141A22 卡片 / #7C9CFF 主色）
- `Prefs.resolveDark()` 决定用哪套：跟随系统 / 浅色 / 深色
- 卡片外观统一在 `MainActivity.card(int id)`：圆角 16dp + 1dp 描边 + 10dp 下边距（改皮肤从这里入手）

## 安全说明（发布前请务必阅读）

本项目涉及无障碍服务、悬浮窗、屏幕截图等高敏感权限，公开代码前已做过一轮脱敏处理。
如果你 fork 或二次分发，请保持以下约束：

### 1. 调试入口只在 debug 构建可用

`MainActivity` 是 `exported="true"`（带 LAUNCHER），**任何第三方 App 都能直接 `startActivity` 进来**。
因此所有有副作用的 Intent 参数（`cfgApiKey` / `cfgBaseUrl` / `start` / `tapXY` / `analyze` /
`exportBackup` / `importBackup` / `clearSkills` / `selftest` 等）都被收进了
`handleDebugExtras()`，并由 `isDebugBuild()` 门禁。

- **release 构建**：这些参数被完全忽略，第三方 App 无法借此改写你的 API Key、
  篡改后端地址、触发任务、读取屏幕内容或模拟点击。
- **debug 构建**：仍可通过 adb 驱动，例如：

  ```bash
  adb shell am start -n io.lhagnet.app/.MainActivity --es cfgApiKey "sk-xxx"
  adb shell am start -n io.lhagnet.app/.MainActivity --es goal "打开美团搜炒饭"
  adb shell am start -n io.lhagnet.app/.MainActivity --es tapXY "540,1200"
  adb shell am start -n io.lhagnet.app/.MainActivity --es analyze --ei analyzeCount 3
  ```

`isDebugBuild()` 优先反射读取 `BuildConfig.DEBUG`；读不到时退回 `android:debuggable` 应用标记；
再读不到则**默认为 false**（安全默认值）。

### 2. 只走 HTTPS

`AndroidManifest.xml` 已设置 `android:usesCleartextTraffic="false"` 并引入
`res/xml/network_security_config.xml`，全站禁止明文 HTTP。账号密码、手机号、会话 id
都依赖这条链路。需要在本地连自建服务器时，请参考 `network_security_config.xml` 里的注释，
**只放行你信任的具体 IP**，不要用泛匹配。

### 3. 日志放在应用私有目录

运行日志（`lhagnet.log` / `trace.log`）写入 `getFilesDir()/logs/`，**不写入外部存储**。
日志里可能含手机号、任务目标、模型返回内容，属于个人信息；放在外部存储时任何有存储
权限的 App 都能读到。

### 4. 权限已收敛

- 已移除 `QUERY_ALL_PACKAGES`（Play 政策敏感权限，且本应用只需访问用户指定的少数 App，
  应当改用 `<queries>` 白名单）
- 无障碍服务 `LhA11y` 的 `exported="true"` 是系统绑定所必需，安全性由
  `android:permission="BIND_ACCESSIBILITY_SERVICE"` 保证 —— 只有系统能绑定，第三方 App 无法调用
- 其余 Service / Receiver 均为 `exported="false"`
- `android:allowBackup="false"` 防止通过 ADB 备份提取应用数据

### 5. 本仓库不包含

- 任何真实 API Key / Token / 私钥（全部为运行时输入）
- 后端服务器地址与 `Prefs.hashPwd()` 实现（属于非 UI 部分）
- 真实用户手机号、用户名、邮箱

## 改哪里

| 想改什么 | 改哪里 |
|---|---|
| 配色 / 明暗主题 | `java/Palette.java` + `MainActivity.applyTheme()` |
| 卡片圆角、描边 | `MainActivity.card(int id)` |
| 页面结构 | `res/layout/activity_main.xml` |
| 聊天气泡样式 | `MainActivity.ChatAdapter.getView()` |
| 抽屉菜单项 | `activity_main.xml` 的 `drawerLayer` + `MainActivity.setupTabs()` |
| 多语言文案 | `java/Lang.java` |

## 许可

见 [LICENSE](LICENSE)。本备份仅供学习与参考使用。
