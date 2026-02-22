package com.iovoi.ime

object ZhuyinParser {
    
    // 注音四個位置的定義：聲母(C), 介母(M), 韻母(V), 聲調(T)
    val CONSONANTS = setOf("ㄅ", "ㄆ", "ㄇ", "ㄈ", "ㄉ", "ㄊ", "ㄋ", "ㄌ", "ㄍ", "ㄎ", "ㄏ", "ㄐ", "ㄑ", "ㄒ", "ㄓ", "ㄔ", "ㄕ", "ㄖ", "ㄗ", "ㄘ", "ㄙ")
    val MEDIALS = setOf("ㄧ", "ㄨ", "ㄩ")
    val VOWELS = setOf("ㄚ", "ㄛ", "ㄜ", "ㄝ", "ㄞ", "ㄟ", "ㄠ", "ㄡ", "ㄢ", "ㄣ", "ㄤ", "ㄥ", "ㄦ")
    val TONES = setOf("˙", "ˊ", "ˇ", "ˋ")
    
    // 預設聲調 (第一聲空白)
    val TONE_1 = ""

    /**
     * 表示一個完整的注音音節
     */
    data class Syllable(
        val consonant: String = "",
        val medial: String = "",
        val vowel: String = "",
        val tone: String = TONE_1,
        val rawInput: String = "" // 原始輸入序列
    ) {
        val isValid: Boolean
            get() = consonant.isNotEmpty() || medial.isNotEmpty() || vowel.isNotEmpty()

        val fullPinyin: String
            get() = "$consonant$medial$vowel$tone"
    }

    /**
     * 將連續的注音字串切分成音節列表 (支援搜狗式的無聲調連打與容錯切分)
     * 例如："ㄨㄛˇㄒㄧㄤˇ" -> [Syllable(ㄨ,ㄛ,ˇ), Syllable(ㄒ,ㄧ,ㄤ,ˇ)]
     * 例如："ㄨㄛㄒㄧㄤ" -> [Syllable(ㄨ,ㄛ), Syllable(ㄒ,ㄧ,ㄤ)]
     */
    fun parse(input: String): List<Syllable> {
        val syllables = mutableListOf<Syllable>()
        var currentSyllable = Syllable()
        var currentRaw = StringBuilder()

        for (i in input.indices) {
            val char = input[i].toString()
            currentRaw.append(char)

            // 判斷當前字元屬於哪個部分
            val isConsonant = CONSONANTS.contains(char)
            val isMedial = MEDIALS.contains(char)
            val isVowel = VOWELS.contains(char)
            val isTone = TONES.contains(char)

            // 如果遇到新的聲母，且當前音節已經有內容了 (代表這是一個新字的開始)
            // 搜狗的規則：遇到聲母一定會切分出新的音節 (除非有特例，但標準注音聲母不能接在後面)
            if (isConsonant && currentSyllable.isValid) {
                syllables.add(currentSyllable.copy(rawInput = currentRaw.dropLast(1).toString()))
                currentSyllable = Syllable(consonant = char)
                currentRaw = StringBuilder(char)
                continue
            }

            // 如果遇到介母，但當前音節已經有介母或韻母了，這也代表是新字的開始 (例如：ㄧㄧ)
            if (isMedial && (currentSyllable.medial.isNotEmpty() || currentSyllable.vowel.isNotEmpty())) {
                syllables.add(currentSyllable.copy(rawInput = currentRaw.dropLast(1).toString()))
                currentSyllable = Syllable(medial = char)
                currentRaw = StringBuilder(char)
                continue
            }

            // 遇到輕聲 (˙) 的特殊處理：輕聲通常放在最前面 (例如：˙ㄇㄚ)，但輸入時可能放在後面，這裡統一處理為聲調
            if (isTone) {
                currentSyllable = currentSyllable.copy(tone = char)
                // 收到聲調，代表這個字結束了，可以切分 (搜狗規則：有聲調必切)
                syllables.add(currentSyllable.copy(rawInput = currentRaw.toString()))
                currentSyllable = Syllable()
                currentRaw.clear()
                continue
            }

            // 填入對應的位置
            if (isConsonant) currentSyllable = currentSyllable.copy(consonant = char)
            else if (isMedial) currentSyllable = currentSyllable.copy(medial = char)
            else if (isVowel) {
                // 如果已經有韻母了，遇到新的韻母代表新字開始 (例如：ㄚㄚ)
                if (currentSyllable.vowel.isNotEmpty()) {
                    syllables.add(currentSyllable.copy(rawInput = currentRaw.dropLast(1).toString()))
                    currentSyllable = Syllable(vowel = char)
                    currentRaw = StringBuilder(char)
                } else {
                    currentSyllable = currentSyllable.copy(vowel = char)
                }
            }
        }

        // 把最後一個未完成的音節加進去
        if (currentSyllable.isValid) {
            syllables.add(currentSyllable.copy(rawInput = currentRaw.toString()))
        }

        return syllables
    }

    /**
     * 格式化輸出音節列表供 Debug 使用
     */
    fun formatSyllables(syllables: List<Syllable>): String {
        return syllables.joinToString(" ' ") { it.fullPinyin }
    }
}
