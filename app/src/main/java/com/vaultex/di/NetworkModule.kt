package com.vaultex.di

import android.content.Context
import android.content.SharedPreferences
import com.vaultex.core.config.ApiKeys
import com.vaultex.data.remote.api.*
import com.vaultex.data.remote.api.EtherscanApi
import com.vaultex.data.repository.MarketRepository
import com.vaultex.data.repository.PriceRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    // ─── Base OkHttp client ───────────────────────────────────────────

    @Provides @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        /*
        ═══════════════════════════════════════════════════════════════════
        DÉLAIS D'ATTENTE — CALIBRÉS POUR UNE CONNEXION LENTE
        ═══════════════════════════════════════════════════════════════════

        Les trois valeurs étaient à 8 secondes, pour basculer vite sur un
        nœud RPC de secours. Le raisonnement était bon, la conséquence ne
        l'était pas.

        L'accueil déclenche une dizaine d'appels SIMULTANÉS — huit chaînes,
        les jetons, les cours. Sur la 3G du marché visé, ils se partagent
        une bande passante étroite : chacun attend son tour, et le compteur
        de 8 secondes court pendant cette attente. Les lectures les plus
        tardives expiraient donc alors que le réseau fonctionnait.

        C'est ce que produit le bandeau « Total incomplet : ETH, BNB, TRX
        illisibles ». Et la suite est pire qu'un bandeau : quand une lecture
        échoue, le solde précédent est conservé — protection volontaire pour
        ne pas afficher un faux zéro. Des fonds reçus restent donc INVISIBLES
        tant qu'aucune lecture n'aboutit. Constaté : un envoi confirmé sur la
        chaîne, absent de l'application, et réapparu après une réinstallation
        — la réinstallation ayant simplement effacé le solde mémorisé.

        On sépare donc les deux rôles au lieu de leur donner la même valeur :

        - CONNEXION (10 s) : détecte un hôte injoignable. C'est ce délai qui
          commande le basculement vers un nœud de secours, il reste court.
        - LECTURE (20 s) : attend une réponse déjà en route. L'allonger ne
          retarde aucun basculement — un nœud qui répond n'est pas en panne.
        ═══════════════════════════════════════════════════════════════════
         */
        val builder = OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            // Durcissement MITM : TLS 1.2+ uniquement (C-02)
            .connectionSpecs(
                listOf(okhttp3.ConnectionSpec.RESTRICTED_TLS, okhttp3.ConnectionSpec.MODERN_TLS)
            )
            .addInterceptor(HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.NONE })
            .addInterceptor(CertPinFailureReporter())

        // Certificate pinning (P1) — anti-MITM. Activé uniquement quand
        // ENABLE_CERT_PINNING=true ET que des empreintes réelles existent.
        if (com.vaultex.BuildConfig.ENABLE_CERT_PINNING) {
            buildCertificatePinner()?.let { builder.certificatePinner(it) }
        }
        return builder.build()
    }

    /**
     * Rend AUDIBLE un échec de certificate pinning.
     *
     * Sans ça, l'échec est parfaitement silencieux : OkHttp lève une
     * SSLPeerUnverifiedException que les couches supérieures traitent comme
     * une panne réseau ordinaire. L'utilisateur voit des soldes à zéro, et
     * rien ne distingue cette panne — présente sur TOUS les appareils, causée
     * par l'application elle-même — d'une simple coupure de connexion.
     *
     * L'intercepteur ne modifie rien : il relaie l'exception après l'avoir
     * signalée. Le message d'OkHttp contient la chaîne réelle présentée par
     * le serveur, donc les empreintes exactes à reporter dans CERT_PINS —
     * telles que les voit CET appareil, sur SON réseau.
     */
    private class CertPinFailureReporter : okhttp3.Interceptor {
        override fun intercept(chain: okhttp3.Interceptor.Chain): okhttp3.Response =
            try {
                chain.proceed(chain.request())
            } catch (e: javax.net.ssl.SSLPeerUnverifiedException) {
                runCatching {
                    com.vaultex.core.monitoring.AdminBot.certPinFailed(
                        chain.request().url.host, e.message
                    )
                }
                throw e
            }
    }

    /**
     * Empreintes SHA-256 (SPKI) des hôtes sensibles. À RENSEIGNER avant
     * d'activer le pinning, sinon toutes les connexions échoueraient.
     *
     * Pour obtenir l'empreinte d'un hôte :
     *   openssl s_client -connect api.changenow.io:443 -servername api.changenow.io < /dev/null 2>/dev/null \
     *     | openssl x509 -pubkey -noout \
     *     | openssl pkey -pubin -outform der \
     *     | openssl dgst -sha256 -binary | openssl enc -base64
     * Préfixer chaque valeur par "sha256/". Mettre au moins 2 pins par hôte
     * (certificat courant + backup) pour survivre aux rotations.
     */
    /*
    ═══════════════════════════════════════════════════════════════════════
    EMPREINTES DE CERTIFICATS — on épingle les AUTORITÉS, pas les feuilles.
    ═══════════════════════════════════════════════════════════════════════

    Chaque entrée épingle l'AC intermédiaire du serveur, plus sa racine en
    secours. Jamais le certificat du serveur lui-même.

    POURQUOI. Un certificat de serveur est renouvelé tous les 60 à 90 jours.
    Une empreinte de feuille devient donc fausse toute seule, sans qu'aucune
    ligne de code n'ait changé — et le jour où ça arrive, OkHttp refuse la
    connexion en silence. Aucun message, aucun plantage : juste des soldes à
    zéro et des écrans vides, en production, chez tous les utilisateurs à la
    fois. Les AC intermédiaires vivent des années, les racines des décennies.

    CE QUI EST ARRIVÉ ICI. La version précédente épinglait des feuilles
    périmées. Deux hôtes étaient cassés :

      · api.coingecko.com — empreinte ne correspondant plus à rien. CoinGecko
        fournissant TOUS les prix, chaque montant en monnaie s'affichait à
        0,00, y compris quand les soldes crypto se chargeaient correctement.

      · api.bscscan.com — un caractère faux dans l'empreinte : le chiffre « 1 »
        à la place de la lettre « l ». Recopie manuelle, confusion invisible à
        l'œil, accès à BscScan perdu.

    Les quatre autres hôtes avaient, eux, des empreintes valides.

    POUR RÉGÉNÉRER (Git Bash, une seule ligne) :

      for h in api.changenow.io api.flutterwave.com api.trongrid.io \
      api.etherscan.io api.bscscan.com api.coingecko.com; do echo "=== $h"; \
      openssl s_client -connect $h:443 -servername $h -showcerts </dev/null \
      2>/dev/null | awk '/BEGIN CERT/{n++} n{print > ("/tmp/p" n ".pem")}'; \
      for f in /tmp/p*.pem; do echo "   sha256/$(openssl x509 -in $f -pubkey \
      -noout | openssl pkey -pubin -outform der | openssl dgst -sha256 \
      -binary | openssl enc -base64)  <- $(openssl x509 -in $f -noout \
      -subject)"; done; rm -f /tmp/p*.pem; done

    Ne jamais recopier une empreinte à la main : copier-coller uniquement.

    LIMITE À CONNAÎTRE. Si un service change d'autorité de certification —
    passage de Google Trust Services à Cloudflare, par exemple — le pinning
    casse malgré tout. C'est le prix de cette protection. Vérifier ces
    empreintes avant chaque publication, et garder `cert.pinning=false` dans
    local.properties comme moyen de diagnostic rapide.

    Empreintes relevées le 5 août 2026.
    ═══════════════════════════════════════════════════════════════════════
     */
    /*
    ─── PORTÉE VOLONTAIREMENT RESTREINTE ──────────────────────────────────

    Seuls les hôtes où une interception TLS permettrait de DÉTOURNER DE
    L'ARGENT sont épinglés. Les fournisseurs de données ne le sont pas.

    Le raisonnement. Les clés privées ne quittent jamais l'appareil : aucune
    interception ne peut les voler. Ce qu'un attaquant pourrait faire varie
    donc énormément selon l'hôte :

      · api.changenow.io — CRITIQUE. Le service renvoie l'ADRESSE DE DÉPÔT
        d'un swap. Une réponse altérée redirige les fonds de l'utilisateur
        vers l'attaquant. Perte directe, irréversible.

      · api.coingecko.com, api.etherscan.io, api.bscscan.com — prix, cours et
        historique. Une réponse falsifiée affiche des chiffres faux. Trompeur,
        jamais coûteux : aucune transaction n'en dépend. Les soldes réels et
        la détection de dépôt passent par les nœuds RPC, pas par ces hôtes.

      · api.trongrid.io — épinglé par prudence : il sert aussi à construire
        des transactions TRON, pas seulement à lire des soldes.

      · api.flutterwave.com — retiré : l'API est câblée mais jamais appelée,
        aucun écran ne l'utilise.

    Pourquoi restreindre plutôt que tout épingler. Un pinning trop large est
    une panne en attente. Chaque hôte épinglé est un point de rupture : le
    jour où son autorité de certification change, l'application cesse de
    fonctionner d'un coup, chez tous les utilisateurs, sans qu'aucune ligne de
    code n'ait bougé. C'est arrivé trois fois pendant la mise au point — et à
    chaque fois pour des hôtes qui ne servaient qu'à afficher des chiffres.

    Le compromis retenu : la protection là où une interception coûterait de
    l'argent, la robustesse partout ailleurs.

    Empreintes relevées le 5 août 2026. Vérifier avant chaque publication.
    ───────────────────────────────────────────────────────────────────────
     */
    private val CERT_PINS: Map<String, List<String>> = mapOf(
        // ─ ChangeNOW : renvoie les adresses de dépôt de swap ─
        //   Google Trust Services : WE1 (intermédiaire) + GTS Root R4
        "api.changenow.io" to listOf(
            "sha256/kIdp6NNEd8wsugYyyIYFsi1ylMCED3hZbSR8ZFsa/A4=",
            "sha256/mEflZT5enoR1FuXLgYYGqnVEoZvmf9c2bVBpiOjYQ0c="
        ),
        // ─ TronGrid : construction de transactions TRON ─
        //   Amazon : RSA 2048 M04 (intermédiaire) + Amazon Root CA 1
        "api.trongrid.io" to listOf(
            "sha256/G9LNNAql897egYsabashkzUCTEJkWBzgoEtk8X/678c=",
            "sha256/++MBgDH5WGvL9Bcn5Be30cRcL0f5O+NyoXuWtQdX1aI="
        )
    )

    private fun buildCertificatePinner(): okhttp3.CertificatePinner? {
        if (CERT_PINS.isEmpty()) return null
        val b = okhttp3.CertificatePinner.Builder()
        CERT_PINS.forEach { (host, pins) -> pins.forEach { b.add(host, it) } }
        return b.build()
    }

    /**
     * Clients RPC nœud (#2) : bascule automatiquement sur un nœud public
     * secondaire si le primaire échoue (timeout / IOException). Les corps
     * JSON-RPC (EVM, Solana) et l'API REST Bitcoin (Blockstream/mempool)
     * sont compatibles entre primaire et secours.
     */
    private fun fallbackClient(
        base: OkHttpClient,
        prefs: SharedPreferences,
        prefKey: String,
        defaultBase: String,
        backupHosts: List<String>
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(DynamicBaseUrlInterceptor(prefs, prefKey, defaultBase))
        .addInterceptor(RpcFallbackInterceptor(backupHosts))
        .build()

    private fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
        Retrofit.Builder().baseUrl(baseUrl).client(client)
            .addConverterFactory(GsonConverterFactory.create()).build()

    /** Builds an OkHttpClient that rewrites the base URL from SharedPreferences on every request. */
    private fun dynamicClient(
        base: OkHttpClient,
        prefs: SharedPreferences,
        prefKey: String,
        defaultBase: String
    ): OkHttpClient = base.newBuilder()
        .addInterceptor(DynamicBaseUrlInterceptor(prefs, prefKey, defaultBase))
        .build()

    // ─── Fixed / non-configurable APIs ───────────────────────────────

    /*
    ═══════════════════════════════════════════════════════════════════════
    COURS : LE RELAIS D'ABORD, COINGECKO EN SECOURS
    ═══════════════════════════════════════════════════════════════════════

    Le quota gratuit de CoinGecko vaut pour la CLÉ, donc pour TOUTE
    l'application réunie : 10 000 appels par mois. À un millier par
    téléphone, une dizaine d'appareils l'épuisent — après quoi l'API répond
    429 à tout le monde en même temps et plus aucun prix ne s'affiche nulle
    part. C'est arrivé avec deux téléphones de test.

    Aucune optimisation embarquée ne peut lever ce plafond : mille
    utilisateurs regardant le Bitcoin à la même minute, c'est mille fois la
    même question. Le relais la pose UNE fois et sert la réponse à tous, si
    bien que le coût cesse de dépendre du nombre d'installations.

    POURQUOI LE CHANGEMENT SE RÉDUIT À UNE ADRESSE. Le relais imite les
    chemins et les formats de CoinGecko. Aucun modèle de données n'est
    réécrit ici, donc aucune occasion de se tromper sur un champ au passage.

    BASCULE AUTOMATIQUE. RpcFallbackInterceptor remplace l'hôte en
    conservant le chemin — écrit pour les nœuds blockchain, il convient
    exactement ici puisque les deux hôtes parlent la même langue. Relais
    injoignable, en panne ou saturé : l'application repart sur CoinGecko en
    direct, c'est-à-dire le comportement d'avant. Le pire scénario du relais
    est donc l'état actuel, jamais pire.

    RELAIS NON CONFIGURÉ (chaîne vide) : on appelle CoinGecko directement,
    sans intercepteur. Une compilation sur une machine qui ignore ce réglage
    reste pleinement fonctionnelle.
    ═══════════════════════════════════════════════════════════════════════
     */
    @Provides @Singleton
    fun provideCoinGeckoApi(client: OkHttpClient): CoinGeckoApi {
        val builder = client.newBuilder()

        // Clé Demo CoinGecko (header x-cg-demo-api-key) si renseignée : lève le
        // rate-limit qui faisait échouer le Marché. Sinon, API publique libre.
        // Toujours ajoutée : elle sert à l'appel direct comme à la bascule.
        if (ApiKeys.COINGECKO.isNotBlank()) {
            builder.addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("x-cg-demo-api-key", ApiKeys.COINGECKO)
                        .build()
                )
            }
        }

        val relais = ApiKeys.PRICE_RELAY.trim()
        if (relais.isBlank()) {
            return retrofit(COINGECKO_DIRECT, builder.build()).create(CoinGeckoApi::class.java)
        }
        // Barre oblique finale exigée par Retrofit sur une URL de base.
        val base = if (relais.endsWith("/")) relais else "$relais/"
        val avecBascule = builder
            .addInterceptor(RpcFallbackInterceptor(listOf(COINGECKO_DIRECT)))
            .build()
        return retrofit(base, avecBascule).create(CoinGeckoApi::class.java)
    }

    private const val COINGECKO_DIRECT = "https://api.coingecko.com/api/v3/"

    /**
     * Source de prix de SECOURS — voir PriceFallbackSource pour le pourquoi.
     *
     * Aucune clé n'est nécessaire : ce point d'entrée est public. Il n'est
     * volontairement PAS épinglé (certificate pinning) : le pinning est
     * réservé aux hôtes qui manipulent des fonds, et un épinglage périmé sur
     * une source de secours la rendrait inutile au moment précis où on en a
     * besoin. Aucune donnée sensible n'y transite — on demande un cours, on
     * n'envoie ni adresse ni identifiant.
     */
    /**
     * GeckoTerminal — prix par adresse de contrat, sans clé.
     *
     * Appelé DEPUIS LE TÉLÉPHONE et non depuis le relais : mesuré à « 429
     * Rate Limited » quand le relais l'interrogeait, l'adresse d'un serveur
     * Cloudflare étant partagée par des milliers de sites. Depuis une
     * connexion mobile, le même appel passe.
     */
    @Provides @Singleton
    fun provideGeckoTerminalApi(client: OkHttpClient): GeckoTerminalApi =
        retrofit("https://api.geckoterminal.com/", client).create(GeckoTerminalApi::class.java)

    @Provides @Singleton
    fun provideBinanceApi(client: OkHttpClient): BinanceApi =
        retrofit("https://api.binance.com/", client).create(BinanceApi::class.java)

    // ─── User-configurable RPC / explorer APIs ────────────────────────

    @Provides @Singleton @Named("eth")
    fun provideEthRpcApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): EvmRpcApi {
        // Ankr/free a fini par exiger une clé (403/429) → eth_call ETH échouait
        // (prix et détection de token KO) alors que BNB marchait. On passe sur
        // des nœuds publics ouverts et fiables, avec bascule automatique.
        val default = "https://ethereum-rpc.publicnode.com/"
        val backups = listOf(
            "https://eth.llamarpc.com/",
            "https://cloudflare-eth.com/",
            "https://rpc.ankr.com/eth/"
        )
        return retrofit(default, fallbackClient(client, rpcPrefs(ctx), "rpc_eth", default, backups))
            .create(EvmRpcApi::class.java)
    }

    @Provides @Singleton @Named("bnb")
    fun provideBnbRpcApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): EvmRpcApi {
        val default = "https://bsc-dataseed.binance.org/"
        val backups = listOf("https://bsc.publicnode.com/", "https://bsc-dataseed1.defibit.io/")
        return retrofit(default, fallbackClient(client, rpcPrefs(ctx), "rpc_bnb", default, backups))
            .create(EvmRpcApi::class.java)
    }

    @Provides @Singleton
    fun provideBitcoinApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): BitcoinApi {
        val default = "https://blockstream.info/api/"
        val backups = listOf("https://mempool.space/api/") // API REST compatible Blockstream
        return retrofit(default, fallbackClient(client, rpcPrefs(ctx), "rpc_btc", default, backups))
            .create(BitcoinApi::class.java)
    }

    @Provides @Singleton
    fun provideSolanaRpcApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): SolanaRpcApi {
        val default = "https://api.mainnet-beta.solana.com/"
        val backups = listOf("https://solana-rpc.publicnode.com/", "https://api.mainnet-beta.solana.com/")
        return retrofit(default, fallbackClient(client, rpcPrefs(ctx), "rpc_sol", default, backups))
            .create(SolanaRpcApi::class.java)
    }

    @Provides @Singleton
    fun provideTronApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): TronApi {
        val default = "https://api.trongrid.io/"
        /*
        TronGrid est le SEUL fournisseur de cette API REST : contrairement à
        Ethereum, BNB, Bitcoin ou Solana, il n'existe pas de nœud de secours
        vers qui basculer. Sans clé, son quota gratuit est vite atteint et il
        répond 403/429 — TRX et USDT-TRC20 deviennent alors illisibles EN MÊME
        TEMPS, puisqu'ils partagent cet appel.

        Faute de repli possible, on réessaie sur place, après une courte pause :
        ces refus sont transitoires par nature. Cela ne remplace pas une clé
        d'API, mais évite qu'un pic passager fasse afficher un solde à zéro.
         */
        var trxClient = dynamicClient(client, rpcPrefs(ctx), "rpc_trx", default)
            .newBuilder()
            .addInterceptor(TransientRetryInterceptor(attempts = 3, pauseMs = 900))
            .build()
        // Clé TronGrid optionnelle (header TRON-PRO-API-KEY) — anti rate-limit
        if (ApiKeys.TRONGRID.isNotBlank()) {
            trxClient = trxClient.newBuilder().addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("TRON-PRO-API-KEY", ApiKeys.TRONGRID)
                        .build()
                )
            }.build()
        }
        return retrofit(default, trxClient).create(TronApi::class.java)
    }

    @Provides @Singleton @Named("etherscan")
    fun provideEtherscanApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): EtherscanApi {
        // V2 : hote unique pour toutes les chaines, distinguees par `chainid`.
        val default = "https://api.etherscan.io/v2/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_etherscan", default))
            .create(EtherscanApi::class.java)
    }

    @Provides @Singleton @Named("bscscan")
    fun provideBscScanApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): EtherscanApi {
        /*
        BNB Chain passe DESORMAIS par api.etherscan.io, pas par api.bscscan.com :
        en V2, toutes les chaines partagent le meme hote et se distinguent par
        `chainid=56`. La cle Etherscan couvre donc aussi BNB — plus besoin d'une
        cle BscScan separee.
         */
        val default = "https://api.etherscan.io/v2/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_bscscan", default))
            .create(EtherscanApi::class.java)
    }

    @Provides @Singleton
    fun provideChangeNowApi(
        @ApplicationContext ctx: Context, client: OkHttpClient
    ): ChangeNowApi {
        val default = "https://api.changenow.io/v1/"
        return retrofit(default, dynamicClient(client, rpcPrefs(ctx), "rpc_changenow", default))
            .create(ChangeNowApi::class.java)
    }

    @Provides @Singleton
    fun provideFlutterwaveApi(client: OkHttpClient): FlutterwaveApi {
        val authedClient = client.newBuilder()
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .addHeader("Authorization", "Bearer ${ApiKeys.FLUTTERWAVE}")
                        .addHeader("Content-Type", "application/json")
                        .build()
                )
            }.build()
        return retrofit("https://api.flutterwave.com/v3/", authedClient)
            .create(FlutterwaveApi::class.java)
    }

    @Provides @Singleton
    fun provideMarketRepository(
        api: CoinGeckoApi,
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context
    ): MarketRepository = MarketRepository(api, context)

    @Provides @Singleton
    fun providePriceRepository(api: CoinGeckoApi): PriceRepository = PriceRepository(api)

    // ─── Helpers ─────────────────────────────────────────────────────

    private fun rpcPrefs(ctx: Context): SharedPreferences =
        com.vaultex.core.security.RpcPrefs.get(ctx)
}

