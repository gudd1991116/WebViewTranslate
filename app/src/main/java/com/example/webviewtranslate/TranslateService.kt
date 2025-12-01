package com.example.webviewtranslate

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TranslateService {
    
    companion object {
        private const val TAG = "TranslateService"
        // 模拟翻译接口URL（实际使用时替换为真实接口）
        private const val TRANSLATE_API_URL = "https://api.example.com/translate"
        
        // 如果服务端接口未就绪，使用模拟模式
        private const val USE_MOCK_MODE = true
    }
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    /**
     * 批量翻译文本
     * @param texts 需要翻译的文本列表
     * @return 翻译结果Map，key为原文，value为翻译后的文本
     */
    suspend fun translateBatch(texts: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        Log.d(TAG, "========== 开始批量翻译 ==========")
        Log.d(TAG, "待翻译文本数量: ${texts.size}")
        
        if (texts.isEmpty()) {
            Log.w(TAG, "待翻译文本列表为空")
            return@withContext emptyMap()
        }
        
        val result = if (USE_MOCK_MODE) {
            // 模拟模式：直接返回模拟翻译结果
            mockTranslate(texts)
        } else {
            // 真实模式：调用服务端接口
            realTranslate(texts)
        }
        
        Log.d(TAG, "========== 翻译完成 ==========")
        Log.d(TAG, "翻译结果数量: ${result.size}")
        result.forEach { (original, translated) ->
//            Log.d(TAG, "原文: $original" + "======> 翻译: $translated")
        }
        Log.d(TAG, "==============================")
        
        result
    }
    
    /**
     * 模拟翻译（用于测试）
     */
    private fun mockTranslate(texts: List<String>): Map<String, String> {
        Log.d(TAG, "使用模拟翻译模式")
        val result = mutableMapOf<String, String>()
        
        texts.forEach { text ->
            // 模拟随机文本翻译
            val randomWords = listOf("翻译", "文本", "内容", "数据", "信息", "语言", "转换", "处理")
            val translated = text.split(Regex("[\\s，。！？；：、\\n\\r]+"))
                .filter { it.isNotBlank() }
                .joinToString("") { randomWords.random() }
            result[text] = translated
        }
        
        // 模拟网络延迟
        Thread.sleep(500)
        
        return result
    }
    
    /**
     * 真实翻译：调用服务端接口
     */
    private suspend fun realTranslate(texts: List<String>): Map<String, String> = withContext(Dispatchers.IO) {
        try {
            // 构建请求JSON
            val requestJson = JSONObject().apply {
                put("texts", JSONArray().apply {
                    texts.forEach { put(it) }
                })
            }
            
            Log.d(TAG, "发送翻译请求到: $TRANSLATE_API_URL")
            Log.d(TAG, "请求数据: ${requestJson.toString()}")
            
            // 创建请求
            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())
            val request = Request.Builder()
                .url(TRANSLATE_API_URL)
                .post(requestBody)
                .addHeader("Content-Type", "application/json")
                .build()
            
            // 执行请求
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            Log.d(TAG, "响应状态码: ${response.code}")
            Log.d(TAG, "响应数据: $responseBody")
            
            if (!response.isSuccessful) {
                Log.e(TAG, "翻译请求失败: ${response.code} - ${response.message}")
                return@withContext emptyMap()
            }
            
            // 解析响应
            val responseJson = JSONObject(responseBody ?: "{}")
            val translations = responseJson.getJSONArray("translations")
            
            val result = mutableMapOf<String, String>()
            for (i in 0 until translations.length()) {
                val item = translations.getJSONObject(i)
                val original = item.getString("original")
                val translated = item.getString("translated")
                result[original] = translated
            }
            
            result
        } catch (e: Exception) {
            Log.e(TAG, "翻译请求异常", e)
            emptyMap()
        }
    }
}

