package legacybridge.demo;

import java.math.BigDecimal;

/**
 * ACCTREC_A2 -- ACCOUNT MASTER RECORD.
 *
 * <p>Translation of the COBOL copybook. The record is a fixed 79-byte binary
 * image; this class is a typed view over that image rather than a copy of it,
 * so REDEFINES can be honoured by reading the same bytes two ways.
 *
 * <pre>
 * off len  field                  picture / usage
 *   0  10  ACCT-NUMBER            PIC X(10)
 *  10  30  ACCT-HOLDER-NAME       PIC X(30)
 *  40   1  ACCT-TYPE-CODE         PIC X(01)          88 D=deposit, L=loan
 *  41   6  CURR-BAL               PIC S9(9)V99 COMP-3
 *  47   4  APR-RATE               PIC S9(2)V9(5) COMP-3
 *  51   2  OVERDRAFT-DAYS         PIC S9(4) COMP
 *  53   8  LAST-ACTIVITY-DATE     PIC 9(08)          redefined as YYYY/MM/DD
 *  61  18  ACCT-DETAIL            PIC X(18)          redefined by type code
 *                                                    = 79 bytes
 * </pre>
 */
public final class AccountRecord {

    public static final int RECORD_LENGTH = 79;

    // ----- field offsets and lengths (the copybook, verbatim) ---------------

    private static final int OFF_ACCT_NUMBER = 0;
    private static final int LEN_ACCT_NUMBER = 10;

    private static final int OFF_HOLDER_NAME = 10;
    private static final int LEN_HOLDER_NAME = 30;

    private static final int OFF_TYPE_CODE = 40;

    /** S9(9)V99 COMP-3 -> 11 digits + sign = 6 bytes, scale 2. */
    private static final int OFF_CURR_BAL = 41;
    private static final int LEN_CURR_BAL = 6;
    private static final int SCALE_CURR_BAL = 2;

    /** S9(2)V9(5) COMP-3 -> 7 digits + sign = 4 bytes, scale 5. */
    private static final int OFF_APR_RATE = 47;
    private static final int LEN_APR_RATE = 4;
    private static final int SCALE_APR_RATE = 5;

    private static final int OFF_OVERDRAFT_DAYS = 51;

    private static final int OFF_LAST_ACTIVITY_DATE = 53;
    private static final int LEN_LAST_ACTIVITY_DATE = 8;

    private static final int OFF_ACCT_DETAIL = 61;
    private static final int LEN_ACCT_DETAIL = 18;

    // ----- DEPOSIT-DETAIL REDEFINES ACCT-DETAIL ----------------------------

    /** S9(7)V99 COMP-3 -> 9 digits + sign = 5 bytes, scale 2. */
    private static final int OFF_DEP_INTEREST_YTD = OFF_ACCT_DETAIL;
    private static final int LEN_DEP_INTEREST_YTD = 5;
    private static final int SCALE_DEP_INTEREST_YTD = 2;

    private static final int OFF_DEP_TIER_CODE = OFF_ACCT_DETAIL + 5;
    private static final int LEN_DEP_TIER_CODE = 2;

    // ----- LOAN-DETAIL REDEFINES ACCT-DETAIL -------------------------------

    /** S9(9)V99 COMP-3 -> 11 digits + sign = 6 bytes, scale 2. */
    private static final int OFF_LOAN_ORIG_AMT = OFF_ACCT_DETAIL;
    private static final int LEN_LOAN_ORIG_AMT = 6;
    private static final int SCALE_LOAN_ORIG_AMT = 2;

    private static final int OFF_LOAN_TERM_MONTHS = OFF_ACCT_DETAIL + 6;
    private static final int LEN_LOAN_TERM_MONTHS = 3;

    // -----------------------------------------------------------------------

    /** The live record image. Mutating a setter mutates these bytes. */
    private final byte[] image;

    public AccountRecord() {
        this.image = new byte[RECORD_LENGTH];
        java.util.Arrays.fill(this.image, (byte) ' ');
    }

    public AccountRecord(byte[] image) {
        if (image.length != RECORD_LENGTH) {
            throw new IllegalArgumentException(
                    "ACCOUNT-RECORD is " + RECORD_LENGTH + " bytes, got " + image.length);
        }
        this.image = image;
    }

    public byte[] image() {
        return image;
    }

    // ----- ACCT-NUMBER / ACCT-HOLDER-NAME ----------------------------------

    /** PIC X(10), space padded exactly as stored (report layout depends on it). */
    public String acctNumber() {
        return CobolCodec.getAlphanumeric(image, OFF_ACCT_NUMBER, LEN_ACCT_NUMBER);
    }

    public String acctHolderName() {
        return CobolCodec.getAlphanumeric(image, OFF_HOLDER_NAME, LEN_HOLDER_NAME);
    }

    // ----- ACCT-TYPE-CODE and its 88-levels --------------------------------

    public char acctTypeCode() {
        return (char) (image[OFF_TYPE_CODE] & 0xFF);
    }

    /** 88 ACCT-DEPOSIT VALUE 'D'. Gates interest accrual -- behavioural checkpoint. */
    public boolean isAcctDeposit() {
        return acctTypeCode() == 'D';
    }

    /** 88 ACCT-LOAN VALUE 'L'. */
    public boolean isAcctLoan() {
        return acctTypeCode() == 'L';
    }

    // ----- CURR-BAL / APR-RATE (COMP-3, two different scales) --------------

