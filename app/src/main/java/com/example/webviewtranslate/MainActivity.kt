package com.example.webviewtranslate

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.webviewtranslate.databinding.ActivityMainBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.security.MessageDigest
import java.util.Random

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val random = Random()
    private var currentUrl: String? = null
    private var isPageTranslated = false
    private val translateService = TranslateService()
    private val coroutineScope = CoroutineScope(Dispatchers.Main)
    private var translationMap: Map<String, String> = emptyMap() // 保存翻译映射：原始文本 -> 翻译文本
    private var reverseTranslationMap: Map<String, String> = emptyMap() // 反向映射：翻译文本 -> 原始文本
    
    // DOM变化防抖和去重相关
    private val domChangeHandler = Handler(Looper.getMainLooper())
    private var domChangeRunnable: Runnable? = null
    private val processedChangeHashes = mutableSetOf<String>() // 已处理的变化hash集合
    private var isProcessingDomChange = false // 是否正在处理DOM变化
    private val DOM_CHANGE_DEBOUNCE_DELAY = 1000L // 防抖延迟时间（毫秒）
    private val MAX_PROCESSED_HASHES = 100 // 最多保存的hash数量（防止内存泄漏）

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupWebView()
        setupButtons()
    }

    private fun setupWebView() {
        binding.webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(
                view: WebView?,
                url: String?,
                favicon: android.graphics.Bitmap?
            ) {
                super.onPageStarted(view, url, favicon)
                Log.d("Translate", "========== 页面开始加载 ==========")
                Log.d("Translate", "URL: $url")
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                Log.d("Translate", "========== 页面加载完成 ==========")
                Log.d("Translate", "URL: $url")
                currentUrl = url
                // 注意：保持翻译状态，这样新页面也会自动翻译
                // 注入翻译功能的JavaScript代码
                injectTranslationScript()
                // 注入页面内容变化监听（使用MutationObserver，更准确）
                webpageContentChangedListener()
                // 页面加载完成后，延迟保存原始内容（确保DOM完全渲染）
                binding.webView.postDelayed({
                    saveOriginalPageContent()
                    // 如果当前处于翻译状态，自动翻译新页面内容
                    if (isPageTranslated) {
                        Log.d("Translate", "[页面加载] 当前处于翻译状态，自动翻译新页面内容")
                        translatePage()
                    }
                }, 500)
            }

            override fun onPageCommitVisible(view: WebView?, url: String?) {
                super.onPageCommitVisible(view, url)
                Log.d("Translate", "========== 页面内容可见 ==========")
                Log.d("Translate", "URL: $url")
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: android.webkit.WebResourceRequest?
            ): Boolean {
                val url = request?.url?.toString()
                if (url != null && url != currentUrl) {
                    Log.d("Translate", "========== URL变化（可能跳转） ==========")
                    Log.d("Translate", "旧URL: $currentUrl")
                    Log.d("Translate", "新URL: $url")
                }
                return super.shouldOverrideUrlLoading(view, request)
            }

            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                return super.shouldInterceptRequest(view, request)
            }
        }

        // 添加JavaScript接口
        binding.webView.addJavascriptInterface(WebAppInterface(), "AndroidTranslate")

        // 加载百度网页
        binding.webView.loadUrl("https://tieba.baidu.com/f?kw=%E6%84%9F%E6%82%9F&fr=fenter&prequery=%E6%84%9F%E6%82%9F%E5%8D%81%E4%BA%94%E4%BA%94%E8%A7%84%E5%88%92%E5%BB%BA%E8%AE%AE%E9%87%8C%E7%9A%84%E6%B0%91%E7%94%9F%E6%B8%A9%E5%BA%A6")
    }

    private fun setupButtons() {
        binding.btnTranslateRandom.setOnClickListener {
            translatePage()
        }

        binding.btnReload.setOnClickListener {
            reloadPage()
        }

        binding.btnRestore.setOnClickListener {
            restoreOriginalPage()
        }
    }

    private fun reloadPage() {
        // 清除缓存并重新加载当前页面
        currentUrl?.let { url ->
            binding.webView.clearCache(true)
            binding.webView.loadUrl(url)
        } ?: run {
            // 如果没有当前URL，重新加载默认URL
            binding.webView.clearCache(true)
            binding.webView.loadUrl("https://tieba.baidu.com/f?kw=%E6%84%9F%E6%82%9F&fr=fenter&prequery=%E6%84%9F%E6%82%9F%E5%8D%81%E4%BA%94%E4%BA%94%E8%A7%84%E5%88%92%E5%BB%BA%E8%AE%AE%E9%87%8C%E7%9A%84%E6%B0%91%E7%94%9F%E6%B8%A9%E5%BA%A6")
        }
    }

    private fun saveOriginalPageContent() {
        Log.d("Translate", "========== 保存页面原始内容 ==========")
        // 延迟一点时间，确保页面完全渲染完成
        binding.webView.postDelayed({
            val saveScript = WebViewScripts.getSaveOriginalContentScript()
            binding.webView.evaluateJavascript(saveScript) { result ->
                val count = result?.removeSurrounding("\"")?.toIntOrNull() ?: 0
                Log.d("Translate", "原始内容保存完成，保存了 $count 个节点")
            }
        }, 500) // 延迟500ms，确保页面完全渲染
    }

    private fun translatePage() {
        isPageTranslated = true
        Log.d("Translate", "========== 开始翻译 ==========")
        Log.d("Translate", "翻译状态已设置 - isPageTranslated: $isPageTranslated")

        // 先收集所有需要翻译的文本（过滤纯数字）
        val collectScript = WebViewScripts.getCollectTextsScript()

        binding.webView.evaluateJavascript(collectScript) { result ->
            try {
//                Log.d("TranslateA", "收集脚本返回结果: $result")

                // 处理JavaScript返回的JSON字符串（可能包含转义字符）
                var jsonString = result?.removeSurrounding("\"") ?: "[]"
                // 处理转义字符
                jsonString = jsonString.replace("\\\"", "\"")
                    .replace("\\n", "\n")
                    .replace("\\r", "\r")
                    .replace("\\t", "\t")
                    .replace("\\\\", "\\")

                Log.d("TranslateA", "解析后的JSON字符串: $jsonString")

                val jsonArray = JSONArray(jsonString)
                val texts = mutableListOf<String>()
                for (i in 0 until jsonArray.length()) {
                    val text = jsonArray.getString(i)
                    texts.add(text)
//                    Log.d("TranslateA", "收集到文本[$i]: $text")
                }

                Log.d("TranslateA", "========== 收集完成 ==========")
                Log.d("TranslateA", "共收集到 ${texts.size} 个待翻译文本")

                if (texts.isEmpty()) {
                    Log.w("TranslateA", "没有收集到需要翻译的文本")
                    Toast.makeText(this@MainActivity, "没有找到需要翻译的内容", Toast.LENGTH_SHORT)
                        .show()
                    return@evaluateJavascript
                }

                // 批量翻译
                coroutineScope.launch {
                    Log.d("TranslateA", "开始调用翻译服务...")
                    val translations = translateService.translateBatch(texts)

                    if (translations.isEmpty()) {
                        Log.e("Translate", "翻译服务返回空结果")
                        Toast.makeText(
                            this@MainActivity,
                            "翻译失败：服务端未返回结果",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@launch
                    }

                    // 保存翻译映射（用于恢复和避免重复翻译）
                    translationMap = translations
                    reverseTranslationMap = translations.entries.associate { (original, translated) ->
                        translated to original
                    }
                    Log.d("TranslateA", "已保存翻译映射，共 ${translationMap.size} 条")
                    Log.d("TranslateA", "已保存反向映射，共 ${reverseTranslationMap.size} 条")
                    Log.d("Translate", "反向映射用于识别已翻译内容，避免重复翻译")

                    Log.d("TranslateA", "========== 准备应用翻译结果 ==========")
                    Log.d("TranslateA", "翻译结果数量: ${translations.size}")

                    // 将翻译结果转换为JavaScript对象字符串
                    val translationsJsonString =
                        translations.entries.joinToString(", ") { (original, translated) ->
                            val escapedOriginal =
                                original.replace("\"", "\\\"").replace("\n", "\\n")
                                    .replace("\r", "\\r")
                            val escapedTranslated =
                                translated.replace("\"", "\\\"").replace("\n", "\\n")
                                    .replace("\r", "\\r")
                            "\"$escapedOriginal\": \"$escapedTranslated\""
                        }

                    Log.d("TranslateA", "翻译结果转换为脚本再插入到网页中：$translationsJsonString")
                    val replaceScript = WebViewScripts.getApplyTranslationScript(translationsJsonString)

                    binding.webView.evaluateJavascript(replaceScript) { result ->
                        Log.d("TranslateA", "========== 翻译完成 ==========")
                        Log.d("TranslateA", "翻译结果已应用到页面")
                        Toast.makeText(this@MainActivity, "翻译完成", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Log.e("Translate", "翻译过程出错", e)
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "翻译失败: ${e.message}", Toast.LENGTH_SHORT)
                    .show()
            }
        }
    }

    private fun restoreOriginalPage() {
        if (!isPageTranslated) {
            Log.d("Translate", "页面未被翻译，重新加载URL")
            currentUrl?.let {
                binding.webView.loadUrl(it)
            } ?: run {
                binding.webView.loadUrl("https://tieba.baidu.com/f?kw=%E6%84%9F%E6%82%9F&fr=fenter&prequery=%E6%84%9F%E6%82%9F%E5%8D%81%E4%BA%94%E4%BA%94%E8%A7%84%E5%88%92%E5%BB%BA%E8%AE%AE%E9%87%8C%E7%9A%84%E6%B0%91%E7%94%9F%E6%B8%A9%E5%BA%A6")
            }
            return
        }

        Log.d("Translate", "========== 开始恢复原始页面 ==========")

        // 使用WeakMap中保存的原始内容直接恢复
        val restoreScript = WebViewScripts.getRestoreOriginalPageScript()

        binding.webView.evaluateJavascript(restoreScript) { result ->
            val count = result?.removeSurrounding("\"")?.toIntOrNull() ?: 0
            Log.d("Translate", "========== 恢复完成 ==========")
            Log.d("Translate", "已恢复 $count 个节点")
            // 清除翻译状态（恢复到原始内容后，不再保持翻译状态）
            isPageTranslated = false
            translationMap = emptyMap()
            reverseTranslationMap = emptyMap()

            // 清除JavaScript中的翻译状态
            binding.webView.evaluateJavascript(WebViewScripts.getClearTranslationStateScript(), null)

            if (count > 0) {
                Toast.makeText(
                    this@MainActivity,
                    "已恢复到原始内容（恢复了 $count 个节点）",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                Toast.makeText(
                    this@MainActivity,
                    "恢复完成，但未找到需要恢复的内容",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun injectTranslationScript() {
        val script = WebViewScripts.getInjectTranslationScript()
        binding.webView.evaluateJavascript(script, null)
    }

    fun webpageContentChangedListener() {
        val script = WebViewScripts.getWebpageContentChangedListenerScript()
        binding.webView.evaluateJavascript(script) { result ->
            Log.d("Translate", "[DOM监听] 页面内容变化监听器注入完成")
        }
    }
    
    private fun applyTranslationsToNewContent(translations: Map<String, String>) {
        if (translations.isEmpty()) {
            Log.d("Translate", "[DOM变化] 没有翻译需要应用")
            return
        }
        
        Log.d("Translate", "[DOM变化] 开始应用 ${translations.size} 个翻译")
        
        // 构建JavaScript代码来应用翻译
        val translationsJsonString = translations.entries.joinToString(", ") { (original, translated) ->
            val escapedOriginal = original.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            val escapedTranslated = translated.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r")
            "\"$escapedOriginal\": \"$escapedTranslated\""
        }
        
        val applyScript = WebViewScripts.getApplyTranslationsToNewContentScript(translationsJsonString)
        
        binding.webView.evaluateJavascript(applyScript) { result ->
            val count = result?.removeSurrounding("\"")?.toIntOrNull() ?: 0
            Log.d("Translate", "[DOM变化] 翻译应用完成，共应用 $count 个翻译")
        }
    }

    /**
     * 计算变化信息的hash值（用于去重）
     */
    private fun calculateChangeHash(changeInfoJson: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val hashBytes = md.digest(changeInfoJson.toByteArray())
            hashBytes.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            // 如果计算hash失败，使用原始字符串的hashCode
            changeInfoJson.hashCode().toString()
        }
    }
    
    /**
     * 实际处理DOM变化的逻辑
     */
    private fun processDomChange(changeInfoJson: String) {
        try {
            if (changeInfoJson.isEmpty()) {
                Log.d("Translate", "[DOM变化] 收到空的变化信息")
                return
            }
            
            Log.d("Translate", "[DOM变化] ========== 开始处理DOM变化 ==========")
            Log.d("TranslateA", "[DOM变化] 变化信息: $changeInfoJson")
            
            val changeInfo = org.json.JSONObject(changeInfoJson)
            val newNodesCount = changeInfo.optInt("newNodesCount", 0)
            val newTextsCount = changeInfo.optInt("newTextsCount", 0)
            val newTextsArray = changeInfo.optJSONArray("newTexts")
            
            Log.d("TranslateA", "[DOM变化] 新节点数量: $newNodesCount")
            Log.d("TranslateA", "[DOM变化] 新文本数量: $newTextsCount")
            
            if (newTextsCount > 0 && newTextsArray != null) {
                val texts = mutableListOf<String>()
                for (i in 0 until newTextsArray.length()) {
                    val text = newTextsArray.getString(i)
                    if (text.isNotBlank()) {
                        texts.add(text)
                    }
                }
                
                Log.d("Translate", "[DOM变化] 提取到 ${texts.size} 个文本需要处理")
                
                // 如果当前处于翻译状态，自动翻译新内容
                Log.d("Translate", "[DOM变化] 检查翻译状态 - isPageTranslated: $isPageTranslated")
                Log.d("Translate", "[DOM变化] translationMap大小: ${translationMap.size}, reverseTranslationMap大小: ${reverseTranslationMap.size}")
                
                if (isPageTranslated) {
                    Log.d("Translate", "[DOM变化] ========== 当前处于翻译状态，开始翻译新内容 ==========")
                    
                    // 过滤文本：
                    // 1. 排除纯数字文本
                    // 2. 排除已经是翻译后的内容（通过反向映射检查）
                    val originalTexts = texts.filter { text ->
                        // 检查是否是纯数字（去除空白后，全是数字相关字符）
                        val trimmed = text.trim()
                        if (trimmed.isNotEmpty() && trimmed.matches(Regex("^[\\d\\s.,\\-+]+$"))) {
                            Log.d("Translate", "[DOM变化] 跳过纯数字文本: ${text.substring(0, minOf(30, text.length))}")
                            return@filter false
                        }
                        
                        // 检查是否已经是翻译后的内容
                        if (reverseTranslationMap.containsKey(text)) {
                            Log.d("Translate", "[DOM变化] 跳过已翻译文本: ${text.substring(0, minOf(30, text.length))}")
                            return@filter false
                        }
                        
                        true
                    }
                    
                    Log.d("Translate", "[DOM变化] 过滤后原始文本数量: ${originalTexts.size} (总文本: ${texts.size})")
                    
                    if (originalTexts.isEmpty()) {
                        Log.d("Translate", "[DOM变化] 所有文本都已被过滤（纯数字或已翻译），无需处理")
                        Log.d("Translate", "[DOM变化] ⚠️ 因此不会调用 translateBatch，不会打印'开始批量翻译'")
                        return
                    }
                    
                    // 分离已有映射和新文本
                    val existingTranslations = mutableMapOf<String, String>()
                    val textsToTranslate = mutableListOf<String>()
                    
                    originalTexts.forEach { text ->
                        if (translationMap.containsKey(text)) {
                            existingTranslations[text] = translationMap[text]!!
                        } else {
                            textsToTranslate.add(text)
                        }
                    }
                    
                    Log.d("Translate", "[DOM变化] 已有映射数量: ${existingTranslations.size}, 需要新翻译数量: ${textsToTranslate.size}")
                    
                    // 先应用已有映射
                    if (existingTranslations.isNotEmpty()) {
                        Log.d("Translate", "[DOM变化] 应用 ${existingTranslations.size} 个已有翻译")
                        applyTranslationsToNewContent(existingTranslations)
                    }
                    
                    // 翻译新文本
                    if (textsToTranslate.isNotEmpty()) {
                        Log.d("Translate", "[DOM变化] ========== 准备调用 translateBatch，翻译 ${textsToTranslate.size} 个新文本 ==========")
                        coroutineScope.launch {
                            try {
                                val newTranslations = translateService.translateBatch(textsToTranslate)
                                if (newTranslations.isNotEmpty()) {
                                    Log.d("Translate", "[DOM变化] 新翻译完成，共 ${newTranslations.size} 个")
                                    applyTranslationsToNewContent(newTranslations)
                                }
                            } catch (e: Exception) {
                                Log.e("Translate", "[DOM变化] 翻译出错", e)
                            }
                        }
                    } else {
                        Log.d("Translate", "[DOM变化] ⚠️ textsToTranslate 为空（所有文本都在 translationMap 中），不会调用 translateBatch")
                        Log.d("Translate", "[DOM变化] ⚠️ 因此不会打印'开始批量翻译'")
                    }
                } else {
                    Log.d("Translate", "[DOM变化] ⚠️ 当前未处于翻译状态（isPageTranslated=false），只保存原始内容")
                    Log.d("Translate", "[DOM变化] ⚠️ 因此不会调用 translateBatch，不会打印'开始批量翻译'")
                    Log.d("Translate", "[DOM变化] 💡 提示：需要先点击'翻译页面'按钮，将 isPageTranslated 设置为 true")
                }
            }
            
            Log.d("Translate", "[DOM变化] ======================================")
        } catch (e: Exception) {
            Log.e("Translate", "[DOM变化] 处理DOM变化时出错", e)
            e.printStackTrace()
        } finally {
            // 标记处理完成
            isProcessingDomChange = false
        }
    }

    inner class WebAppInterface {
        @JavascriptInterface
        fun onDomChanged(changeInfoJson: String?) {
            // 当网页DOM发生变化时，会从这里回调
            runOnUiThread {
                try {
                    if (changeInfoJson.isNullOrEmpty()) {
                        Log.d("Translate", "[DOM变化] 收到空的变化信息")
                        return@runOnUiThread
                    }
                    
                    // 计算变化信息的hash
                    val changeHash = calculateChangeHash(changeInfoJson)
                    
                    // 检查是否已经处理过相同的变化
                    if (processedChangeHashes.contains(changeHash)) {
                        Log.d("Translate", "[DOM变化] ⚠️ 检测到重复的变化信息（hash: $changeHash），跳过处理")
                        return@runOnUiThread
                    }
                    
                    // 检查是否正在处理中
                    if (isProcessingDomChange) {
                        Log.d("Translate", "[DOM变化] ⚠️ 正在处理DOM变化，延迟处理此次变化")
                    }
                    
                    // 清除之前的防抖任务
                    domChangeRunnable?.let { domChangeHandler.removeCallbacks(it) }
                    
                    // 创建新的防抖任务
                    domChangeRunnable = Runnable {
                        // 再次检查是否已处理（防止在防抖期间重复处理）
                        if (processedChangeHashes.contains(changeHash)) {
                            Log.d("Translate", "[DOM变化] ⚠️ 防抖期间检测到重复变化（hash: $changeHash），跳过处理")
                            return@Runnable
                        }
                        
                        // 添加到已处理集合
                        processedChangeHashes.add(changeHash)
                        
                        // 限制集合大小，防止内存泄漏
                        if (processedChangeHashes.size > MAX_PROCESSED_HASHES) {
                            val oldestHash = processedChangeHashes.first()
                            processedChangeHashes.remove(oldestHash)
                            Log.d("Translate", "[DOM变化] 清理旧的hash记录，当前集合大小: ${processedChangeHashes.size}")
                        }
                        
                        // 标记正在处理
                        isProcessingDomChange = true
                        
                        // 实际处理变化
                        processDomChange(changeInfoJson)
                    }
                    
                    // 延迟执行（防抖）
                    domChangeHandler.postDelayed(domChangeRunnable!!, DOM_CHANGE_DEBOUNCE_DELAY)
                    Log.d("Translate", "[DOM变化] 收到DOM变化回调，hash: $changeHash，将在 ${DOM_CHANGE_DEBOUNCE_DELAY}ms 后处理")
                    
                } catch (e: Exception) {
                    Log.e("Translate", "[DOM变化] 处理DOM变化回调时出错", e)
                    e.printStackTrace()
                    isProcessingDomChange = false
                }
            }
        }

        @JavascriptInterface
        fun logMessage(message: String) {
            Log.d("Translate", message)
        }

    }

    override fun onBackPressed() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}




