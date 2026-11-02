package dev.vis.weather.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager

/** Switches the launcher icon by enabling exactly one <activity-alias>. */
object AppIcons {
    fun apply(context: Context, icon: AppIcon) {
        val pm = context.packageManager
        fun cn(i: AppIcon) = ComponentName(context.packageName, "dev.vis.weather.${i.alias}")
        // Enable the new one first so there's never a moment without a launcher entry.
        pm.setComponentEnabledSetting(cn(icon), PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
        AppIcon.entries.filter { it != icon }.forEach {
            pm.setComponentEnabledSetting(cn(it), PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
