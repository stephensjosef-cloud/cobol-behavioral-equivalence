# Behavioral-Equivalence Findings — ACCTPROC / ACCTREC_A2

**Analyst:** Josef Stephens — Legacy Bridge
**Date:** 2026-06-10
**Source artifacts:** `ACCTREC_A2.cpy`, `ACCTPROC.cbl`
**Migration target:** Java (`AcctProc.java`, `Comp3.java`, `RecordParser.java`), translated via Claude Code
**Checklist applied:** Legacy Bridge consolidated validation checklist (COMP-3 precision, copybook completeness, REDEFINES semantics)

> **Self-review caveat:** the Java under review was generated in the same
> session that reviewed it. Independent signal requires a second translator.
> Verdicts below describe what *this* translation did; the `editBalance`
> formatting divergence (Finding 6) was fixed after review and re-verified.

> Deliverable type: *referenceable behavioral equivalence* — a documented,
> defensible statement of where the migrated system preserves, and where it
> diverges from, the original's business behavior. Findings below report what
> the validation pass actually found. "Verified correct" is a finding.

---

## System overview
ACCTPROC is a monthly batch job that reads an account-master file
(`ACCTREC.DAT`), and for each **deposit** account (type code `'D'`) accrues one
month of interest: monthly rate = APR ÷ 12, interest = balance × monthly rate,
new balance written back to `CURR-BAL`. Loan and non-deposit accounts pass
through unchanged. Accounts with a negative balance are counted as overdrawn.
It emits one audit line per account plus a totals line to `ACCTRPT.DAT`. The
migration-critical behaviors are COMP-3 packed-decimal precision, IBM
round-half-away-from-zero on `COMPUTE … ROUNDED`, and the `ACCT-DEPOSIT`
discriminator gating accrual.

---

## Methodology
- Ran archaeology pass (Prompt 01) on copybook + program.
- Had Claude Code translate `ACCTPROC.cbl` to Java.
- Ran validation pass (Prompt 02) on the Java output.
- Cross-checked each item against the consolidated checklist trifecta:
  COMP-3 precision · copybook completeness · REDEFINES semantics.

---

## Findings

> One block per finding. Severity scale: REGULATORY > PRODUCTION FAILURE >
> DATA QUALITY > PERFORMANCE. Don't pre-decide the verdict — record what the
> translation actually did.

### Finding 1 — CURR-BAL / COMP-3 precision
- **COBOL location:** `ACCTREC_A2.cpy:17`, CURR-BAL PIC S9(9)V99 COMP-3
- **What the AI produced:** `BigDecimal currBal`, decoded at scale 2
  (`Comp3.decode(b, p, 6, 2)` in `RecordParser.java:26`; arithmetic in
  `AcctProc.java` 2100). No `double`/`float`.
- **Correct behavior:** BigDecimal, scale 2
- **Verdict:** ☑ verified correct ☐ divergence
- **Business impact:** None — monetary precision preserved through accrual.
- **Severity:** n/a (verified correct)

### Finding 2 — APR-RATE scale (V9(5))
- **COBOL location:** `ACCTREC_A2.cpy:18`, APR-RATE PIC S9(2)V9(5) COMP-3
- **What the AI produced:** `BigDecimal aprRate` decoded at scale 5
  (`Comp3.decode(b, p, 4, 5)`, `RecordParser.java:27`). Monthly rate carried
  at scale 7 (`S9(2)V9(7)`), matching `WS-MONTHLY-RATE`.
- **Correct behavior:** BigDecimal, scale 5 (NOT scale 2)
- **Verdict:** ☑ verified correct ☐ divergence
- **Business impact:** None — rate precision preserved; no scale truncation
  that would skew accrued interest.
- **Severity:** n/a (verified correct)

### Finding 3 — Rounding mode on COMPUTE ROUNDED
- **COBOL location:** `ACCTPROC.cbl:78` and `:80`, para 2100-ACCRUE-INTEREST
- **What the AI produced:** Both `COMPUTE … ROUNDED` use
  `RoundingMode.HALF_UP` (`AcctProc.java` 2100). The un-ROUNDED third COMPUTE
  (`WS-NEW-BAL`, `:82`) uses `RoundingMode.UNNECESSARY` (exact scale-2 sum), so
  it does not round where COBOL does not. Multiply uses the already-rounded
  scale-7 monthly rate, preserving COBOL's two-step rounding.
- **Correct behavior:** RoundingMode.HALF_UP (round half away from zero) —
  the IBM Enterprise COBOL default. NOT HALF_EVEN.
