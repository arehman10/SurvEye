#!/usr/bin/env node
"use strict";

// DOM and interaction tests only: jsdom does not render a browser layout.
// Chart.js and Leaflet are deliberately stubbed. These checks verify DOM
// controls, shared calculations and state, not charts, map tiles, pixels,
// responsive geometry, keyboard focus visibility or browser accessibility.
const fs = require("node:fs");
const path = require("node:path");
const assert = require("node:assert/strict");

function dependency(name) {
  try { return require(name); } catch (firstError) {
    const modules = process.env.SURVEYE_DOM_NODE_MODULES || process.env.SURVEYE_QA_NODE_MODULES;
    if (modules) return require(path.join(modules, name));
    throw new Error(`Cannot load ${name}. Run npm install in tests/ or set SURVEYE_DOM_NODE_MODULES.`, { cause: firstError });
  }
}
const { JSDOM, VirtualConsole } = dependency("jsdom");
const repo = path.resolve(__dirname, "..");
const htmlPath = path.resolve(process.argv[2] || path.join(repo, "examples/surveye_review_dashboard.html"));
const html = fs.readFileSync(htmlPath, "utf8");
const dashboardSource = fs.readFileSync(path.join(repo, "src/resources/dashboard.js"), "utf8");
const dashboardCss = fs.readFileSync(path.join(repo, "src/resources/dashboard.css"), "utf8");
const embeddedScripts = [...html.matchAll(/<script\b[^>]*>([\s\S]*?)<\/script>/gi)].map(match => match[1]);
const dataScript = embeddedScripts.find(source => /^\s*var META=/.test(source));
assert.ok(dataScript, "generated HTML includes its META, DATA and CONFIG payload");
// This suite runs the actual packaged resources, and fails if the example was
// not regenerated after an edit. Outer renderer whitespace is immaterial;
// all JavaScript content must match the current source byte-for-byte.
const embeddedDashboard = embeddedScripts.find(source => source.trim() === dashboardSource.trim());
assert.ok(embeddedDashboard, "generated HTML dashboard.js matches current source; rebuild and regenerate the example");
assert.ok(html.includes(dashboardCss.trim()), "generated HTML dashboard.css matches current source; rebuild and regenerate the example");
assert.ok(!html.includes("workspace-ready"), "generated HTML contains no workspace runtime or styling");

