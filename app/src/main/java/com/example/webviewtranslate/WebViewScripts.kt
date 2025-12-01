package com.example.webviewtranslate

/**
 * WebView JavaScript脚本集合
 * 统一管理所有注入到WebView的JavaScript代码
 */
object WebViewScripts {

    /**
     * 保存页面原始内容的脚本
     */
    fun getSaveOriginalContentScript(): String {
        return """
            javascript:(function() {
                // 初始化WeakMap（如果还没有）
                if (!window.nodeOriginalTexts) {
                    window.nodeOriginalTexts = new WeakMap();
                }
                if (!window.nodeOriginalAttributes) {
                    window.nodeOriginalAttributes = new WeakMap();
                }
                
                var savedCount = 0;
                
                function saveOriginalContent(node) {
                    if (node.nodeType === 3) { // 文本节点
                        const text = node.textContent;
                        if (text && text.trim() !== '') {
                            // 保存原始文本（如果还没有保存过）
                            if (!window.nodeOriginalTexts.has(node)) {
                                window.nodeOriginalTexts.set(node, text);
                                savedCount++;
                            }
                        }
                    } else if (node.nodeType === 1) { // 元素节点
                        if (node.tagName && 
                            (node.tagName.toLowerCase() === 'script' || 
                             node.tagName.toLowerCase() === 'style')) {
                            return;
                        }
                        
                        // 保存原始属性
                        var attrs = {};
                        var hasAttrs = false;
                        if (node.placeholder) {
                            attrs.placeholder = node.placeholder;
                            hasAttrs = true;
                        }
                        if (node.title) {
                            attrs.title = node.title;
                            hasAttrs = true;
                        }
                        if (node.alt) {
                            attrs.alt = node.alt;
                            hasAttrs = true;
                        }
                        if (hasAttrs && !window.nodeOriginalAttributes.has(node)) {
                            window.nodeOriginalAttributes.set(node, attrs);
                            savedCount++;
                        }
                        
                        // 递归处理子节点
                        for (let i = 0; i < node.childNodes.length; i++) {
                            saveOriginalContent(node.childNodes[i]);
                        }
                    }
                }
                saveOriginalContent(document.body);
                console.log('[保存原始内容] 已保存 ' + savedCount + ' 个节点的原始内容');
                return savedCount;
            })();
        """.trimIndent()
    }

    /**
     * 收集需要翻译的文本脚本（过滤纯数字）
     */
    fun getCollectTextsScript(): String {
        return """
            javascript:(function() {
                // 检查是否是纯数字文本
                function isPureNumber(text) {
                    // 去除空白字符后，检查是否全是数字
                    var trimmed = text.trim();
                    if (trimmed === '') return false;
                    // 匹配：全是数字，可能包含小数点、负号、空格、逗号等数字相关字符
                    return /^[\d\s.,\-+]+$/.test(trimmed);
                }
                
                var texts = [];
                function collectTexts(node) {
                    if (node.nodeType === 3) { // 文本节点
                        const text = node.textContent;
                        if (text && text.trim() !== '') {
                            // 过滤纯数字文本
                            if (!isPureNumber(text)) {
                                texts.push(text);
                            }
                        }
                    } else if (node.nodeType === 1) { // 元素节点
                        if (node.tagName && 
                            (node.tagName.toLowerCase() === 'script' || 
                             node.tagName.toLowerCase() === 'style')) {
                            return;
                        }
                        if (node.placeholder && !isPureNumber(node.placeholder)) {
                            texts.push(node.placeholder);
                        }
                        if (node.title && !isPureNumber(node.title)) {
                            texts.push(node.title);
                        }
                        if (node.alt && !isPureNumber(node.alt)) {
                            texts.push(node.alt);
                        }
                        for (let i = 0; i < node.childNodes.length; i++) {
                            collectTexts(node.childNodes[i]);
                        }
                    }
                }
                collectTexts(document.body);
                // 去重
                texts = Array.from(new Set(texts));
                return JSON.stringify(texts);
            })();
        """.trimIndent()
    }

