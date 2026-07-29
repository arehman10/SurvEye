#!/usr/bin/env node
"use strict";

/**
 * Visual/browser QA for a generated SurvEye dashboard.
 *
 * Usage:
 *   node visual_qa.js dashboard.html
 *   node visual_qa.js dashboard.html --out ./qa-results
 *
 * The page is opened as a local file. Expected Leaflet basemap tile requests are
 * intercepted and aborted for deterministic rendering; any other HTTP(S)
 * request remains a failure.
 */

const fs = require("node:fs");
const fsp = fs.promises;
const os = require("node:os");
const path = require("node:path");
const { pathToFileURL } = require("node:url");
const { createBrotliDecompress } = require("node:zlib");
const { pipeline } = require("node:stream/promises");

const VIEWPORTS = [
  { name: "desktop", width: 1440, height: 1000, deviceScaleFactor: 1, isMobile: false, hasTouch: false },
  { name: "desktop-short", width: 1536, height: 640, deviceScaleFactor: 1, isMobile: false, hasTouch: false },
  { name: "tablet", width: 1024, height: 1366, deviceScaleFactor: 1, isMobile: false, hasTouch: true },
  { name: "mobile", width: 390, height: 844, deviceScaleFactor: 1, isMobile: true, hasTouch: true },
];

const MODULE_ROOT = process.env.SURVEYE_QA_NODE_MODULES || path.resolve(__dirname, "../../work/npm/node_modules");
const CHROMIUM_CACHE = process.env.SURVEYE_QA_CHROMIUM_CACHE || path.join(os.tmpdir(), "surveye-qa-chromium");

// @sparticuz/chromium extracts into os.tmpdir(). A dedicated location avoids
// collisions with other Chromium versions and makes repeat runs much faster.
process.env.TMPDIR = CHROMIUM_CACHE;

function loadDependency(name) {
  try {
    return require(name);
  } catch (firstError) {
    try {
      return require(path.join(MODULE_ROOT, name));
    } catch (secondError) {
      const error = new Error(
        `Cannot load ${name}. Set SURVEYE_QA_NODE_MODULES to the node_modules directory.\n` +
        `Tried the normal Node search path and ${MODULE_ROOT}.`
      );
      error.cause = secondError;
      throw error;
    }
  }
}

const puppeteer = loadDependency("puppeteer-core");
const chromium = loadDependency("@sparticuz/chromium");
const tar = loadDependency("tar-fs");

function printUsage() {
  process.stdout.write(
    [
      "Browser QA for a generated Survey Solutions dashboard",
      "",
      "Usage:",
      "  node visual_qa.js <dashboard.html> [--out <directory>] [--timeout <ms>] [--soft]",
      "",
      "Options:",
      "  --out      Screenshot/report directory (default: tests/qa-output/<html-name>)",
      "  --timeout  Navigation timeout in milliseconds (default: 30000)",
      "  --soft     Write all findings but return exit code 0 even when checks fail",
      "  --help     Show this help",
      "",
      "Environment:",
      "  CHROME_PATH              Optional existing Chrome/Chromium executable",
      "  SURVEYE_QA_NODE_MODULES   Directory containing the required npm packages",
      "  SURVEYE_QA_CHROMIUM_CACHE Extraction cache for @sparticuz/chromium",
      "",
    ].join("\n")
  );
}

function parseArguments(argv) {
  const result = { html: null, out: null, timeout: 30000, soft: false, help: false };
  const positional = [];
  for (let index = 0; index < argv.length; index += 1) {
    const argument = argv[index];
    if (argument === "--help" || argument === "-h") {
      result.help = true;
    } else if (argument === "--soft") {
      result.soft = true;
    } else if (argument === "--out") {
      if (index + 1 >= argv.length) throw new Error("--out requires a directory");
      result.out = argv[++index];
    } else if (argument === "--timeout") {
      if (index + 1 >= argv.length) throw new Error("--timeout requires milliseconds");
      result.timeout = Number(argv[++index]);
      if (!Number.isInteger(result.timeout) || result.timeout < 1000) {
        throw new Error("--timeout must be an integer of at least 1000 milliseconds");
      }
    } else if (argument.startsWith("-")) {
      throw new Error(`Unknown option: ${argument}`);
    } else {
      positional.push(argument);
    }
  }
  if (positional.length > 1) throw new Error("Pass exactly one HTML path");
  result.html = positional[0] || null;
  return result;
}

function safeName(value) {
  const cleaned = value.normalize("NFKD").replace(/[^A-Za-z0-9._-]+/g, "-").replace(/^-+|-+$/g, "");
  return cleaned || "dashboard";
}

function delay(milliseconds) {
  return new Promise((resolve) => setTimeout(resolve, milliseconds));
}

async function extractTarBrotli(archive, destination, marker) {
  if (fs.existsSync(marker)) return;
  await fsp.mkdir(destination, { recursive: true });
  const extractor = tar.extract(destination, { chown: false, readable: true, writable: true });
  await pipeline(fs.createReadStream(archive), createBrotliDecompress(), extractor);
  await fsp.writeFile(marker, "ok\n", "utf8");
}

async function chromiumExecutable() {
  if (process.env.CHROME_PATH) {
    const configured = path.resolve(process.env.CHROME_PATH);
    const stat = await fsp.stat(configured).catch(() => null);
    if (!stat || !stat.isFile()) throw new Error(`CHROME_PATH does not point to a file: ${configured}`);
    return configured;
  }

  await fsp.mkdir(CHROMIUM_CACHE, { recursive: true });
  const bundledBinary = path.join(CHROMIUM_CACHE, "chromium");
  const current = await fsp.stat(bundledBinary).catch(() => null);
  if (current && current.size < 1000000) await fsp.rm(bundledBinary, { force: true });

  // tar-fs preserves archive ownership when run as root. Some managed
  // containers prohibit chown, so extract the two tarballs explicitly with
  // chown disabled before asking @sparticuz/chromium for its executable.
  const packageBin = path.join(MODULE_ROOT, "@sparticuz", "chromium", "bin");
  await extractTarBrotli(
    path.join(packageBin, "fonts.tar.br"),
    path.join(CHROMIUM_CACHE, "fonts"),
    path.join(CHROMIUM_CACHE, "fonts", ".qa-extracted")
  );
  await extractTarBrotli(
    path.join(packageBin, "swiftshader.tar.br"),
    CHROMIUM_CACHE,
    path.join(CHROMIUM_CACHE, ".swiftshader-qa-extracted")
  );

  // Keep SwiftShader enabled: Canvas-based charts and maps can crash the
  // serverless Chromium build when its graphics stack is forcibly disabled.
  chromium.setGraphicsMode = true;
  const executable = await chromium.executablePath(packageBin);
  const stat = await fsp.stat(executable).catch(() => null);
  if (!stat || stat.size < 1000000) throw new Error(`Chromium extraction produced an invalid executable: ${executable}`);
  return executable;
}

function isExternalUrl(rawUrl) {
  try {
    const protocol = new URL(rawUrl).protocol;
    return !["file:", "data:", "blob:", "about:", "javascript:", "chrome:"].includes(protocol);
  } catch (_) {
    return true;
  }
}

function isExpectedBasemapTileUrl(rawUrl) {
  try {
    const url = new URL(rawUrl);
    if (url.protocol !== "https:") return false;
    if (/^mt[0-3]\.google\.com$/i.test(url.hostname)) {
      return /^\/vt\/lyrs=[ysm]&x=-?\d+&y=-?\d+&z=\d+$/.test(url.pathname);
    }
    if (/^[abc]\.tile\.openstreetmap\.org$/i.test(url.hostname)) {
      return /^\/\d+\/-?\d+\/-?\d+\.png$/.test(url.pathname);
    }
    return false;
  } catch (_) {
    return false;
  }
}

function compact(items, limit = 20) {
  const seen = new Set();
  const unique = [];
  for (const item of items) {
    const key = JSON.stringify(item);
    if (seen.has(key)) continue;
    seen.add(key);
    unique.push(item);
  }
  return {
    count: unique.length,
    items: unique.slice(0, limit),
    truncated: unique.length > limit,
  };
}

async function settle(page, milliseconds = 180) {
  await page.evaluate(async () => {
    if (document.fonts && document.fonts.ready) await document.fonts.ready;
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
  });
  await delay(milliseconds);
}

async function setControlsExpanded(page, expanded) {
  const changed = await page.evaluate((target) => {
    const toggle = document.getElementById("controls-toggle");
    const body = document.getElementById("controls-body");
    if (!toggle || !body) return false;
    const current = toggle.getAttribute("aria-expanded") === "true";
    if (current === target) return false;
    toggle.click();
    return true;
  }, expanded);
  if (changed) await settle(page, 80);
}

