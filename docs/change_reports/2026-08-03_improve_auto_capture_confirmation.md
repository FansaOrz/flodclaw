# 自动记账确认体验改进

## Background / purpose

旧悬浮窗使用全屏宽度、方形浅灰背景和固定屏幕坐标，并启用了
`FLAG_LAYOUT_IN_SCREEN`，在 Fold 外屏和状态栏附近容易显得拥挤。弹窗内的“忽略”
会直接解决待确认记录，批量确认也没有二次确认；识别错误时，用户只能先入账再修改。

本次将自动记账改造成“识别—核对—入账”的安全闭环，并补齐金额精度和删除撤销等高频交互。

## Files changed

- `products/ledger/capture/src/main/res/layout/overlay_confirm.xml`
  - 重构自动记账悬浮卡片。
- `products/ledger/capture/src/main/res/drawable/`
  - 新增卡片、按钮、来源标签和通知栏品牌图标资源。
- `products/ledger/capture/src/main/kotlin/com/foldledger/capture/overlay/ConfirmOverlayController.kt`
  - 修复安全区和宽度适配，加入进出场动画与新的确认流程。
- `products/ledger/capture/src/main/kotlin/com/foldledger/capture/notify/CaptureAlertNotifier.kt`
  - 同步重做通知栏文案、图标和操作。
- `products/ledger/capture/src/main/kotlin/com/foldledger/capture/pipeline/CapturePipeline.kt`
  - 支持核对待确认记录时写入备注。
- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/ledger/LedgerScreen.kt`
  - 重构待确认卡片，新增编辑核对、批量确认保护、删除确认和撤销。
- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/ledger/LedgerViewModel.kt`
  - 新增核对后入账、恢复流水和精确金额换算。
- `products/ledger/presentation/src/test/kotlin/com/foldledger/presentation/ledger/LedgerViewModelTest.kt`
  - 新增金额换算回归测试。
- `products/ledger/presentation/build.gradle.kts`
  - 增加 presentation 单元测试依赖。
- `docs/foldledger-fold7-verify.md`
  - 更新真机自动记账验收步骤。

## Key logic changes

- 悬浮窗限制为最大 420 dp，左右保留 16 dp 间距，不再使用
  `FLAG_LAYOUT_IN_SCREEN`，从根因上避免侵入状态栏。
- 使用暖瓷白浮层、28 dp 圆角、群青主操作和来源胶囊标签；出现时执行
  280 ms 淡入、下移和轻缩放，关闭时执行 180 ms 上移淡出。
- 悬浮窗提供三个安全分支：
  - “确认记账”：触觉反馈后直接入账。
  - “打开应用核对”：保留 pending，在 App 内编辑后入账。
  - 关闭按钮：只关闭本次浮层，记录仍在待确认列表。
- 移除悬浮窗和通知栏中容易误触且不可撤销的“忽略”操作。
- App 内待确认卡片可编辑金额、商户、收支方向、账户、分类和备注。
- “确认全部”增加二次确认；“不记录”显示具体商户和金额后再次确认。
- 流水删除增加确认，并通过 Snackbar 提供即时撤销。
- 元转分不再使用 `Double × 100`，改为 `BigDecimal` 精确换算；拒绝负数、零值、
  超过两位小数和非法输入。

## API or behavior changes

- `CapturePipeline.confirmPending` 新增具有默认值的 `note` 参数，现有调用保持兼容。
- 关闭悬浮窗不再丢弃待确认记录；需要在 App 内明确选择“不记录”。
- 自动识别记录在入账前可以修改；修改不会改变原始识别文本和抓取时间。
- 手动记账和核对入账只接受大于 0 且最多两位小数的金额。

## Risks and compatibility impact

- 没有数据库迁移、备份格式或 applicationId 变化。
- 悬浮窗使用 Android View XML 而非 Compose，以保持前台服务和系统 Overlay 的现有架构。
- 固定使用浅色品牌浮层，确保覆盖在任意第三方 App 上时对比度稳定；不跟随宿主 App 主题。
- 转账型自动识别仍沿用现有数据行为；本次未推断未知的转入账户。
- 自动记账真机端到端验证需要实际触发微信、支付宝或银行短信识别事件。

## Test coverage added or updated

- 新增 `LedgerViewModelTest`：
  - 验证 `0.01`、一位小数和大金额的精确元转分。
  - 验证空值、零、负数、超过两位小数和非数字输入被拒绝。
- 保留并执行既有领域和数据层测试。
- 实际执行结果：
  - `:products:ledger:domain:test`：2 个测试通过。
  - `:products:ledger:data:testDebugUnitTest`：10 个测试通过。
  - `:products:ledger:presentation:testDebugUnitTest`：新增 2 个测试通过。
  - `:apps:ledger:lintNext`：通过，`No issues found`。
  - `:apps:ledger:assembleDebug`：通过。
  - `:apps:ledger:assembleNext`：通过。
  - `git diff --check`：通过。

## How to verify manually

```bash
./gradlew :products:ledger:domain:test \
  :products:ledger:data:testDebugUnitTest \
  :products:ledger:presentation:testDebugUnitTest \
  :apps:ledger:lintNext \
  :apps:ledger:assembleNext

adb install --no-streaming -r apps/ledger/build/outputs/apk/next/ledger-next.apk
```

1. 用微信或支付宝完成一笔小额付款，确认悬浮卡片不覆盖状态栏。
2. 分别验证关闭、打开应用核对和确认记账三个分支。
3. 在核对页修改金额、商户、账户、分类和备注，确认入账结果一致。
4. 对多条待确认记录使用“确认全部”，确认存在二次确认且未知金额被跳过。
5. 删除一笔流水，点击 Snackbar 的“撤销”，确认流水恢复。
