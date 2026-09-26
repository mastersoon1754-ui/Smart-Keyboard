package com.azertyai.keyboard.context

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import com.azertyai.keyboard.logic.RawText

/**
 * The service does not record events and does not keep a conversation log.
 * The keyboard calls [snapshot] only after the user picks 5, 10 or 30 messages.
 */
class ChatAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        if (instance === this) instance = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    fun readScreen(): List<RawText> {
        val output = ArrayList<RawText>(64)
        val budget = intArrayOf(700)
        val current = windows ?: return output
        for (window in current) {
            if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
            val root = window.root ?: continue
            try {
                if (root.packageName?.toString() == packageName) continue
                collect(root, output, budget)
            } catch (_: Exception) {
                // A window can disappear while it is being read.
            } finally {
                root.recycle()
            }
        }
        return output
    }

    private fun collect(node: AccessibilityNodeInfo, output: MutableList<RawText>, budget: IntArray) {
        if (budget[0] <= 0) return
        budget[0] -= 1
        try {
            if (node.isVisibleToUser && !node.isPassword) {
                val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() } ?: describedText(node)
                if (text != null) {
                    val rect = Rect()
                    node.getBoundsInScreen(rect)
                    if (rect.width() > 4 && rect.height() > 4) {
                        output += RawText(text, rect.top, rect.bottom, rect.left, rect.right, node.isEditable)
                    }
                }
            }
            for (index in 0 until node.childCount) {
                if (budget[0] <= 0) return
                val child = node.getChild(index) ?: continue
                try {
                    collect(child, output, budget)
                } finally {
                    @Suppress("DEPRECATION")
                    child.recycle()
                }
            }
        } catch (_: Exception) {
            // Stale nodes are skipped.
        }
    }

    private fun describedText(node: AccessibilityNodeInfo): String? {
        val value = node.contentDescription?.toString()?.trim().orEmpty()
        if (value.length < 12 || !value.contains(' ')) return null
        val type = node.className?.toString().orEmpty()
        if (!type.endsWith("TextView")) return null
        return value
    }

    companion object {
        @Volatile
        private var instance: ChatAccessibilityService? = null

        fun snapshot(): List<RawText> = try {
            instance?.readScreen().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
    }
}
