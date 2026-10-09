"""Execute SQL extracted from production Kotlin using host SQLite.

This checks SQL behavior with normalized fixture parameters. It does not run
the Kotlin normalizer, Android, USB enumeration, UI, playback, or TV hardware.
"""
import hashlib
import json
from pathlib import Path
import re
import sqlite3
import unicodedata
import unittest

ROOT = Path(__file__).resolve().parents[2]
SOURCE = ROOT / "app/src/main/java/com/araujo/karaoke_ia/data/MusicRepository.kt"
TEXT = SOURCE.read_text(encoding="utf-8-sig")
LITERAL = r'"(?:\\.|[^"\\])*"'
SCHEMA_SQL = [json.loads(value) for value in re.findall(
    rf'\bdb\.execSQL\(({LITERAL})\)', TEXT
) if json.loads(value).startswith("CREATE ")]
SEARCH_BODY = TEXT.split("override fun searchLocal", 1)[1].split("override fun findMusicByCode", 1)[0]
SEARCH_SQL = json.loads(re.search(rf'\bdb\.rawQuery\(({LITERAL})', SEARCH_BODY).group(1))
REPLACEMENTS = [(json.loads(old), json.loads(new)) for old, new in re.findall(
    rf'\.replace\(({LITERAL}),\s*({LITERAL})\)', SEARCH_BODY
)]


def normalized_fixture(value):
    """Prepare normalized test inputs; this is not execution of Kotlin code."""
    return "".join(character for character in unicodedata.normalize("NFD", value)
                   if not unicodedata.category(character).startswith("M")).lower()


class RepositorySqlTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.row_factory = sqlite3.Row
        for statement in SCHEMA_SQL:
            self.db.execute(statement)
        self.addCleanup(self.db.close)

    def song(self, code, title, artist="João", root="/storage/USB-A/karaoke", available=1,
             path=None, duration=120000):
        path = path or f"{root}/{code}.mp4"
        key = code.lstrip("0") or "0" if code.isdigit() else code
        self.db.execute("INSERT INTO songs VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                        (path, root, code, key, f"{code}.mp4", artist, title, "", duration,
                         "fixture-metadata", "fixture-video", normalized_fixture(f"{code} {title} {artist}"),
                         available, 1))
        return path

    def search(self, query, limit=200, offset=0):
        term = normalized_fixture(query.strip())
        for old, new in REPLACEMENTS:
            term = term.replace(old, new)
        return self.db.execute(SEARCH_SQL, (f"%{term}%", str(limit), str(offset))).fetchall()

    def test_schema_and_column_positions_match_cursor_contract(self):
        self.assertEqual(4, len(SCHEMA_SQL))
        columns = [row[1] for row in self.db.execute("PRAGMA table_info(songs)")]
        self.assertEqual(["path", "root", "code", "code_key", "file_name", "artist", "title", "lyrics",
                          "duration", "signature", "video_signature", "search_text", "available", "seen"], columns)
        indexes = {row[1] for row in self.db.execute("PRAGMA index_list(songs)")}
        self.assertTrue({"songs_root", "songs_code", "songs_title"}.issubset(indexes))

    def test_normalized_title_artist_and_code_parameters(self):
        self.song("01007", "Evidências", "João e José")
        self.song("01008", "Outra música", "Cantor")
        for query in ("EVIDENCIAS", " evidências ", "joao", "JOSE", "01007"):
            with self.subTest(query=query):
                self.assertEqual(["01007"], [row["code"] for row in self.search(query)])
        self.assertEqual([], self.search("inexistente"))

    def test_pagination_has_stable_nonoverlapping_results(self):
        for index in range(25):
            self.song(f"{index:05d}", f"Canção {index:02d}")
        pages = [self.search("cancao", 10, offset) for offset in (0, 10, 20)]
        self.assertEqual([10, 10, 5], [len(page) for page in pages])
        paths = [row["path"] for page in pages for row in page]
        self.assertEqual(25, len(set(paths)))
        self.assertEqual([row["path"] for row in self.search("cancao", 100)], paths)
        self.assertEqual([], self.search("cancao", 10, 100))

    def test_extracted_escape_replacements_treat_wildcards_literally(self):
        self.assertEqual([("\\", "\\\\"), ("%", "\\%"), ("_", "\\_")], REPLACEMENTS)
        self.song("00001", "100% amor")
        self.song("00002", "Nome_com_underscore")
        self.song("00003", r"Caminho\Especial")
        self.song("00004", "Outro nome comum")
        for query, code in (("%", "00001"), ("_", "00002"), ("\\", "00003")):
            with self.subTest(query=query):
                self.assertEqual([code], [row["code"] for row in self.search(query)])

    def test_duplicate_codes_choose_connected_volume_before_pagination(self):
        self.song("00001", "Versão antiga", root="/storage/AAA", available=0)
        connected_path = self.song("1", "Versão conectada", root="/storage/ZZZ", available=1)
        self.song("00002", "Outra canção", root="/storage/ZZZ", available=1)
        rows = self.search("", 10)
        self.assertEqual(2, len(rows))
        self.assertEqual(connected_path, next(row["path"] for row in rows if row["code_key"] == "1"))
        self.assertEqual([], self.search("antiga"))
        self.assertEqual(connected_path, self.search("conectada", 1)[0]["path"])

    def test_disconnected_catalogue_remains_searchable_with_original_path(self):
        original_path = "/storage/USB-B/karaoke/subpasta/Canção #1.mp4"
        self.song("Canção #1", "Canção especial", path=original_path, available=0, duration=135123)
        row = self.search("cancao especial")[0]
        self.assertEqual(original_path, row["path"])
        self.assertEqual(0, row["available"])
        self.assertEqual(135123, row["duration"])

    def test_connected_duplicate_selection_is_deterministic(self):
        first = self.song("00003", "Mesmo título", root="/storage/A", available=1)
        self.song("3", "Mesmo título", root="/storage/B", available=1)
        self.assertEqual(first, self.search("mesmo")[0]["path"])
        self.assertEqual(1, len(self.search("")))


if __name__ == "__main__":
    import io
    output = io.StringIO()
    result = unittest.TextTestRunner(stream=output, verbosity=2).run(
        unittest.defaultTestLoader.loadTestsFromTestCase(RepositorySqlTest))
    summary = {
        "scope": "Production SQL extracted from MusicRepository.kt, executed with host Python SQLite; Android and TV hardware not executed",
        "source": str(SOURCE.relative_to(ROOT)),
        "source_sha256": hashlib.sha256(SOURCE.read_bytes()).hexdigest(),
        "host_sqlite_version": sqlite3.sqlite_version,
        "tests_run": result.testsRun,
        "failures": len(result.failures),
        "errors": len(result.errors),
        "schema_statements": SCHEMA_SQL,
        "search_statement": SEARCH_SQL,
        "output": output.getvalue(),
    }
    destination = Path(__file__).with_name("host-sql-results.json")
    destination.write_text(json.dumps(summary, indent=2, ensure_ascii=False), encoding="utf-8")
    print(output.getvalue(), end="")
    print(f"Results: {destination}")
    raise SystemExit(0 if result.wasSuccessful() else 1)
