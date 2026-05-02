package com.huoyejia

import android.app.Service
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast

class FloatingCaptureService : Service() {
    private lateinit var windowManager: WindowManager
    private var floatingView: View? = null
    private var currentX = 18
    private var currentY = 100

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        showExpandedWindow()
    }

    override fun onDestroy() {
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
        super.onDestroy()
    }

    private fun showExpandedWindow() {
        removeFloatingView()
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = roundRect(Color.rgb(255, 252, 246), dp(18))
        }
        val detected = clipboardText()
        val header = TextView(this).apply {
            text = "\u68c0\u6d4b\u6587\u7ae0/\u7f51\u5740\uff0c\u53ef\u624b\u52a8\u7c98\u8d34"
            setTextColor(Color.rgb(15, 47, 51))
            textSize = 16f
            setPadding(0, 0, 0, dp(8))
        }
        val editor = EditText(this).apply {
            hint = "\u7c98\u8d34\u6587\u7ae0\u3001\u7b14\u8bb0\u6216\u7f51\u5740"
            setText(detected)
            minLines = 5
            maxLines = 8
            textSize = 15f
            setSingleLine(false)
        }
        val state = TextView(this).apply {
            text = if (detected.isBlank()) "\u672a\u68c0\u6d4b\u5230\u526a\u8d34\u677f\u5185\u5bb9" else detectLabel(detected)
            setTextColor(Color.rgb(86, 77, 68))
            textSize = 12f
            setPadding(0, 0, 0, dp(8))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END
        }
        val save = Button(this).apply { text = "\u5b58\u5165" }
        val collapse = Button(this).apply { text = "\u6536\u8d77" }
        val back = Button(this).apply { text = "\u8fd4\u56de" }
        listOf(save, collapse, back).forEach { row.addView(it) }
        root.addView(header)
        root.addView(state)
        root.addView(editor)
        root.addView(row)

        val params = overlayParams(dp(342), WindowManager.LayoutParams.WRAP_CONTENT).apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
        }
        header.setOnTouchListener(DragTouchListener(params))
        save.setOnClickListener {
            val raw = editor.text?.toString()?.trim().orEmpty()
            if (raw.isBlank()) {
                Toast.makeText(this, "\u5148\u7c98\u8d34\u5185\u5bb9\u6216\u7f51\u5740", Toast.LENGTH_SHORT).show()
            } else {
                openCapture(raw, pickFolder = true)
                stopSelf()
            }
        }
        collapse.setOnClickListener { showCollapsedIcon() }
        back.setOnClickListener {
            val raw = editor.text?.toString()?.trim().orEmpty()
            openCapture(raw, pickFolder = false)
            stopSelf()
        }

        floatingView = root
        windowManager.addView(root, params)
    }

    private fun showCollapsedIcon() {
        removeFloatingView()
        val icon = TextView(this).apply {
            text = "\u5939"
            textSize = 22f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            background = oval(Color.rgb(15, 47, 51))
        }
        val params = overlayParams(dp(58), dp(58)).apply {
            flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        }
        icon.setOnTouchListener(DragTouchListener(params) { showExpandedWindow() })
        floatingView = icon
        windowManager.addView(icon, params)
    }

    private fun openCapture(raw: String, pickFolder: Boolean) {
        val isUrl = raw.startsWith("http://") || raw.startsWith("https://")
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_NAV_CAPTURE, true)
            putExtra(MainActivity.EXTRA_OPEN_CAPTURE, raw.isNotBlank())
            putExtra(MainActivity.EXTRA_CAPTURE_TITLE, if (isUrl) "\u94fe\u63a5\u5b66\u4e60\u603b\u7ed3" else "\u60ac\u6d6e\u7a97\u91c7\u96c6")
            putExtra(MainActivity.EXTRA_CAPTURE_TEXT, if (isUrl) "" else raw)
            putExtra(MainActivity.EXTRA_CAPTURE_URL, if (isUrl) raw else "")
            putExtra(MainActivity.EXTRA_PICK_FOLDER, pickFolder)
        }
        startActivity(intent)
    }

    private fun clipboardText(): String {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        return clipboard.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim().orEmpty()
    }

    private fun detectLabel(text: String): String {
        return if (text.startsWith("http://") || text.startsWith("https://")) {
            "\u5df2\u68c0\u6d4b\u5230\u7f51\u5740"
        } else {
            "\u5df2\u68c0\u6d4b\u5230\u6587\u7ae0/\u7b14\u8bb0"
        }
    }

    private fun overlayParams(width: Int, height: Int): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            width,
            height,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = currentX
            y = currentY
        }
    }

    private fun removeFloatingView() {
        floatingView?.let { windowManager.removeView(it) }
        floatingView = null
    }

    private fun roundRect(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = radius.toFloat()
        }
    }

    private fun oval(color: Int): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }
    }

    private inner class DragTouchListener(
        private val params: WindowManager.LayoutParams,
        private val onClick: (() -> Unit)? = null
    ) : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var moved = false

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    touchX = event.rawX
                    touchY = event.rawY
                    moved = false
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - touchX).toInt()
                    val dy = (event.rawY - touchY).toInt()
                    if (kotlin.math.abs(dx) > dp(3) || kotlin.math.abs(dy) > dp(3)) moved = true
                    params.x = startX + dx
                    params.y = startY + dy
                    currentX = params.x
                    currentY = params.y
                    floatingView?.let { windowManager.updateViewLayout(it, params) }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    if (!moved) onClick?.invoke()
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
