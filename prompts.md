# Validation Prompts — demo run

Pulled out of the roadmap so you're not digging through the HTML mid-run.
Run them in order against the files in this folder. These are your IP —
refine them every engagement.

---

## PROMPT 01 — Initial Archaeology
*Use first. Point Claude Code at ACCTREC_A2.cpy and ACCTPROC.cbl.*

```
Analyze this COBOL program and its copybook comprehensively. For each:

1. Identify the PROGRAM-ID and the copybook's record name.
2. List every WORKING-STORAGE / record field with PIC COMP-3 or COMP
   (packed or binary), with its PIC clause and implied scale.
3. Map every PERFORM and any CALL to external programs.
4. Identify every COPY statement and the copybook it references.
5. List every REDEFINES clause and the field it overlays. State the
   discriminator that decides which interpretation is valid.
6. Describe the business purpose of the program in plain English.
7. Identify any date fields and how they're stored.

Output one section per file. Be explicit about what you CANNOT determine
from static analysis alone.
```

---

## PROMPT 02 — Validation Checklist
*Use after you have Claude Code translate ACCTPROC.cbl to Java. Point it at the Java.*

```
Review this AI-generated Java translated from the COBOL above.
Check specifically:

1. PACKED DECIMALS: Every COMP-3 field should be BigDecimal. Flag any
   double/float. Confirm scale matches the V in the PIC (V99 -> scale 2,
   V9(5) -> scale 5).
2. BINARY COMP: OVERDRAFT-DAYS is COMP (binary), not COMP-3 -> it should
   be int/long, NOT BigDecimal. Flag if it was widened to BigDecimal.
3. ROUNDING: COMPUTE ... ROUNDED in IBM COBOL = round half AWAY FROM ZERO.
   The Java must use RoundingMode.HALF_UP, NOT HALF_EVEN. Flag a mismatch.
4. REDEFINES: DEPOSIT-DETAIL / LOAN-DETAIL overlay the same 18 bytes,
   gated by ACCT-TYPE-CODE. Did the Java preserve shared-memory semantics
   (one discriminated type), or did it create independent parallel fields?
5. DISCRIMINATOR LOGIC: Is interest accrual still gated on ACCT-DEPOSIT?
6. MOVE precision: CURR-BAL (COMP-3) moved to an edited display field —
   is precision preserved in the Java equivalent?

For each issue: (a) COBOL location, (b) what the AI produced, (c) what it
should produce, (d) business impact.
```

---

## PROMPT 04 — Client Report Generator
*Use last, only after findings-report.md is filled with your own findings.*

```
Based on the validation findings in findings-report.md, generate a
professional, non-alarmist modernization assessment summary for a
business reader (CTO / VP Eng). Structure:

1. Executive summary (3 short paragraphs, non-technical).
2. What this system does, in business terms.
3. Behavioral-equivalence findings, ranked by severity.
4. Recommended remediation order (blocking vs deferrable).
5. What still needs human sign-off before production cutover.
```
