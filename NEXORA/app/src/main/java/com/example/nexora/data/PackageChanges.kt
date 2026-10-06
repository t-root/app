package com.example.nexora.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat

/**
 * Calls [onChange] whenever an app is installed or uninstalled, so the lists
 * can follow at once instead of waiting for the next resume. An update shows
 * up as remove + add; only the add is reported. Returns the function that
 * stops listening.
 */
fun listenForPackageChanges(context: Context, onChange: () -> Unit): () -> Unit {
    val appContext = context.applicationContext
    val handler = Handler(Looper.getMainLooper())
    val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val replacing = intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)
            if (intent.action == Intent.ACTION_PACKAGE_REMOVED && replacing) return
            onChange()
            if (intent.action == Intent.ACTION_PACKAGE_ADDED) {
                // The broadcast can come before the launcher entry of the new app
                // can be queried; ask again a little later.
                handler.postDelayed(onChange, RETRY_MS)
                handler.postDelayed(onChange, RETRY_MS * 3)
            }
        }
    }
    val filter = IntentFilter().apply {
        addAction(Intent.ACTION_PACKAGE_ADDED)
        addAction(Intent.ACTION_PACKAGE_REMOVED)
        addDataScheme("package")
    }
    ContextCompat.registerReceiver(appContext, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    return {
        handler.removeCallbacksAndMessages(null)
        appContext.unregisterReceiver(receiver)
    }
}

private const val RETRY_MS = 1500L