async function ensureControlsExpanded(page) {
  await setControlsExpanded(page, true);
}

async function testControlsPanel(page, viewport) {
  const initial = await page.evaluate(() => {
    const controls = document.getElementById("dashboard-controls");
    const toggle = document.getElementById("controls-toggle");
    const body = document.getElementById("controls-body");
    if (!controls || !toggle || !body) return null;
    const rect = controls.getBoundingClientRect();
    return {
      expanded: toggle.getAttribute("aria-expanded"),
      target: toggle.getAttribute("aria-controls"),
      type: toggle.getAttribute("type"),
      name: toggle.textContent.trim(),
      bodyHidden: body.hidden,
      bodyDisplay: getComputedStyle(body).display,
      height: rect.height,
      position: getComputedStyle(controls).position,
      activeCount: document.getElementById("active-filter-count")?.dataset.activeCount || null,
    };
  });
  if (!initial) return { status: "failed", pass: false, reason: "expandable controls are missing" };

  await page.focus("#controls-toggle");
  await page.keyboard.press("Enter");
  await settle(page, 80);
  const entered = await page.evaluate(() => ({
    expanded: document.getElementById("controls-toggle")?.getAttribute("aria-expanded"),
    hidden: document.getElementById("controls-body")?.hidden,
    height: document.getElementById("dashboard-controls")?.getBoundingClientRect().height || 0,
    focus: document.activeElement?.id || null,
  }));
  await page.keyboard.press("Space");
  await settle(page, 80);
  const spaced = await page.evaluate(() => ({
    expanded: document.getElementById("controls-toggle")?.getAttribute("aria-expanded"),
    hidden: document.getElementById("controls-body")?.hidden,
    focus: document.activeElement?.id || null,
  }));
  await ensureControlsExpanded(page);

  const chip = await page.$(".chip[data-filter]");
  let preserved = { tested: false, pass: true };
  if (chip) {
    await page.evaluate((element) => element.click(), chip);
    await settle(page, 100);
    const selected = await page.evaluate((element) => ({
      pressed: element.getAttribute("aria-pressed"),
      matched: document.querySelector(".matched [data-matched]")?.textContent?.trim() || null,
      activeCount: document.getElementById("active-filter-count")?.dataset.activeCount || null,
      badgeHidden: document.getElementById("active-filter-count")?.hidden,
      resetDisabled: document.getElementById("reset")?.disabled,
    }), chip);
    await setControlsExpanded(page, false);
    const collapsed = await page.evaluate((element) => ({
      pressed: element.getAttribute("aria-pressed"),
      matched: document.querySelector(".matched [data-matched]")?.textContent?.trim() || null,
      activeCount: document.getElementById("active-filter-count")?.dataset.activeCount || null,
      bodyHidden: document.getElementById("controls-body")?.hidden,
    }), chip);
    await page.click("#reset");
    await settle(page, 100);
    const reset = await page.evaluate((element) => ({
      pressed: element.getAttribute("aria-pressed"),
      activeCount: document.getElementById("active-filter-count")?.dataset.activeCount || null,
      badgeHidden: document.getElementById("active-filter-count")?.hidden,
      resetDisabled: document.getElementById("reset")?.disabled,
    }), chip);
    preserved = {
      tested: true,
      pass: selected.pressed === "true" && Number(selected.activeCount) > 0 && !selected.badgeHidden && !selected.resetDisabled &&
        collapsed.pressed === selected.pressed && collapsed.matched === selected.matched &&
        collapsed.activeCount === selected.activeCount && collapsed.bodyHidden === true &&
        reset.pressed === "false" && reset.activeCount === "0" && reset.badgeHidden && reset.resetDisabled,
      selected, collapsed, reset,
    };
    await ensureControlsExpanded(page);
  }

  await page.focus("#indicator-search");
  await page.keyboard.press("Escape");
  await settle(page, 60);
  const focusReturn = await page.evaluate(() => {
    const toggle = document.getElementById("controls-toggle");
    const body = document.getElementById("controls-body");
    return Boolean(toggle && body && document.activeElement === toggle &&
      toggle.getAttribute("aria-expanded") === "false" && body.hidden);
  });
  await ensureControlsExpanded(page);

  const collapsedLimit = viewport.width <= 600 ? 96 : 72;
  const responsivePosition = viewport.width <= 900 ? initial.position !== "sticky" : initial.position === "sticky";
  const pass = initial.expanded === "false" && initial.target === "controls-body" && initial.type === "button" &&
    Boolean(initial.name) && initial.bodyHidden && initial.bodyDisplay === "none" && initial.height <= collapsedLimit &&
    initial.activeCount === "0" && responsivePosition && entered.expanded === "true" && !entered.hidden &&
    entered.height > initial.height + 15 && entered.focus === "controls-toggle" && spaced.expanded === "false" &&
    spaced.hidden && spaced.focus === "controls-toggle" && preserved.pass && focusReturn;
  return { status: pass ? "passed" : "failed", pass, initial, entered, spaced, preserved, focusReturn, collapsedLimit, responsivePosition };
}

