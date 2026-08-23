       import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.FileInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

public final class AcctProc {
    private static final Charset ASCII = StandardCharsets.US_ASCII;
    private static final int RECORD_LENGTH = 79;
    private static final BigDecimal TWELVE = new BigDecimal("12");

    public static void main(String[] args) throws IOException {
        String inputFile = "ACCTREC.DAT";
        String outputFile = "ACCTRPT.DAT";
        if (args.length >= 2) {
            inputFile = args[0];
            outputFile = args[1];
        }
        process(inputFile, outputFile);
    }

    public static void process(String inputFile, String outputFile) throws IOException {
        long processed = 0;
        long overdrawn = 0;
        Path outputPath = Paths.get(outputFile);
        try (BufferedInputStream in = new BufferedInputStream(new FileInputStream(inputFile));
             BufferedWriter writer = Files.newBufferedWriter(outputPath, ASCII,
                     StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            byte[] record = new byte[RECORD_LENGTH];
            while (readFull(in, record)) {
                AccountRecord acct = AccountRecord.fromBytes(record);
                processed++;
                if (acct.isDeposit()) {
                    acct.accrueInterest();
                }
                if (acct.currBal.compareTo(BigDecimal.ZERO) < 0) {
                    overdrawn++;
                }
                writer.write(acct.reportLine());
                writer.newLine();
            }
            writer.write(String.format("PROCESSED=%d OVERDRAWN=%d", processed, overdrawn));
            writer.newLine();
        }
    }

    private static boolean readFull(BufferedInputStream in, byte[] buffer) throws IOException {
        int offset = 0;
        int remaining = buffer.length;
        while (remaining > 0) {
            int read = in.read(buffer, offset, remaining);
            if (read < 0) {
                return false;
            }
            offset += read;
            remaining -= read;
        }
        return true;
    }

    private static final class AccountRecord {
        String accountNumber;
        String accountHolderName;
        char accountTypeCode;
        BigDecimal currBal;
        BigDecimal aprRate;
        int overdraftDays;
        String lastActivityDate;
        DepositDetail depositDetail;
        LoanDetail loanDetail;

        static AccountRecord fromBytes(byte[] bytes) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
            AccountRecord record = new AccountRecord();
            record.accountNumber = readAscii(buffer, 10);
            record.accountHolderName = readAscii(buffer, 30);
            record.accountTypeCode = (char) buffer.get();
            record.currBal = unpackComp3(readBytes(buffer, 6), 2);
            record.aprRate = unpackComp3(readBytes(buffer, 4), 5);
            record.overdraftDays = buffer.getShort();
            record.lastActivityDate = readAscii(buffer, 8);
            byte[] detailBytes = readBytes(buffer, 18);
            if (record.isDeposit()) {
                record.depositDetail = DepositDetail.fromBytes(detailBytes);
            } else {
                record.loanDetail = LoanDetail.fromBytes(detailBytes);
            }
            return record;
        }

        boolean isDeposit() {
            return accountTypeCode == 'D';
        }

        void accrueInterest() {
            BigDecimal monthlyRate = aprRate.divide(TWELVE, 7, RoundingMode.HALF_UP);
            BigDecimal interest = currBal.multiply(monthlyRate).setScale(2, RoundingMode.HALF_UP);
            currBal = currBal.add(interest).setScale(2, RoundingMode.HALF_UP);
            if (depositDetail != null) {
                depositDetail.interestYtd = depositDetail.interestYtd.add(interest).setScale(2, RoundingMode.HALF_UP);
            }
        }

        String reportLine() {
            return String.format("%-10s  %s  BAL=%s", accountNumber, String.valueOf(accountTypeCode), formatBalance(currBal));
        }

        private static String formatBalance(BigDecimal value) {
            return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
        }

        private static String readAscii(ByteBuffer buffer, int length) {
            byte[] bytes = new byte[length];
            buffer.get(bytes);
            int end = length;
            while (end > 0 && bytes[end - 1] == ' ') {
                end--;
            }
            return new String(bytes, 0, end, ASCII);
        }

        private static byte[] readBytes(ByteBuffer buffer, int length) {
            byte[] bytes = new byte[length];
            buffer.get(bytes);
            return bytes;
        }

        private static BigDecimal unpackComp3(byte[] bytes, int scale) {
            if (bytes == null || bytes.length == 0) {
                return BigDecimal.ZERO.setScale(scale);
            }
            StringBuilder digits = new StringBuilder(bytes.length * 2);
            boolean negative = false;
            for (int i = 0; i < bytes.length; i++) {
                int b = bytes[i] & 0xFF;
                int high = (b >>> 4) & 0x0F;
                int low = b & 0x0F;
                if (i < bytes.length - 1) {
                    digits.append(high).append(low);
                } else {
                    digits.append(high);
                    if (low == 0xB || low == 0xD) {
                        negative = true;
                    }
                    if (low != 0xD && low != 0xB && low != 0xC && low != 0xF && low != 0xA && low != 0x0) {
                        throw new IllegalArgumentException("Invalid COMP-3 sign nibble: " + low);
                    }
                }
            }
            String number = digits.toString();
            if (scale > 0) {
                int pointPosition = number.length() - scale;
                if (pointPosition <= 0) {
                    number = "0." + "0".repeat(-pointPosition) + number;
                } else {
                    number = number.substring(0, pointPosition) + "." + number.substring(pointPosition);
                }
            }
            BigDecimal result = new BigDecimal(number);
            return negative ? result.negate() : result;
        }

        static final class DepositDetail {
            BigDecimal interestYtd;
            String tierCode;

            static DepositDetail fromBytes(byte[] bytes) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
                DepositDetail detail = new DepositDetail();
                detail.interestYtd = unpackComp3(readBytes(buffer, 5), 2);
                detail.tierCode = readAscii(buffer, 2);
                return detail;
            }
        }

        static final class LoanDetail {
            BigDecimal origAmount;
            String termMonths;

            static LoanDetail fromBytes(byte[] bytes) {
                ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
                LoanDetail detail = new LoanDetail();
                detail.origAmount = unpackComp3(readBytes(buffer, 6), 2);
                detail.termMonths = readAscii(buffer, 3);
                return detail;
            }
        }
    }
}
