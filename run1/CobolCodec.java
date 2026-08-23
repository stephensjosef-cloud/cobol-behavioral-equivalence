package legacybridge.demo;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;

/**
 * Primitive codecs for the COBOL storage types used by ACCTREC_A2.
 *
 * <p>These mirror the on-disk representation exactly; the record is a fixed
 * 79-byte binary image, NOT character text, so every field is addressed by
 * byte offset and decoded according to its USAGE clause.
 */
public final class CobolCodec {

    /**
     * Charset of the data file. EBCDIC (e.g. "IBM037") if the file came off the
     * mainframe untranslated; ASCII/ISO-8859-1 if it was converted on transfer.
     * Only DISPLAY and alphanumeric fields are affected -- COMP-3 and COMP are
     * charset independent.
     */
    public static final Charset ENCODING = Charset.forName("ISO-8859-1");

    private CobolCodec() {
    }

    // ---------------------------------------------------------------- PIC X

    /** PIC X(n) -- fixed-width alphanumeric, space padded on the right. */
    public static String getAlphanumeric(byte[] rec, int offset, int length) {
        return new String(rec, offset, length, ENCODING);
    }

    /** PIC X(n) -- writes exactly {@code length} bytes, space padded/truncated. */
    public static void putAlphanumeric(byte[] rec, int offset, int length, String value) {
        byte[] src = (value == null ? "" : value).getBytes(ENCODING);
        int n = Math.min(src.length, length);
        System.arraycopy(src, 0, rec, offset, n);
        java.util.Arrays.fill(rec, offset + n, offset + length, (byte) ' ');
    }

    // ---------------------------------------------------------- PIC 9 DISPLAY

    /** PIC 9(n) unsigned DISPLAY -- one character digit per byte. */
    public static long getUnsignedDisplay(byte[] rec, int offset, int length) {
        String s = getAlphanumeric(rec, offset, length).trim();
        if (s.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(s);
    }

    /** PIC 9(n) unsigned DISPLAY -- zero filled on the left. */
    public static void putUnsignedDisplay(byte[] rec, int offset, int length, long value) {
        String s = Long.toString(Math.abs(value));
        if (s.length() > length) {
            s = s.substring(s.length() - length); // COBOL truncates high-order digits
        }
        StringBuilder sb = new StringBuilder(length);
        for (int i = s.length(); i < length; i++) {
            sb.append('0');
        }
        sb.append(s);
        putAlphanumeric(rec, offset, length, sb.toString());
    }

    // ---------------------------------------------------------------- COMP

    /**
     * PIC S9(1-4) COMP -- 2-byte big-endian two's complement binary halfword.
     *
     * <p>Stays an {@code int}. This field must NOT become a BigDecimal: it is a
     * whole-number binary counter, not a scaled decimal quantity.
     */
    public static int getCompHalfword(byte[] rec, int offset) {
        return (short) (((rec[offset] & 0xFF) << 8) | (rec[offset + 1] & 0xFF));
    }

    public static void putCompHalfword(byte[] rec, int offset, int value) {
        rec[offset] = (byte) (value >> 8);
        rec[offset + 1] = (byte) value;
    }

    // --------------------------------------------------------------- COMP-3

    /** Byte length of a COMP-3 field holding {@code digits} total digits. */
    public static int packedLength(int digits) {
        return (digits / 2) + 1;
    }

    /**
     * Signed packed decimal (COMP-3). Two digits per byte, the low nibble of the
     * final byte carrying the sign: 0xD negative, 0xC/0xF positive.
     *
     * @param scale digits to the right of the implied decimal point (the V)
     */
    public static BigDecimal getPacked(byte[] rec, int offset, int length, int scale) {
        StringBuilder digits = new StringBuilder(length * 2);
        for (int i = 0; i < length; i++) {
            int b = rec[offset + i] & 0xFF;
            digits.append((char) ('0' + (b >>> 4)));
            if (i < length - 1) {
                digits.append((char) ('0' + (b & 0x0F)));
            }
        }
        int sign = rec[offset + length - 1] & 0x0F;
        BigInteger unscaled = new BigInteger(digits.toString());
        if (sign == 0x0D || sign == 0x0B) {
            unscaled = unscaled.negate();
        }
        return new BigDecimal(unscaled, scale);
    }

    /**
     * Writes a signed packed decimal field. The value is rescaled to
     * {@code scale} (truncating, as a COBOL MOVE does) and high-order digits
     * beyond the field capacity are dropped, again matching MOVE semantics.
     */
    public static void putPacked(byte[] rec, int offset, int length, int scale, BigDecimal value) {
        BigDecimal scaled = value.setScale(scale, java.math.RoundingMode.DOWN);
        boolean negative = scaled.signum() < 0;
        String digits = scaled.abs().unscaledValue().toString();

        int capacity = length * 2 - 1;
        if (digits.length() > capacity) {
            digits = digits.substring(digits.length() - capacity);
        }
        StringBuilder sb = new StringBuilder(capacity);
        for (int i = digits.length(); i < capacity; i++) {
            sb.append('0');
        }
        sb.append(digits);
        String d = sb.toString();

        int p = 0;
        for (int i = 0; i < length - 1; i++) {
            rec[offset + i] = (byte) (((d.charAt(p) - '0') << 4) | (d.charAt(p + 1) - '0'));
            p += 2;
        }
        rec[offset + length - 1] =
                (byte) (((d.charAt(p) - '0') << 4) | (negative ? 0x0D : 0x0C));
    }
}