/**
 * On every request, replaces the Retrofit base URL with the value saved in SharedPreferences.
 * Falls back to [defaultBase] if no override is stored.
 *
 * Strategy: string-replace the default base URL prefix in the full request URL.
 * This preserves API paths (e.g., /address/{addr}) while swapping the host/scheme/port.
 */
private class DynamicBaseUrlInterceptor(
    private val prefs: SharedPreferences,
    private val prefKey: String,
    defaultBase: String
) : Interceptor {

    private val normalizedDefault = defaultBase.trimEnd('/') + "/"

    override fun intercept(chain: Interceptor.Chain): Response {
        val saved = (prefs.getString(prefKey, normalizedDefault) ?: normalizedDefault)
            .let { it.trimEnd('/') + "/" }

        val original = chain.request()
        if (saved == normalizedDefault) return chain.proceed(original)

        /*
        Réécriture par HttpUrl, et non par remplacement de texte.

        L'ancienne version faisait `requestUrl.replaceFirst(base, saved)` sur
        l'URL entière. Un remplacement de chaîne ne connaît pas la structure
        d'une URL : selon la valeur saisie par l'utilisateur, le chemin pouvait
        se retrouver dupliqué ou tronqué, et la requête partait vers une adresse
        silencieusement fausse. La classe voisine RpcFallbackInterceptor
        utilisait déjà la bonne méthode — les deux sont maintenant cohérentes.

        On ne remplace QUE l'origine (schéma, hôte, port) : chemin, paramètres
        et fragment de la requête d'origine sont préservés tels quels.
         */
        val override = saved.toHttpUrlOrNull()
            ?: return chain.proceed(original)          // valeur illisible : on ignore
        if (override.scheme != "https") return chain.proceed(original)

        val newUrl = original.url.newBuilder()
            .scheme(override.scheme)
            .host(override.host)
            .port(override.port)
            .build()
        return chain.proceed(original.newBuilder().url(newUrl).build())
    }
}

