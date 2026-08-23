package legacybridge.demo;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * ACCTPROC -- Monthly account interest accrual (batch).
 *
 * <p>Java translation of the COBOL program of the same name. Reads
 * ACCOUNT-RECORD, accrues one month of interest on deposit accounts, flags
 * overdrawn accounts, writes an audit line per record plus a totals line.
 *
 * <p>The behaviour carried over from COBOL, unchanged:
 * <ul>
 *   <li>fixed 79-byte binary record layout, read as bytes (see
 *       {@link AccountRecord}) -- the input is NOT a text file, because COMP-3
 *       data can contain a 0x0A byte;
 *   <li>COMP-3 arithmetic precision for CURR-BAL and APR-RATE, carried in
 *       {@link BigDecimal} at the copybook's scales;
 *   <li>ROUNDED = {@link RoundingMode#HALF_UP} (round half away from zero, the
 *       IBM Enterprise COBOL default) -- NOT banker's rounding;
 *   <li>the ACCT-DEPOSIT discriminator gating interest accrual.
 * </ul>
 *
 * <p>The report file stays line-oriented text, as it was LINE SEQUENTIAL.
 */
public final class AcctProc {

    private static final String ACCT_FILE_NAME = "ACCTREC.DAT";
    private static final String RPT_FILE_NAME = "ACCTRPT.DAT";

    /** Width of RPT-LINE PIC X(80). */
    private static final int RPT_LINE_LENGTH = 80;

    // ----- WORKING-STORAGE -------------------------------------------------

    /** WS-MONTHLY-RATE PIC S9(2)V9(7) COMP-3. */
    private static final int SCALE_MONTHLY_RATE = 7;

    /** WS-INTEREST / WS-NEW-BAL PIC S9(9)V99 COMP-3. */
    private static final int SCALE_MONEY = 2;

    /** WS-EOF / 88 END-OF-FILE. */
    private boolean endOfFile;

    /** WS-PROCESSED PIC 9(05). */
    private long wsProcessed;

    /** WS-OVERDRAWN PIC 9(05). */
    private long wsOverdrawn;

    /** The FD record area for ACCT-FILE -- one instance, reused per READ. */
    private AccountRecord acctRecord;

    private final InputStream acctFile;
    private final BufferedWriter rptFile;

    private AcctProc(InputStream acctFile, BufferedWriter rptFile) {
        this.acctFile = acctFile;
        this.rptFile = rptFile;
    }

    // ----- 0000-MAIN -------------------------------------------------------

    public static void main(String[] args) throws IOException {
        Path acctPath = Paths.get(args.length > 0 ? args[0] : ACCT_FILE_NAME);
        Path rptPath = Paths.get(args.length > 1 ? args[1] : RPT_FILE_NAME);

        // OPEN INPUT ACCT-FILE / OUTPUT RPT-FILE ... CLOSE ACCT-FILE RPT-FILE
        try (InputStream in = new BufferedInputStream(Files.newInputStream(acctPath));
             BufferedWriter out = Files.newBufferedWriter(rptPath, CobolCodec.ENCODING)) {

            AcctProc program = new AcctProc(in, out);
            program.readAcct();                 // PERFORM 1000-READ-ACCT
            while (!program.endOfFile) {        // PERFORM 2000-PROCESS-ACCT
                program.processAcct();          //     UNTIL END-OF-FILE
            }
            program.wrapUp();                   // PERFORM 9000-WRAP-UP
        }
        // STOP RUN
    }

    // ----- 1000-READ-ACCT --------------------------------------------------

    private void readAcct() throws IOException {
        byte[] buf = new byte[AccountRecord.RECORD_LENGTH];
        int n = acctFile.readNBytes(buf, 0, buf.length);
        if (n == 0) {
            endOfFile = true;                   // AT END SET END-OF-FILE TO TRUE
            acctRecord = null;
            return;
        }
        if (n < buf.length) {
            // A partial record is a malformed file, not end-of-file. COBOL would
            // raise a file status 04; surfacing it beats silently dropping data.
            throw new EOFException("Short ACCOUNT-RECORD: expected "
                    + AccountRecord.RECORD_LENGTH + " bytes, got " + n);
        }
        acctRecord = new AccountRecord(buf);
    }

