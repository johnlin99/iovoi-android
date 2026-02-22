package com.iovoi.ime

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.content.ContentValues
import android.util.Log

class DictionaryDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "iovoi_dict.db"
        // 更新資料庫版本以套用新的 Schema 與簡拼資料
        private const val DATABASE_VERSION = 3
        const val TABLE_NAME = "dictionary"
        const val COLUMN_ID = "_id"
        
        // 原始完整拼音序列 (例如: ㄅㄠˇ'ㄎㄜˇ'ㄇㄥˋ)
        const val COLUMN_PINYIN = "pinyin"
        
        // 新增：簡拼序列，僅包含聲母 (例如: ㄅ'ㄎ'ㄇ)
        const val COLUMN_JIANPIN = "jianpin"
        
        const val COLUMN_WORD = "word"
        const val COLUMN_FREQUENCY = "frequency"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createTable = """
            CREATE TABLE $TABLE_NAME (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_PINYIN TEXT NOT NULL,
                $COLUMN_JIANPIN TEXT NOT NULL,
                $COLUMN_WORD TEXT NOT NULL,
                $COLUMN_FREQUENCY INTEGER DEFAULT 0
            )
        """.trimIndent()
        db.execSQL(createTable)
        
        // 建立雙重索引，加快全拼與簡拼的查詢速度！
        db.execSQL("CREATE INDEX idx_pinyin ON $TABLE_NAME($COLUMN_PINYIN)")
        db.execSQL("CREATE INDEX idx_jianpin ON $TABLE_NAME($COLUMN_JIANPIN)")
        
        insertInitialData(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_NAME")
        onCreate(db)
    }

    /**
     * 輔助函式：從完整拼音字串萃取出「簡拼 (聲母)」
     * 例如輸入 "ㄅㄠˇ'ㄎㄜˇ'ㄇㄥˋ" 會回傳 "ㄅ'ㄎ'ㄇ"
     * 若該音節沒有聲母 (例如 "ㄚ")，則保留原樣作為錨點 "ㄚ"
     */
    private fun extractJianpin(fullPinyin: String): String {
        val syllables = fullPinyin.split("'")
        val jianpinList = syllables.map { syllable ->
            // 從我們 ZhuyinParser 裡定義的聲母集合中找出聲母
            val consonant = ZhuyinParser.CONSONANTS.find { syllable.startsWith(it) }
            consonant ?: syllable.firstOrNull()?.toString() ?: ""
        }
        return jianpinList.joinToString("'")
    }

    private fun insertInitialData(db: SQLiteDatabase) {
        val words = listOf(
            Triple("ㄨㄛˇ", "我", 10000),
            Triple("ㄒㄧㄤˇ", "想", 8000),
            Triple("ㄅㄠˇ", "寶", 5000),
            Triple("ㄎㄜˇ", "可", 6000),
            Triple("ㄇㄥˋ", "夢", 3000),
            Triple("ㄔ", "吃", 7000),
            Triple("ㄈㄢˋ", "飯", 8000),
            Triple("ㄐㄧㄣ", "今", 5000),
            Triple("ㄊㄧㄢ", "天", 6000),
            Triple("ㄏㄣˇ", "很", 8000),
            Triple("ㄏㄠˇ", "好", 9000),
            Triple("ㄓㄨˋ", "注", 4000),
            Triple("ㄧㄣ", "音", 4500),
            
            // 常用詞彙 (全拼會自動被轉換出簡拼)
            Triple("ㄨㄛˇ'ㄒㄧㄤˇ", "我想", 15000),
            Triple("ㄅㄠˇ'ㄎㄜˇ'ㄇㄥˋ", "寶可夢", 20000),
            Triple("ㄔ'ㄈㄢˋ", "吃飯", 18000),
            Triple("ㄐㄧㄣ'ㄊㄧㄢ", "今天", 16000),
            Triple("ㄏㄣˇ'ㄏㄠˇ", "很好", 15000),
            Triple("ㄓㄨˋ'ㄧㄣ", "注音", 10000),
            Triple("ㄎㄨˋ'ㄎㄨˋ'ㄧ", "庫庫伊", 99999)
        )

        db.beginTransaction()
        try {
            for ((pinyin, word, freq) in words) {
                // 自動產生簡拼 (例如 "ㄅㄠˇ'ㄎㄜˇ'ㄇㄥˋ" -> "ㄅ'ㄎ'ㄇ")
                val jianpin = extractJianpin(pinyin)
                
                val values = ContentValues().apply {
                    put(COLUMN_PINYIN, pinyin)
                    put(COLUMN_JIANPIN, jianpin)
                    put(COLUMN_WORD, word)
                    put(COLUMN_FREQUENCY, freq)
                }
                db.insert(TABLE_NAME, null, values)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * 動態組詞引擎 (全拼)
     */
    fun buildDynamicSentence(syllables: List<String>): String {
        if (syllables.isEmpty()) return ""
        val db = readableDatabase
        var currentIndex = 0
        val sentence = java.lang.StringBuilder()

        while (currentIndex < syllables.size) {
            var matchFound = false
            for (len in (syllables.size - currentIndex) downTo 1) {
                val subList = syllables.subList(currentIndex, currentIndex + len)
                val pinyinQuery = subList.joinToString("'")
                
                val cursor = db.query(
                    TABLE_NAME, arrayOf(COLUMN_WORD),
                    "$COLUMN_PINYIN = ?", arrayOf(pinyinQuery),
                    null, null, "$COLUMN_FREQUENCY DESC", "1"
                )
                
                if (cursor.moveToFirst()) {
                    sentence.append(cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_WORD)))
                    currentIndex += len
                    matchFound = true
                    cursor.close()
                    break
                }
                cursor.close()
            }
            if (!matchFound) {
                sentence.append(syllables[currentIndex])
                currentIndex += 1
            }
        }
        return sentence.toString()
    }

    /**
     * 強大的混合查詢引擎：同時支援「全拼」前綴查詢 與 「簡拼」匹配
     */
    fun queryCandidates(pinyinSequence: String): List<String> {
        val candidates = mutableListOf<String>()
        val db = readableDatabase
        
        // 將輸入的注音轉成簡拼格式，看看使用者是不是在打簡拼
        // 例如使用者輸入了 "ㄅ'ㄎ'ㄇ"，轉簡拼後依然是 "ㄅ'ㄎ'ㄇ"
        val inputJianpin = extractJianpin(pinyinSequence)
        
        // 我們使用 OR 查詢：
        // 1. 如果它是全拼的前綴 (例如 ㄅㄠˇ'ㄎ -> 寶可夢)
        // 2. 如果它是簡拼的完全匹配或前綴 (例如 ㄅ'ㄎ'ㄇ -> 寶可夢)
        val selection = "$COLUMN_PINYIN LIKE ? OR $COLUMN_JIANPIN LIKE ?"
        val selectionArgs = arrayOf("$pinyinSequence%", "$inputJianpin%")
        
        val cursor = db.query(
            TABLE_NAME,
            arrayOf(COLUMN_WORD),
            selection,
            selectionArgs,
            null,
            null,
            "$COLUMN_FREQUENCY DESC", // 依舊靠詞頻決勝負！
            "15"
        )
        
        with(cursor) {
            while (moveToNext()) {
                candidates.add(getString(getColumnIndexOrThrow(COLUMN_WORD)))
            }
            close()
        }
        return candidates
    }
}