    /**
     * 应用翻译结果的脚本
     */
    fun getApplyTranslationScript(translationsJsonString: String): String {
        return """
            javascript:(function() {
                // 设置标志，暂时禁用MutationObserver回调（避免循环）
                window.isApplyingTranslation = true;
                
                var translations = { $translationsJsonString };
                
                // 将翻译映射保存到全局，供动态内容使用
                window.translations = translations;
                console.log('[应用翻译] 翻译映射已保存到 window.translations，数量: ' + Object.keys(translations).length);
                
                // 验证设置是否成功
                console.log('[应用翻译] 验证 - window.translations存在: ' + (window.translations ? '是' : '否'));
                
                // 打印一些翻译映射示例（用于调试）
                // var sampleKeys = Object.keys(translations).slice(0, 3);
                // sampleKeys.forEach(function(key) {
                //     console.log('[应用翻译] 映射示例: "' + key.substring(0, 30) + '" -> "' + translations[key].substring(0, 30) + '"');
                // });
                
                // 确保WeakMap已初始化（应该在页面加载时已初始化）
                if (!window.nodeOriginalTexts) {
                    window.nodeOriginalTexts = new WeakMap();
                }
                if (!window.nodeOriginalAttributes) {
                    window.nodeOriginalAttributes = new WeakMap();
                }
                
                function replaceTexts(node) {
                    if (node.nodeType === 3) { // 文本节点
                        const originalText = node.textContent;
                        if (originalText && originalText.trim() !== '' && translations[originalText]) {
                            // 确保原始内容已保存（如果还没有保存）
                            if (!window.nodeOriginalTexts.has(node)) {
                                window.nodeOriginalTexts.set(node, originalText);
                            }
                            // 原始内容已在页面加载时保存，这里直接替换
                            const translated = translations[originalText];
                            node.textContent = translated;
                            console.log('[页面替换] 原文: ' + originalText.substring(0, 50) + '==> [页面替换] 翻译: ' + translated.substring(0, 50));
                            // console.log('[页面替换] 翻译: ' + translated.substring(0, 50));
                            if (window.AndroidTranslate && window.AndroidTranslate.logReplace) {
                                window.AndroidTranslate.logReplace(
                                    originalText.substring(0, 200),
                                    translated.substring(0, 200)
                                );
                            }
                        }
                    } else if (node.nodeType === 1) { // 元素节点
                        if (node.tagName && 
                            (node.tagName.toLowerCase() === 'script' || 
                             node.tagName.toLowerCase() === 'style')) {
                            return;
                        }
                        
                        // 替换属性（原始属性已在页面加载时保存）
                        if (node.placeholder && translations[node.placeholder]) {
                            // 确保原始属性已保存
                            if (!window.nodeOriginalAttributes.has(node)) {
                                window.nodeOriginalAttributes.set(node, {
                                    placeholder: node.placeholder,
                                    title: node.title || '',
                                    alt: node.alt || ''
                                });
                            }
                            node.placeholder = translations[node.placeholder];
                            console.log('[属性替换] placeholder: ' + node.placeholder);
                        }
                        if (node.title && translations[node.title]) {
                            if (!window.nodeOriginalAttributes.has(node)) {
                                window.nodeOriginalAttributes.set(node, {
                                    placeholder: node.placeholder || '',
                                    title: node.title,
                                    alt: node.alt || ''
                                });
                            }
                            node.title = translations[node.title];
                            console.log('[属性替换] title: ' + node.title);
                        }
                        if (node.alt && translations[node.alt]) {
                            if (!window.nodeOriginalAttributes.has(node)) {
                                window.nodeOriginalAttributes.set(node, {
                                    placeholder: node.placeholder || '',
                                    title: node.title || '',
                                    alt: node.alt
                                });
                            }
                            node.alt = translations[node.alt];
                            console.log('[属性替换] alt: ' + node.alt);
                        }
                        
                        for (let i = 0; i < node.childNodes.length; i++) {
                            replaceTexts(node.childNodes[i]);
                        }
                    }
                }
                replaceTexts(document.body);
                console.log('[应用翻译] 翻译结果已应用到页面');
            })();
        """.trimIndent()
    }

