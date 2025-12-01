# WebView翻译应用

这是一个Android应用，可以在WebView中加载网页并对页面内容进行实时翻译。应用支持自动检测页面DOM变化，并对新添加的内容进行自动翻译。

## 功能特性

### 核心功能
- ✅ **网页浏览**：在WebView中加载和浏览网页
- ✅ **页面翻译**：将页面文本内容翻译成目标语言
- ✅ **原始内容保存**：自动保存页面原始内容，支持一键恢复
- ✅ **动态内容翻译**：自动监听DOM变化，实时翻译新添加的内容
- ✅ **智能去重**：防止重复翻译相同内容
- ✅ **防抖机制**：优化性能，避免频繁触发翻译

### 翻译支持
- 文本节点翻译
- HTML属性翻译（placeholder、title、alt）
- 自动过滤纯数字文本
- 批量翻译优化

## 项目结构

```
app/src/main/java/com/example/webviewtranslate/
├── MainActivity.kt          # 主Activity，处理UI和业务逻辑
├── TranslateService.kt     # 翻译服务，处理批量翻译
└── WebViewScripts.kt       # JavaScript脚本集合，注入到WebView
```

## 构建和安装

### 方法1：使用安装脚本（推荐）

```bash
chmod +x install.sh
./install.sh
```

### 方法2：手动构建和安装

1. **构建APK**：
```bash
./gradlew assembleDebug
```

2. **安装到手机**：
```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 前提条件

- Android SDK已安装
- Gradle已配置
- 手机已通过USB连接
- 已开启USB调试模式
- Android 7.0 (API 24) 或更高版本

## 使用方法

### 基本操作

1. **打开应用**
   - 应用启动后会自动加载默认网页（百度贴吧）

2. **翻译页面**
   - 点击"翻译成随机文本"按钮
   - 应用会收集页面所有文本内容
   - 调用翻译服务进行批量翻译
   - 将翻译结果应用到页面

3. **恢复原始内容**
   - 点击"恢复"按钮
   - 页面内容会恢复到翻译前的状态

4. **刷新页面**
   - 点击"刷新"按钮
   - 重新加载当前页面
   - 如果之前处于翻译状态，新页面会自动翻译

### 动态内容翻译

应用会自动监听页面DOM变化：

1. **自动监听**
   - 页面加载完成后，自动启动DOM变化监听器
   - 使用MutationObserver监听页面变化

2. **自动翻译**
   - 当检测到新内容添加时，自动收集新文本
   - 如果页面处于翻译状态，会自动翻译新内容
   - 使用防抖机制，等待DOM稳定后再处理（800ms）

3. **智能过滤**
   - 自动过滤纯数字文本
   - 自动跳过已翻译的内容
   - 使用已有翻译映射，避免重复翻译

## 技术实现

### 架构设计

```
┌─────────────────┐
│   MainActivity  │  ← UI层，处理用户交互
└────────┬────────┘
         │
         ├──→ TranslateService  ← 翻译服务层
         │    (批量翻译API调用)
         │
         └──→ WebViewScripts   ← JavaScript脚本层
              (注入到WebView)
                  │
                  ├──→ 保存原始内容
                  ├──→ 收集文本
                  ├──→ 应用翻译
                  ├──→ 恢复原始内容
                  └──→ DOM变化监听
```

### 核心技术

- **WebView + JavaScript注入**：通过JavaScript接口实现Android与WebView的双向通信
- **MutationObserver**：监听DOM变化，实现动态内容检测
- **WeakMap存储**：使用WeakMap保存原始内容，避免内存泄漏
- **防抖机制**：JavaScript端（800ms）+ Android端（1000ms）双重防抖
- **去重机制**：使用MD5 hash去重，防止重复处理
- **协程异步处理**：使用Kotlin协程处理翻译请求

### JavaScript脚本说明

应用使用多个JavaScript脚本实现不同功能：

1. **getSaveOriginalContentScript()**：保存页面原始内容到WeakMap
2. **getCollectTextsScript()**：收集需要翻译的文本（过滤纯数字）
3. **getApplyTranslationScript()**：应用翻译结果到页面
4. **getRestoreOriginalPageScript()**：恢复页面原始内容
5. **getInjectTranslationScript()**：注入翻译功能核心脚本
6. **getWebpageContentChangedListenerScript()**：DOM变化监听脚本
7. **getApplyTranslationsToNewContentScript()**：应用翻译到新内容

## 配置说明

### 翻译服务配置

在 `TranslateService.kt` 中可以配置：

```kotlin
// 翻译API地址
private const val TRANSLATE_API_URL = "https://api.example.com/translate"

