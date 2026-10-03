#!/usr/bin/env python3
"""Build a source-traceable SQLite dictionary for a future HanScan data pack.

Usage:
  python build_full_dictionary.py --cedict cedict_1_0_ts_utf-8_mdbg.txt --unihan Unihan.zip --out hanscan_full.sqlite

Important: this script does NOT machine-translate CC-CEDICT English glosses into Vietnamese.
That is intentional: Vietnamese meanings should be curated/reviewed separately instead of silently
presenting machine translations as verified dictionary data.
"""
import argparse, re, sqlite3, zipfile
from pathlib import Path

CEDICT_RE = re.compile(r"^(\S+)\s+(\S+)\s+\[([^]]+)\]\s+/(.+)/$")

def init_db(con):
    con.executescript('''
    PRAGMA journal_mode=WAL;
    CREATE TABLE IF NOT EXISTS cedict(
      traditional TEXT NOT NULL,
      simplified TEXT NOT NULL,
      pinyin_numbered TEXT NOT NULL,
      gloss_en TEXT NOT NULL,
      source TEXT NOT NULL DEFAULT 'CC-CEDICT',
      verified_vi INTEGER NOT NULL DEFAULT 0
    );
    CREATE INDEX IF NOT EXISTS idx_cedict_s ON cedict(simplified);
    CREATE INDEX IF NOT EXISTS idx_cedict_t ON cedict(traditional);
    CREATE TABLE IF NOT EXISTS unihan(
      codepoint TEXT NOT NULL,
      property TEXT NOT NULL,
      value TEXT NOT NULL,
      source TEXT NOT NULL DEFAULT 'Unicode Unihan'
    );
    CREATE INDEX IF NOT EXISTS idx_unihan_cp ON unihan(codepoint);
    CREATE TABLE IF NOT EXISTS vi_curated(
      headword TEXT PRIMARY KEY,
      pinyin TEXT,
      meaning_vi TEXT NOT NULL,
      reviewer TEXT,
      review_status TEXT NOT NULL DEFAULT 'REVIEWED',
      updated_at TEXT
    );
    ''')

def import_cedict(con, path):
    rows=[]
    with open(path,'r',encoding='utf-8') as f:
        for line in f:
            line=line.strip()
            if not line or line.startswith('#'): continue
            m=CEDICT_RE.match(line)
            if not m: continue
            trad,simp,pinyin,gloss=m.groups()
            rows.append((trad,simp,pinyin,gloss.replace('/','; ')))
            if len(rows)>=5000:
                con.executemany('INSERT INTO cedict(traditional,simplified,pinyin_numbered,gloss_en) VALUES(?,?,?,?)',rows); rows=[]
    if rows: con.executemany('INSERT INTO cedict(traditional,simplified,pinyin_numbered,gloss_en) VALUES(?,?,?,?)',rows)

def import_unihan(con, zpath):
    batch=[]
    with zipfile.ZipFile(zpath) as z:
        for name in z.namelist():
            if not name.startswith('Unihan_') or not name.endswith('.txt'): continue
            for raw in z.read(name).decode('utf-8').splitlines():
                if not raw or raw.startswith('#'): continue
                parts=raw.split('\t',2)
                if len(parts)!=3: continue
                cp,prop,val=parts
                # Keep the properties most useful to a learner-facing dictionary.
                if prop not in {'kMandarin','kHanyuPinyin','kDefinition','kSimplifiedVariant','kTraditionalVariant','kTotalStrokes','kRSUnicode'}:
                    continue
                batch.append((cp,prop,val))
                if len(batch)>=10000:
                    con.executemany('INSERT INTO unihan(codepoint,property,value) VALUES(?,?,?)',batch); batch=[]
    if batch: con.executemany('INSERT INTO unihan(codepoint,property,value) VALUES(?,?,?)',batch)

def main():
    ap=argparse.ArgumentParser()
    ap.add_argument('--cedict',required=True)
    ap.add_argument('--unihan',required=True)
    ap.add_argument('--out',required=True)
    a=ap.parse_args()
    out=Path(a.out); out.unlink(missing_ok=True)
    con=sqlite3.connect(out)
    try:
        init_db(con); import_cedict(con,a.cedict); import_unihan(con,a.unihan); con.commit()
        print('built',out)
        print('cedict rows',con.execute('select count(*) from cedict').fetchone()[0])
        print('unihan rows',con.execute('select count(*) from unihan').fetchone()[0])
    finally: con.close()
if __name__=='__main__': main()
