# App 脚手架（不要 include 进 settings）

复制步骤：

1. `cp -R apps/_template apps/<id>`
2. 创建 `products/<id>/domain`（及需要的 feature 库），用 `fold.jvm.library` / `fold.android.library(.compose)`
3. 在根 `settings.gradle.kts` 增加：
   - `include(":apps:<id>")`
   - `include(":products:<id>:domain")` …
4. 改 `applicationId` / `namespace` / 图标 / 主题
5. 只依赖 `:shared:core`、`:shared:platform` 与自己的 `:products:<id>:*`
6. 根 `build.gradle.kts` 的 `assembleAllApps` 增加依赖
7. `./gradlew :apps:<id>:assembleDebug`

完整规则见 [docs/multi-app.md](../../docs/multi-app.md)。
