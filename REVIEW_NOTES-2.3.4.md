# SurvEye 2.3.4 review notes

This candidate contains fixes identified by licensed Stata testing on Windows, live Chrome testing, source review, and independent checks of generated HTML/JavaScript. Read `QA_REPORT.md` for the evidence, reproduction commands, and environment limits.

The source archive supplied for this review remains unchanged. Corrected files are in the extracted source folder. Older release JARs were preserved under `build/qa_windows/original-artifacts/`; only the current version belongs in the runtime package.

Use the local tested package for these fixes. No GitHub push or SSC submission was performed. Restart Stata after replacing a previously installed package. All five themes were checked in Chrome at desktop and phone widths. Older supported Stata versions, other operating systems/browser engines, and exhaustive accessibility testing remain outside the verified scope.

The earlier review notes are preserved in `docs/history/REVIEW_NOTES-2.3.2-original.md` and `docs/history/REVIEW_NOTES-2.3.3.md`.
