package com.vaultex.data.repository

import com.vaultex.data.remote.api.CoinGeckoApi
import com.vaultex.data.remote.dto.CoinGeckoChartDto
import com.vaultex.data.remote.dto.CoinGeckoMarketDto
import javax.inject.Inject

class MarketRepository @Inject constructor(
    private val api: CoinGeckoApi,
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context
) {

    // Cache DISQUE : la dernière liste marché survit au redémarrage → l'écran
    // Marché affiche les derniers prix connus même lancé HORS LIGNE.
    private val diskPrefs = appContext.getSharedPreferences("vaultex_market_cache", android.content.Context.MODE_PRIVATE)
    private val gson = com.google.gson.Gson()

    /** true si le dernier getMarkets() a servi des données de cache (hors ligne). */
    @Volatile var lastFromCache: Boolean = false
        private set

    private fun saveToDisk(list: List<CoinGeckoMarketDto>) {
        try {
            // Sparkline réduite à ≤ 40 points pour garder un JSON léger.
            val slim = list.map { d ->
                val p = d.sparkline_in_7d?.price
                if (p != null && p.size > 40) {
                    val step = p.size / 40
                    d.copy(sparkline_in_7d = com.vaultex.data.remote.dto.SparklineDto(
                        p.filterIndexed { i, _ -> i % step == 0 }))
                } else d
            }
            diskPrefs.edit().putString("markets", gson.toJson(slim)).apply()
        } catch (_: Exception) { }
    }

    private fun loadFromDisk(): List<CoinGeckoMarketDto> = try {
        val json = diskPrefs.getString("markets", null)
        if (json == null) emptyList()
        else gson.fromJson(json, Array<CoinGeckoMarketDto>::class.java)?.toList() ?: emptyList()
    } catch (_: Exception) { emptyList() }

    // Cache mémoire de la dernière liste marché (instance singleton partagée).
    // L'écran détail le réutilise → ouverture instantanée SANS nouvel appel
    // réseau, ce qui évite le rate-limit CoinGecko (cause des « impossible de
    // charger »). Tout est en USD pour que liste et détail soient cohérents.
    @Volatile
    private var cache: List<CoinGeckoMarketDto> = emptyList()
    @Volatile
    private var cacheTime: Long = 0L
    private val cacheTtlMs = 90_000L

    suspend fun getMarkets(): List<CoinGeckoMarketDto> {
        val now = System.currentTimeMillis()
        // Sert le cache si récent → bien moins d'appels CoinGecko (donc moins de
        // rate-limit) et le cache reste rempli pour l'écran détail.
        if (cache.isNotEmpty() && now - cacheTime < cacheTtlMs) return cache
        return try {
            val list = try {
                api.getMarkets(vsCurrency = "usd")
            } catch (e: Exception) {
                // Réseau lent / rate-limit passager : un réessai après 1,5 s.
                kotlinx.coroutines.delay(1500)
                api.getMarkets(vsCurrency = "usd")
            }
            if (list.isNotEmpty()) {
                cache = list
                cacheTime = now
                lastFromCache = false
                saveToDisk(list)
            }
            if (list.isNotEmpty()) list else cache
        } catch (e: Exception) {
            // Échec (hors ligne / rate-limit) : mémoire, puis DISQUE (survit au
            // redémarrage) — l'écran Marché n'est plus jamais vide hors ligne.
            lastFromCache = true
            if (cache.isNotEmpty()) return cache
            val disk = loadFromDisk()
            if (disk.isNotEmpty()) { cache = disk; return disk }
            throw e
        }
    }

    /**
     * Détail d'UNE pièce. D'abord le cache (0 appel réseau) ; sinon un appel
     * léger filtré (ids=) en repli, par ex. si on arrive directement sur le détail.
     */
    suspend fun getMarket(coinId: String): List<CoinGeckoMarketDto> {
        cache.firstOrNull { it.id == coinId }?.let { return listOf(it) }
        /*
        Cet appel N'AVAIT NI REESSAI NI REPLI DISQUE, contrairement à la liste
        marché. Un seul refus de CoinGecko — son quota gratuit est vite atteint —
        et la fonction levait une exception : l'écran détail affichait « $ — »,
        « +0,00 % » et un graphique vide, sur TOUTES les monnaies.

        Le plus frustrant : la liste marché enregistre déjà tout sur le DISQUE
        (prix ET courbe 7 j), mais l'écran détail n'allait jamais y regarder. Les
        données étaient là, sur le téléphone, inutilisées.

        Même résilience que getMarkets() désormais : réessai, puis repli disque.
         */
        return try {
            // sparkline=true → ramène aussi la courbe 7j (utilisée par l'écran
            // détail), ce qui évite un second appel réseau pour le graphique.
            val list = try {
                api.getMarkets(vsCurrency = "usd", sparkline = true, ids = coinId)
            } catch (_: Exception) {
                kotlinx.coroutines.delay(1500)   // rate-limit passager
                api.getMarkets(vsCurrency = "usd", sparkline = true, ids = coinId)
            }
            if (list.isNotEmpty()) {
                cache = (cache + list).distinctBy { it.id }
                lastFromCache = false
                list
            } else diskFallback(coinId)
        } catch (e: Exception) {
            // Ecran Marche vide = quota CoinGecko atteint, neuf fois sur dix.
            // Sans remontee, on ne peut pas distinguer « pas de reseau chez
            // l'utilisateur » de « notre quota est sature pour tout le monde ».
            com.vaultex.core.monitoring.reportUnlessCancelled("CoinGecko", e)
            lastFromCache = true
            diskFallback(coinId)
        }
    }

    /** Dernière donnée connue pour [coinId], relue du cache disque. */
    private fun diskFallback(coinId: String): List<CoinGeckoMarketDto> {
        val disk = loadFromDisk()
        if (disk.isEmpty()) return emptyList()
        cache = (cache + disk).distinctBy { it.id }
        val hit = disk.firstOrNull { it.id == coinId }
        return if (hit != null) listOf(hit) else emptyList()
    }

    /** Courbe de prix d'un token sur [days] jours (CoinGecko market_chart, USD). */
    suspend fun getMarketChart(coinId: String, days: Int): CoinGeckoChartDto {
        return api.getMarketChart(coinId, days = days)
    }

    /** Stats globales (cap. totale USD, variation 24 h, dominance BTC), avec cache. */
    data class GlobalStats(val totalMcapUsd: Double, val mcapChange24h: Double, val btcDominance: Double)

    @Volatile private var globalCache: GlobalStats? = null
    @Volatile private var globalTime: Long = 0L

    suspend fun getGlobal(): GlobalStats? {
        val now = System.currentTimeMillis()
        globalCache?.let { if (now - globalTime < cacheTtlMs) return it }
        val d = api.getGlobal().data ?: return globalCache
        val stats = GlobalStats(
            totalMcapUsd = d.totalMarketCap["usd"] ?: 0.0,
            mcapChange24h = d.mcapChange24h,
            btcDominance = d.marketCapPercentage["btc"] ?: 0.0
        )
        globalCache = stats
        globalTime = now
        return stats
    }
}
