# Public synthetic fixtures

These compact questionnaires were authored for the portable regression suite.
They are not official survey instruments and contain no respondent data.
The filenames deliberately identify them as synthetic.

| Fixture | Contracts exercised |
|---|---|
| `synthetic-australia.html` | Leading-zero category codes, special codes, computed-variable exclusion, roster/group boundaries, categorical/binary/percentage rendering, custom fields, and the GPS bridge |
| `synthetic-informal-en.html` | Hidden scope, static battery prompts, suffix grouping, and numeric plots |
| `synthetic-informal-si.html` | The same question/type/category structure in Sinhala, with preserved zero-width joiners |
| `synthetic-trg.html` | Critical-marker stripping and markdown link-label cleaning |

Run from the repository root:

```bash
sh ./tests/run_parser_tests.sh
sh ./tests/run_engine_smoke.sh
```

Missing fixtures fail the tests. The engine no longer falls back to a smaller
test that leaves mapping and custom-variable contracts unexercised.

The original large private questionnaires are not redistributed. To run their
historical counts and the same structural contracts, supply both arguments:

```bash
sh ./tests/run_parser_tests.sh /path/to/private-questionnaires tests/private_parser_expected.tsv
sh ./tests/run_engine_smoke.sh /path/to/private-questionnaires
```

Passing the public suite does not certify those unpublished questionnaires.
