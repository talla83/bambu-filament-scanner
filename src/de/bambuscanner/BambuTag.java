package de.bambuscanner;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Daten eines Bambu-Lab-Filament-Tags (MIFARE Classic 1K).
 * Layout laut https://github.com/Bambu-Research-Group/RFID-Tag-Guide
 */
public final class BambuTag {

    public static final int BLOCKS = 64;

    /** HKDF-Salt ("Master") laut RFID-Tag-Guide / deriveKeys.py */
    private static final byte[] HKDF_SALT = {
            (byte) 0x9a, (byte) 0x75, (byte) 0x9c, (byte) 0xf2, (byte) 0xc4, (byte) 0xf7, (byte) 0xca, (byte) 0xff,
            (byte) 0x22, (byte) 0x2c, (byte) 0xb9, (byte) 0x76, (byte) 0x9b, (byte) 0x41, (byte) 0xbc, (byte) 0x96};
    private static final byte[] HKDF_INFO = {'R', 'F', 'I', 'D', '-', 'A', 0};

    public final byte[][] blocks; // 64 x 16 Byte, null = nicht gelesen
    public long scannedAt;

    public String uid = "";
    public String variantId = "", materialId = "", filamentType = "", detailedType = "";
    public String trayUid = "", productionDate = "", shortDate = "";
    public int color = 0xFF808080;       // ARGB
    public int alpha = 255;
    public int spoolWeight, dryTemp, dryTime, bedTempType, bedTemp, maxHotend, minHotend, lengthM;
    public float diameter, nozzleDiameter, spoolWidth;
    public int colorCount = 1;
    public Integer color2 = null;        // ARGB, nur bei Mehrfarb-Spulen

    public BambuTag(byte[][] blocks, long scannedAt) {
        this.blocks = blocks;
        this.scannedAt = scannedAt;
        parse();
    }

    // ---------- Schlüsselableitung ----------

