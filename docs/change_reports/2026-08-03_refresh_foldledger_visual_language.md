# FoldLedger 视觉语言更新

## Background / purpose

用户提供了鲨鱼记账的界面作为体验参考。参考重点不是复制黄色、鲨鱼图形或具体页面，
而是借鉴它稳定的视觉规律：单一高识别度品牌色、轻中性背景、白色信息容器、深色线性图标、
清晰的数字层级，以及始终突出的主操作。

本次采用原创的“清透薄荷青 + 云白 + 深海墨”方向，将主题、Logo、导航、页面头部、
流水分类图标和自动记账弹窗统一到同一套品牌语言。

## Files changed

- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/theme/Theme.kt`
  - 重建浅色/深色色板并收紧圆角尺度。
- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/common/LedgerDesign.kt`
  - 重做品牌标识和通用品牌色页面头部。
- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/common/CategoryColorUi.kt`
  - 新增分类线性图标徽章。
- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/FoldLedgerApp.kt`
  - 统一状态栏背景与底部导航选中态，强化中央流水入口。
- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/nav/TopDest.kt`
  - 调整手机底部导航顺序，使流水位于中央。
- `products/ledger/presentation/src/main/kotlin/com/foldledger/presentation/ledger/LedgerScreen.kt`
  - 流水列表使用分类图标徽章替代单色小圆点。
- `products/ledger/capture/src/main/res/layout/overlay_confirm.xml`
  - 自动记账弹窗同步新品牌色。
- `products/ledger/capture/src/main/res/drawable/overlay_*.xml`
  - 同步弹窗卡片、来源标签和按钮颜色、圆角。
- `apps/ledger/src/main/res/mipmap-anydpi/ic_launcher.xml`
  - 重做 Launcher Logo。

## Key logic changes

- 浅色主题以薄荷青 `#62D6BF` 作为大面积品牌容器，深海绿 `#176B61` 作为可访问的
  操作色，背景使用 `#F3F5F4`，内容卡使用纯白。
- 支出/风险只使用少量珊瑚红，避免整页多色争抢视觉焦点。
- 深色主题使用同一色相的低亮容器和高亮薄荷操作色，保持品牌一致性。
- 状态栏后方只绘制品牌背景，页面内容仍消费安全区，不会重新侵入系统状态栏。
- 页面标题统一为品牌色头部；操作按钮使用深海绿，确保在薄荷背景上仍清晰可见。
- 手机底部导航调整为“统计、账户、流水、预算、设置”，中央流水入口使用独立圆形容器。
- 流水分类不再只显示小色点，改用餐饮、交通、购物、居住、运动、娱乐、医疗等线性图标；
  未匹配名称使用通用分类图标。
- 新 Logo 是一本沿中缝展开的折页账本，跨页资金流以珊瑚端点收束；不使用鲨鱼轮廓或
  参考应用的图形细节。
- 自动记账浮层改用白卡、薄荷主按钮、淡薄荷来源标签和更紧凑的 14–20 dp 圆角。

## API or behavior changes

- 没有领域 API、数据库、备份格式、权限或 applicationId 变化。
- 手机底部导航项目顺序发生变化，但每个导航目标和页面状态保持不变。
- Fold/平板仍使用左侧导航轨；中央强调只应用于 COMPACT 手机底部导航。

## Risks and compatibility impact

- 现有用户自定义分类颜色不变；只有分类图标容器和系统默认配色变化。
- Material 图标按分类中文名称映射，自定义分类目前使用通用图标。
- Logo 为项目内原创矢量实现，没有复制参考应用的商标或鲨鱼形象。
- 本次视觉变更不影响已导入的数据和旧版并排安装。
- `com.foldledger.next` 已通过 `adb install --no-streaming -r` 覆盖安装成功；设备外屏处于
  锁屏/息屏状态，因此未绕过用户锁屏进行页面截图验收。

## Test coverage added or updated

- 本次为纯视觉与导航排列更新，没有新增业务单元测试。
- 保留并执行领域、数据、presentation 金额精度测试，以及 Next lint 和两套 APK 构建。
- 实际执行结果：
  - `:products:ledger:domain:test`：2 个测试通过。
  - `:products:ledger:data:testDebugUnitTest`：10 个测试通过。
  - `:products:ledger:presentation:testDebugUnitTest`：2 个测试通过。
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
  :apps:ledger:assembleDebug \
  :apps:ledger:assembleNext

adb install --no-streaming -r apps/ledger/build/outputs/apk/next/ledger-next.apk
```

1. 折叠态检查品牌色状态栏背景、页面头部、中央流水入口和分类图标。
2. 展开态检查侧边导航轨、品牌 Logo 与页面头部是否连续且不侵入状态栏。
3. 检查浅色/深色模式下文字、主按钮和警示色对比度。
4. 触发一笔自动记账，确认悬浮卡片与主 App 使用同一配色语言。
