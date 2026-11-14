package de.bambuscanner;

/** Nächstgelegener Bambu-Farbname zu einem RGB-Wert (Tabelle: Bambu PLA Basic/Matte, ungefähr). */
public final class ColorNames {

    private static final Object[][] TABLE = {
            {"Jade White", 0xFFFFFF}, {"Beige", 0xF7E6DE}, {"Light Gray", 0xD1D3D5},
            {"Gray", 0x8E9089}, {"Silver", 0xA6A9AA}, {"Blue Grey", 0x5B6579},
            {"Dark Gray", 0x545454}, {"Black", 0x000000}, {"Yellow", 0xF4EE2A},
            {"Sunflower Yellow", 0xFEC600}, {"Gold", 0xE4BD68}, {"Pumpkin Orange", 0xFF8E16},
            {"Orange", 0xFF6A13}, {"Bright Green", 0xBECF00}, {"Bambu Green", 0x00AE42},
            {"Mistletoe Green", 0x3F8E43}, {"Pink", 0xF55A74}, {"Hot Pink", 0xF5547C},
            {"Magenta", 0xEC008C}, {"Red", 0xC12E1F}, {"Maroon Red", 0x9D2235},
            {"Purple", 0x5E43B7}, {"Indigo Purple", 0x482960}, {"Turquoise", 0x00B1B7},
            {"Cyan", 0x0086D6}, {"Cobalt Blue", 0x0056B8}, {"Blue", 0x0A2989},
            {"Brown", 0x9D432C}, {"Cocoa Brown", 0x6F5034}, {"Bronze", 0x847D48},
            {"Ivory White", 0xFFFFFF}, {"Bone White", 0xCBC6B8}, {"Lemon Yellow", 0xF7D959},
            {"Mandarin Orange", 0xF99963}, {"Sakura Pink", 0xE8AFCF}, {"Lilac Purple", 0xAE96D4},
            {"Plum", 0x950051}, {"Scarlet Red", 0xDE4343}, {"Dark Red", 0xBB3D43},
            {"Apple Green", 0xC2E189}, {"Grass Green", 0x61C680}, {"Dark Green", 0x68724D},
            {"Ice Blue", 0xA3D8E1}, {"Sky Blue", 0x56B7E6}, {"Marine Blue", 0x0078BF},
            {"Dark Blue", 0x042F56}, {"Desert Tan", 0xE8DBB7}, {"Latte Brown", 0xD3B7A7},
            {"Caramel", 0xAE835B}, {"Terracotta", 0xB15533}, {"Dark Brown", 0x7D6556},
            {"Dark Chocolate", 0x4D3324}, {"Ash Gray", 0x9B9EA0}, {"Nardo Gray", 0x757575},
            {"Charcoal", 0x000000},
    };

    /** Liefert den Namen; mit "≈ " davor, wenn die Farbe nicht exakt passt. */
    public static String nearest(int argb) {
        int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        String best = "";
        long bestD = Long.MAX_VALUE;
        for (Object[] e : TABLE) {
            int c = (Integer) e[1];
            int dr = r - ((c >> 16) & 0xFF), dg = g - ((c >> 8) & 0xFF), db = b - (c & 0xFF);
            long d = (long) dr * dr + (long) dg * dg + (long) db * db;
            if (d < bestD) { bestD = d; best = (String) e[0]; }
        }
        return bestD == 0 ? best : "≈ " + best;
    }
}
