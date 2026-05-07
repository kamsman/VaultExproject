package com.vaultex.data.local.db;

import androidx.annotation.NonNull;
import androidx.room.DatabaseConfiguration;
import androidx.room.InvalidationTracker;
import androidx.room.RoomDatabase;
import androidx.room.RoomOpenHelper;
import androidx.room.migration.AutoMigrationSpec;
import androidx.room.migration.Migration;
import androidx.room.util.DBUtil;
import androidx.room.util.TableInfo;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.sqlite.db.SupportSQLiteOpenHelper;
import com.vaultex.data.local.dao.AccountDao;
import com.vaultex.data.local.dao.AccountDao_Impl;
import com.vaultex.data.local.dao.ContactDao;
import com.vaultex.data.local.dao.ContactDao_Impl;
import com.vaultex.data.local.dao.PriceAlertDao;
import com.vaultex.data.local.dao.PriceAlertDao_Impl;
import com.vaultex.data.local.dao.TokenDao;
import com.vaultex.data.local.dao.TokenDao_Impl;
import com.vaultex.data.local.dao.TransactionDao;
import com.vaultex.data.local.dao.TransactionDao_Impl;
import com.vaultex.data.local.dao.WalletDao;
import com.vaultex.data.local.dao.WalletDao_Impl;
import java.lang.Class;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.processing.Generated;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class VaultExDatabase_Impl extends VaultExDatabase {
  private volatile WalletDao _walletDao;

  private volatile AccountDao _accountDao;

  private volatile TokenDao _tokenDao;

  private volatile TransactionDao _transactionDao;

  private volatile ContactDao _contactDao;

  private volatile PriceAlertDao _priceAlertDao;

  @Override
  @NonNull
  protected SupportSQLiteOpenHelper createOpenHelper(@NonNull final DatabaseConfiguration config) {
    final SupportSQLiteOpenHelper.Callback _openCallback = new RoomOpenHelper(config, new RoomOpenHelper.Delegate(1) {
      @Override
      public void createAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS `wallets` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `accounts` (`walletId` TEXT NOT NULL, `blockchain` TEXT NOT NULL, `address` TEXT NOT NULL, `publicKey` TEXT NOT NULL, `derivationPath` TEXT NOT NULL, `addressIndex` INTEGER NOT NULL, PRIMARY KEY(`walletId`, `blockchain`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `tokens` (`contractAddress` TEXT NOT NULL, `blockchain` TEXT NOT NULL, `symbol` TEXT NOT NULL, `name` TEXT NOT NULL, `decimals` INTEGER NOT NULL, `iconUrl` TEXT, `isCustom` INTEGER NOT NULL, `isHidden` INTEGER NOT NULL, PRIMARY KEY(`contractAddress`, `blockchain`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `transactions` (`hash` TEXT NOT NULL, `type` TEXT NOT NULL, `blockchain` TEXT NOT NULL, `fromAddress` TEXT NOT NULL, `toAddress` TEXT NOT NULL, `amount` TEXT NOT NULL, `tokenSymbol` TEXT NOT NULL, `fee` TEXT NOT NULL, `status` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, `confirmations` INTEGER NOT NULL, `blockNumber` INTEGER, PRIMARY KEY(`hash`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `contacts` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `addressesJson` TEXT NOT NULL, `notes` TEXT, `avatarColor` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS `price_alerts` (`id` TEXT NOT NULL, `tokenSymbol` TEXT NOT NULL, `condition` TEXT NOT NULL, `targetPrice` TEXT NOT NULL, `isActive` INTEGER NOT NULL, PRIMARY KEY(`id`))");
        db.execSQL("CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)");
        db.execSQL("INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '89eff80feccb2dedf4cc295bcd19f928')");
      }

      @Override
      public void dropAllTables(@NonNull final SupportSQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS `wallets`");
        db.execSQL("DROP TABLE IF EXISTS `accounts`");
        db.execSQL("DROP TABLE IF EXISTS `tokens`");
        db.execSQL("DROP TABLE IF EXISTS `transactions`");
        db.execSQL("DROP TABLE IF EXISTS `contacts`");
        db.execSQL("DROP TABLE IF EXISTS `price_alerts`");
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onDestructiveMigration(db);
          }
        }
      }

      @Override
      public void onCreate(@NonNull final SupportSQLiteDatabase db) {
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onCreate(db);
          }
        }
      }

      @Override
      public void onOpen(@NonNull final SupportSQLiteDatabase db) {
        mDatabase = db;
        internalInitInvalidationTracker(db);
        final List<? extends RoomDatabase.Callback> _callbacks = mCallbacks;
        if (_callbacks != null) {
          for (RoomDatabase.Callback _callback : _callbacks) {
            _callback.onOpen(db);
          }
        }
      }

      @Override
      public void onPreMigrate(@NonNull final SupportSQLiteDatabase db) {
        DBUtil.dropFtsSyncTriggers(db);
      }

      @Override
      public void onPostMigrate(@NonNull final SupportSQLiteDatabase db) {
      }

      @Override
      @NonNull
      public RoomOpenHelper.ValidationResult onValidateSchema(
          @NonNull final SupportSQLiteDatabase db) {
        final HashMap<String, TableInfo.Column> _columnsWallets = new HashMap<String, TableInfo.Column>(4);
        _columnsWallets.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWallets.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWallets.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsWallets.put("createdAt", new TableInfo.Column("createdAt", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysWallets = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesWallets = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoWallets = new TableInfo("wallets", _columnsWallets, _foreignKeysWallets, _indicesWallets);
        final TableInfo _existingWallets = TableInfo.read(db, "wallets");
        if (!_infoWallets.equals(_existingWallets)) {
          return new RoomOpenHelper.ValidationResult(false, "wallets(com.vaultex.data.local.entity.WalletEntity).\n"
                  + " Expected:\n" + _infoWallets + "\n"
                  + " Found:\n" + _existingWallets);
        }
        final HashMap<String, TableInfo.Column> _columnsAccounts = new HashMap<String, TableInfo.Column>(6);
        _columnsAccounts.put("walletId", new TableInfo.Column("walletId", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAccounts.put("blockchain", new TableInfo.Column("blockchain", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAccounts.put("address", new TableInfo.Column("address", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAccounts.put("publicKey", new TableInfo.Column("publicKey", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAccounts.put("derivationPath", new TableInfo.Column("derivationPath", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsAccounts.put("addressIndex", new TableInfo.Column("addressIndex", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysAccounts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesAccounts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoAccounts = new TableInfo("accounts", _columnsAccounts, _foreignKeysAccounts, _indicesAccounts);
        final TableInfo _existingAccounts = TableInfo.read(db, "accounts");
        if (!_infoAccounts.equals(_existingAccounts)) {
          return new RoomOpenHelper.ValidationResult(false, "accounts(com.vaultex.data.local.entity.AccountEntity).\n"
                  + " Expected:\n" + _infoAccounts + "\n"
                  + " Found:\n" + _existingAccounts);
        }
        final HashMap<String, TableInfo.Column> _columnsTokens = new HashMap<String, TableInfo.Column>(8);
        _columnsTokens.put("contractAddress", new TableInfo.Column("contractAddress", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("blockchain", new TableInfo.Column("blockchain", "TEXT", true, 2, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("symbol", new TableInfo.Column("symbol", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("decimals", new TableInfo.Column("decimals", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("iconUrl", new TableInfo.Column("iconUrl", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("isCustom", new TableInfo.Column("isCustom", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTokens.put("isHidden", new TableInfo.Column("isHidden", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTokens = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTokens = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTokens = new TableInfo("tokens", _columnsTokens, _foreignKeysTokens, _indicesTokens);
        final TableInfo _existingTokens = TableInfo.read(db, "tokens");
        if (!_infoTokens.equals(_existingTokens)) {
          return new RoomOpenHelper.ValidationResult(false, "tokens(com.vaultex.data.local.entity.TokenEntity).\n"
                  + " Expected:\n" + _infoTokens + "\n"
                  + " Found:\n" + _existingTokens);
        }
        final HashMap<String, TableInfo.Column> _columnsTransactions = new HashMap<String, TableInfo.Column>(12);
        _columnsTransactions.put("hash", new TableInfo.Column("hash", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("type", new TableInfo.Column("type", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("blockchain", new TableInfo.Column("blockchain", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("fromAddress", new TableInfo.Column("fromAddress", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("toAddress", new TableInfo.Column("toAddress", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("amount", new TableInfo.Column("amount", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("tokenSymbol", new TableInfo.Column("tokenSymbol", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("fee", new TableInfo.Column("fee", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("status", new TableInfo.Column("status", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("timestamp", new TableInfo.Column("timestamp", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("confirmations", new TableInfo.Column("confirmations", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsTransactions.put("blockNumber", new TableInfo.Column("blockNumber", "INTEGER", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysTransactions = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesTransactions = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoTransactions = new TableInfo("transactions", _columnsTransactions, _foreignKeysTransactions, _indicesTransactions);
        final TableInfo _existingTransactions = TableInfo.read(db, "transactions");
        if (!_infoTransactions.equals(_existingTransactions)) {
          return new RoomOpenHelper.ValidationResult(false, "transactions(com.vaultex.data.local.entity.TransactionEntity).\n"
                  + " Expected:\n" + _infoTransactions + "\n"
                  + " Found:\n" + _existingTransactions);
        }
        final HashMap<String, TableInfo.Column> _columnsContacts = new HashMap<String, TableInfo.Column>(5);
        _columnsContacts.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContacts.put("name", new TableInfo.Column("name", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContacts.put("addressesJson", new TableInfo.Column("addressesJson", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContacts.put("notes", new TableInfo.Column("notes", "TEXT", false, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsContacts.put("avatarColor", new TableInfo.Column("avatarColor", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysContacts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesContacts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoContacts = new TableInfo("contacts", _columnsContacts, _foreignKeysContacts, _indicesContacts);
        final TableInfo _existingContacts = TableInfo.read(db, "contacts");
        if (!_infoContacts.equals(_existingContacts)) {
          return new RoomOpenHelper.ValidationResult(false, "contacts(com.vaultex.data.local.entity.ContactEntity).\n"
                  + " Expected:\n" + _infoContacts + "\n"
                  + " Found:\n" + _existingContacts);
        }
        final HashMap<String, TableInfo.Column> _columnsPriceAlerts = new HashMap<String, TableInfo.Column>(5);
        _columnsPriceAlerts.put("id", new TableInfo.Column("id", "TEXT", true, 1, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPriceAlerts.put("tokenSymbol", new TableInfo.Column("tokenSymbol", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPriceAlerts.put("condition", new TableInfo.Column("condition", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPriceAlerts.put("targetPrice", new TableInfo.Column("targetPrice", "TEXT", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        _columnsPriceAlerts.put("isActive", new TableInfo.Column("isActive", "INTEGER", true, 0, null, TableInfo.CREATED_FROM_ENTITY));
        final HashSet<TableInfo.ForeignKey> _foreignKeysPriceAlerts = new HashSet<TableInfo.ForeignKey>(0);
        final HashSet<TableInfo.Index> _indicesPriceAlerts = new HashSet<TableInfo.Index>(0);
        final TableInfo _infoPriceAlerts = new TableInfo("price_alerts", _columnsPriceAlerts, _foreignKeysPriceAlerts, _indicesPriceAlerts);
        final TableInfo _existingPriceAlerts = TableInfo.read(db, "price_alerts");
        if (!_infoPriceAlerts.equals(_existingPriceAlerts)) {
          return new RoomOpenHelper.ValidationResult(false, "price_alerts(com.vaultex.data.local.entity.PriceAlertEntity).\n"
                  + " Expected:\n" + _infoPriceAlerts + "\n"
                  + " Found:\n" + _existingPriceAlerts);
        }
        return new RoomOpenHelper.ValidationResult(true, null);
      }
    }, "89eff80feccb2dedf4cc295bcd19f928", "d17385df8586459c3817f1258d5c72da");
    final SupportSQLiteOpenHelper.Configuration _sqliteConfig = SupportSQLiteOpenHelper.Configuration.builder(config.context).name(config.name).callback(_openCallback).build();
    final SupportSQLiteOpenHelper _helper = config.sqliteOpenHelperFactory.create(_sqliteConfig);
    return _helper;
  }

  @Override
  @NonNull
  protected InvalidationTracker createInvalidationTracker() {
    final HashMap<String, String> _shadowTablesMap = new HashMap<String, String>(0);
    final HashMap<String, Set<String>> _viewTables = new HashMap<String, Set<String>>(0);
    return new InvalidationTracker(this, _shadowTablesMap, _viewTables, "wallets","accounts","tokens","transactions","contacts","price_alerts");
  }

  @Override
  public void clearAllTables() {
    super.assertNotMainThread();
    final SupportSQLiteDatabase _db = super.getOpenHelper().getWritableDatabase();
    try {
      super.beginTransaction();
      _db.execSQL("DELETE FROM `wallets`");
      _db.execSQL("DELETE FROM `accounts`");
      _db.execSQL("DELETE FROM `tokens`");
      _db.execSQL("DELETE FROM `transactions`");
      _db.execSQL("DELETE FROM `contacts`");
      _db.execSQL("DELETE FROM `price_alerts`");
      super.setTransactionSuccessful();
    } finally {
      super.endTransaction();
      _db.query("PRAGMA wal_checkpoint(FULL)").close();
      if (!_db.inTransaction()) {
        _db.execSQL("VACUUM");
      }
    }
  }

  @Override
  @NonNull
  protected Map<Class<?>, List<Class<?>>> getRequiredTypeConverters() {
    final HashMap<Class<?>, List<Class<?>>> _typeConvertersMap = new HashMap<Class<?>, List<Class<?>>>();
    _typeConvertersMap.put(WalletDao.class, WalletDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(AccountDao.class, AccountDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TokenDao.class, TokenDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(TransactionDao.class, TransactionDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(ContactDao.class, ContactDao_Impl.getRequiredConverters());
    _typeConvertersMap.put(PriceAlertDao.class, PriceAlertDao_Impl.getRequiredConverters());
    return _typeConvertersMap;
  }

  @Override
  @NonNull
  public Set<Class<? extends AutoMigrationSpec>> getRequiredAutoMigrationSpecs() {
    final HashSet<Class<? extends AutoMigrationSpec>> _autoMigrationSpecsSet = new HashSet<Class<? extends AutoMigrationSpec>>();
    return _autoMigrationSpecsSet;
  }

  @Override
  @NonNull
  public List<Migration> getAutoMigrations(
      @NonNull final Map<Class<? extends AutoMigrationSpec>, AutoMigrationSpec> autoMigrationSpecs) {
    final List<Migration> _autoMigrations = new ArrayList<Migration>();
    return _autoMigrations;
  }

  @Override
  public WalletDao walletDao() {
    if (_walletDao != null) {
      return _walletDao;
    } else {
      synchronized(this) {
        if(_walletDao == null) {
          _walletDao = new WalletDao_Impl(this);
        }
        return _walletDao;
      }
    }
  }

  @Override
  public AccountDao accountDao() {
    if (_accountDao != null) {
      return _accountDao;
    } else {
      synchronized(this) {
        if(_accountDao == null) {
          _accountDao = new AccountDao_Impl(this);
        }
        return _accountDao;
      }
    }
  }

  @Override
  public TokenDao tokenDao() {
    if (_tokenDao != null) {
      return _tokenDao;
    } else {
      synchronized(this) {
        if(_tokenDao == null) {
          _tokenDao = new TokenDao_Impl(this);
        }
        return _tokenDao;
      }
    }
  }

  @Override
  public TransactionDao transactionDao() {
    if (_transactionDao != null) {
      return _transactionDao;
    } else {
      synchronized(this) {
        if(_transactionDao == null) {
          _transactionDao = new TransactionDao_Impl(this);
        }
        return _transactionDao;
      }
    }
  }

  @Override
  public ContactDao contactDao() {
    if (_contactDao != null) {
      return _contactDao;
    } else {
      synchronized(this) {
        if(_contactDao == null) {
          _contactDao = new ContactDao_Impl(this);
        }
        return _contactDao;
      }
    }
  }

  @Override
  public PriceAlertDao priceAlertDao() {
    if (_priceAlertDao != null) {
      return _priceAlertDao;
    } else {
      synchronized(this) {
        if(_priceAlertDao == null) {
          _priceAlertDao = new PriceAlertDao_Impl(this);
        }
        return _priceAlertDao;
      }
    }
  }
}
