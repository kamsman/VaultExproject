# VaultEx ProGuard rules

-keepattributes Signature, *Annotation*, EnclosingMethod, InnerClasses

# Web3j
-keep class org.web3j.** { *; }
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# BitcoinJ
-keep class org.bitcoinj.** { *; }

# BIP39/44
-keep class io.github.novacrypto.** { *; }

# Retrofit / OkHttp
-keepattributes Signature
-keepattributes Exceptions
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class retrofit2.** { *; }

# Kotlinx serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Hilt
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Room
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase

# Crash on obfuscation
-keepattributes SourceFile, LineNumberTable
-renamesourcefileattribute SourceFile

# Models
-keep class com.vaultex.domain.model.** { *; }
-keep class com.vaultex.data.remote.dto.** { *; }
