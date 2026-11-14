package de.bambuscanner;

import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.MifareClassic;

import java.io.IOException;
import java.util.Arrays;

/** Liest einen Bambu-Tag per MIFARE-Classic-Authentifizierung mit abgeleiteten Schlüsseln. */
public final class TagReader {

    public static final class ReadException extends Exception {
        public ReadException(String msg) { super(msg); }
    }

    public static BambuTag read(Tag tag) throws ReadException {
        MifareClassic mfc = MifareClassic.get(tag);
        if (mfc == null) {
            String techs = Arrays.toString(tag.getTechList()).replace("android.nfc.tech.", "");
            throw new ReadException("Das ist kein MIFARE-Classic-Tag, oder dein Handy unterstützt "
                    + "MIFARE Classic nicht (z. B. viele Pixel- und einige Samsung-Modelle).\n\nErkannt: " + techs);
        }
        byte[][] blocks = new byte[BambuTag.BLOCKS][];
        try {
            byte[][] keys = BambuTag.deriveKeys(tag.getId());
            mfc.connect();
            mfc.setTimeout(1500);
            int sectors = Math.min(16, mfc.getSectorCount());
            for (int s = 0; s < sectors; s++) {
                if (!mfc.authenticateSectorWithKeyA(s, keys[s])) {
                    if (s == 0) throw new ReadException("Authentifizierung fehlgeschlagen – "
                            + "das ist vermutlich kein Original-Bambu-Lab-Tag.");
                    continue; // Signatur-Sektoren sind optional
                }
                int first = mfc.sectorToBlock(s);
                for (int b = 0; b < mfc.getBlockCountInSector(s); b++) {
                    blocks[first + b] = mfc.readBlock(first + b);
                }
            }
        } catch (ReadException e) {
            throw e;
        } catch (TagLostException e) {
            throw new ReadException("Verbindung zum Tag verloren – halte die Spule ruhig, bis der Scan fertig ist.");
        } catch (IOException e) {
            throw new ReadException("Lesefehler: " + e.getMessage() + "\nBitte erneut versuchen.");
        } catch (Exception e) {
            throw new ReadException("Fehler: " + e);
        } finally {
            try { mfc.close(); } catch (IOException ignored) { }
        }
        BambuTag t = new BambuTag(blocks, System.currentTimeMillis());
        if (!t.looksValid()) throw new ReadException("Tag gelesen, enthält aber keine Filament-Daten.");
        return t;
    }
}
