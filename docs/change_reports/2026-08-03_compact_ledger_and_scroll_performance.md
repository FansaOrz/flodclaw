# 流水密度与滚动性能优化

## Background / purpose

用户反馈流水文字和各页面标题过大，上下滑动存在明显卡顿。真机检查发现设备系统字体缩放为
1.3 倍；原页面标题使用 24sp、流水默认正文使用 16sp，再叠加较大的图标、卡片内边距和
卡片间距，明显低于参考应用的信息密度。

同时，流水页自制滚动条持续读取 `LazyListState.layoutInfo`，滚动中会高频重组；主页面将所有
访问过的 Tab 一直保留在组合树中，导致隐藏页面继续收集 Flow、测量和响应状态变化。

## Files changed

- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/theme/Theme.kt`
  - 收紧全局标题、正文和标签字号。
- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/common/LedgerDesign.kt`
  - 缩小通用页面头部高度和标题层级。
- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/ledger/LedgerScreen.kt`
  - 重构紧凑流水行、日期标题和月度摘要。
  - 搜索与分类筛选默认折叠，由标题栏按钮展开。
  - 移除高频重组的自制滚动条。
- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/FoldLedgerApp.kt`
  - 用 `AnimatedContent + SaveableStateHolder` 取代所有已访问 Tab 常驻组合。
  - 收紧底部导航中央入口和标签。

## Key logic changes

- 页面标题从 24sp 级别降为 15sp `titleMedium`，头部垂直内边距从 18dp 降为 8dp；
  在测试机 1.3 倍系统字体下，通用页头由约 79dp 收紧至约 64dp。
- 流水主文字和金额使用 12sp `bodySmall`；分类图标从 42dp 降为 32dp，行内垂直边距
  从 12dp 降为 4dp。在测试机 1.3 倍系统字体下，单行高度约 55–56dp。
- 逐笔圆角卡片改为白底连续分组行，使用 0.5dp 轻分割线；减少圆角裁剪、外边距和无效空白，
  同屏可展示更多流水。
- 日期分组标题使用 12sp 正文小号样式，并减少垂直空白。
- 月度摘要改用 13sp 数字和 10dp 垂直内边距。
- 搜索框和分类 Chip 不再长期占据流水首屏；筛选结果状态仍由 ViewModel 保存。
- 删除 `LedgerScrollbar`：它原本在滚动时读取布局信息、计算估算总高度并更新 thumb，
  会让 Compose 在连续手势中频繁重组。
- 删除“访问过的页面全部常驻”的实现。现在切页期间只短暂组合进入/退出页面，结束后仅保留
  当前页面；`SaveableStateHolder` 保存滚动位置和可保存 UI 状态，ViewModel 状态保持不变。
- 页面切换动画缩短到 140–260ms，并将移动距离限制为页面宽度的 1/12–1/14，减少大面积
  透明层和缩放绘制。

## API or behavior changes

- 没有领域 API、数据库、备份格式或权限变化。
- 流水搜索与分类筛选入口从常驻控件改为标题栏搜索按钮。
- 页面切换后隐藏页面不再继续组合，但滚动位置和 ViewModel 数据状态继续保存。
- 自制快速拖动滚动条被移除，列表回归系统惯性滚动行为。

## Risks and compatibility impact

- 极大字体模式下仍尊重 Android 字体缩放，没有通过固定 dp 字号绕过无障碍设置。
- 自制滚动条移除后不能拖动 thumb 快速跳转；搜索入口和系统惯性滚动仍可用于长账本。
- `SaveableStateHolder` 只保存可保存的 Compose 状态；复杂业务状态由既有 ViewModel 持有。
- 真机性能对比受设备刷新率、系统省电模式和数据量影响，应使用相同页面与手势复测。

## Test coverage added or updated

- 本次没有新增业务单元测试；布局和组合生命周期变化由编译、lint 与真机滚动验证覆盖。
- 保留领域、数据和金额精度测试。
- 已执行并通过：
  - Domain：2 个测试。
  - Data：10 个测试。
  - Presentation：2 个测试。
  - `:apps:ledger:lintNext`：No issues found。
  - `:apps:ledger:assembleDebug`、`:apps:ledger:assembleNext`：构建成功。
- 同一设备预热后、同一组 16 次上下滑动的最终真机 `gfxinfo` 结果：
  - FoldLedger：650 帧，P50 7ms、P90 12ms、P95 16ms，Missed Vsync 0，
    现代 jank 指标 8.46%。
  - 鲨鱼记账参考：496 帧，P50 7ms、P90 23ms、P95 29ms，Missed Vsync 26。
  - 两者渲染管线与刷新率策略可能不同，因此不只比较 jank 百分比；长帧分位数和
    Missed Vsync 用于确认本次优化没有引入滚动回退。
  - 冷启动后立即测试会包含首次组合/缓存建立成本；上述数字采用预热后结果。

## How to verify manually

```bash
./gradlew :products:ledger:domain:test \
  :products:ledger:data:testDebugUnitTest \
  :products:ledger:presentation:testDebugUnitTest \
  :apps:ledger:lintNext \
  :apps:ledger:assembleDebug \
  :apps:ledger:assembleNext

adb install --no-streaming -r apps/ledger/build/outputs/apk/next/ledger-next.apk
```

1. 在系统字体 1.3 倍下检查页面标题、流水商户、金额和日期分组密度。
2. 点击流水标题栏搜索图标，验证筛选展开、收起和筛选状态。
3. 连续上下滚动长流水，确认不再出现左侧自制滚动条，滚动帧率稳定。
4. 在五个主页面之间反复切换，确认动画结束后状态和滚动位置仍能恢复。

## Installation verification

- 使用 `adb install --no-streaming -r` 覆盖安装，未卸载旧包，既有应用数据得以保留。
- 真机包名：`com.foldledger.next`。
- 版本：`1.0.0-next`（versionCode 1）。
