# Politique de Confidentialité — VaultEx

**Dernière mise à jour : 24 juillet 2026**

Cette politique explique quelles données VaultEx (« l'Application ») traite, comment et pourquoi. Nous avons conçu VaultEx pour collecter **le strict minimum**.

---

## 1. En résumé

- **Nous ne demandons aucun compte**, aucun nom, aucun e-mail, aucun numéro de téléphone, aucune pièce d'identité.
- **Nous n'avons jamais accès** à votre phrase de récupération, à vos clés privées ni à vos fonds.
- **Nous ne vendons aucune donnée** et n'affichons aucune publicité.

## 2. Données qui restent sur votre appareil (jamais transmises)

Les informations suivantes sont stockées **uniquement sur votre téléphone**, sous forme chiffrée, et ne nous sont **jamais** envoyées :

- Votre **phrase de récupération** (12 mots) et vos clés privées, chiffrées via le module de sécurité matériel de l'appareil (Android Keystore).
- Votre **code PIN** (stocké sous forme de empreinte cryptographique, jamais en clair).
- Votre **carnet d'adresses**, l'historique local de vos transactions et vos préférences (langue, devise, alertes).
- La liste de vos jetons personnalisés.

Si vous désinstallez l'Application sans avoir sauvegardé votre phrase de récupération, ces données sont **définitivement perdues** — y compris pour nous.

## 3. Données techniques traitées

### 3.1 Adresses publiques de portefeuille

Pour afficher vos soldes, votre historique et vous notifier des dépôts, l'Application interroge des services d'infrastructure blockchain avec vos **adresses publiques** (jamais vos clés privées) :

- **Blockstream** (Bitcoin), **nœuds RPC publics** (Ethereum, BNB Chain, Solana), **TronGrid** (Tron)
- **Etherscan / BscScan** (historique des transactions)
- **CoinGecko** (cours des cryptomonnaies)

Une adresse publique est par nature **visible de tous sur la blockchain** : ces requêtes ne révèlent pas votre identité.

### 3.2 Notifications push (Firebase Cloud Messaging)

Si vous activez les notifications, l'Application enregistre auprès de nos serveurs (Google Firebase) :

- un **jeton de notification** anonyme propre à l'installation ;
- vos **adresses publiques** de portefeuille, afin de détecter les fonds reçus et vous en avertir.

Ces données servent **exclusivement** à l'envoi de notifications. Elles ne sont ni revendues, ni utilisées à des fins publicitaires.

### 3.3 Rapports d'anomalie et statistiques techniques

Afin de corriger les dysfonctionnements, l'Application peut transmettre des **informations techniques anonymes** :

- type d'erreur rencontrée et emplacement dans le code ;
- version de l'Application, version d'Android, langue de l'appareil ;
- événements techniques d'usage — installation, création ou import de portefeuille, envoi et réception (montant et cryptomonnaie concernés), échange réalisé ou échoué, sauvegarde de la phrase effectuée, ainsi que des jalons d'usage (premier dépôt, premier échange, portefeuille resté inutilisé) — associés à un **identifiant d'installation aléatoire** (ex. « VX-8F3A1C ») et à la date d'installation.

Ces informations **ne contiennent ni votre identité, ni vos adresses de portefeuille, ni vos clés, ni votre phrase de récupération**. Elles servent uniquement à assurer le bon fonctionnement et la sécurité du service.

### 3.4 Service d'échange tiers

Lorsque vous réalisez un échange, les informations nécessaires à l'opération (montant, cryptomonnaies concernées, adresse de réception, adresse de remboursement) sont transmises au prestataire d'échange **ChangeNOW**, qui les traite selon **sa propre politique de confidentialité**. Nous vous invitons à en prendre connaissance sur son site.

## 4. Autorisations demandées par l'Application

| Autorisation | Pourquoi |
|---|---|
| **Internet** | consulter les blockchains, les cours, envoyer des transactions |
| **Appareil photo** | scanner un QR code d'adresse (l'image n'est ni stockée ni transmise) |
| **Notifications** | vous avertir des fonds reçus et des alertes de prix |
| **Biométrie** | déverrouiller l'Application (traitée par Android, jamais transmise) |
| **Exemption d'optimisation batterie** *(facultative)* | permettre la détection des dépôts en arrière-plan |

## 5. Conservation et suppression

- Les données locales sont conservées tant que l'Application est installée. **Désinstaller l'Application les efface**, de même que la fonction « Effacer le wallet » de l'Application.
- Le jeton de notification est supprimé de nos serveurs lorsqu'il devient invalide (désinstallation, réinitialisation).
- Les rapports techniques anonymes sont conservés le temps nécessaire au diagnostic.

## 6. Sécurité

Nous mettons en œuvre des mesures de protection reconnues : chiffrement des données sensibles (AES-256), stockage des clés dans le module sécurisé de l'appareil, base de données locale chiffrée, communications chiffrées (HTTPS), protection contre les captures d'écran et détection des appareils compromis.

Aucun système n'étant infaillible, **la sécurité de votre phrase de récupération et de votre appareil relève de votre responsabilité**.

## 7. Enfants

VaultEx n'est pas destinée aux personnes de moins de 18 ans et nous ne collectons pas sciemment de données les concernant.

## 8. Vos droits

Comme nous ne collectons aucune donnée permettant de vous identifier, nous ne pouvons pas relier des données à votre personne. Vous gardez à tout moment le contrôle : désactiver les notifications, effacer les données de l'Application, ou la désinstaller.

## 9. Modifications

Cette politique peut évoluer. La version en vigueur est celle publiée dans l'Application, avec sa date de mise à jour.

## 10. Contact

Pour toute question relative à vos données :

- **WhatsApp :** +226 72 30 65 26
- **Telegram :** https://t.me/vaultexWallet
- **Facebook :** VaultEx
