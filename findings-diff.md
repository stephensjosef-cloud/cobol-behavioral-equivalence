# Findings — Two Independent Translations of ACCTPROC

**Subject:** `ACCTPROC.cbl` + `ACCTREC_A2.cpy` (synthetic; no client data)
**Date:** 2026-08-03

## Method

Two translations to Java, produced independently. Neither run received an
archaeology pass, a validation checklist, or any indication of where the
difficult constructs were. The prompt in both cases was a single line:
*translate this COBOL program and its copybook to Java.*

Both runs used the same source: a fixed-length 79-byte `ORGANIZATION IS
SEQUENTIAL` input record carrying two COMP-3 fields at different scales, one
binary COMP halfword, a date REDEFINES, and a discriminated-union REDEFINES on
the account type.

Run 1 was produced by an agentic tool that compiled and executed its own output
against synthetic records before returning. Run 2 was a single completion. That
difference is a property of the tools, not of the experiment, and it accounts
for part of what follows.

## A note on the substrate

An earlier version of this fixture, now published as
`cobol/ACCTPROC-precorrection.cbl`, declared the input file `ORGANIZATION IS
LINE SEQUENTIAL`. Both tools responded to that declaration by reading the
record as character text — decoding a byte stream containing packed decimal
through a character decoder, and treating digit counts as byte widths. Every
field after the account type landed at the wrong offset.

Changing one line in the ENVIRONMENT DIVISION moved both outputs from
unusable to substantially correct.

This is worth stating because it cuts against the reflex reading. The tools
were not ignoring the record format; they were honoring a declaration that was
wrong. How source is presented materially changes what comes back, which means
a migration's accuracy depends partly on the quality of the artifacts handed to
the tool — something usually outside the tool's control and inside the
client's.

## What both translations got right

- Fixed 79-byte offset addressing; input read as bytes, never through a
  `Reader`. Offsets sum correctly: 10+30+1+6+4+2+8+18.
- COMP-3 decoded to `BigDecimal` at the copybook's implied scales. No `double`,
  no `float`, at any point.
- `OVERDRAFT-DAYS` correctly treated as a 2-byte big-endian binary halfword and
  left as an integer — not promoted to `BigDecimal` alongside its packed
  neighbours.
- `ROUNDED` implemented as HALF_UP (half away from zero), not HALF_EVEN.
- The monthly rate rounded to its own 7 decimals *before* the multiply, rather
  than collapsing both COMPUTE statements into one expression. Collapsing them
  drifts by a cent on some records.
- The `ACCT-DEPOSIT` 88-level gate preserved.
- REDEFINES modeled as alternate views over shared bytes rather than as
  separate parsed fields.

The constructs most often named as what breaks AI migration — packed decimal
precision, rounding mode, copybook layout — were cleared by both runs in a
single pass.

## Divergences

| Behavior | Run 1 | Run 2 |
|---|---|---|
| `PIC -(9)9.99` edited move | Dedicated picture-editor implementation | `toPlainString()` — variable width, no floating sign |
| `PIC 9(05)` counters | Zero-padded | Unpadded (`PROCESSED=42`), and widened to `long` |
| Character encoding | Named as an open decision: ISO-8859-1 vs IBM037 | Hardcoded US-ASCII |
| Unrecognized `ACCT-TYPE-CODE` | No pre-branch; offset views only | Anything other than `D` parsed as a loan record |
| `DEP-INTEREST-YTD` | Untouched, as in source | Accumulated into during accrual |
| Output line endings | Flagged as a byte-comparison decision | Silent |

### The material one

`ACCTPROC` does not read or write `DEP-INTEREST-YTD`. Run 2 added an
accumulation into that field during interest accrual.

This is the most consequential error in either output, and it is the hardest
class to catch on review. A dropped rule announces itself when output goes
missing. An *added* rule is plausible, reads as correct, and is very likely
true of the wider system somewhere — just not of this program. A reviewer
comparing the Java against a mental model of what the system does will nod at
it.

### The quiet one

Hardcoded US-ASCII silently substitutes any byte above 0x7F. Account numbers
and holder names containing such bytes decode to replacement characters with no
error raised. Both the packed and binary fields are unaffected, which means the
corruption is confined to exactly the fields nobody validates numerically.

## The finding neither translation could resolve

`APR-RATE PIC S9(2)V9(5)` is divided by 12 and multiplied against the balance.
There is no divide-by-100 anywhere in the program.

The field must therefore hold a decimal fraction (`0.03500`) rather than a
percentage (`3.50000`). The picture clause accommodates both readings. On a
1000.00 balance, the percentage reading yields a monthly accrual of 291.67 —
arithmetically faithful to the source, and obviously wrong in production.

Run 1 surfaced this by reasoning about the absurdity of its own test output.
Run 2 did not raise it. But neither could *resolve* it, and neither could any
tool, because the answer is not in the code. It is held by whoever loads that
field.

This is the shape of the residual risk in an AI-assisted migration. Both
translations are defensible readings of the source. Only one matches what the
business means. Static analysis cannot close that gap, and a tool that
verifies its output against the source will confirm a faithful translation of
an ambiguous specification without ever flagging the ambiguity.

## Limitations of this exercise

- Synthetic single-program fixture. No CICS, no DB2, no JCL, no call graph.
- Run 1 self-verified by compiling and executing; Run 2 did not. Some of the
  divergence above is attributable to that difference rather than to
  translation quality.
- Neither output was executed against a shared input file, so this is a
  code-level comparison, not a behavioral one. A byte-level diff of both
  programs' output against the same records would be a stronger result and has
  not been done.
- One reviewer, no second opinion on the findings themselves.