    public BigDecimal currBal() {
        return CobolCodec.getPacked(image, OFF_CURR_BAL, LEN_CURR_BAL, SCALE_CURR_BAL);
    }

    public void setCurrBal(BigDecimal value) {
        CobolCodec.putPacked(image, OFF_CURR_BAL, LEN_CURR_BAL, SCALE_CURR_BAL, value);
    }

    public BigDecimal aprRate() {
        return CobolCodec.getPacked(image, OFF_APR_RATE, LEN_APR_RATE, SCALE_APR_RATE);
    }

    public void setAprRate(BigDecimal value) {
        CobolCodec.putPacked(image, OFF_APR_RATE, LEN_APR_RATE, SCALE_APR_RATE, value);
    }

    // ----- OVERDRAFT-DAYS (binary COMP -- stays an int) --------------------

    public int overdraftDays() {
        return CobolCodec.getCompHalfword(image, OFF_OVERDRAFT_DAYS);
    }

    public void setOverdraftDays(int value) {
        CobolCodec.putCompHalfword(image, OFF_OVERDRAFT_DAYS, value);
    }

    // ----- LAST-ACTIVITY-DATE and LAST-ACTIVITY-DATE-R ---------------------

    /** PIC 9(08) read as a whole number, e.g. 20240131. */
    public long lastActivityDate() {
        return CobolCodec.getUnsignedDisplay(
                image, OFF_LAST_ACTIVITY_DATE, LEN_LAST_ACTIVITY_DATE);
    }

    public void setLastActivityDate(long yyyymmdd) {
        CobolCodec.putUnsignedDisplay(
                image, OFF_LAST_ACTIVITY_DATE, LEN_LAST_ACTIVITY_DATE, yyyymmdd);
    }

    /** LAD-YEAR PIC 9(04) -- the REDEFINES view of the same 8 bytes. */
    public int ladYear() {
        return (int) CobolCodec.getUnsignedDisplay(image, OFF_LAST_ACTIVITY_DATE, 4);
    }

    /** LAD-MONTH PIC 9(02). */
    public int ladMonth() {
        return (int) CobolCodec.getUnsignedDisplay(image, OFF_LAST_ACTIVITY_DATE + 4, 2);
    }

    /** LAD-DAY PIC 9(02). */
    public int ladDay() {
        return (int) CobolCodec.getUnsignedDisplay(image, OFF_LAST_ACTIVITY_DATE + 6, 2);
    }

    /**
     * The date as a {@link java.time.LocalDate}, or empty when the field holds a
     * value COBOL would accept as digits but that is not a real calendar date
     * (all zeros, 99999999, day 31 of a 30-day month, ...). COBOL never
     * validated this; anything downstream that needs a date must decide.
     */
    public java.util.Optional<java.time.LocalDate> lastActivityLocalDate() {
        try {
            return java.util.Optional.of(
                    java.time.LocalDate.of(ladYear(), ladMonth(), ladDay()));
        } catch (java.time.DateTimeException e) {
            return java.util.Optional.empty();
        }
    }

    // ----- ACCT-DETAIL and its two REDEFINES views -------------------------

    /** The raw 18-byte detail area, uninterpreted. */
    public String acctDetail() {
        return CobolCodec.getAlphanumeric(image, OFF_ACCT_DETAIL, LEN_ACCT_DETAIL);
    }

    /**
     * DEP-INTEREST-YTD. Only meaningful when {@link #isAcctDeposit()}; on a loan
     * record these bytes are LOAN-ORIG-AMT and this returns garbage.
     */
    public BigDecimal depInterestYtd() {
        return CobolCodec.getPacked(
                image, OFF_DEP_INTEREST_YTD, LEN_DEP_INTEREST_YTD, SCALE_DEP_INTEREST_YTD);
    }

    public void setDepInterestYtd(BigDecimal value) {
        CobolCodec.putPacked(
                image, OFF_DEP_INTEREST_YTD, LEN_DEP_INTEREST_YTD, SCALE_DEP_INTEREST_YTD, value);
    }

    /** DEP-TIER-CODE PIC X(02). Deposit records only. */
    public String depTierCode() {
        return CobolCodec.getAlphanumeric(image, OFF_DEP_TIER_CODE, LEN_DEP_TIER_CODE);
    }

    /** LOAN-ORIG-AMT. Loan records only -- see {@link #isAcctLoan()}. */
    public BigDecimal loanOrigAmt() {
        return CobolCodec.getPacked(
                image, OFF_LOAN_ORIG_AMT, LEN_LOAN_ORIG_AMT, SCALE_LOAN_ORIG_AMT);
    }

    public void setLoanOrigAmt(BigDecimal value) {
        CobolCodec.putPacked(
                image, OFF_LOAN_ORIG_AMT, LEN_LOAN_ORIG_AMT, SCALE_LOAN_ORIG_AMT, value);
    }

    /** LOAN-TERM-MONTHS PIC 9(03). Loan records only. */
    public int loanTermMonths() {
        return (int) CobolCodec.getUnsignedDisplay(
                image, OFF_LOAN_TERM_MONTHS, LEN_LOAN_TERM_MONTHS);
    }

    public void setLoanTermMonths(int value) {
        CobolCodec.putUnsignedDisplay(
                image, OFF_LOAN_TERM_MONTHS, LEN_LOAN_TERM_MONTHS, value);
    }
}