async function primeLazyContent(page) {
  const dimensions = await page.evaluate(() => ({
    viewport: window.innerHeight,
    height: Math.max(document.documentElement.scrollHeight, document.body?.scrollHeight || 0),
  }));
  const maximum = Math.max(0, dimensions.height - dimensions.viewport);
  if (!maximum) return;
  const naturalSteps = Math.ceil(maximum / Math.max(420, dimensions.viewport * 0.72));
  const steps = Math.max(1, Math.min(36, naturalSteps));
  for (let index = 0; index <= steps; index += 1) {
    await page.evaluate((position) => window.scrollTo(0, position), Math.round(maximum * index / steps));
    await delay(35);
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await settle(page, 160);
}

async function testSearch(page) {
  await ensureControlsExpanded(page);
  const selector = await page.evaluate(() => {
    const candidates = ["#indicator-search", "input[type='search']", "input[data-search]"];
    return candidates.find((candidate) => {
      const element = document.querySelector(candidate);
      if (!element) return false;
      const style = getComputedStyle(element);
      return style.display !== "none" && style.visibility !== "hidden" && !element.disabled;
    }) || null;
  });
  if (!selector) return { status: "not-present", pass: true };

  const before = await page.evaluate(() => {
    const visible = (element) => {
      const style = getComputedStyle(element);
      return style.display !== "none" && style.visibility !== "hidden" && !element.classList.contains("hidden");
    };
    return Array.from(document.querySelectorAll(".panel,[data-search]")).filter(visible).length;
  });

  const impossibleQuery = "__surveye_qa_no_match_7f3a__";
  // Assign the full value before firing input. Some live-search dashboards
  // rerender after every keystroke and replace or blur the focused node, which
  // makes ElementHandle.type() stop after its first character.
  await page.$eval(selector, (field, value) => {
    field.focus();
    field.value = value;
    field.dispatchEvent(new Event("input", { bubbles: true }));
  }, impossibleQuery);
  await settle(page, 80);
  const filtered = await page.evaluate((searchSelector) => {
    const field = document.querySelector(searchSelector);
    const visible = (element) => {
      const style = getComputedStyle(element);
      return style.display !== "none" && style.visibility !== "hidden" && !element.classList.contains("hidden");
    };
    const noResults = document.querySelector("#no-results,.no-results,[data-no-results]");
    return {
      value: field ? field.value : null,
      visibleItems: Array.from(document.querySelectorAll(".panel,[data-search]")).filter(visible).length,
      noResultsVisible: Boolean(noResults && visible(noResults)),
    };
  }, selector);

  await page.$eval(selector, (field) => {
    field.value = "";
    field.dispatchEvent(new Event("input", { bubbles: true }));
    field.dispatchEvent(new Event("change", { bubbles: true }));
  });
  await settle(page, 80);
  const restored = await page.evaluate((searchSelector) => {
    const visible = (element) => {
      const style = getComputedStyle(element);
      return style.display !== "none" && style.visibility !== "hidden" && !element.classList.contains("hidden");
    };
    return {
      value: document.querySelector(searchSelector)?.value ?? null,
      visibleItems: Array.from(document.querySelectorAll(".panel,[data-search]")).filter(visible).length,
    };
  }, selector);

  const uniqueQuery = await page.evaluate(() => {
    const panels = Array.from(document.querySelectorAll(".panel[data-search]"));
    for (const panel of panels) {
      const value = panel.querySelector(".varbadge")?.textContent?.trim().toLocaleLowerCase();
      if (value && panels.filter((candidate) => candidate.getAttribute("data-search").includes(value)).length === 1) return value;
    }
    return null;
  });
  let singleton = { tested: false, pass: true };
  if (uniqueQuery) {
    await page.$eval(selector, (field, value) => {
      field.value = value;
      field.dispatchEvent(new Event("input", { bubbles: true }));
    }, uniqueQuery);
    await settle(page, 80);
    singleton = await page.evaluate(() => {
      const panel = Array.from(document.querySelectorAll(".panel")).find((candidate) =>
        !candidate.classList.contains("hidden") && getComputedStyle(candidate).display !== "none");
      const grid = panel?.closest(".grid");
      if (!panel || !grid) return { tested: true, pass: false, reason: "singleton panel/grid missing" };
      const panelRect = panel.getBoundingClientRect();
      const gridRect = grid.getBoundingClientRect();
      const difference = Math.abs(panelRect.width - gridRect.width);
      return { tested: true, pass: difference <= 2.5, difference, pattern: grid.dataset.rowPattern || "" };
    });
    await page.$eval(selector, (field) => {
      field.value = "";
      field.dispatchEvent(new Event("input", { bubbles: true }));
    });
    await settle(page, 80);
  }

  const reacted = before === 0 || filtered.visibleItems < before || filtered.noResultsVisible;
  const pass = filtered.value === impossibleQuery && reacted && restored.value === "" &&
    restored.visibleItems >= filtered.visibleItems && singleton.pass;
  return { status: pass ? "passed" : "failed", pass, selector, before, filtered, restored, singleton };
}

async function testFilter(page) {
  await ensureControlsExpanded(page);
  const button = await page.$(".chip[data-filter],button[data-filter],button[aria-pressed]");
  if (button) {
    const state = (element) => ({
      ariaPressed: element.getAttribute("aria-pressed"),
      className: element.className,
      matched: document.querySelector("[data-matched],.matched b")?.textContent?.trim() ?? null,
    });
    const before = await page.evaluate(state, button);
    await button.click();
    await settle(page, 100);
    const after = await page.evaluate(state, button);
    const changed = before.ariaPressed !== after.ariaPressed || before.className !== after.className || before.matched !== after.matched;

    const reset = await page.$("#reset,[data-reset-filters]");
    if (reset) await reset.click();
    else await button.click();
    await settle(page, 100);
    const restored = await page.evaluate(state, button);
    const resetWorked = restored.ariaPressed === before.ariaPressed || restored.className === before.className;
    const pass = changed && resetWorked;
    return { status: pass ? "passed" : "failed", pass, type: "button", before, after, restored };
  }

  const select = await page.$("select[data-filter],.filters select,.filter select");
  if (!select) return { status: "not-present", pass: true };
  const options = await page.evaluate((element) => Array.from(element.options).map((option) => option.value), select);
  if (options.length < 2) return { status: "present-no-alternative", pass: true, type: "select" };
  const before = await page.evaluate((element) => element.value, select);
  const target = options.find((value) => value !== before);
  await select.select(target);
  await settle(page, 100);
  const after = await page.evaluate((element) => element.value, select);
  await select.select(before);
  await settle(page, 80);
  const restored = await page.evaluate((element) => element.value, select);
  const pass = after === target && restored === before;
  return { status: pass ? "passed" : "failed", pass, type: "select", before, after, restored };
}

async function testDetails(page) {
  const details = await page.$("details.story") || await page.$("details");
  if (!details) return { status: "not-present", pass: true };
  const before = await page.evaluate((element) => element.open, details);
  const hasSummary = await page.evaluate((element) => Boolean(element.querySelector(":scope > summary")), details);
  if (!hasSummary) return { status: "failed", pass: false, reason: "details has no summary" };
  await page.evaluate((element) => element.querySelector(":scope > summary").click(), details);
  await settle(page, 100);
  const after = await page.evaluate((element) => element.open, details);
  await page.evaluate((element, original) => { element.open = original; }, details, before);
  await settle(page, 60);
  const restored = await page.evaluate((element) => element.open, details);
  const pass = after !== before && restored === before;
  return { status: pass ? "passed" : "failed", pass, before, after, restored };
}

async function testMap(page) {
  const mapSelector = await page.evaluate(() => {
    const candidates = ["#spatial-map", ".map-card", "[data-map]"];
    return candidates.find((candidate) => document.querySelector(candidate)) || null;
  });
  if (!mapSelector) return { status: "not-present", pass: true };

  const mapState = await page.$eval(mapSelector, (element) => ({
    isDetails: element.tagName.toLowerCase() === "details",
    open: element.tagName.toLowerCase() === "details" ? element.open : null,
    density: document.body.getAttribute("data-density") || "compact",
  }));
  if (mapState.isDetails && !mapState.open) {
    await page.$eval(mapSelector, (element) => { element.open = true; });
    await settle(page, 220);
  }
  await page.$eval(mapSelector, (element) => element.scrollIntoView({ block: "center", inline: "nearest" }));
  await settle(page, 120);
  const rendering = await page.evaluate((selector) => {
    const root = document.querySelector(selector);
    const canvas = root?.querySelector("canvas");
    const svg = root?.querySelector("svg");
    const path = svg?.querySelector("path");
    const leaflet = root?.querySelector("#leaflet-map.leaflet-container");
    const rect = root?.getBoundingClientRect();
    let pixel = null;
    let nonTransparentSample = 0;
    if (canvas && canvas.width > 0 && canvas.height > 0) {
      try {
        const context = canvas.getContext("2d", { willReadFrequently: true });
        const data = context.getImageData(0, 0, canvas.width, canvas.height).data;
        const stride = Math.max(1, Math.floor(Math.sqrt((canvas.width * canvas.height) / 150000)));
        let bestDensity = -1;
        const alphaAt = (x, y) => {
          if (x < 0 || y < 0 || x >= canvas.width || y >= canvas.height) return 0;
          return data[(y * canvas.width + x) * 4 + 3];
        };
        for (let y = 0; y < canvas.height; y += stride) {
          for (let x = 0; x < canvas.width; x += stride) {
            if (data[(y * canvas.width + x) * 4 + 3] > 5) {
              nonTransparentSample += 1;
              let density = 0;
              for (const dy of [-8, 0, 8]) {
                for (const dx of [-8, 0, 8]) density += alphaAt(x + dx, y + dy) > 5 ? 1 : 0;
              }
              if (density > bestDensity) {
                bestDensity = density;
                pixel = { x, y };
              }
            }
          }
        }
      } catch (error) {
        return { error: error.message, width: rect?.width ?? 0, height: rect?.height ?? 0 };
      }
    }
    const canvasRect = canvas?.getBoundingClientRect();
    const mapConfig = window.CONFIG?.map || null;
    const coordinateRows = mapConfig ? (window.DATA || []).filter((row) =>
      row?._lat !== null && row?._lat !== "" && row?._lon !== null && row?._lon !== "" &&
      Number.isFinite(Number(row._lat)) && Number.isFinite(Number(row._lon))
    ) : [];
    const expectedPoints = mapConfig ? coordinateRows.length : null;
    const lonCenter = Number(mapConfig?.lonCenter);
    const unwrapLongitude = (longitude) => {
      let result = Number(longitude);
      if (!Number.isFinite(lonCenter)) return result;
      while (result - lonCenter > 180) result -= 360;
      while (result - lonCenter < -180) result += 360;
      return result;
    };
    const boundsValid = Boolean(mapConfig && [mapConfig.minLat, mapConfig.maxLat, mapConfig.minLon, mapConfig.maxLon]
      .every((value) => Number.isFinite(Number(value))) && Number(mapConfig.minLat) < Number(mapConfig.maxLat) &&
      Number(mapConfig.minLon) < Number(mapConfig.maxLon));
    const pointsWithinBoundaryBounds = boundsValid ? coordinateRows.filter((row) => {
      const latitude = Number(row._lat);
      const longitude = unwrapLongitude(row._lon);
      return latitude >= Number(mapConfig.minLat) - 1e-7 && latitude <= Number(mapConfig.maxLat) + 1e-7 &&
        longitude >= Number(mapConfig.minLon) - 1e-7 && longitude <= Number(mapConfig.maxLon) + 1e-7;
    }).length : 0;
    const displayedPoints = Number((root?.querySelector("#map-count")?.textContent || "").replace(/[^0-9]/g, ""));
    const pointMarkers = Array.from(root?.querySelectorAll(".leaflet-interactive[role='button']") || []);
    return {
      width: rect?.width ?? 0,
      height: rect?.height ?? 0,
      hasSvgPath: Boolean(path && path.getAttribute("d")),
      canvasWidth: canvas?.width ?? 0,
      canvasHeight: canvas?.height ?? 0,
      nonTransparentSample,
      leafletExpected: Boolean(mapConfig && root?.querySelector("#leaflet-map")),
      leafletReady: Boolean(leaflet && leaflet.getBoundingClientRect().width > 10 && leaflet.getBoundingClientRect().height > 10),
      layerControl: Boolean(root?.querySelector(".leaflet-control-layers")),
      zoomControl: Boolean(root?.querySelector(".leaflet-control-zoom")),
      mapType: mapConfig?.type || null,
      basemap: mapConfig?.basemap || null,
      expectedPoints,
      displayedPoints: Number.isFinite(displayedPoints) ? displayedPoints : null,
      renderedMarkers: leaflet?.hasAttribute("data-rendered-markers") ? Number(leaflet.dataset.renderedMarkers) : null,
      accessiblePointMarkers: pointMarkers.length,
      pointMarkerAccessibility: pointMarkers.every((marker) => marker.getAttribute("tabindex") === "0" &&
        marker.getAttribute("focusable") === "true" && Boolean(marker.getAttribute("aria-label"))),
      boundaryExpected: Boolean(mapConfig?.boundary),
      boundaryReady: leaflet?.dataset.boundaryReady === "true",
      boundaryRings: leaflet?.hasAttribute("data-boundary-rings") ? Number(leaflet.dataset.boundaryRings) : 0,
      boundsValid,
      pointsWithinBoundaryBounds,
      hoverPoint: pixel && canvasRect ? {
        x: canvasRect.left + pixel.x * canvasRect.width / canvas.width,
        y: canvasRect.top + pixel.y * canvasRect.height / canvas.height,
      } : null,
    };
  }, mapSelector);

  let markerKeyboard = { status: "not-present", pass: true };
  if (rendering.mapType === "points" && rendering.expectedPoints > 0) {
    markerKeyboard = await page.evaluate((selector) => {
      const marker = document.querySelector(selector)?.querySelector(".leaflet-interactive[role='button']");
      if (!marker) return { status: "failed", pass: false, reason: "no keyboard marker" };
      marker.focus();
      marker.dispatchEvent(new KeyboardEvent("keydown", { key: "Enter", bubbles: true, cancelable: true }));
      const enterPopup = Boolean(document.querySelector(".leaflet-popup"));
      document.querySelector(".leaflet-popup-close-button")?.click();
      marker.dispatchEvent(new KeyboardEvent("keydown", { key: " ", bubbles: true, cancelable: true }));
      const spacePopup = Boolean(document.querySelector(".leaflet-popup"));
      document.querySelector(".leaflet-popup-close-button")?.click();
      const focused = document.activeElement === marker;
      return { status: enterPopup && spacePopup && focused ? "passed" : "failed", pass: enterPopup && spacePopup && focused, enterPopup, spacePopup, focused };
    }, mapSelector);
  }

  let tooltipShown = null;
  let hoverPointUsed = null;
  if (rendering.hoverPoint) {
    // The first painted pixel can sit on the antialiased rim of a cluster while
    // the interactive hit target is centered a few pixels inward. Probe a
    // compact neighborhood so the test verifies interaction without depending
    // on one renderer-specific edge pixel.
    const offsets = [[0, 0], [0, 6], [6, 0], [0, 12], [12, 0], [-6, 0], [0, -6], [6, 6], [-6, 6]];
    for (const [dx, dy] of offsets) {
      const point = { x: rendering.hoverPoint.x + dx, y: rendering.hoverPoint.y + dy };
      await page.evaluate((selector, position) => {
        const canvas = document.querySelector(selector)?.querySelector("canvas");
        if (canvas) canvas.dispatchEvent(new MouseEvent("mousemove", {
          bubbles: true,
          clientX: position.x,
          clientY: position.y,
        }));
      }, mapSelector, point);
      await settle(page, 25);
      tooltipShown = await page.evaluate(() => {
        const tooltip = document.querySelector("#map-tooltip,.map-tooltip,[role='tooltip']");
        if (!tooltip) return null;
        const style = getComputedStyle(tooltip);
        return style.display !== "none" && style.visibility !== "hidden" && style.opacity !== "0";
      });
      if (tooltipShown !== false) {
        hoverPointUsed = point;
        break;
      }
    }
  }

  let filterRefit = { status: "not-present", pass: true };
  const filterButtons = await page.$$(".chip[data-filter],button[data-filter]");
  if (filterButtons.length && rendering.displayedPoints > 1) {
    const viewportState = () => page.evaluate(() => {
      const map = document.getElementById("leaflet-map");
      const count = Number((document.getElementById("map-count")?.textContent || "").replace(/[^0-9]/g, ""));
      const signature = [
        map?.querySelector(".leaflet-map-pane")?.getAttribute("style") || "",
        map?.querySelector(".leaflet-proxy")?.getAttribute("style") || "",
        map?.querySelector(".leaflet-tile-pane")?.getAttribute("style") || "",
      ].join("|");
      return {
        count: Number.isFinite(count) ? count : null,
        markers: map?.hasAttribute("data-rendered-markers") ? Number(map.dataset.renderedMarkers) : null,
        fitRevision: map?.hasAttribute("data-fit-revision") ? Number(map.dataset.fitRevision) : null,
        fitBounds: map?.dataset.fitBounds || null,
        signature,
      };
    });
    const initial = await viewportState();
    const reset = await page.$("#reset,[data-reset-filters]");
    for (const button of filterButtons) {
      await page.evaluate((element) => element.click(), button);
      await settle(page, 220);
      const filtered = await viewportState();
      if (reset) await page.evaluate((element) => element.click(), reset);
      else await page.evaluate((element) => element.click(), button);
      await settle(page, 220);
      const restored = await viewportState();
      if (filtered.count !== null && filtered.count > 0 && filtered.count < initial.count) {
        // A refit may legitimately resolve to the same pixel transform when a
        // filtered subset spans almost the same bounds.  Current dashboards
        // expose a monotone fit revision; retain the transform fallback for
        // older generated fixtures.
        const hasFitRevision = Number.isFinite(initial.fitRevision) && Number.isFinite(filtered.fitRevision);
        const refit = hasFitRevision
          ? filtered.fitRevision > initial.fitRevision
          : filtered.signature !== initial.signature;
        // Point mode promises one marker layer per valid observation. Cluster
        // and heat modes deliberately aggregate, so their rendered layer count
        // only needs to stay positive and no larger than the displayed count.
        const exactMarkers = rendering.mapType === "points";
        const markerCount = filtered.markers === null || (exactMarkers
          ? filtered.markers === filtered.count
          : filtered.markers > 0 && filtered.markers <= filtered.count);
        const restoredCount = restored.count === initial.count &&
          (restored.markers === null || (exactMarkers
            ? restored.markers === initial.count
            : restored.markers === initial.markers));
        const restoredRefit = !hasFitRevision || !Number.isFinite(restored.fitRevision) || restored.fitRevision > filtered.fitRevision;
        const pass = refit && restoredRefit && markerCount && restoredCount;
        filterRefit = { status: pass ? "passed" : "failed", pass, initial, filtered, restored, refit, restoredRefit, markerCount, restoredCount };
        break;
      }
    }
    if (filterRefit.status === "not-present") {
      filterRefit = { status: "no-reducing-filter", pass: true, initial };
    }
  }
  await page.evaluate(() => window.scrollTo(0, 0));
  await settle(page, 60);

  if (mapState.isDetails) {
    await page.$eval(mapSelector, (element, wasOpen) => { element.open = wasOpen; }, mapState.open);
    await settle(page, 80);
  }

  const graphicRendered = rendering.width > 10 && rendering.height > 10 &&
    (rendering.hasSvgPath || (rendering.canvasWidth > 0 && rendering.canvasHeight > 0));
  const hoverWorked = rendering.hoverPoint ? tooltipShown !== false : true;
  const leafletPass = !rendering.leafletExpected ||
    (rendering.leafletReady && rendering.layerControl && rendering.zoomControl);
  const pointCountPass = rendering.mapType !== "points" ||
    (rendering.expectedPoints === rendering.displayedPoints &&
      rendering.expectedPoints === rendering.renderedMarkers && rendering.expectedPoints > 0);
  const pointAccessibilityPass = rendering.mapType !== "points" ||
    (rendering.accessiblePointMarkers === rendering.expectedPoints && rendering.pointMarkerAccessibility && markerKeyboard.pass);
  const boundaryAlignmentPass = !rendering.boundaryExpected ||
    (rendering.boundaryReady && rendering.boundaryRings > 0 && rendering.boundsValid &&
      rendering.pointsWithinBoundaryBounds > 0);
  const densityPass = mapState.density !== "compact" || !mapState.isDetails || mapState.open === false;
  const pass = !rendering.error && graphicRendered && hoverWorked && leafletPass && pointCountPass && pointAccessibilityPass && boundaryAlignmentPass && filterRefit.pass && densityPass;
  return { status: pass ? "passed" : "failed", pass, mapState, densityPass, leafletPass, pointCountPass, pointAccessibilityPass, markerKeyboard, boundaryAlignmentPass, filterRefit, rendering, tooltipShown, hoverPointUsed };
}

async function checkCharts(page) {
  const chartCount = await page.$$eval("canvas.chart", (canvases) => canvases.length);
  if (!chartCount) return { status: "not-present", pass: true, canvases: 0 };

  const originalDetails = await page.evaluate(() => Array.from(document.querySelectorAll("details.story"), (element) => element.open));
  const originalMapOpen = await page.evaluate(() => {
    const map = document.getElementById("spatial-map");
    return map?.tagName.toLowerCase() === "details" ? map.open : null;
  });
  await page.evaluate(() => {
    // Force all lazy canvases to build deterministically. The generated
    // dashboard's print hook uses the same supported build path as printing.
    if (window.Chart?.defaults?.animation) window.Chart.defaults.animation.duration = 0;
    window.dispatchEvent(new Event("beforeprint"));
  });
  await settle(page, 300);
  // Restore the responsive row grammar after using the print hook to build
  // every lazy chart. Chart instances remain alive; only grid spans change.
  await page.evaluate(() => window.dispatchEvent(new Event("afterprint")));
  await settle(page, 80);

  const results = await page.evaluate(() => Array.from(document.querySelectorAll("canvas.chart"), (canvas) => {
    const empty = document.getElementById(`empty-${canvas.id}`) || canvas.parentElement?.querySelector(".empty");
    const emptyStyle = empty ? getComputedStyle(empty) : null;
    const emptyVisible = Boolean(empty && emptyStyle.display !== "none" && emptyStyle.visibility !== "hidden" && emptyStyle.opacity !== "0");
    let paintedSamples = 0;
    let pixelError = null;
    if (canvas.width > 0 && canvas.height > 0) {
      try {
        const context = canvas.getContext("2d", { willReadFrequently: true });
        const pixels = context.getImageData(0, 0, canvas.width, canvas.height).data;
        const stride = Math.max(1, Math.floor(Math.sqrt((canvas.width * canvas.height) / 120000)));
        for (let y = 0; y < canvas.height; y += stride) {
          for (let x = 0; x < canvas.width; x += stride) {
            if (pixels[(y * canvas.width + x) * 4 + 3] > 5) paintedSamples += 1;
          }
        }
      } catch (error) {
        pixelError = error.message;
      }
    }
    const chart = window.Chart?.getChart ? window.Chart.getChart(canvas) : null;
    const statText = canvas.closest(".panel")?.querySelector(".panel-stat")?.textContent?.trim() || "";
    const legendExpected = Boolean(chart && chart.options?.plugins?.legend?.display !== false);
    const legendItems = chart?.legend?.legendItems?.length || 0;
    const legendWidth = chart?.legend?.width || 0;
    const legendHeight = chart?.legend?.height || 0;
    const rendered = emptyVisible || Boolean(chart && paintedSamples > 0 && !pixelError);
    const legendPass = !legendExpected || (legendItems > 0 && legendWidth > 0 && legendHeight > 0);
    const panel = canvas.closest(".panel");
    const summaryText = panel?.querySelector(".chart-summary")?.textContent?.replace(/\s+/g, " ").trim() || "";
    const chartHeight = Math.round(canvas.parentElement?.getBoundingClientRect().height || 0);
    const variable = canvas.getAttribute("data-variable");
    const kind = canvas.getAttribute("data-kind");
    const expectedType = ({ yesno: "bar", completion: "bar", bar: "bar", multibar: "bar", hist: "bar", date: "line", donut: "doughnut" })[kind];
    const ciExpected = Boolean(window.CONFIG?.showCI && ["bar", "multibar"].includes(kind) && !emptyVisible);
    const ciForbidden = Boolean(!emptyVisible &&
      (!window.CONFIG?.showCI || ["yesno", "completion", "donut"].includes(kind)));
    const ciIntervals = chart?.options?.plugins?.barConfidenceIntervals?.intervals;
    const ciPlugin = Boolean(chart?.config?.plugins?.some((plugin) => plugin?.id === "barConfidenceIntervals"));
    const ciIntervalCount = Array.isArray(ciIntervals) ? ciIntervals.filter(Boolean).length : 0;
    const ciToken = String(window.CONFIG?.strings?.confidenceInterval || "CI").trim();
    const ciTextPresent = Boolean(ciToken && (/^[A-Za-z0-9]+$/.test(ciToken)
      ? new RegExp(`(^|[^A-Za-z0-9])${ciToken.replace(/[.*+?^${}()|[\]\\]/g, "\\$&")}([^A-Za-z0-9]|$)`, "i").test(summaryText)
      : summaryText.toLocaleLowerCase().includes(ciToken.toLocaleLowerCase())));
    const ciPresentPass = !ciExpected || Boolean(ciPlugin && Array.isArray(ciIntervals) &&
      ciIntervals.length === (chart?.data?.labels?.length || 0) &&
      ciIntervals.some((interval) => interval && Number.isFinite(interval.low) && Number.isFinite(interval.high)));
    const ciAbsentPass = !ciForbidden || Boolean(ciIntervalCount === 0 && !ciTextPresent &&
      (!["yesno", "completion", "donut"].includes(kind) || !ciPlugin));
    const ciPass = ciPresentPass && ciAbsentPass;
    const expectedRespondentN = kind === "multibar"
      ? (window.DATA || []).filter((row) => Array.isArray(row?.[variable])).length
      : null;
    const respondentN = canvas.hasAttribute("data-respondent-n") ? Number(canvas.dataset.respondentN) : null;
    const respondentDenominatorPass = kind !== "multibar" || respondentN === expectedRespondentN;
    return {
      id: canvas.id || null,
      variable,
      kind,
      width: canvas.width,
      height: canvas.height,
      emptyVisible,
      chartInstance: Boolean(chart),
      paintedSamples,
      pixelError,
      rendered,
      legendExpected,
      legendItems,
      legendWidth,
      legendHeight,
      legendPass,
      dataLabels: chart?.data?.labels?.length || 0,
      hasOtherCategory: Boolean(chart?.data?.labels?.some((label) => String(label).startsWith("Other ("))),
      statText,
      summaryText,
      chartHeight,
      chartType: chart?.config?.type || null,
      expectedType: expectedType || null,
      typePass: emptyVisible || !expectedType || chart?.config?.type === expectedType,
      ciExpected,
      ciForbidden,
      ciIntervals: ciIntervalCount,
      ciTextPresent,
      ciPass,
      expectedRespondentN,
      respondentN,
      respondentDenominatorPass,
      described: Boolean(canvas.getAttribute("aria-label") && canvas.getAttribute("aria-describedby")),
    };
  }));

  const density = await page.evaluate(() => {
    const mode = document.body.getAttribute("data-density") || "compact";
    const expectedColumns = mode === "compact" ? (innerWidth > 1120 ? 3 : innerWidth > 680 ? 2 : 1) : (innerWidth > 680 ? 2 : 1);
    const tolerance = 2.5;
    const grids = Array.from(document.querySelectorAll(".grid")).filter((element) => getComputedStyle(element).display === "grid");
    const details = grids.map((grid) => {
      const panels = Array.from(grid.children).filter((panel) => panel.classList.contains("panel") &&
        !panel.classList.contains("hidden") && getComputedStyle(panel).display !== "none");
      const rows = new Map();
      panels.forEach((panel) => {
        const key = panel.dataset.gridRow || "unassigned";
        if (!rows.has(key)) rows.set(key, []);
        rows.get(key).push(panel);
      });
      const gridRect = grid.getBoundingClientRect();
      const rowDetails = Array.from(rows.values(), (rowPanels) => {
        const rects = rowPanels.map((panel) => panel.getBoundingClientRect());
        const widths = rects.map((rect) => rect.width);
        const heights = rects.map((rect) => rect.height);
        const left = Math.min(...rects.map((rect) => rect.left));
        const right = Math.max(...rects.map((rect) => rect.right));
        return {
          count: rowPanels.length,
          widthSpread: widths.length ? Math.max(...widths) - Math.min(...widths) : 0,
          heightSpread: heights.length ? Math.max(...heights) - Math.min(...heights) : 0,
          edgeGap: Math.abs((right - left) - gridRect.width),
          pass: rowPanels.length <= expectedColumns &&
            (!widths.length || Math.max(...widths) - Math.min(...widths) <= tolerance) &&
            (!heights.length || Math.max(...heights) - Math.min(...heights) <= tolerance) &&
            Math.abs((right - left) - gridRect.width) <= tolerance,
        };
      });
      return {
        pattern: grid.dataset.rowPattern || "",
        rows: rowDetails,
        pass: rowDetails.every((row) => row.pass),
      };
    });
    const columns = details.reduce((maximum, grid) => Math.max(maximum, ...grid.rows.map((row) => row.count), 0), 0);
    return { mode, columns, expectedColumns, grids: details, pass: details.every((grid) => grid.pass) };
  });

  await page.evaluate((states, mapOpen) => {
    document.querySelectorAll("details.story").forEach((element, index) => { element.open = Boolean(states[index]); });
    const map = document.getElementById("spatial-map");
    if (mapOpen !== null && map?.tagName.toLowerCase() === "details") map.open = mapOpen;
  }, originalDetails, originalMapOpen);
  await settle(page, 100);

  const blank = results.filter((chart) => !chart.rendered).map((chart) => chart.id || chart.variable || "unnamed");
  const badLegends = results.filter((chart) => !chart.legendPass).map((chart) => chart.id || chart.variable || "unnamed");
  const badTypes = results.filter((chart) => !chart.typePass).map((chart) => chart.id || chart.variable || "unnamed");
  const missingSummaries = results.filter((chart) => !chart.summaryText || !chart.described).map((chart) => chart.id || chart.variable || "unnamed");
  const badConfidenceIntervals = results.filter((chart) => !chart.ciPass).map((chart) => chart.id || chart.variable || "unnamed");
  const badRespondentDenominators = results.filter((chart) => !chart.respondentDenominatorPass).map((chart) => chart.id || chart.variable || "unnamed");
  const heightLimits = { yesno: 72, completion: 72, bar: 250, multibar: 250, hist: 205, date: 205, donut: 215 };
  const oversized = density.mode === "compact" ? results.filter((chart) => !chart.emptyVisible && heightLimits[chart.kind] && chart.chartHeight > heightLimits[chart.kind]).map((chart) => `${chart.id || chart.variable}:${chart.chartHeight}px`) : [];
  const pass = blank.length === 0 && badLegends.length === 0 && badTypes.length === 0 && missingSummaries.length === 0 && badConfidenceIntervals.length === 0 && badRespondentDenominators.length === 0 && oversized.length === 0 && density.pass;
  return {
    status: pass ? "passed" : "failed",
    pass,
    canvases: results.length,
    rendered: results.filter((chart) => chart.rendered).length,
    emptyStates: results.filter((chart) => chart.emptyVisible).length,
    legendsExpected: results.filter((chart) => chart.legendExpected).length,
    legendsPassed: results.filter((chart) => chart.legendExpected && chart.legendPass).length,
    blank,
    badLegends,
    badTypes,
    missingSummaries,
    badConfidenceIntervals,
    badRespondentDenominators,
    oversized,
    density,
    details: results.slice(0, 20),
    truncated: results.length > 20,
  };
}

async function checkMotionLifecycle(page) {
  const prepared = await page.evaluate(async () => {
    const originalDetails = Array.from(document.querySelectorAll("details.story"), (detail) => detail.open);
    const originalScroll = { x: window.scrollX, y: window.scrollY };
    window.scrollTo(0, 0);
    document.querySelectorAll("details.story").forEach((detail) => { detail.open = true; });
    await new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve)));
    const candidates = Array.from(document.querySelectorAll("canvas.chart")).filter((canvas) => {
      const rect = canvas.getBoundingClientRect();
      return rect.width > 0 && rect.height > 0 && rect.top > window.innerHeight + 80 && !Chart.getChart(canvas);
    });
    return { id: candidates.at(-1)?.id || null, originalDetails, originalScroll };
  });

  async function restore() {
    await page.evaluate(({ originalDetails, originalScroll }) => {
      document.querySelectorAll("details.story").forEach((detail, index) => { detail.open = originalDetails[index]; });
      window.scrollTo(originalScroll.x, originalScroll.y);
    }, prepared);
    await settle(page, 60);
  }

  if (!prepared.id) {
    await restore();
    return { status: "not-applicable", pass: true, reason: "no unbuilt offscreen chart" };
  }

  await settle(page, 100);
  const prebuilt = await page.evaluate((id) => Boolean(Chart.getChart(document.getElementById(id))), prepared.id);
  if (prebuilt) {
    await restore();
    return { status: "failed", pass: false, target: prepared.id, reason: "opening a section eagerly built an offscreen chart" };
  }

  await page.evaluate((id) => document.getElementById(id).scrollIntoView({ block: "center", behavior: "instant" }), prepared.id);
  try {
    await page.waitForFunction((id) => Boolean(Chart.getChart(document.getElementById(id))), { timeout: 1500 }, prepared.id);
  } catch (error) {
    await restore();
    return { status: "failed", pass: false, target: prepared.id, reason: "visible chart was not constructed", error: error.message };
  }

  async function snapshot() {
    return page.evaluate((id) => {
      const chart = Chart.getChart(document.getElementById(id));
      const element = chart?.getDatasetMeta(0)?.data?.[0];
      const values = ["x", "y", "base", "width", "height", "circumference", "startAngle", "endAngle", "outerRadius"]
        .map((key) => element?.[key]).filter(Number.isFinite);
      return {
        running: Boolean(chart && Chart.animator.running(chart)),
        duration: chart?.options?.animation?.duration,
        easing: chart?.options?.animation?.easing,
        values,
      };
    }, prepared.id);
  }

  const early = await snapshot();
  await settle(page, 180);
  const middle = await snapshot();
  await settle(page, 760);
  const final = await snapshot();
  const geometryChanged = early.values.length === middle.values.length &&
    early.values.some((value, index) => Math.abs(value - middle.values[index]) > 0.01);
  const pass = early.duration === 800 && early.easing === "easeOutCubic" && early.running && geometryChanged && !final.running;
  await restore();
  return { status: pass ? "passed" : "failed", pass, target: prepared.id, geometryChanged, early, middle, final };
}

