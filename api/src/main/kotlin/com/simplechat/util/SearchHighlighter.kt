package com.simplechat.util

import org.springframework.stereotype.Component

/**
 * 검색 결과 하이라이팅 유틸리티
 * 
 * 검색 키워드를 강조 표시하는 기능을 제공합니다.
 */
@Component
class SearchHighlighter {

    companion object {
        private const val HIGHLIGHT_START_TAG = "<mark>"
        private const val HIGHLIGHT_END_TAG = "</mark>"
        private const val MAX_SNIPPET_LENGTH = 200
    }

    /**
     * 주어진 텍스트에서 키워드를 하이라이팅합니다.
     * 
     * @param content 원본 텍스트
     * @param keyword 검색 키워드
     * @return 하이라이팅된 텍스트
     */
    fun highlightKeyword(content: String, keyword: String?): String? {
        if (keyword.isNullOrBlank() || content.isBlank()) {
            return null
        }

        return try {
            // 대소문자 구분 없이 키워드 검색 및 하이라이팅
            val regex = Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE)
            val highlighted = content.replace(regex) { matchResult ->
                "$HIGHLIGHT_START_TAG${matchResult.value}$HIGHLIGHT_END_TAG"
            }

            // 하이라이팅된 부분이 있는 경우에만 반환
            if (highlighted != content) highlighted else null
        } catch (e: Exception) {
            // 정규식 오류 시 원본 반환
            null
        }
    }

    /**
     * 키워드 주변의 텍스트 스니펫을 생성합니다.
     * 
     * @param content 원본 텍스트
     * @param keyword 검색 키워드
     * @param maxLength 최대 스니펫 길이
     * @return 키워드가 포함된 텍스트 스니펫
     */
    fun createSnippet(content: String, keyword: String?, maxLength: Int = MAX_SNIPPET_LENGTH): String? {
        if (keyword.isNullOrBlank() || content.isBlank()) {
            return if (content.length <= maxLength) content else content.take(maxLength) + "..."
        }

        return try {
            val keywordIndex = content.indexOf(keyword, ignoreCase = true)
            if (keywordIndex == -1) {
                return if (content.length <= maxLength) content else content.take(maxLength) + "..."
            }

            val keywordLength = keyword.length
            val beforeLength = (maxLength - keywordLength) / 2
            val afterLength = maxLength - keywordLength - beforeLength

            val startIndex = maxOf(0, keywordIndex - beforeLength)
            val endIndex = minOf(content.length, keywordIndex + keywordLength + afterLength)

            var snippet = content.substring(startIndex, endIndex)

            // 앞뒤 말줄임표 추가
            if (startIndex > 0) snippet = "...$snippet"
            if (endIndex < content.length) snippet = "$snippet..."

            // 키워드 하이라이팅
            val regex = Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE)
            snippet.replace(regex) { matchResult ->
                "$HIGHLIGHT_START_TAG${matchResult.value}$HIGHLIGHT_END_TAG"
            }
        } catch (e: Exception) {
            if (content.length <= maxLength) content else content.take(maxLength) + "..."
        }
    }

    /**
     * 다중 키워드 하이라이팅
     * 
     * @param content 원본 텍스트
     * @param keywords 검색 키워드 목록
     * @return 하이라이팅된 텍스트
     */
    fun highlightMultipleKeywords(content: String, keywords: List<String>): String? {
        if (keywords.isEmpty() || content.isBlank()) {
            return null
        }

        var highlightedContent = content

        keywords.forEach { keyword ->
            if (keyword.isNotBlank()) {
                try {
                    val regex = Regex(Regex.escape(keyword), RegexOption.IGNORE_CASE)
                    highlightedContent = highlightedContent.replace(regex) { matchResult ->
                        "$HIGHLIGHT_START_TAG${matchResult.value}$HIGHLIGHT_END_TAG"
                    }
                } catch (e: Exception) {
                    // 정규식 오류 시 해당 키워드는 건너뜀
                }
            }
        }

        return if (highlightedContent != content) highlightedContent else null
    }

    /**
     * HTML 태그 제거 (보안상 필요시 사용)
     * 
     * @param content HTML이 포함된 텍스트
     * @return HTML 태그가 제거된 텍스트
     */
    fun stripHtmlTags(content: String): String {
        return content.replace(Regex("<[^>]*>"), "")
    }

    /**
     * 하이라이팅 태그만 제거
     * 
     * @param content 하이라이팅된 텍스트
     * @return 하이라이팅 태그가 제거된 원본 텍스트
     */
    fun removeHighlighting(content: String): String {
        return content
            .replace(HIGHLIGHT_START_TAG, "")
            .replace(HIGHLIGHT_END_TAG, "")
    }
}