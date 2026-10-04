# kotlinx.serialization keeps generated serializers via its own consumer rules; keep our data models' names stable
# so persisted JSON written by one release is readable by the next.
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.wakeup.**$$serializer { *; }
-keepclassmembers class com.wakeup.** { *** Companion; }
-keepclasseswithmembers class com.wakeup.** { kotlinx.serialization.KSerializer serializer(...); }
-dontwarn org.slf4j.**
