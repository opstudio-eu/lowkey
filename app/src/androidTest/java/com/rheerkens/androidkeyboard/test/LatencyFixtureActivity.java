package com.rheerkens.androidkeyboard.test;

import android.app.Activity;
import android.os.Bundle;
import android.os.SystemClock;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputConnectionWrapper;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;

/** Separate-process editor with a repeatable cost for synchronous IME reads. */
public final class LatencyFixtureActivity extends Activity {
    private int reads;
    private final ArrayList<Long> changes = new ArrayList<>();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        int delay = getIntent().getIntExtra("readDelayMs", 5);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        EditText editor = new EditText(this) {
            @Override public InputConnection onCreateInputConnection(EditorInfo info) {
                InputConnection base = super.onCreateInputConnection(info);
                return new InputConnectionWrapper(base, false) {
                    private void read() { reads++; SystemClock.sleep(delay); }
                    @Override public CharSequence getTextBeforeCursor(int n, int flags) {
                        read(); return super.getTextBeforeCursor(n, flags);
                    }
                    @Override public int getCursorCapsMode(int modes) {
                        read(); return super.getCursorCapsMode(modes);
                    }
                };
            }
        };
        editor.setSingleLine(false);
        editor.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
            android.text.InputType.TYPE_TEXT_FLAG_CAP_SENTENCES |
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        editor.setHint("Latency test editor");
        editor.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (count > 0) changes.add(SystemClock.uptimeMillis());
            }
            public void afterTextChanged(Editable e) {}
        });
        TextView stats = new TextView(this);
        stats.setText("Latency fixture");
        Button reset = new Button(this);
        reset.setAllCaps(false);
        reset.setText("Reset latency counters");
        reset.setOnClickListener(v -> { reads = 0; changes.clear(); });
        Button report = new Button(this);
        report.setAllCaps(false);
        report.setText("Report latency counters");
        report.setOnClickListener(v -> stats.setText("Latency reads=" + reads + " changes=" + changes));
        root.addView(stats);
        root.addView(reset);
        root.addView(report);
        root.addView(editor, new LinearLayout.LayoutParams(-1, 180));
        setContentView(root);
        editor.requestFocus();
        editor.postDelayed(() -> getSystemService(InputMethodManager.class).showSoftInput(editor, 0), 200);
    }
}
