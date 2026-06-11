# 视频音频提取器 (Video-Audio Extractor)

![Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg?style=flat-size&logo=android)
![Compose](https://img.shields.io/badge/UI-Jetpack%2520Compose-4285F4.svg?style=flat-size&logo=jetpackcompose)
![License](https://img.shields.io/badge/License-MIT-blue.svg)

一款专为 Android 打造的、**完全离线**且**安全私密**的高性能视频音频提取工具。本应用旨在帮助用户快速、无损地从本地视频文件中提取高质量音轨，并支持一键导出、分享与播放。

---

## 🌟 核心特性

- **🚀 极速本地提取**：依托纯本地高性能解析内核，所有视频解析和音轨提取操作均在您的移动设备上密闭完成，不耗费任何云端流量，处理速度极快。
- **🔒 绝对隐私安全**：
  - 单机纯本地运行，不含任何联网上传逻辑，杜绝任何媒体数据传输风险。
  - 用户选定的视频及提取出来的音频对第三方完全保密。
- **🎵 多格式无损导出**：支持将提取出的音频一键转换为高品质 **MP3**、**M4A** 等各种常见音轨格式，满足您的多终端播放需求。
- **📂 智能历史与导出管理**：
  - 基于 **Room Database** 构建的提取记录历史面板，支持随时追踪、删除或重命名您的提取记录。
  - 支持一键将音频导出保存至系统核心的 **Downloads (下载)** 目录，方便在其他播放器中随心调用。
- **🎧 内置音频媒体播放器**：
  - 应用内置功能完备的音频播放内核，支持在应用内直接试听、循环播放、拖动进度条。
- **🛡️ 完善的合规与隐私支持**：
  - 首屏强交互的安全隐私政策同意弹窗，完美契合各大应用商店（如华为、小米、Google Play）的最新上架合规条件。
  - 内置便捷的关于面板，集成了隐私政策、用户服务协议、客服联系等一站式服务。

---

## 🎨 视觉与交互设计

- **深邃科技酷黑主题**：基于 Material Design 3 进行高度定制，采用亮丽的 **等离子蓝 (AccentCyan)** 和 **珊瑚红 (AccentCoral)** 互补色，极具律动与呼吸感。
- **沉浸式无边框体验 (Edge-to-Edge)**：支持全面屏无缝接壤以及完美的系统状态栏、导航条避让设计。
- **灵动的波动声谱逻辑**：优雅简洁的小幅可视化波形声谱图标及实时状态机动画。

---

## 🛠️ 技术栈与架构设计

- **开发语言**：Kotlin
- **UI 框架**：Jetpack Compose (声明式 UI 编程)
- **架构模式**：MVVM (Model-View-ViewModel) + LiveData / StateFlow
- **本地持久化**：Android Room (SQLite)
- **多线程/异步**：Kotlin Coroutines (协程) + Flow
- **生命周期收集**：`collectAsStateWithLifecycle()` 防止内存泄露
- **系统适配**：Android 13+ 细粒度媒体读取权限，向下兼容标准外部存储读取。

---

## 🌐 线上隐私政策服务

本软件遵守极严苛的个人信息合规标准。我们已部署相应的隐私协议及服务条款网页：

* **官方隐私政策页面**：[https://geeklx.github.io/geeklx.github.com/privacy/privacy_policy_index.html](https://geeklx.github.io/geeklx.github.com/privacy/privacy_policy_index.html)
* **用户服务协议页面**：[https://geeklx.github.io/geeklx.github.com/privacy/privacy_policy_index.html (Tab: 用户协议)](https://geeklx.github.io/geeklx.github.com/privacy/privacy_policy_index.html)

---

## 📦 编译与打包构建

### 1. 克隆/拉取代码仓库
```bash
git clone <your-repository-url>
cd <your-repository-name>
```

### 2. 本地项目编译
在项目根目录下，使用 Gradle 任务编译 debug 的 APK：
```bash
gradle assembleDebug
```

### 3. 运行本地单元测试与 UI 测试 (Robolectric)
```bash
gradle :app:testDebugUnitTest
```

---

## ✉️ 客服与支持

如果您在使用、开发或者审查此应用时遇到任何问题，欢迎通过以下方式与我们取得联系：

- **邮件联系**：[liangxiaogeek6@gmail.com](mailto:liangxiaogeek6@gmail.com)
- **Github 公开页**：[https://github.com/geeklx](https://github.com/geeklx)