function chartStub(window) {
  function Chart(canvas, config) { this.canvas = canvas; this.config = config; this.options = config.options; this.data = config.data; Chart.instances.push(this); }
  Chart.instances = [];
  Chart.defaults = { font: {}, animation: {}, plugins: { legend: { labels: {} }, tooltip: {} } };
  Chart.prototype.destroy = function () { this.destroyed = true; };
  Chart.prototype.resize = function () {};
  Chart.prototype.update = function () {};
  window.Chart = Chart;
}
function leafletStub(window) {
  const document = window.document;
  function group() { return { layers: [], addTo() { return this; }, clearLayers() { this.layers = []; }, getLayers() { return this.layers; } }; }
  function marker() { const element = document.createElementNS("http://www.w3.org/2000/svg", "path"); return {
    addTo(parent) { if (parent.layers) parent.layers.push(this); return this; },
    bindTooltip() { return this; }, bindPopup() { return this; }, getElement() { return element; }, openPopup() { return this; },
  }; }
  function control() { const container = document.createElement("div"), toggle = document.createElement("a"); toggle.className = "leaflet-control-layers-toggle"; container.appendChild(toggle); return { addTo() { return this; }, getContainer() { return container; } }; }
  function latLng(lat, lng) { if (Array.isArray(lat)) return { lat: lat[0], lng: lat[1] }; return { lat, lng }; }
  const L = {
    tileLayer: () => ({ addTo() { return this; }, on() { return this; } }), layerGroup: group, circleMarker: marker,
    polyline: marker, svg: () => ({}), latLng, point: (x, y) => ({ x, y }),
    latLngBounds(points) { return { getCenter: () => latLng(points[0]), toBBoxString: () => "stubbed-map-bounds" }; },
    control: { zoom: control, attribution: control, scale: control, layers: control },
    map(id) { const container = document.getElementById(id); return {
      getContainer: () => container, getZoom: () => 9, getMaxZoom: () => 21,
      setView() { return this; }, fitBounds() { return this; }, on() { return this; },
      whenReady(callback) { callback(); return this; }, invalidateSize() { return this; },
      project: values => ({ x: values[1] * 100, y: values[0] * 100 }),
      unproject: point => latLng(point.y / 100, point.x / 100),
      latLngToContainerPoint: values => ({ x: values[1] * 100, y: values[0] * 100 }),
      containerPointToLatLng: values => latLng(values[1] / 100, values[0] / 100),
    }; },
  };
  window.L = L;
}
async function openDom(hash = "", options = {}) {
  const errors = [], virtualConsole = new VirtualConsole();
  virtualConsole.on("jsdomError", error => errors.push(`jsdom: ${error.message}`));
  virtualConsole.on("error", (...args) => errors.push(args.map(String).join(" ")));
  // outside-only evaluates only the selected payload and verified packaged scripts.
  // No resource loader is enabled; embedded libraries and network URLs do not run.
  const dom = new JSDOM(html, { runScripts: "outside-only", url: "https://surveye.test/dashboard.html" + hash, virtualConsole });
  const window = dom.window, document = window.document, timers = new Map();
  let timerId = 0;
  if (document.readyState === "loading") await new Promise(resolve => document.addEventListener("DOMContentLoaded", resolve, { once: true }));
  window.addEventListener("error", event => errors.push(event.error ? event.error.stack : event.message));
  window.matchMedia = () => ({ matches: false, addListener() {}, removeListener() {} });
  window.HTMLElement.prototype.scrollIntoView = function () {};
  window.scrollTo = () => {};
  window.print = () => {};
  // Drain only explicitly scheduled UI work. Deterministic timers prevent a
  // lingering map/animation timer from keeping this Node process alive.
  window.setTimeout = callback => { const id = ++timerId; timers.set(id, callback); return id; };
  window.clearTimeout = id => timers.delete(id);
  window.requestAnimationFrame = callback => window.setTimeout(callback);
  window.cancelAnimationFrame = window.clearTimeout;
  window.IntersectionObserver = class { observe() {} disconnect() {} };
  window.ResizeObserver = class { observe() {} disconnect() {} };
  chartStub(window); leafletStub(window);
  window.eval(dataScript);
  if (options.mutatePayload) options.mutatePayload(window);
  const downloads = [];
  // Use the engine's data URI fallback and intercept only the final navigation.
  window.URL.createObjectURL = undefined;
  window.HTMLAnchorElement.prototype.click = function () {
    if (this.download) downloads.push({ href: this.href, filename: this.download });
    else this.dispatchEvent(new window.MouseEvent("click", { bubbles: true, cancelable: true }));
  };
  const graphGrids = [...document.querySelectorAll("details.story > .grid")].map(grid => ({
    grid, story: grid.parentElement, panels: [...grid.querySelectorAll(":scope > .panel")],
  }));
  window.eval(embeddedDashboard);
  function flush() {
    for (let round = 0; timers.size && round < 100; round++) {
      const queue = [...timers.entries()]; timers.clear();
      queue.forEach(([, callback]) => { try { callback(); } catch (error) { errors.push(error.stack); } });
    }
    assert.equal(timers.size, 0, "UI timers settle without a recurring loop");
  }
  flush();
  return { dom, window, document, errors, graphGrids, downloads, flush, close() { timers.clear(); dom.window.close(); } };
}
function element(session, selector) {
  const node = session.document.querySelector(selector);
  assert.ok(node, `control exists: ${selector}`); return node;
}
function click(session, selector) { element(session, selector).click(); session.flush(); }
function change(session, selector, value) {
  const node = element(session, selector); if (node.type === "checkbox") node.checked = value; else node.value = value;
  node.dispatchEvent(new session.window.Event("change", { bubbles: true })); session.flush(); return node;
}
function search(session, value) { const input = element(session, "#indicator-search"); input.value = value; input.dispatchEvent(new session.window.Event("input", { bubbles: true })); session.flush(); }
function completeGraphGrid(session) {
  const originalPanels = session.graphGrids.flatMap(record => record.panels);
  assert.deepEqual([...session.document.querySelectorAll("details.story .panel")], originalPanels, "every chart remains in original section order");
  for (const { grid, story, panels } of session.graphGrids) {
    assert.equal(grid.parentElement, story, "chart grid remains in its section");
    assert.deepEqual([...grid.querySelectorAll(":scope > .panel")], panels, "all original panel nodes retain their positions");
  }
}
function matchedCount(session, expected) {
  const nodes = [...session.document.querySelectorAll("[data-matched]")];
  assert.ok(nodes.length, "original sample counters exist");
  for (const node of nodes) assert.equal(node.textContent, expected.toLocaleString(session.window.CONFIG.locale || "en"), "sample counter agrees with independent row count");
}
function statsCell(panel, label) {
  const cell = [...panel.querySelectorAll(".stats-table th")].find(node => node.textContent === label);
  assert.ok(cell, `visible Stats table includes ${label}`);
  return cell.nextElementSibling.textContent;
}
function closeNumber(actual, expected, message) {
  assert.ok(Math.abs(actual - expected) <= Math.max(1, Math.abs(expected)) * 1e-10, `${message}: expected ${expected}, received ${actual}`);
}
function median(values) {
  const sorted = values.slice().sort((a, b) => a - b), middle = Math.floor(sorted.length / 2);
  return sorted.length % 2 ? sorted[middle] : (sorted[middle - 1] + sorted[middle]) / 2;
}
function csvRows(text) {
  const rows = [], row = []; let cell = "", quoted = false;
  for (let index = 0; index < text.length; index++) {
    const character = text[index];
    if (character === '"') {
      if (quoted && text[index + 1] === '"') { cell += '"'; index++; }
      else quoted = !quoted;
    } else if (!quoted && (character === "," || character === "\n" || character === "\r")) {
      row.push(cell); cell = "";
      if (character !== ",") { rows.push(row.splice(0)); if (character === "\r" && text[index + 1] === "\n") index++; }
    } else cell += character;
  }
  if (cell || row.length) { row.push(cell); rows.push(row); }
  return rows;
}

