# 腕上课程表同步器

![Android](https://img.shields.io/badge/Android-%E2%89%A58.1-blue?style=flat&logo=android&logoColor=%233DDC84&labelColor=%2302303A)
![Gradle](https://img.shields.io/badge/Gradle-9.1.0-blue?style=flat&logo=gradle&labelColor=%2302303A)
![Jetpack Compose](https://img.shields.io/badge/Jetpack_Compose-1.10.5-blue?style=flat&logo=jetpackcompose&logoColor=%234285F4&labelColor=%2302303A)

## 构建命令
> 项目已配置 ABI 分包（`armeabi-v8a`、`arm64-v7a`、`x86`、`x86_64`）

- 清空缓存
  ```powershell
  .\gradlew.bat clean
  ```
- 仅构建 Debug：
  ```powershell
  .\gradlew.bat :app:assembleDebug
  ```
- 仅构建 Release：
  ```powershell
  .\gradlew.bat :app:assembleRelease
  ```
- 产物目录：
  - Debug：`app/build/outputs/apk/debug/`
  - Release：`app/build/outputs/apk/release/`

## 相关仓库
- [腕上课程表](https://github.com/Jursin/Schedule-Vela)

## 文档
- [使用文档](https://sgschedule.jursin.top/guide/user/band-edition.html)

## 许可协议
[MIT](LICENSE)

## 感谢
参考 [leset0ng/BandTOTP-Android](https://github.com/leset0ng/BandTOTP-Android)