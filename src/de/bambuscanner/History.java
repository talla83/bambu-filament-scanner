package de.bambuscanner;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Verlauf der gescannten Spulen (lokal in SharedPreferences). Pro Tag-UID ein Eintrag. */
public final class History {

    private final SharedPreferences prefs;

    public History(Context ctx) {
        prefs = ctx.getSharedPreferences("history", Context.MODE_PRIVATE);
    }

    public List<BambuTag> all() {
        List<BambuTag> out = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(prefs.getString("entries", "[]"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(BambuTag.fromDump(o.getString("dump"), o.getLong("time")));
            }
        } catch (Exception ignored) { }
        return out;
    }

    /** Neuester Eintrag zuerst; vorhandener Eintrag mit gleicher UID wird ersetzt. */
    public void add(BambuTag t) {
        List<BambuTag> list = all();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).uid.equals(t.uid)) list.remove(i);
        }
        list.add(0, t);
        save(list);
    }

    public void remove(BambuTag t) {
        List<BambuTag> list = all();
        for (int i = list.size() - 1; i >= 0; i--) {
            if (list.get(i).uid.equals(t.uid)) list.remove(i);
        }
        save(list);
    }

    public void clear() {
        prefs.edit().remove("entries").apply();
    }

    private void save(List<BambuTag> list) {
        JSONArray arr = new JSONArray();
        try {
            for (BambuTag t : list) {
                JSONObject o = new JSONObject();
                o.put("time", t.scannedAt);
                o.put("dump", t.toDump());
                arr.put(o);
            }
        } catch (Exception ignored) { }
        prefs.edit().putString("entries", arr.toString()).apply();
    }
}
