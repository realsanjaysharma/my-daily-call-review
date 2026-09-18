package com.dailycallsreview.app.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

/**
 * Single source of truth for the app's required runtime permissions. Used by both
 * [PermissionsGate] (main UI) and the home screen widget, so the two never drift out
 * of sync with each other.
 */
object RequiredPermissions {
    val ALL = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS
    )

    fun hasAll(context: Context): Boolean =
        ALL.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}
