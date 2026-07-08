# AGENTS.md

## 项目概述

Android 证件拍照库，支持身份证正反面、营业执照横版/竖版的拍摄与裁剪。使用已废弃的 `android.hardware.Camera` API（Camera1）。

## 模块结构

- `certificateCamera/` — 核心库模块（`com.android.library`），包名 `win.smartown.android.library.certificateCamera`
- `app/` — Demo 应用模块，依赖 `:certificateCamera`

## 构建与运行

```bash
./gradlew :certificateCamera:assembleRelease   # 构建库
./gradlew :app:assembleDebug                   # 构建 Demo
./gradlew clean                                 # 清理
```

- Gradle 插件版本：`7.3.1`，`compileSdkVersion 28`，`minSdkVersion 14`
- 依赖：`com.android.support:appcompat-v7:28.0.0`（使用旧版 support 库，非 AndroidX）

## 关键注意事项

- **无测试**：项目中无任何单元测试或仪器测试目录
- **Camera1 API**：使用已废弃的 `android.hardware.Camera`，非 Camera2/X
- **非 AndroidX**：项目使用旧版 support 库，迁移需运行 `Refactor > Migrate to AndroidX`
- **横屏为主**：`CameraActivity` 默认横屏，仅竖版营业执照为竖屏
- **文件输出**：拍摄图片保存到 `getExternalCacheDir()`，裁剪文件命名格式为 `{type}Crop.jpg`
- **库调用入口**：`CameraActivity.openCertificateCamera(activity, type)`，结果通过 `onActivityResult` + `CameraActivity.getResult(data)` 获取文件路径