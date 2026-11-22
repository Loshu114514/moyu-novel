# Keep JNI entry points and the native engine bridge.
-keep class com.moyu.novel.local.LocalEngine { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}
