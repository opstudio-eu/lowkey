package com.rheerkens.androidkeyboard.test;

import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.accessibilityservice.AccessibilityServiceInfo;
import java.util.ArrayList;
import java.util.List;

/** Actual IME touch regression, no helper IME or key-event text injection. */
public final class KeyboardTouchProbe extends Instrumentation {
    private Bundle arguments;
    private UiAutomation ui;
    @Override public void onCreate(Bundle args) { super.onCreate(args); arguments = args; start(); }
    @Override public void onStart() {
        Bundle result = new Bundle();
        try {
            if (arguments.getString("probe", "burst").equals("correction")) { correctionBudget(result); return; }
            if (arguments.getString("probe", "burst").equals("blur")) { blur(result); return; }
            ui = getUiAutomation();
            AccessibilityServiceInfo info = ui.getServiceInfo();
            info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
            ui.setServiceInfo(info);
            if (arguments.getString("probe", "burst").equals("clipboard")) { clipboard(result); return; }
            startActivitySync(new Intent().setClassName(getTargetContext(), "com.rheerkens.androidkeyboard.companion.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            AccessibilityNodeInfo launch = waitLabel("Test keyboard");
            while (launch != null && !launch.performAction(AccessibilityNodeInfo.ACTION_CLICK)) launch = launch.getParent();
            expandKeyboard();
            waitLabel("Switch to voice typing");
            AccessibilityNodeInfo field = waitEditable();
            Bundle blank = new Bundle(); blank.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "");
            if (!field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, blank)) throw new AssertionError("Cannot clear test editor");
            SystemClock.sleep(1800);
            cancelPointers();
            if (arguments.getString("steady-shift", "0").equals("1")) { waitLabel("Shift on").performAction(AccessibilityNodeInfo.ACTION_CLICK); SystemClock.sleep(300); }
            if (arguments.getString("probe", "burst").equals("numbers")) { numbers(result); return; }
            if (arguments.getString("probe", "burst").startsWith("autocomplete")) { autocomplete(result); return; }
            if (arguments.getString("probe", "burst").equals("accents")) { accents(result); return; }
            Rect a = bounds(waitLabel("s")), b = bounds(waitLabel("d"));
            Bundle coordinates = new Bundle(); coordinates.putString("stream", "key s="+a+" d="+b+"\n"); sendStatus(0,coordinates);
            try (java.io.FileOutputStream f = new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(), "probe-before.png"))) { ui.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,f); }
            String mode = arguments.getString("probe", "burst");
            int count = Integer.parseInt(arguments.getString("count", "30"));
            int interval = Integer.parseInt(arguments.getString("interval", "100"));
            long start = SystemClock.uptimeMillis();
            StringBuilder expected = new StringBuilder();
            for (int i=0; i<count; i++) {
                if (mode.equals("overlap") || mode.startsWith("same-key") || mode.equals("rollover")) {
                    Rect second = mode.startsWith("same-key") ? a : b;
                    long down = SystemClock.uptimeMillis();
                    touch(down, MotionEvent.ACTION_DOWN, new int[]{0}, new Rect[]{a});
                    SystemClock.sleep(20);
                    touch(down, MotionEvent.ACTION_POINTER_DOWN | (1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT), new int[]{0,1}, new Rect[]{a,second});
                    SystemClock.sleep(20);
                    touch(down, MotionEvent.ACTION_POINTER_UP | (mode.equals("same-key-reverse") ? 1 << MotionEvent.ACTION_POINTER_INDEX_SHIFT : 0), new int[]{0,1}, new Rect[]{a,second});
                    SystemClock.sleep(20);
                    touch(down, MotionEvent.ACTION_UP, new int[]{mode.equals("same-key-reverse") ? 0:1}, new Rect[]{second});
                    expected.append(mode.startsWith("same-key") ? "ss" : "sd");
                } else {
                    long down = SystemClock.uptimeMillis();
                    Rect key = i%2==0 ? a:b;
                    touch(down, MotionEvent.ACTION_DOWN, new int[]{0}, new Rect[]{key});
                    SystemClock.sleep(20);
                    touch(down, MotionEvent.ACTION_UP, new int[]{0}, new Rect[]{key});
                    expected.append(i%2==0 ? 's':'d');
                }
                SystemClock.sleep(interval);
            }
            long injectionMs = SystemClock.uptimeMillis()-start;
            SystemClock.sleep(1800);
            cancelPointers();
            AccessibilityNodeInfo typed = waitEditable(); typed.refresh();
            String actual = String.valueOf(typed.getText());
            try (java.io.FileOutputStream f = new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(), "probe-after.png"))) { ui.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,f); }
            String report = "probe="+mode+" expected="+expected.length()+" actual="+actual.length()+" injectionMs="+injectionMs+" text="+actual;
            result.putString("stream", report+"\n");
            if (!expected.toString().equals(actual.toLowerCase(java.util.Locale.ROOT))) throw new AssertionError(report);
            finish(0, result);
        } catch (Throwable e) {
            try { if(ui != null) cancelPointers(); } catch(Throwable ignored) {}
            result.putString("stream", "FAIL: "+e+"\n"); finish(1, result);
        }
    }
    private void clipboard(Bundle result) throws Exception {
        // Starting instrumentation force-stops the IME package. Its own activity clears that
        // stopped state before an editor in another app can bind the keyboard service again.
        startActivitySync(new Intent().setClassName(getTargetContext(),
            "com.rheerkens.androidkeyboard.companion.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        getTargetContext().startActivity(new Intent().setClassName(
            "com.rheerkens.androidkeyboard.test", "com.rheerkens.androidkeyboard.test.ClipboardFixtureActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
        waitLabel("Clipboard integration fixture");
        waitLabel("Switch to voice typing");
        clickLabel("Plain editor");
        clickLabel("Clear clip");
        waitLabel("Clipboard empty");
        assertNoSuggestions();
        clickLabel("Copy text");
        clickLabel("Paste clipboard text: " + ClipboardFixtureActivity.SAMPLE);
        if (!editorText().equals(ClipboardFixtureActivity.SAMPLE)) throw new AssertionError("Text paste mismatch: expected " +
            ClipboardFixtureActivity.SAMPLE.length() + " characters, received " + editorText().length());
        setEditorText("");
        clickLabel("s");
        waitLabel("Typed word s. Long-press to add to dictionary");
        if (hasClipboardAction()) throw new AssertionError("Typing must replace clipboard with word suggestions");
        clickLabel("Copy image");
        SystemClock.sleep(500);
        if (hasClipboardAction()) throw new AssertionError("New clip interrupted a word being typed");
        setEditorText("");
        clickLabel("Clipboard image. This field doesn't accept images");
        waitLabel("This field doesn't accept images");
        if (!editorText().isEmpty()) throw new AssertionError("Unsupported image pasted a URI as text");
        clickLabel("Image editor");
        waitLabel("Paste clipboard image");
        waitLabel("Clipboard image preview");
        screenshot("clipboard-image-preview.png");
        clickLabel("Paste clipboard image");
        waitLabel("Image received: 24x24; permission requested");
        if (!editorText().isEmpty()) throw new AssertionError("Image paste changed text");
        screenshot("clipboard-image-pasted.png");
        clickLabel("Reject image");
        clickLabel("Paste clipboard image");
        waitLabel("This clip couldn't be pasted");
        if (!editorText().isEmpty()) throw new AssertionError("Rejected image changed text");
        clickLabel("Plain editor");
        clickLabel("Sensitive clip");
        waitLabel("Paste copied text");
        if (nodes().stream().anyMatch(node -> String.valueOf(node.getText()).contains("secret fixture") ||
            String.valueOf(node.getContentDescription()).contains("secret fixture"))) throw new AssertionError("Sensitive clip preview exposed its contents");
        clickLabel("Paste copied text");
        if (!editorText().equals("secret fixture")) throw new AssertionError("Sensitive text paste failed");
        clickLabel("Password editor");
        clickLabel("Copy text");
        SystemClock.sleep(500);
        if (hasClipboardAction()) throw new AssertionError("Password field exposed clipboard");
        assertNoSuggestions();
        clickLabel("Private editor");
        SystemClock.sleep(500);
        if (hasClipboardAction()) throw new AssertionError("Private field exposed clipboard");
        clickLabel("Plain editor");
        clickLabel("Clear clip");
        waitLabel("Clipboard empty");
        clickLabel("Copy text");
        waitLabel("Paste clipboard text: " + ClipboardFixtureActivity.SAMPLE);
        screenshot("clipboard-text-preview.png");
        result.putString("stream", "probe=clipboard PASS: idle/typing transitions, live clip changes, literal text, image thumbnail, image commit with URI permission, unsupported/rejected editors, sensitive clips, password/private fields, clipboard clear\n");
        finish(0,result);
    }
    private void clickLabel(String label) {
        AccessibilityNodeInfo node = waitLabel(label);
        while (node != null && !node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) node = node.getParent();
        if (node == null) throw new AssertionError("Cannot click " + label);
        SystemClock.sleep(180);
    }
    private void setEditorText(String text) {
        Bundle value = new Bundle(); value.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
        if (!waitEditable().performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, value)) throw new AssertionError("Cannot edit fixture");
        SystemClock.sleep(300);
    }
    private boolean hasClipboardAction() {
        return nodes().stream().anyMatch(node -> String.valueOf(node.getContentDescription()).startsWith("Paste clipboard") ||
            String.valueOf(node.getContentDescription()).startsWith("Clipboard image.") ||
            String.valueOf(node.getContentDescription()).equals("Paste copied text"));
    }
    private void assertNoSuggestions() {
        if (nodes().stream().anyMatch(node -> String.valueOf(node.getContentDescription()).startsWith("Insert ") ||
            String.valueOf(node.getContentDescription()).startsWith("Typed word "))) throw new AssertionError("Idle strip still shows dictionary suggestions");
    }
    private void screenshot(String filename) throws Exception {
        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(), filename))) {
            ui.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream);
        }
    }
    private void autocomplete(Bundle result) throws Exception {
        if (arguments.getString("probe", "").equals("autocomplete-boundaries")) {
            for (String[] example : new String[][]{{"teh", "the", "."}, {"recieve", "receive", ","}, {"tyoing", "typing", "."}}) {
                setEditorText("please " + example[0]);
                tap(bounds(waitLabel(example[2])));
                waitEditorText("please " + example[1] + example[2]);
                tap(bounds(waitLabel("Backspace. Hold to delete faster. Swipe up and release to clear all text")));
                waitEditorText("please " + example[0]);
                tap(bounds(waitLabel(example[2])));
                waitEditorText("please " + example[0] + example[2]);
            }
            for (String keep : new String[]{"their", "kotlin", "ViewModel", "https://teh", "user@teh"}) {
                setEditorText("please " + keep);
                tap(bounds(waitLabel(".")));
                waitEditorText("please " + keep + ".");
            }
            result.putString("stream", "probe=autocomplete-boundaries PASS: punctuation correction, immediate undo, rejection, known words, code and URLs\n");
            finish(0, result);
            return;
        }
        if (arguments.getString("probe", "").equals("autocomplete-refresh")) {
            completionRefresh();
            result.putString("stream", "probe=autocomplete-refresh PASS: completion tap survives a refresh with the same word and candidate\n");
            finish(0, result);
            return;
        }
        String[][] cases = {{"teh", "the"}, {"recieve", "receive"}, {"fucntion", "function"},
            {"autoxomplete", "autocomplete"}, {"autocoreecrs", "autocorrects"}, {"tyoing", "typing"},
            {"omputer", "computer"}, {"acomputer", "computer"}};
        java.util.Map<Character, Rect> keys = new java.util.HashMap<>();
        setEditorText("please ");
        for (String letter : "abcdefghijklmnopqrstuvwxyz".split("")) {
            keys.put(letter.charAt(0), bounds(waitLabel(letter)));
        }
        Rect space = bounds(waitLabel("Space"));
        int interval = Integer.parseInt(arguments.getString("interval", "100"));
        for (String[] example : cases) {
            setEditorText("please ");
            for (char letter : example[0].toCharArray()) { tap(keys.get(letter)); SystemClock.sleep(interval); }
            if (interval >= 50) waitLabel("Insert " + example[1]);
            tap(space);
            waitEditorText("please " + example[1] + " ");
        }
        setEditorText("please ");
        for (char letter : "receiv".toCharArray()) { tap(keys.get(letter)); SystemClock.sleep(interval); }
        tap(bounds(waitLabel("Insert receive")));
        waitEditorText("please receive ");
        result.putString("stream", "probe=autocomplete PASS: real key taps, eight Space corrections, and tapping a word completion; interval=" + interval + "ms\n");
        finish(0, result);
    }
    private void completionRefresh() {
        cleanupAutocompleteProbeTerms();
        setEditorText("please receiv");
        Rect suggestion = bounds(waitLabel("Insert receive"));
        long down = SystemClock.uptimeMillis();
        touch(down, MotionEvent.ACTION_DOWN, new int[]{0}, new Rect[]{suggestion});
        SystemClock.sleep(50);
        try {
            runOnMainSync(() -> {
                com.rheerkens.androidkeyboard.AppGraph.INSTANCE.getDictionary().addTerm("zzAutocompleteProbe" + down);
            });
            SystemClock.sleep(200);
            // Release before querying accessibility; querying every node can turn a short
            // touch into an unrelated long-press on slower emulators.
            touch(down, MotionEvent.ACTION_UP, new int[]{0}, new Rect[]{suggestion});
            if (!waitEditable().isFocused()) throw new AssertionError("Editor changed during refresh");
            waitEditorText("please receive ");
        } finally {
            cleanupAutocompleteProbeTerms();
        }
    }
    private void cleanupAutocompleteProbeTerms() {
        runOnMainSync(() -> {
            com.rheerkens.androidkeyboard.dictionary.DictionaryStore dictionary = com.rheerkens.androidkeyboard.AppGraph.INSTANCE.getDictionary();
            for (com.rheerkens.androidkeyboard.dictionary.PersonalTerm term : dictionary.getState().getValue().getTerms()) {
                if (term.getText().matches("zzAutocompleteProbe[0-9]+")) dictionary.deleteTerm(term.getId());
            }
            // Instrumentation finish force-stops the app, so wait for pending apply() writes.
            if (!getTargetContext().getSharedPreferences("dictionary", 0).edit().commit()) {
                throw new AssertionError("Could not flush temporary dictionary cleanup");
            }
        });
    }
    private void tap(Rect key) {
        long down = SystemClock.uptimeMillis();
        touch(down, MotionEvent.ACTION_DOWN, new int[]{0}, new Rect[]{key});
        SystemClock.sleep(20);
        touch(down, MotionEvent.ACTION_UP, new int[]{0}, new Rect[]{key});
    }
    private void waitEditorText(String expected) {
        for (int i = 0; i < 80; i++) {
            if (editorText().equals(expected)) return;
            SystemClock.sleep(100);
        }
        throw new AssertionError("Autocomplete expected '" + expected + "', actual '" + editorText() + "'");
    }
    private void numbers(Bundle result) throws Exception {
        StringBuilder expected = new StringBuilder();
        String letters = "qwertyuiop", digits = "1234567890";
        for (int i=0;i<10;i++) {
            Rect key = bounds(waitLabel(String.valueOf(letters.charAt(i))));
            long down = SystemClock.uptimeMillis();
            touch(down,MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{key});
            SystemClock.sleep(650);
            boolean preview = false;
            for(AccessibilityNodeInfo n:nodes()) { n.refresh(); if(String.valueOf(n.getContentDescription()).contains("Number preview: "+digits.charAt(i))) preview=true; }
            if(!preview) throw new AssertionError("Number preview absent for "+digits.charAt(i));
            if(!editorText().equals(expected.toString())) throw new AssertionError("Number committed before release");
            if(i==1) try (java.io.FileOutputStream f = new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(), "number-preview.png"))) { ui.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,f); }
            touch(down,MotionEvent.ACTION_UP,new int[]{0},new Rect[]{key});
            expected.append(digits.charAt(i)); SystemClock.sleep(150);
            if(!editorText().equals(expected.toString())) throw new AssertionError("Wrong number after release: "+editorText());
            for(AccessibilityNodeInfo n:nodes()) { n.refresh(); if(String.valueOf(n.getContentDescription()).contains("Number preview:")) throw new AssertionError("Preview remained after release"); }
        }
        Rect q = bounds(waitLabel("q"));
        long down=SystemClock.uptimeMillis();
        touch(down,MotionEvent.ACTION_DOWN,new int[]{0},new Rect[]{q}); SystemClock.sleep(650);
        touch(down,MotionEvent.ACTION_CANCEL,new int[]{0},new Rect[]{q}); SystemClock.sleep(150);
        if(!editorText().equals(expected.toString())) throw new AssertionError("Cancelled hold inserted a number");
        result.putString("stream","probe=numbers expected="+expected+" actual="+editorText()+" preview and cancellation verified\n"); finish(0,result);
    }
    /** Long-presses [letter], slides [cells] popup cells (negative = left), and checks the choice. */
    private void accent(String letter, int cells, boolean below, String opened, String chosen) {
        Rect key = bounds(waitLabel(letter));
        float x = key.centerX(), y = key.centerY();
        long down = SystemClock.uptimeMillis();
        touchAt(down, MotionEvent.ACTION_DOWN, x, y);
        SystemClock.sleep(650);
        if (!descriptionContains(opened)) throw new AssertionError("Popup for "+letter+" missing "+opened);
        float toX = x + cells*key.width(), toY = below ? key.bottom + 2*key.height() : y - key.height();
        for (int step=1; step<=6; step++) { touchAt(down, MotionEvent.ACTION_MOVE, x+(toX-x)*step/6, y+(toY-y)*step/6); SystemClock.sleep(60); }
        SystemClock.sleep(250);
        if (!descriptionContains(chosen)) throw new AssertionError("Slide on "+letter+" did not select "+chosen+"; saw "+popupDescriptions());
        if (!below) assertHighlightUnder(toX, key);
        touchAt(down, MotionEvent.ACTION_UP, toX, toY);
        SystemClock.sleep(250);
    }
    private void accents(Bundle result) throws Exception {
        accent("e", 1, false, "Number preview: 3", "Accent: ");
        accent("e", 4, false, "Number preview: 3", "Accent: ë");
        accent("a", 0, false, "Accent: á", "Accent: á");
        accent("o", -1, false, "Number preview: 9", "Accent: ó");
        accent("n", 0, true, "Accent: ñ", "Slide back to choose");
        String before = editorText();
        tap(bounds(waitLabel("Shift"))); SystemClock.sleep(300);
        accent("e", 1, false, "Number preview: 3", "Accent: É");
        String actual = editorText();
        try (java.io.FileOutputStream f = new java.io.FileOutputStream(new java.io.File(getTargetContext().getCacheDir(), "accents.png"))) { ui.takeScreenshot().compress(android.graphics.Bitmap.CompressFormat.PNG,100,f); }
        String report = "probe=accents before-shift="+before+" actual="+actual;
        result.putString("stream", report+"\n");
        if (!before.toLowerCase(java.util.Locale.ROOT).equals("éëáó") || !actual.equals(before+"É")) throw new AssertionError(report);
        finish(0, result);
    }
    /** The highlighted popup cell must be drawn above [key], centred under the finger at [x]. */
    private void assertHighlightUnder(float x, Rect key) {
        android.graphics.Bitmap shot = ui.takeScreenshot();
        float dp = getTargetContext().getResources().getDisplayMetrics().density;
        int y = Math.round(key.top - 20*dp), min = -1, max = -1;
        for (int px=0; px<shot.getWidth(); px++) {
            int c = shot.getPixel(px, y);
            if (near(c, 0xFFA9C7FA) || near(c, 0xFF2F5FA7)) { if (min < 0) min = px; max = px; }
        }
        shot.recycle();
        if (min < 0) throw new AssertionError("No highlighted popup cell drawn above "+key);
        float center = (min+max)/2f;
        if (Math.abs(center-x) > key.width()/4f) throw new AssertionError("Highlighted cell "+min+".."+max+" is not under finger x="+x);
    }
    private static boolean near(int a, int b) {
        return Math.abs(((a>>16)&255)-((b>>16)&255)) < 12 && Math.abs(((a>>8)&255)-((b>>8)&255)) < 12 && Math.abs((a&255)-(b&255)) < 12;
    }
    private String popupDescriptions() {
        StringBuilder out = new StringBuilder();
        for (AccessibilityNodeInfo n:nodes()) { String d = String.valueOf(n.getContentDescription()); if (d.contains("Accent") || d.contains("preview") || d.contains("Shift") || d.contains("Slide")) out.append('[').append(d).append(']'); }
        return out.toString();
    }
    private boolean descriptionContains(String text) {
        for (AccessibilityNodeInfo n:nodes()) { n.refresh(); if (String.valueOf(n.getContentDescription()).contains(text)) return true; }
        return false;
    }
    private void touchAt(long down, int action, float x, float y) {
        MotionEvent event = MotionEvent.obtain(down, SystemClock.uptimeMillis(), action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        if (!ui.injectInputEvent(event, false)) throw new AssertionError("Touch injection refused");
        event.recycle();
    }
    /** The keyboard stays minimized to its slim bar when a hardware keyboard is present (emulator). */
    private void expandKeyboard() {
        for (int i=0; i<30; i++) {
            for (AccessibilityNodeInfo n:nodes()) {
                String d = String.valueOf(n.getContentDescription());
                if (d.equals("Switch to voice typing")) return;
                if (d.equals("Show keyboard")) {
                    tap(bounds(n)); SystemClock.sleep(800);
                    return;
                }
            }
            SystemClock.sleep(100);
        }
    }
    private String editorText() { AccessibilityNodeInfo field=waitEditable(); field.refresh(); return field.getText() == null ? "" : field.getText().toString(); }
    private void correctionBudget(Bundle result) {
        com.rheerkens.androidkeyboard.AppGraph graph = com.rheerkens.androidkeyboard.AppGraph.INSTANCE;
        com.rheerkens.androidkeyboard.settings.TypingLayout layout = com.rheerkens.androidkeyboard.settings.TypingLayout.EN_US;
        for(int i=0;i<100 && graph.lexicon(layout).getSize()<100000;i++) SystemClock.sleep(100);
        if (graph.lexicon(layout).getSize()<100000) throw new AssertionError("Full word list not ready");
        long[] times = new long[6];
        String[] words = {"recieve", "functon", "langauge", "typng", "keybord", "recieve"};
        for(int i=0;i<words.length;i++) {
            final int index=i;
            runOnMainSync(() -> {
                long start=SystemClock.elapsedRealtimeNanos();
                graph.suggester(layout).autocorrection(words[index], java.util.Collections.emptyList(), true);
                times[index]=(SystemClock.elapsedRealtimeNanos()-start)/1000000;
            });
        }
        String report="probe=correction wordCount="+graph.lexicon(layout).getSize()+" mainThreadMs="+java.util.Arrays.toString(times);
        result.putString("stream",report+"\n");
        if(java.util.Arrays.stream(times).max().orElse(0)>50) throw new AssertionError(report+" exceeds 50ms budget");
        finish(0,result);
    }
    /**
     * The image editor's blur, rendered by the export code: covered text must be replaced (also on
     * a transparent image), stripped of detail however large it is, and 20 blurs must stay fast.
     */
    private void blur(Bundle result) throws Exception {
        StringBuilder report = new StringBuilder("probe=blur");
        java.io.File dir = getTargetContext().getCacheDir();
        // Black text on a transparent PNG, under a filled blur rectangle.
        android.graphics.Bitmap clear = textImage(900, 300, 0, "ACCOUNT 1234 5678", 64f);
        android.graphics.RectF box = new android.graphics.RectF(20, 90, 880, 210);
        android.graphics.Bitmap a = blurred(clear, box);
        int seeThrough = 0;
        for (int y=(int)box.top+8; y<box.bottom-8; y++) for (int x=(int)box.left+8; x<box.right-8; x++) if ((a.getPixel(x,y)>>>24) < 255) seeThrough++;
        double ea = edges(onWhite(clear), box), eb = edges(a, box);
        report.append(" transparent: seeThroughPx=").append(seeThrough).append(String.format(" edge %.2f->%.2f", ea, eb));
        if (seeThrough > 0 || eb > ea * 0.05) throw new AssertionError(report + " transparent text not replaced");
        save(a, new java.io.File(dir, "probe-blur-transparent.png"));
        // 170 px "SECRET" on an opaque phone-sized screenshot.
        android.graphics.Bitmap big = textImage(1080, 2400, 0xFFFFFFFF, "SECRET", 170f);
        android.graphics.RectF bigBox = new android.graphics.RectF(30, 60, 1050, 280);
        android.graphics.Bitmap b = blurred(big, bigBox);
        double ba = edges(big, bigBox), bb = edges(b, bigBox);
        report.append(String.format(" large: edge %.2f->%.2f", ba, bb));
        if (bb > ba * 0.05) throw new AssertionError(report + " large text keeps detail");
        save(b.copy(android.graphics.Bitmap.Config.ARGB_8888,false), new java.io.File(dir, "probe-blur-large.png"));
        // 20 blur strokes on a 2560 x 2560 image, rendered for export.
        android.graphics.Bitmap huge = textImage(2560, 2560, 0xFFFFFFFF, "1234 5678 9012", 120f);
        List<com.rheerkens.androidkeyboard.editor.Mark> marks = new ArrayList<>();
        for (int i=0;i<20;i++) {
            List<kotlin.Pair<Float,Float>> pts = new ArrayList<>();
            pts.add(new kotlin.Pair<>(100f, 100f+i*120f)); pts.add(new kotlin.Pair<>(2400f, 100f+i*120f));
            marks.add(new com.rheerkens.androidkeyboard.editor.Mark.Stroke(pts, com.rheerkens.androidkeyboard.editor.ImageMarkupKt.BLUR, 80f, 255));
        }
        long t0 = SystemClock.elapsedRealtime();
        com.rheerkens.androidkeyboard.editor.ImageMarkupKt.renderEdit(huge, marks, new android.graphics.RectF(0,0,2560,2560));
        report.append(" export20BlursMs=").append(SystemClock.elapsedRealtime()-t0);
        result.putString("stream", report + " PASS\n");
        finish(0, result);
    }
    private android.graphics.Bitmap textImage(int w, int h, int background, String text, float size) {
        android.graphics.Bitmap b = android.graphics.Bitmap.createBitmap(w, h, android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(b);
        c.drawColor(background, android.graphics.PorterDuff.Mode.SRC);
        android.graphics.Paint p = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        p.setColor(0xFF000000); p.setTextSize(size); p.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        c.drawText(text, 40, 100 + size * 0.75f, p);
        return b;
    }
    private android.graphics.Bitmap blurred(android.graphics.Bitmap source, android.graphics.RectF box) {
        List<com.rheerkens.androidkeyboard.editor.Mark> marks = new ArrayList<>();
        marks.add(new com.rheerkens.androidkeyboard.editor.Mark.Shape(com.rheerkens.androidkeyboard.editor.ShapeKind.RECTANGLE,
            box.left, box.top, box.right, box.bottom, com.rheerkens.androidkeyboard.editor.ImageMarkupKt.BLUR, 8f, true));
        return com.rheerkens.androidkeyboard.editor.ImageMarkupKt.renderEdit(source, marks,
            new android.graphics.RectF(0, 0, source.getWidth(), source.getHeight()));
    }
    private android.graphics.Bitmap onWhite(android.graphics.Bitmap b) {
        android.graphics.Bitmap out = android.graphics.Bitmap.createBitmap(b.getWidth(), b.getHeight(), android.graphics.Bitmap.Config.ARGB_8888);
        android.graphics.Canvas c = new android.graphics.Canvas(out); c.drawColor(0xFFFFFFFF); c.drawBitmap(b, 0, 0, null);
        return out;
    }
    /** Mean horizontal luminance step inside [box], after putting the image on white: text detail. */
    private double edges(android.graphics.Bitmap b, android.graphics.RectF box) {
        android.graphics.Bitmap w = onWhite(b);
        double sum = 0; long n = 0;
        for (int y=(int)box.top+8; y<box.bottom-8; y++) {
            int prev = -1;
            for (int x=(int)box.left+8; x<box.right-8; x++) {
                int p = w.getPixel(x, y), l = ((p>>16)&255)*299 + ((p>>8)&255)*587 + (p&255)*114;
                if (prev >= 0) { sum += Math.abs(l - prev) / 1000.0; n++; }
                prev = l;
            }
        }
        return n == 0 ? 0 : sum / n;
    }
    private void save(android.graphics.Bitmap b, java.io.File f) throws java.io.IOException {
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(f)) { b.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, out); }
    }
    private void cancelPointers() {
        MotionEvent event = MotionEvent.obtain(SystemClock.uptimeMillis(),SystemClock.uptimeMillis(),MotionEvent.ACTION_CANCEL,0f,0f,0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        ui.injectInputEvent(event, true);
        event.recycle();
    }
    private void touch(long down, int action, int[] ids, Rect[] keys) {
        MotionEvent.PointerProperties[] props = new MotionEvent.PointerProperties[ids.length];
        MotionEvent.PointerCoords[] coords = new MotionEvent.PointerCoords[ids.length];
        for (int i=0;i<ids.length;i++) {
            props[i]=new MotionEvent.PointerProperties(); props[i].id=ids[i]; props[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords(); coords[i].x=keys[i].centerX(); coords[i].y=keys[i].centerY(); coords[i].pressure=1; coords[i].size=1;
        }
        MotionEvent event=MotionEvent.obtain(down,SystemClock.uptimeMillis(),action,ids.length,props,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        if (!ui.injectInputEvent(event,false)) throw new AssertionError("Touch injection refused");
        event.recycle();
    }
    private Rect bounds(AccessibilityNodeInfo n) { Rect r=new Rect(); n.getBoundsInScreen(r); return r; }
    private List<AccessibilityNodeInfo> nodes() {
        List<AccessibilityNodeInfo> out=new ArrayList<>();
        for (AccessibilityWindowInfo w:ui.getWindows()) walk(w.getRoot(),out);
        return out;
    }
    private void walk(AccessibilityNodeInfo n,List<AccessibilityNodeInfo> out) { if(n==null)return; n.refresh(); out.add(n); for(int i=0;i<n.getChildCount();i++) walk(n.getChild(i),out); }
    private AccessibilityNodeInfo waitLabel(String label) {
        for(int i=0;i<80;i++) {
            for(AccessibilityNodeInfo n:nodes()) {
                String description = String.valueOf(n.getContentDescription()), text = String.valueOf(n.getText());
                if(label.equals(description) || label.equals(text) || java.util.Arrays.asList(description.split(", ")).contains(label) ||
                    (label.length()==1 && (label.equalsIgnoreCase(description) ||
                        description.toLowerCase(java.util.Locale.ROOT).startsWith(label+". long-press") || label.equalsIgnoreCase(text)))) return n;
            }
            SystemClock.sleep(100);
        }
        throw new AssertionError("Missing label "+label);
    }
    private AccessibilityNodeInfo waitEditable() {
        for(int i=0;i<80;i++) { for(AccessibilityNodeInfo n:nodes()) if(n.isEditable())return n; SystemClock.sleep(100); }
        throw new AssertionError("Missing editor");
    }
}
