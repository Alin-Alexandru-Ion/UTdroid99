# Preparing your GitHub repository

The screened source snapshot is produced locally with:

```powershell
py -3 scripts/prepare_source_review.py --output research/source-review.zip --game-dir <your-local-ut99-folder>
```

Use a new output path. This is a heuristic screen: review the archive contents,
third-party notices, source provenance and applicable terms before publishing.
The game folder is only used for hash comparisons and is not copied. Never add
`research/`, generated APKs, game files, patch archives, keys or personal reports
to source control. The provided `.gitignore` is a safeguard, not a complete audit.

Create an empty GitHub repository and extract the reviewed source snapshot into a
new folder. Then from that folder, configure your Git identity if needed and run:

```powershell
git init -b main
git add .
git diff --cached --stat
git commit -m "Initial UTdroid99 source"
git remote add origin <your-repository-url>
git push -u origin main
```

The source snapshot is distinct from a release APK. The direct-rehost APK must
be independently reviewed with its matching SDL/audio source, licence notices,
replacement and signing instructions. A debug-signed local APK is for testing;
it is not an established redistributable release.
