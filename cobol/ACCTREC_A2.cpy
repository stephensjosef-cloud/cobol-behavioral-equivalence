      *================================================================
      * ACCTREC_A2 - ACCOUNT MASTER RECORD
      * Legacy Bridge demo copybook (synthetic - no client data)
      *
      * Deliberately exercises the validation trifecta:
      *   - COMP-3 packed decimal at TWO different scales
      *   - binary COMP for contrast (must NOT become BigDecimal)
      *   - REDEFINES used as a discriminated union (ACCT-TYPE-CODE)
      *   - a date REDEFINES (display number reinterpreted as Y/M/D)
      *================================================================
       01  ACCOUNT-RECORD.
           05  ACCT-NUMBER             PIC X(10).
           05  ACCT-HOLDER-NAME        PIC X(30).
           05  ACCT-TYPE-CODE          PIC X(01).
               88  ACCT-DEPOSIT        VALUE 'D'.
               88  ACCT-LOAN           VALUE 'L'.
           05  CURR-BAL                PIC S9(9)V99   COMP-3.
           05  APR-RATE                PIC S9(2)V9(5) COMP-3.
           05  OVERDRAFT-DAYS          PIC S9(4)      COMP.
           05  LAST-ACTIVITY-DATE      PIC 9(08).
           05  LAST-ACTIVITY-DATE-R REDEFINES LAST-ACTIVITY-DATE.
               10  LAD-YEAR            PIC 9(04).
               10  LAD-MONTH           PIC 9(02).
               10  LAD-DAY             PIC 9(02).
           05  ACCT-DETAIL             PIC X(18).
      *    --- ACCT-DETAIL means different things by ACCT-TYPE-CODE ---
           05  DEPOSIT-DETAIL  REDEFINES ACCT-DETAIL.
               10  DEP-INTEREST-YTD    PIC S9(7)V99   COMP-3.
               10  DEP-TIER-CODE       PIC X(02).
               10  FILLER              PIC X(11).
           05  LOAN-DETAIL     REDEFINES ACCT-DETAIL.
               10  LOAN-ORIG-AMT       PIC S9(9)V99   COMP-3.
               10  LOAN-TERM-MONTHS    PIC 9(03).
               10  FILLER              PIC X(09).
