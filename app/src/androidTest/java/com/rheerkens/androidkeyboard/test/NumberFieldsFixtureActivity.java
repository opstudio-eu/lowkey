package com.rheerkens.androidkeyboard.test;

import android.app.Activity;
import android.os.Bundle;
import android.text.InputType;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

/** One editor per kind of field the keyboard opens differently: text, number, phone, date and PIN. */
public final class NumberFieldsFixtureActivity extends Activity {
    /** Field descriptions, each with its input type. */
    static final String[] FIELDS = {"Text field", "Number field", "Phone field", "Date field", "PIN field"};
    private static final int[] TYPES = {
        InputType.TYPE_CLASS_TEXT,
        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED | InputType.TYPE_NUMBER_FLAG_DECIMAL,
        InputType.TYPE_CLASS_PHONE,
        InputType.TYPE_CLASS_DATETIME | InputType.TYPE_DATETIME_VARIATION_DATE,
        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD,
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24, 24, 24, 24);
        TextView title = new TextView(this);
        title.setText("Number fields fixture");
        root.addView(title);
        for (int i = 0; i < FIELDS.length; i++) {
            EditText field = new EditText(this);
            field.setContentDescription(FIELDS[i]);
            field.setHint(FIELDS[i]);
            field.setInputType(TYPES[i]);
            field.setSingleLine(true);
            root.addView(field);
        }
        setContentView(root);
    }
}
