package org.meshly.app.data.local;

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
public final class ChatMessageDao_Impl implements ChatMessageDao {
  private final RoomDatabase __db;

  private final EntityInsertionAdapter<ChatMessageEntity> __insertionAdapterOfChatMessageEntity;

  private final SharedSQLiteStatement __preparedStmtOfUpdateMessageStatus;

  private final SharedSQLiteStatement __preparedStmtOfClearHistory;

  public ChatMessageDao_Impl(@NonNull final RoomDatabase __db) {
    this.__db = __db;
    this.__insertionAdapterOfChatMessageEntity = new EntityInsertionAdapter<ChatMessageEntity>(__db) {
      @Override
      @NonNull
      protected String createQuery() {
        return "INSERT OR REPLACE INTO `chat_messages` (`id`,`conversationId`,`senderJamiId`,`text`,`timestamp`,`status`,`attachmentPath`,`isIncoming`) VALUES (?,?,?,?,?,?,?,?)";
      }

      @Override
      protected void bind(@NonNull final SupportSQLiteStatement statement,
          @NonNull final ChatMessageEntity entity) {
        statement.bindString(1, entity.getId());
        statement.bindString(2, entity.getConversationId());
        statement.bindString(3, entity.getSenderJamiId());
        statement.bindString(4, entity.getText());
        statement.bindLong(5, entity.getTimestamp());
        statement.bindString(6, entity.getStatus());
        if (entity.getAttachmentPath() == null) {
          statement.bindNull(7);
        } else {
          statement.bindString(7, entity.getAttachmentPath());
        }
        final int _tmp = entity.isIncoming() ? 1 : 0;
        statement.bindLong(8, _tmp);
      }
    };
    this.__preparedStmtOfUpdateMessageStatus = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "UPDATE chat_messages SET status = ? WHERE id = ?";
        return _query;
      }
    };
    this.__preparedStmtOfClearHistory = new SharedSQLiteStatement(__db) {
      @Override
      @NonNull
      public String createQuery() {
        final String _query = "DELETE FROM chat_messages WHERE conversationId = ?";
        return _query;
      }
    };
  }

  @Override
  public Object insertMessage(final ChatMessageEntity message,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        __db.beginTransaction();
        try {
          __insertionAdapterOfChatMessageEntity.insert(message);
          __db.setTransactionSuccessful();
          return Unit.INSTANCE;
        } finally {
          __db.endTransaction();
        }
      }
    }, $completion);
  }

  @Override
  public Object updateMessageStatus(final String id, final String status,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfUpdateMessageStatus.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, status);
        _argIndex = 2;
        _stmt.bindString(_argIndex, id);
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
          __preparedStmtOfUpdateMessageStatus.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Object clearHistory(final String conversationId,
      final Continuation<? super Unit> $completion) {
    return CoroutinesRoom.execute(__db, true, new Callable<Unit>() {
      @Override
      @NonNull
      public Unit call() throws Exception {
        final SupportSQLiteStatement _stmt = __preparedStmtOfClearHistory.acquire();
        int _argIndex = 1;
        _stmt.bindString(_argIndex, conversationId);
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
          __preparedStmtOfClearHistory.release(_stmt);
        }
      }
    }, $completion);
  }

  @Override
  public Flow<List<ChatMessageEntity>> getMessagesForConversation(final String conversationId) {
    final String _sql = "SELECT * FROM chat_messages WHERE conversationId = ? ORDER BY timestamp ASC";
    final RoomSQLiteQuery _statement = RoomSQLiteQuery.acquire(_sql, 1);
    int _argIndex = 1;
    _statement.bindString(_argIndex, conversationId);
    return CoroutinesRoom.createFlow(__db, false, new String[] {"chat_messages"}, new Callable<List<ChatMessageEntity>>() {
      @Override
      @NonNull
      public List<ChatMessageEntity> call() throws Exception {
        final Cursor _cursor = DBUtil.query(__db, _statement, false, null);
        try {
          final int _cursorIndexOfId = CursorUtil.getColumnIndexOrThrow(_cursor, "id");
          final int _cursorIndexOfConversationId = CursorUtil.getColumnIndexOrThrow(_cursor, "conversationId");
          final int _cursorIndexOfSenderJamiId = CursorUtil.getColumnIndexOrThrow(_cursor, "senderJamiId");
          final int _cursorIndexOfText = CursorUtil.getColumnIndexOrThrow(_cursor, "text");
          final int _cursorIndexOfTimestamp = CursorUtil.getColumnIndexOrThrow(_cursor, "timestamp");
          final int _cursorIndexOfStatus = CursorUtil.getColumnIndexOrThrow(_cursor, "status");
          final int _cursorIndexOfAttachmentPath = CursorUtil.getColumnIndexOrThrow(_cursor, "attachmentPath");
          final int _cursorIndexOfIsIncoming = CursorUtil.getColumnIndexOrThrow(_cursor, "isIncoming");
          final List<ChatMessageEntity> _result = new ArrayList<ChatMessageEntity>(_cursor.getCount());
          while (_cursor.moveToNext()) {
            final ChatMessageEntity _item;
            final String _tmpId;
            _tmpId = _cursor.getString(_cursorIndexOfId);
            final String _tmpConversationId;
            _tmpConversationId = _cursor.getString(_cursorIndexOfConversationId);
            final String _tmpSenderJamiId;
            _tmpSenderJamiId = _cursor.getString(_cursorIndexOfSenderJamiId);
            final String _tmpText;
            _tmpText = _cursor.getString(_cursorIndexOfText);
            final long _tmpTimestamp;
            _tmpTimestamp = _cursor.getLong(_cursorIndexOfTimestamp);
            final String _tmpStatus;
            _tmpStatus = _cursor.getString(_cursorIndexOfStatus);
            final String _tmpAttachmentPath;
            if (_cursor.isNull(_cursorIndexOfAttachmentPath)) {
              _tmpAttachmentPath = null;
            } else {
              _tmpAttachmentPath = _cursor.getString(_cursorIndexOfAttachmentPath);
            }
            final boolean _tmpIsIncoming;
            final int _tmp;
            _tmp = _cursor.getInt(_cursorIndexOfIsIncoming);
            _tmpIsIncoming = _tmp != 0;
            _item = new ChatMessageEntity(_tmpId,_tmpConversationId,_tmpSenderJamiId,_tmpText,_tmpTimestamp,_tmpStatus,_tmpAttachmentPath,_tmpIsIncoming);
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