    /**
     * 恢复原始页面的脚本
     */
    fun getRestoreOriginalPageScript(): String {
        return """
            javascript:(function() {
                var restoredCount = 0;
                
                if (!window.nodeOriginalTexts || !window.nodeOriginalAttributes) {
                    console.error('[恢复] WeakMap未初始化，无法恢复');
                    if (window.AndroidTranslate && window.AndroidTranslate.logReplace) {
                        window.AndroidTranslate.logReplace('WeakMap未初始化', '无法恢复');
                    }
                    return;
                }
                
                console.log('[恢复] 开始恢复页面内容...');
                
                function restoreTexts(node) {
                    if (node.nodeType === 3) { // 文本节点
                        // 从WeakMap中获取原始文本
                        if (window.nodeOriginalTexts.has(node)) {
                            const originalText = window.nodeOriginalTexts.get(node);
                            const currentText = node.textContent;
                            if (originalText !== currentText) {
                                node.textContent = originalText;
                                restoredCount++;
                                console.log('[恢复] 文本节点恢复: ' + currentText.substring(0, 50) + ' -> ' + originalText.substring(0, 50));
                                if (window.AndroidTranslate && window.AndroidTranslate.logReplace) {
                                    window.AndroidTranslate.logReplace(
                                        currentText.substring(0, 200),
                                        originalText.substring(0, 200)
                                    );
                                }
                            }
                        } else {
                            // 如果WeakMap中没有，说明这个节点可能是动态添加的，尝试查找父节点
                            console.log('[恢复] 文本节点未在WeakMap中找到: ' + currentText.substring(0, 30));
                        }
                    } else if (node.nodeType === 1) { // 元素节点
                        if (node.tagName && 
                            (node.tagName.toLowerCase() === 'script' || 
                             node.tagName.toLowerCase() === 'style')) {
                            return;
                        }
                        
                        // 从WeakMap中恢复属性
                        if (window.nodeOriginalAttributes.has(node)) {
                            const originalAttrs = window.nodeOriginalAttributes.get(node);
                            if (originalAttrs.placeholder !== undefined && node.placeholder !== originalAttrs.placeholder) {
                                node.placeholder = originalAttrs.placeholder;
                                restoredCount++;
                                console.log('[恢复] placeholder恢复: ' + originalAttrs.placeholder);
                            }
                            if (originalAttrs.title !== undefined && node.title !== originalAttrs.title) {
                                node.title = originalAttrs.title;
                                restoredCount++;
                                console.log('[恢复] title恢复: ' + originalAttrs.title);
                            }
                            if (originalAttrs.alt !== undefined && node.alt !== originalAttrs.alt) {
                                node.alt = originalAttrs.alt;
                                restoredCount++;
                                console.log('[恢复] alt恢复: ' + originalAttrs.alt);
                            }
                        }
                        
                        for (let i = 0; i < node.childNodes.length; i++) {
                            restoreTexts(node.childNodes[i]);
                        }
                    }
                }
                restoreTexts(document.body);
                console.log('[恢复] 已恢复 ' + restoredCount + ' 个节点');
                return restoredCount;
            })();
        """.trimIndent()
    }

    /**
     * 清除翻译状态的脚本
     */
    fun getClearTranslationStateScript(): String {
        return """
            javascript:(function() {
                window.translations = null;
                console.log('[恢复] 已清除翻译状态和映射');
            })();
        """.trimIndent()
    }

