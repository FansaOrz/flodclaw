# FoldLedger 融合与多 App 构建改进

## Background / purpose

将独立 FoldLedger Android 工程融合进 FoldSuite 多 App monorepo，同时保持应用包名、
debug 包名、模块边界和已有功能不变。修复根项目全量构建需要手工登记新 App 的扩展性
问题，并收紧账本数据库迁移失败时的数据安全行为。

## Files changed

- `apps/ledger/`：新增 FoldLedger application 入口、Manifest、资源和启动代码。
- `products/ledger/domain/`：新增账本领域模型、仓储接口、工具类及单元测试。
- `products/ledger/data/`：新增 Room、DataStore、解析、备份和仓储实现及单元测试。
- `products/ledger/capture/`：新增通知、无障碍、短信、悬浮窗和前台保活能力。
- `products/ledger/presentation/`：新增 Compose 流水、统计、账户、预算和设置界面。
- `settings.gradle.kts`：登记 FoldLedger 的五个模块。
- `build.gradle.kts`：`assembleAllApps` 自动发现 `:apps:*` 直接子模块。
- `README.md`、`docs/multi-app.md`、`apps/_template/README.md`：更新产品、
  构建、安装和接入说明。
- `docs/foldledger-fold7-verify.md`：迁入 Fold 7 真机验收清单。

## Key logic changes

- 独立工程模块映射为：
  - `app` → `:apps:ledger`
  - `domain` → `:products:ledger:domain`
  - `data` → `:products:ledger:data`
  - `capture` → `:products:ledger:capture`
  - `presentation` → `:products:ledger:presentation`
- 所有模块改用 FoldSuite 约定插件，统一 compileSdk 36、minSdk 31、targetSdk 36、
  Java/Kotlin 17 和 debug applicationId 后缀。
- `assembleAllApps` 从硬编码 FoldClaw/FoldPods 改为按已 include 的一级
  `:apps:*` 模块生成依赖，新 App 不再需要修改根构建任务。
- Room 保留显式 `1 → 2 → 3` migration，但移除 destructive fallback。
  缺失迁移时会明确失败，不再静默删除个人账本数据。

## API or behavior changes

- 新增独立可安装应用：
  - release applicationId：`com.foldledger`
  - debug applicationId：`com.foldledger.debug`
- FoldLedger 原有 Kotlin 包名、数据库名 `foldledger.db` 和业务行为保持不变，
  可继续覆盖安装相同签名和 applicationId 的原 APK。
- 对未知或缺失的 Room migration，启动将失败并保留数据库文件，而不是清空重建。
- 根 `assembleAllApps` 会自动包含 FoldLedger 及后续纳入 `:apps:*` 的应用。

## Risks and compatibility impact

- 与原独立工程使用相同签名时，可通过 `adb install -r` 覆盖并保留私有数据；
  签名不同则 Android 会拒绝覆盖安装。
- destructive fallback 被移除后，未来每次提升数据库版本都必须提供完整 migration。
  这是有意的数据保护约束。
- 账本包含通知监听、无障碍、短信和 special-use 前台服务，仍需在目标设备上逐项授权
  并按验收清单做真机验证。
- 编译仍报告部分 Android deprecated API 和 Kotlin 2.x kapt 兼容警告；当前构建不受影响，
  后续升级工具链时应处理。

## Test coverage added or updated

- 迁入原有领域测试 `UtilsTest`。
- 迁入原有数据解析测试 `PaymentNotificationParserTest` 和
  `BankSmsAndBillImportTest`。
- 本次未新增 Room instrumentation migration test；当前 migration 行为通过配置审查和
  应用构建验证，数据库升级仍应在真机保留数据场景验证。

## How to verify manually

```bash
./gradlew :products:ledger:domain:test \
  :products:ledger:data:testDebugUnitTest \
  :apps:ledger:assembleDebug

./gradlew assembleAllApps --dry-run

adb install -r apps/ledger/build/outputs/apk/debug/ledger-debug.apk
```

安装后按 `docs/foldledger-fold7-verify.md` 检查通知捕获、支付宝无障碍补全、短信导入、
悬浮确认、统计、预算、备份与重启后的数据保留。
