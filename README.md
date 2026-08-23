# COBOL Behavioral Equivalence — Two Independent Translations

Published artifacts from behavioral equivalence validation passes on an
AI-migrated COBOL batch program. Everything needed to check the work is here:
source, three translations, the prompts used verbatim, and the findings.

## What this is

AI tools translate COBOL competently. What they don't do is independently
certify that the translated system behaves the way the original did — that
packed-decimal scales survived, that rounding mode held, that a REDEFINES
became a discriminated union rather than parallel fields, that a fixed-width
report field is still fixed-width.

This repo is that validation, run end to end and published so the method is
inspectable rather than asserted.

## The subject

`ACCTPROC` is a monthly batch job that reads an account-master file and accrues
one month of interest on deposit accounts. Synthetic, but seeded deliberately
with the patterns that break migrations: COMP-3 at two different scales, a
binary COMP field as a contrast case, two REDEFINES of different kinds, and a
numeric-edited output picture.

## Method

Two translations to Java, produced independently by different vendors' models.
Neither run received an archaeology pass, a validation checklist, or any
indication of where the difficult constructs were. The prompt in both cases was
a single line: *translate this COBOL program and its copybook to Java.*

Both outputs were then reviewed against the same checklist (`prompts.md`,
Prompt 02), used unchanged between runs. Run 2 was reviewed before Run 1's
findings were reopened, so the second review was not primed by the first.

One asymmetry worth stating: Run 1 was produced by an agentic tool that
compiled and executed its own output against synthetic records before
returning. Run 2 was a single completion. That is a property of the tools, not
of the experiment, and it accounts for part of what follows.

## Contents

| Path | What it is |
|---|---|
| `cobol/ACCTREC_A2.cpy` | Copybook — the record layout |
| `cobol/ACCTPROC.cbl` | The COBOL program under migration |
| `cobol/ACCTPROC-precorrection.cbl` | The original fixture, declaring `LINE SEQUENTIAL`. What `run1-precorrection/` was generated against. |
| `run1/` | Translation 1, against the corrected fixture |
| `run2/` | Translation 2, independent vendor, against the corrected fixture |
| `run1-precorrection/` | Translation 1, against the original fixture. See below. |
| `prompts.md` | Archaeology and validation prompts, verbatim |
| `findings-report.md` | Run 1 findings, with severity and business impact |
| `findings-diff.md` | The two-run comparison |

No translation in this repo has been edited. Where an output is wrong, that is
the finding, not a defect to repair.

## What both translations got right

This is the part that surprised me, and it is worth stating before the
divergences.

- Fixed 79-byte offset addressing; input read as bytes, never through a
  `Reader`. Offsets sum correctly: 10+30+1+6+4+2+8+18.
- COMP-3 decoded to `BigDecimal` at the copybook's implied scales. No `double`,
  no `float`, anywhere.
- `OVERDRAFT-DAYS` correctly treated as a 2-byte big-endian binary halfword and
  left as an integer, not promoted to `BigDecimal` alongside its packed
  neighbours.
- `ROUNDED` implemented as HALF_UP — half away from zero — not HALF_EVEN.
- The monthly rate rounded to its own 7 decimals *before* the multiply, rather
  than collapsing both COMPUTE statements into one expression. Collapsing them
  drifts by a cent on some records.
- The `ACCT-DEPOSIT` 88-level gate preserved.
- REDEFINES modeled as alternate views over shared bytes rather than as
  separately parsed fields.

The constructs most often named as what breaks AI migration were cleared by
both runs in a single pass, off a one-line prompt.

## Divergences

| Behavior | Run 1 | Run 2 |
|---|---|---|
| `PIC -(9)9.99` edited move | Dedicated picture-editor implementation | `toPlainString()` — variable width, no floating sign |
| `PIC 9(05)` counters | Zero-padded | Unpadded (`PROCESSED=42`), widened to `long` |
| Character encoding | Named as an open decision: ISO-8859-1 vs IBM037 | Hardcoded US-ASCII |
| Unrecognized `ACCT-TYPE-CODE` | No pre-branch; offset views only | Anything other than `D` parsed as a loan record |
| `DEP-INTEREST-YTD` | Untouched, as in source | Accumulated into during accrual |
| Output line endings | Flagged as a byte-comparison decision | Silent |