(async () => {
  const sessions = [];
  try {
    const session = await openDom(); sessions.push(session);
    const { document, window } = session, D = window.SurvEyeDashboard;
    assert.ok(D, "packaged dashboard initializes its shared analytical API");
    assert.equal(window.DATA.length, 240, "review fixture contains 240 invented interviews");
    assert.equal(document.querySelectorAll("details.story .panel").length, 117, "review fixture retains 117 chart panels");
    const members = new Set([...document.querySelectorAll("canvas.chart")].flatMap(canvas => canvas.getAttribute("data-members").split(/\s+/)));
    assert.equal(members.size, 120, "117 panels cover all 120 original questionnaire variables");
    assert.ok([...members].every(variable => window.META[variable]), "every displayed variable has original metadata");
    assert.equal(document.querySelectorAll('#surveye-workspace, [id^="workspace-"], .ws-pane, .ws-tabs').length, 0, "original page has no workspace shell or navigation");
    for (const selector of [".brandbar", ".topbar", "#section-nav", ".hero h1", "#dashboard-controls", ".kpis", ".highlight-grid", "#summary-profile", "#spatial-map"])
      assert.ok(element(session, selector).textContent.trim(), `original page retains ${selector}`);
    assert.ok(element(session, "#controls-body").contains(element(session, "#indicator-search")), "indicator search remains in original filter controls");
    const stories = [...document.querySelectorAll("details.story")];
    assert.equal(stories.length, 6, "six original questionnaire sections are retained");
    assert.deepEqual(stories.map(story => story.open), [true, false, false, false, false, false], "original six-section page starts with its first section open");
    completeGraphGrid(session); matchedCount(session, 240);
    assert.equal(element(session, "#controls-toggle").getAttribute("aria-expanded"), "false");
    assert.equal(element(session, "#controls-body").hidden, true);
    assert.equal(element(session, "#reset").disabled, true);

    click(session, "#collapse-all");
    assert.ok(stories.every(story => !story.open), "Collapse all closes every chart section");
    click(session, "#controls-toggle");
    assert.equal(element(session, "#controls-body").hidden, false, "filter disclosure opens");
    const sectionLink = [...document.querySelectorAll("#section-nav a")].find(link => stories.includes(document.getElementById(link.hash.slice(1))));
    assert.ok(sectionLink, "original section navigation targets a chart section");
    sectionLink.addEventListener("click", event => event.preventDefault(), { once: true });
    sectionLink.click(); session.flush();
    assert.equal(document.getElementById(sectionLink.hash.slice(1)).open, true, "section jump opens its collapsed target");
    assert.equal(stories.filter(story => story.open).length, 1, "section jump opens only its target");
    assert.equal(element(session, "#controls-body").hidden, true, "section jump closes filter disclosure");
    click(session, "#expand-all");
    assert.ok(stories.every(story => story.open), "Expand all restores the original chart sheet");

    // These are distinct string categories in the actual generated fixture.
    // Compare to literal row values, not the dashboard's category comparator.
    click(session, "#controls-toggle");
    const western = window.DATA.filter(row => row.region === "01"), central = window.DATA.filter(row => row.region === "1");
    assert.ok(western.length && central.length, "fixture contains both leading-zero and canonical string codes");
    click(session, '.chip[data-filter="region"][data-value="01"]');
    assert.deepEqual(Array.from(D.rows()), Array.from(western), "01 filter selects only literal 01 records");
    matchedCount(session, western.length);
    assert.equal(element(session, '#active-filter-count').dataset.activeCount, "1");
    click(session, '.chip[data-filter="region"][data-value="1"]');
    assert.equal(D.rows().length, western.length + central.length, "two selected codes form a union");
    click(session, '.chip[data-filter="region"][data-value="01"]');
    assert.deepEqual(Array.from(D.rows()), Array.from(central), "1 filter excludes distinct 01 records");
    assert.equal(element(session, '.chip[data-filter="region"][data-value="01"]').getAttribute("aria-pressed"), "false");
    matchedCount(session, central.length);

    const canvas = element(session, 'canvas.chart[data-variable="sales"]'), panel = canvas.closest(".panel");
    const statsTab = panel.querySelector('[data-panel-view="stats"]'), chartTab = panel.querySelector('[data-panel-view="distribution"]');
    const statsPane = panel.querySelector('[data-panel-view-pane="stats"]');
    statsTab.click(); session.flush();
    assert.equal(statsTab.getAttribute("aria-selected"), "true", "Stats tab is selected");
    assert.equal(statsPane.getAttribute("aria-hidden"), "false", "Stats pane is exposed");
    const weightedMedian = D.summary("sales").number;
    const validSales = central.map(row => row.sales).filter(value => typeof value === "number" && Number.isFinite(value));
    assert.equal(statsCell(panel, "Valid raw n"), String(validSales.length), "Stats uses the filtered valid raw n");
    assert.equal(statsCell(panel, "Missing/excluded"), String(central.length - validSales.length));
    change(session, "#weight-toggle", false);
    const localMedian = median(validSales), mean = validSales.reduce((sum, value) => sum + value, 0) / validSales.length;
    const sd = Math.sqrt(validSales.reduce((sum, value) => sum + (value - mean) ** 2, 0) / (validSales.length - 1));
    assert.equal(D.weightsActive(), false);
    closeNumber(D.summary("sales").number, localMedian, "unweighted median agrees with independent sorted values");
    assert.equal(statsCell(panel, "Median"), D.metricFmt("sales", localMedian, 2), "open Stats refreshes after weighting changes");
    assert.equal(statsCell(panel, "Mean + 3 SD"), D.metricFmt("sales", mean + 3 * sd, 2), "Stats reports independently calculated mean plus three sample SDs");
    assert.equal(panel.querySelector(".stats-note"), null, "unweighted Stats removes the weighted note");
    change(session, "#usd-toggle", true);
    closeNumber(D.summary("sales").number, localMedian / window.CONFIG.usd.rate, "USD converts summary once");
    assert.equal(statsCell(panel, "Median"), D.metricFmt("sales", localMedian / window.CONFIG.usd.rate, 2), "open Stats refreshes on currency change");
    assert.match(statsPane.textContent, /USD/, "Stats identifies the selected currency");
    change(session, "#weight-toggle", true);
    closeNumber(D.summary("sales").number, weightedMedian / window.CONFIG.usd.rate, "weight control restores weighted USD median");
    assert.equal(D.rows().length, central.length, "presentation controls preserve the selected interviews");
    assert.ok(panel.querySelector(".stats-note"), "weighted Stats note is restored");

    // Refresh must reach the visible Stats table even while its chart is hidden.
    D.setFilter("region", ["01"]); session.flush();
    assert.equal(statsCell(panel, "Valid raw n"), String(western.filter(row => typeof row.sales === "number").length), "open Stats refreshes immediately after filtering");
    D.setFilter("region", ["1"]); session.flush();
    chartTab.click(); session.flush();
    assert.equal(chartTab.getAttribute("aria-selected"), "true", "distribution tab restores chart view");
    assert.equal(statsPane.getAttribute("aria-hidden"), "true", "distribution tab hides Stats");
    // jsdom has no visible chart geometry. Build explicitly and inspect only
    // the data model passed to the Chart stub, never rendering or pixels.
    D.ensureBuilt(canvas);
    const chart = window.Chart.instances.filter(item => item.canvas === canvas && !item.destroyed).at(-1);
    assert.ok(chart, "original histogram retains a live chart data model");
    const bins = Array.from(chart.data.datasets[0].data);
    assert.equal(bins.reduce((sum, bin) => sum + bin.raw, 0), validSales.length, "histogram raw frequencies equal independently filtered valid n");
    closeNumber(bins.reduce((sum, bin) => sum + bin.y, 0), central.filter(row => typeof row.sales === "number").reduce((sum, row) => sum + row._w, 0), "histogram weighted frequencies retain sample weights");

    // The original search hides matching chart panels and whole empty sections.
    search(session, "sales");
    const panels = [...document.querySelectorAll("details.story .panel")];
    assert.ok(panels.some(item => item.classList.contains("hidden")), "search hides nonmatching chart panels");
    assert.ok(panels.some(item => !item.classList.contains("hidden")), "search retains matching chart panels");
    for (const item of panels) assert.equal(item.classList.contains("hidden"), !item.getAttribute("data-search").includes("sales"), "panel search matches its original searchable wording and variables");
    for (const story of stories) assert.equal(story.style.display === "none", !story.querySelector(".panel:not(.hidden)"), "search hides sections with no matching panel");
    assert.equal(D.rows().length, central.length, "indicator search preserves sample scope");
    completeGraphGrid(session);

    change(session, "#weight-toggle", false);
    const hash = window.location.hash, shared = JSON.parse(decodeURIComponent(hash.slice(6)));
    assert.deepEqual(shared.f.region, ["1"], "shared view retains the exact string filter code");
    assert.equal(shared.q, "sales"); assert.equal(shared.w, 0); assert.equal(shared.u, 1);
    assert.equal(Object.hasOwn(shared, "workspace"), false, "shared original view contains no workspace route");
    const restored = await openDom(hash); sessions.push(restored);
    assert.equal(restored.window.SurvEyeDashboard.rows().length, central.length, "shared view restores sample scope");
    assert.equal(restored.window.SurvEyeDashboard.weightsActive(), false);
    assert.equal(restored.window.SurvEyeDashboard.usdActive(), true);
    assert.equal(element(restored, "#indicator-search").value, "sales", "shared view restores original indicator search");
    assert.deepEqual([...restored.document.querySelectorAll(".panel")].map(item => item.classList.contains("hidden")), panels.map(item => item.classList.contains("hidden")), "shared view restores the same visible panels");

    search(session, "variable-that-does-not-exist");
    assert.ok(panels.every(item => item.classList.contains("hidden")), "empty search hides all panels");
    assert.ok(element(session, "#no-results").classList.contains("show"), "empty search exposes original no-results explanation");
    click(session, "#reset");
    assert.equal(element(session, "#indicator-search").value, "", "Reset all clears search");
    assert.equal(D.rows().length, window.DATA.length, "Reset all restores every interview");
    assert.ok(panels.every(item => !item.classList.contains("hidden")), "Reset all restores every panel");
    assert.ok(stories.every(story => story.style.display !== "none"), "Reset all restores every section");
    assert.ok(!element(session, "#no-results").classList.contains("show"));
    assert.equal(element(session, "#reset").disabled, true);
    assert.ok([...document.querySelectorAll(".chip[data-filter]")].every(chip => chip.getAttribute("aria-pressed") === "false"));
    matchedCount(session, 240); completeGraphGrid(session);
    statsTab.click(); session.flush();
    D.setFilter("region", ["not-an-observed-code"]); session.flush();
    matchedCount(session, 0);
    assert.match(statsPane.textContent, /No numeric values/, "empty filtered sample clears the visible Stats table");
    click(session, "#reset");
    assert.equal(statsCell(panel, "Valid raw n"), String(window.DATA.filter(row => typeof row.sales === "number").length), "reset repopulates open Stats with the complete sample");

    const mapDetails = element(session, "#spatial-map");
    stories.forEach((story, index) => { story.open = index % 2 === 0; });
    mapDetails.open = false;
    const originalStoryStates = stories.map(story => story.open);
    window.dispatchEvent(new window.Event("beforeprint")); session.flush();
    assert.ok(stories.every(story => story.open), "printing opens every chart section");
    assert.equal(mapDetails.open, true, "printing opens the embedded map");
    window.dispatchEvent(new window.Event("beforeprint")); session.flush();
    window.dispatchEvent(new window.Event("afterprint")); session.flush();
    assert.deepEqual(stories.map(story => story.open), originalStoryStates, "after printing restores section states even after repeated beforeprint events");
    assert.equal(mapDetails.open, false, "after printing restores the map disclosure");
    completeGraphGrid(session);

    // A separate payload extension exercises the CSV underscore regression
    // without changing the committed fixture or its 120-variable contract.
    const exporting = await openDom("", { mutatePayload(win) {
      win.META._income = { label: "Underscore income", kind: "continuous" };
      win.DATA.forEach((row, index) => { row._income = 1000 + index; });
    } }); sessions.push(exporting);
    click(exporting, '.chip[data-filter="region"][data-value="01"]');
    change(exporting, "#usd-toggle", true);
    click(exporting, "#download-data");
    assert.equal(exporting.downloads.length, 1, "Download data triggers one CSV download");
    const download = exporting.downloads[0];
    assert.match(download.filename, /-filtered-data\.csv$/);
    const csv = csvRows(decodeURIComponent(download.href.slice(download.href.indexOf(",") + 1)).replace(/^\ufeff/, ""));
    const header = csv.shift(), source = exporting.window.DATA.filter(row => row.region === "01");
    assert.ok(header.includes("_income"), "CSV retains an ordinary underscore-prefixed survey variable");
    for (const reserved of ["_w", "_x", "_y", "_lat", "_lon", "_mapby"]) assert.ok(!header.includes(reserved), `CSV excludes generated helper ${reserved}`);
    assert.equal(csv.length, source.length, "CSV exports exactly the selected interviews");
    assert.equal(csv[0][header.indexOf("_income")], String(source[0]._income));
    assert.equal(csv[0][header.indexOf("sales")], source[0].sales == null ? "" : String(source[0].sales), "USD display mode preserves raw local-currency CSV values");
    assert.ok(csv.every(row => row[header.indexOf("region")] === "Western"), "CSV applies exact 01 filtering and labels");

    assert.equal(document.querySelectorAll("[id]").length, new Set([...document.querySelectorAll("[id]")].map(node => node.id)).size, "original generated ids are unique");
    for (const candidate of sessions) assert.deepEqual(candidate.errors, [], "no DOM or application errors");
    console.log("PASS original dashboard DOM: packaged resource parity, 240 interviews, 120 variables, 117 panels, original header and section grids, section controls, exact 01/1 filters, Stats refresh, weight/USD modes, chart data, original search/reset, shared views, empty samples and CSV export");
    console.log("LIMITS DOM emulation only; Chart.js and Leaflet stubbed; no browser rendering, pixel, responsive-layout or map-tile verification");
  } finally { sessions.forEach(session => session.close()); }
})().catch(error => { console.error(error.stack); process.exitCode = 1; });
