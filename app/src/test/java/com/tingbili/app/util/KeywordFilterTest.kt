package com.tingbili.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeywordFilterTest {
    private val kw = setOf("有声书", "小说", "广播剧", "播客")

    @Test fun `标题命中关键词`() {
        assertTrue(KeywordFilter.isListeningRelated("【有声书】凡人修仙传", kw))
        assertTrue(KeywordFilter.isListeningRelated("纯爱广播剧《某某》", kw))
    }

    @Test fun `标题未命中返回false`() {
        assertFalse(KeywordFilter.isListeningRelated("三星手机评测", kw))
        assertFalse(KeywordFilter.isListeningRelated("鬼畜舞蹈合集", kw))
    }

    @Test fun `空关键词集不误杀`() {
        assertFalse(KeywordFilter.isListeningRelated("凡人修仙传", emptySet()))
    }
}
