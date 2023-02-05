package org.randoom.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

/**
 * Simple class to provide access to persistent storage of key-value pairs.
 */
public class AndroidDataStorage {

    private static final String DATABASE_NAME    = "persistentStorage.db";
    private static final int    DATABASE_VERSION = 1;

    private SQLiteDatabase      db;
    private OpenHelper          oh;

    /**
     * Create new handle for persistent storage.
     *
     * @param context Android Context.
     */
    public AndroidDataStorage(final Context context) {
        this.oh = new OpenHelper(context);
        this.db = oh.getWritableDatabase();
    }

    /**
     * Close database connections.
     */
    public void close() {
        try {
            if (db != null && db.isOpen()) {
                db.close();
            }
            if (oh != null) {
                oh.close();
            }
        } catch (final NullPointerException npe) {
            /* screw you Android for crashing my application */
        }
        db = null;
        oh = null;
        SQLiteDatabase.releaseMemory();
    }

    /**
     * Add some value into the database.
     *
     * @param codeName  Name to store the value under (i.e. key).
     * @param codeValue Value to store.
     */
    public void setCode(final String codeName, final String codeValue) {
        Cursor codeRow = null;
        SQLiteStatement stmt = null;
        try {
            codeRow = db.rawQuery(
                    "SELECT * FROM code WHERE codeName = ?",
                    new String[]{codeName}
            );

            if (codeRow.getCount() > 0) {// exists-- update
                db.execSQL(
                        "UPDATE code SET codeValue = ? WHERE codeName = ?",
                        new String[]{codeValue, codeName}
                );
            } else { // does not exist, insert
                stmt = db.compileStatement(
                        "INSERT INTO code(codeName, codeValue) values (?, ?)"
                );
                stmt.bindString(1, codeName);
                stmt.bindString(2, codeValue);
                stmt.executeInsert();
            }
        } finally {
            if (stmt != null) {
                stmt.close();
            }
            if (codeRow != null) {
                codeRow.close();
            }
        }
    }

    /**
     * Get some value from the database.
     *
     * @param codeName     Name to get the value from (i.e. key).
     * @param defaultValue Value return when nothing is stored.
     * @return             Value stored in database, or default.
     */
    public String getCode(final String codeName, final String defaultValue) {
        Cursor codeRow = null;
        //noinspection TryFinallyCanBeTryWithResources
        try {
            // check to see if it already exists
            codeRow = db.rawQuery(
                    "SELECT * FROM code WHERE codeName = ?",
                    new String[]{codeName}
            );

            if (codeRow.moveToFirst()) {
                return codeRow.getString(codeRow.getColumnIndexOrThrow("codeValue"));
            } else {
                return defaultValue;
            }
        } finally {
            if (codeRow != null) {
                codeRow.close();
            }
        }
    }

    private static class OpenHelper extends SQLiteOpenHelper {

        OpenHelper(final Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(final SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS code"
                    + "(id INTEGER PRIMARY KEY, codeName TEXT, codeValue TEXT)");
        }

        @Override
        public void onUpgrade(final SQLiteDatabase db, final int oldVersion, final int newVersion) {
        }
    }
}
