# Run 1 — pre-correction

These files are the first translation, generated against
`../cobol/ACCTPROC-precorrection.cbl`, which declares the input file
`ORGANIZATION IS LINE SEQUENTIAL` while the record carries COMP-3 and COMP
binary fields.

The output is wrong, and that is why it is here. Every field after the account
type decodes at the wrong offset. See "The substrate result" in the top-level
README for what that cost and what fixed it.

`RecordParser.java` is the file worth reading. Its header comment flags the
contradiction, states the encoding cannot be settled from source alone, and
calls itself a placeholder. It produced wrong output anyway.

Comments in these files that refer to `ACCTPROC.cbl` mean the pre-correction
fixture, not `../cobol/ACCTPROC.cbl` as it now stands.

Nothing in this directory has been edited.
