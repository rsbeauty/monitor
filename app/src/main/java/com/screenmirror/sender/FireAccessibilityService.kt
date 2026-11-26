package com.screenmirror.sender

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.res.Resources
import android.graphics.Path
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class FireAccessibilityService : AccessibilityService() {

    companion object {
        var instance: FireAccessibilityService? = null
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {
        instance = null
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    // Tap at normalized coordinates (0.0 to 1.0)
    fun performNormalizedTap(normX: Float, normY: Float) {
        val metrics = Resources.getSystem().displayMetrics
        val x = (normX * metrics.widthPixels).coerceIn(1f, (metrics.widthPixels - 1).toFloat())
        val y = (normY * metrics.heightPixels).coerceIn(1f, (metrics.heightPixels - 1).toFloat())

        val path = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    // Swipe Up to open PIN/Pattern Lock Screen
    fun performSwipeUpUnlock() {
        val metrics = Resources.getSystem().displayMetrics
        val centerX = metrics.widthPixels / 2f
        val startY = metrics.heightPixels * 0.82f
        val endY = metrics.heightPixels * 0.22f

        val path = Path().apply {
            moveTo(centerX, startY)
            lineTo(centerX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, 260)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    // Unlock using PIN digits (clicks matching lockscreen keypad nodes or types into password field)
    fun enterPinOrPassword(pin: String) {
        val handler = Handler(Looper.getMainLooper())
        val root = rootInActiveWindow ?: return

        // First try typing directly into focused/password EditText if present
        val editNode = findPasswordField(root)
        if (editNode != null) {
            val args = Bundle().apply {
                putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, pin)
            }
            editNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            return
        }

        // Otherwise tap lockscreen keypad digit buttons one-by-one
        pin.forEachIndexed { index, ch ->
            handler.postDelayed({
                val currentRoot = rootInActiveWindow ?: return@postDelayed
                clickNodeByText(currentRoot, ch.toString())
            }, (index * 280).toLong())
        }

        // Tap Enter/OK button after digits
        handler.postDelayed({
            val currentRoot = rootInActiveWindow ?: return@postDelayed
            if (!clickNodeByText(currentRoot, "OK")) {
                clickNodeByText(currentRoot, "Enter")
            }
        }, (pin.length * 280 + 300).toLong())
    }

    private fun findPasswordField(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val found = findPasswordField(node.getChild(i))
            if (found != null) return found
        }
        return null
    }

    private fun clickNodeByText(node: AccessibilityNodeInfo?, target: String): Boolean {
        if (node == null) return false
        val txt = node.text?.toString()?.trim() ?: ""
        val desc = node.contentDescription?.toString()?.trim() ?: ""

        if (txt.equals(target, ignoreCase = true) || desc.startsWith(target, ignoreCase = true)) {
            if (node.isClickable) {
                node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                return true
            } else {
                var parent = node.parent
                while (parent != null) {
                    if (parent.isClickable) {
                        parent.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                        return true
                    }
                    parent = parent.parent
                }
            }
        }
        for (i in 0 until node.childCount) {
            if (clickNodeByText(node.getChild(i), target)) return true
        }
        return false
    }
}
