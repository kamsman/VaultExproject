package com.vaultex.data.local.dao;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.vaultex.data.local.entity.TokenEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Object;
import java.lang.Override;
import java.lang.String;
import java.lang.SuppressWarnings;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import javax.annotation.processing.Generated;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlinx.coroutines.flow.Flow;

@Generated("androidx.room.RoomProcessor")
@SuppressWarnings({"unchecked", "deprecation"})
public final class TokenDao_Impl implements TokenDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TokenEntity> __insertionAdapterOfTokenEntity;

  private final SharedSQLiteStatement __preparedStmtOfSetHidden;

  public TokenDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTokenEntity = new EntityInsertionAdapter<TokenEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `tokens` (`contractAddress`,`blockchain`,`symbol`,`name`,`decimals`,`iconUrl`,`isCustom`,`isHidden`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TokenEntity entity) {
        statement.bindString(1, entity.getContractAddress());
        statement.bindString(2, entity.getBlockchain());
        statement.bindString(3, entity.getSymbol());
        statement.bindString(4, entity.getName());
        statement.bindLong(5, entity.getDecimals());
        if (entity.getIconUrl() == null) {
          statement.bindNull(6);
        } else {
          statement.bindString(6, entity.getIconUrl());
        }
        final int _tmp = entity.isCustom() ? 1 : 0;
        statement.bindLong(7, _tmp);
        final int _tmp_1 = entity.isHidden() ? 1 : 0;
        statement.bindLong(8, _tmp_1);
      }
    };
    this.__preparedStmtOfSetHidden = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE tokens SET isHidden = ? WHERE contractAddress = ? AND blockchain = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final TokenEntity token, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTokenEntity.insert(token);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object setHidden(final String address, final String blockchain, final boolean hidden,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfSetHidden.acquire();
        int _argIndex = 1;
        final int _tmp = hidden ? 1 : 0;
        _stmt.bindLong(_argIndex, _tmp);
        _argIndex = 2;
        _stmt.bindString(_argIndex, address);
        _argIndex = 3;
        _stmt.bindString(_argIndex, blockchain);
        try {
          __db.beginTransaction();
          try {
            _stmt.executeUpdateDelete();
            __db.setTransactionSuccessful();
            return Unit.INSTANCE;
          } finally {
            __db.endTransaction();
          }
        } finally {
          __preparedStmtOfSetHidden.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TokenEntity>> observeAll() {
    final String _sql = "SELECT * FROM tokens";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"tokens"}, new Callable<List<TokenEntity>>() {
      @Override
      @NonNull
      public List<TokenEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContractAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "contractAddress");
          final int _cursorIndexOfBlockchain = CursorUtil.getColumnIndexOrThrow(_cursor, "blockchain");
          final int _cursorIndexOfSymbol = CursorUtil.getColumnIndexOrThrow(_cursor, "symbol");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfDecimals = CursorUtil.getColumnIndexOrThrow(_cursor, "decimals");
          final int _cursorIndexOfIconUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "iconUrl");
          final int _cursorIndexOfIsCustom = CursorUtil.getColumnIndexOrThrow(_cursor, "isCustom");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final List<TokenEntity> _result = new ArrayList<TokenEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TokenEntity _item;
            final String _tmpContractAddress;
            _tmpContractAddress = _cursor.getString(_cursorIndexOfContractAddress);
            final String _tmpBlockchain;
            _tmpBlockchain = _cursor.getString(_cursorIndexOfBlockchain);
            final String _tmpSymbol;
            _tmpSymbol = _cursor.getString(_cursorIndexOfSymbol);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final int _tmpDecimals;
            _tmpDecimals = _cursor.getInt(_cursorIndexOfDecimals);
            final String _tmpIconUrl;
            if (_cursor.isNull(_cursorIndexOfIconUrl)) {
              _tmpIconUrl = null;
            } else {
              _tmpIconUrl = _cursor.getString(_cursorIndexOfIconUrl);
            }
            final boolean _tmpIsCustom;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsCustom);
            _tmpIsCustom = _tmp != 0;
            final boolean _tmpIsHidden;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp_1 != 0;
            _item = new TokenEntity(_tmpContractAddress,_tmpBlockchain,_tmpSymbol,_tmpName,_tmpDecimals,_tmpIconUrl,_tmpIsCustom,_tmpIsHidden);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
        }
      }

      @Override
      protected void finalize() {
        _statement.release();
      }
    });
  }

  @Override
  public Object getByBlockchain(final String blockchain,
      final Continuation<? super List<TokenEntity>> $completion) {
    final String _sql = "SELECT * FROM tokens WHERE blockchain = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, blockchain);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<List<TokenEntity>>() {
      @Override
      @NonNull
      public List<TokenEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfContractAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "contractAddress");
          final int _cursorIndexOfBlockchain = CursorUtil.getColumnIndexOrThrow(_cursor, "blockchain");
          final int _cursorIndexOfSymbol = CursorUtil.getColumnIndexOrThrow(_cursor, "symbol");
          final int _cursorIndexOfName = CursorUtil.getColumnIndexOrThrow(_cursor, "name");
          final int _cursorIndexOfDecimals = CursorUtil.getColumnIndexOrThrow(_cursor, "decimals");
          final int _cursorIndexOfIconUrl = CursorUtil.getColumnIndexOrThrow(_cursor, "iconUrl");
          final int _cursorIndexOfIsCustom = CursorUtil.getColumnIndexOrThrow(_cursor, "isCustom");
          final int _cursorIndexOfIsHidden = CursorUtil.getColumnIndexOrThrow(_cursor, "isHidden");
          final List<TokenEntity> _result = new ArrayList<TokenEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TokenEntity _item;
            final String _tmpContractAddress;
            _tmpContractAddress = _cursor.getString(_cursorIndexOfContractAddress);
            final String _tmpBlockchain;
            _tmpBlockchain = _cursor.getString(_cursorIndexOfBlockchain);
            final String _tmpSymbol;
            _tmpSymbol = _cursor.getString(_cursorIndexOfSymbol);
            final String _tmpName;
            _tmpName = _cursor.getString(_cursorIndexOfName);
            final int _tmpDecimals;
            _tmpDecimals = _cursor.getInt(_cursorIndexOfDecimals);
            final String _tmpIconUrl;
            if (_cursor.isNull(_cursorIndexOfIconUrl)) {
              _tmpIconUrl = null;
            } else {
              _tmpIconUrl = _cursor.getString(_cursorIndexOfIconUrl);
            }
            final boolean _tmpIsCustom;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsCustom);
            _tmpIsCustom = _tmp != 0;
            final boolean _tmpIsHidden;
            final int _tmp_1;
            _tmp_1 = _cursor.getInt(_cursorIndexOfIsHidden);
            _tmpIsHidden = _tmp_1 != 0;
            _item = new TokenEntity(_tmpContractAddress,_tmpBlockchain,_tmpSymbol,_tmpName,_tmpDecimals,_tmpIconUrl,_tmpIsCustom,_tmpIsHidden);
            _result.add(_item);
          }
          return _result;
        } finally {
          _cursor.close();
          _statement.release();
        }
      }
    }, $completion);
  }

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