async function testStatsTabs(page) {
  const count = await page.$$eval('[data-panel-view="stats"]', (buttons) => buttons.length);
  if (!count) return { status: "not-present", pass: true, tabs: 0 };

  const details = await page.evaluate(() => Array.from(document.querySelectorAll('[data-panel-view="stats"]'), (button) => {
    const panel = button.closest(".panel");
    const distribution = panel?.querySelector('[data-panel-view="distribution"]');
    const statsPane = panel?.querySelector('[data-panel-view-pane="stats"]');
    const distributionPane = panel?.querySelector('[data-panel-view-pane="distribution"]');
    button.click();
    const table = statsPane?.querySelector("table.stats-table");
    const selected = button.getAttribute("aria-selected") === "true" && button.tabIndex === 0;
    const paneVisible = statsPane?.getAttribute("aria-hidden") === "false" && statsPane?.classList.contains("is-active");
    const distributionHidden = distributionPane?.getAttribute("aria-hidden") === "true" && !distributionPane?.classList.contains("is-active");
    const populated = Boolean((table && table.querySelectorAll("tr").length >= 5) || statsPane?.querySelector(".stats-empty"));
    const finalRow = table?.querySelector("tr:last-child");
    const summaryField = !table || (finalRow?.querySelectorAll("th,td").length >= 4 &&
      Array.from(finalRow.querySelectorAll("th,td"), (cell) => cell.textContent.trim()).every(Boolean));
    const legacyOutlierCopy = /Tukey|outlier/i.test(statsPane?.textContent || "");
    distribution?.click();
    const restored = distribution?.getAttribute("aria-selected") === "true" &&
      distributionPane?.getAttribute("aria-hidden") === "false";
    return {
      variable: panel?.querySelector("canvas.chart")?.getAttribute("data-variable") || null,
      selected,
      paneVisible,
      distributionHidden,
      populated,
      summaryField,
      legacyOutlierCopy,
      restored,
      rows: table?.querySelectorAll("tr").length || 0,
    };
  }));
  const failed = details.filter((item) => !item.selected || !item.paneVisible || !item.distributionHidden ||
    !item.populated || !item.summaryField || item.legacyOutlierCopy || !item.restored).map((item) => item.variable || "unnamed");
  const pass = failed.length === 0;
  return { status: pass ? "passed" : "failed", pass, tabs: details.length, failed, details: details.slice(0, 20) };
}

