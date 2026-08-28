package com.lladlam.melox.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable

/** Starts an Activity page without adding a second custom transition. */
internal fun Context.startMeloXPage(intent: Intent) {
    startActivity(intent)
}

internal fun Activity.finishMeloXPage() {
    finish()
}

/** Lets the previous Activity show through while the system runs back preview. */
internal fun Activity.prepareMeloXPagePredictiveBack() {
    window.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
}
