package org.randoom.util;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteStatement;

public class AndroidDataStorage {

    private static final String DATABASE_NAME    = "persistentStorage.db";
    private static final int    DATABASE_VERSION = 1;

    private Context             context;
    private SQLiteDatabase      db;
    private OpenHelper          oh;

    public AndroidDataStorage(Context context) {
        this.context = context;
        this.oh = new OpenHelper(this.context);
        this.db = oh.getWritableDatabase();

    }

    public void close() {
        if (db != null) {
            db.close();
        }
        if (oh != null) {
            oh.close();
        }
        db = null;
        oh = null;
        SQLiteDatabase.releaseMemory();
    }

    public void setCode(String codeName, String codeValue) {
        SQLiteStatement stmt;
        
        
        Cursor codeRow = db.rawQuery(
            "SELECT * FROM code WHERE codeName = ?",
            new String[] {codeName}
        );

        if (codeRow.getCount() > 0) {// exists-- update
            db.execSQL(
                "UPDATE code SET codeValue = ? WHERE codeName = ?",
                new String[] {codeValue, codeName}
            );
        } else { // does not exist, insert
            stmt = db.compileStatement(
                "INSERT INTO code(codeName, codeValue) values (?, ?)"
            );
            stmt.bindString(1, codeName);
            stmt.bindString(2, codeValue);
            stmt.executeInsert();
        }
    }

    public String getCode(String codeName, String defaultValue) {

        // check to see if it already exists
        Cursor codeRow = db.rawQuery(
            "SELECT * FROM code WHERE codeName = ?",
            new String[] {codeName}
        );

        if (codeRow.moveToFirst()) {
            return codeRow.getString(codeRow.getColumnIndex("codeValue"));
        } else {
            return defaultValue;
        }
    }

    private static class OpenHelper extends SQLiteOpenHelper {

        OpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS code"
                    + "(id INTEGER PRIMARY KEY, codeName TEXT, codeValue TEXT)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }
}