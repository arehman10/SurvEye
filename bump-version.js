#!/usr/bin/env node
"use strict";
/*
 * Change the SurvEye version in every file that carries it, in one step.
 *
 *   node scripts/bump-version.js 2.3.6                  (uses today's date)
 *   node scripts/bump-version.js 2.3.6 --date 2026-12-01
 *   node scripts/bump-version.js --check                (no edits; verify only)
 *
 * Why this exists: the version lives in about a dozen places (ado header,
 * four r(package_version) returns, two JAR names, help header, package
 * descriptor, Java constant, manifest ...). Editing them by hand is how a
 * repository ends up with one number in VERSION while shipping another.
 *
 * Rules the script follows:
 *   1. Assert before patching. Every site must contain exactly the text the
 *      script expects, the expected number of times. If one does not, nothing
 *      is written and the script stops with the name of the site.
 *   2. After patching, search every tracked text file for the old version in
 *      a live position and fail if any is left.
 *
 * Rebuild afterwards (bash build.sh, or tests/build_windows.ps1) so that
 * surveye.jar and surveye_<version>.jar exist under the new name, and delete
 * the old surveye_<old>.jar.
 */
const fs = require("fs");
const path = require("path");

const ROOT = path.resolve(__dirname, "..");
const MONTHS = ["jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec"];
const read = (file) => fs.readFileSync(path.join(ROOT, file), "utf8");
const exists = (file) => fs.existsSync(path.join(ROOT, file));
const escape = (text) => text.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
const fail = (message) => { console.error("FAIL: " + message); process.exit(1); };

function parseArgs(argv) {
  const out = { check: false, version: null, date: null };
  for (let i = 0; i < argv.length; i++) {
    if (argv[i] === "--check") out.check = true;
    else if (argv[i] === "--date") out.date = argv[++i];
    else if (!out.version) out.version = argv[i];
    else fail("unexpected argument: " + argv[i]);
  }
  return out;
}

/* Every place the version is written. `count` is how many times the pattern
 * must match; `optional` files are skipped when absent (source-only bundles). */
function sites(version, stataDate, isoCompact) {
  const jar = "surveye_" + version.replace(/\./g, "_") + ".jar";
  const V = "(\\d+\\.\\d+\\.\\d+)";
  const J = "surveye_\\d+_\\d+_\\d+\\.jar";
  const D = "\\d{1,2}[a-z]{3}\\d{4}";
  return [
    { file: "VERSION", pattern: new RegExp("^" + V + "\\s*$"), count: 1, replace: version + "\n" },
    { file: "MANIFEST.MF", pattern: new RegExp("^Implementation-Version: " + V + "$", "m"), count: 1,
      replace: "Implementation-Version: " + version },
    { file: "src/SurvEye.java", pattern: new RegExp('String VERSION = "' + V + '";'), count: 1,
      replace: 'String VERSION = "' + version + '";' },
    { file: "surveye.ado", pattern: new RegExp("^\\*! version " + V + " " + D + "$", "m"), count: 1,
      replace: "*! version " + version + " " + stataDate },
    { file: "surveye.ado", pattern: new RegExp('return local package_version "' + V + '"', "g"), count: 4,
      replace: 'return local package_version "' + version + '"' },
    { file: "surveye.ado", pattern: new RegExp('local jarname "' + J + '"', "g"), count: 2,
      replace: 'local jarname "' + jar + '"' },
    { file: "surveye.sthlp", pattern: new RegExp("^\\{\\* \\*! version " + V + " +" + D + "\\}\\{\\.\\.\\.\\}$", "m"), count: 1,
      replace: "{* *! version " + version + "  " + stataDate + "}{...}" },
    { file: "surveye.pkg", pattern: new RegExp("^d Version: " + V + "$", "m"), count: 1, replace: "d Version: " + version },
    { file: "surveye.pkg", pattern: new RegExp("^F " + J + "$", "m"), count: 1, replace: "F " + jar },
    { file: "surveye.pkg", pattern: /^d Distribution-Date: \d{8}$/m, count: 1, replace: "d Distribution-Date: " + isoCompact },
    { file: "example.do", pattern: new RegExp("^\\*! SurvEye example " + V + " " + D + "$", "m"), count: 1,
      replace: "*! SurvEye example " + version + " " + stataDate },
    { file: "surveye_example.do", pattern: new RegExp("^\\*! SurvEye example " + V + " " + D + "$", "m"), count: 1,
      replace: "*! SurvEye example " + version + " " + stataDate },
    { file: "tests/package.json", pattern: new RegExp('"version": "' + V + '"'), count: 1, replace: '"version": "' + version + '"', optional: true },
    { file: "tests/stata_install.do", pattern: new RegExp("^findfile " + J + "$", "m"), count: 1, replace: "findfile " + jar, optional: true },
    { file: "tests/stata_install.do", pattern: new RegExp('r\\(package_version\\)\'"\'=="' + V + '"'), count: 1,
      replace: "r(package_version)'\"'==\"" + version + '"', optional: true },
  ];
}

function countMatches(text, pattern) {
  const flags = pattern.flags.includes("g") ? pattern.flags : pattern.flags + "g";
  return (text.match(new RegExp(pattern.source, flags)) || []).length;
}

