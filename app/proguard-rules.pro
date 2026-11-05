# PlainPad R8 规则

# ViewBinding 生成的类通过反射式 inflate 使用，保留
-keep class com.sunwenqing.plainpad.databinding.** { *; }

# 保留注解、内部类、泛型签名与行号，便于崩溃日志定位
-keepattributes *Annotation*, InnerClasses, Signature, SourceFile, LineNumberTable

# Kotlin 协程内部类
-dontwarn kotlinx.coroutines.**