async function testEstimateModes(page) {
  const presence = await page.evaluate(() => ({
    weight: Boolean(document.getElementById("weight-toggle")),
    usd: Boolean(document.getElementById("usd-toggle")),
    table: Boolean(document.getElementById("summary-profile-table")),
  }));
  if (!presence.weight && !presence.usd && !presence.table) {
    return { status: "not-present", pass: true, presence };
  }

  const result = { presence, weight: null, usd: null, tableOwnFilter: null, liveStats: null };
  if (presence.weight) {
    const before = await page.evaluate(() => ({
      checked: document.getElementById("weight-toggle")?.checked,
      mode: document.body.dataset.weightMode,
      tableColumns: document.querySelectorAll("#summary-profile-table thead th").length,
    }));
    await page.evaluate(() => document.getElementById("weight-toggle")?.click());
    await settle(page, 100);
    const after = await page.evaluate(() => ({
      checked: document.getElementById("weight-toggle")?.checked,
      mode: document.body.dataset.weightMode,
      tableColumns: document.querySelectorAll("#summary-profile-table thead th").length,
      status: document.querySelector("[data-profile-status]")?.textContent.trim() || "",
      footer: document.querySelector("[data-weight-footer-label]")?.textContent.trim() || "",
      badge: document.querySelector("[data-weight-mode-label]")?.textContent.trim() || "",
    }));
    const tablePass = !presence.table || after.tableColumns === before.tableColumns - 1;
    const copyPass = /unweighted/i.test(`${after.status} ${after.footer} ${after.badge}`);
    result.weight = { before, after, pass: before.checked === true && before.mode === "weighted" &&
      after.checked === false && after.mode === "unweighted" && tablePass && copyPass };
    await page.evaluate(() => document.getElementById("weight-toggle")?.click());
    await settle(page, 100);
  }

  if (presence.usd) {
    const before = await page.evaluate(() => ({
      checked: document.getElementById("usd-toggle")?.checked,
      mode: document.body.dataset.currencyMode,
      table: document.getElementById("summary-profile-table")?.textContent || "",
    }));
    await page.evaluate(() => document.getElementById("usd-toggle")?.click());
    await settle(page, 100);
    const after = await page.evaluate(() => ({
      checked: document.getElementById("usd-toggle")?.checked,
      mode: document.body.dataset.currencyMode,
      table: document.getElementById("summary-profile-table")?.textContent || "",
      status: document.querySelector("[data-profile-status]")?.textContent.trim() || "",
    }));
    const tablePass = !presence.table || (after.table.includes("USD") && after.table !== before.table);
    const statusPass = !presence.table || /USD/i.test(after.status);
    result.usd = { before: { checked: before.checked, mode: before.mode }, after: {
      checked: after.checked, mode: after.mode, status: after.status,
    }, pass: before.checked === false && before.mode === "local" && after.checked === true &&
      after.mode === "usd" && tablePass && statusPass };
    await page.evaluate(() => document.getElementById("usd-toggle")?.click());
    await settle(page, 100);
  }

  if (presence.table) {
    const tableState = await page.evaluate(() => {
      const by = window.CONFIG?.table?.by;
      const chip = by ? document.querySelector(`.chip[data-filter="${CSS.escape(by)}"]`) : null;
      const table = document.getElementById("summary-profile-table");
      if (!by || !chip || !table) return { tested: false, pass: true, reason: "no tableby filter chip" };
      const beforeRows = table.querySelectorAll("tbody tr").length;
      const beforeMatched = document.querySelector("[data-matched]")?.textContent.trim();
      chip.click();
      const afterRows = table.querySelectorAll("tbody tr").length;
      const selectedRows = table.querySelectorAll("tbody tr.profile-selected").length;
      const totalRows = table.querySelectorAll("tbody tr.profile-total").length;
      const afterMatched = document.querySelector("[data-matched]")?.textContent.trim();
      chip.click();
      return { tested: true, beforeRows, afterRows, selectedRows, totalRows, beforeMatched, afterMatched,
        pass: beforeRows > 1 && afterRows === beforeRows && selectedRows === 1 && totalRows === 1 &&
          beforeMatched !== afterMatched };
    });
    result.tableOwnFilter = tableState;
    await settle(page, 100);
  }

  result.liveStats = await page.evaluate(() => {
    const panel = Array.from(document.querySelectorAll(".panel")).find((candidate) => {
      const canvas = candidate.querySelector("canvas.chart");
      const kind = canvas?.getAttribute("data-kind");
      return (kind === "hist" || kind === "discrete") && candidate.querySelector('[data-panel-view="stats"]');
    });
    const tableBy = window.CONFIG?.table?.by;
    const chip = Array.from(document.querySelectorAll(".chip[data-filter]")).find((candidate) =>
      candidate.getAttribute("data-filter") !== tableBy) || document.querySelector(".chip[data-filter]");
    if (!panel || !chip) return { tested: false, pass: true, reason: "no numeric Stats/filter pair" };
    const statsButton = panel.querySelector('[data-panel-view="stats"]');
    const distributionButton = panel.querySelector('[data-panel-view="distribution"]');
    statsButton.click();
    const pane = panel.querySelector('[data-panel-view-pane="stats"]');
    const before = pane?.textContent.trim() || "";
    const beforeMatched = document.querySelector("[data-matched]")?.textContent.trim();
    chip.click();
    const after = pane?.textContent.trim() || "";
    const afterMatched = document.querySelector("[data-matched]")?.textContent.trim();
    chip.click();
    distributionButton?.click();
    return { tested: true, beforeMatched, afterMatched, changed: before !== after,
      pass: Boolean(before && after && beforeMatched !== afterMatched && before !== after) };
  });

  const pass = (!result.weight || result.weight.pass) && (!result.usd || result.usd.pass) &&
    (!result.tableOwnFilter || result.tableOwnFilter.pass) && (!result.liveStats || result.liveStats.pass);
  return { ...result, status: pass ? "passed" : "failed", pass };
}

