# FoldClaw Alpha 兼容矩阵（初稿）

设备：Samsung Galaxy Z Fold 7  
范围：能力信封内 3 App / 约 12 任务；单任务 ≤8 步  
前提：已开启 FoldClaw 无障碍；亮屏解锁；真实 API（百炼）或 Fake LLM  

应用内入口：聊天页顶部「快捷任务」芯片（对应下表 ID）。  
运行期：任务会拉起前台服务通知；锁屏会取消当前 run（不自动重放）。

## 冻结 App

| App | 包名 |
|---|---|
| 系统设置 | `com.android.settings` / `com.samsung.android.settings` |
| 三星时钟 | `com.sec.android.app.clockpackage` |
| 三星日历 | `com.samsung.android.calendar` |

淘宝等 WebView 重度 App **不在**本阶段硬承诺内。

## 任务清单

### 设置（A11y 主路径）

| ID | 指令示例 | 期望工具链 | 成功判据 |
|---|---|---|---|
| S1 | 打开设置 | `open_app` | 前台进入设置 |
| S2 | 打开设置并看看当前界面 | `open_app` → `get_ui_tree` | Timeline 出现非空 UI 摘要 |
| S3 | 在设置里搜索（需模型点搜索框） | `open_app` → `get_ui_tree` → `tap_node`/`type_text` | 搜索框获得焦点或出现查询 UI |
| S4 | 返回上一页 | `go_back` | 离开当前子页或回设置首页 |

### 时钟（Intent + 观察）

| ID | 指令示例 | 期望工具链 | 成功判据 |
|---|---|---|---|
| C1 | 设置明天下午三点闹钟 | `set_alarm` | 系统时钟确认 UI 弹出 |
| C2 | 打开时钟 | `open_app` | 时钟前台 |
| C3 | 打开时钟并读界面 | `open_app` → `get_ui_tree` | 非空 UI 树 |
| C4 | 再次打开时钟检查 | UI 复查按钮 | 时钟打开 |

### 日历（Intent + 观察）

| ID | 指令示例 | 期望工具链 | 成功判据 |
|---|---|---|---|
| K1 | 明天下午建团队会议日程 | `create_calendar_event` | 日历新建页预填 |
| K2 | 打开日历 | `open_app` | 日历前台 |
| K3 | 打开日历并读界面 | `open_app` → `get_ui_tree` | 非空 UI 树 |
| K4 | 查询明天北京天气（辅助） | `get_weather` | 返回气温摘要 |

## 明确失败面

- WebView / Canvas / `FLAG_SECURE`：UI 树可能为空或不全
- 未开无障碍：`get_ui_tree` / `tap_node` / `type_text` 失败
- 敏感按钮文案（发送/支付/删除/卸载）：策略拒绝
- 白名单外 App：`tap_node` 拒绝

## 验收记录（请在真机填写）

| 任务 | Fake LLM | 百炼真机 | 备注 |
|---|---|---|---|
| S1 | | | |
| S2 | | | |
| S3 | | | |
| S4 | | | |
| C1 | | | |
| C2 | | | |
| C3 | | | |
| C4 | | | |
| K1 | | | |
| K2 | | | |
| K3 | | | |
| K4 | | | |
