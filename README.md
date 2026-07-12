# APro 视频播放器

一款用于浏览和播放本机视频文件的 Android 视频播放器。应用以本地视频管理与播放体验为核心，支持简洁的视频列表、网格浏览和播放设置。

## 功能

- 自动读取设备中的本地视频
- 列表与网格两种浏览方式，展示视频缩略图
- 播放、暂停、快进与精确拖动进度
- 视频管理：排序、隐藏和删除操作
- 可设置自动播放下一条视频，以及启动时随机播放
- 内置中文、English、Français、Español、Русский、日本語、한국어等语言

## 下载与安装

请前往 [Releases](https://github.com/GOGOGTA/apro-video-player/releases) 下载最新 APK。

当前 `v1.0.0` 是测试版，提供的是 debug APK。Android 可能显示“来源不明”或“风险提示”；这是因为它尚未使用正式发布证书签名。仅请从本仓库的 Release 页面下载和安装。

安装后首次打开时，请授予“视频和音乐”读取权限，应用才能扫描和播放设备上的本地视频。

## 隐私说明

应用在本地读取视频文件，不会自行上传视频文件。为提供系统播放通知及受支持设备上的 OPPO 流体云功能，当前媒体标题、播放进度和本地媒体标识可能会发送给设备系统服务。

## 开发与构建

项目使用 Android Studio 打开。运行以下命令可构建测试 APK：

```bash
./gradlew assembleDebug
```

生成文件位于 `app/build/outputs/apk/debug/app-debug.apk`。

## 反馈

如有问题或建议，欢迎发送邮件至 [aaallthebest@outlook.com](mailto:aaallthebest@outlook.com)。
