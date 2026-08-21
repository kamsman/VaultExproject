package com.vaultex.data.repository

import com.vaultex.data.remote.api.BinanceApi
import com.vaultex.data.remote.dto.CoinGeckoPriceDto
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Deuxième source de prix, indépendante de CoinGecko.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * POURQUOI CETTE CLASSE EXISTE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Toute la valorisation du portefeuille reposait sur un seul fournisseur,
 * dont le forfait gratuit plafonne à 10 000 appels par MOIS. Deux téléphones
 * de test ont suffi à l'épuiser. CoinGecko a alors répondu 429 à TOUT, et
 * l'application a montré ceci, capture d'écran à l'appui :
 *
 *     Solde total  $0,00        BTC  $0,00   Prix : $0   +0,0 %
 *                               ETH  $0,00   Prix : $0   +0,0 %
 *
 * Les fonds étaient intacts sur les chaînes — les soldes ne passent pas par
 * CoinGecko. Seule la conversion en dollars manquait. Mais sur un
 * portefeuille, « $0,00 » ne se lit pas « prix indisponible » : ça se lit
 * « ton argent a disparu ». C'est la pire panne possible pour la confiance,
 * et elle vient d'un quota, pas d'une erreur de calcul.
 *
 * Le prix collant (dernier cours connu) ne protège pas de ce cas : sur une
 * installation neuve, il n'y a AUCUN cours précédent à réutiliser. C'était
 * exactement la situation des testeurs.
 *
 * Réduire la consommation était nécessaire, mais pas suffisant : un quota
 * unique reste un point de défaillance unique. Il faut une seconde source.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * POURQUOI BINANCE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Le point d'entrée public de Binance ne demande ni clé, ni compte, ni
 * carte bancaire, et n'impose aucun quota mensuel — seulement une limite par
 * minute, hors d'atteinte pour un portefeuille mobile. Il couvre toutes les
 * monnaies natives de l'application et la plupart des jetons importés.
 *
 * Ce n'est PAS un remplacement de CoinGecko : Binance ne connaît pas les
 * adresses de contrat, ni les capitalisations, ni les courbes historiques.
 * C'est un filet, interrogé uniquement quand la source principale n'a rien
 * rendu.
 *
 * ═══════════════════════════════════════════════════════════════════════
 * EUR ET FCFA SANS SERVICE DE CHANGE
 * ═══════════════════════════════════════════════════════════════════════
 *
 * Binance ne cote qu'en USDT. Il faut pourtant afficher des euros et des
 * FCFA, sans ajouter un troisième fournisseur qui aurait son propre quota et
 * sa propre panne.
 *
 * La paire EURUSDT donne le taux euro/dollar — c'est un marché coté en
 * continu, pas une donnée à aller chercher ailleurs. Et le FCFA est arrimé à
 * l'euro à parité FIXE et légale : 1 € = 655,957 FCFA, inchangé depuis la
 * création de l'euro. Les trois monnaies d'affichage se déduisent donc d'un
 * seul appel.
 */
