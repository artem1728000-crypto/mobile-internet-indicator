package ru.artemzozula.mobileindicator;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

final class HistoryStore extends SQLiteOpenHelper {
    static final String FULL = "Доступ есть";
    static final String LIMITED = "Вероятно, белый список";
    static final String NONE = "Доступ не подтверждён";
    private static final long MAX_GAP_MS = 90_000;
    private long currentId = -1;
    private long last;
    private String previousState;
    private String previousNetwork;

    HistoryStore(Context context) {
        super(context, "connection-history.db", null, 2);
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE periods (_id INTEGER PRIMARY KEY, start INTEGER NOT NULL,"
                + " finish INTEGER NOT NULL, state TEXT NOT NULL, network TEXT NOT NULL, min_dbm INTEGER, max_dbm INTEGER)");
        db.execSQL("CREATE INDEX periods_finish ON periods(finish)");
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE periods ADD COLUMN min_dbm INTEGER");
            db.execSQL("ALTER TABLE periods ADD COLUMN max_dbm INTEGER");
        }
    }

    void separate() {
        currentId = -1;
    }

    void observe(long now, String state, String network) {
        observe(now, state, network, Integer.MAX_VALUE);
    }

    void observe(long now, String state, String network, int dbm) {
        SQLiteDatabase db = getWritableDatabase();
        long gap = now - last;
        boolean contiguous = currentId >= 0 && gap >= 0 && gap <= MAX_GAP_MS;
        db.beginTransaction();
        try {
            if (contiguous) {
                ContentValues end = new ContentValues();
                end.put("finish", now);
                db.update("periods", end, "_id=?", new String[]{Long.toString(currentId)});
            }
            if (!contiguous || !state.equals(previousState) || !network.equals(previousNetwork)) {
                ContentValues row = new ContentValues();
                row.put("start", now);
                row.put("finish", now);
                row.put("state", state);
                row.put("network", network);
                currentId = db.insertOrThrow("periods", null, row);
            }
            if (dbm != Integer.MAX_VALUE) {
                db.execSQL("UPDATE periods SET min_dbm = min(coalesce(min_dbm, ?), ?),"
                        + " max_dbm = max(coalesce(max_dbm, ?), ?) WHERE _id = ?",
                        new Object[]{dbm, dbm, dbm, dbm, currentId});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        last = now;
        previousState = state;
        previousNetwork = network;
    }

    Cursor between(long start, long end) {
        return getReadableDatabase().rawQuery(
                "SELECT start, finish, state, network, min_dbm, max_dbm FROM periods WHERE finish>? AND start<? ORDER BY start, _id",
                new String[]{Long.toString(start), Long.toString(end)});
    }
}
