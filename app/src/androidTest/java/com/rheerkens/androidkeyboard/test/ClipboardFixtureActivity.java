package com.rheerkens.androidkeyboard.test;

import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.text.InputType;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputContentInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

/** A real editor/copy source in another UID. Its image callback reads the delivered URI. */
public final class ClipboardFixtureActivity extends Activity {
    public static final String SAMPLE = "MiXeD 😀\nsecond line ";
    private EditText editor;
    private TextView status;
    private ImageView preview;
    private boolean images;
    private boolean rejectImages;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(12, 12, 12, 12);
        TextView title = new TextView(this); title.setText("Clipboard integration fixture"); root.addView(title);
        editor = new EditText(this) {
            @Override public InputConnection onCreateInputConnection(EditorInfo attrs) {
                InputConnection connection = super.onCreateInputConnection(attrs);
                if (connection == null || !images) return connection;
                attrs.contentMimeTypes = new String[]{"image/*"};
                return new InputConnectionWrapper(connection, false) {
                    @Override public boolean commitContent(InputContentInfo content, int flags, Bundle opts) {
                        if (rejectImages) return false;
                        try {
                            if ((flags & InputConnection.INPUT_CONTENT_GRANT_READ_URI_PERMISSION) == 0) return false;
                            content.requestPermission();
                            try (InputStream stream = getContentResolver().openInputStream(content.getContentUri())) {
                                Bitmap bitmap = BitmapFactory.decodeStream(stream);
                                if (bitmap == null) return false;
                                preview.setImageBitmap(bitmap);
                                status.setText("Image received: " + bitmap.getWidth() + "x" + bitmap.getHeight() + "; permission requested");
                                return true;
                            } finally { content.releasePermission(); }
                        } catch (Exception error) {
                            status.setText("Image failure: " + error.getClass().getSimpleName()); return false;
                        }
                    }
                };
            }
        };
        editor.setContentDescription("Clipboard test editor");
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setMinLines(2); root.addView(editor);
        status = new TextView(this); status.setText("No image received"); root.addView(status);
        preview = new ImageView(this); preview.setContentDescription("Received image preview");
        root.addView(preview, new LinearLayout.LayoutParams(80,80));
        LinearLayout copy = row(root);
        button(copy, "Copy text", () -> clipboard().setPrimaryClip(ClipData.newPlainText("Fixture", SAMPLE)));
        button(copy, "Copy image", () -> clipboard().setPrimaryClip(ClipData.newUri(getContentResolver(), "Fixture image", ClipboardFixtureProvider.IMAGE)));
        button(copy, "Clear clip", () -> clipboard().clearPrimaryClip());
        LinearLayout fields = row(root);
        button(fields, "Plain editor", () -> configure(false, false, false));
        button(fields, "Image editor", () -> configure(true, false, false));
        button(fields, "Password editor", () -> configure(false, true, false));
        LinearLayout extra = row(root);
        button(extra, "Sensitive clip", () -> {
            ClipData clip = ClipData.newPlainText("Sensitive fixture", "secret fixture");
            PersistableBundle extras = new PersistableBundle(); extras.putBoolean("android.content.extra.IS_SENSITIVE", true);
            clip.getDescription().setExtras(extras); clipboard().setPrimaryClip(clip);
        });
        button(extra, "Private editor", () -> configure(false, false, true));
        button(extra, "Reject image", () -> { configure(true, false, false); rejectImages = true; });
        setContentView(root);
        try {
            Bitmap bitmap = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.rgb(45,130,220));
            try (FileOutputStream output = new FileOutputStream(new File(getCacheDir(), "clipboard-fixture.png"))) {
                bitmap.compress(Bitmap.CompressFormat.PNG,100,output);
            }
        } catch (Exception error) { throw new IllegalStateException(error); }
        editor.postDelayed(this::focusEditor, 350);
    }
    @Override public void onWindowFocusChanged(boolean focused) {
        super.onWindowFocusChanged(focused);
        if (focused) editor.postDelayed(this::focusEditor, 150);
    }
    private ClipboardManager clipboard() { return getSystemService(ClipboardManager.class); }
    private LinearLayout row(LinearLayout root) { LinearLayout row = new LinearLayout(this); root.addView(row); return row; }
    private void button(LinearLayout row, String label, Runnable action) {
        Button button = new Button(this); button.setAllCaps(false); button.setText(label); button.setTextSize(11);
        row.addView(button, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        button.setOnClickListener(view -> { action.run(); focusEditor(); });
    }
    private void configure(boolean acceptImages, boolean password, boolean privateField) {
        images = acceptImages; rejectImages = false; editor.setText("");
        editor.setInputType(password ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD :
            InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setImeOptions(privateField ? EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING : EditorInfo.IME_ACTION_NONE);
        status.setText("No image received"); preview.setImageDrawable(null);
        getSystemService(InputMethodManager.class).restartInput(editor);
    }
    private void focusEditor() { editor.requestFocus(); getSystemService(InputMethodManager.class).showSoftInput(editor, InputMethodManager.SHOW_IMPLICIT); }
}
