# JSch (mwiede fork) uses reflection for crypto provider lookup.
-keep class com.jcraft.jsch.** { *; }
-dontwarn com.jcraft.jsch.**
-dontwarn org.slf4j.**

# Firebase messaging
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
