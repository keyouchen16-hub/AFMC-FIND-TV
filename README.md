# AFMC FIND · Android TV

[AFMC-FIND](https://github.com/keyouchen16-hub/AFMC-FIND) 的 Android TV 版本：把桌面端的“多源聚合搜索 + 去重排序”搬到电视大屏，用遥控器完成搜索、筛选、查看磁力与扫码取种。

**Find the You.**

| 搜索 | 结果 | 详情 | 设置 |
| --- | --- | --- | --- |
| ![](docs/screenshot-home.png) | ![](docs/screenshot-results.png) | ![](docs/screenshot-detail.png) | ![](docs/screenshot-settings.png) |

## 这是什么

桌面版是 Windows WPF 应用，内含 libtorrent + VLC 的边下边播内核。WPF、DPAPI、Windows 原生播放器都无法在 Android 上复用，因此 TV 版是**按同一套搜索与排序规则重写**的原生应用：

- 保留：多源并行搜索、按 infohash 去重、名称/年份/季集/分辨率约束、相关度与做种加权排序、分类筛选、来源状态。
- 重写：界面（Jetpack Compose，1080p 遥控器焦点导航）、输入（电视输入法）、磁力交付（复制、二维码、Intent 交给外部应用、详情网页）。
- 未移植：内置边下边播（依赖 Windows 侧 libtorrent/Python 运行库与 LibVLC，Android 端需另建 torrent 引擎）、Playwright 浏览器发现、仅桌面存在的站点适配。

## 功能

- 6 个内置搜索源并行查询：Knaben、BitSearch、海盗湾（APIBay）、Nyaa、动漫花园、Torrents.csv。
- 可选接入自建 **Torznab / Jackett** 索引器（地址 + API Key，支持局域网 HTTP）。
- 同一 infohash 只保留做种最好的一条；排序按“关键词覆盖率 → 名称顺序 → 年份/季集约束 → 做种 → 时间”。
- 分类筛选（影视 / 动漫 / 音乐 / 软件 / 游戏 / 书籍 / 科研 / 其他）、仅看有做种、按相关度/做种/大小/时间排序。
- 详情页：完整标题、来源、大小、做种、类别、日期、磁力；支持复制磁力、二维码（手机扫码取种）、打开磁力、打开详情网页。
- 设置页：逐源启用/停用、Torznab 配置；配置保存在本机 SharedPreferences。

## 已验证

- 设备：小米电视 S Pro 65 Mini LED（`MiTV_MFTR0`，Android 14 / SDK 34，1080p，armeabi-v7a），ADB 安装运行。
- “ubuntu” 搜索：Knaben 54、BitSearch 100、海盗湾 100、Torrents.csv 25、动漫花园 2，去重排序后列表正常；详情页、二维码、设置开关与滚动均实测通过。
- JVM 单测：词条规范化、匹配层级、做种加权、去重、磁力解析、RSS/Torznab、日期/大小解析全部通过；`LiveSourcesTest` 在开发机网络下 6/6 源可用。

## 已知限制

- **无内置播放/边下边播**：本版本定位为“大屏搜索 + 磁力交付”。电视上没有能处理 `magnet:` 的应用时，请用二维码或复制磁力交给手机/下载器。
- **网络**：应用强制直连，不读取系统代理。若电视残留了指向 `127.0.0.1` 的全局代理（本机实测存在），其他应用会全部请求失败，本应用不受影响。Nyaa 在部分网络会被连接重置，状态栏会显示“Nyaa 失败”，不影响其他源。
- 只做公开接口聚合，不内置任何账号、不做站内登录。

## 构建

要求：JDK 17、Android SDK 35（build-tools 35.0.0）、Gradle 8.10.2（仓库自带 wrapper）。

```bash
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

发布签名：仓库不包含密钥。构建前在项目根目录创建 `keystore.properties` 与 keystore：

```bash
keytool -genkeypair -v -keystore keystore/afmc-find-tv.jks -alias afmc \
  -keyalg RSA -keysize 2048 -validity 10950
cat > keystore.properties <<'EOF'
storeFile=keystore/afmc-find-tv.jks
storePassword=你的密码
keyAlias=afmc
keyPassword=你的密码
EOF
```

未提供 keystore 时 release 产物未签名，`assembleDebug` 可直接安装调试包。

## 安装到电视

- ADB：电视开启「开发者选项 → USB 调试」，`adb connect <电视IP>:5555`，电视上点允许，然后 `adb install -r app-release.apk`。
- U 盘：把 APK 拷到 U 盘，用电视自带文件管理器或「当贝市场」等工具安装。
- 首次启动按遥控器 OK 打开搜索框；输入后按 OK（或键盘上的「搜索」键）提交。

## 测试

```bash
./gradlew testDebugUnitTest
```

覆盖：词条规范化、匹配层级、做种加权、去重、磁力解析、RSS/Torznab 解析、日期与大小解析。`LiveSourcesTest` 会真实请求 6 个公开源，无网络时自动跳过。

## 目录

- `app/src/main/java/com/afmc/find/tv/core`：排序、去重、链接解析、来源目录、搜索引擎。
- `app/src/main/java/com/afmc/find/tv/sources`：各搜索源解析。
- `app/src/main/java/com/afmc/find/tv/ui`：Compose TV 界面。
- `app/src/test`：JVM 单元测试与真实源冒烟测试。

## 隐私

应用只做公开接口聚合：不登录、不上传搜索词、不保存资源索引；搜索历史仅保留最后一次关键词，用于启动时回填。配置与 Torznab Key 均只存本机。

## 许可

桌面版仓库未另行授予整体开源许可；本移植版沿用同一约定，仅供个人使用与学习。
