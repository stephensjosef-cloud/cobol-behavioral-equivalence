import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Minimal COMP-3 (packed decimal) codec. Two BCD digits per byte; the
 * low nibble of the last byte is the sign (C/F = +, D = -).
 */
public final class Comp3 {

    private Comp3() {}

    /** Decode {@code len} packed bytes at {@code off} into a BigDecimal of the given scale. */
    public static BigDecimal decode(byte[] buf, int off, int len, int scale) {
        StringBuilder digits = new StringBuilder(len * 2);
        int sign = 1;
        for (int i = 0; i < len; i++) {
            int b = buf[off + i] & 0xFF;
            int hi = (b >> 4) & 0x0F;
            int lo = b & 0x0F;
            digits.append((char) ('0' + hi));
            if (i == len - 1) {
                if (lo == 0x0D) sign = -1;            // negative
            } else {
                digits.append((char) ('0' + lo));
            }
        }
        BigInteger unscaled = new BigInteger(digits.toString());
        if (sign < 0) unscaled = unscaled.negate();
        return new BigDecimal(unscaled, scale);
    }
}
