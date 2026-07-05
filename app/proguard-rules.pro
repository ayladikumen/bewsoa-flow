# Project-specific ProGuard/R8 rules. Room, Compose, WorkManager, Glance and
# DataStore all ship consumer rules; only app-specific keeps live here.

# Keep crash stack traces readable in release builds.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