/* Files where an old version number is history, not a live reference. */
const HISTORY = [/^CHANGELOG\.md$/, /^docs\//, /^examples\/.*\.html$/, /^tests\/stata_option_matrix\.do$/,
  /^tests\/OPTION_COVERAGE\.md$/, /^src\/resources\/(chart\.umd|leaflet)\.js$/, /^QA_REPORT\.md$/, /^REVIEW_NOTES\.md$/,
  /^build\//, /^node_modules\//, /^tests\/node_modules\//, /^\.git\//, /^release\//];
const TEXT = /\.(ado|sthlp|do|pkg|toc|md|MF|java|js|json|sh|ps1|yml|yaml|txt|tsv|css|py)$|^VERSION$/;

function walk(dir, out) {
  for (const entry of fs.readdirSync(path.join(ROOT, dir), { withFileTypes: true })) {
    const relative = dir ? dir + "/" + entry.name : entry.name;
    if (HISTORY.some((rule) => rule.test(relative) || rule.test(relative + "/"))) continue;
    if (entry.isDirectory()) walk(relative, out);
    else if (TEXT.test(relative)) out.push(relative);
  }
  return out;
}

function leftovers(oldVersion) {
  const dotted = new RegExp("(^|[^0-9.])" + escape(oldVersion) + "($|[^0-9.])");
  const underscored = new RegExp("surveye_" + escape(oldVersion.replace(/\./g, "_")) + "\\.jar");
  const hits = [];
  for (const file of walk("", [])) {
    read(file).split(/\r?\n/).forEach((line, index) => {
      if (underscored.test(line) || (dotted.test(line) && /version|Version|VERSION|surveye|SurvEye/.test(line))) {
        hits.push(file + ":" + (index + 1) + ": " + line.trim().slice(0, 120));
      }
    });
  }
  return hits;
}

function main() {
  const args = parseArgs(process.argv.slice(2));
  const current = read("VERSION").trim();
  if (!/^\d+\.\d+\.\d+$/.test(current)) fail("VERSION does not hold one major.minor.patch version: " + current);

  if (args.check) {
    const problems = [];
    for (const site of sites(current, "", "")) {
      if (!exists(site.file)) { if (!site.optional) problems.push(site.file + " is missing"); continue; }
      const text = read(site.file);
      const matches = text.match(new RegExp(site.pattern.source, site.pattern.flags.includes("g") ? site.pattern.flags : site.pattern.flags + "g")) || [];
      if (matches.length !== site.count) { problems.push(site.file + ": expected " + site.count + " match(es) of " + site.pattern + ", found " + matches.length); continue; }
      const versionLike = /Distribution-Date/.test(site.pattern.source) ? null : current;
      if (versionLike && !matches.every((m) => m.includes(current) || m.includes(current.replace(/\./g, "_")))) {
        problems.push(site.file + ": carries a version other than " + current + " -> " + matches.join(" | "));
      }
    }
    if (problems.length) fail("version drift\n  " + problems.join("\n  "));
    console.log("PASS version " + current + " is identical in all " + sites(current, "", "").length + " sites");
    return;
  }

  if (!args.version || !/^\d+\.\d+\.\d+$/.test(args.version)) fail("usage: node scripts/bump-version.js <major.minor.patch> [--date YYYY-MM-DD] | --check");
  if (args.version === current) fail("VERSION is already " + current);
  const date = args.date ? new Date(args.date + "T00:00:00Z") : new Date();
  if (isNaN(date)) fail("--date must be YYYY-MM-DD");
  const dd = String(date.getUTCDate()).padStart(2, "0");
  const stataDate = dd + MONTHS[date.getUTCMonth()] + date.getUTCFullYear();
  const isoCompact = date.getUTCFullYear() + String(date.getUTCMonth() + 1).padStart(2, "0") + dd;

  // Pass 1: assert every site before touching any file.
  const plan = sites(args.version, stataDate, isoCompact);
  const texts = {};
  for (const site of plan) {
    if (!exists(site.file)) { if (site.optional) continue; fail("required file is missing: " + site.file); }
    if (!(site.file in texts)) texts[site.file] = read(site.file);
    const found = countMatches(texts[site.file], site.pattern);
    if (found !== site.count) fail(site.file + ": expected " + site.count + " match(es) of " + site.pattern + ", found " + found + ". Nothing was changed.");
  }
  // Pass 2: patch in memory, then write.
  for (const site of plan) {
    if (!(site.file in texts)) continue;
    const flags = site.pattern.flags.includes("g") ? site.pattern.flags : site.pattern.flags + "g";
    texts[site.file] = texts[site.file].replace(new RegExp(site.pattern.source, flags), site.replace);
  }
  for (const file of Object.keys(texts)) fs.writeFileSync(path.join(ROOT, file), texts[file]);

  const left = leftovers(current);
  if (left.length) fail("old version " + current + " is still referenced:\n  " + left.join("\n  "));
  console.log("Bumped " + current + " -> " + args.version + " (" + stataDate + ") in " + Object.keys(texts).length + " files; 0 live references to " + current + " remain.");
  console.log("Next: rebuild the JARs, delete surveye_" + current.replace(/\./g, "_") + ".jar, add a CHANGELOG entry, run the test gate.");
}

main();
