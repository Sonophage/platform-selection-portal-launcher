#!/usr/bin/env python3
"""Find comment references to symbols that no longer exist in the Kotlin sources.

    python3 tools/stale-comments/check.py            # scan the repo from its root
    python3 tools/stale-comments/check.py --selftest # prove the detector still detects

WHAT IT FINDS

A "reference" is a KDoc link — [Foo], [Foo.bar] — or a backticked `Foo` / `foo()`. It is
reported when the base identifier appears nowhere in the CODE (comments stripped) of any .kt
file in the tree. That catches the narrow, checkable class of rot: a doc naming a symbol that
was renamed or deleted out from under it. Two real ones on the day this was written:

    GamepadInputHandler   [STICK_DEAD_ZONE]            the constant is STICK_DEAD_ZONE_FLOOR
    BackupKeyDriftTest    [DELIBERATELY_NOT_BACKED_UP] the property is deliberatelyNotBackedUp

WHAT IT CANNOT FIND, AND WHY THE OUTPUT IS A REPORT RATHER THAN A GATE

Most hits are correct and must stay. Of 49 on the first full run, 47 were fine, in three kinds:

  * framework symbols this repo does not declare — [ShortcutManager], [DocumentBuilderFactory],
    [PixelCopy], `DataStoreImpl.handleUpdate`. The index is repo identifiers only, so every
    reference to an Android or Java API looks absent to it;
  * deliberate history — "Was `contextRailOnly`, when the rail was the only one", "there used to
    be two: ... `IconColorCustomPicker`". Naming a thing that is gone is the point of the sentence;
  * stated absences — "There is deliberately no `PfpText` composable."

So it exits 0 whatever it finds. A human reads the list. Wiring it into CI would mean either a
wall of false alarms or a suppression list, and a suppression list is the second thing that has
to be kept in step with the first.

It also cannot see the larger class: prose that describes behaviour wrongly while naming nothing
that vanished. XMBViewModel carried two stacked doc blocks where the `//` one described a
different property entirely, and nothing here would ever have flagged it. Read the code.

ONE MORE BLIND SPOT, STATED PLAINLY: identifiers inside string literals count as code, so a dead
name that also appears in a string will not be reported.
"""
import argparse
import collections
import pathlib
import re
import sys

# A string literal, so `//` and `[Name]` inside one are never mistaken for a comment.
STRING = re.compile(r'"{3}.*?"{3}|"(?:\\.|[^"\\\n])*"', re.S)
IDENT = re.compile(r'[A-Za-z_][A-Za-z0-9_]*')
KDOC_LINK = re.compile(r'\[([A-Za-z_][A-Za-z0-9_.]*)\]')
BACKTICK = re.compile(r'`([A-Za-z_][A-Za-z0-9_.]*)\(?\)?`')

# Markdown/KDoc words that are prose, not symbols.
NOISE = {'true', 'false', 'null', 'this', 'it', 'and', 'or', 'not', 'the', 'a', 'an'}


def split_comments(src):
    """Return (code_with_comments_blanked, [(line_number, comment_text)])."""
    comments, out = [], []
    i, n, line = 0, len(src), 1
    while i < n:
        if src[i] == '"':
            m = STRING.match(src, i)
            if m:
                out.append(m.group())
                line += m.group().count('\n')
                i = m.end()
                continue
        if src.startswith('//', i):
            j = src.find('\n', i)
            j = n if j < 0 else j
            comments.append((line, src[i:j]))
            out.append(' ' * (j - i))
            i = j
            continue
        if src.startswith('/*', i):
            j = src.find('*/', i + 2)
            j = n if j < 0 else j + 2
            text = src[i:j]
            comments.append((line, text))
            out.append(' ' * len(text))
            line += text.count('\n')
            i = j
            continue
        if src[i] == '\n':
            line += 1
        out.append(src[i])
        i += 1
    return ''.join(out), comments


def scan(root):
    """Yield (path, line, symbol, source_line) for every comment reference with no definition."""
    files = [p for p in pathlib.Path(root).rglob('*.kt') if '/build/' not in str(p)]
    parsed, code_idents = {}, set()
    for p in files:
        code, comments = split_comments(p.read_text(encoding='utf-8', errors='replace'))
        parsed[p] = comments
        code_idents.update(IDENT.findall(code))

    for p, comments in parsed.items():
        for start, text in comments:
            for offset, ln in enumerate(text.split('\n')):
                refs = set(KDOC_LINK.findall(ln)) | set(BACKTICK.findall(ln))
                for ref in sorted(refs):
                    base = ref.split('.')[0]
                    if base.lower() in NOISE or len(base) < 3:
                        continue
                    if base not in code_idents:
                        yield p, start + offset, base, ln.strip()[:120]
    return


