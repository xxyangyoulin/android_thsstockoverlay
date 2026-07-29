package com.xxyangyoulin.thsstockoverlay

data class StockInfo(val name: String, val code: String?)

object StockTextParser {
    fun findCode(name: String, texts: List<String>): String? {
        val namedPattern = Regex("\\$?${Regex.escape(name)}\\s*\\(([0-9]{6})\\)\\$?")
        val namedCode = texts.firstNotNullOfOrNull {
            namedPattern.find(it)?.groupValues?.get(1)
        }
        if (namedCode != null) return namedCode

        return findStandaloneCode(texts)
    }

    fun findStandaloneCode(texts: List<String>): String? = texts.firstNotNullOfOrNull {
        STANDALONE_CODE.find(it)?.groupValues?.get(1)
    }

    private val STANDALONE_CODE = Regex("(?<![0-9])([0368][0-9]{5})(?![0-9])")
}
