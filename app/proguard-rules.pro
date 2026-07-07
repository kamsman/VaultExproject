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

# ─────────────────────────────────────────────────────────────────────────
# Gson : (dé)sérialisation par RÉFLEXION sur le NOM des champs.
# L'app round-trip des data classes en JSON SANS @SerializedName (instantané
# portefeuille écrit par une classe et relu par d'autres, centre de
# notifications, caches marché…). En release, R8 renommerait les champs de
# façon incohérente entre classes → clés JSON qui ne correspondent plus
# (soldes affichés à 0, cache illisible). On préserve donc les noms de champs
# de tout le code applicatif.
-keep class com.google.gson.** { *; }
-dontwarn com.google.gson.**
-keep class * extends com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class com.vaultex.** { <fields>; }

# ─── Room : entités (champs référencés par le code généré + Gson) ───
-keep class com.vaultex.data.local.entity.** { *; }

# ─── SQLCipher (base Room chiffrée, chargée via JNI/réflexion) ───
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.zetetic.**
-dontwarn net.sqlcipher.**

# ─── OkHttp / Conscrypt (providers TLS optionnels référencés par réflexion) ───
-dontwarn org.conscrypt.**
-dontwarn org.openjsse.**
-dontwarn org.bouncycastle.jsse.**
