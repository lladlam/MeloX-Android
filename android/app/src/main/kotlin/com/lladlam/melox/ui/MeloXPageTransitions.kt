package com.lladlam.melox.ui

import android.app.Activity
import android.content.Context
import android.content.Intent

/** Starts an Activity page without adding a second custom transition. */
internal fun Context.startMeloXPage(intent: Intent) {
    startActivity(intent)
}

internal fun Activity.finishMeloXPage() {
    finish()
}
