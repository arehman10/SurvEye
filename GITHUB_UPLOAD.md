# Updating the GitHub repository through the web page

Use this page only if you publish with **Add file → Upload files**. A normal
`git` commit (see [PUBLISHING.md](PUBLISHING.md)) is safer, because git
removes retired files for you.

## The one thing to know

**The web upload adds and overwrites. It never deletes.** After an upload,
every file that the new release no longer contains is still in the
repository. That is how the repository once held three engine jars and a
`VERSION` file two releases behind the command it shipped.

## Steps

1. Run `bash release.sh /path/to/release` and extract
   `surveye-<version>-github.zip`.
2. On GitHub, open the repository root, choose **Add file → Upload files**,
   and drag in the **contents** of the extracted folder (not the folder, not
   the ZIP). Commit.
   - Folders whose names start with a dot are hidden in most file browsers.
     Make sure `.github` and `.gitignore` are included, or the automatic
     checks will not run.
3. Delete what the release retired. For each file: open it, choose the bin
   icon (**Delete file**), commit.
   - every `surveye_<older version>.jar` at the root (only the jar named
     after the current `VERSION` belongs there);
   - anything the release notes list under "delete from the repository".
4. Open the **Actions** tab. The `build` run for your last commit must be
   green. Its first steps fail with a plain message if a stale jar or a
   mismatched version is still present.

## Check from Stata

```stata
net install surveye, from("https://raw.githubusercontent.com/arehman10/SurvEye/main/") replace
discard
which surveye
```

`which surveye` must print the new version. Restart Stata before building a
dashboard: a Stata session that already loaded the old engine keeps using it.

## What must be at the repository root

`README.md`, `stata.toc`, `surveye.pkg`, `surveye.ado`, `surveye.sthlp`,
`surveye.jar`, `surveye_<version>.jar`, `example.do`, `surveye_example.do`,
`LICENSE`, `THIRDPARTY-LICENSES.md`, `CHANGELOG.md`, `QA_REPORT.md`,
`REVIEW_NOTES.md`, `VERSION`. Stata reads `stata.toc` and `surveye.pkg` from
the root, and every file `surveye.pkg` names must sit beside them.
