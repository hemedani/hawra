#!/usr/bin/env python3
"""Verify that hand-written Room migrations match the schema Room generates.

Why this exists
---------------
Room does not check a `Migration`'s SQL against the entity definitions at compile time. It
checks at *runtime*, on the upgrade, on the user's device — where the failure is
`IllegalStateException: Migration didn't properly handle: …` and the app will not open.

So the migration in `HawraaDatabase.kt` is compared here against
`app/schemas/<db>/<version>.json`, which is the schema Room itself would create for that
version. Two kinds of statement are checked:

* **CREATE TABLE / CREATE INDEX** for tables that did not exist before — compared as
  normalised SQL, because Room compares `createSql` textually too.
* **ALTER TABLE … ADD COLUMN** for columns added to a table that already exists — compared
  as `(table, column, affinity, notNull, default)` tuples rather than as text, because Room
  validates the resulting column, not the statement that produced it. Comparing the tuple
  means clause order and quoting cannot produce a false failure.

Usage
-----
    python3 tools/verify-room-migration.py            # check every version > 1
    python3 tools/verify-room-migration.py 1 2        # check only 1 -> 2

Exit code 0 means the migration produces exactly the schema Room expects: nothing missing,
nothing invented.
"""

from __future__ import annotations

import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
DB_CLASS = "ir.hawraa.quran.data.local.HawraaDatabase"
SCHEMA_DIR = ROOT / "app" / "schemas" / DB_CLASS
SOURCE = (
    ROOT / "app" / "src" / "main" / "kotlin" / "ir" / "hawraa" / "quran" / "data" / "local"
    / "HawraaDatabase.kt"
)


def normalise(sql: str) -> str:
    """Collapse whitespace and the cosmetic `( … )` spacing Kotlin multi-line strings add."""
    sql = re.sub(r"\s+", " ", sql).strip().rstrip(";")
    return sql.replace("( ", "(").replace(" )", ")")


def load(version: int) -> dict:
    path = SCHEMA_DIR / f"{version}.json"
    if not path.exists():
        sys.exit(f"missing schema {path} — run ./gradlew compileDebugKotlin first")
    return json.loads(path.read_text())


def tables_of(schema: dict) -> dict[str, dict]:
    return {e["tableName"]: e for e in schema["database"]["entities"]}


def expected_creates(current: dict, previous: dict) -> set[str]:
    """Every CREATE Room expects at `version`, minus tables that already existed."""
    existing = set(tables_of(previous))
    out: list[str] = []
    for table, entity in tables_of(current).items():
        if table in existing:
            continue
        out.append(entity["createSql"].replace("${TABLE_NAME}", table))
        out += [i["createSql"].replace("${TABLE_NAME}", table) for i in entity.get("indices", [])]
    return {normalise(s) for s in out}


def expected_alters(current: dict, previous: dict) -> set[tuple]:
    """Columns that must be added to tables which already existed."""
    before = tables_of(previous)
    out: set[tuple] = set()
    for table, entity in tables_of(current).items():
        if table not in before:
            continue
        known = {f["columnName"] for f in before[table]["fields"]}
        for field in entity["fields"]:
            if field["columnName"] in known:
                continue
            out.add(
                (
                    table,
                    field["columnName"],
                    field["affinity"],
                    bool(field.get("notNull")),
                    field.get("defaultValue"),
                )
            )
    return out


def parse_alter(statement: str) -> tuple | None:
    match = re.match(
        r"ALTER\s+TABLE\s+`?(\w+)`?\s+ADD\s+COLUMN\s+`?(\w+)`?\s+([A-Z]+)(.*)$",
        normalise(statement),
        re.I,
    )
    if not match:
        return None
    table, column, affinity, tail = match.groups()
    not_null = bool(re.search(r"\bNOT\s+NULL\b", tail, re.I))
    default = re.search(r"\bDEFAULT\s+(\S+)", tail, re.I)
    value = default.group(1).strip("'\"") if default else None
    return (table, column, affinity.upper(), not_null, value)


def migration_statements(from_version: int, to_version: int) -> tuple[set[str], set[tuple], list[str]]:
    """Pull the SQL out of the `Migration(from, to)` block in the database class."""
    source = SOURCE.read_text()
    marker = f"Migration({from_version}, {to_version})"
    start = source.find(marker)
    if start < 0:
        sys.exit(f"no {marker} found in {SOURCE.name}")
    end = source.find("\n        }", start)
    block = source[start : end if end > 0 else len(source)]

    creates = re.findall(r'"""\s*(CREATE TABLE.*?)\s*"""', block, re.S)
    creates += re.findall(r'"(CREATE INDEX[^"]*)"', block)
    alters_raw = re.findall(r'"(ALTER TABLE[^"]*)"', block)
    alters = [parse_alter(s) for s in alters_raw]
    unparsed = [s for s, parsed in zip(alters_raw, alters) if parsed is None]
    return {normalise(s) for s in creates}, {a for a in alters if a}, unparsed


def main() -> int:
    versions = sorted(int(p.stem) for p in SCHEMA_DIR.glob("*.json") if p.stem.isdigit())
    if len(sys.argv) == 3:
        pairs = [(int(sys.argv[1]), int(sys.argv[2]))]
    else:
        pairs = [(v, versions[i + 1]) for i, v in enumerate(versions[:-1])]

    if not pairs:
        print(f"only one schema version ({versions}) — nothing to verify")
        return 0

    failures = 0
    for old, new in pairs:
        previous, current = load(old), load(new)
        expected_create = expected_creates(current, previous)
        expected_alter = expected_alters(current, previous)

        actual_create, actual_alter, unparsed = migration_statements(old, new)
        missing_create, extra_create = expected_create - actual_create, actual_create - expected_create
        missing_alter, extra_alter = expected_alter - actual_alter, actual_alter - expected_alter

        print(
            f"v{old} -> v{new}: {len(actual_create)} CREATE, {len(actual_alter)} ALTER "
            f"({len(expected_create)} + {len(expected_alter)} expected)"
        )
        for statement in sorted(missing_create):
            print(f"  MISSING from migration: {statement}")
        for statement in sorted(extra_create):
            print(f"  EXTRA in migration    : {statement}")
        for column in sorted(missing_alter):
            print(f"  MISSING column        : {column[0]}.{column[1]}")
        for column in sorted(extra_alter):
            print(f"  EXTRA column          : {column[0]}.{column[1]}")
        for statement in unparsed:
            print(f"  UNPARSED statement    : {statement}")

        if missing_create or extra_create or missing_alter or extra_alter or unparsed:
            failures += 1
        else:
            print("  exact match")

    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