### The material one

`ACCTPROC` does not read or write `DEP-INTEREST-YTD`. Run 2 added an
accumulation into that field during interest accrual
(`run2/AcctProc.java`, `accrueInterest()`).

This is the most consequential error in either output, and the hardest class to
catch on review. A dropped rule announces itself when output goes missing. An
*added* rule is plausible, reads as correct, and is very likely true of the
wider system somewhere — just not of this program. A reviewer comparing the
Java against a mental model of what the system does will nod at it.

### The quiet one

Run 2 hardcodes US-ASCII, which silently substitutes any byte above 0x7F.
Account numbers and holder names containing such bytes decode to replacement
characters with no error raised. Both the packed and binary fields are
unaffected — which means the corruption is confined to exactly the fields
nobody validates numerically.

## The substrate result

`run1-precorrection/` exists because it is evidence, not history.

The original fixture declared its input file `ORGANIZATION IS LINE SEQUENTIAL`
while carrying COMP-3 and COMP binary fields in the record — a declaration that
contradicts the content. Real COBOL does this.

Both tools honored the declaration. Both read the record as character text,
decoding a byte stream containing packed decimal through a character decoder,
and treating digit counts as byte widths. Every field after the account type
landed at the wrong offset. Unusable output, twice, for the same reason.

Changing one line in the ENVIRONMENT DIVISION moved both outputs from unusable
to substantially correct.

Both fixtures are here. `diff cobol/ACCTPROC-precorrection.cbl cobol/ACCTPROC.cbl`
is the entire change: the file's ORGANIZATION clause, plus header comments
recording the reasoning. Everything in `run1-precorrection/` was generated
against the first; everything in `run1/` and `run2/` against the second.
Comments inside `run1-precorrection/` that cite `ACCTPROC.cbl` are referring to
the pre-correction file.

Run 1's `run1-precorrection/RecordParser.java` is worth reading directly: its
header comment flags the contradiction, states the encoding cannot be settled
from source alone, and instructs the reader to treat the parser as a
placeholder. It produced wrong output anyway. Noticing did not save it.

This cuts against the reflex reading. The tools were not ignoring the record
format; they were honoring a declaration that was wrong. Which means a
migration's accuracy depends partly on the quality of the artifacts handed to
the tool — usually outside the tool's control and inside the client's.

`findings-report.md` Finding 7 identified this contradiction as a blocking risk
before either corrected run existed. What the two-run comparison added was the
size of the blast radius.

## The finding neither translation could resolve

`APR-RATE PIC S9(2)V9(5)` is divided by 12 and multiplied against the balance.
There is no divide-by-100 anywhere in the program.

The field must therefore hold a decimal fraction (`0.03500`) rather than a
percentage (`3.50000`). The picture clause accommodates both readings. On a
1000.00 balance, the percentage reading yields a monthly accrual of 291.67 —
arithmetically faithful to the source, and obviously wrong in production.

Run 1 surfaced this by reasoning about the absurdity of its own test output.
Run 2 did not raise it. Neither could *resolve* it, and neither could any tool,
because the answer is not in the code. It is held by whoever loads that field.

This is the shape of the residual risk in an AI-assisted migration. Both
translations are defensible readings of the source. Only one matches what the
business means. Static analysis cannot close that gap, and a tool that verifies
its output against the source will confirm a faithful translation of an
ambiguous specification without ever flagging the ambiguity.

## Honest limitations

- Synthetic single-program fixture. No CICS, no DB2, no JCL, no call graph.
- Run 1 self-verified by compiling and executing; Run 2 did not. Some of the
  divergence above is attributable to that difference rather than to
  translation quality.
- Neither output was executed against a shared input file. This is a
  code-level comparison, not a behavioral one. A byte-level diff of all three
  programs' output against the same records is the stronger result and has not
  been done yet. It is the next thing in this repo.
- Run 2 used a general-purpose model, not a mainframe migration product.
  Nothing here is a claim about watsonx Code Assistant for Z, IBM Bob, or AWS
  Transform specifically.
- One reviewer, no second opinion on the findings themselves.

## Contact

Josef Stephens — independent COBOL behavioral equivalence validation.
[LinkedIn](https://www.linkedin.com/in/josef-stephens-aa2aa319a/)
