#!/usr/bin/env python3
"""Build a dummy content tree for the PSPLauncher emulators.

Everything is GENERATED, not downloaded: no copyright question, and — more usefully — the
metadata is exactly what the app's own parsers read, so the UI can be driven into states that
are awkward to reach with real files.

Deliberate choices, each aimed at something currently untested:
  * 30 games spanning A-Z, for the letter rail (LETTER_JUMP_MIN_ITEMS / MIN_LETTERS).
    The last four titles are deliberately OUT of alphabetical order. Item count and initial
    count are not sufficient: letterAnchors() also refuses any list whose initials ever go
    backwards, so on the Recently Added shelf -- which is insertion order -- these four make
    the rail correctly absent. Drop them, or use a title-sorted list, to see it appear.
  * Music where `artist` is a CREDIT STRING and `albumArtist` is the single act, because that
    split is what the Artists column groups on.
  * One book with a series, one without, since seriesIndex is nullable and rarely exercised.
"""
import os, pathlib, shutil, subprocess, zipfile, sys

ROOT = pathlib.Path(sys.argv[1] if len(sys.argv) > 1 else 'PFPTest')
if ROOT.exists(): shutil.rmtree(ROOT)

def ff(*args):
    subprocess.run(['ffmpeg','-y','-loglevel','error',*args], check=True)

# ── Games: 30 .gba files, A-Z coverage for the letter rail ────────────────────
# Extension-only placeholders. The scanner keys off romExtensions ("gba,agb,zip" in
# PlatformSeeder) and never parses the ROM, so these list and open a detail page. They will NOT
# launch — nothing here is a real GBA image, and that is the point: no copyrighted ROMs.
GAMES = [
 "Aurora Drift","Basalt Rally","Cinder Court","Dust Harbour","Ember Lane","Fathom Reach",
 "Glass Meridian","Harrow Point","Iron Lantern","Jetty Bloom","Kettle Ridge","Lumen Fall",
 "Marrow Fields","Nimbus Track","Onyx Parade","Pallet Storm","Quarry Light","Rust Cadence",
 "Salt Meridian","Tinder Vale","Umber Coast","Vellum Sky","Willow Static","Xenon Alley",
 "Yarrow Bend","Zephyr Cascade","Amber Second","Basalt Second","3 Below Zero","12 Lanterns",
]
gdir = ROOT/'Roms'/'GBA'; gdir.mkdir(parents=True)
for i, name in enumerate(GAMES):
    # Not empty: a zero-byte file is a plausible thing for a scanner to skip.
    (gdir/f'{name}.gba').write_bytes(bytes((i*7+j) % 256 for j in range(4096)))

# ── Video ─────────────────────────────────────────────────────────────────────
vdir = ROOT/'Videos'; vdir.mkdir(parents=True)
for name, secs, size in [("Harbour Lights (2019)",6,"640x360"),
                         ("Signal Test S01E02",4,"1280x720"),
                         ("Quiet Coast",5,"854x480")]:
    ff('-f','lavfi','-i',f'testsrc=size={size}:rate=24:duration={secs}',
       '-f','lavfi','-i',f'sine=frequency=320:duration={secs}',
       '-c:v','libx264','-pix_fmt','yuv420p','-c:a','aac','-shortest',
       '-metadata',f'title={name}', str(vdir/f'{name}.mp4'))

# ── Photos ────────────────────────────────────────────────────────────────────
pdir = ROOT/'Photos'; pdir.mkdir(parents=True)
for i,(name,src) in enumerate([
        ("Gradient Study","gradients=size=1600x1000:speed=0.1"),
        ("Test Pattern","testsrc2=size=1920x1080"),
        ("Colour Bars","smptebars=size=1280x720"),
        ("Mandelbrot","mandelbrot=size=1400x1400"),
        ("Plasma Field","life=size=800x600:rate=1"),
        ("Flat Slate","color=c=0x2A3550:size=1200x1600")]):
    ff('-f','lavfi','-i',src,'-vframes','1','-q:v','3', str(pdir/f'{name}.jpg'))