- **Verdict:** ☑ verified correct ☐ divergence
- **Business impact:** None — interest pennies match the legacy job. This was
  the primary checkpoint; HALF_UP held across both COMPUTEs (no silent drop to
  banker's rounding).
- **Severity:** n/a (verified correct)

### Finding 4 — OVERDRAFT-DAYS binary COMP (contrast case)
- **COBOL location:** `ACCTREC_A2.cpy:19`, OVERDRAFT-DAYS PIC S9(4) COMP
- **What the AI produced:** `int overdraftDays` (`AcctProc.java:34`) — correctly
  an int, NOT widened to BigDecimal. **Minor:** the parser decodes it as an
  *unsigned* 16-bit halfword (`RecordParser.java:28`); PIC is signed `S9(4)`, so
  a negative stored value would mis-decode. Neither program references the field,
  so impact is latent only.
- **Correct behavior:** int/long — should NOT be widened to BigDecimal
- **Verdict:** ☑ verified correct ☐ divergence (with latent sign-decode note)
- **Business impact:** None today (field unused). Latent: incorrect value if the
  field is ever read and holds a negative count. Fix: sign-extend (`(short)`).
- **Severity:** DATA QUALITY (latent only)

### Finding 5 — REDEFINES (DEPOSIT-DETAIL / LOAN-DETAIL)
- **COBOL location:** `ACCTREC_A2.cpy:25-34`, ACCT-DETAIL X(18) + two REDEFINES
- **What the AI produced:** A single shared `byte[18] acctDetail`; deposit and
  loan views decode from the **same** buffer (`AcctProc.java` accessors
  `depInterestYtd()/depTierCode()` and `loanOrigAmt()/loanTermMonths()`), gated
  by `acctTypeCode`. The date REDEFINES likewise slices one `lastActivityDate`
  string. True discriminated union — NOT independent parallel fields.
- **Correct behavior:** shared-memory semantics gated by ACCT-TYPE-CODE
  (discriminated union / sealed type) — NOT two independent field sets
- **Verdict:** ☑ verified correct ☐ divergence
- **Business impact:** None — overlay semantics preserved; a record cannot be
  read as both arms simultaneously.
- **Severity:** n/a (verified correct)

### Finding 6 — MOVE CURR-BAL → WS-BAL-ED edited display (PIC -(9)9.99)
- **COBOL location:** `ACCTPROC.cbl:87` (MOVE) / `:41` (WS-BAL-ED PIC -(9)9.99)
- **What the AI produced (initial):** `editBalance()` returned
  `sign + abs().toPlainString()` — correct 2-decimal value but **variable width,
  left-aligned, no floating sign placement** (e.g. `"-12.30"` instead of a
  13-char right-justified field). Monetary precision preserved; field *format*
  diverged from `PIC -(9)9.99`.
- **Correct behavior:** Fixed **13-character** field: 10 integer positions with
  leading-zero suppression, a floating minus immediately left of the most
  significant digit (space when positive), forced units digit, 2 decimals.
- **Verdict:** ☐ verified correct ☑ divergence (**fixed + re-verified**)
- **Fix applied:** `editBalance()` rewritten to right-justify a floating-sign
  field to width 13. Verified across `1234.56`, `-12.30`, `-1234567.89`,
  `0.00`, `0.50`, `987654321.99` — all emit 13-char output with correct sign
  placement; full translation recompiles clean.
- **Business impact (pre-fix):** `ACCTRPT.DAT` column alignment drifts and the
  `BAL=` field is no longer fixed-width — any downstream fixed-column parser or
  byte-for-byte report diff against the legacy output would mismatch despite the
  value being numerically correct.
- **Severity:** DATA QUALITY (report fidelity) — resolved.

### Finding 7 — (open risk) Record encoding / on-disk layout unverified
- **COBOL location:** `ACCTPROC.cbl:20` (ORGANIZATION LINE SEQUENTIAL) vs.
  COMP-3/COMP binary fields in the record.
- **What the AI produced:** `RecordParser` assumes a fixed-width byte image with
  standard IBM packed/binary layout, contiguous offsets, no SYNC slack bytes.
- **Correct behavior:** Cannot be determined from source. The true encoding
  (ASCII text vs. EBCDIC vs. binary image, COMP-3 sign nibble convention, any
  alignment slack) must be pinned against a real data sample.
- **Verdict:** ☐ verified correct ☑ divergence — **unresolved / blocking**
- **Business impact:** If the assumed encoding is wrong, every COMP-3/COMP field
  decodes to garbage and all field offsets after OVERDRAFT-DAYS shift. This
  dominates all other findings and is the gating item before any cutover.
- **Severity:** PRODUCTION FAILURE (until validated against real data)

---

## Summary
**4 verified correct** (Findings 1, 2, 3, 5) — the classic AI-translation traps
all came up clean: BigDecimal not float, int not BigDecimal, HALF_UP not
HALF_EVEN, and a true REDEFINES discriminated union rather than parallel fields.
**2 divergences** in the arithmetic/format layer were found: the edited-display
field (Finding 6, **fixed and re-verified**) and a latent unsigned-decode of the
unused OVERDRAFT-DAYS (Finding 4, latent only). **1 blocking open risk**
(Finding 7): the record's physical encoding is unverified and contradicts the
`LINE SEQUENTIAL` declaration.

**Overall equivalence:** the *business logic and decimal arithmetic* are
behaviorally equivalent to ACCTPROC; the *report format* is now equivalent after
the Finding 6 fix. **Not cutover-ready** until the record encoding (Finding 7) is
validated against a real `ACCTREC.DAT` sample — that test gates trust in every
COMP-3/COMP field. One honest line: *the math holds; the bytes are unproven.*
