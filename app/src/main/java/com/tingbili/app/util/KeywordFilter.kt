package com.tingbili.app.util

object KeywordFilter {
    fun isListeningRelated(title: String, keywords: Set<String>): Boolean =
        keywords.any { title.contains(it) }
}
