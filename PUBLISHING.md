# Publishing SurvEye

A release goes to two places: the GitHub repository (the `net install`
source and the code of record) and SSC (`ssc install surveye`). This page is
the whole procedure. It names no version on purpose: the version comes from
the `VERSION` file.

Permanent repository: <https://github.com/arehman10/SurvEye>
SSC entry: `RePEc:boc:bocode:s459823`

## 1. Set the version

```bash
node scripts/bump-version.js 2.4.0          # example; uses today's date
```

This edits all 15 places the version is written, or stops without changing
anything if one of them does not look as expected. Then add a section to
`CHANGELOG.md` in words a user understands.

## 2. Build

```bash
bash build.sh                               # Linux, macOS, Git Bash
./tests/build_windows.ps1                   # Windows PowerShell 7
```

Both write `surveye.jar` and `surveye_<version>.jar` (identical files) and
print the file sha256 and the content fingerprint. Delete the previous
`surveye_<old version>.jar`; the gate fails while a stale one is present.

## 3. Run the gates

Portable gate (no Stata needed):

```bash
sh tests/run_all.sh
sh tests/run_jvm_matrix.sh                  # every installed Java runtime
```

On Windows: `./tests/run_windows_tests.ps1`.

Licensed-Stata gate, from the repository root on a machine with Stata:

```powershell
./tests/run_stata_windows.ps1 -Suite all
node tests/verify_stata_outputs.js
node tests/test_generated_payloads.js
node tests/verify_stata_quotes.js
```

The portable gate proves the engine and the browser code. Only the Stata gate
proves the command itself. A release that has passed only the first is a
release candidate; say so in `QA_REPORT.md`.

Open at least one generated dashboard in a browser: one weighted, one Arabic
or Urdu, one with a map.

## 4. Confirm map-provider policy

The Google base-map choices use keyless compatibility endpoints, not an
authenticated Google Maps Platform integration. Confirm permitted use and
attribution for the intended audience, and keep `basemap(osm)` documented as
the alternative. References:
[Google Map Tiles API](https://developers.google.com/maps/documentation/tile),
[OpenStreetMap tile-use policy](https://operations.osmfoundation.org/policies/tiles/).

## 5. Create the archives

```bash
bash release.sh /path/to/release
```

This rebuilds, reruns the portable gate, and writes:

- `surveye-<version>.zip`: full source in a folder;
- `surveye-<version>-github.zip`: the same tree, flat, for the repository root;
- `surveye-<version>-ssc.zip`: only the files SSC installs;
- `SHA256SUMS.txt`: checksums of the archives and jars, plus the jar's
  content fingerprint.

## 6. Publish to GitHub

Preferred: a normal commit, because git also removes files.

```bash
git clone https://github.com/arehman10/SurvEye.git && cd SurvEye
# replace the working tree with the contents of surveye-<version>-github.zip
git add -A && git status        # expect the old versioned jar as deleted
git commit -m "SurvEye <version>" && git push
```

If you use the web page's **Add file → Upload files** instead, read
[GITHUB_UPLOAD.md](GITHUB_UPLOAD.md) first: the web upload never deletes, so
retired files stay behind unless you remove them by hand.

Then check, in a fresh Stata session:

```stata
net install surveye, from("https://raw.githubusercontent.com/arehman10/SurvEye/main/") replace
discard
which surveye
help surveye
```

`which surveye` must show the new version. The **Actions** tab must show a
green `build` run for the commit. Tag the commit `v<version>` and attach the
archives and `SHA256SUMS.txt` to a GitHub release.

## 7. Update SSC

SSC already carries `surveye`. Email `surveye-<version>-ssc.zip` to the
archive maintainer (Christopher F. Baum, baum@bc.edu) as an **update to the
existing package**, and say:

- the version and date;
- that `surveye_<old version>.jar` is replaced by `surveye_<version>.jar`,
  and that both jars in the archive must be installed with the ado-file
  (the command loads the versioned one from the ado-path through Stata's
  Java integration);
- that `surveye_example.do`, `LICENSE` and `THIRDPARTY-LICENSES.md` are
  ancillary;
- two or three lines on what changed, taken from `CHANGELOG.md`.

The archive contains no `surveye.pkg` or `stata.toc`; SSC writes its own.
After the maintainer confirms, check `ssc install surveye, replace` and
`which surveye` in a fresh session.
