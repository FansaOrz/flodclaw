# 多 App Monorepo 指南

本仓库（`FoldSuite`）可同时承载多个独立 Android App。业务按产品垂直切片；跨 App 只通过 `shared/platform` 契约做**运行时**适配。

## 目录约定

```text
apps/<id>/                 # 唯一 application 入口（包名、签名、Manifest）
products/<id>/…            # 该产品业务库（禁止依赖其他 products/*）
shared/core                # 纯工具（Outcome 等），禁止产品名词
shared/platform            # 跨 App Intent / package 契约
build-logic/               # fold.android.* / fold.jvm.* 约定插件
apps/_template/            # 拷贝用脚手架（不 include）
```

## 依赖硬规则

- `apps/A` → `products/A/*` + `shared/*` ✅
- `products/A` → `shared/*` ✅
- `products/A` → `products/B` ❌
- 跨 App 唤起：只用 `com.foldsuite.platform.crossapp.*` 常量构造 Intent

## 第 N 个 App 接入清单

1. 复制 `apps/_template` → `apps/<id>`
2. 新建 `products/<id>/domain`（+ 按需 presentation / bluetooth 等）
3. `settings.gradle.kts` 增加对应 `include`
4. 设定独立 `applicationId`（debug 自动 `.debug`）
5. 各 App 独立 `@HiltAndroidApp`
6. 若需被其他 App 打开：在 Manifest 声明 `CrossAppIntents` 中的 action，并在 `CrossAppIds` 登记 package
7. `./gradlew :apps:<id>:assembleDebug`
8. 把该任务加入根项目 `assembleAllApps`

## 编译命令

```bash
# 全部 App debug
./gradlew assembleAllApps

# 单独
./gradlew :apps:foldclaw:assembleDebug
./gradlew :apps:airpods:assembleDebug
```

APK 路径：

- `apps/foldclaw/build/outputs/apk/debug/foldclaw-debug.apk
- `apps/airpods/build/outputs/apk/debug/airpods-debug.apk`

安装（保留数据请用 `-r`）：

```bash
adb install -r apps/foldclaw/build/outputs/apk/debug/*.apk
adb install -r apps/airpods/build/outputs/apk/debug/*.apk
```

## 跨 App 调用示例（调用方）

```kotlin
val launch = CrossAppLauncher.openAirPods(debug = BuildConfig.DEBUG)
val intent = Intent(launch.action).setPackage(launch.targetPackage)
intent.putExtra(CrossAppIntents.EXTRA_SOURCE_APP, launch.sourceApp)
if (packageManager.resolveActivity(intent, 0) != null) {
    startActivity(intent)
} else {
    // 未安装：提示或跳转下载
}
```

## shared 禁区

`shared/*` 不得出现：Task、Agent、AirPods 协议细节、具体 UI 页面。产品专有模型放在对应 `products/<id>`。
