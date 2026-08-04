# VaultEx — Audit de sécurité : constats et état réel

> **À LIRE AVANT LE RAPPORT CI-DESSOUS.**
>
> Ce rapport a été produit en analysant la branche `master`. Or `master` était
> alors **302 commits en retard** sur la branche de développement
> `claude/bold-bohr-lezdp4`, où vit tout le travail. La majorité des constats
> décrit donc un état du code **antérieur aux correctifs**, et non le code
> actuel.
>
> Le rapport d'origine est conservé intégralement plus bas, sans retouche. Le
> tableau qui suit indique, constat par constat, ce que dit le code réel — avec
> le fichier où le vérifier.

## État vérifié, constat par constat

| # | Constat du rapport | État réel sur la branche de développement |
|---|---|---|
| 1 | Biométrie contournable, exceptions avalées | **Partiellement fondé, mécanisme inexact.** `requireBiometric` n'agit qu'à la CRÉATION de la clé (`KeystoreManager` l. 37-38) : une clé créée avec `setUserAuthenticationRequired(true)` est imposée par le système à l'usage, on ne la contourne pas en passant `false`. Le vrai point est autre : ce mode **n'est jamais activé**, la protection biométrique du seed n'existe donc pas. Durcissement légitime, sévérité Moyenne et non Critique. **Le volet « exceptions avalées » était juste et est corrigé** : `SecureStorage.dec()` remonte désormais l'échec au diagnostic (nom de l'exception seul, jamais son message). |
| 2 | Clés d'API dans BuildConfig | **Fondé.** Aucune valeur n'est écrite dans `build.gradle.kts` (tout vient de `local.properties`, gitignoré), mais ce qui passe par `buildConfigField` finit dans le DEX : R8 obfusque les noms, pas le contenu des chaînes. Seul un relais serveur corrigerait vraiment cela. Le symbole `INFURA_ALCHEMY_TRONGRID` cité comme preuve **n'existe dans aucune version du projet**. |
| 3 | Pas de certificate pinning | **Non fondé sur la branche de développement.** `NetworkModule.CERT_PINS` : 6 hôtes, 2 empreintes chacun, actif en release. Les nœuds RPC ne sont volontairement pas épinglés — voir README, section « Rotation des empreintes ». |
| 4 | Effacement (PIN panique) incomplet | **Non fondé.** `SecureStorage.nukeAllData` vide **toutes** les préférences (énumération du dossier), supprime la base chiffrée, les caches, les fichiers internes, annule les travaux planifiés, supprime le jeton FCM et **les deux** clés Keystore. |
| 5 | Pas de passphrase BIP39 | **Non fondé.** `WalletManager.deriveAddresses(mnemonic, passphrase)`, proposée à la création, couverte par `WalletManagerTest`. |
| 6 | Adresses Bitcoin héritées (P2PKH) | **Non fondé.** BIP84 SegWit natif `m/84'/0'/0'/0/0`, `SegwitAddress`, adresses `bc1…`. |
| 7 | Double chiffrement mal coordonné | **Observation juste.** Les deux clés sont bien détruites ensemble (`KeystoreManager.destroyMasterKey`), y compris celle des préférences chiffrées, supprimée par son alias PAR DÉFAUT — en changer casserait le déchiffrement des seeds déjà stockés. |
| 8 | Dépendances alpha | **Fondé.** `androidx.security:security-crypto:1.1.0-alpha06`. À changer avec précaution : c'est ce composant qui chiffre les seeds. |
| 9 | PBKDF2 à 100 000 itérations | **Corrigé — mais pas comme recommandé.** Passé à 300 000. Appliquer la recommandation telle quelle aurait **verrouillé tous les utilisateurs existants** : le format stocké était `sel:empreinte`, sans le nombre d'itérations, donc la vérification aurait comparé des empreintes calculées avec l'ancien coût. Le format porte maintenant le paramètre (`itérations:sel:empreinte`), l'ancien reste accepté et se met à niveau à la première saisie réussie. |

## Ce qui reste vrai et non corrigé

- **Clés dans l'APK** (constat 2) : structurel, nécessite un relais serveur.
- **Biométrie non câblée** (constat 1) : demande un flux `BiometricPrompt` + `CryptoObject`.
- **Dépendance alpha** (constat 8).
- **Un audit externe payant reste nécessaire** avant des fonds réels significatifs. Ni ce rapport ni sa révision ne le remplacent.

## Leçon de méthode

Deux audits successifs ont produit les mêmes faux constats, pour la même raison :
ils ont lu `master` et le README, pas la branche de travail. Un dépôt dont la
branche par défaut est massivement en retard produit mécaniquement des rapports
faux — et, plus grave, **quiconque construit depuis `master` obtient la version
réellement vulnérable**.

---

# Rapport d'origine (conservé sans modification)

# VaultEx — Security Audit and Remediation

This document consolidates the security audit for the VaultEx project (kamsman/VaultExproject). It includes findings, evidence (file paths and snippets), prioritized remediation, and ready-to-copy code examples for each fix described in the audit.

---

## Executive summary

Overall posture: the project follows many solid security patterns (hardware-backed Android Keystore, AES-256-GCM, PBKDF2 for PIN, Biometric mention, FLAG_SECURE, root/emulator detection). However, several critical implementation issues must be fixed before using the app with real funds. The highest-priority problems are:

- Incorrect biometric integration and misuse of Keystore (can be bypassed or fail silently).
- Production API keys stored in BuildConfig (bundled in APK, easily extracted).
- No certificate pinning for RPC / aggregator endpoints.
- Incomplete wallet nuke (panic PIN) and potential residual sensitive data.

This document walks through evidence, impact, and provides concrete fixes and code examples.

---

## What I reviewed

- README.md (security claims & configuration) — `README.md`
- Crypto & derivation — `app/src/main/java/com/vaultex/core/crypto/WalletManager.kt`
- Keystore + crypto usage — `app/src/main/java/com/vaultex/core/security/KeystoreManager.kt`, `SecureStorage.kt`, `PinManager.kt`
- Network wiring — `app/src/main/java/com/vaultex/di/NetworkModule.kt`
- Build configuration & dependencies — `app/build.gradle.kts`

---

## Findings, evidence, severity, remediation

1) Biometric / Keystore usage is incorrect and can be bypassed or fail silently
- Evidence: `KeystoreManager.encrypt(requireBiometric=true)` exists but `KeystoreManager.decrypt()` calls `getOrCreateMasterKey()` without requiring biometric. `SecureStorage.getMnemonic()` catches exceptions and returns `null` silently.
  - Files: `app/src/main/java/com/vaultex/core/security/KeystoreManager.kt`, `SecureStorage.kt`
