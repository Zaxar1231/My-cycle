# Hilt, WorkManager and kotlinx.serialization (navigation routes) ship their own consumer rules,
# so the defaults from proguard-android-optimize.txt are enough for now.

# Glance creates widget actions from their class name.
-keep class * extends androidx.glance.appwidget.action.ActionCallback { <init>(); }