    /** Leitet die 16 Key-A-Schlüssel (je 6 Byte) per HKDF-SHA256 aus der Tag-UID ab. */
    public static byte[][] deriveKeys(byte[] uid) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(HKDF_SALT, "HmacSHA256"));
        byte[] prk = mac.doFinal(uid);                         // HKDF-Extract
        mac.init(new SecretKeySpec(prk, "HmacSHA256"));
        byte[] okm = new byte[16 * 6];
        byte[] t = new byte[0];
        int pos = 0;
        for (int i = 1; pos < okm.length; i++) {               // HKDF-Expand
            mac.update(t);
            mac.update(HKDF_INFO);
            mac.update((byte) i);
            t = mac.doFinal();
            int n = Math.min(t.length, okm.length - pos);
            System.arraycopy(t, 0, okm, pos, n);
            pos += n;
        }
        byte[][] keys = new byte[16][6];
        for (int s = 0; s < 16; s++) System.arraycopy(okm, s * 6, keys[s], 0, 6);
        return keys;
    }

    // ---------- Parsing ----------

    private void parse() {
        byte[] b0 = blk(0);
        uid = hex(b0, 0, 4);
        variantId = str(blk(1), 0, 8);
        materialId = str(blk(1), 8, 8);
        filamentType = str(blk(2), 0, 16);
        detailedType = str(blk(4), 0, 16);

        byte[] b5 = blk(5);
        int r = b5[0] & 0xFF, g = b5[1] & 0xFF, b = b5[2] & 0xFF;
        alpha = b5[3] & 0xFF;
        color = 0xFF000000 | (r << 16) | (g << 8) | b;
        spoolWeight = u16(b5, 4);
        diameter = f32(b5, 8);

        byte[] b6 = blk(6);
        dryTemp = u16(b6, 0);
        dryTime = u16(b6, 2);
        bedTempType = u16(b6, 4);
        bedTemp = u16(b6, 6);
        maxHotend = u16(b6, 8);
        minHotend = u16(b6, 10);

        nozzleDiameter = f32(blk(8), 12);
        trayUid = hex(blk(9), 0, 16);
        spoolWidth = u16(blk(10), 4) / 100f;
        productionDate = str(blk(12), 0, 16);
        shortDate = str(blk(13), 0, 16);
        lengthM = u16(blk(14), 4);

        byte[] b16 = blk(16);
        int format = u16(b16, 0);
        if (format == 2) {
            colorCount = Math.max(1, u16(b16, 2));
            // zweite Farbe als ABGR gespeichert
            int a2 = b16[4] & 0xFF, bb = b16[5] & 0xFF, gg = b16[6] & 0xFF, rr = b16[7] & 0xFF;
            if (colorCount > 1 && (a2 | bb | gg | rr) != 0) color2 = 0xFF000000 | (rr << 16) | (gg << 8) | bb;
        }
    }

    public boolean looksValid() {
        return blocks[1] != null && blocks[2] != null && !filamentType.isEmpty();
    }

    public String displayName() {
        if (!detailedType.isEmpty()) return detailedType;
        if (!filamentType.isEmpty()) return filamentType;
        return "Unbekanntes Filament";
    }

    public String colorHex() {
        return String.format(Locale.ROOT, "#%06X", color & 0xFFFFFF);
    }

    public String color2Hex() {
        return color2 == null ? "" : String.format(Locale.ROOT, "#%06X", color2 & 0xFFFFFF);
    }

    /** "2024_05_10_08_33" -> "10.05.2024 08:33" */
    public String productionDateFormatted() {
        String[] p = productionDate.split("_");
        if (p.length >= 5) return p[2] + "." + p[1] + "." + p[0] + " " + p[3] + ":" + p[4];
        if (p.length >= 3) return p[2] + "." + p[1] + "." + p[0];
        return productionDate;
    }

    // ---------- Serialisierung (für den Verlauf) ----------

    public String toDump() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BLOCKS; i++) {
            if (i > 0) sb.append(',');
            if (blocks[i] != null) sb.append(hex(blocks[i], 0, 16));
        }
        return sb.toString();
    }

    public static BambuTag fromDump(String dump, long time) {
        byte[][] bl = new byte[BLOCKS][];
        String[] parts = dump.split(",", -1);
        for (int i = 0; i < BLOCKS && i < parts.length; i++) {
            if (parts[i].length() == 32) bl[i] = unhex(parts[i]);
        }
        return new BambuTag(bl, time);
    }

    /** Lesbarer Hex-Dump aller gelesenen Blöcke. */
    public String hexDump() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < BLOCKS; i++) {
            sb.append(String.format(Locale.ROOT, "%02d  ", i));
            if (blocks[i] == null) {
                sb.append("-- nicht gelesen --");
            } else {
                for (int j = 0; j < 16; j++) {
                    sb.append(String.format(Locale.ROOT, "%02X", blocks[i][j]));
                    if (j == 7) sb.append(' ');
                }
            }
            if (i < BLOCKS - 1) sb.append('\n');
        }
        return sb.toString();
    }

    // ---------- Helfer ----------

    private byte[] blk(int i) {
        return blocks[i] != null ? blocks[i] : new byte[16];
    }

    private static int u16(byte[] b, int o) {
        return (b[o] & 0xFF) | ((b[o + 1] & 0xFF) << 8);
    }

    private static float f32(byte[] b, int o) {
        return ByteBuffer.wrap(b, o, 4).order(ByteOrder.LITTLE_ENDIAN).getFloat();
    }

    private static String str(byte[] b, int o, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = o; i < o + len; i++) {
            int c = b[i] & 0xFF;
            if (c == 0) break;
            if (c >= 0x20 && c < 0x7F) sb.append((char) c);
        }
        return sb.toString().trim();
    }

    public static String hex(byte[] b, int o, int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = o; i < o + len; i++) sb.append(String.format(Locale.ROOT, "%02X", b[i]));
        return sb.toString();
    }

    private static byte[] unhex(String s) {
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++)
            out[i] = (byte) Integer.parseInt(s.substring(i * 2, i * 2 + 2), 16);
        return out;
    }
}