# ── Music ─────────────────────────────────────────────────────────────────────
# artist is the credit string, album_artist is the act. The Artists column groups on the act;
# a library tagged only with `artist` lists every collaboration as its own artist.
mdir = ROOT/'Music'; mdir.mkdir(parents=True)
ALBUMS = [
 ("Vela Quartet","Longitudes",2021,
  [("Meridian Hum","Vela Quartet"),("Slack Tide","Vela Quartet"),
   ("Foghorn","Vela Quartet feat. Ida Pell"),("Low Water","Vela Quartet")]),
 ("Ida Pell","Paper Radio",2023,
  [("Paper Radio","Ida Pell"),("Nine Volt","Ida Pell & The Static"),
   ("Aerial","Ida Pell"),("Test Card","Ida Pell")]),
]
for album_artist, album, year, tracks in ALBUMS:
    adir = mdir/album_artist/album; adir.mkdir(parents=True)
    for n,(title,credit) in enumerate(tracks, 1):
        freq = 220 + n*55
        ff('-f','lavfi','-i',f'sine=frequency={freq}:duration=8',
           '-c:a','libmp3lame','-b:a','128k',
           '-metadata',f'title={title}', '-metadata',f'artist={credit}',
           '-metadata',f'album_artist={album_artist}', '-metadata',f'album={album}',
           '-metadata',f'track={n}/{len(tracks)}', '-metadata',f'date={year}',
           str(adir/f'{n:02d} {title}.mp3'))

# ── Books ─────────────────────────────────────────────────────────────────────
bdir = ROOT/'Books'; bdir.mkdir(parents=True)
def epub(path, title, author, series=None, index=None):
    series_meta = ""
    if series:
        series_meta = (f'<meta name="calibre:series" content="{series}"/>'
                       f'<meta name="calibre:series_index" content="{index}"/>')
    opf = f'''<?xml version="1.0" encoding="utf-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="bid">
 <metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
  <dc:identifier id="bid">urn:uuid:dummy-{abs(hash(title))}</dc:identifier>
  <dc:title>{title}</dc:title>
  <dc:creator>{author}</dc:creator>
  <dc:language>en</dc:language>
  {series_meta}
 </metadata>
 <manifest>
  <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
  <item id="c1" href="c1.xhtml" media-type="application/xhtml+xml"/>
 </manifest>
 <spine><itemref idref="c1"/></spine>
</package>'''
    nav = ('<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml" '
           'xmlns:epub="http://www.idpf.org/2007/ops"><head><title>nav</title></head><body>'
           '<nav epub:type="toc"><ol><li><a href="c1.xhtml">Chapter One</a></li></ol></nav>'
           '</body></html>')
    ch = (f'<?xml version="1.0" encoding="utf-8"?><html xmlns="http://www.w3.org/1999/xhtml">'
          f'<head><title>{title}</title></head><body><h1>{title}</h1>'
          f'<p>Placeholder text generated for layout testing.</p></body></html>')
    with zipfile.ZipFile(path,'w') as z:
        # mimetype must be first and STORED — the one part of the format that is not just a zip.
        z.writestr(zipfile.ZipInfo('mimetype'), 'application/epub+zip', zipfile.ZIP_STORED)
        z.writestr('META-INF/container.xml',
            '<?xml version="1.0"?><container version="1.0" '
            'xmlns="urn:oasis:names:tc:opendocument:xmlns:container"><rootfiles>'
            '<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>'
            '</rootfiles></container>')
        z.writestr('OEBPS/content.opf', opf)
        z.writestr('OEBPS/nav.xhtml', nav)
        z.writestr('OEBPS/c1.xhtml', ch)

epub(bdir/'The Salt Almanac.epub', 'The Salt Almanac', 'Wren Halloway')
epub(bdir/'Lantern Volume One.epub', 'Lantern, Volume One', 'Ovid Marchetti', 'Lantern', 1)
epub(bdir/'Lantern Volume Two.epub', 'Lantern, Volume Two', 'Ovid Marchetti', 'Lantern', 2)

for d in sorted(ROOT.rglob('*')):
    if d.is_file(): pass
print(f"tree: {ROOT}")
for sub in ('Roms/GBA','Videos','Photos','Music','Books'):
    n = sum(1 for _ in (ROOT/sub).rglob('*') if _.is_file())
    print(f"  {sub:12s} {n:3d} files")