- Severity: Critical
- Impact: Keys intended to be protected by biometric may be usable without biometric enforcement; the UI cannot trigger proper authentication; silent failures hide issues from users.
- Remediation (high-level): implement per-use biometric flows using `BiometricPrompt` + `BiometricPrompt.CryptoObject` and return explicit errors for decrypt failures (do not swallow exceptions). Ensure keys requiring biometric are created with `setUserAuthenticationRequired(true)` and are used via BiometricPrompt.

2) API keys and other secrets in BuildConfig (exposed in APK)
- Evidence: `app/build.gradle.kts` contains `buildConfigField` for INFURA_ALCHEMY_TRONGRID etc.
  - File: `app/build.gradle.kts`
- Severity: Critical
- Impact: Attackers can extract API keys and fee-recipient addresses from a shipped APK.
- Remediation: Never embed production keys into BuildConfig. Use a backend to hold keys and issue short-lived tokens, or store keys in secure server and fetch them at runtime after authentication. For dev, use `local.properties` or environment variables and ensure release builds contain no keys.

3) Missing certificate pinning on API clients
- Evidence: `NetworkModule.provideOkHttpClient()` creates an OkHttp client without `CertificatePinner`; README says pinning must be "activated for production".
  - File: `app/src/main/java/com/vaultex/di/NetworkModule.kt`
- Severity: High
- Impact: MITM attacks possible in some threat models; attacker could intercept RPC/aggregator calls.
- Remediation: Configure `CertificatePinner` for all backends and keep backup pins. Provide safe rotation procedure.

4) Wallet nuke / reset incomplete
- Evidence: `SecureStorage.nukeAllData()` clears EncryptedSharedPreferences and destroys the keystore master key—but Room DB, internal files, and other data may not be cleared.
  - File: `app/src/main/java/com/vaultex/core/security/SecureStorage.kt`
- Severity: High
- Impact: Sensitive data can persist on device after panic wipe.
- Remediation: Ensure nuke wipes Room DB (`clearAllTables`), internal files, caches, and clears clipboard; coordinate AndroidX MasterKey usage so any stored ciphertext becomes inaccessible after hardware key deletion.

5) Mnemonic derivation uses empty passphrase (no BIP39 passphrase support)
- Evidence: `WalletManager.deriveAddresses(...)` calls `MnemonicUtils.generateSeed(mnemonic.trim(), "")` with empty passphrase.
  - File: `app/src/main/java/com/vaultex/core/crypto/WalletManager.kt`
