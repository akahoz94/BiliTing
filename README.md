# BiliTing

> B 站听书 Android 客户端 · Material 3 · 沉浸式播放页 · 迷你播放条常驻 · UP 主主页 · 3 层搜索 waterfall

[📦 下载最新 release (BiliTing-v0.6-release.apk)](../../releases/latest)

## 项目简介

BiliTing 是一个轻量级 Android 客户端，专门用来把 B 站上高质量的有声书/影视解说/教程视频当成"听书"。视频不必看，进地铁、做家务、睡前都能听。

## 功能

- 🎵 **沉浸式播放页** — 全屏渐变背景延伸至状态栏后，标题/作者/封面真正紧凑贴合（借鉴得到、微信读书版式）
- 🎛 **迷你播放条常驻** — 在底部导航上方显示当前播放项，点击随时回到播放页
- 🎚 **后台播放 / 倍速 / 选集 / 定时** — 后台保活，倍速支持 0.75× / 1× / 1.25× / 1.5× / 2×，定时 15 / 30 / 60 分钟
- ❤️ **收藏（书架） + 历史** — 本地 Room 存储，共用一张表用 `isFavorite` 区分
- 👤 **UP 主主页** — 搜索项点头像/作者名进入 UP 主主页，分页拉取全部投稿
- 🔍 **3 层搜索 waterfall** — `/x/web-interface/wbi/search/type` → `/x/web-interface/search/all/v2` → `/api.bilibili.com/search`，任意一层成功即可
- 🌗 **主题跟随系统** — Material 3，支持 force light / force dark / 跟随系统

## 截图

待补

## 技术栈

- **语言**：Kotlin
- **UI**：Jetpack Compose + Material 3
- **网络**：Retrofit + OkHttp
- **数据库**：Room (v4，含 4 个迁移版本)
- **播放器**：ExoPlayer (Media3)
- **构建**：Gradle (KTS) · AGP · KSP

## 项目结构

```
BiliTing/
├── app/
│   ├── src/main/java/com/tingbili/app/
│   │   ├── data/
│   │   │   ├── api/          # Retrofit 接口 + wbi 签名 + 3 层搜索 waterfall
│   │   │   ├── local/        # Room (BookRecord, AppDatabase v4)
│   │   │   └── repo/         # Repository
│   │   ├── player/           # PlayerHolder, PlayerService
│   │   ├── ui/
│   │   │   ├── nav/          # BiliNavHost, MiniPlayerBar
│   │   │   ├── search/       # 搜索
│   │   │   ├── player/       # 沉浸式播放页
│   │   │   ├── shelf/        # 收藏书架
│   │   │   ├── history/      # 历史
│   │   │   ├── settings/     # 设置
│   │   │   └── author/       # UP 主主页
│   │   └── util/             # CoverUtil 等
│   └── build.gradle.kts
├── bin/                       # 构建产物（APK）
└── docs/
    ├── superpowers/
    │   ├── plans/             # 实施计划
    │   └── specs/             # 设计文档
```

## 构建

需求：

- JDK 17 (`C:\Users\Admin\jdk-17.0.20.1+1` 或环境变量 `JAVA_HOME`)
- Android SDK（compileSdk 36, minSdk 26, targetSdk 36）

```bash
cd BiliTing
gradlew.bat assembleRelease
# 产物: app/build/outputs/apk/release/app-release.apk
```

## 版本

| versionName | versionCode | 变更 |
|---|---|---|
| 0.1.0 | 1 | 初版：搜索 + 播放 + 收藏 + 历史 + 设置 |
| 0.2.0 | 2 | UI 重设计 |
| 0.3.0 | 3 | 移除书签功能；播放页 UI 重排（封面 + 标题贴合） |
| 0.4.x | 4 | UP 主主页功能；播放页作者名可点击 |
| 0.5.0 | 5 | Room schema 升级到 v4 + 全局崩溃落地 |
| **0.6.0** | **6** | **3 层搜索 waterfall；versionCode 修正** |

## 已知问题 / 教训

- **B 站匿名搜索风控** — 单端点会空，3 层 waterfall 是当前最稳的方案
- **Room schema 必须随实体字段同步升级** — v0.4 闪退就是这个原因，加 `fallbackToDestructiveMigration()` 兜底
- **Android 15+ 强制 edge-to-edge** — 必须显式 `enableEdgeToEdge()` + `WindowInsets.statusBarsPadding()`，否则状态栏盖顶栏

## License

MIT