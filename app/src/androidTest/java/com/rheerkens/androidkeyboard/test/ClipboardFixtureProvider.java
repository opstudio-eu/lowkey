package com.rheerkens.androidkeyboard.test;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;

/** Grants only the generated test PNG; installed in the test APK, never the shipped app. */
public final class ClipboardFixtureProvider extends ContentProvider {
    public static final Uri IMAGE = Uri.parse("content://com.rheerkens.androidkeyboard.test.clipboard/image");
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!IMAGE.equals(uri) || !mode.equals("r")) throw new FileNotFoundException("Unknown fixture");
        return ParcelFileDescriptor.open(new File(getContext().getCacheDir(), "clipboard-fixture.png"), ParcelFileDescriptor.MODE_READ_ONLY);
    }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String order) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
}
