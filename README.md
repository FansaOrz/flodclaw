# FoldSuite

单仓多 App 工程。当前产品：

| App | applicationId | 说明 |
|-----|---------------|------|
| **FoldClaw** | `com.foldclaw` | Z Fold 个人侧载 AI Agent |
| **FoldPods** | `com.foldpods` | AirPods Pro 伴侣（壳，能力待接入） |

跨产品只共享 `shared/*` 与 Gradle 约定；业务互不 compile 依赖。详见 [docs/multi-app.md](docs/multi-app.md)。

---

## FoldClaw

三星 Z Fold 上的**个人侧载手机原生 AI Agent**。用自然语言驱动本机操作：设闹钟、建日程、改系统设置、读界面点击、开应用关代理等。

云端 LLM 负责规划，手机负责感知、策略、执行与 UI。无 PC Gateway。

### 能力概览

| 类型 | 示例 |
|------|------|
| 系统 Intent | 设闹钟、建日历事件、打开应用、查天气、铃声模式 |
| 联网与音乐 | DuckDuckGo 搜索；网易云深链播放 |
| 无障碍闭环 | 读 UI 树、点击（含校验）、输入、滑动、返回/回桌面 |
| 只读情报 | 设备状态摘要、通知摘要（需通知使用权） |
| 个人记忆 | 记住/忘掉偏好，注入后续对话 |
| 语音闭环 | 百炼 ASR 录音识别 + 可开关的 TTS 播报（无需 Google 语音） |
| 随时唤起 | 可设为系统数字助理；快捷设置「FoldClaw 开麦」磁贴；唤起即录音 |
| 体验 | 执行悬窗、任务结束后回 App、历史对话恢复、快捷任务芯片 |

刻意不做：Wi‑Fi / 手电筒等控制台已有的琐碎开关。

### 模块结构（FoldClaw）

```
apps/foldclaw/                      入口与 DI
products/foldclaw/presentation/     Compose UI
products/foldclaw/agent/            编排器（observe → plan → policy → execute）
products/foldclaw/domain/           领域模型与工具契约
products/foldclaw/data/             LLM、Room 账本、Keystore、ASR
products/foldclaw/device/           无障碍、Intent、FGS、悬窗、通知监听、TTS
products/foldclaw/policy/           能力信封与审批
shared/core|platform                跨 App 工具与 Intent 契约
```

## 环境要求

- Android Studio / JDK 17
- Android SDK 36，minSdk 31
- 真机建议：Samsung One UI（已在 Z Fold 系列验证）
- 无障碍服务：通用自动化必须开启（FoldClaw）
- 语音识别：设置页配置百炼 API Key（与聊天共用）

## 快速开始

```bash
# 编译全部 App Debug 包
./gradlew assembleAllApps

# 或单独编译
./gradlew :apps:foldclaw:assembleDebug
./gradlew :apps:airpods:assembleDebug

# 安装 FoldClaw（覆盖安装，保留数据）
adb install -r apps/foldclaw/build/outputs/apk/debug/*.apk

# 安装 FoldPods 壳
adb install -r apps/airpods/build/outputs/apk/debug/*.apk
```

### FoldClaw 首次打开

1. 按引导开启 **FoldClaw 无障碍服务**
2. 在设置页填入百炼 API Key（存 Keystore，不进仓库）
3. （可选）开启通知使用权以使用通知摘要
4. （可选）设为默认数字助理，并在快捷设置添加「FoldClaw 开麦」
5. 对话、语音或点快捷芯片下发任务

## 常用指令示例

- `设置一个明天下午三点的闹钟`
- `帮我把系统字体调大一点`
- `帮我把系统设置成静音模式`
- `打开clash并关闭代理`
- `明天北京的天气怎么样`
- `北京鸟巢今天有没有演唱会`
- `用网易云播放陶喆的普通朋友`
- `记住我常住城市是北京`
- `摘要一下最近通知`

## 数据与重装兼容

Debug 包名为 `com.foldclaw.debug`。记忆/设置存在 App 私有目录，**卸载或清数据会清空**；且已关闭系统云备份。

- 日常更新：始终 `adb install -r …`（覆盖安装），**不要先 uninstall**
- 换签名/换包名等于新 App，数据不会自动带过来
- 设置页提供 **导出/导入备份**（JSON：个人记忆 + 模型设置；不含 API Key）
- 两个工程树只要 `applicationId` 与签名一致，覆盖安装可保留数据；否则请先导出再导入

## 安全说明

- 能力信封限制工具与包名；`open_app` 成功打开的应用可在本任务内继续操作
- 敏感控件（发送/支付/删除等）默认拦截
- 记忆仅在用户明确要求时写入，不会从屏幕/通知自动学习
- API Key 不落盘明文；`.gitignore` 已排除 `local.properties`、密钥与构建产物
- 个人侧载用途，请自行评估风险

## 文档

- 多 App 接入与跨 App 契约：`docs/multi-app.md`
- 设计与对抗审查：`fold-ai-agent-plan-review.md`
- 兼容矩阵：`docs/compat-matrix.md`

## 仓库

远程：`git@github.com:FansaOrz/flodclaw.git`（目录名可为 `ai_app` / `flodclaw`）
