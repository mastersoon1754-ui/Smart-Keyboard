package com.azertyai.keyboard.system

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityManager
import android.view.inputmethod.InputMethodManager
import com.azertyai.keyboard.context.ChatAccessibilityService
import com.azertyai.keyboard.ime.AzertyInputMethodService

fun keyboardEnabled(context: Context): Boolean = safe {
    val manager = context.getSystemService(InputMethodManager::class.java) ?: return false
    val expected = ComponentName(context, AzertyInputMethodService::class.java)
    manager.enabledInputMethodList.orEmpty().any { same(it.component, expected) }
}

fun keyboardSelected(context: Context): Boolean = safe {
    val manager = context.getSystemService(InputMethodManager::class.java) ?: return false
    val expected = ComponentName(context, AzertyInputMethodService::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        return same(manager.currentInputMethodInfo?.component, expected)
    }
    listed(context, Settings.Secure.DEFAULT_INPUT_METHOD, AzertyInputMethodService::class.java)
}

fun accessibilityEnabled(context: Context): Boolean = safe {
    val manager = context.getSystemService(AccessibilityManager::class.java) ?: return false
    val expected = ComponentName(context, ChatAccessibilityService::class.java)
    val shortName = expected.flattenToShortString()
    val fullName = expected.flattenToString()
    manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
        .orEmpty()
        .any { info -> info.id == shortName || info.id == fullName }
}

fun openKeyboardSettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun showKeyboardPicker(context: Context) {
    val manager = context.getSystemService(InputMethodManager::class.java)
    manager?.showInputMethodPicker()
}

fun openAccessibilitySettings(context: Context) {
    val component = ComponentName(context, ChatAccessibilityService::class.java)
    val details = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS").apply {
        putExtra("android.intent.extra.COMPONENT_NAME", component.flattenToString())
        data = Uri.parse("package:${context.packageName}")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(details)
    } catch (_: ActivityNotFoundException) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

private fun listed(context: Context, setting: String, type: Class<*>): Boolean {
    val raw = Settings.Secure.getString(context.contentResolver, setting) ?: return false
    val name = ComponentName(context, type)
    val shortName = name.flattenToShortString()
    val fullName = name.flattenToString()
    return raw.split(':').any { it.equals(shortName, ignoreCase = true) || it.equals(fullName, ignoreCase = true) }
}

private fun same(component: ComponentName?, expected: ComponentName): Boolean =
    component?.packageName == expected.packageName && component.className == expected.className

private inline fun safe(block: () -> Boolean): Boolean = try {
    block()
} catch (_: SecurityException) {
    false
}
