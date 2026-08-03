# 快速切页性能优化

## Background / purpose

用户反馈连续快速点击底部导航时存在明显卡顿。真机使用 20 次、约 80ms 间隔的连续切页复现后，
发现普通帧耗时较低，但动画被新目标反复中断时会短时间同时组合和绘制多个全屏页面，产生
125–200ms 的偶发长帧和 5 次 Missed Vsync。

## Files changed

- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/FoldLedgerApp.kt`
  - 分离底栏选中页面与当前实际展示页面。
  - 增加快速切页合并策略，动画期间只记录最新目标。
  - 缩短主页面进入、退出和淡入淡出动画。
- `products/ledger/presentation/src/test/kotlin/com/foldledger/presentation/FoldLedgerAppTest.kt`
  - 增加空闲切换和动画中合并目标的回归测试。

## Key logic changes

- 底部导航点击后，选中态立即响应。
- 当前没有动画时，目标页面立即进入正常过渡。
- 当前动画尚未结束时，后续点击不再中断现有过渡，也不会继续创建中间页面；只保留最后一个
  用户选择。现有过渡结束后，再切换到最后目标。
- 任意时刻最多只有当前进入页和退出页参与组合与绘制，避免快速点击造成页面过渡堆叠。
- 进入动画从 260ms 缩短至 160ms，退出动画从 220ms 缩短至 130ms；淡入淡出同步缩短。
- 页面 `SaveableStateHolder` 和 ViewModel 状态保存逻辑不变。

## API or behavior changes

- 没有领域 API、数据库、备份格式或权限变化。
- 快速连点时会合并中间目标，底栏仍立即显示最后一次选择，内容在当前短动画结束后跟进。
- 正常速度的单次切页仍保留滑动与淡入淡出动画。

## Risks and compatibility impact

- 极端连续点击时，中间页面可能不会实际展示，这是主动合并行为，最终页面始终以最后一次点击为准。
- 真机帧数据来自 ADB 合成点击，输入延迟统计包含 ADB 注入开销；主要比较 UI 线程长帧和
  Missed Vsync。
- 首次进入尚未创建过的页面仍需完成 ViewModel 和 UI 初次组合，但不会再与多个中断动画叠加。

## Test coverage added or updated

- 新增 2 个 `resolveDisplayedTab` 单元测试：
  - 动画空闲时立即切换目标。
  - 动画运行时保留当前内容、合并后续目标。
- 已执行并通过：
  - Domain：2 个测试。
  - Data：10 个测试。
  - Presentation：4 个测试。
  - `:apps:ledger:lintNext`：No issues found。
  - `:apps:ledger:assembleDebug`、`:apps:ledger:assembleNext`：构建成功。
  - `git diff --check`：通过。

## Performance verification

同一台 Samsung SM-F966U、相同页面数据、页面预热后，连续执行 20 次导航点击，每次间隔约
80ms：

- 优化前：P50 5ms、P90 5ms、P95 5ms、P99 150ms，Missed Vsync 5；
  出现 4 个 150ms 桶帧和 2 个 200ms 桶帧。
- 优化后复测：P50 5ms、P90 6ms、P95 11ms、P99 61ms，Missed Vsync 0；
  未再出现 125ms 及以上帧。

优化后的中位数基本不变，主要收益是消除了快速中断导致的 150–200ms 尖峰和 VSync 丢失。

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

1. 按正常速度依次切换五个主页面，确认方向动画和页面状态恢复正常。
2. 快速来回点击不同底栏入口，确认底栏即时响应、内容不闪烁、不叠加多个中间页面。
3. 最后停在任一页面，确认显示内容与最终选中的底栏入口一致。
