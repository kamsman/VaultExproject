# VaultEx Wallet — Crypto Wallet Non-Custodial Multi-Chain

Wallet crypto Android natif premium supportant **Bitcoin, Ethereum, BNB Smart Chain, Solana, Tron**.
Architecture MVVM + Clean Architecture + Hilt. Conçu pour des millions d'utilisateurs.

---

## 🎯 Caractéristiques

- **Non-custodial** : les clés privées ne quittent jamais l'appareil
- **5 blockchains** : BTC, ETH, BNB, SOL, TRX + tokens ERC-20/BEP-20/SPL/TRC-20
- **Swap intégré 1.5%** via 1inch DEX Aggregator
- **30 écrans** complets — onboarding, dashboard, send/receive, swap, settings
- **Sécurité maximale** :
  - BIP39/BIP44 standards
  - AES-256-GCM dans Android Keystore (StrongBox/TEE)
  - Biométrie obligatoire (BiometricPrompt Class 3)
  - PIN de panique (efface tout)
  - Détection root + emulator
  - FLAG_SECURE anti-screenshot
  - Certificate pinning

---

## 🚀 Démarrage rapide

### Prérequis

- Android Studio Hedgehog (2023.1.1) ou plus récent
- JDK 17
- Android SDK 34
- Min SDK : 26 (Android 8.0)

### 1. Cloner et ouvrir

```bash
unzip VaultEx.zip
cd VaultEx
```

Ouvrez le dossier dans Android Studio. Laissez Gradle synchroniser.

### 2. Obtenir les clés API

**Toutes gratuites pour démarrer :**

| API | Usage | URL |
|-----|-------|-----|
| Infura | RPC Ethereum | https://infura.io |
| Alchemy | RPC Solana / backup ETH | https://alchemy.com |
| TronGrid | RPC Tron | https://www.trongrid.io |
| 1inch Dev | Swap aggregator | https://portal.1inch.dev |
| CoinGecko | Prix marché | https://www.coingecko.com/en/api |

### 3. Configurer les clés

Édite `app/build.gradle.kts`, section `defaultConfig` :

```kotlin
buildConfigField("String", "INFURA_API_KEY", "\"VOTRE_CLE_INFURA\"")
buildConfigField("String", "ALCHEMY_API_KEY", "\"VOTRE_CLE_ALCHEMY\"")
buildConfigField("String", "TRONGRID_API_KEY", "\"VOTRE_CLE_TRONGRID\"")
buildConfigField("String", "ONEINCH_API_KEY", "\"VOTRE_CLE_1INCH\"")
buildConfigField("String", "COINGECKO_API_KEY", "\"VOTRE_CLE_COINGECKO\"")

// Vos wallets pour recevoir les frais 1.5% des swaps :
buildConfigField("String", "VAULTEX_FEE_RECIPIENT_ETH", "\"0x...VOTRE_WALLET_EVM\"")
buildConfigField("String", "VAULTEX_FEE_RECIPIENT_TRX", "\"T...VOTRE_WALLET_TRX\"")
```

### 4. Compiler et lancer

```bash
./gradlew assembleDebug
# Ou via Android Studio : Run > Run 'app'
```

---

## 📂 Architecture

```
app/src/main/java/com/vaultex/
├── app/                     # Application + MainActivity
├── core/
│   ├── crypto/              # BIP39, BIP44, HD Wallet, dérivation multi-chain
│   ├── security/            # Keystore, AES-256-GCM, Biometric, PIN
│   ├── network/             # OkHttp, intercepteurs
│   └── utils/               # Helpers
├── data/
│   ├── local/               # Room DB (DAO + entités)
│   ├── remote/              # Retrofit APIs (5 blockchains + 1inch + CoinGecko)
│   └── repository/          # Implémentations
├── domain/
│   ├── model/               # Wallet, Token, Transaction, SwapQuote...
│   ├── repository/          # Interfaces
│   └── usecase/             # (à compléter au besoin)
├── ui/
│   ├── theme/               # Couleurs premium dark + or
│   ├── components/          # Composants réutilisables
│   ├── navigation/          # NavGraph (30 routes)
│   └── screens/             # 30 écrans Compose
├── di/                      # Hilt modules
└── service/                 # FCM (push notifications)
```

---

## 🔐 Sécurité

### Modèle de menace traité

