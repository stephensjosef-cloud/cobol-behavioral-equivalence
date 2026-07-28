# COBOL Behavioral Equivalence — Run 1

Published artifacts from a behavioral equivalence validation pass on an
AI-migrated COBOL batch program. Everything needed to check the work is here:
source, translation, the prompts used, and the findings.

## What this is

AI tools translate COBOL competently. What they don't do is independently
certify that the translated system behaves the way the original did — that
packed-decimal scales survived, that rounding mode held, that a REDEFINES
became a discriminated union rather than parallel fields, that a fixed-width
report field is still fixed-width.

This repo is one such validation, run end to end and published so the method
is inspectable rather than asserted.

## The subject

`ACCTPROC` is a monthly batch job that reads an account-master file and accrues
one month of interest on deposit accounts. Synthetic, but seeded deliberately
with the patterns that break migrations: COMP-3 at two different scales, a
binary COMP field as a contrast case, two REDEFINES of different kinds, and a
numeric-edited output picture.

## Contents

| Path | What it is |
|---|---|
| `cobol/ACCTREC_A2.cpy` | Copybook — the record layout |
| `cobol/ACCTPROC.cbl` | The COBOL program under migration |
| `java/` | The AI-generated Java translation |
| `prompts.md` | The archaeology and validation prompts used |
| `findings-report.md` | The deliverable — findings with severity and business impact |

## What Run 1 found

Seven findings. Four verified correct, two divergences, one blocking open risk.

The four classic traps came up clean: BigDecimal rather than float, int rather
than a widened BigDecimal, HALF_UP rather than HALF_EVEN, and a true
discriminated union rather than parallel field sets.

The divergences were elsewhere. An edited-display field lost its fixed 13-character
width — numerically correct, but any downstream fixed-column parser or byte-for-byte
report diff would mismatch. Fixed and re-verified.

The blocking item is Finding 7: the record's physical encoding is unverifiable
from source and contradicts the file's own `LINE SEQUENTIAL` declaration. If the
assumed layout is wrong, every packed field decodes to garbage. That gates
cutover regardless of how clean the arithmetic looks.

The math holds. The bytes are unproven.

## An honest limitation

The translation reviewed here was generated in the same session that reviewed
it. That's weak independence — a system checking its own output tends to check
for the behavior it already produced.

Run 2 repeats this against a translation produced by an independent tool. Same
checklist, same program, higher signal.

## Contact

Josef Stephens — independent COBOL behavioral equivalence validation.
[LinkedIn](https://www.linkedin.com/in/josef-stephens-aa2aa319a/)