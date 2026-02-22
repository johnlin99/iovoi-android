package com.iovoi.ime

import android.inputmethodservice.InputMethodService
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Button
import android.view.inputmethod.EditorInfo
import android.graphics.Color
import android.view.Gravity
import android.widget.HorizontalScrollView
import android.view.KeyEvent

class IovoiInputMethodService : InputMethodService() {

    private lateinit var composingView: TextView
    private lateinit var candidateContainer: LinearLayout
    private var currentComposing = StringBuilder()
    private lateinit var dictionaryHelper: DictionaryDatabaseHelper

    // 簡化的鍵盤，為了版面整潔
    private val zhuyinRows = listOf(
        listOf("ㄅ", "ㄉ", "ˇ", "ˋ", "ㄓ", "ˊ", "˙", "ㄚ", "ㄞ", "ㄢ", "ㄦ"),
        listOf("ㄆ", "ㄊ", "ㄍ", "ㄐ", "ㄔ", "ㄗ", "ㄧ", "ㄛ", "ㄟ", "ㄣ"),
        listOf("ㄇ", "ㄋ", "ㄎ", "ㄑ", "ㄕ", "ㄘ", "ㄨ", "ㄜ", "ㄠ", "ㄤ"),
        listOf("ㄈ", "ㄌ", "ㄏ", "ㄒ", "ㄖ", "ㄙ", "ㄩ", "ㄝ", "ㄡ", "ㄥ")
    )

    override fun onCreate() {
        super.onCreate()
        dictionaryHelper = DictionaryDatabaseHelper(this)
        // 為了測試，強制升級資料庫以載入新詞庫
        dictionaryHelper.readableDatabase
    }

    override fun onCreateInputView(): View {
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EEEEEE"))
        }

        val topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(16, 16, 16, 16)
            setBackgroundColor(Color.WHITE)
        }

        composingView = TextView(this).apply {
            text = ""
            textSize = 20f
            setTextColor(Color.parseColor("#007AFF"))
            setPadding(0, 0, 32, 0)
        }

        val candidateScrollView = HorizontalScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            isHorizontalScrollBarEnabled = false
        }

        candidateContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        candidateScrollView.addView(candidateContainer)

        topBar.addView(composingView)
        topBar.addView(candidateScrollView)
        mainLayout.addView(topBar)

        val divider = View(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
            setBackgroundColor(Color.LTGRAY)
        }
        mainLayout.addView(divider)

        val keyboardLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 16, 0, 16)
        }

        for (row in zhuyinRows) {
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                gravity = Gravity.CENTER
            }
            for (key in row) {
                rowLayout.addView(createKeyButton(key) { handleKeyPress(key) })
            }
            keyboardLayout.addView(rowLayout)
        }

        val bottomRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            gravity = Gravity.CENTER
        }
        bottomRow.addView(createKeyButton("Space", weight = 2f) { handleSpace() })
        bottomRow.addView(createKeyButton("Del", weight = 1f) { handleDelete() })
        bottomRow.addView(createKeyButton("Enter", weight = 1f) { handleEnter() })
        
        keyboardLayout.addView(bottomRow)
        mainLayout.addView(keyboardLayout)

        return mainLayout
    }

    private fun createKeyButton(text: String, weight: Float = 1f, onClick: () -> Unit): Button {
        return Button(this).apply {
            this.text = text
            textSize = 18f
            isAllCaps = false
            setBackgroundColor(Color.WHITE)
            setTextColor(Color.BLACK)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, weight).apply {
                setMargins(4, 8, 4, 8)
            }
            setOnClickListener { onClick() }
        }
    }

    private fun handleKeyPress(key: String) {
        currentComposing.append(key)
        updateComposingView()
        generateCandidates()
    }

    private fun handleDelete() {
        if (currentComposing.isNotEmpty()) {
            currentComposing.deleteCharAt(currentComposing.length - 1)
            updateComposingView()
            generateCandidates()
        } else {
            currentInputConnection?.deleteSurroundingText(1, 0)
        }
    }

    private fun handleSpace() {
        if (currentComposing.isNotEmpty()) {
            val firstChild = candidateContainer.getChildAt(0) as? TextView
            if (firstChild != null) {
                commitText(firstChild.text.toString())
            } else {
                commitText(currentComposing.toString())
            }
        } else {
            currentInputConnection?.commitText(" ", 1)
        }
    }

    private fun handleEnter() {
        if (currentComposing.isNotEmpty()) {
            commitText(currentComposing.toString())
        } else {
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER))
            currentInputConnection?.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_ENTER))
        }
    }

    private fun commitText(text: String) {
        currentInputConnection?.commitText(text, 1)
        currentComposing.clear()
        updateComposingView()
        candidateContainer.removeAllViews()
    }

    private fun updateComposingView() {
        val inputStr = currentComposing.toString()
        if (inputStr.isNotEmpty()) {
            val syllables = ZhuyinParser.parse(inputStr)
            val formatted = ZhuyinParser.formatSyllables(syllables)
            composingView.text = formatted
            currentInputConnection?.setComposingText(formatted, 1)
        } else {
            composingView.text = ""
            currentInputConnection?.finishComposingText()
        }
    }

    private fun generateCandidates() {
        candidateContainer.removeAllViews()
        if (currentComposing.isEmpty()) return

        val syllables = ZhuyinParser.parse(currentComposing.toString())
        val pinyinList = syllables.map { it.fullPinyin }
        val formattedPinyin = ZhuyinParser.formatSyllables(syllables)
        
        val words = mutableListOf<String>()

        // 1. 動態組詞 (整句聯想) - 這是 iovoi 最聰明的地方！
        val dynamicSentence = dictionaryHelper.buildDynamicSentence(pinyinList)
        if (dynamicSentence.isNotEmpty() && dynamicSentence != formattedPinyin) {
            words.add(dynamicSentence)
        }

        // 2. 傳統前綴查詢 (例如打到一半的詞)
        val prefixMatches = dictionaryHelper.queryCandidates(formattedPinyin)
        words.addAll(prefixMatches.filter { it != dynamicSentence }) // 避免重複
        
        // 3. 防呆拼音輸出
        if (words.isEmpty()) {
            words.add(formattedPinyin)
        }

        // 去除重複並取前 15 個顯示
        val finalCandidates = words.distinct().take(15)

        for (word in finalCandidates) {
            val candidateView = TextView(this).apply {
                text = word
                textSize = 18f
                setPadding(32, 16, 32, 16)
                setTextColor(Color.BLACK)
                setOnClickListener { commitText(word) }
            }
            candidateContainer.addView(candidateView)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        currentComposing.clear()
        if (::composingView.isInitialized) {
            updateComposingView()
            candidateContainer.removeAllViews()
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        dictionaryHelper.close()
    }
}
