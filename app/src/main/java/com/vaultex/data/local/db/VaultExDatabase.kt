package com.vaultex.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vaultex.data.local.dao.*
import com.vaultex.data.local.entity.*

@Database(
    entities = [
        WalletEntity::class,
        AccountEntity::class,
        TokenEntity::class,
        TransactionEntity::class,
        ContactEntity::class,
        PriceAlertEntity::class,
        PendingSendEntity::class
    ],
    version = 3,
    exportSchema = false
)
abstract class VaultExDatabase : RoomDatabase() {
    abstract fun walletDao(): WalletDao
    abstract fun accountDao(): AccountDao
    abstract fun tokenDao(): TokenDao
    abstract fun transactionDao(): TransactionDao
    abstract fun contactDao(): ContactDao
    abstract fun priceAlertDao(): PriceAlertDao
    abstract fun pendingSendDao(): PendingSendDao

    companion object {
        /*
        ═══════════════════════════════════════════════════════════════════
        UNE MIGRATION, PARCE QUE L'ALTERNATIVE EFFACE TOUT
        ═══════════════════════════════════════════════════════════════════

        La base est ouverte avec fallbackToDestructiveMigration() : sans
        migration déclarée, toute montée de version la DÉTRUIT et la
        reconstruit vide. Ajouter une colonne à une alerte de prix coûterait
        donc, à chaque utilisateur, son historique de transactions, son
        carnet d'adresses, ses alertes et sa file d'envois hors ligne.

        Les phrases de récupération survivent — elles vivent dans le
        stockage chiffré, pas ici — mais tout le reste part. C'est hors de
        proportion avec le confort qu'apporte la colonne.

        ALTER TABLE ADD COLUMN est l'opération la plus sûre de SQLite : les
        lignes existantes reçoivent la valeur par défaut, rien n'est
        recopié, rien n'est supprimé. La chaîne vide signifie « alerte
        créée avant ce choix », et le worker sait la lire.

        Le repli destructif RESTE en place. Il ne doit jamais être atteint,
        mais une migration oubliée vaut mieux qu'une application qui ne
        s'ouvre plus : le jour où ça arrive, l'utilisateur perd des données
        et garde ses fonds, au lieu de perdre l'accès à l'application.
        ═══════════════════════════════════════════════════════════════════
        */
        val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE price_alerts ADD COLUMN intention TEXT NOT NULL DEFAULT ''"
                )
            }
        }

        const val DATABASE_NAME = "vaultex.db"
    }
}
