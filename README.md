# PlainPad

**PlainPad** 是一个轻量、纯粹、离线的 Android 纯文本编辑器。对标 Windows 记事本，没有广告、没有账号、没有云同步，所有文本都保存在你的设备本地。

- **包名**：`com.sunwenqing.plainpad`
- **技术栈**：Kotlin, AndroidX, ViewBinding, Material 3 (XML 布局体系，无 Compose)
- **最低支持**：Android 7.0 (API 24)
- **推荐版本(可体验全部功能)**：Android 12.0+

---

## ✨ 核心特性

### 📝 编辑与多标签
- **多标签编辑**：支持横向滑动，切换时自动保留光标与滚动位置，冷启动可恢复历史标签。
- **快捷工具**：撤销/重做（防抖防 OOM）、查找/替换（分离弹窗）、跳转到指定行。
- **特殊字符**：一键插入换行、制表符、零宽字符、BOM 等 13 种不可见字符。

### 📂 文件与编码
- **基于 SAF**：通过系统文件选择器安全读写文件，支持从外部（文件管理器/微信等）直接打开或分享文本。
- **广泛编码支持**：自动识别并支持保存 UTF-8、UTF-16LE/BE、GBK、GB2312。
- **大文件保护**：读取上限 20MB；当字符数超过 100 万时，自动进入“大文件模式”，禁用行号、撤销、双指缩放等以保证流畅。

### 🎨 显示与美化
- **沉浸体验**：沉浸阅读模式、自定义右侧粗滚动条（带惯性与自动淡出）。
- **精确行号**：像素级对齐，不随字号缩放而抖动。
- **个性化主题**：全局 8 色主题（含 Android 12 动态色）、编辑区 7 种背景色、3 种字体、双指缩放字号。
- **特殊字符标记**：可在设置中开启不可见字符的符号替换显示（不修改原文）。
- **动态图标**：桌面 App 图标会跟随用户选择的主题色自动切换（基于 Activity Alias）。

### 🛠 辅助系统
- **自动保存**：支持切后台、切标签、停止输入（防抖）及定时自动保存。
- **设置中心**：带独立搜索页、板块折叠、自定义平滑滚动条。
- 新手引导、帮助页、关于页、最近文件列表。

---

## 📦 下载与安装

你可以前往 [Releases](https://github.com/Gaoxdwqxql/PlainPad/releases) 页面下载最新的正式版 APK。

**当前最新正式版本**：`v1.1.2`

**安装说明**：
1. 下载 `PlainPad-v*.apk`。
2. 如果设备上安装过 Debug 版本，请先卸载，避免签名冲突。
3. 安装并首次打开，按引导完成设置。

---

## 🛠 构建指南

如果你想自己编译本项目，请确保你的开发环境满足以下条件：

- **Android Studio**：最新版本（推荐使用支持 AGP 9.3.0）
- **JDK**：Java 11 或以上
- **Gradle**：9.5.0（项目已包含 wrapper）

**克隆并编译**：

    git clone https://github.com/Gaoxdwqxql/PlainPad.git
    cd PlainPad
    ./gradlew :app:assembleDebug

**注**：由于使用了动态图标（`activity-alias`），在 Android Studio 中运行调试时，请确保 Run Configuration 中 Launch Options 选择为 `Specified Activity`，并填入 `com.sunwenqing.plainpad.MainActivity`，否则会报错找不到 Activity。

---

## 🔒 隐私说明

- 本应用**没有任何网络权限**，绝对不会上传你的任何数据。
- 所有文件读取、保存均通过 Android SAF 在你本地完成。
- 仅使用 SharedPreferences 保存你的界面设置和未保存的草稿。
- 你可以先在沙箱或虚拟机中运行，以进行确认
- 如果你不信任预编译的APK，请自行构建

---

## 📄 开源协议

本项目基于 [MIT License](LICENSE) 开源。
