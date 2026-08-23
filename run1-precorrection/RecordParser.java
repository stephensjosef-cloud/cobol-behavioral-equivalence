import java.nio.charset.StandardCharsets;

/**
 * Parses one ACCOUNT-RECORD (copybook ACCTREC_A2) from a raw line/byte image.
 *
 * NOTE: ACCTPROC.cbl declares the file as ORGANIZATION LINE SEQUENTIAL, yet the
 * record carries COMP-3/COMP binary fields. The true on-disk encoding cannot be
 * settled from source alone; this parser assumes a fixed-width byte image with
 * the standard IBM packed/binary layout. Treat as a placeholder until validated
 * against a real data sample.
 */
public final class RecordParser {

    private RecordParser() {}

    public static AcctProc.AccountRecord parse(String line) {
        byte[] b = line.getBytes(StandardCharsets.ISO_8859_1);
        AcctProc.AccountRecord r = new AcctProc.AccountRecord();
        int p = 0;
        r.acctNumber      = ascii(b, p, 10);                 p += 10;
        r.acctHolderName  = ascii(b, p, 30);                 p += 30;
        r.acctTypeCode    = (char) b[p];                     p += 1;
        r.currBal         = Comp3.decode(b, p, 6, 2);        p += 6; // S9(9)V99
        r.aprRate         = Comp3.decode(b, p, 4, 5);        p += 4; // S9(2)V9(5)
        r.overdraftDays   = ((b[p] & 0xFF) << 8) | (b[p + 1] & 0xFF); p += 2; // COMP halfword
        r.lastActivityDate = ascii(b, p, 8);                 p += 8; // 9(08)
        System.arraycopy(b, p, r.acctDetail, 0, 18);         p += 18;
        return r;
    }

    private static String ascii(byte[] b, int off, int len) {
        return new String(b, off, len, StandardCharsets.US_ASCII);
    }
}
