# No reflection-based serialization is used; R8 defaults are sufficient.
-dontwarn org.jetbrains.annotations.**

# Glance instantiates widget action callbacks reflectively by class name.
-keep class * implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# Widgets are looked up by class (GlanceAppWidgetManager.getGlanceIds), so R8 must not merge them.
-keep class dev.vis.weather.widget.** extends androidx.glance.appwidget.GlanceAppWidget
-keep class dev.vis.weather.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver
