import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * ACCTPROC - Monthly account interest accrual (batch).
 * Java translation of ACCTPROC.cbl + copybook ACCTREC_A2.
 *
 * Behavior that MUST survive migration (per COBOL header):
 *   - COMP-3 arithmetic precision (CURR-BAL, APR-RATE) -> BigDecimal
 *   - COMPUTE ... ROUNDED == round half away from zero == HALF_UP
 *   - the ACCT-DEPOSIT discriminator gating interest accrual
 */
public final class AcctProc {

    // ---- copybook ACCTREC_A2 : ACCOUNT-RECORD --------------------------
    // ACCT-DETAIL is an 18-byte block reinterpreted by ACCT-TYPE-CODE.
    static final class AccountRecord {
        String acctNumber;          // PIC X(10)
        String acctHolderName;      // PIC X(30)
        char   acctTypeCode;        // PIC X(01) : 'D' deposit, 'L' loan
        BigDecimal currBal;         // PIC S9(9)V99   COMP-3  -> scale 2
        BigDecimal aprRate;         // PIC S9(2)V9(5) COMP-3  -> scale 5
        int        overdraftDays;   // PIC S9(4)      COMP    -> binary int
        String     lastActivityDate;// PIC 9(08)              -> YYYYMMDD

        // ACCT-DETAIL X(18) shared storage (the discriminated union).
        final byte[] acctDetail = new byte[18];

        boolean isDeposit() { return acctTypeCode == 'D'; }
        boolean isLoan()    { return acctTypeCode == 'L'; }

        // REDEFINES of LAST-ACTIVITY-DATE (always-valid reinterpretation)
        int ladYear()  { return Integer.parseInt(lastActivityDate.substring(0, 4)); }
        int ladMonth() { return Integer.parseInt(lastActivityDate.substring(4, 6)); }
        int ladDay()   { return Integer.parseInt(lastActivityDate.substring(6, 8)); }

        // DEPOSIT-DETAIL REDEFINES ACCT-DETAIL  (valid only when isDeposit())
        BigDecimal depInterestYtd() {
            return Comp3.decode(acctDetail, 0, 5, 2); // S9(7)V99 COMP-3
        }
        String depTierCode() {
            return new String(acctDetail, 5, 2, StandardCharsets.US_ASCII);
        }

        // LOAN-DETAIL REDEFINES ACCT-DETAIL     (valid only when isLoan())
        BigDecimal loanOrigAmt() {
            return Comp3.decode(acctDetail, 0, 6, 2); // S9(9)V99 COMP-3
        }
        int loanTermMonths() {
            return Integer.parseInt(new String(acctDetail, 6, 3, StandardCharsets.US_ASCII));
        }
    }

    // ---- WORKING-STORAGE ----------------------------------------------
    private boolean endOfFile = false;
    private int wsProcessed = 0;   // PIC 9(05)
    private int wsOverdrawn = 0;   // PIC 9(05)

    private BufferedReader acctFile;
    private BufferedWriter rptFile;
    private AccountRecord rec;

    // 0000-MAIN
    public void run(Path in, Path out) throws IOException {
        acctFile = Files.newBufferedReader(in, StandardCharsets.US_ASCII);
        rptFile  = Files.newBufferedWriter(out, StandardCharsets.US_ASCII);
        readAcct();                          // priming read
        while (!endOfFile) {                 // PERFORM 2000 UNTIL END-OF-FILE
            processAcct();
        }
        wrapUp();
        acctFile.close();
        rptFile.close();
    }

    // 1000-READ-ACCT
    private void readAcct() throws IOException {
        String line = acctFile.readLine();
        if (line == null) {
            endOfFile = true;
            return;
        }
        rec = RecordParser.parse(line);
    }

    // 2000-PROCESS-ACCT
    private void processAcct() throws IOException {
        wsProcessed++;
        if (rec.isDeposit()) {               // IF ACCT-DEPOSIT
            accrueInterest();
        }
        if (rec.currBal.compareTo(BigDecimal.ZERO) < 0) {
            wsOverdrawn++;
        }
        writeLine();
        readAcct();
    }

    // 2100-ACCRUE-INTEREST
    private void accrueInterest() {
        // COMPUTE WS-MONTHLY-RATE ROUNDED = APR-RATE / 12   (scale 7)
        BigDecimal wsMonthlyRate = rec.aprRate
                .divide(new BigDecimal("12"), 7, RoundingMode.HALF_UP);
        // COMPUTE WS-INTEREST ROUNDED = CURR-BAL * WS-MONTHLY-RATE (scale 2)
        BigDecimal wsInterest = rec.currBal
                .multiply(wsMonthlyRate)
                .setScale(2, RoundingMode.HALF_UP);
        // COMPUTE WS-NEW-BAL = CURR-BAL + WS-INTEREST          (no ROUNDED)
        BigDecimal wsNewBal = rec.currBal.add(wsInterest).setScale(2, RoundingMode.UNNECESSARY);
        rec.currBal = wsNewBal;              // MOVE WS-NEW-BAL TO CURR-BAL
    }

    // 3000-WRITE-LINE
    private void writeLine() throws IOException {
        String balEd = editBalance(rec.currBal);          // MOVE CURR-BAL TO WS-BAL-ED
        String line = rec.acctNumber
                + "  " + rec.acctTypeCode
                + "  BAL=" + balEd;
        rptFile.write(pad(line, 80));
        rptFile.newLine();
    }

    // 9000-WRAP-UP
    private void wrapUp() throws IOException {
        String line = "PROCESSED=" + z5(wsProcessed)
                + "  OVERDRAWN=" + z5(wsOverdrawn);
        rptFile.write(pad(line, 80));
        rptFile.newLine();
    }

    // PIC -(9)9.99 : fixed 13-char edited field. 10 integer positions with
    // leading-zero suppression and a FLOATING minus that sits immediately to
    // the left of the most significant digit (space when positive); the units
    // digit is a fixed 9 (never suppressed); always 2 decimals.
    private static final int ED_INT_WIDTH = 10; // "-(9)9" = 10 char positions

    private static String editBalance(BigDecimal v) {
        BigDecimal scaled = v.setScale(2, RoundingMode.UNNECESSARY);
        String plain = scaled.abs().toPlainString();   // e.g. "1234.56", "0.50"
        int dot = plain.indexOf('.');
        String intPart = plain.substring(0, dot);       // no leading zeros, >=1 digit
        String frac    = plain.substring(dot + 1);      // exactly 2 digits (scale 2)
        String signed  = (scaled.signum() < 0 ? "-" : "") + intPart;
        StringBuilder b = new StringBuilder();
        for (int i = signed.length(); i < ED_INT_WIDTH; i++) b.append(' '); // right-justify
        b.append(signed).append('.').append(frac);
        return b.toString();
    }

    private static String z5(int v) { return String.format("%05d", v); }

    private static String pad(String s, int n) {
        if (s.length() >= n) return s.substring(0, n);
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) b.append(' ');
        return b.toString();
    }

    public static void main(String[] args) throws IOException {
        new AcctProc().run(Path.of("ACCTREC.DAT"), Path.of("ACCTRPT.DAT"));
    }
}
