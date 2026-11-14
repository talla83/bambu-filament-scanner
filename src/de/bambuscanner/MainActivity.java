package de.bambuscanner;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {

    // Farben im Stil des dunklen Bambu-Studio-Themes
    static final int BG = 0xFF1F1F23, PANEL = 0xFF2B2B30, PANEL2 = 0xFF323238, BORDER = 0xFF3A3A41;
    static final int TEXT = 0xFFE8E8EA, SUB = 0xFF9A9AA2, GREEN = 0xFF00AE42, RED = 0xFFE5484D;

    private static final int IDLE = 0, READING = 1, RESULT = 2, ERROR = 3;

    private NfcAdapter nfc;
    private History history;
    private FrameLayout content;
    private TextView tabScan, tabHist;
    private View indScan, indHist;

    private int tab = 0;
    private int scanState = IDLE;
    private String errorMsg = "";
    private BambuTag current;      // zuletzt gescannt
    private BambuTag histDetail;   // geöffneter Verlaufseintrag
    private boolean rawOpen = false;

    // ------------------------------------------------------------ Lebenszyklus

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        nfc = NfcAdapter.getDefaultAdapter(this);
        history = new History(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);

        // Kopfzeile
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackgroundColor(PANEL);

        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        titleRow.setPadding(dp(16), dp(14), dp(16), dp(6));
        SpoolView logo = new SpoolView(this);
        logo.setColors(GREEN, null, 255);
        titleRow.addView(logo, new LinearLayout.LayoutParams(dp(26), dp(26)));
        TextView title = text("Filament Scanner", 19, TEXT, true);
        title.setPadding(dp(10), 0, 0, 0);
        titleRow.addView(title);
        header.addView(titleRow);

        // Tabs (wie Home / Vorbereiten / Vorschau / Gerät in Bambu Studio)
        LinearLayout tabs = new LinearLayout(this);
        tabs.setPadding(dp(8), 0, dp(8), 0);
        tabScan = tabLabel("Scannen");
        tabHist = tabLabel("Verlauf");
        indScan = new View(this);
        indHist = new View(this);
        tabs.addView(tabCell(tabScan, indScan, 0));
        tabs.addView(tabCell(tabHist, indHist, 1));
        header.addView(tabs);
        root.addView(header);

        View line = new View(this);
        line.setBackgroundColor(BORDER);
        root.addView(line, new LinearLayout.LayoutParams(-1, 1));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        render();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (nfc != null && nfc.isEnabled()) {
            Bundle opts = new Bundle();
            opts.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, 250);
            nfc.enableReaderMode(this, this,
                    NfcAdapter.FLAG_READER_NFC_A | NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK, opts);
        }
        render();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (nfc != null) nfc.disableReaderMode(this);
    }

    @Override
    public void onBackPressed() {
        if (tab == 1 && histDetail != null) {
            histDetail = null;
            render();
        } else if (tab == 1) {
            tab = 0;
            render();
        } else {
            super.onBackPressed();
        }
    }

    // ------------------------------------------------------------ NFC

    @Override
    public void onTagDiscovered(Tag tag) {
        runOnUiThread(() -> {
            scanState = READING;
            tab = 0;
            histDetail = null;
            render();
        });
        try {
            final BambuTag t = TagReader.read(tag);
            history.add(t);
            runOnUiThread(() -> {
                current = t;
                scanState = RESULT;
                rawOpen = false;
                render();
                content.performHapticFeedback(HapticFeedbackConstants.CONFIRM);
            });
        } catch (TagReader.ReadException e) {
            final String msg = e.getMessage();
            runOnUiThread(() -> {
                errorMsg = msg;
                scanState = ERROR;
                render();
                content.performHapticFeedback(HapticFeedbackConstants.REJECT);
            });
        }
    }

    // ------------------------------------------------------------ Rendering

    private void render() {
        if (content == null) return;
        styleTab(tabScan, indScan, tab == 0);
        styleTab(tabHist, indHist, tab == 1);
        content.removeAllViews();

        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(14), dp(14), dp(24));
        sv.addView(col);
        content.addView(sv);

        if (tab == 0) renderScan(col);
        else renderHistory(col);
    }

    private void renderScan(LinearLayout col) {
        if (nfc == null) {
            col.addView(messageCard("Kein NFC", "Dieses Gerät hat keinen NFC-Chip – Bambu-Tags können nicht gelesen werden.", RED, null));
            return;
        }
        if (!nfc.isEnabled()) {
            Button b = greenButton("NFC-Einstellungen öffnen");
            b.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_NFC_SETTINGS)));
            col.addView(messageCard("NFC ist ausgeschaltet", "Schalte NFC ein, um Spulen zu scannen.", RED, b));
            return;
        }

        // Status-Karte
        LinearLayout status = card();
        status.setGravity(Gravity.CENTER_HORIZONTAL);
        boolean big = current == null || scanState == READING;
        if (scanState == READING) {
            ProgressBar pb = new ProgressBar(this);
            pb.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(GREEN));
            status.addView(pb, new LinearLayout.LayoutParams(dp(56), dp(56)));
            status.addView(centered(text("Lese Tag …", 17, TEXT, true), dp(12)));
            status.addView(centered(text("Spule nicht bewegen", 13, SUB, false), dp(4)));
        } else {
            if (big) {
                SpoolView pulse = new SpoolView(this);
                pulse.setEmpty();
                AlphaAnimation a = new AlphaAnimation(1f, 0.35f);
                a.setDuration(900);
                a.setRepeatMode(Animation.REVERSE);
                a.setRepeatCount(Animation.INFINITE);
                pulse.startAnimation(a);
                status.addView(pulse, new LinearLayout.LayoutParams(dp(120), dp(120)));
                status.addView(centered(text("Bereit zum Scannen", 17, TEXT, true), dp(14)));
                status.addView(centered(text("Halte die Spule mit der Seite an die Rückseite deines Handys. "
                        + "Der Tag sitzt in der Nabe der Spule.", 13, SUB, false), dp(6)));
            } else {
                LinearLayout row = new LinearLayout(this);
                row.setGravity(Gravity.CENTER_VERTICAL);
                View dot = new View(this);
                dot.setBackground(round(GREEN, dp(5)));
                row.addView(dot, new LinearLayout.LayoutParams(dp(10), dp(10)));
                TextView t = text("Bereit – nächste Spule anhalten", 14, SUB, false);
                t.setPadding(dp(10), 0, 0, 0);
                row.addView(t);
                status.setGravity(Gravity.START);
                status.addView(row);
            }
            if (scanState == ERROR) {
                TextView err = text(errorMsg, 14, RED, false);
                err.setPadding(dp(12), dp(10), dp(12), dp(10));
                err.setBackground(box(0x22E5484D, 0x66E5484D, dp(6)));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = dp(14);
                status.addView(err, lp);
            }
        }
        col.addView(status);

        if (current != null && scanState != READING) {
            space(col, 12);
            renderDetail(col, current);
        }
    }

    private void renderHistory(LinearLayout col) {
        if (histDetail != null) {
            TextView back = text("‹  Verlauf", 15, GREEN, true);
            back.setPadding(dp(4), dp(2), dp(4), dp(12));
            back.setOnClickListener(v -> { histDetail = null; render(); });
            col.addView(back);
            renderDetail(col, histDetail);
            return;
        }
        final List<BambuTag> list = history.all();
        if (list.isEmpty()) {
            col.addView(messageCard("Noch keine Spulen", "Gescannte Spulen erscheinen hier.", SUB, null));
            return;
        }
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setPadding(dp(4), 0, dp(4), dp(10));
        TextView count = text(list.size() + (list.size() == 1 ? " Spule" : " Spulen"), 14, SUB, false);
        top.addView(count, new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = text("Alle löschen", 14, SUB, false);
        clear.setOnClickListener(v -> new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("Verlauf löschen?")
                .setMessage("Alle " + list.size() + " Einträge werden entfernt.")
                .setNegativeButton("Abbrechen", null)
                .setPositiveButton("Löschen", (d, w) -> { history.clear(); render(); })
                .show());
        top.addView(clear);
        col.addView(top);

        LinearLayout c = card();
        c.setPadding(0, dp(4), 0, dp(4));
        SimpleDateFormat df = new SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY);
        for (int i = 0; i < list.size(); i++) {
            final BambuTag t = list.get(i);
            if (i > 0) c.addView(divider(dp(70)));
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(14), dp(10), dp(14), dp(10));
            row.setBackground(ripple());
            SpoolView sp = new SpoolView(this);
            sp.setColors(t.color, t.color2, t.alpha);
            row.addView(sp, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout txt = new LinearLayout(this);
            txt.setOrientation(LinearLayout.VERTICAL);
            txt.setPadding(dp(14), 0, dp(8), 0);
            txt.addView(text(t.displayName(), 15, TEXT, true));
            txt.addView(text(ColorNames.nearest(t.color) + "  ·  " + t.colorHex(), 13, SUB, false));
            row.addView(txt, new LinearLayout.LayoutParams(0, -2, 1));
            row.addView(text(df.format(new Date(t.scannedAt)), 12, SUB, false));

            row.setOnClickListener(v -> { histDetail = t; rawOpen = false; render(); });
            row.setOnLongClickListener(v -> {
                new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                        .setTitle("Eintrag löschen?")
                        .setMessage(t.displayName() + " – " + t.colorHex())
                        .setNegativeButton("Abbrechen", null)
                        .setPositiveButton("Löschen", (d, w) -> { history.remove(t); render(); })
                        .show();
                return true;
            });
            c.addView(row);
        }
        col.addView(c);
        TextView hint = text("Tippen zum Öffnen · lange drücken zum Löschen", 12, SUB, false);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(10), 0, 0);
        col.addView(hint);
    }

    private void renderDetail(LinearLayout col, final BambuTag t) {
        // Kopf: Spule + Name + Farbe
        LinearLayout head = card();
        LinearLayout hr = new LinearLayout(this);
        hr.setGravity(Gravity.CENTER_VERTICAL);
        SpoolView sp = new SpoolView(this);
        sp.setColors(t.color, t.color2, t.alpha);
        hr.addView(sp, new LinearLayout.LayoutParams(dp(96), dp(96)));
        LinearLayout ht = new LinearLayout(this);
        ht.setOrientation(LinearLayout.VERTICAL);
        ht.setPadding(dp(16), 0, 0, 0);
        ht.addView(text(t.displayName(), 21, TEXT, true));
        TextView cn = text(ColorNames.nearest(t.color), 15, TEXT, false);
        cn.setPadding(0, dp(2), 0, dp(8));
        ht.addView(cn);
        LinearLayout chips = new LinearLayout(this);
        chips.addView(chip(t.colorHex(), t.color));
        if (t.color2 != null) chips.addView(chip(t.color2Hex(), t.color2));
        if (!t.filamentType.isEmpty()) chips.addView(chip(t.filamentType, null));
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.setHorizontalScrollBarEnabled(false);
        hs.addView(chips);
        ht.addView(hs);
        hr.addView(ht, new LinearLayout.LayoutParams(0, -2, 1));
        head.addView(hr);
        col.addView(head);

        // Filament
        space(col, 12);
        LinearLayout f = section(col, "Filament");
        kv(f, "Material", t.filamentType);
        kv(f, "Typ", t.detailedType);
        kv(f, "Material-ID", t.materialId);
        kv(f, "Varianten-ID", t.variantId);
        kv(f, "Durchmesser", t.diameter > 0 ? fmt(t.diameter, 2) + " mm" : "");
        kv(f, "Gewicht", t.spoolWeight > 0 ? t.spoolWeight + " g" : "");
        kv(f, "Länge", t.lengthM > 0 ? t.lengthM + " m" : "");
        kv(f, "Farben", t.colorCount > 1 ? t.colorCount + " (Mehrfarbig)" : "1");
        kv(f, "Transparenz", t.alpha < 255 ? Math.round(100 - t.alpha / 2.55f) + " %" : "");

        // Druck
        space(col, 12);
        LinearLayout pr = section(col, "Druckeinstellungen");
        kv(pr, "Düsentemperatur",
                t.minHotend > 0 || t.maxHotend > 0 ? t.minHotend + " – " + t.maxHotend + " °C" : "");
        kv(pr, "Betttemperatur", t.bedTemp > 0 ? t.bedTemp + " °C" : "");
        kv(pr, "Düse (unbestätigt)",t.nozzleDiameter > 0.05f && t.nozzleDiameter < 5f
                ? fmt(t.nozzleDiameter, 1) + " mm" : "");

        // Trocknung
        space(col, 12);
        LinearLayout dr = section(col, "Trocknung");
        kv(dr, "Temperatur", t.dryTemp > 0 ? t.dryTemp + " °C" : "");
        kv(dr, "Dauer", t.dryTime > 0 ? t.dryTime + " h" : "");

        // Produktion
        space(col, 12);
        LinearLayout po = section(col, "Produktion & Tag");
        kv(po, "Hergestellt", t.productionDateFormatted());
        kv(po, "Tag-UID", t.uid);
        kv(po, "Spulen-ID", t.trayUid);

        // Rohdaten (aufklappbar)
        space(col, 12);
        LinearLayout raw = card();
        TextView rh = text((rawOpen ? "▾  " : "▸  ") + "Rohdaten (Hex-Dump)", 15, TEXT, true);
        rh.setOnClickListener(v -> { rawOpen = !rawOpen; render(); });
        raw.addView(rh);
        if (rawOpen) {
            TextView dump = text(t.hexDump(), 11, SUB, false);
            dump.setTypeface(Typeface.MONOSPACE);
            dump.setTextIsSelectable(true);
            dump.setPadding(0, dp(10), 0, 0);
            HorizontalScrollView hsv = new HorizontalScrollView(this);
            hsv.addView(dump);
            raw.addView(hsv);
        }
        col.addView(raw);

        // Teilen
        space(col, 16);
        Button share = greenButton("Teilen");
        share.setOnClickListener(v -> share(t));
        col.addView(share, new LinearLayout.LayoutParams(-1, dp(48)));
    }

    private void share(BambuTag t) {
        StringBuilder sb = new StringBuilder();
        sb.append(t.displayName()).append(" – ").append(ColorNames.nearest(t.color))
                .append(" (").append(t.colorHex()).append(")\n");
        if (t.diameter > 0) sb.append("Durchmesser: ").append(fmt(t.diameter, 2)).append(" mm\n");
        if (t.spoolWeight > 0) sb.append("Gewicht: ").append(t.spoolWeight).append(" g\n");
        if (t.maxHotend > 0) sb.append("Düse: ").append(t.minHotend).append("–").append(t.maxHotend).append(" °C\n");
        if (t.bedTemp > 0) sb.append("Bett: ").append(t.bedTemp).append(" °C\n");
        if (t.dryTemp > 0) sb.append("Trocknen: ").append(t.dryTemp).append(" °C, ").append(t.dryTime).append(" h\n");
        sb.append("Material-ID: ").append(t.materialId).append(" / ").append(t.variantId).append('\n');
        sb.append("Hergestellt: ").append(t.productionDateFormatted()).append('\n');
        sb.append("Tag-UID: ").append(t.uid).append("\n\n").append(t.hexDump());
        Intent i = new Intent(Intent.ACTION_SEND);
        i.setType("text/plain");
        i.putExtra(Intent.EXTRA_SUBJECT, t.displayName() + " " + t.colorHex());
        i.putExtra(Intent.EXTRA_TEXT, sb.toString());
        startActivity(Intent.createChooser(i, "Spule teilen"));
    }

    // ------------------------------------------------------------ UI-Bausteine

    private LinearLayout tabCell(TextView label, View ind, final int idx) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setPadding(dp(10), 0, dp(10), 0);
        cell.setLayoutParams(new LinearLayout.LayoutParams(-2, -2));
        cell.addView(label, new LinearLayout.LayoutParams(-2, -2));
        cell.addView(ind, new LinearLayout.LayoutParams(-1, dp(3)));
        cell.setOnClickListener(v -> {
            tab = idx;
            if (idx == 1) histDetail = null;
            render();
        });
        return cell;
    }

    private TextView tabLabel(String s) {
        TextView t = text(s, 15, SUB, true);
        t.setPadding(dp(4), dp(10), dp(4), dp(10));
        return t;
    }

    private void styleTab(TextView t, View ind, boolean active) {
        t.setTextColor(active ? GREEN : SUB);
        ind.setBackgroundColor(active ? GREEN : Color.TRANSPARENT);
    }

    private LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(16), dp(16), dp(16));
        c.setBackground(box(PANEL, BORDER, dp(8)));
        c.setLayoutParams(new LinearLayout.LayoutParams(-1, -2));
        return c;
    }

    /** Abschnitt im Stil der Bambu-Studio-Seitenleiste: grüner Marker + Titel. */
    private LinearLayout section(LinearLayout parent, String title) {
        LinearLayout c = card();
        c.setPadding(dp(16), dp(12), dp(16), dp(8));
        LinearLayout h = new LinearLayout(this);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(0, 0, 0, dp(6));
        View mark = new View(this);
        mark.setBackground(round(GREEN, dp(2)));
        h.addView(mark, new LinearLayout.LayoutParams(dp(4), dp(16)));
        TextView t = text(title, 15, TEXT, true);
        t.setPadding(dp(10), 0, 0, 0);
        h.addView(t);
        c.addView(h);
        parent.addView(c);
        return c;
    }

    private void kv(LinearLayout parent, String k, String v) {
        if (TextUtils.isEmpty(v)) return;
        if (parent.getChildCount() > 1) parent.addView(divider(0));
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(9), 0, dp(9));
        row.addView(text(k, 14, SUB, false), new LinearLayout.LayoutParams(0, -2, 1));
        TextView val = text(v, 14, TEXT, false);
        val.setGravity(Gravity.END);
        val.setTextIsSelectable(true);
        row.addView(val, new LinearLayout.LayoutParams(0, -2, 1.4f));
        parent.addView(row);
    }

    private View chip(String label, Integer swatch) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(8), dp(4), dp(10), dp(4));
        c.setBackground(box(PANEL2, BORDER, dp(12)));
        if (swatch != null) {
            View s = new View(this);
            GradientDrawable d = round(swatch, dp(6));
            d.setStroke(1, 0x55FFFFFF);
            s.setBackground(d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(12), dp(12));
            lp.rightMargin = dp(6);
            c.addView(s, lp);
        }
        TextView t = text(label, 12, TEXT, false);
        if (swatch != null) t.setTypeface(Typeface.MONOSPACE);
        c.addView(t);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        c.setLayoutParams(lp);
        return c;
    }

    private LinearLayout messageCard(String title, String msg, int color, View extra) {
        LinearLayout c = card();
        c.addView(text(title, 17, color == SUB ? TEXT : color, true));
        TextView m = text(msg, 14, SUB, false);
        m.setPadding(0, dp(6), 0, 0);
        c.addView(m);
        if (extra != null) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(48));
            lp.topMargin = dp(16);
            c.addView(extra, lp);
        }
        return c;
    }

    private Button greenButton(String label) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setStateListAnimator(null);
        b.setBackground(new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x33FFFFFF), round(GREEN, dp(6)), null));
        return b;
    }

    private android.graphics.drawable.Drawable ripple() {
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22FFFFFF), null,
                new android.graphics.drawable.ColorDrawable(Color.WHITE));
    }

    private View divider(int leftInset) {
        View v = new View(this);
        v.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, 1);
        lp.leftMargin = leftInset;
        v.setLayoutParams(lp);
        return v;
    }

    private TextView centered(TextView t, int topPad) {
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(8), topPad, dp(8), 0);
        return t;
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void space(LinearLayout parent, int dpH) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(dpH)));
    }

    private GradientDrawable box(int fill, int stroke, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setStroke(1, stroke);
        d.setCornerRadius(radius);
        return d;
    }

    private GradientDrawable round(int fill, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(fill);
        d.setCornerRadius(radius);
        return d;
    }

    private static String fmt(float f, int digits) {
        return String.format(Locale.GERMANY, "%." + digits + "f", f);
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