    /**
     * 注入翻译功能的核心脚本
     */
    fun getInjectTranslationScript(): String {
        return """
            (function() {
                // 使用WeakMap存储原始文本内容，直接以节点为key
                var originalTexts = new WeakMap();
                var originalAttributes = new Map();
                
                // 保存单个节点的原始内容
                function saveNodeOriginalContent(node) {
                    if (node.nodeType === 3) { // 文本节点
                        const text = node.textContent;
                        if (text && text.trim() !== '' && !window.nodeOriginalTexts.has(node)) {
                            window.nodeOriginalTexts.set(node, text);
                        }
                    } else if (node.nodeType === 1) { // 元素节点
                        if (node.tagName && 
                            (node.tagName.toLowerCase() === 'script' || 
                             node.tagName.toLowerCase() === 'style')) {
                            return;
                        }
                        var attrs = {};
                        var hasAttrs = false;
                        if (node.placeholder) {
                            attrs.placeholder = node.placeholder;
                            hasAttrs = true;
                        }
                        if (node.title) {
                            attrs.title = node.title;
                            hasAttrs = true;
                        }
                        if (node.alt) {
                            attrs.alt = node.alt;
                            hasAttrs = true;
                        }
                        if (hasAttrs && !window.nodeOriginalAttributes.has(node)) {
                            window.nodeOriginalAttributes.set(node, attrs);
                        }
                    }
                }
                
                // 保存节点的所有子节点的原始内容
                function saveNodeTreeOriginalContent(node) {
                    saveNodeOriginalContent(node);
                    if (node.nodeType === 1) {
                        for (let i = 0; i < node.childNodes.length; i++) {
                            saveNodeTreeOriginalContent(node.childNodes[i]);
                        }
                    }
                }
                
                // 翻译单个节点树（使用全局翻译映射）
                function translateNodeTree(node) {
                    if (!node) {
                        console.log('[translateNodeTree] 节点为空，返回');
                        return;
                    }
                    
                    console.log('[translateNodeTree] 开始翻译节点树');
                    console.log('[translateNodeTree] window.translations存在: ' + (window.translations ? '是，数量: ' + Object.keys(window.translations).length : '否'));
                    
                    // 先保存原始内容
                    saveNodeTreeOriginalContent(node);
                    
                    // 如果有翻译映射，立即翻译新内容
                    if (window.translations) {
                        var translatedCount = 0;
                        // 递归翻译所有子节点
                        function translateNodeRecursive(n) {
                            if (!n) return;
                            
                            if (n.nodeType === 3) { // 文本节点
                                const originalText = n.textContent;
                                if (originalText && originalText.trim() !== '') {
                                    let translated = originalText;
                                    
                                    // 使用全局翻译映射
                                    if (window.translations && window.translations[originalText]) {
                                        translated = window.translations[originalText];
                                        n.textContent = translated;
                                        translatedCount++;
                                        console.log('[动态翻译] 使用映射翻译: ' + originalText.substring(0, 50) + ' -> ' + translated.substring(0, 50));
                                    }
                                }
                            } else if (n.nodeType === 1) { // 元素节点
                                if (n.tagName && 
                                    (n.tagName.toLowerCase() === 'script' || 
                                     n.tagName.toLowerCase() === 'style')) {
                                    return;
                                }
                                
                                // 处理属性
                                if (n.placeholder && window.translations && window.translations[n.placeholder]) {
                                    n.placeholder = window.translations[n.placeholder];
                                    translatedCount++;
                                    console.log('[动态翻译] placeholder翻译: ' + n.placeholder.substring(0, 30));
                                }
                                if (n.title && window.translations && window.translations[n.title]) {
                                    n.title = window.translations[n.title];
                                    translatedCount++;
                                    console.log('[动态翻译] title翻译: ' + n.title.substring(0, 30));
                                }
                                if (n.alt && window.translations && window.translations[n.alt]) {
                                    n.alt = window.translations[n.alt];
                                    translatedCount++;
                                    console.log('[动态翻译] alt翻译: ' + n.alt.substring(0, 30));
                                }
                                
                                // 递归处理子节点
                                for (let i = 0; i < n.childNodes.length; i++) {
                                    translateNodeRecursive(n.childNodes[i]);
                                }
                            }
                        }
                        
                        translateNodeRecursive(node);
                        console.log('[translateNodeTree] 翻译完成，共翻译 ' + translatedCount + ' 个节点');
                    } else {
                        console.log('[translateNodeTree] 无翻译映射，跳过翻译');
                    }
                }
                
                // 监听DOM变化
                if (!window.domObserver) {
                    window.domObserver = new MutationObserver(function(mutations) {
                        mutations.forEach(function(mutation) {
                            if (mutation.type === 'childList' && mutation.addedNodes.length > 0) {
                                // 有新节点添加
                                console.log('[DOM变化] ========== 检测到新节点添加 ==========');
                                console.log('[DOM变化] 新增节点数量: ' + mutation.addedNodes.length);
                                console.log('[DOM变化] window.translations存在: ' + (window.translations ? '是，数量: ' + Object.keys(window.translations).length : '否'));
                                
                                // 输出到Android Log
                                if (window.AndroidTranslate && window.AndroidTranslate.logMessage) {
                                    window.AndroidTranslate.logMessage('[DOM变化] 检测到新节点添加，数量: ' + mutation.addedNodes.length);
                                }
                                
                                mutation.addedNodes.forEach(function(node, index) {
                                    console.log('[DOM变化] 节点[' + index + '] 类型: ' + node.nodeType);
                                    
                                    // 只处理元素节点和文本节点
                                    if (node.nodeType === 1) { // 元素节点
                                        console.log('[DOM变化] 元素节点标签: ' + (node.tagName || '未知'));
                                        // 输出到Android Log
                                        if (window.AndroidTranslate && window.AndroidTranslate.logMessage) {
                                            window.AndroidTranslate.logMessage('[DOM变化] 元素节点: ' + (node.tagName || '未知'));
                                        }
                                        // 保存原始内容
                                        saveNodeTreeOriginalContent(node);
                                        
                                        // 如果有翻译映射，自动翻译新内容
                                        if (window.translations) {
                                            console.log('[DOM变化] 开始自动翻译元素节点');
                                            if (window.AndroidTranslate && window.AndroidTranslate.logMessage) {
                                                window.AndroidTranslate.logMessage('[DOM变化] 开始翻译元素节点');
                                            }
                                            translateNodeTree(node);
                                            console.log('[DOM变化] 元素节点翻译完成');
                                        } else {
                                            console.log('[DOM变化] 无翻译映射，跳过翻译');
                                        }
                                    } else if (node.nodeType === 3) { // 文本节点
                                        const text = node.textContent;
                                        if (text && text.trim() !== '') {
                                            console.log('[DOM变化] 文本节点内容: ' + text.substring(0, 50));
                                            // 输出到Android Log
                                            if (window.AndroidTranslate && window.AndroidTranslate.logMessage) {
                                                window.AndroidTranslate.logMessage('[DOM变化] 文本节点: ' + text.substring(0, 100));
                                            }
                                            // 保存原始内容
                                            saveNodeOriginalContent(node);
                                            
                                            // 使用翻译映射
                                            if (window.translations && window.translations[text]) {
                                                node.textContent = window.translations[text];
                                                console.log('[DOM变化] 使用映射翻译文本节点: ' + text.substring(0, 30) + ' -> ' + window.translations[text].substring(0, 30));
                                                if (window.AndroidTranslate && window.AndroidTranslate.logMessage) {
                                                    window.AndroidTranslate.logMessage('[DOM变化] 使用映射翻译: ' + text.substring(0, 50));
                                                }
                                            } else {
                                                console.log('[DOM变化] 无翻译映射，跳过翻译');
                                            }
                                        }
                                    }
                                });
                                console.log('[DOM变化] ======================================');
                            }
                            } else if (mutation.type === 'characterData') {
                                // 文本节点内容变化
                                if (mutation.target.nodeType === 3) {
                                    const text = mutation.target.textContent;
                                    if (text && text.trim() !== '') {
                                        // 保存原始内容
                                        if (!window.nodeOriginalTexts.has(mutation.target)) {
                                            window.nodeOriginalTexts.set(mutation.target, text);
                                            console.log('[DOM变化] 保存文本节点原始内容');
                                            
                                            // 如果有翻译映射，自动翻译
                                            if (window.translations && window.translations[text]) {
                                                mutation.target.textContent = window.translations[text];
                                                console.log('[DOM变化] 自动翻译文本节点');
                                            }
                                        }
                                    }
                                }
                            }
                        });
                    });
                    
                    // 开始观察
                    window.domObserver.observe(document.body, {
                        childList: true,
                        subtree: true,
                        characterData: true
                    });
                    
                    console.log('[DOM监听] MutationObserver已启动');
                }
                
                
                function restoreNode(node) {
                    if (node.nodeType === 3) { // 文本节点
                        // 从WeakMap恢复原始文本
                        if (originalTexts.has(node)) {
                            node.textContent = originalTexts.get(node);
                        }
                    } else if (node.nodeType === 1) { // 元素节点
                        // 跳过script和style标签
                        if (node.tagName && 
                            (node.tagName.toLowerCase() === 'script' || 
                             node.tagName.toLowerCase() === 'style')) {
                            return;
                        }
                        
                        // 恢复属性
                        const nodeId = node.getAttribute ? node.getAttribute('data-original-id') : null;
                        if (nodeId && originalAttributes.has(nodeId)) {
                            const attrs = originalAttributes.get(nodeId);
                            if (attrs.placeholder && node.placeholder !== undefined) {
                                node.placeholder = attrs.placeholder;
                            }
                            if (attrs.title && node.title !== undefined) {
                                node.title = attrs.title;
                            }
                            if (attrs.alt && node.alt !== undefined) {
                                node.alt = attrs.alt;
                            }
                        }
                        
                        // 递归处理子节点
                        for (let i = 0; i < node.childNodes.length; i++) {
                            restoreNode(node.childNodes[i]);
                        }
                    }
                }
                
                function restorePageContent() {
                    restoreNode(document.body);
                }
                
                // 暴露 translateNodeTree 函数供外部调用
                window.translateNodeTree = translateNodeTree;
            })();
        """.trimIndent()
    }

