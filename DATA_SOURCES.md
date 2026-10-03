# HanScan V3 — Data policy

HanScan separates **OCR**, **pronunciation**, and **meaning** data so that a high-confidence OCR result is not confused with a high-confidence translation.

## Bundled in this alpha

`app/src/main/assets/dictionary/core_vi.json`

- 255 high-frequency entries.
- Vietnamese meanings are a small curated HanScan core, tagged `REVIEWED_CORE`.
- This file is intentionally small: the app must never label an unknown machine translation as "verified".
- Pinyin for dictionary hits comes from the curated entry. Unknown single characters use pinyin4j as a fallback and are explicitly shown as fallback data.

## Planned full data pack

### CC-CEDICT

- Chinese headword dictionary with Traditional, Simplified, Pinyin, and English glosses.
- Current download page: https://www.mdbg.net/chinese/dictionary?page=cc-cedict
- License: CC BY-SA 4.0.
- Do **not** automatically turn the English gloss into a "verified Vietnamese meaning". Vietnamese review is a separate layer.

### Unicode Unihan

- Character-level Han data such as Mandarin readings, variants, radical/stroke information, and dictionary-like properties.
- UAX #38 / Unihan: https://www.unicode.org/reports/tr38/
- Unicode data terms apply.

`tools/build_full_dictionary.py` can ingest a user-downloaded CC-CEDICT text file and Unicode `Unihan.zip` into a source-traceable SQLite database.

## Confidence labels

- `REVIEWED_CORE`: Vietnamese meaning reviewed in HanScan's small core.
- `SOURCE_DICTIONARY`: dictionary-derived reading/gloss but not yet Vietnamese-reviewed.
- `FALLBACK`: generated/algorithmic helper, never presented as verified Vietnamese meaning.
- OCR quality and dictionary confidence are displayed separately.