async function checkOverflow(page) {
  const originalState = await page.evaluate(() => {
    const details = Array.from(document.querySelectorAll("details"));
    const original = details.map((element) => element.open);
    details.forEach((element) => { element.open = true; });
    const toggle = document.getElementById("controls-toggle");
    const controlsExpanded = toggle?.getAttribute("aria-expanded") === "true";
    if (toggle && !controlsExpanded) toggle.click();
    return { details: original, controlsExpanded };
  });
  await settle(page, 80);
  const result = await page.evaluate(() => {
    const tolerance = 2;
    const root = document.documentElement;
    const body = document.body;
    const viewportWidth = root.clientWidth;
    const scrollWidth = Math.max(root.scrollWidth, body?.scrollWidth || 0);
    const overflowPixels = Math.max(0, scrollWidth - viewportWidth);
    const describe = (element) => {
      if (element.id) return `${element.tagName.toLowerCase()}#${element.id}`;
      const classes = Array.from(element.classList || []).slice(0, 3).join(".");
      return element.tagName.toLowerCase() + (classes ? `.${classes}` : "");
    };
    const offenders = [];
    if (overflowPixels > tolerance) {
      for (const element of document.body.querySelectorAll("*")) {
        const style = getComputedStyle(element);
        if (style.display === "none" || style.visibility === "hidden") continue;
        const rect = element.getBoundingClientRect();
        if (rect.width <= 0 || rect.height <= 0) continue;
        const right = rect.right - viewportWidth;
        const left = -rect.left;
        if (right > tolerance || left > tolerance) {
          offenders.push({ selector: describe(element), right: Math.round(right * 10) / 10, left: Math.round(left * 10) / 10 });
        }
        if (offenders.length >= 12) break;
      }
    }
    return { pass: overflowPixels <= tolerance, viewportWidth, scrollWidth, overflowPixels, offenders };
  });
  await page.evaluate((state) => {
    document.querySelectorAll("details").forEach((element, index) => { element.open = Boolean(state.details[index]); });
    const toggle = document.getElementById("controls-toggle");
    const expanded = toggle?.getAttribute("aria-expanded") === "true";
    if (toggle && expanded !== state.controlsExpanded) toggle.click();
  }, originalState);
  await settle(page, 60);
  return result;
}

