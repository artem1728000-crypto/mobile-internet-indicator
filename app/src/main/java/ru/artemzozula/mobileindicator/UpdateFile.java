package ru.artemzozula.mobileindicator;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

public final class UpdateFile extends ContentProvider {
    static final String MIME = "application/vnd.android.package-archive";

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return MIME; }

    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!"/update.apk".equals(uri.getPath()) || !"r".equals(mode)) throw new FileNotFoundException();
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(), "update.apk"),
                ParcelFileDescriptor.MODE_READ_ONLY);
    }

    @Override public Cursor query(Uri uri, String[] columns, String selection, String[] args, String sort) {
        File file = new File(getContext().getCacheDir(), "update.apk");
        if (!"/update.apk".equals(uri.getPath()) || !file.isFile()) return null;
        String[] projection = columns == null ? new String[]{"_display_name", "_size"} : columns;
        android.database.MatrixCursor cursor = new android.database.MatrixCursor(projection);
        android.database.MatrixCursor.RowBuilder row = cursor.newRow();
        for (String column : projection) {
            row.add("_display_name".equals(column) ? "update.apk" : "_size".equals(column) ? file.length() : null);
        }
        return cursor;
    }

    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
    @Override public int delete(Uri uri, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