    // ----- 2000-PROCESS-ACCT -----------------------------------------------

    private void processAcct() throws IOException {
        wsProcessed++;                                  // ADD 1 TO WS-PROCESSED

        if (acctRecord.isAcctDeposit()) {               // IF ACCT-DEPOSIT
            accrueInterest();                           //   PERFORM 2100
        }
        if (acctRecord.currBal().signum() < 0) {        // IF CURR-BAL < ZERO
            wsOverdrawn++;                              //   ADD 1 TO WS-OVERDRAWN
        }
        writeLine();                                    // PERFORM 3000-WRITE-LINE
        readAcct();                                     // PERFORM 1000-READ-ACCT
    }

    // ----- 2100-ACCRUE-INTEREST --------------------------------------------

    /**
     * Monthly rate = annual APR / 12; interest = balance * rate.
     *
     * <p>COBOL ROUNDED here is round half AWAY FROM ZERO, so both COMPUTE
     * ... ROUNDED statements use HALF_UP. The intermediate rate is rounded to
     * its own 7 decimal places BEFORE it is multiplied by the balance -- doing
     * the division and multiplication in one unrounded expression would drift
     * by a cent on some records.
     */
    private void accrueInterest() {
        // COMPUTE WS-MONTHLY-RATE ROUNDED = APR-RATE / 12
        BigDecimal wsMonthlyRate = acctRecord.aprRate()
                .divide(BigDecimal.valueOf(12), SCALE_MONTHLY_RATE, RoundingMode.HALF_UP);

        // COMPUTE WS-INTEREST ROUNDED = CURR-BAL * WS-MONTHLY-RATE
        BigDecimal wsInterest = acctRecord.currBal()
                .multiply(wsMonthlyRate)
                .setScale(SCALE_MONEY, RoundingMode.HALF_UP);

        // COMPUTE WS-NEW-BAL = CURR-BAL + WS-INTEREST  (both already scale 2,
        // so the addition is exact and no rounding applies)
        BigDecimal wsNewBal = acctRecord.currBal().add(wsInterest);

        // MOVE WS-NEW-BAL TO CURR-BAL
        acctRecord.setCurrBal(wsNewBal);
    }

    // ----- 3000-WRITE-LINE -------------------------------------------------

    private void writeLine() throws IOException {
        // MOVE CURR-BAL TO WS-BAL-ED
        String wsBalEd = PictureEditor.editSigned9v99(acctRecord.currBal());

        // MOVE SPACES TO RPT-LINE / STRING ... INTO RPT-LINE
        StringBuilder rptLine = new StringBuilder();
        rptLine.append(acctRecord.acctNumber())     // DELIMITED BY SIZE: all 10
               .append("  ")
               .append(acctRecord.acctTypeCode())
               .append("  BAL=")
               .append(wsBalEd);

        writeRptLine(rptLine.toString());           // WRITE RPT-LINE
    }

    // ----- 9000-WRAP-UP ----------------------------------------------------

    private void wrapUp() throws IOException {
        StringBuilder rptLine = new StringBuilder();
        rptLine.append("PROCESSED=")
               .append(PictureEditor.editUnsigned(wsProcessed, 5))
               .append("  OVERDRAWN=")
               .append(PictureEditor.editUnsigned(wsOverdrawn, 5));

        writeRptLine(rptLine.toString());
    }

    /**
     * WRITE to a LINE SEQUENTIAL file: the record is padded to its PIC X(80)
     * width, then trailing spaces are stripped on output -- the same as the
     * COBOL runtime does.
     */
    private void writeRptLine(String content) throws IOException {
        String line = content.length() > RPT_LINE_LENGTH
                ? content.substring(0, RPT_LINE_LENGTH)
                : content;
        int end = line.length();
        while (end > 0 && line.charAt(end - 1) == ' ') {
            end--;
        }
        rptFile.write(line, 0, end);
        rptFile.newLine();
    }
}