function checkFailures(viewportReport) {
  const failures = [];
  if (viewportReport.errors.console.count) failures.push(`${viewportReport.errors.console.count} console error(s)`);
  if (viewportReport.errors.page.count) failures.push(`${viewportReport.errors.page.count} page error(s)`);
  if (viewportReport.errors.localRequests.count) failures.push(`${viewportReport.errors.localRequests.count} local request failure(s)`);
  if (viewportReport.network.externalRequests.count) failures.push(`${viewportReport.network.externalRequests.count} external request attempt(s)`);
  if (!viewportReport.checks.horizontalOverflow.pass) {
    const overflow = viewportReport.checks.horizontalOverflow;
    failures.push(overflow.reason ? `horizontal overflow check failed: ${overflow.reason}` : `${overflow.overflowPixels}px horizontal overflow`);
  }
  for (const name of ["motion", "controls", "search", "filter", "details", "map", "charts", "stats", "estimateModes"]) {
    if (!viewportReport.checks[name].pass) failures.push(`${name} interaction failed`);
  }
  if (!viewportReport.screenshot.saved) failures.push("full-page screenshot failed");
  return failures;
}

async function runViewport(browser, htmlUrl, outputDirectory, viewport, timeout) {
  const page = await browser.newPage();
  const consoleErrors = [];
  const pageErrors = [];
  const localRequestFailures = [];
  const externalRequests = [];
  const basemapTileRequests = [];
  const screenshotPath = path.join(outputDirectory, `${viewport.name}.png`);

  page.on("console", (message) => {
    if (message.type() !== "error") return;
    const location = message.location();
    if (isExpectedBasemapTileUrl(location.url || "") && message.text().includes("ERR_BLOCKED_BY_CLIENT")) return;
    consoleErrors.push({ text: message.text(), url: location.url || null, line: location.lineNumber ?? null });
  });
  page.on("pageerror", (error) => pageErrors.push({ message: error.message, stack: error.stack || null }));
  page.on("requestfailed", (request) => {
    if (isExternalUrl(request.url())) return;
    localRequestFailures.push({ url: request.url(), error: request.failure()?.errorText || "unknown" });
  });

  await page.setViewport(viewport);
  await page.setCacheEnabled(false);
  await page.setBypassServiceWorker(true);
  await page.setRequestInterception(true);
  page.on("request", (request) => {
    if (isExternalUrl(request.url())) {
      const record = { method: request.method(), resourceType: request.resourceType(), url: request.url() };
      if (isExpectedBasemapTileUrl(request.url())) basemapTileRequests.push(record);
      else externalRequests.push(record);
      request.abort("blockedbyclient").catch(() => {});
    } else {
      request.continue().catch(() => {});
    }
  });

  const report = {
    name: viewport.name,
    viewport: { width: viewport.width, height: viewport.height, deviceScaleFactor: viewport.deviceScaleFactor },
    document: null,
    checks: {},
    network: null,
    errors: null,
    screenshot: { file: path.basename(screenshotPath), saved: false },
    failures: [],
    pass: false,
  };

  try {
    await page.emulateMediaFeatures([{ name: "prefers-reduced-motion", value: "no-preference" }]);
    await page.goto(htmlUrl, { waitUntil: "load", timeout });
    report.checks.motion = viewport.name === "desktop"
      ? await checkMotionLifecycle(page)
      : { status: "covered-on-desktop", pass: true };
    await settle(page, 250);
    await page.addStyleTag({ content: "*,*::before,*::after{animation-duration:0s!important;transition-duration:0s!important}html{scroll-behavior:auto!important}.panel{content-visibility:visible!important;contain-intrinsic-size:auto!important}" });
    await settle(page, 80);

    report.document = await page.evaluate(() => ({
      title: document.title,
      language: document.documentElement.lang || null,
      width: Math.max(document.documentElement.scrollWidth, document.body?.scrollWidth || 0),
      height: Math.max(document.documentElement.scrollHeight, document.body?.scrollHeight || 0),
    }));
    report.checks.controls = await testControlsPanel(page, viewport);
    report.checks.search = await testSearch(page);
    report.checks.filter = await testFilter(page);
    report.checks.details = await testDetails(page);
    report.checks.map = await testMap(page);
    report.checks.charts = await checkCharts(page);
    report.checks.stats = await testStatsTabs(page);
    report.checks.estimateModes = await testEstimateModes(page);
    report.checks.horizontalOverflow = await checkOverflow(page);

    // The deliverable starts compact. Restore that default before screenshots
    // so visual review measures the unobstructed dashboard rather than QA state.
    await setControlsExpanded(page, false);

    // Full-page screenshots do not themselves scroll the page. Walk it first
    // so IntersectionObserver-driven charts and reveal effects are rendered.
    await primeLazyContent(page);
    try {
      await page.screenshot({ path: screenshotPath, fullPage: true, type: "png", captureBeyondViewport: true });
      report.screenshot.saved = true;
    } catch (error) {
      report.screenshot.error = error.message;
    }
  } catch (error) {
    report.navigationError = { message: error.message, stack: error.stack || null };
    report.checks.motion ||= { status: "not-run", pass: false };
    report.checks.controls ||= { status: "not-run", pass: false };
    report.checks.search ||= { status: "not-run", pass: false };
    report.checks.filter ||= { status: "not-run", pass: false };
    report.checks.details ||= { status: "not-run", pass: false };
    report.checks.map ||= { status: "not-run", pass: false };
    report.checks.charts ||= { status: "not-run", pass: false };
    report.checks.stats ||= { status: "not-run", pass: false };
    report.checks.estimateModes ||= { status: "not-run", pass: false };
    report.checks.horizontalOverflow ||= { pass: false, reason: "navigation failed" };
  } finally {
    report.network = {
      policy: "Expected Google/OSM tile requests intercepted; every other HTTP(S) attempt fails QA",
      basemapTileRequests: compact(basemapTileRequests),
      externalRequests: compact(externalRequests),
    };
    report.errors = {
      console: compact(consoleErrors),
      page: compact(pageErrors),
      localRequests: compact(localRequestFailures),
    };
    report.failures = checkFailures(report);
    if (report.navigationError) report.failures.unshift(`navigation failed: ${report.navigationError.message}`);
    report.pass = report.failures.length === 0;
    await page.close().catch(() => {});
  }
  return report;
}