FIXTURE = '''package fixture

/**
 * Renders via [LiveThing] and the old [DeadThing] pipeline.
 * Callers should use `liveFun()` not `deadFun()`.
 */
class LiveThing {
    fun liveFun() {
        val url = "trailing // [GhostInString] inside a string"
        val block = """
            trailing // [GhostInRawString] inside a raw string
        """
        println(url + block)
    }
}
'''


def selftest():
    """Break-it-on-purpose check: the detector must flag what is gone and nothing else.

    The fixture is deliberately NOT drawn from this repo. A check that has only ever seen its
    own home input has not been tested — that is how a scanner comes back clean because it
    could not see the thing at all.
    """
    import tempfile
    with tempfile.TemporaryDirectory() as d:
        pathlib.Path(d, 'Sample.kt').write_text(FIXTURE)
        found = {sym for _, _, sym, _ in scan(d)}

    expected = {'DeadThing', 'deadFun'}
    # Live symbols must not be flagged, and neither must a reference inside a string literal.
    # The `//` comes FIRST in those two fixture strings on purpose: that is what makes the
    # assertion bite. With the marker after the reference, dropping string-tracking produces no
    # reference to flag and this check passes while broken — it did, the first time it was run.
    forbidden = {'LiveThing', 'liveFun', 'GhostInString', 'GhostInRawString'}

    ok = True
    if found & forbidden:
        print(f"FAIL: flagged something it must not: {sorted(found & forbidden)}")
        ok = False
    if not expected <= found:
        print(f"FAIL: missed a dead reference: {sorted(expected - found)}")
        ok = False
    print("selftest: ok" if ok else "selftest: FAILED")
    return 0 if ok else 1


# The baseline is a set of "file::symbol" keys, NOT line numbers.
#
# Line numbers would make the baseline stale on the first edit above a finding, and a baseline
# that goes stale on every commit is one nobody regenerates and everybody ignores. file::symbol
# survives code moving around inside a file, which is what actually happens.
def key_of(path, symbol):
    return f"{path}::{symbol}"


def load_baseline(path):
    p = pathlib.Path(path)
    if not p.exists():
        return None
    return {
        line.strip()
        for line in p.read_text().splitlines()
        if line.strip() and not line.startswith('#')
    }


def main():
    ap = argparse.ArgumentParser(description=__doc__.split('\n')[0])
    ap.add_argument('root', nargs='?', default='.', help='repo root to scan (default: .)')
    ap.add_argument('--selftest', action='store_true', help='check the detector, scan nothing')
    ap.add_argument('--baseline', metavar='FILE',
                    help='accept everything in FILE; exit 1 only on references NOT in it')
    ap.add_argument('--write-baseline', metavar='FILE',
                    help='record the current findings to FILE and exit 0')
    args = ap.parse_args()

    if args.selftest:
        return selftest()

    findings = sorted(scan(args.root), key=lambda f: (str(f[0]), f[1]))
    by_file = collections.Counter(str(f[0]) for f in findings)

    if args.write_baseline:
        keys = sorted({key_of(path, symbol) for path, _, symbol, _ in findings})
        pathlib.Path(args.write_baseline).write_text(
            "# Stale-comment baseline. Regenerate with --write-baseline after READING the diff.\n"
            "# Keys are file::symbol, not line numbers, so code moving inside a file does not\n"
            "# invalidate an entry. A key here means 'known and accepted', NOT 'correct'.\n"
            + "\n".join(keys) + "\n"
        )
        print(f"wrote {len(keys)} key(s) to {args.write_baseline}")
        return 0

    baseline = load_baseline(args.baseline) if args.baseline else None
    fresh = [f for f in findings if baseline is None or key_of(f[0], f[2]) not in baseline]

    for path, line, symbol, source in (fresh if baseline is not None else findings):
        print(f"{path}:{line}: [{symbol}]  {source}")

    if baseline is None:
        print(f"\n{len(findings)} comment reference(s) with no definition, in {len(by_file)} file(s).")
        print("Most are framework symbols, deliberate history or stated absences — read before editing.")
        return 0

    if not fresh:
        print(f"no NEW stale comment references ({len(baseline)} known, accepted).")
        return 0
    print(f"\n{len(fresh)} NEW comment reference(s) with no definition.")
    print("Either fix the comment, or — if it is a framework symbol or deliberate history —")
    print("regenerate the baseline with --write-baseline and say why in the commit.")
    return 1


if __name__ == '__main__':
    sys.exit(main())
