# AceShare

局域网 AceShare 客户端。`:tv` 是电视界面，`:mobile` 是手机、平板和折叠屏界面。两端共用包名 `com.alex.aceshare`，准备作为同一个 Google Play 应用发布。

## 模块

```
core/      协议、发现、仓库、共享 ViewModel、更新接口
update/    GitHub 自更新，只打进 github 变体
tv/        电视界面（Compose TV）
mobile/    手机 / 折叠屏界面（Material 3）
```

每个应用有 `distribution` 维度：

| 变体 | 用途 |
|------|------|
| `github` | 从 GitHub Release 安装，带应用内更新 |
| `play` | 上架 Google Play，不含自更新代码和 `REQUEST_INSTALL_PACKAGES` |

`versionCode = VERSION_CODE_BASE * 10 + 设备位`。电视是 1，手机是 2。例如 `1.2.3` 的基数是 `10203`，电视包是 `102031`，手机包是 `102032`。GitHub 版是否有更新改为比较 versionName，不再比较 versionCode。

## 构建

需要带 `jlink` 的 JDK（Android Studio 的 JBR）。不要用 Cursor 自带的 JRE。

```bash
./gradlew :tv:assembleGithubDebug
./gradlew :mobile:assembleGithubDebug
```

Play 包：

```bash
./gradlew :tv:bundlePlayRelease :mobile:bundlePlayRelease -PVERSION_NAME=1.2.0 -PVERSION_CODE_BASE=10200
```

电视和手机包名相同，不能同时装在一台设备上。

## 发布

推送 `v*` tag 后，CI 会：

- 把四个文件附到本仓库的 Release：`AceShare-TV-vX.Y.Z.apk`、`AceShare-TV-vX.Y.Z.aab`、`Mobile-AceShare-vX.Y.Z.apk`、`Mobile-AceShare-vX.Y.Z.aab`。
- 电视和手机的应用内更新都读这个 Release，分别按 `AceShare-TV-` 和 `Mobile-AceShare-` 选择 APK，忽略 AAB。
- v1.1.0 及更早的电视端会下载按文件名排序后的第一个 `.apk`。手机 APK 因此用 `Mobile-AceShare-` 前缀，排在 `AceShare-TV-` 后面。不要再添加文件名排在 `AceShare-TV-` 前面的 `.apk`。

Play 应用签名使用现有的 release keystore，这样 GitHub 安装的用户可以升级到 Play 版。电视 AAB 上传到 Android TV 轨道，手机 AAB 上传到手机轨道。

## 要求

- 电视：Android TV / 模拟器，API 25+
- 手机：API 25+，不锁定方向
- 与运行 AceShare 的电脑处于同一 Wi‑Fi
- 主机端：Go AceShare（宣告 `_aceshare._tcp`，提供 HTTP API）

## 权限

| 权限 | 用途 |
|------|------|
| `INTERNET` | 访问 `http://host:port` |
| `ACCESS_NETWORK_STATE` | 网络状态 |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS / NSD 组播发现 |
| `WRITE_EXTERNAL_STORAGE`（API ≤ 28） | DownloadManager 保存文件 |
| `REQUEST_INSTALL_PACKAGES` | 仅 `github` 变体的应用内更新 |

局域网使用明文 HTTP，已开启 `usesCleartextTraffic`。

## 协议摘要

- 发现：`NsdManager`，serviceType `_aceshare._tcp.`
- `GET /api/list` → files / lines / texts
- `GET /api/events` → SSE `change`（失败时 8s 轮询兜底）
- `GET /files/{name}` → 文件下载
- `POST /api/upload` → `multipart/form-data` 上传到 `files/`，字段名 `file`（可多选）。重名保存为 `名称_2.扩展名`

详见 [docs/android-tv-client-prompt.md](docs/android-tv-client-prompt.md)。

## 手机端布局

窗口宽度决定布局，不判断机型。窄屏使用底部导航；更宽的窗口（展开的折叠屏、平板）使用侧边导航。长文本在窄屏是列表和详情两个页面，在宽屏左右分栏。当前标签、打开的长文本和下载确认会在折叠、展开导致 Activity 重建后保留。

## Lottie 资源

- 文件：`tv/src/main/res/raw/search_radar.json`（手机端有一份副本）
- 来源：项目内自制「雷达扫描」占位动画，可自由商用
