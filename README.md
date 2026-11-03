# AceShare

局域网 AceShare 客户端，连接同一局域网里的 [AceShare](https://github.com/zhongwcool/AceShare) 服务端。`:tv` 是电视界面，`:mobile` 是手机、平板和折叠屏界面。

功能运行依赖服务端，当前最新版 [去下载](https://github.com/zhongwcool/AceShare/releases/)。

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
