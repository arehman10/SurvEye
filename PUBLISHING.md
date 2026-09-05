# Publishing SurvEye

> This is an unpublished 2.3.1 local review candidate. Publishing instructions
> below are for a later, separately authorized release. `release.sh` only
> creates local archives and does not perform any remote action.


Use this checklist for releases from the permanent public repository:

<https://github.com/arehman10/SurvEye>

## 1. Confirm public package metadata

- Keep the repository and issue URLs synchronized in `README.md`,
  `surveye.sthlp`, and `surveye.pkg`.
- Keep the release date synchronized in `surveye.pkg`, `surveye.sthlp`, and
  `CHANGELOG.md`.
- Confirm that the `surveye` command/package name is available at SSC.

## 2. Run the portable release gate

From the repository root:

```bash
bash ./build.sh
sh ./tests/check_stata_source.sh
sh ./tests/check_package.sh
node tests/test_statistics.js
sh ./tests/run_engine_smoke.sh
sh ./tests/run_parser_tests.sh
sh ./tests/run_surveycto_tests.sh
sh ./tests/test_appearance.sh
```

## 3. Run the licensed-Stata gate

Use Stata 16 and the current supported Stata release. The smoke file must use
the source directory explicitly so an older installed command cannot mask the
candidate release:

```stata
do "tests/stata_smoke.do" "C:/path/to/surveye"
```

Also test an installation from the staged source directory (which includes `surveye.pkg`) in a clean ado-path:

```stata
net install surveye, from("C:/path/to/release/surveye-2.3.1-github") replace
discard
which surveye
help surveye
```

Build at least one real, one weighted, one Arabic/Urdu RTL, and one map-enabled
dashboard in Stata. Open each generated file in the supported browsers. Confirm
that default output contains no CI text or whiskers, then rerun an eligible
categorical or multiselect bar with `ci level(90)`. Binary yes/no,
answered/missing completion, and donut cards must remain CI-free even when
`ci` is supplied.

For every dashboard, confirm that the search/filter toolbar starts collapsed,
does not cover the figures on a short desktop viewport, opens with Enter and
Space, closes with Escape, and preserves active choices. Test Reset all while
the body is collapsed: it must clear both response filters and indicator search.
Check the expanded state for horizontal overflow in LTR and RTL layouts and
verify that charts and an open Leaflet map remain correctly sized afterward.

## 4. Confirm map-provider policy

The Google choices mirror the keyless compatibility endpoints used by
`esqc_gps`; they are not an authenticated Google Maps Platform integration.
Confirm permitted use, attribution, and organizational policy for the intended
release. Keep `basemap(osm)` documented as an alternative and review the
OpenStreetMap tile-use policy as well. Current primary references are the
[Google Map Tiles API documentation](https://developers.google.com/maps/documentation/tile)
and the [OpenStreetMap tile-use policy](https://operations.osmfoundation.org/policies/tiles/).

## 5. Create clean archives

```bash
bash ./release.sh /path/to/release
```

This creates:

- `surveye-2.3.1.zip` — full source, tests, documentation, example, and JARs;
- `surveye-2.3.1-github.zip` — the same clean source with its contents flat at
  the archive root, ready to upload to the GitHub repository root;
- `surveye-2.3.1-ssc.zip` — only the flat installable package; and
- matching inspectable staging directories.

The script builds from source, verifies the exact flat SSC inventory before
writing any archive, excludes retired installable filenames even if they
remain in a developer's working directory, and prints SHA-256 checksums.

## 6. Publish to GitHub

- Read `GITHUB_UPLOAD.md`.
- Upload the *contents* of `surveye-2.3.1-github.zip` to the root of the
  `main` branch. Do not upload only the ZIP and do not add an enclosing folder.
- Make the repository public before testing unauthenticated installation.
- Confirm that both raw metadata URLs return plain text:
  - <https://raw.githubusercontent.com/arehman10/SurvEye/main/stata.toc>
  - <https://raw.githubusercontent.com/arehman10/SurvEye/main/surveye.pkg>
- Test from a fresh Stata session:

```stata
net install surveye, from("https://raw.githubusercontent.com/arehman10/SurvEye/main/") replace
discard
which surveye
findfile surveye_2_3_1.jar
help surveye
```

## 7. Create the formal release and submit to SSC

- Create an annotated `v2.3.1` tag and GitHub release.
- Attach the source and SSC archives and publish the checksums shown by the
  release workflow.
- Submit per the official instructions
  (http://repec.org/bocode/s/sscsubmit.html): email the flat zip built by
  `release.sh` (`surveye-2.3.1-ssc.zip`) to the archive maintainer
  (baum@bc.edu). The zip must contain only `surveye.ado`, `surveye.sthlp`,
  `surveye_2_3_1.jar`, `example.do`, `LICENSE`, and
  `THIRDPARTY-LICENSES.md` — never `surveye.pkg` or `stata.toc` (the
  archive generates both) and never the generic `surveye.jar` (a GitHub
  convenience the command does not load).
- The email must state that the submission is new, suggest the package name
  (`surveye`), give the title line and abstract, name the author,
  affiliation, and contact email, declare no dependencies on other SSC
  packages, and ask that `surveye_2_3_1.jar` be installed together with the
  ado by `net install` (the command loads it from the ado-path through
  Stata's Java integration); `example.do`, `LICENSE`, and
  `THIRDPARTY-LICENSES.md` can stay ancillary. A ready-to-edit draft ships
  as `ssc_submission_email.txt` in the release staging output.
- SSC also requires that commands run under `set varabbrev off`;
  `tests/stata_smoke.do` sets it, so the licensed-Stata smoke run doubles as
  that check. After the maintainer confirms, announce on Statalist as is
  customary.
