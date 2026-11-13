"""Create a private emulator fixture from the locally owned game and pinned patch."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED, ZIP_STORED

root = Path(__file__).resolve().parents[2]
research = root / "research"
patch = research / "linux-unmodified-import-test/stage"
game = research / "pc-reference"
out = research / "android-exec-probe/direct-network-fixture.zip"
gameid = "game-00000000-0000-4000-8000-000000000001"
patchid = "patch-00000000-0000-4000-8000-000000000002"
with ZipFile(out, "w", compression=ZIP_STORED, allowZip64=True) as z:
    def add_tree(source, dest, skip=()):
        for f in source.rglob('*'):
            if f.is_file():
                rel = f.relative_to(source).as_posix()
                if rel.lower() not in skip: z.write(f, dest + rel)
    overrides = {p.relative_to(patch / 'game').as_posix().lower() for p in (patch / 'game').rglob('*') if p.is_file()}
    add_tree(game, f'files/game-imports/{gameid}/', overrides)
    add_tree(patch / 'game', f'files/game-imports/{gameid}/')
    z.writestr(f'files/game-imports/{gameid}/.ut99-import-complete', b'')
    add_tree(patch, f'files/patch-imports/{patchid}/prepared/')
    z.writestr(f'files/patch-imports/{patchid}/prepared/.patch-complete', b'')
    with ZipFile(research / 'engine-free-support-v1.zip') as support:
        for name in support.namelist(): z.writestr('files/core-load/' + name, support.read(name))
    for p in (patch / 'runtime').iterdir():
        if p.is_file() and p.name not in {'libmpg123.so', 'libsndfile.so.1', 'libxmp.so.4'}:
            z.write(p, 'files/core-load/' + p.name)
    add_tree(patch / 'runtime/Fonts', 'files/core-load/Fonts/')
    z.write(patch / 'runtime/Default.ini', 'files/core-load/UnrealTournament.ini')
    z.write(patch / 'runtime/DefUser.ini', 'files/core-load/User.ini')
    z.writestr('shared_prefs/phone-storage.xml', f'<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><string name="import-id">{gameid}</string></map>')
    z.writestr('shared_prefs/patch-storage.xml', f'<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><string name="patch-id">{patchid}</string></map>')
    z.writestr('shared_prefs/setup-acknowledgment.xml', '<?xml version="1.0" encoding="utf-8" standalone="yes" ?><map><boolean name="compatibility-v1" value="true" /></map>')
print(out, out.stat().st_size)
