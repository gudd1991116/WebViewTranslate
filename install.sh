#!/bin/bash

# Android WebView翻译应用安装脚本

echo "开始构建Android应用..."

# 检查并下载Gradle Wrapper（如果需要）
if [ ! -f "gradle/wrapper/gradle-wrapper.jar" ]; then
    echo "Gradle Wrapper未找到，正在下载..."
    mkdir -p gradle/wrapper
    curl -L -o gradle/wrapper/gradle-wrapper.jar https://raw.githubusercontent.com/gradle/gradle/v8.0.0/gradle/wrapper/gradle-wrapper.jar 2>/dev/null || {
        echo "自动下载失败，请手动运行: gradle wrapper"
        echo "或者从 https://github.com/gradle/gradle/releases 下载gradle-wrapper.jar"
        exit 1
    }
fi

# 检查是否已连接设备
echo "检查连接的设备..."
if ! command -v adb &> /dev/null; then
    echo "警告: 未找到adb命令，请确保Android SDK已安装并配置到PATH"
    echo "将只构建APK，不进行安装"
    INSTALL_ONLY=false
else
    adb devices
    INSTALL_ONLY=true
fi

# 构建APK
echo "正在构建APK..."
./gradlew assembleDebug

if [ $? -eq 0 ]; then
    echo "构建成功！"
    APK_PATH="app/build/outputs/apk/debug/app-debug.apk"
    
    if [ "$INSTALL_ONLY" = true ]; then
        # 安装到手机
        echo "正在安装到手机..."
        adb install -r "$APK_PATH"
        
        if [ $? -eq 0 ]; then
            echo "安装成功！"
            echo "应用已安装到您的手机，可以在应用列表中找到'WebView翻译'"
        else
            echo "安装失败，请确保："
            echo "1. 手机已通过USB连接"
            echo "2. 已开启USB调试"
            echo "3. 已授权此电脑进行USB调试"
            echo ""
            echo "APK文件位置: $APK_PATH"
            echo "您可以手动安装此APK文件"
        fi
    else
        echo "APK文件位置: $APK_PATH"
        echo "您可以手动安装此APK文件到手机"
    fi
else
    echo "构建失败，请检查错误信息"
    echo "确保已安装Android SDK和配置JAVA_HOME"
fi