    /**
     * DOM变化监听器脚本
     */
    fun getWebpageContentChangedListenerScript(): String {
        return """
            javascript:(function() {
                console.log('[DOM监听] 开始设置DOM变化监听器');
                
                // 确保WeakMap已初始化
                if (!window.nodeOriginalTexts) {
                    window.nodeOriginalTexts = new WeakMap();
                }
                if (!window.nodeOriginalAttributes) {
                    window.nodeOriginalAttributes = new WeakMap();
                }
                
                // 检查是否是纯数字文本
                function isPureNumber(text) {
                    var trimmed = text.trim();
                    if (trimmed === '') return false;
                    // 匹配：全是数字，可能包含小数点、负号、空格、逗号等数字相关字符
                    return /^[\d\s.,\-+]+$/.test(trimmed);
                }
                
                // 判断文本是否是翻译后的文本（用于过滤翻译导致的变化）
                function isTranslatedText(text) {
                    if (!window.translations || !text) {
                        return false;
                    }
                    // 如果文本在translations的key中，说明是原始文本，不是翻译后的
                    if (window.translations[text]) {
                        return false;
                    }
                    // 如果文本在translations的value中，说明是翻译后的文本
                    var keys = Object.keys(window.translations);
                    for (var i = 0; i < keys.length; i++) {
                        if (window.translations[keys[i]] === text) {
                            return true;
                        }
                    }
                    return false;
                }
                
                // 保存单个节点的原始内容
                function saveNodeOriginalContent(node) {
                    if (node.nodeType === 3) { // 文本节点
                        const text = node.textContent;
                        if (text && text.trim() !== '' && !window.nodeOriginalTexts.has(node)) {
                            // 如果当前文本是翻译后的文本，不应该保存为原始内容
                            if (!isTranslatedText(text)) {
                                window.nodeOriginalTexts.set(node, text);
                            } else {
                                console.log('[DOM监听] 跳过保存翻译后的文本为原始内容: ' + text.substring(0, 30));
                            }
                        }
                    } else if (node.nodeType === 1) { // 元素节点
                        if (node.tagName && 
                            (node.tagName.toLowerCase() === 'script' || 
                             node.tagName.toLowerCase() === 'style')) {
                            return;
                        }
                        var attrs = {};
                        var hasAttrs = false;
                        // 只保存非翻译后的属性值
                        if (node.placeholder && !isTranslatedText(node.placeholder)) {
                            attrs.placeholder = node.placeholder;
                            hasAttrs = true;
                        }
                        if (node.title && !isTranslatedText(node.title)) {
                            attrs.title = node.title;
                            hasAttrs = true;
                        }
                        if (node.alt && !isTranslatedText(node.alt)) {
                            attrs.alt = node.alt;
                            hasAttrs = true;
                        }
                        if (hasAttrs && !window.nodeOriginalAttributes.has(node)) {
                            window.nodeOriginalAttributes.set(node, attrs);
                        }
                    }
                }
                
                // 保存节点的所有子节点的原始内容
                function saveNodeTreeOriginalContent(node) {
                    saveNodeOriginalContent(node);
                    if (node.nodeType === 1) {
                        for (let i = 0; i < node.childNodes.length; i++) {
                            saveNodeTreeOriginalContent(node.childNodes[i]);
                        }
                    }
                }
                
                // 收集节点中的所有文本内容（过滤纯数字和已翻译的文本）
                function collectTextsFromNode(node) {
                    var texts = [];
                    if (node.nodeType === 3) { // 文本节点
                        const text = node.textContent;
                        if (text && text.trim() !== '' && !isPureNumber(text)) {
                            // 过滤掉翻译后的文本（避免收集翻译导致的变化）
                            if (!isTranslatedText(text)) {
                                texts.push(text);
                            } else {
                                console.log('[DOM监听] 跳过已翻译文本: ' + text.substring(0, 30));
                            }
                        }
                    } else if (node.nodeType === 1) { // 元素节点
                        if (node.tagName && 
                            (node.tagName.toLowerCase() === 'script' || 
                             node.tagName.toLowerCase() === 'style')) {
                            return texts;
                        }
                        if (node.placeholder && !isPureNumber(node.placeholder) && !isTranslatedText(node.placeholder)) {
                            texts.push(node.placeholder);
                        }
                        if (node.title && !isPureNumber(node.title) && !isTranslatedText(node.title)) {
                            texts.push(node.title);
                        }
                        if (node.alt && !isPureNumber(node.alt) && !isTranslatedText(node.alt)) {
                            texts.push(node.alt);
                        }
                        for (let i = 0; i < node.childNodes.length; i++) {
                            texts = texts.concat(collectTextsFromNode(node.childNodes[i]));
                        }
                    }
                    return texts;
                }
                
                function setupDOMChangeListener() {
                    // 找到你需要观察的目标节点，如果是整个页面可以用 document.body
                    const targetNode = document.body; 
                
                    // 配置观察选项，指定要监听哪些变化
                    const config = { 
                        childList: true,     // 监听直接子节点的添加删除
                        subtree: true,       // 监听所有后代节点的变化
                        attributes: false,    // 不监听属性变化（避免过多回调）
                        characterData: true  // 监听文本节点内容变化
                    };
                
                    // 标志：是否正在应用翻译（避免循环）
                    window.isApplyingTranslation = false;
                    
                    // 防抖定时器和状态
                    var debounceTimer = null;
                    var lastChangeTime = 0;
                    var pendingChanges = {
                        allNewNodes: [],
                        allNewTexts: []
                    };
                    
                    // 处理并发送变化的函数
                    function processAndNotifyChanges() {
                        // 去重
                        var uniqueTexts = Array.from(new Set(pendingChanges.allNewTexts));
                        
                        if (pendingChanges.allNewNodes.length > 0 || uniqueTexts.length > 0) {
                            console.log('[DOM监听] ========== DOM已稳定，开始处理变化 ==========');
                            console.log('[DOM监听] 最终统计 - 新节点: ' + pendingChanges.allNewNodes.length + ', 新文本: ' + uniqueTexts.length);
                            
                            // 构建变化信息
                            var changeInfo = {
                                newNodesCount: pendingChanges.allNewNodes.length,
                                newTextsCount: uniqueTexts.length,
                                newTexts: uniqueTexts.slice(0, 200) // 增加发送数量到200个文本
                            };
                            
                            console.log('[DOM监听] 准备回调Android端，文本数量: ' + changeInfo.newTexts.length);
                            
                            // 通过JavaScript接口通知Android端（只回调一次）
                            if (window.AndroidTranslate && window.AndroidTranslate.onDomChanged) {
                                window.AndroidTranslate.onDomChanged(JSON.stringify(changeInfo));
                                console.log('[DOM监听] 已回调Android端');
                            } else {
                                console.error('[DOM监听] AndroidTranslate.onDomChanged 不存在！');
                            }
                            
                            console.log('[DOM监听] ======================================');
                        } else {
                            console.log('[DOM监听] 没有需要处理的变化');
                        }
                        
                        // 清空待处理的变化
                        pendingChanges.allNewNodes = [];
                        pendingChanges.allNewTexts = [];
                        debounceTimer = null;
                    }
                    
                    // 检查DOM是否稳定的函数
                    function checkStability() {
                        var timeSinceLastChange = Date.now() - lastChangeTime;
                        
                        console.log('[DOM监听] 检查稳定性，距离最后变化: ' + timeSinceLastChange + 'ms');
                        
                        // 如果800ms内没有新变化，认为DOM已稳定
                        if (timeSinceLastChange >= 800) {
                            console.log('[DOM监听] DOM已稳定，开始处理变化');
                            processAndNotifyChanges();
                        } else {
                            // 继续等待，每200ms检查一次
                            console.log('[DOM监听] DOM仍在变化，继续等待...');
                            debounceTimer = setTimeout(checkStability, 200);
                        }
                    }
                    
                    // 当观察到变化时执行的回调函数
                    const callback = function(mutationsList) {
                        // 更新最后变化时间
                        lastChangeTime = Date.now();
                        
                        var currentBatchNodes = 0;
                        var currentBatchTexts = 0;
                        
                        // 收集本次变化
                        mutationsList.forEach(function(mutation) {
                            if (mutation.type === 'childList') {
                                // 处理新增的节点
                                mutation.addedNodes.forEach(function(node) {
                                    if (node.nodeType === 1 || node.nodeType === 3) {
                                        // 即使正在翻译，也要处理新节点（因为可能是页面原生新内容）
                                        // 但会通过isTranslatedText过滤掉翻译导致的变化
                                        currentBatchNodes++;
                                        pendingChanges.allNewNodes.push(node);
                                        // 保存原始内容（只在首次出现时保存，且不是翻译后的文本）
                                        saveNodeTreeOriginalContent(node);
                                        // 收集文本（自动过滤已翻译的文本和纯数字）
                                        var texts = collectTextsFromNode(node);
                                        if (texts.length > 0) {
                                            currentBatchTexts += texts.length;
                                            pendingChanges.allNewTexts = pendingChanges.allNewTexts.concat(texts);
                                            console.log('[DOM监听] 收集到新文本: ' + texts.length + ' 个');
                                        }
                                    }
                                });
                            } else if (mutation.type === 'characterData') {
                                // 文本节点内容变化
                                // 注意：如果这个变化是翻译导致的，不应该保存为原始内容
                                // 只在节点首次出现时保存原始内容
                                if (mutation.target.nodeType === 3) {
                                    const text = mutation.target.textContent;
                                    if (text && text.trim() !== '' && !isPureNumber(text)) {
                                        // 只在没有保存过原始内容时才保存
                                        if (!window.nodeOriginalTexts.has(mutation.target)) {
                                            // 如果当前文本是翻译后的文本，不应该保存为原始内容
                                            if (!isTranslatedText(text)) {
                                                window.nodeOriginalTexts.set(mutation.target, text);
                                                console.log('[DOM监听] 保存文本节点原始内容: ' + text.substring(0, 30));
                                                currentBatchTexts++;
                                                pendingChanges.allNewTexts.push(text);
                                            } else {
                                                console.log('[DOM监听] 跳过翻译后的文本，不保存为原始内容: ' + text.substring(0, 30));
                                            }
                                        } else {
                                            // 如果已经保存过，说明这是后续的变化，可能是翻译导致的
                                            // 不添加到待处理列表，避免循环
                                            console.log('[DOM监听] 文本节点已保存过原始内容，跳过此次变化');
                                        }
                                    }
                                }
                            }
                        });
                        
                        // 立即输出日志，显示检测到变化
                        if (currentBatchNodes > 0 || currentBatchTexts > 0) {
                            console.log('[DOM监听] ========== 检测到DOM变化 ==========');
                            console.log('[DOM监听] 本次变化 - 新节点: ' + currentBatchNodes + ', 新文本: ' + currentBatchTexts);
                            console.log('[DOM监听] 累计变化 - 新节点: ' + pendingChanges.allNewNodes.length + ', 新文本: ' + pendingChanges.allNewTexts.length);
                            console.log('[DOM监听] 等待DOM稳定后统一处理...');
                            
                            // 输出到Android Log
                            if (window.AndroidTranslate && window.AndroidTranslate.logMessage) {
                                window.AndroidTranslate.logMessage('[DOM监听] 检测到变化 - 节点:' + currentBatchNodes + ', 文本:' + currentBatchTexts);
                            }
                        }
                        
                        // 清除之前的定时器
                        if (debounceTimer) {
                            clearTimeout(debounceTimer);
                        }
                        
                        // 设置防抖定时器，等待DOM稳定
                        // 初始延迟500ms，然后每200ms检查一次稳定性
                        debounceTimer = setTimeout(checkStability, 500);
                    };
                
                    // 创建观察者实例并传入回调
                    const observer = new MutationObserver(callback);
                    
                    // 开始观察
                    observer.observe(targetNode, config);
                    
                    console.log('[DOM监听] MutationObserver已启动');
                }
                
                // 在页面初始加载完成后就设置监听器
                if (document.readyState === 'loading') {
                    document.addEventListener('DOMContentLoaded', setupDOMChangeListener);
                } else {
                    setupDOMChangeListener();
                }
            })();
        """.trimIndent()
    }