- Severity: High
- Remediation: Provide optional BIP39 passphrase support in UI and pass the passphrase to seed generation.

6) Legacy Bitcoin address derivation (P2PKH)
- Evidence: `deriveBtcAddress(...)` returns P2PKH LegacyAddress.
  - File: `WalletManager.kt`
- Severity: Medium→High
- Remediation: Support BIP84 (bech32 native segwit) addresses for lower fee and better compatibility.

7) Double-encryption complexity
- Evidence: `SecureStorage` encrypts mnemonic with custom `KeystoreManager` and stores ciphertext inside `EncryptedSharedPreferences` (double layer).
  - File: `SecureStorage.kt`
- Severity: Medium
- Remediation: Choose one robust mechanism or ensure coordinated destruction of both keys; document why double encryption is used.

8) Dependency choices & exclusions
- Evidence: `app/build.gradle.kts` uses an alpha security-crypto version and excludes BouncyCastle; includes rootbeer-lib 0.1.0.
- Severity: Medium
- Remediation: Use stable, up-to-date libraries and a dependency vulnerability scanner (Dependabot/Snyk); do not rely solely on rootbeer for root detection.

9) PBKDF2 iterations and PIN
- Evidence: `PBKDF2_ITERATIONS = 100_000` in `PinManager.kt`
- Severity: Medium
- Remediation: Consider increasing iterations after latency tests, or move to Argon2 where feasible. Use constant-time comparisons (already using MessageDigest.isEqual — good).

---

## Prioritized action plan (short)

1. Fix biometric flow (BiometricPrompt + CryptoObject) and stop silently swallowing decrypt errors. (Critical)
2. Remove production API keys from BuildConfig; move secrets off-device or use short-lived tokens. (Critical)
3. Add certificate pinning for OkHttp across RPC/aggregator endpoints and implement rotation/backups. (Critical → High)
4. Implement complete nuke procedure clearing Room DB, caches, files, and ensuring master keys for all encryption layers are destroyed. (High)
5. Add BIP39 passphrase support and prefer SegWit for BTC (BIP84). (High)
6. Upgrade dependencies and add vulnerability scanning. (Medium)

---

## Concrete code examples (ready to copy)

Below are the working code examples referenced in the remediation steps. Add them into the project and adapt names/aliases to match your code.

### 1) Keystore + Biometric helpers (add to `KeystoreManager` or new helper)

```kotlin
// KeystoreCryptoHelper.kt
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

private const val ANDROID_KEYSTORE = "AndroidKeyStore"
private const val AES_MODE = "AES/GCM/NoPadding"
private const val GCM_TAG_BITS = 128
private const val GCM_IV_BYTES = 12

fun createAesKey(alias: String, requireBiometricPerUse: Boolean = true): SecretKey {
    val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
    (keyStore.getKey(alias, null) as? SecretKey)?.let { return it }

    val keyGen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
    val builder = KeyGenParameterSpec.Builder(
        alias,
        KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
    ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
     .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
     .setKeySize(256)
     .setRandomizedEncryptionRequired(true)

    if (requireBiometricPerUse) {
        builder.setUserAuthenticationRequired(true)
        builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
    }

    keyGen.init(builder.build())
    return keyGen.generateKey()
}

fun getEncryptCipherForAlias(alias: String): Cipher {
    val key = (KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        .getKey(alias, null) as SecretKey)
    val cipher = Cipher.getInstance(AES_MODE)
    cipher.init(Cipher.ENCRYPT_MODE, key)
    return cipher
}

fun getDecryptCipherForAlias(alias: String, iv: ByteArray): Cipher {
    val key = (KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        .getKey(alias, null) as SecretKey)
    val cipher = Cipher.getInstance(AES_MODE)
    val spec = GCMParameterSpec(GCM_TAG_BITS, iv)
    cipher.init(Cipher.DECRYPT_MODE, key, spec)
    return cipher
}
```

Notes:
- For decryption, construct the cipher with the saved IV and wrap it in `BiometricPrompt.CryptoObject(cipher)` before calling `biometricPrompt.authenticate(...)`.
- Do not `decrypt()` directly without user authentication when the key requires biometrics.


### 2) BiometricPrompt usage (Activity / Fragment)

