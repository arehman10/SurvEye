# Publishing SurvEye on GitHub

This repository is a Stata `net install` source. Its package metadata and every
file named by that metadata must be present at the repository root.

## Recommended upload

1. Run `./release.sh /path/to/release`.
2. Extract `surveye-2.1.3-github.zip`.
3. Upload the **contents** of the extracted archive to the root of
   `arehman10/SurvEye` on the `main` branch.
4. Replace the existing root files when GitHub asks. Do not upload the ZIP by
   itself and do not retain an enclosing `surveye-2.1.3-github` folder.
5. Make the repository public. Stata cannot authenticate to a private GitHub
   raw-content URL during a normal `net install`.

At minimum, the root must contain:

```text
README.md
stata.toc
surveye.pkg
surveye.ado
surveye.sthlp
surveye.jar
surveye_2_1_3.jar
example.do
LICENSE
THIRDPARTY-LICENSES.md
CHANGELOG.md
```

The GitHub-ready archive also includes the Java source, tests, workflow, and
release documentation. Retired `suso_dashboard*.jar` and `surveydash*.jar`
files are deliberately excluded.

## Verify the public files

These addresses must open as plain text without signing in:

- <https://raw.githubusercontent.com/arehman10/SurvEye/main/stata.toc>
- <https://raw.githubusercontent.com/arehman10/SurvEye/main/surveye.pkg>

If either address returns 404, check repository visibility, branch name, file
location, and letter casing.

## Test installation

Start a fresh Stata session and run:

```stata
net install surveye, from("https://raw.githubusercontent.com/arehman10/SurvEye/main/") replace
discard
which surveye
findfile surveye_2_1_3.jar
help surveye
```

Then perform a minimal functional check:

```stata
surveye demo using "questionnaire.html", ///
    saving("surveye_preview.html") n(100) seed(42) replace open
```

If the command was used before reinstalling, fully restart Stata before the
functional check. Stata's JVM can retain an already loaded JAR until the
application exits.

## Updating a release

Rebuild and rerun the complete checks before every upload. Keep the version and
date synchronized in `surveye.pkg`, `surveye.sthlp`, and `CHANGELOG.md`. After
publishing, test installation again from GitHub rather than relying only on the
local source directory.
