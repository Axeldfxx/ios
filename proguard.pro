# Kanged from Lawnchair

# Optimization options.
-allowaccessmodification
##-dontoptimize
-dontpreverify
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-keepattributes InnerClasses, EnclosingMethod, *Annotation*, Signature, SourceFile, LineNumberTable

# Remove some Kotlin overhead
-processkotlinnullchecks remove

-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
}

# Common rules.
##-keep class com.android.** { *; }
-keep class android.window.** { *; }
-keep class android.view.** { *; }

-keepclasseswithmembernames class * {
    native <methods>;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keepclassmembers class * implements android.os.Parcelable {
  public static final ** CREATOR;
}

# Keep android:onClick methods referenced from XML layouts
-keepclassmembers class com.android.launcher3.dragndrop.AddItemActivity {
    public void onCancelClick(android.view.View);
    public void onPlaceAutomaticallyClick(android.view.View);
}
-keepclassmembers class com.android.launcher3.secondarydisplay.SecondaryDisplayLauncher {
    public void onAppsButtonClicked(android.view.View);
}
## BROADER RULE:
##-keepclassmembers class * extends android.app.Activity {
##    public void *(android.view.View);
##}

# Lawnchair specific rules.
#-keep class app.lawnchair.LawnchairProto$* { *; }
#-keep class app.lawnchair.LawnchairApp { *; }
#-keep class app.lawnchair.LawnchairLauncher { *; }
#-keep class app.lawnchair.compatlib.** { *; }

-keep,allowshrinking,allowoptimization class com.google.protobuf.Timestamp { *; }
-keepclassmembers class * extends com.google.protobuf.GeneratedMessageLite { <fields>; }

# TODO: Remove this after the change in https://github.com/ChickenHook/RestrictionBypass/pull/9 has been released.
# UPDATE: not needed anyway after changing chickenhook.restrictionbypass to lsposed.hiddenapibypass
-keep class org.chickenhook.restrictionbypass.** { *; }
