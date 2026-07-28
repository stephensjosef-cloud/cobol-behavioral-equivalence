       IDENTIFICATION DIVISION.
       PROGRAM-ID. ACCTPROC.
      *================================================================
      * ACCTPROC - Monthly account interest accrual (batch)
      * Legacy Bridge demo program (synthetic - no client data)
      *
      * Reads ACCOUNT-RECORD, accrues one month of interest on
      * deposit accounts, flags overdrawn accounts, writes an audit
      * line per record plus a totals line.
      *
      * The behavior that MUST survive migration:
      *   - COMP-3 arithmetic precision (CURR-BAL, APR-RATE)
      *   - rounding on COMPUTE ... ROUNDED  (see note in 2100)
      *   - the ACCT-DEPOSIT discriminator gating interest accrual
      *================================================================
       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCT-FILE  ASSIGN TO 'ACCTREC.DAT'
               ORGANIZATION IS LINE SEQUENTIAL.
           SELECT RPT-FILE   ASSIGN TO 'ACCTRPT.DAT'
               ORGANIZATION IS LINE SEQUENTIAL.

       DATA DIVISION.
       FILE SECTION.
       FD  ACCT-FILE.
       COPY ACCTREC_A2.

       FD  RPT-FILE.
       01  RPT-LINE                   PIC X(80).

       WORKING-STORAGE SECTION.
       01  WS-FLAGS.
           05  WS-EOF                 PIC X(01)  VALUE 'N'.
               88  END-OF-FILE        VALUE 'Y'.
       01  WS-CALC.
           05  WS-MONTHLY-RATE        PIC S9(2)V9(7) COMP-3.
           05  WS-INTEREST            PIC S9(9)V99   COMP-3.
           05  WS-NEW-BAL             PIC S9(9)V99   COMP-3.
       01  WS-EDIT.
           05  WS-BAL-ED              PIC -(9)9.99.
       01  WS-COUNTS.
           05  WS-PROCESSED           PIC 9(05)  VALUE ZERO.
           05  WS-OVERDRAWN           PIC 9(05)  VALUE ZERO.

       PROCEDURE DIVISION.
       0000-MAIN.
           OPEN INPUT  ACCT-FILE
                OUTPUT RPT-FILE
           PERFORM 1000-READ-ACCT
           PERFORM 2000-PROCESS-ACCT
               UNTIL END-OF-FILE
           PERFORM 9000-WRAP-UP
           CLOSE ACCT-FILE RPT-FILE
           STOP RUN.

       1000-READ-ACCT.
           READ ACCT-FILE
               AT END SET END-OF-FILE TO TRUE
           END-READ.

       2000-PROCESS-ACCT.
           ADD 1 TO WS-PROCESSED
           IF ACCT-DEPOSIT
               PERFORM 2100-ACCRUE-INTEREST
           END-IF
           IF CURR-BAL < ZERO
               ADD 1 TO WS-OVERDRAWN
           END-IF
           PERFORM 3000-WRITE-LINE
           PERFORM 1000-READ-ACCT.

       2100-ACCRUE-INTEREST.
      *    Monthly rate = annual APR / 12; interest = balance * rate.
      *    COBOL ROUNDED here = round half AWAY FROM ZERO (the IBM
      *    Enterprise COBOL default), NOT banker's rounding. The Java
      *    must use HALF_UP to match - this is a validation checkpoint.
           COMPUTE WS-MONTHLY-RATE ROUNDED =
               APR-RATE / 12
           COMPUTE WS-INTEREST ROUNDED =
               CURR-BAL * WS-MONTHLY-RATE
           COMPUTE WS-NEW-BAL =
               CURR-BAL + WS-INTEREST
           MOVE WS-NEW-BAL TO CURR-BAL.

       3000-WRITE-LINE.
           MOVE CURR-BAL TO WS-BAL-ED
           MOVE SPACES   TO RPT-LINE
           STRING ACCT-NUMBER      DELIMITED BY SIZE
                  '  '             DELIMITED BY SIZE
                  ACCT-TYPE-CODE   DELIMITED BY SIZE
                  '  BAL='         DELIMITED BY SIZE
                  WS-BAL-ED        DELIMITED BY SIZE
                  INTO RPT-LINE
           END-STRING
           WRITE RPT-LINE.

       9000-WRAP-UP.
           MOVE SPACES TO RPT-LINE
           STRING 'PROCESSED='    DELIMITED BY SIZE
                  WS-PROCESSED    DELIMITED BY SIZE
                  '  OVERDRAWN='  DELIMITED BY SIZE
                  WS-OVERDRAWN    DELIMITED BY SIZE
                  INTO RPT-LINE
           END-STRING
           WRITE RPT-LINE.