    /**
     * 应用翻译到新内容的脚本
     */
    fun getApplyTranslationsToNewContentScript(translationsJsonString: String): String {
        return """
            javascript:(function() {
                // 设置标志，暂时禁用MutationObserver回调（避免循环）
                window.isApplyingTranslation = true;
                
                var translations = { $translationsJsonString };
                var appliedCount = 0;
                
                function applyTranslations(node) {
                    if (node.nodeType === 3) { // 文本节点
                        const currentText = node.textContent;
                        if (currentText && currentText.trim() !== '') {
                            // 检查是否是原始文本（在翻译映射中）
                            if (translations[currentText]) {
                                // 确保原始内容已保存
                                if (!window.nodeOriginalTexts.has(node)) {
                                    window.nodeOriginalTexts.set(node, currentText);
                                }
                                const translated = translations[currentText];
                                // 只有当翻译后的内容与当前内容不同时才替换
                                if (currentText !== translated) {
                                    node.textContent = translated;
                                    appliedCount++;
                                    console.log('[DOM变化应用翻译] 文本: ' + currentText.substring(0, 30) + ' -> ' + translated.substring(0, 30));
                                } else {
                                    console.log('[DOM变化应用翻译] 文本已是翻译状态，跳过: ' + currentText.substring(0, 30));
                                }
                            } else {
                                // 如果不在翻译映射中，可能是已经翻译过的文本，跳过
                                console.log('[DOM变化应用翻译] 跳过已翻译文本: ' + currentText.substring(0, 30));
                            }
                        }
                    } else if (node.nodeType === 1) { // 元素节点
                        if (node.tagName && 
                            (node.tagName.toLowerCase() === 'script' || 
                             node.tagName.toLowerCase() === 'style')) {
                            return;
                        }
                        
                        if (node.placeholder && translations[node.placeholder]) {
                            // 确保原始属性已保存
                            if (!window.nodeOriginalAttributes.has(node)) {
                                window.nodeOriginalAttributes.set(node, {
                                    placeholder: node.placeholder,
                                    title: node.title || '',
                                    alt: node.alt || ''
                                });
                            }
                            node.placeholder = translations[node.placeholder];
                            appliedCount++;
                        }
                        if (node.title && translations[node.title]) {
                            if (!window.nodeOriginalAttributes.has(node)) {
                                window.nodeOriginalAttributes.set(node, {
                                    placeholder: node.placeholder || '',
                                    title: node.title,
                                    alt: node.alt || ''
                                });
                            }
                            node.title = translations[node.title];
                            appliedCount++;
                        }
                        if (node.alt && translations[node.alt]) {
                            if (!window.nodeOriginalAttributes.has(node)) {
                                window.nodeOriginalAttributes.set(node, {
                                    placeholder: node.placeholder || '',
                                    title: node.title || '',
                                    alt: node.alt
                                });
                            }
                            node.alt = translations[node.alt];
                            appliedCount++;
                        }
                        
                        for (let i = 0; i < node.childNodes.length; i++) {
                            applyTranslations(node.childNodes[i]);
                        }
                    }
                }
                
                applyTranslations(document.body);
                
                // 恢复标志
                window.isApplyingTranslation = false;
                
                console.log('[DOM变化应用翻译] 已应用 ' + appliedCount + ' 个翻译');
                return appliedCount;
            })();
        """.trimIndent()
    }
}

