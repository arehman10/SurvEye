# SurvEye 2.3.5 review notes

2.3.5 is a small release. One bug a user could see is fixed: the command
configurator now writes the theme you selected into the Stata command,
including `theme(worldbank)`. Dashboards show the same numbers as 2.3.4.

The rest of the work was on the release itself. The repository had drifted:
its `VERSION` file was two releases behind the command it shipped, three
engine jars sat side by side, and its automatic checks were not running. All
of that is repaired, and the engine is now tested on Java 8, 11, 17 and 21.

This is a **release candidate**. It passed every check that does not need a
Stata licence. Run `do tests/stata_release_smoke.do` in Stata before
distributing; `QA_REPORT.md` has the details and the limits.

Restart Stata after updating: a session that already loaded the old engine
keeps using it. Regenerate configurator pages made with 2.3.4 to get the
theme fix.

Earlier notes: `docs/history/REVIEW_NOTES-2.3.4.md` and older files beside it.
