package com.vaultex.ui.components

/**
 * Source unique des logos crypto, par TICKER → URL prévisible et stable.
 * Le même symbole donne donc le même logo partout dans l'app (et sur tout
 * appareil). Les variantes (ex. USDT-BNB) sont ramenées au ticker de base.
 *
 * Repo communautaire d'icônes par ticker (mis en cache par Coil après le
 * 1er chargement, donc disponible hors-ligne ensuite).
 */
object CryptoIcon {

    fun url(symbol: String): String {
        val ticker = symbol.substringBefore("-").trim().lowercase()
        return "https://raw.githubusercontent.com/spothq/cryptocurrency-icons/master/128/color/$ticker.png"
    }
}
