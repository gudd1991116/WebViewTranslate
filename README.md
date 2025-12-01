# WebView翻译应用

这是一个Android应用，可以在WebView中加载网页并对页面内容进行翻译。

## 功能特性

- 在WebView中加载和浏览网页
- 将页面内容翻译成数字（ASCII码）
- 将页面内容翻译成随机文本
- 支持页面刷新和返回导航

## 构建和安装

### 方法1：使用安装脚本（推荐）

```bash
chmod +x install.sh
./install.sh
```

### 方法2：手动构建和安装

1. 构建APK：
```bash
./gradlew assembleDebug
```

2. 安装到手机：
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 前提条件

- Android SDK已安装
- Gradle已配置
- 手机已通过USB连接
- 已开启USB调试模式

## 使用方法

1. 打开应用后，WebView会加载默认网页
2. 点击"翻译成数字"按钮，将页面文本转换为ASCII码
3. 点击"翻译成随机文本"按钮，将页面文本转换为随机中文词汇
4. 点击"刷新"按钮重新加载页面

## 技术实现

- 使用WebView加载网页
- 通过JavaScript注入实现页面内容翻译
- 支持文本节点和HTML属性的翻译
- 使用ViewBinding进行视图绑定

## 注意事项

- 当前使用临时翻译方案（数字或随机文本），可以后续接入真实的翻译API
- 需要网络权限以加载网页
- 支持HTTP和HTTPS网页

# WebViewTranslate