// 是否使用模拟模式（用于测试）
private const val USE_MOCK_MODE = true
```

### 防抖时间配置

在 `MainActivity.kt` 中可以调整防抖延迟：

```kotlin
// DOM变化防抖延迟（毫秒）
private val DOM_CHANGE_DEBOUNCE_DELAY = 1000L
```

在 `WebViewScripts.kt` 中JavaScript端的防抖时间：

```javascript
// 如果800ms内没有新变化，认为DOM已稳定
if (timeSinceLastChange >= 800) {
    // 处理变化
}
```

## 注意事项

### ⚠️ 重要提示

1. **翻译状态管理**
   - 必须先点击"翻译成随机文本"按钮，将 `isPageTranslated` 设置为 `true`
   - 只有处于翻译状态时，DOM变化才会触发自动翻译
   - 页面刷新后，如果之前处于翻译状态，新页面会自动翻译

2. **网络权限**
   - 应用需要网络权限以加载网页和调用翻译API
   - 确保设备已连接网络

3. **性能考虑**
   - DOM监听会监听整个页面树（`subtree: true`），可能影响性能
   - 防抖机制可以缓解性能问题
   - 建议在低端设备上适当增加防抖延迟时间

4. **内存管理**
   - 使用WeakMap存储原始内容，节点被回收后映射也会消失
   - 已处理的变化hash集合有大小限制（100个），防止内存泄漏
   - 页面刷新时会清理相关状态

5. **翻译限制**
   - 每次最多发送200个文本进行翻译
   - 纯数字文本会被自动过滤
   - 已翻译的内容不会重复翻译

6. **兼容性**
   - 支持HTTP和HTTPS网页
   - 需要Android 7.0 (API 24) 或更高版本
   - 某些网站可能有CSP（内容安全策略）限制，可能影响JavaScript注入

### 🔧 调试建议

1. **查看日志**
   - 使用 `adb logcat | grep Translate` 查看应用日志
   - 使用 `adb logcat | grep TranslateA` 查看详细的变化信息
   - 使用 `view_logs.sh` 脚本查看日志

2. **常见问题排查**
   - 如果翻译不生效，检查 `isPageTranslated` 状态
   - 如果DOM变化未触发，检查 `window.AndroidTranslate.onDomChanged` 是否存在
   - 如果重复翻译，检查防抖和去重机制是否正常工作

3. **性能优化**
   - 如果页面变化频繁，可以增加防抖延迟时间
   - 如果内存占用过高，可以减少已处理hash集合的大小限制

## 开发说明

### 接入真实翻译API

1. 修改 `TranslateService.kt` 中的 `USE_MOCK_MODE = false`
2. 配置真实的翻译API地址
3. 根据API文档修改请求和响应解析逻辑

### 自定义翻译逻辑

可以在 `TranslateService.realTranslate()` 方法中实现自定义翻译逻辑：

```kotlin
private suspend fun realTranslate(texts: List<String>): Map<String, String> {
    // 实现你的翻译逻辑
    // 返回 Map<原文, 译文>
}
```

### 扩展功能

- 支持多语言翻译目标选择
- 添加翻译历史记录
- 支持离线翻译
- 添加翻译质量评估

## 常见问题

### Q: 为什么新添加的内容没有被翻译？

A: 请检查以下几点：
1. 是否已点击"翻译成随机文本"按钮（`isPageTranslated` 必须为 `true`）
2. 新内容是否被过滤（纯数字或已翻译内容会被跳过）
3. DOM变化监听器是否正常启动

### Q: 为什么会出现重复翻译？

A: 应用已实现防抖和去重机制。如果仍出现重复，可能是：
1. 防抖时间设置过短
2. hash计算出现问题
3. 多个监听器被注册

### Q: 翻译后如何恢复原始内容？

A: 点击"恢复"按钮即可恢复。应用使用WeakMap保存原始内容，即使页面结构变化也能正确恢复。

### Q: 支持哪些网站？

A: 理论上支持所有网站，但某些网站可能有：
- CSP限制（可能阻止JavaScript注入）
- 动态加载内容（需要等待DOM稳定）
- 特殊的安全策略

## 许可证

本项目仅供学习和研究使用。

## 更新日志

### v1.0
- ✅ 基础翻译功能
- ✅ DOM变化监听
- ✅ 防抖和去重机制
- ✅ 原始内容保存和恢复

---

**提示**：如有问题或建议，请查看日志文件或联系开发者。
