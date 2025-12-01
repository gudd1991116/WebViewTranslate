# 构建和安装说明

## 方法1：使用安装脚本（推荐）

```bash
chmod +x install.sh
./install.sh
```

## 方法2：手动步骤

### 1. 初始化Gradle Wrapper（如果还没有）

如果gradle/wrapper/gradle-wrapper.jar不存在，需要先初始化：

```bash
# 如果有gradle命令
gradle wrapper --gradle-version=8.0

# 或者手动下载gradle-wrapper.jar到gradle/wrapper/目录
```

### 2. 构建APK

```bash
./gradlew assembleDebug
```

### 3. 安装到手机

确保手机已连接并开启USB调试：

```bash
# 检查设备
adb devices

# 安装APK
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 4. 或者手动安装APK

APK文件位置：`app/build/outputs/apk/debug/app-debug.apk`

可以将此文件传输到手机并手动安装。

## 前提条件

- Java JDK 8或更高版本
- Android SDK已安装
- 环境变量配置：
  - `JAVA_HOME` 指向JDK目录
  - `ANDROID_HOME` 指向Android SDK目录（可选）
  - `PATH` 包含 `$ANDROID_HOME/platform-tools`（用于adb命令）

## 常见问题

1. **找不到gradle-wrapper.jar**
   - 手动下载：https://github.com/gradle/gradle/releases
   - 或运行：`gradle wrapper`

2. **构建失败：找不到Android SDK**
   - 确保已安装Android SDK
   - 在 `local.properties` 文件中添加：`sdk.dir=/path/to/android/sdk`

3. **adb命令找不到**
   - 确保Android SDK platform-tools在PATH中
   - 或使用完整路径：`$ANDROID_HOME/platform-tools/adb`

