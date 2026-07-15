# AceShare TV

Android TV 客户端，通过局域网 mDNS 发现 AceShare 服务，用遥控器浏览并复制 lines / texts，查看 files。

## 要求

- Android TV / 模拟器，API 24+
- 与运行 AceShare 的电脑处于同一 Wi‑Fi
- 主机端：Go AceShare（宣告 `_aceshare._tcp`，提供 HTTP API）

## 权限

| 权限 | 用途 |
|------|------|
| `INTERNET` | 访问 `http://host:port` |
| `ACCESS_NETWORK_STATE` | 网络状态 |
| `CHANGE_WIFI_MULTICAST_STATE` | mDNS / NSD 组播发现 |
| `WRITE_EXTERNAL_STORAGE`（API ≤ 28） | DownloadManager 保存文件 |

局域网使用明文 HTTP，已开启 `usesCleartextTraffic`。

## 联调步骤

1. 在电脑上启动 AceShare（确保 mDNS 已宣告，浏览器能打开服务地址）。
2. 电视与电脑连接同一 Wi‑Fi。
3. Android Studio 安装本 App 到 TV 设备 / 模拟器：
   ```bash
   ./gradlew :app:assembleDebug
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. 启动后应出现雷达 Lottie 搜索态；发现服务后进入内容页。
5. 在 **短文本** 中用方向键选中一项，按 OK 复制到剪贴板。

## 模块结构

```
app/src/main/java/com/alex/aceshare/
  AceShareApp.kt          # 应用级 Discovery / Repository / Prefs
  MainActivity.kt         # 单 Activity
  discovery/              # NsdManager 发现
  data/                   # API、SSE、Repository、DataStore
  ui/
    discovery/            # 发现页 + Lottie
    browse/               # Lines / Texts / Files
    components/           # 可聚焦卡片、Tab
    navigation/           # NavHost
    theme/                # 深色 TV 主题
```

## 协议摘要

- 发现：`NsdManager`，serviceType `_aceshare._tcp.`
- `GET /api/list` → files / lines / texts
- `GET /api/events` → SSE `change`（失败时 8s 轮询兜底）
- `GET /files/{name}` → 文件下载

详见 [docs/android-tv-client-prompt.md](docs/android-tv-client-prompt.md)。

## Lottie 资源

- 文件：`app/src/main/res/raw/search_radar.json`
- 来源：项目内自制「雷达扫描」占位动画（深蓝描边圆环 + 旋转扫掠），可自由商用；可替换为 [LottieFiles](https://lottiefiles.com/) 等可商用免费资源。

## 交互约定

- 内容页返回 → 发现页；发现页再返回 → 退出确认
- 顶栏「当前源」OK → 切换服务器
- 单台服务器且开启自动进入（默认开）时，短暂展示后自动进入