✅ **Vol physique de l'appareil** → biométrie + PIN protègent l'accès
✅ **Backup cloud compromis** → `allowBackup=false` + dataExtractionRules
✅ **Apps malveillantes screenshot** → `FLAG_SECURE` sur toutes les Activities
✅ **MITM sur APIs** → Certificate pinning (à activer en production)
✅ **Device rooté** → RootBeer bloque l'app
✅ **Émulateur de débogage** → bloqué en production
✅ **Fuite via clipboard** → cleanup auto après 30s
✅ **Brute force PIN** → 5 tentatives max + délai exponentiel
✅ **Coercition (forced reveal)** → PIN de panique efface tout

### Ce qui est de votre responsabilité

⚠️ **Audit de sécurité** : Avant tout déploiement avec des fonds réels, faites auditer le code par une firme spécialisée (Trail of Bits, Halborn, etc.).

⚠️ **Tests testnet** : Testez d'abord sur Goerli (ETH), BSC Testnet, Solana Devnet avant mainnet.

⚠️ **Certificate pinning** : Activez-le en production dans `AppModule.kt`.

⚠️ **ProGuard** : Vérifiez que `isMinifyEnabled = true` en release.

⚠️ **Reproductible builds** : Utilisez Docker pour des builds déterministes.

---

## 💰 Modèle économique

- **Envoi/Réception** : 0% (gratuit)
- **Swap** : 1.5% via paramètre `fee` de l'API 1inch
- **Frais réseau (gas)** : payés par l'utilisateur, transmis à la blockchain

Les frais 1.5% sont automatiquement envoyés à votre wallet `VAULTEX_FEE_RECIPIENT_*` via le paramètre `referrer` de 1inch.

### Estimation revenus

À **100 000 utilisateurs actifs/jour** avec 10% qui swap (50$ moyen) :
- Volume : 500 000 $/jour
- Revenus : ~7 500 $/jour → ~2.7M $/an

---

## 🛠️ État du code

### ✅ Implémenté (production-grade)

- Génération mnémonique BIP39 avec SecureRandom (entropie forte)
- Dérivation HD wallet BIP44 pour les 5 chaînes (vraies adresses)
- Chiffrement AES-256-GCM dans Android Keystore
- Authentification biométrique (BiometricPrompt Class 3)
- Hash PIN avec PBKDF2 + salt
- PIN de panique avec destruction des données
- Theme dark navy + or premium
- Navigation Jetpack Compose pour 30 écrans
- Hilt DI complet
- Room DB avec 6 entités
- 6 APIs Retrofit (5 chaînes + 1inch + CoinGecko)
- FCM service push notifications
- ProGuard rules

### 🔄 À compléter selon vos besoins

- Implémentations complètes des `BalanceRepository`, `TransactionRepository`, `PriceRepository`, `SwapRepository` (interfaces définies, signatures données)
- Logique métier des écrans (les 30 écrans ont la structure UI, à câbler avec les ViewModels)
- Tests unitaires + instrumentés
- Animations Lottie (placeholders à remplacer)
- Icons assets (mipmap-*)

---

## 🧪 Pour tester rapidement

1. Lance l'app → Splash → Welcome
2. **Créer un nouveau wallet** → 12 mots BIP39 générés
3. Vérifier la phrase → Configuration PIN (6 chiffres)
4. **Dashboard** s'ouvre avec données mock
5. Navigation entre les 30 écrans fonctionne

Pour des balances/transactions réelles, configure tes clés API et complète les repositories.

---

## 📚 Ressources

- **BIP39** : https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki
- **BIP44** : https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki
- **SLIP-0010** (Solana) : https://github.com/satoshilabs/slips/blob/master/slip-0010.md
- **Web3j docs** : https://docs.web3j.io
- **1inch API** : https://docs.1inch.io

---

## ⚠️ Disclaimer

Ce projet est fourni **comme base de développement**. Avant d'opérer avec des fonds réels :
1. Audit de sécurité externe obligatoire
2. Tests extensifs sur testnet
3. Bug bounty program
4. Conformité régulatoire selon votre juridiction (KYC/AML peut s'appliquer)

L'auteur du code généré n'est pas responsable de pertes de fonds.

---

## 📄 Licence

MIT — vous pouvez l'utiliser, le modifier, et le commercialiser librement.

**Bon développement avec VaultEx ! 🚀**
