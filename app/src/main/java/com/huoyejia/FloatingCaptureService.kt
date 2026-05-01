package com.huoyejia

import android.app.Service
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import com.huoyejia.domain.StatsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class FloatingCaptureService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var collapsed = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showFloatingCapture()
    }

    override fun onDestroy() {
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        scope.cancel()
        super.onDestroy()
    }

    private fun showFloatingCapture() {
        if (floatingView != null) return
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22, 18, 22, 18)
            setBackgroundColor(Color.rgb(255, 252, 246))
        }
        val header = TextView(this).apply {
            text = "活页夹悬浮采集"
            setTextColor(Color.rgb(24, 59, 55))
            textSize = 16f
            setPadding(0, 0, 0, 10)
        }
        val editor = EditText(this).apply {
            hint = "复制微信/小红书内容后，在这里粘贴..."
            minLines = 5
            maxLines = 8
            textSize = 15f
            setSingleLine(false)
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val paste = Button(this).apply { text = "粘贴" }
        val save = Button(this).apply { text = "存入" }
        val fold = Button(this).apply { text = "收起" }
        val close = Button(this).apply { text = "关闭" }
        listOf(paste, save, fold, close).forEach { row.addView(it) }
        root.addView(header)
        root.addView(editor)
        root.addView(row)

        val params = WindowManager.LayoutParams(
            dp(330),
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = dp(18)
            y = dp(100)
        }

        header.setOnTouchListener(DragTouchListener(params))
        paste.setOnClickListener { pasteClipboard(editor) }
        save.setOnClickListener { saveFloatingContent(editor) }
        close.setOnClickListener { stopSelf() }
        fold.setOnClickListener {
            collapsed = !collapsed
            editor.visibility = if (collapsed) View.GONE else View.VISIBLE
            paste.visibility = if (collapsed) View.GONE else View.VISIBLE
            save.visibility = if (collapsed) View.GONE else View.VISIBLE
            fold.text = if (collapsed) "展开" else "收起"
            windowManager.updateViewLayout(root, params)
        }

        floatingView = root
        windowManager.addView(root, params)
        editor.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun pasteClipboard(editor: EditText) {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, "剪贴板没有可粘贴内容", Toast.LENGTH_SHORT).show()
            return
        }
        editor.setText(text)
        editor.setSelection(editor.text.length)
    }

    private fun saveFloatingContent(editor: EditText) {
        val raw = editor.text?.toString()?.trim().orEmpty()
        if (raw.isBlank()) {
            Toast.makeText(this, "先粘贴内容或链接", Toast.LENGTH_SHORT).show()
            return
        }
        saveCurrentClip(raw)
        editor.setText("")
    }

    private fun saveCurrentClip(raw: String) {
        scope.launch {
            val app = application as HuoyejiaApp
            val isLink = raw.startsWith("http://") || raw.startsWith("https://")
            val title = if (isLink) "悬浮窗：链接学习总结" else "悬浮窗：${raw.lineSequence().firstOrNull().orEmpty().take(18)}"
            val content = if (isLink) {
                "学习链接：$raw\n请总结这个链接对应的核心内容、结构和可复习问题。"
            } else {
                raw
            }
            runCatching {
                app.container.processor.captureAndProcess(
                    rawText = content,
                    imagePath = null,
                    sourceType = if (isLink) "web" else "floating",
                    sourceTitle = title,
                    url = if (isLink) raw else null
                )
                val notes = app.container.noteRepository.loadAllNotes()
                app.container.statsRepository.upsert(StatsCalculator.calculate(notes))
            }.onSuccess {
                Toast.makeText(this@FloatingCaptureService, "已存入活页夹", Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(this@FloatingCaptureService, "保存失败，请回到 App 重试", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private inner class DragTouchListener(private val params: WindowManager.LayoutParams) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = startX + (event.rawX - touchX).toInt()
                    params.y = startY + (event.rawY - touchY).toInt()
                    floatingView?.let { windowManager.updateViewLayout(it, params) }
                    return true
                }
            }
            return false
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        fun start(context: Context) {
            context.startService(Intent(context, FloatingCaptureService::class.java))
        }
    }
}
