# -----------------------------------------------------
# FINAL PROGUARD RULES FOR IMGPRO
# -----------------------------------------------------

# 1. THE FIX FOR THE SCREENSHOT ERROR (Ignore missing Java Desktop classes)
-dontwarn java.awt.**
-dontwarn java.awt.color.**

# 2. Keep ExifInterface safe (Using correct AndroidX path)
-keep class androidx.exifinterface.media.ExifInterface { *; }

# 3. Coil Image Loader safety
-keep class io.coil3.** { *; }
-dontwarn io.coil3.**
-dontwarn okio.**

# 4. Compose & Coroutines standard rules
-dontwarn kotlinx.coroutines.**
-dontwarn androidx.compose.**

# 5. Keep essential metadata so the app doesn't crash on launch
-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod