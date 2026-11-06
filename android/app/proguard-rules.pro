# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keepclassmembers class kz.zhurt.messenger.data.** { *** Companion; }
-keepclasseswithmembers class kz.zhurt.messenger.data.** { kotlinx.serialization.KSerializer serializer(...); }