/**
 * Bascule RPC (#2). En cas d'échec réseau (IOException/timeout) ou de
 * réponse 5xx du nœud courant, rejoue la requête en remplaçant l'hôte
 * par un nœud de secours, jusqu'à épuisement de la liste.
 */
/**
 * Réessaie une requête refusée pour une raison PASSAGÈRE (quota dépassé, nœud
 * momentanément indisponible), avec une pause entre les tentatives.
 *
 * Utilisé pour les services sans nœud de secours : là où l'on ne peut pas
 * basculer ailleurs, insister un peu reste la seule option.
 */
private class TransientRetryInterceptor(
    private val attempts: Int,
    private val pauseMs: Long
) : Interceptor {

    private fun transient(code: Int) =
        code == 403 || code == 408 || code == 425 || code == 429 || code >= 500

    override fun intercept(chain: Interceptor.Chain): Response {
        var last: Response? = null
        repeat(attempts) { i ->
            // La requête est rejouée telle quelle : les corps produits par
            // Retrofit sont adossés à un tableau d'octets, donc relisables.
            val response = try {
                chain.proceed(chain.request())
            } catch (e: java.io.IOException) {
                // Panne réseau : on retente, sauf au dernier tour.
                if (i == attempts - 1) throw e
                Thread.sleep(pauseMs)
                return@repeat
            }
            if (!transient(response.code) || i == attempts - 1) return response
            response.close()
            Thread.sleep(pauseMs)
            last = null
        }
        return last ?: chain.proceed(chain.request())
    }
}

