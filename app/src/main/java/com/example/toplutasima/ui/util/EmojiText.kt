package com.example.toplutasima.ui.util

fun String.withoutEmojiCharacters(): String = filterNot { ch ->
    val code = ch.code
    code in 0xD800..0xDFFF ||
        code == 0x200D ||
        code == 0xFE0F ||
        code in 0x2300..0x23FF ||
        code in 0x2600..0x27BF ||
        code in 0x2B00..0x2BFF
}.trim()
