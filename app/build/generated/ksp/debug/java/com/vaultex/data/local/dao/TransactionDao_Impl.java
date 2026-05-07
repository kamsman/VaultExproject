package com.vaultex.data.local.dao;

import android.database.Cursor;
import androidx.annotation.NonNull;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
import com.vaultex.data.local.entity.TransactionEntity;
import java.lang.Class;
import java.lang.Exception;
import java.lang.Long;
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
public final class TransactionDao_Impl implements TransactionDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<TransactionEntity> __insertionAdapterOfTransactionEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateStatus;

  public TransactionDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfTransactionEntity = new EntityInsertionAdapter<TransactionEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `transactions` (`hash`,`type`,`blockchain`,`fromAddress`,`toAddress`,`amount`,`tokenSymbol`,`fee`,`status`,`timestamp`,`confirmations`,`blockNumber`) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final TransactionEntity entity) {
        statement.bindString(1, entity.getHash());
        statement.bindString(2, entity.getType());
        statement.bindString(3, entity.getBlockchain());
        statement.bindString(4, entity.getFromAddress());
        statement.bindString(5, entity.getToAddress());
        statement.bindString(6, entity.getAmount());
        statement.bindString(7, entity.getTokenSymbol());
        statement.bindString(8, entity.getFee());
        statement.bindString(9, entity.getStatus());
        statement.bindLong(10, entity.getTimestamp());
        statement.bindLong(11, entity.getConfirmations());
        if (entity.getBlockNumber() == null) {
          statement.bindNull(12);
        } else {
          statement.bindLong(12, entity.getBlockNumber());
        }
      }
    };
    this.__preparedStmtOfUpdateStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE transactions SET status = ?, confirmations = ? WHERE hash = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insert(final TransactionEntity transaction,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfTransactionEntity.insert(transaction);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateStatus(final String hash, final String status, final int conf,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, status);
        _argIndex = 2;
        _stmt.bindLong(_argIndex, conf);
        _argIndex = 3;
        _stmt.bindString(_argIndex, hash);
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
          __preparedStmtOfUpdateStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<TransactionEntity>> observeAll() {
    final String _sql = "SELECT * FROM transactions ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<TransactionEntity>>() {
      @Override
      @NonNull
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfBlockchain = CursorUtil.getColumnIndexOrThrow(_cursor, "blockchain");
          final int _cursorIndexOfFromAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "fromAddress");
          final int _cursorIndexOfToAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "toAddress");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfTokenSymbol = CursorUtil.getColumnIndexOrThrow(_cursor, "tokenSymbol");
          final int _cursorIndexOfFee = CursorUtil.getColumnIndexOrThrow(_cursor, "fee");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfConfirmations = CursorUtil.getColumnIndexOrThrow(_cursor, "confirmations");
          final int _cursorIndexOfBlockNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "blockNumber");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionEntity _item;
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpBlockchain;
            _tmpBlockchain = _cursor.getString(_cursorIndexOfBlockchain);
            final String _tmpFromAddress;
            _tmpFromAddress = _cursor.getString(_cursorIndexOfFromAddress);
            final String _tmpToAddress;
            _tmpToAddress = _cursor.getString(_cursorIndexOfToAddress);
            final String _tmpAmount;
            _tmpAmount = _cursor.getString(_cursorIndexOfAmount);
            final String _tmpTokenSymbol;
            _tmpTokenSymbol = _cursor.getString(_cursorIndexOfTokenSymbol);
            final String _tmpFee;
            _tmpFee = _cursor.getString(_cursorIndexOfFee);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final int _tmpConfirmations;
            _tmpConfirmations = _cursor.getInt(_cursorIndexOfConfirmations);
            final Long _tmpBlockNumber;
            if (_cursor.isNull(_cursorIndexOfBlockNumber)) {
              _tmpBlockNumber = null;
            } else {
              _tmpBlockNumber = _cursor.getLong(_cursorIndexOfBlockNumber);
            }
            _item = new TransactionEntity(_tmpHash,_tmpType,_tmpBlockchain,_tmpFromAddress,_tmpToAddress,_tmpAmount,_tmpTokenSymbol,_tmpFee,_tmpStatus,_tmpTimestamp,_tmpConfirmations,_tmpBlockNumber);
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
  public Flow<List<TransactionEntity>> observeByAddress(final String address) {
    final String _sql = "SELECT * FROM transactions WHERE fromAddress = ? OR toAddress = ? ORDER BY timestamp DESC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 2);
    int _argIndex = 1;
    _statement.bindString(_argIndex, address);
    _argIndex = 2;
    _statement.bindString(_argIndex, address);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"transactions"}, new Callable<List<TransactionEntity>>() {
      @Override
      @NonNull
      public List<TransactionEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfHash = CursorUtil.getColumnIndexOrThrow(_cursor, "hash");
          final int _cursorIndexOfType = CursorUtil.getColumnIndexOrThrow(_cursor, "type");
          final int _cursorIndexOfBlockchain = CursorUtil.getColumnIndexOrThrow(_cursor, "blockchain");
          final int _cursorIndexOfFromAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "fromAddress");
          final int _cursorIndexOfToAddress = CursorUtil.getColumnIndexOrThrow(_cursor, "toAddress");
          final int _cursorIndexOfAmount = CursorUtil.getColumnIndexOrThrow(_cursor, "amount");
          final int _cursorIndexOfTokenSymbol = CursorUtil.getColumnIndexOrThrow(_cursor, "tokenSymbol");
          final int _cursorIndexOfFee = CursorUtil.getColumnIndexOrThrow(_cursor, "fee");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfConfirmations = CursorUtil.getColumnIndexOrThrow(_cursor, "confirmations");
          final int _cursorIndexOfBlockNumber = CursorUtil.getColumnIndexOrThrow(_cursor, "blockNumber");
          final List<TransactionEntity> _result = new ArrayList<TransactionEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final TransactionEntity _item;
            final String _tmpHash;
            _tmpHash = _cursor.getString(_cursorIndexOfHash);
            final String _tmpType;
            _tmpType = _cursor.getString(_cursorIndexOfType);
            final String _tmpBlockchain;
            _tmpBlockchain = _cursor.getString(_cursorIndexOfBlockchain);
            final String _tmpFromAddress;
            _tmpFromAddress = _cursor.getString(_cursorIndexOfFromAddress);
            final String _tmpToAddress;
            _tmpToAddress = _cursor.getString(_cursorIndexOfToAddress);
            final String _tmpAmount;
            _tmpAmount = _cursor.getString(_cursorIndexOfAmount);
            final String _tmpTokenSymbol;
            _tmpTokenSymbol = _cursor.getString(_cursorIndexOfTokenSymbol);
            final String _tmpFee;
            _tmpFee = _cursor.getString(_cursorIndexOfFee);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final int _tmpConfirmations;
            _tmpConfirmations = _cursor.getInt(_cursorIndexOfConfirmations);
            final Long _tmpBlockNumber;
            if (_cursor.isNull(_cursorIndexOfBlockNumber)) {
              _tmpBlockNumber = null;
            } else {
              _tmpBlockNumber = _cursor.getLong(_cursorIndexOfBlockNumber);
            }
            _item = new TransactionEntity(_tmpHash,_tmpType,_tmpBlockchain,_tmpFromAddress,_tmpToAddress,_tmpAmount,_tmpTokenSymbol,_tmpFee,_tmpStatus,_tmpTimestamp,_tmpConfirmations,_tmpBlockNumber);
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

  @NonNull
  public static List<Class<?>> getRequiredConverters() {
    return Collections.emptyList();
  }
}
