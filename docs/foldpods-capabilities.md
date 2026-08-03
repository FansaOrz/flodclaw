# FoldPods 能力说明（L0–L4）

FoldPods（`com.foldpods`）是 FoldSuite 内的 **AirPods Pro 安卓伴侣**。无苹果官方 SDK；能力来自标准蓝牙与社区逆向协议。

## 分级

| 档位 | 内容 | FoldPods 状态 |
|------|------|----------------|
| **L0** | 听歌、通话、机身长按切降噪 | 系统自带；App 提供配对引导 |
| **L1** | BLE 邻近广播电量、开盖感知、通知 | **P0 已实现** |
| **L1+** | 开盖弹层、辅助重连已配对设备 | **P0.5 已实现**（OEM 不稳定） |
| **L2** | AACP：手机切降噪、QS Tile、入耳暂停钩子 | **P1 已实现**（L2CAP 在三星上可能需 root） |
| **L2+** | 对话感知 / 点头接听偏好、摘下暂停 | **P2 偏好已落地**；入耳事件依赖后续 AACP 读包 |
| **L3** | 通透细调、助听、Multipoint | **P3 高级模式说明**（需 root/伪装，默认关闭） |
| **L4** | Find My、固件 OTA、空间头追、Siri | **不支持** |

## 编译与安装

```bash
./gradlew :apps:airpods:assembleDebug
adb install -r apps/airpods/build/outputs/apk/debug/airpods-debug.apk
```

## 模块

```text
apps/airpods
products/airpods/domain|bluetooth|data|presentation
```

禁止依赖 `products/foldclaw`。跨 App 用 `shared/platform`。

## 开盖自动连接

- 可：开盖弹电量 + 尝试 A2DP 辅助重连
- 不可：首次开盖像 iPhone 一样系统级一键配对

## 不支持清单（L4）

Siri、Find My 完整网络、固件升级、耳塞贴合度测试、系统级动态空间音频、iCloud Continuity 无缝切换。