async function main() {
  let options;
  try {
    options = parseArguments(process.argv.slice(2));
  } catch (error) {
    process.stderr.write(`Error: ${error.message}\n\n`);
    printUsage();
    process.exitCode = 2;
    return;
  }
  if (options.help) {
    printUsage();
    return;
  }
  if (!options.html) {
    process.stderr.write("Error: an HTML path is required\n\n");
    printUsage();
    process.exitCode = 2;
    return;
  }

  const htmlPath = path.resolve(options.html);
  const stat = await fsp.stat(htmlPath).catch(() => null);
  if (!stat || !stat.isFile()) {
    process.stderr.write(`Error: HTML file not found: ${htmlPath}\n`);
    process.exitCode = 2;
    return;
  }
  const outputDirectory = path.resolve(options.out || path.join(__dirname, "qa-output", safeName(path.basename(htmlPath, path.extname(htmlPath)))));
  await fsp.mkdir(outputDirectory, { recursive: true });
  const reportPath = path.join(outputDirectory, "qa-report.json");
  const report = {
    schemaVersion: 1,
    generatedAt: new Date().toISOString(),
    html: htmlPath,
    outputDirectory,
    policy: {
      localFileEntryPoint: true,
      expectedBasemapTileRequestsIntercepted: true,
      unexpectedExternalNetworkRequestsAllowed: 0,
    },
    runtime: {
      node: process.version,
      puppeteer: loadDependency("puppeteer-core/package.json").version,
      chromiumPackage: loadDependency("@sparticuz/chromium/package.json").version,
    },
    viewports: [],
    summary: null,
  };

  let browser;
  try {
    const executablePath = await chromiumExecutable();
    report.runtime.chromiumExecutable = executablePath;
    const launchArgs = Array.from(new Set([
      ...chromium.args,
      "--allow-file-access-from-files",
      "--disable-background-networking",
      "--disable-component-update",
      "--disable-sync",
      "--metrics-recording-only",
      "--no-first-run",
    ]));
    browser = await puppeteer.launch({ executablePath, args: launchArgs, headless: true, defaultViewport: null });
    const htmlUrl = pathToFileURL(htmlPath).href;
    for (const viewport of VIEWPORTS) {
      report.viewports.push(await runViewport(browser, htmlUrl, outputDirectory, viewport, options.timeout));
    }
  } catch (error) {
    report.fatalError = { message: error.message, stack: error.stack || null };
  } finally {
    if (browser) await browser.close();
  }

  const failures = report.viewports.flatMap((viewport) => viewport.failures.map((failure) => `${viewport.name}: ${failure}`));
  if (report.fatalError) failures.unshift(`fatal: ${report.fatalError.message}`);
  const externalAttempts = report.viewports.reduce((sum, viewport) => sum + viewport.network.externalRequests.count, 0);
  const basemapTileAttempts = report.viewports.reduce((sum, viewport) => sum + viewport.network.basemapTileRequests.count, 0);
  report.summary = {
    pass: failures.length === 0 && report.viewports.length === VIEWPORTS.length,
    viewportsPassed: report.viewports.filter((viewport) => viewport.pass).length,
    viewportsTested: report.viewports.length,
    screenshotsSaved: report.viewports.filter((viewport) => viewport.screenshot.saved).length,
    basemapTileAttempts,
    externalRequestAttempts: externalAttempts,
    failures,
  };
  await fsp.writeFile(reportPath, `${JSON.stringify(report, null, 2)}\n`, "utf8");

  const label = report.summary.pass ? "PASS" : "FAIL";
  process.stdout.write(`${label}: ${report.summary.viewportsPassed}/${VIEWPORTS.length} viewports passed; report: ${reportPath}\n`);
  if (!report.summary.pass && !options.soft) process.exitCode = 1;
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error.message}\n`);
  process.exitCode = 2;
});
