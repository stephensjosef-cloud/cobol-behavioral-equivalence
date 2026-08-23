package legacybridge.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * COBOL numeric-edited MOVE targets.
 *
 * <p>Editing a value into a PIC clause is a formatting operation with exact,
 * fixed output width -- the report layout depends on the width being constant,
 * so this is not interchangeable with {@code String.format}.
 */
public final class PictureEditor {

    private PictureEditor() {
    }

    /**
     * MOVE ... TO PIC -(9)9.99 -- 13 characters.
     *
     * <p>Ten integer positions followed by {@code .99}. The first nine integer
     * positions are zero-suppressed and carry the floating minus sign, which
     * lands immediately left of the first significant digit; the tenth position
     * always prints a digit, so zero edits to {@code "         0.00"}. A
     * positive value prints a space where the sign would be.
     *
     * <p>Digits beyond the ten integer positions are truncated on the left,
     * matching COBOL MOVE to a smaller receiving item.
     */
    public static String editSigned9v99(BigDecimal value) {
        final int intPositions = 10;
        final int width = 13;

        BigDecimal scaled = value.setScale(2, RoundingMode.DOWN);
        boolean negative = scaled.signum() < 0;

        String digits = scaled.abs().unscaledValue().toString();
        while (digits.length() < 3) {
            digits = "0" + digits; // ensure at least 0.00
        }
        String intPart = digits.substring(0, digits.length() - 2);
        String decPart = digits.substring(digits.length() - 2);

        if (intPart.length() > intPositions) {
            intPart = intPart.substring(intPart.length() - intPositions);
        }
        StringBuilder ip = new StringBuilder();
        for (int i = intPart.length(); i < intPositions; i++) {
            ip.append('0');
        }
        ip.append(intPart);

        char[] out = new char[intPositions];
        int firstSignificant = intPositions - 1; // last position never suppressed
        for (int i = 0; i < intPositions - 1; i++) {
            if (ip.charAt(i) != '0') {
                firstSignificant = i;
                break;
            }
        }
        for (int i = 0; i < intPositions; i++) {
            out[i] = (i < firstSignificant) ? ' ' : ip.charAt(i);
        }
        if (negative) {
            out[firstSignificant - 1 < 0 ? 0 : firstSignificant - 1] = '-';
        }

        String result = new String(out) + "." + decPart;
        assert result.length() == width;
        return result;
    }

    /** MOVE ... TO PIC 9(n) -- zero-filled unsigned display, n characters. */
    public static String editUnsigned(long value, int digits) {
        String s = Long.toString(Math.abs(value));
        if (s.length() > digits) {
            s = s.substring(s.length() - digits);
        }
        StringBuilder sb = new StringBuilder(digits);
        for (int i = s.length(); i < digits; i++) {
            sb.append('0');
        }
        return sb.append(s).toString();
    }
}