@Singleton
class PriceFallbackSource @Inject constructor(
    private val api: BinanceApi
) {

    /**
     * Correspondance identifiant CoinGecko → symbole Binance.
     *
     * Limitée aux monnaies que l'application cote elle-même. Un identifiant
     * absent de cette table n'a simplement pas de repli : mieux vaut ça
     * qu'une correspondance devinée, qui afficherait le cours d'une autre
     * monnaie — l'erreur exact que la fiche jeton a déjà connue.
     */
    private val symboleParId = mapOf(
        "bitcoin" to "BTC",
        "ethereum" to "ETH",
        "binancecoin" to "BNB",
        "solana" to "SOL",
        "tron" to "TRX",
        "tether" to "USDT",
        "usd-coin" to "USDC",
        "dai" to "DAI",
        "shiba-inu" to "SHIB",
        "chainlink" to "LINK",
        "pancakeswap-token" to "CAKE",
        "ripple" to "XRP",
        "cardano" to "ADA",
        "dogecoin" to "DOGE",
        "polkadot" to "DOT",
        "avalanche-2" to "AVAX",
        "matic-network" to "MATIC",
        "litecoin" to "LTC"
    )

    /**
     * Paires dont l'existence chez Binance ne fait aucun doute.
     *
     * Ce groupe est délibérément MINIMAL : les cinq monnaies natives de
     * l'application, plus l'euro qui sert de pivot de conversion. Ce sont les
     * paires les plus échangées au monde ; aucune ne peut disparaître sans
     * préavis.
     *
     * Tout le reste — y compris les jetons du registre, pourtant connus — est
     * traité comme incertain. Une paire de jeton peut être retirée de la cote
     * du jour au lendemain, et Binance rejette l'appel ENTIER dès qu'un seul
     * symbole est inconnu. Mettre DAI ici, c'est accepter que son retrait de
     * la cote fasse disparaître le prix du Bitcoin.
     *
     * La règle : ce qui protège les monnaies natives ne partage jamais son
     * appel avec ce qui pourrait échouer.
     */
    private val pairesCertaines = setOf("BTC", "ETH", "BNB", "SOL", "TRX")

    /** Parité fixe et légale du franc CFA avec l'euro. Ce n'est pas un cours. */
    private val xofParEuro = 655.957

    /**
     * Cours mis en cache brièvement.
     *
     * L'accueil, l'écran Marché et la fiche d'un jeton peuvent se rafraîchir
     * en même temps. Sans ce cache, ils déclencheraient trois appels quasi
     * simultanés pour la même donnée.
     */
    @Volatile private var cache: Map<String, CoinGeckoPriceDto> = emptyMap()
    @Volatile private var cacheTime: Long = 0L
    private val cacheTtlMs = 60_000L

    /**
     * Cours des monnaies désignées par leur identifiant CoinGecko.
     *
     * La valeur de retour réutilise volontairement `CoinGeckoPriceDto` : le
     * repli se substitue à la source principale sans qu'aucun appelant ait à
     * distinguer d'où vient le prix.
     */
    suspend fun pricesByCoinGeckoId(ids: Collection<String>): Map<String, CoinGeckoPriceDto> {
        val voulus = ids.mapNotNull { id -> symboleParId[id]?.let { id to it } }
        if (voulus.isEmpty()) return emptyMap()
        val cours = quotes(voulus.map { it.second }.toSet())
        return voulus.mapNotNull { (id, sym) -> cours[sym]?.let { id to it } }.toMap()
    }

    /**
     * Cours des monnaies désignées par leur symbole (SHIB, DAI, CAKE…).
     *
     * C'est la voie utilisée pour les jetons importés par adresse de contrat :
     * Binance ne connaît pas les contrats, mais connaît les symboles. La
     * couverture n'est donc pas totale — un jeton non listé reste sans cours,
     * exactement comme avant. Aucune régression, seulement des cas en plus.
     */
    suspend fun pricesBySymbol(symbols: Collection<String>): Map<String, CoinGeckoPriceDto> =
        quotes(symbols.map { it.uppercase() }.toSet())

    /**
     * Cœur de la classe : un appel, tous les cours demandés.
     *
     * TROIS PRÉCAUTIONS, chacune pour un échec réel de cette API.
     *
     * 1. SÉPARER LES SYMBOLES SÛRS DES SYMBOLES INCERTAINS. Binance rejette
     *    l'appel ENTIER avec un code 400 dès qu'une seule paire n'existe pas.
     *    Un jeton exotique importé par l'utilisateur priverait donc de cours
     *    tout le portefeuille, Bitcoin compris. Les monnaies natives et
     *    l'euro — dont l'existence est certaine — partent dans un appel à
     *    part, qui ne peut pas être contaminé.
     *
     * 2. REPLI UNITAIRE. Si le groupe incertain échoue, chaque symbole est
     *    redemandé seul : les jetons réellement listés obtiennent leur cours,
     *    et seul l'intrus reste sans prix. Sans quota mensuel, ces quelques
     *    appels supplémentaires ne coûtent rien.
     *
     * 3. L'USDT N'A PAS DE PAIRE AVEC LUI-MÊME. `USDTUSDT` n'existe pas.
     *    Demandé tel quel, il déclencherait le 400 du point 1. Sa valeur est
     *    posée à 1 $ — c'est la définition même de ce jeton, et la fraction
     *    de pourcent d'écart possible ne pèse rien face à un « $0,00 ».
     */
    private suspend fun quotes(bases: Set<String>): Map<String, CoinGeckoPriceDto> {
        if (bases.isEmpty()) return emptyMap()
        val maintenant = System.currentTimeMillis()
        if (maintenant - cacheTime < cacheTtlMs && cache.keys.containsAll(bases)) return cache

        // L'euro sert de pivot vers l'EUR et le FCFA : toujours demandé.
        val surs = (bases.filter { it in pairesCertaines } + "EUR").toSet()
        val incertains = bases - surs - "USDT"

        val tickers = mutableMapOf<String, com.vaultex.data.remote.dto.BinanceTickerDto>()
        runCatching { api.getTickers(jsonArray(surs)) }
            .getOrNull()?.forEach { tickers[it.symbol] = it }

        if (incertains.isNotEmpty()) {
            val groupe = runCatching { api.getTickers(jsonArray(incertains)) }.getOrNull()
            if (groupe != null) {
                groupe.forEach { tickers[it.symbol] = it }
            } else {
                // Point 2 : un symbole inconnu a fait tomber le groupe.
                for (base in incertains) {
                    runCatching { api.getTickers(jsonArray(setOf(base))) }
                        .getOrNull()?.forEach { tickers[it.symbol] = it }
                }
            }
        }
        if (tickers.isEmpty()) return emptyMap()

        // 1 € vaut ce nombre d'USDT. Sans lui, on cote quand même en dollars
        // plutôt que de tout abandonner — un prix partiel vaut mieux que zéro.
        val eurUsd = tickers["EURUSDT"]?.lastPrice?.toDoubleOrNull()?.takeIf { it > 0.0 }

        val resultat = mutableMapOf<String, CoinGeckoPriceDto>()
        for (base in bases) {
            if (base == "USDT") {
                // Point 3 : le dollar-jeton vaut un dollar, par construction.
                resultat[base] = CoinGeckoPriceDto(
                    usd = 1.0,
                    eur = eurUsd?.let { 1.0 / it } ?: 0.0,
                    xof = eurUsd?.let { xofParEuro / it } ?: 0.0,
                    change24h = 0.0
                )
                continue
            }
            val t = tickers["${base}USDT"] ?: continue
            val usd = t.lastPrice.toDoubleOrNull() ?: continue
            if (usd <= 0.0) continue
            val eur = eurUsd?.let { usd / it } ?: 0.0
            resultat[base] = CoinGeckoPriceDto(
                usd = usd,
                eur = eur,
                xof = eur * xofParEuro,
                change24h = t.priceChangePercent.toDoubleOrNull() ?: 0.0
            )
        }
        if (resultat.isNotEmpty()) {
            cache = cache + resultat
            cacheTime = maintenant
        }
        return resultat
    }

    /** `["BTCUSDT","ETHUSDT"]` — le format exigé par le paramètre `symbols`. */
    private fun jsonArray(bases: Set<String>): String =
        bases.joinToString(",", "[", "]") { "\"${it}USDT\"" }
}
