# FoldLedger 界面与布局重构

## Background / purpose

原界面主要由裸文本、分割线和默认 Material 组件构成，信息层级弱；Fold 展开态仍使用
底部五栏导航，未充分利用横向空间。本次在不改动 ViewModel、导航状态、数据库和业务行为
的前提下，重构 FoldLedger 的视觉体系与主要页面布局。

## Files changed

- `products/ledger/presentation/theme/Theme.kt`
  - 重建明暗色板、字体层级和圆角体系。
- `products/ledger/presentation/common/LedgerDesign.kt`
  - 新增页面标题、内容卡片、指标卡和空状态组件。
- `products/ledger/presentation/FoldLedgerApp.kt`
  - 新增手机底部导航与 Fold/平板侧边导航轨。
- `products/ledger/presentation/nav/TopDest.kt`
  - 使用支持 RTL 的流水图标。
- `products/ledger/presentation/ledger/LedgerScreen.kt`
  - 重构总览、搜索筛选、交易卡片和 Fold 详情区域。
- `products/ledger/presentation/stats/StatsScreen.kt`
  - 重构时间筛选、支出指标、趋势与分类图表容器。
- `products/ledger/presentation/accounts/AccountsScreen.kt`
  - 将账户和智能分类改为可扫描的分层卡片。
- `products/ledger/presentation/budgets/BudgetsScreen.kt`
  - 重构预算进度卡和引导型空状态。
- `products/ledger/presentation/settings/SettingsScreen.kt`
  - 将长设置页拆成独立功能卡片，并补齐完整 JSON 备份恢复入口与导入确认。
- `products/ledger/presentation/onboarding/OnboardingScreen.kt`
  - 重做隐私说明、权限步骤和主次操作层级。
- `apps/ledger/src/main/AndroidManifest.xml`、`apps/ledger/src/main/res/`
  - 补齐可选电话硬件声明、数据提取规则和新品牌启动图标。
- `docs/brand/foldledger_logo_concept.png`
  - 保存由内置图像生成工具产出的 Logo 品牌概念稿，Android 端使用其简化矢量版本。

## Key logic changes

- 视觉方向调整为黑曜石、暖瓷白与少量群青，珊瑚色只用于支出/警示；深色模式使用连续
  黑色画布，避免大面积彩色卡片。
- 统一使用 18–30 dp 圆角和无阴影色块构建内容层级，减少分割线。
- COMPACT 宽度保留底部导航；MEDIUM/EXPANDED 宽度使用无外框的连续左侧导航轨。
- 只有 EXPANDED 宽度使用流水双栏；Fold 常见 MEDIUM 展开宽度改为单栏主从切换，
  避免列表和详情各自过窄。
- 根内容消费安全区 Insets，导航轨和页面标题不再侵入状态栏，也不再出现圆角轨道与方形
  页面拼接的左上角冲突。
- Tab 切换加入 320–420ms 的方向性感知位移、淡入和轻微缩放动画，同时保留访问过页面的
  Compose 状态。
- 新 Logo 由四块折叠平面和一条连续资金流构成；移除字母、钱包和柱状图等通用符号。
- 流水展开态继续采用列表/详情双栏，但详情区改为独立卡片；空详情也有明确容器。
- 统计页把总支出、每日趋势和分类构成拆成三个视觉区块，图表切换与说明就近放置。
- 设置和首次引导保留全部原操作，只改变信息组织和视觉表达。
- 设置页现在可直接选择完整 JSON 备份；恢复前明确展示合并策略，恢复时显示不可重复触发的
  进度弹窗，失败会保留当前数据并展示错误。

## API or behavior changes

- 没有领域 API、数据格式、数据库、权限或导航目标变化。
- 窗口宽度不为 COMPACT 时，主导航由底部栏改为左侧导航轨。
- 明暗模式均使用新的 FoldLedger 品牌色和排版。
- 首次引导的权限操作顺序保持不变，文案更明确地说明本机处理与只读识别边界。
- 新增 `next` 构建类型：applicationId 为 `com.foldledger.next`，显示名为
  `FoldLedger Next`，可与旧 App 并排安装并测试备份导入。
- 完整 JSON 恢复会按备份 ID 更新账户、分类和预算，并按流水指纹去重后合并；不会先清空
  当前账本。

## Risks and compatibility impact

- 业务状态和数据层未变化，现有账本数据兼容。
- 新版已以 `com.foldledger.next` 安装到 Samsung SM-F966U，与原
  `com.foldledger.debug` 并存；未卸载旧版、未清除旧版数据。
- 当前设备启用了较大系统字体，展开态仍可显示核心标题、导航、筛选和卡片；极端字体缩放下
  横向筛选项依赖已有水平滚动。
- 未引入外部字体或图片资源，不增加网络、APK 资源下载和授权风险。
- Room、偏好和内部文件明确排除云备份与设备迁移；跨设备时应使用设置页的显式导出功能。
- `next` 与旧 App 使用不同 Android 私有数据目录；备份需要在新 App 内手动导入。

## Test coverage added or updated

- 本次没有新增 Compose screenshot test；仓库目前没有 UI 测试基础设施。
- 保留并执行已有 FoldLedger 领域与数据单元测试，确认视觉重构未影响业务层。
- 使用独立 `next` applicationId 安装到真实 Fold 设备；系统当前停在 FoldLedger Next
  的无障碍授权确认页，未代替用户授予“完全控制”权限。
- 实际执行结果：
  - `:products:ledger:domain:test`：2 个测试通过。
  - `:products:ledger:data:testDebugUnitTest`：10 个测试通过。
  - `:apps:ledger:lintNext`：通过，`No issues found`。
  - `:apps:ledger:assembleDebug`：通过。
  - `:apps:ledger:assembleNext`：通过。

## How to verify manually

```bash
./gradlew :products:ledger:domain:test \
  :products:ledger:data:testDebugUnitTest \
  :products:ledger:presentation:compileDebugKotlin \
  :apps:ledger:assembleDebug

adb install -r apps/ledger/build/outputs/apk/debug/ledger-debug.apk

# 与旧调试版并排安装
./gradlew :apps:ledger:assembleNext
adb install --no-streaming -r apps/ledger/build/outputs/apk/next/ledger-next.apk
```

建议分别在折叠和展开状态检查：

1. 流水页搜索、分类横向滚动、手动记账和详情切换。
2. 统计页日期范围、柱状/折线图、横条/饼图及明细弹窗。
3. 账户分类编辑、预算新增和设置页的长内容滚动。
4. 系统浅色/深色模式以及字体大小 100%/150%。
5. 设置 → 备份与分类 → 恢复完整 JSON 备份，确认导入提示、合并结果和重复流水数量。
