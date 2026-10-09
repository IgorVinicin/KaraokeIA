package com.araujo.karaoke_ia.ui.components

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** Some TV boxes report NORMAL uiMode while still having no touch screen. */
fun usesTvNavigation(context: Context, configuration: Configuration): Boolean =
    configuration.uiMode and Configuration.UI_MODE_TYPE_MASK == Configuration.UI_MODE_TYPE_TELEVISION ||
        context.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
        !context.packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)
