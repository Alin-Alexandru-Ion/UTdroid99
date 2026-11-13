"""Assemble ignored, private emulator data from the user's existing install and pinned patch."""
import hashlib
import pathlib
import tarfile
import zipfile

root = pathlib.Path(__file__).resolve().parents[1]
research = root / 'research'
original = research / 'pc-reference'
patch = research / 'OldUnreal-UTPatch469e-Linux-arm64.tar.bz2'
destination = research / 'private-content.zip'
expected = '4c3978073b12b049c3ffdeb4d275cfc7a2313650f3eb5b94db06fbfee77c3e3b'
with patch.open('rb') as f:
    digest=hashlib.file_digest(f,'sha256').hexdigest()
if digest != expected: raise SystemExit('Patch archive hash mismatch')
if not (original/'Maps/CityIntro.unr').is_file(): raise SystemExit('User game content missing')
extensions = {'.unr','.umx','.uax','.utx'}
with tarfile.open(patch,'r:bz2') as archive:
    selected={}
    for m in archive:
        p=pathlib.PurePosixPath(m.name)
        if not m.isfile() or '..' in p.parts: continue
        if len(p.parts)==2 and p.parts[0]=='System' and p.suffix.lower()=='.u': selected['System/'+p.name]=m
        # The v469e UWindow scripts require the matching larger Tahoma fonts;
        # the older GOTY texture package does not contain them.
        if len(p.parts)==2 and p.parts[0]=='Textures' and p.name=='UWindowFonts.utx': selected['Textures/'+p.name]=m
        if len(p.parts)==3 and p.parts[:2]==('SystemLocalized','int') and p.suffix.lower()=='.int': selected['System/'+p.name]=m
        if len(p.parts)==2 and p.parts[0]=='SystemARM64' and (p.name.endswith('.so') or '.so.' in p.name) and p.name not in ('Core.so','Engine.so'):
            selected['core-load/'+p.name]=m
    with zipfile.ZipFile(destination,'w',compression=zipfile.ZIP_STORED,allowZip64=True) as z:
        for folder in ('Maps','Music','Sounds','Textures','System'):
            base=original/folder
            for f in sorted(base.iterdir()):
                if not f.is_file(): continue
                if folder=='System' and f.suffix.lower() not in ('.u','.int'): continue
                if folder!='System' and f.suffix.lower() not in extensions: continue
                name=f'{folder}/{f.name}'
                if name not in selected: z.write(f,name)
        for name,member in selected.items():
            with archive.extractfile(member) as src, z.open(name,'w',force_zip64=True) as out:
                while block := src.read(1024*1024): out.write(block)
print(f'Private content bundle {destination} ({destination.stat().st_size} bytes)')