private class RpcFallbackInterceptor(
    private val backupBaseUrls: List<String>
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()

        // Codes qui doivent déclencher une bascule de nœud : 5xx (panne serveur)
        // mais AUSSI 403 (clé exigée), 408/425/429 (rate-limit). Les nœuds publics
        // gratuits renvoient souvent 403/429 sans être en panne — sinon eth_call
        // restait bloqué sur le primaire (cas ETH/Ankr).
        fun shouldFailover(code: Int): Boolean =
            code >= 500 || code == 403 || code == 408 || code == 425 || code == 429

        // Tentative sur le nœud courant
        val primary: Response? = try {
            val r = chain.proceed(original)
            if (!shouldFailover(r.code)) return r
            r.close()
            null
        } catch (_: Exception) {
            null
        }
        if (primary != null) return primary

        // Bascule successive sur les nœuds de secours
        for (backup in backupBaseUrls) {
            try {
                val backupHost = backup.toHttpUrlOrNull() ?: continue
                val newUrl = original.url.newBuilder()
                    .scheme(backupHost.scheme)
                    .host(backupHost.host)
                    .port(backupHost.port)
                    .build()
                val resp = chain.proceed(original.newBuilder().url(newUrl).build())
                if (!shouldFailover(resp.code)) return resp
                resp.close()
            } catch (_: Exception) {
                // essaie le suivant
            }
        }

        // Tout a échoué : on relance le primaire pour propager l'erreur réelle
        return chain.proceed(original)
    }
}