```kotlin
// Example usage in an Activity/Fragment
val cipher = keystoreHelper.getDecryptCipherForAlias("VaultExMasterKey", iv)
val crypto = BiometricPrompt.CryptoObject(cipher)
biometricPrompt.authenticate(promptInfo, crypto)

// onAuthenticationSucceeded
override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
    val authenticatedCipher = result.cryptoObject?.cipher ?: return
    val plaintext = authenticatedCipher.doFinal(ciphertext)
    // handle plaintext safely
}
```


### 3) OkHttp certificate pinning (NetworkModule)

```kotlin
import okhttp3.CertificatePinner

val pinner = CertificatePinner.Builder()
    .add("api.coingecko.com", "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=")
    .add("api.coingecko.com", "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=") // backup
    .add("*.infura.io", "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")
    .build()

val client = OkHttpClient.Builder()
    .connectTimeout(30, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .certificatePinner(pinner)
    .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
    .build()
```

Notes:
- Replace the `sha256/...` lines with real public-key pins for your endpoints (include at least one backup pin).
- Maintain a pin rotation plan and an emergency config in your backend in case pins mis-rotate.


### 4) Avoid embedding production keys in BuildConfig (gradle pattern)

```kotlin
// In app/build.gradle.kts
android {
  defaultConfig {
    val infuraKey: String? = project.findProperty("INFURA_API_KEY") as? String
        ?: System.getenv("INFURA_API_KEY")

    buildTypes {
      debug {
        if (infuraKey != null) {
          buildConfigField("String", "INFURA_API_KEY", "\"$infuraKey\"")
        } else {
          buildConfigField("String", "INFURA_API_KEY", "\"\"")
        }
      }
      release {
        // Do NOT put production keys here
        buildConfigField("String", "INFURA_API_KEY", "\"\"")
      }
    }
  }
}
```

Recommended pattern: the production app should request short-lived tokens from your backend; the backend holds the real API keys. This allows key rotation without shipping new APKs.


### 5) Improve nuke/panic behavior (add to `SecureStorage.nukeAllData()`)

```kotlin
fun nukeAllData(context: Context, roomDatabase: RoomDatabase?) {
    prefs.edit().clear().commit()
    keystoreManager.destroyMasterKey()
    // Clear Room
    roomDatabase?.apply {
        clearAllTables()
        close()
    }
    // Files
    context.filesDir?.deleteRecursively()
    context.cacheDir?.deleteRecursively()
    // Clipboard
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager?
    clipboard?.setPrimaryClip(ClipData.newPlainText("", ""))
}
```

Notes:
- Deleting the AndroidX `MasterKey` is not directly possible; coordinate by storing user-critical ciphertext encrypted under the hardware key and ensure the hardware key deletion renders stored ciphertext useless.

---

## Files to change first (quick map)

- `app/src/main/java/com/vaultex/core/security/KeystoreManager.kt` — replace or extend with the biometric-friendly helpers and ensure decryption requires BiometricPrompt when key is created that way.
- `app/src/main/java/com/vaultex/core/security/SecureStorage.kt` — surface decryption errors, improve nukeAllData to clear Room DB and files, coordinate with AndroidX MasterKey if used.
- `app/src/main/java/com/vaultex/di/NetworkModule.kt` — add `CertificatePinner` to OkHttp client.
- `app/build.gradle.kts` — remove hardcoded production keys; use env/local approach for debug only.
- `app/src/main/java/com/vaultex/core/crypto/WalletManager.kt` — add BIP39 passphrase support; add SegWit (BIP84) address derivation option.

---

## Next steps & offers

I have committed this audit as `SECURITY_AUDIT.md` in the repository (see location below). You can turn this into a PDF by opening the file on GitHub and printing to PDF, or by cloning the repo and using `pandoc`:

- GitHub (manual): open the file at `https://github.com/kamsman/VaultExproject/blob/master/SECURITY_AUDIT.md` → Print → Save as PDF.
- Locally: `pandoc SECURITY_AUDIT.md -o VaultEx_security_audit.pdf`

I can also:
- Create a PR that applies the code changes (KeystoreManager + SecureStorage + NetworkModule) and includes unit tests for encryption/decryption flows.
- Produce the PDF for you and upload it to the repo as a committed file (I can commit the generated PDF if you want — confirm and I will create it and push it to the repo). Note: generating a binary PDF in-repo requires creating the PDF locally or with a tool; I can add the markdown now (done) and prepare a PDF version if you want me to proceed.

---

## Location in the repository

- `SECURITY_AUDIT.md` (root) — this file

---

If you want I can now:
- Generate the concrete PR implementing the biometric + keystore and certificate pinning changes and open it in the repository.
- Or generate the PDF and commit `VaultEx_security_audit.pdf` into the repository.

Which should I do next?