package org.meshly.app.data.local;

import android.database.Cursor;
import android.os.CancellationSignal;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.room.CoroutinesRoom;
import androidx.room.EntityInsertionAdapter;
import androidx.room.RoomDatabase;
import androidx.room.RoomSQLiteQuery;
import androidx.room.SharedSQLiteStatement;
import androidx.room.util.CursorUtil;
import androidx.room.util.DBUtil;
import androidx.sqlite.db.SupportSQLiteStatement;
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
public final class ContactDao_Impl implements ContactDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ContactEntity> __insertionAdapterOfContactEntity;

  private final SharedSQLiteStatement __preparedStmtOfDeleteContact;

  public ContactDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfContactEntity = new EntityInsertionAdapter<ContactEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `contacts` (`jamiId`,`displayName`,`username`,`status`,`presence`,`addedAt`) VALUES (?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ContactEntity entity) {
        statement.bindString(1, entity.getJamiId());
        statement.bindString(2, entity.getDisplayName());
        if (entity.getUsername() == null) {
          statement.bindNull(3);
        } else {
          statement.bindString(3, entity.getUsername());
        }
        statement.bindString(4, entity.getStatus());
        statement.bindString(5, entity.getPresence());
        statement.bindLong(6, entity.getAddedAt());
      }
    };
    this.__preparedStmtOfDeleteContact = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM contacts WHERE jamiId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertOrUpdateContact(final ContactEntity contact,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfContactEntity.insert(contact);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object deleteContact(final String jamiId, final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfDeleteContact.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, jamiId);
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
          __preparedStmtOfDeleteContact.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ContactEntity>> getAllContacts() {
    final String _sql = "SELECT * FROM contacts ORDER BY displayName ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 0);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"contacts"}, new Callable<List<ContactEntity>>() {
      @Override
      @NonNull
      public List<ContactEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfJamiId = CursorUtil.getColumnIndexOrThrow(_cursor, "jamiId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfPresence = CursorUtil.getColumnIndexOrThrow(_cursor, "presence");
          final int _cursorIndexOfAddedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "addedAt");
          final List<ContactEntity> _result = new ArrayList<ContactEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ContactEntity _item;
            final String _tmpJamiId;
            _tmpJamiId = _cursor.getString(_cursorIndexOfJamiId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpUsername;
            if (_cursor.isNull(_cursorIndexOfUsername)) {
              _tmpUsername = null;
            } else {
              _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpPresence;
            _tmpPresence = _cursor.getString(_cursorIndexOfPresence);
            final long _tmpAddedAt;
            _tmpAddedAt = _cursor.getLong(_cursorIndexOfAddedAt);
            _item = new ContactEntity(_tmpJamiId,_tmpDisplayName,_tmpUsername,_tmpStatus,_tmpPresence,_tmpAddedAt);
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
  public Flow<List<ContactEntity>> getContactsByStatus(final String status) {
    final String _sql = "SELECT * FROM contacts WHERE status = ?";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, status);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"contacts"}, new Callable<List<ContactEntity>>() {
      @Override
      @NonNull
      public List<ContactEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfJamiId = CursorUtil.getColumnIndexOrThrow(_cursor, "jamiId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfPresence = CursorUtil.getColumnIndexOrThrow(_cursor, "presence");
          final int _cursorIndexOfAddedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "addedAt");
          final List<ContactEntity> _result = new ArrayList<ContactEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ContactEntity _item;
            final String _tmpJamiId;
            _tmpJamiId = _cursor.getString(_cursorIndexOfJamiId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpUsername;
            if (_cursor.isNull(_cursorIndexOfUsername)) {
              _tmpUsername = null;
            } else {
              _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpPresence;
            _tmpPresence = _cursor.getString(_cursorIndexOfPresence);
            final long _tmpAddedAt;
            _tmpAddedAt = _cursor.getLong(_cursorIndexOfAddedAt);
            _item = new ContactEntity(_tmpJamiId,_tmpDisplayName,_tmpUsername,_tmpStatus,_tmpPresence,_tmpAddedAt);
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
  public Object getContactById(final String jamiId,
      final Continuation<? super ContactEntity> $completion) {
    final String _sql = "SELECT * FROM contacts WHERE jamiId = ? LIMIT 1";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, jamiId);
    final CancellationSignal _cancellationSignal = DBUtil.createCancellationSignal();
    return CoroutinesRoom.execute(__db, false, _cancellationSignal, new Callable<ContactEntity>() {
      @Override
      @Nullable
      public ContactEntity call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfJamiId = CursorUtil.getColumnIndexOrThrow(_cursor, "jamiId");
          final int _cursorIndexOfDisplayName = CursorUtil.getColumnIndexOrThrow(_cursor, "displayName");
          final int _cursorIndexOfUsername = CursorUtil.getColumnIndexOrThrow(_cursor, "username");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfPresence = CursorUtil.getColumnIndexOrThrow(_cursor, "presence");
          final int _cursorIndexOfAddedAt = CursorUtil.getColumnIndexOrThrow(_cursor, "addedAt");
          final ContactEntity _result;
          if (_cursor.moveToFirst()) {
            final String _tmpJamiId;
            _tmpJamiId = _cursor.getString(_cursorIndexOfJamiId);
            final String _tmpDisplayName;
            _tmpDisplayName = _cursor.getString(_cursorIndexOfDisplayName);
            final String _tmpUsername;
            if (_cursor.isNull(_cursorIndexOfUsername)) {
              _tmpUsername = null;
            } else {
              _tmpUsername = _cursor.getString(_cursorIndexOfUsername);
            }
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpPresence;
            _tmpPresence = _cursor.getString(_cursorIndexOfPresence);
            final long _tmpAddedAt;
            _tmpAddedAt = _cursor.getLong(_cursorIndexOfAddedAt);
            _result = new ContactEntity(_tmpJamiId,_tmpDisplayName,_tmpUsername,_tmpStatus,_tmpPresence,_tmpAddedAt);
          } else {
            _result = null;
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
