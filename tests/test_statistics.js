#!/usr/bin/env node
"use strict";

const fs = require("node:fs");
const path = require("node:path");
const vm = require("node:vm");

const source = fs.readFileSync(path.resolve(__dirname, "../src/resources/dashboard.js"), "utf8");
const config = {
  weighted: false,
  weightType: "",
  showCI: true,
  ciLevel: 95,
  density: "compact",
  direction: "ltr",
  uiLanguage: "english",
  locale: "en",
  strings: {},
  map: null,
};
const windowStub = {
  __SURVEYE_TEST_ONLY__: true,
  matchMedia: () => ({ matches: false }),
  innerWidth: 1024,
  innerHeight: 768,
};
const documentStub = { body: {}, documentElement: { clientWidth: 1024, clientHeight: 768 }, hidden: false, getElementById: () => null };
function ChartStub(canvas, configuration) {
  this.canvas = canvas;
  this.config = configuration;
  ChartStub.instances.push(this);
}
ChartStub.instances = [];
ChartStub.defaults = {
  font: {},
  animation: {},
  plugins: { legend: { labels: {} }, tooltip: {} },
};

const context = {
  CONFIG: config,
  META: {},
  DATA: [],
  Chart: ChartStub,
  Intl,
  console,
  window: windowStub,
  document: documentStub,
  getComputedStyle: () => ({ getPropertyValue: () => "" }),
};
vm.runInNewContext(source, context, { filename: "dashboard.js" });
if (ChartStub.defaults.animation.duration !== 800) {
  throw new Error(
    `dashboard animation: expected 800 ms, received ${ChartStub.defaults.animation.duration}`,
  );
}
if (ChartStub.defaults.animation.easing !== "easeOutCubic") {
  throw new Error(
    `dashboard animation easing: expected easeOutCubic, received ${ChartStub.defaults.animation.easing}`,
  );
}
function ReducedChartStub() {}
ReducedChartStub.defaults = {
  font: {},
  animation: {},
  plugins: { legend: { labels: {} }, tooltip: {} },
};
vm.runInNewContext(source, {
  CONFIG: { ...config },
  META: {},
  DATA: [],
  Chart: ReducedChartStub,
  Intl,
  console,
  window: { __SURVEYE_TEST_ONLY__: true, matchMedia: () => ({ matches: true }) },
  document: { body: {}, hidden: false, getElementById: () => null },
  getComputedStyle: () => ({ getPropertyValue: () => "" }),
}, { filename: "dashboard-reduced-motion.js" });
if (ReducedChartStub.defaults.animation.duration !== 0) {
  throw new Error(
    `reduced-motion animation: expected 0 ms, received ${ReducedChartStub.defaults.animation.duration}`,
  );
}
const stats = windowStub.__SURVEYE_STATS__;
if (!stats) throw new Error("dashboard.js did not expose its statistics test hook");

function chartCanvas(rect, options = {}) {
  const section = { open: options.open !== false };
  const panel = { classList: { contains: (name) => name === "hidden" && options.hidden === true } };
  const pane = { classList: { contains: (name) => name === "is-active" && options.active !== false } };
  return {
    closest(selector) {
      if (selector === "details.story") return section;
      if (selector === ".panel") return panel;
      if (selector === "[data-panel-view-pane]") return options.hasPane === false ? null : pane;
      return null;
    },
    getBoundingClientRect: () => rect,
  };
}
const visibleCanvas = chartCanvas({ left: 10, right: 410, top: 100, bottom: 300, width: 400, height: 200 });
if (!stats.chartIsGenuinelyVisible(visibleCanvas)) throw new Error("visible chart was not recognized as visible");
const barelyVisibleCanvas = chartCanvas({ left: 10, right: 410, top: 750, bottom: 950, width: 400, height: 200 });
if (stats.chartIsGenuinelyVisible(barelyVisibleCanvas)) throw new Error("chart below the 12% visibility threshold was built too early");
if (stats.chartIsGenuinelyVisible(chartCanvas({ left: 10, right: 410, top: 100, bottom: 300, width: 400, height: 200 }, { open: false }))) throw new Error("chart in a closed section was treated as visible");
documentStub.hidden = true;
if (stats.chartIsGenuinelyVisible(visibleCanvas)) throw new Error("chart was treated as visible while the document was hidden");
documentStub.hidden = false;

function close(actual, expected, tolerance = 1e-10, label = "value") {
  if (!Number.isFinite(actual) || Math.abs(actual - expected) > tolerance) {
    throw new Error(`${label}: expected ${expected}, received ${actual}`);
  }
}

function equal(actual, expected, label) {
  if (actual !== expected) throw new Error(`${label}: expected ${expected}, received ${actual}`);
}

// The visual grammar uses stable, semantic colors. Codes—not a second,
// incomplete translation regex—identify binary roles in generated metadata.
context.META.semantic_binary = {
  kind: "yesno",
  labels: { "7": "Evet", "9": "Hayır", "99": "Refused" },
  order: ["7", "9", "99"],
  affirmative: ["7"],
  negative: ["9"],
  special: ["99"],
};
equal(stats.colorFor("semantic_binary", "7", 2), "#009fda", "binary affirmative color");
equal(stats.colorFor("semantic_binary", "9", 0), "#d9d3c5", "binary negative color");
equal(stats.colorFor("semantic_binary", "99", 1), "#9aa0a6", "special response color");

context.META.ordered_colors = {
  kind: "donut",
  labels: {},
  order: Array.from({ length: 12 }, (_, index) => String(index + 1)),
  special: [],
};
equal(stats.stableColorIndex("ordered_colors", "2", 0), 1,
  "stable category index ignores filtered display position");
const orderedColors = context.META.ordered_colors.order.map((code, index) =>
  stats.colorFor("ordered_colors", code, 11 - index));
equal(new Set(orderedColors).size, 12, "first twelve category colors are distinct");

equal(stats.balancedRows(1, 3).join("-"), "1", "one-card row balance");
equal(stats.balancedRows(4, 3).join("-"), "2-2", "four-card row balance");
equal(stats.balancedRows(5, 3).join("-"), "3-2", "five-card row balance");
equal(stats.balancedRows(7, 3).join("-"), "3-2-2", "seven-card row balance");
equal(stats.balancedRows(5, 2).join("-"), "2-2-1", "tablet row balance");
equal(stats.balancedRows(3, 1).join("-"), "1-1-1", "mobile row balance");

// Stata's default percentile rule uses the first cumulative weight strictly
// above Np and averages adjacent observations when Np is an exact boundary.
equal(stats.quantile([1, 2, 3, 4], 0.25), 1.5, "unweighted p25 boundary");
equal(stats.quantile([1, 2, 3, 4], 0.50), 2.5, "unweighted median boundary");
equal(stats.quantile([1, 2, 3, 4, 5], 0.25), 2, "unweighted p25 nonboundary");
equal(stats.quantile([1, 2, 3, 4, 5], 0.75), 4, "unweighted p75 nonboundary");

const points = [
  { value: 1, w: 1 },
  { value: 2, w: 1 },
  { value: 3, w: 2 },
];
config.weighted = true;
config.weightType = "fweight";
equal(stats.weightedQuantile(points, 0.25), 1.5, "fweight p25 boundary");
equal(stats.weightedQuantile(points, 0.50), 2.5, "fweight median boundary");

const sdPoints = [{ value: 1, w: 1 }, { value: 3, w: 2 }];
const fMean = stats.weightedMean(sdPoints);
close(fMean, 7 / 3, 1e-12, "fweight mean");
close(stats.weightedStd(sdPoints, fMean, 3), Math.sqrt(4 / 3), 1e-12, "fweight SD");

config.weightType = "aweight";
const aMean = stats.weightedMean(sdPoints);
close(stats.weightedStd(sdPoints, aMean, 3), 4 / 3, 1e-12, "normalized aweight SD");
const scaled = [{ value: 1, w: 10 }, { value: 3, w: 20 }];
const scaledMean = stats.weightedMean(scaled);
close(scaledMean, aMean, 1e-12, "aweight mean scale invariance");
close(stats.weightedStd(scaled, scaledMean, 30), 4 / 3, 1e-12, "aweight SD scale invariance");

const category = { n: 2, total: 3, sumW2: 5 };
close(stats.effectiveN(category), 9 / 5, 1e-12, "Kish effective n");
const aInterval = stats.confidenceInterval(category, 2);
if (!aInterval || !aInterval.approx) throw new Error("aweight CI must be present and labelled approximate");

config.weightType = "fweight";
equal(stats.effectiveN(category), 3, "expanded fweight effective n");
const fInterval = stats.confidenceInterval(category, 2);
if (!fInterval || fInterval.approx) throw new Error("fweight CI must use expanded count without approximation flag");

config.weightType = "iweight";
equal(stats.confidenceInterval(category, 2), null, "iweight CI suppression");

// Binary and completion cards share the stacked split-bar renderer. They are
// CI-free even under explicit opt-in: no whisker plugin, visible CI text,
// tooltip CI, or accessible summary CI may leak into the card.
function splitCanvas(id) {
  const attributes = { "aria-label": "Binary question" };
  return {
    id,
    getAttribute: (name) => attributes[name] || null,
    setAttribute: (name, value) => { attributes[name] = String(value); },
    removeAttribute: (name) => { delete attributes[name]; },
    attributes,
  };
}
config.weighted = false;
config.weightType = "";
config.showCI = false;
equal(stats.confidenceInterval({ n: 10, total: 10, sumW2: 10 }, 7), null,
  "default CI suppression");
const defaultCanvas = splitCanvas("binary-default");
stats.buildSplit(defaultCanvas, "binary_default", [
  { label: "Yes", count: 7, raw: 7, color: "#008877" },
  { label: "No", count: 3, raw: 3, color: "#cc4433" },
], 10, 0);
let splitChart = ChartStub.instances[ChartStub.instances.length - 1].config;
if ((splitChart.plugins || []).some((plugin) => plugin?.id === "barConfidenceIntervals")) {
  throw new Error("default binary split bar registered a CI whisker plugin");
}
if ((defaultCanvas.attributes["data-chart-summary"] || "").includes("CI")) {
  throw new Error("default binary accessible summary leaked CI text");
}

config.showCI = true;
const explicitInterval = stats.confidenceInterval({ n: 10, total: 10, sumW2: 10 }, 7);
if (!explicitInterval) throw new Error("explicit ci did not enable interval computation");
const optedInCanvas = splitCanvas("binary-opted-in");
const optedInItems = [
  { label: "Yes", count: 7, raw: 7, color: "#008877", interval: explicitInterval },
  { label: "No", count: 3, raw: 3, color: "#cc4433", interval: explicitInterval },
];
stats.buildSplit(optedInCanvas, "binary_opted_in", optedInItems, 10, 0);
splitChart = ChartStub.instances[ChartStub.instances.length - 1].config;
if ((splitChart.plugins || []).some((plugin) => plugin?.id === "barConfidenceIntervals")) {
  throw new Error("opted-in binary split bar registered a CI whisker plugin");
}
if ((optedInCanvas.attributes["data-chart-summary"] || "").includes("CI")) {
  throw new Error("opted-in binary accessible summary leaked CI text");
}
const splitTooltip = splitChart.options.plugins.tooltip.callbacks.label({
  datasetIndex: 0,
  parsed: { x: 70 },
});
if (splitTooltip.includes("CI")) throw new Error("opted-in binary tooltip leaked CI text");
if (optedInItems.some((item) => item.interval)) {
  throw new Error("split renderer retained CI state on a binary/completion item");
}

config.weighted = false;
const unweighted = [{ value: 1, w: 1 }, { value: 3, w: 1 }];
close(stats.weightedStd(unweighted, 2, 2), Math.sqrt(2), 1e-12, "unweighted sample SD");

// Expanded multiselects distinguish an answered all-zero row ([]) from an
// unanswered row (null).  The empty array contributes to the respondent
// denominator but, correctly, to no option numerator.
const multiselectRows = [
  { q: ["1"], _w: 1 },
  { q: ["2"], _w: 2 },
  { q: [], _w: 3 },
  { q: null, _w: 4 },
];
const unweightedMulti = stats.categorical("q", multiselectRows, true);
equal(unweightedMulti.n, 3, "multiselect answered respondent n");
equal(unweightedMulti.total, 3, "multiselect unweighted denominator");
equal(unweightedMulti.counts["1"], 1, "multiselect option 1 count");
equal(unweightedMulti.counts["2"], 1, "multiselect option 2 count");
equal(unweightedMulti.respondents.length, 3, "multiselect respondent records");
equal(unweightedMulti.respondents[2].keys.length, 0, "multiselect empty answered selection");

config.weighted = true;
config.weightType = "aweight";
const weightedMulti = stats.categorical("q", multiselectRows, true);
equal(weightedMulti.n, 3, "weighted multiselect answered respondent n");
equal(weightedMulti.total, 6, "weighted multiselect denominator");
equal(weightedMulti.counts["1"], 1, "weighted multiselect option 1 count");
equal(weightedMulti.counts["2"], 2, "weighted multiselect option 2 count");

// An explicitly configured missing code is removed item-by-item. A row with
// only missing codes leaves the denominator; a mixed row retains its valid
// selections; and a genuinely answered empty array remains in the denominator.
context.META.q = { order: ["1", "-9"], special: ["-9"], missing: ["-9"] };
const missingCodeMultiRows = [
  { q: ["1"], _w: 1 },
  { q: ["-9"], _w: 2 },
  { q: ["1", "-9"], _w: 3 },
  { q: [], _w: 4 },
  { q: null, _w: 5 },
];
config.weighted = false;
const missingCodeMulti = stats.categorical("q", missingCodeMultiRows, true);
equal(missingCodeMulti.n, 3, "multiselect missing-code respondent n");
equal(missingCodeMulti.total, 3, "multiselect missing-code denominator");
equal(missingCodeMulti.counts["1"], 2, "multiselect mixed valid count");
equal(missingCodeMulti.counts["-9"], undefined, "multiselect missing code removed");
equal(missingCodeMulti.respondents[2].keys.length, 0, "multiselect answered empty row retained");

config.weighted = true;
config.weightType = "aweight";
const weightedMissingCodeMulti = stats.categorical("q", missingCodeMultiRows, true);
equal(weightedMissingCodeMulti.n, 3, "weighted multiselect missing-code respondent n");
equal(weightedMissingCodeMulti.total, 8, "weighted multiselect missing-code denominator");
equal(weightedMissingCodeMulti.sumW2, 26, "weighted multiselect missing-code sum of squares");
equal(weightedMissingCodeMulti.counts["1"], 4, "weighted multiselect mixed valid count");

// Category and map-group codes are untrusted data keys. Reserved JavaScript
// property names must behave exactly like ordinary codes rather than reading
// from or mutating Object.prototype.
const reservedLabels = Object.create(null);
reservedLabels["__proto__"] = "Prototype code";
reservedLabels.constructor = "Constructor code";
reservedLabels.toString = "String code";
context.META.reserved = {
  order: ["__proto__", "constructor", "toString"],
  labels: reservedLabels,
  special: [],
  missing: [],
};
config.weighted = false;
const reservedCategory = stats.categorical("reserved", [
  { reserved: "__proto__" },
  { reserved: "constructor" },
  { reserved: "toString" },
], false);
equal(Object.getPrototypeOf(reservedCategory.counts), null, "category count dictionary prototype");
equal(reservedCategory.n, 3, "reserved-code respondent n");
equal(reservedCategory.counts["__proto__"], 1, "__proto__ category count");
equal(reservedCategory.counts.constructor, 1, "constructor category count");
equal(reservedCategory.counts.toString, 1, "toString category count");
equal(stats.labelFor("reserved", "__proto__"), "Prototype code", "reserved category label");
config.map = { groupLabels: Object.create(null) };
config.map.groupLabels["__proto__"] = "Prototype group";
equal(stats.groupLabel("__proto__"), "Prototype group", "reserved map group label");
config.map = null;

// Horizontal percent bars begin at the visual right edge in RTL, and their
// custom end labels use mirrored outside/inside anchors.
equal(stats.horizontalScale({ stacked: true }, false).reverse, false, "LTR horizontal scale");
equal(stats.horizontalScale({ stacked: true }, true).reverse, true, "RTL horizontal scale");
let placement = stats.barValuePlacement({ x: 70, base: 0 }, 70, { left: 0, right: 100 }, 10, false);
equal(placement.inside, false, "LTR outside value label");
equal(placement.textAlign, "left", "LTR outside value-label alignment");
equal(placement.x, 75, "LTR outside value-label anchor");
placement = stats.barValuePlacement({ x: 30, base: 100 }, 30, { left: 0, right: 100 }, 10, true);
equal(placement.inside, false, "RTL outside value label");
equal(placement.textAlign, "right", "RTL outside value-label alignment");
equal(placement.x, 25, "RTL outside value-label anchor");
placement = stats.barValuePlacement({ x: 5, base: 100 }, 5, { left: 0, right: 100 }, 10, true);
equal(placement.inside, true, "RTL inside value label");
equal(placement.textAlign, "left", "RTL inside value-label alignment");
equal(placement.x, 10, "RTL inside value-label anchor");
equal(stats.weightedOutlierShare({ classified: true, outlierWeight: 2, totalWeight: 8 }), 25,
  "weighted Tukey outlier share");
equal(stats.weightedOutlierShare({ classified: false, outlierWeight: 2, totalWeight: 8 }), null,
  "unclassified weighted outlier share");

if (!source.includes('renderer:state.leafletPointRenderer')
    || !source.includes('setAttribute("tabindex","0")')
    || !source.includes('event.key!=="Enter"')
    || !source.includes('marker.openPopup()')) {
  throw new Error("Point-mode Leaflet marker keyboard contract is incomplete");
}

// Questionnaire-defined special responses remain available to categorical
// figures. Negative special response codes are not numeric measurements;
// nonnegative substantive shortcuts remain valid. Explicit missingcodes()
// exclusions continue to apply in both contexts.
context.META.q = { order: ["50", "100", "-4", "-9"], special: ["100", "-4", "-9"], missing: ["-9"] };
config.weighted = false;
const specialCategory = stats.categorical("q", [{ q: "50" }, { q: "100" }, { q: "-9" }], false);
equal(specialCategory.n, 2, "explicit categorical missing-code exclusion");
equal(specialCategory.counts["100"], 1, "substantive questionnaire special retained");
equal(stats.isSpecial("q", "100"), true, "questionnaire special styling");
equal(stats.isConfiguredMissing("q", "100"), false, "substantive special is not missing");
equal(stats.isConfiguredMissing("q", "-9"), true, "explicit missing code");
const specialNumeric = stats.numericValues("q", [{ q: 50 }, { q: 100 }, { q: -4 }, { q: -9 }, { q: null }]);
equal(specialNumeric.length, 2, "negative numeric special-response exclusion");
equal(specialNumeric[0].value, 50, "ordinary numeric value retained");
equal(specialNumeric[1].value, 100, "nonnegative substantive shortcut retained");

// Composite panels keep their calculations at the underlying-variable level.
// This protects per-item denominators, stable category order, exact integer
// supports, and subgroup percentages while the panel itself has a synthetic id.
function compositeCanvas(id, members) {
  const attributes = {
    "data-variable": id,
    "data-members": members || "",
    "aria-label": id,
  };
  return {
    id,
    parentElement: null,
    dataset: {},
    getAttribute: (name) => attributes[name] ?? null,
    setAttribute: (name, value) => { attributes[name] = String(value); },
    removeAttribute: (name) => { delete attributes[name]; },
    attributes,
  };
}
for (const variable of ["itema", "itemb", "itemc"]) {
  context.META[variable] = {
    kind: "yesno",
    label: variable.toUpperCase(),
    order: ["1", "2"],
    labels: { "1": "Yes", "2": "No" },
    affirmative: ["1"],
    negative: ["2"],
    special: [],
    missing: [],
  };
}
context.DATA.splice(0, context.DATA.length,
  { itema: "1", itemb: "1", itemc: "2", gender: "1", count: 1 },
  { itema: "1", itemb: "2", itemc: "2", gender: "1", count: 2 },
  { itema: "2", itemb: "1", itemc: "1", gender: "2", count: 4 },
  { itema: null, itemb: "2", itemc: "1", gender: "2", count: -9 },
);
config.weighted = false;
config.families = {
  family_items: { members: ["itema", "itemb", "itemc"], labels: { itema: "A", itemb: "B", itemc: "C" } },
};
stats.buildFamily(compositeCanvas("family_items", "itema itemb itemc"));
let compositeChart = ChartStub.instances[ChartStub.instances.length - 1].config;
equal(compositeChart.options.scales.x.stacked, true, "family uses stacked percentage scale");
equal(compositeChart.data.labels.length, 3, "family member rows");
equal(compositeChart.data.datasets[0].data[0], 200 / 3, "family member-specific valid denominator");

context.META.gender = {
  kind: "filter",
  order: ["1", "2"],
  labels: { "1": "Women-led", "2": "Men-led" },
  special: [],
  missing: [],
};
config.comparisons = {
  comparison_items: { members: ["itema", "itemb", "itemc"], by: "gender", levels: ["1", "2"] },
};
stats.buildComparison(compositeCanvas("comparison_items", "itema itemb itemc"));
compositeChart = ChartStub.instances[ChartStub.instances.length - 1].config;
equal(compositeChart.data.datasets.length, 2, "comparison subgroup series");
equal(compositeChart.data.datasets[0].data[0], 100, "women-led affirmative share");
equal(compositeChart.data.datasets[1].data[0], 0, "men-led affirmative share with item denominator");
if ((compositeChart.plugins || []).some((plugin) => plugin?.id === "barConfidenceIntervals")) {
  throw new Error("binary comparison registered a CI whisker plugin");
}

context.META.count = {
  kind: "discrete",
  distributionMode: "discrete",
  nonnegative: true,
  special: ["-9"],
  missing: [],
  labels: {},
  order: [],
};
context.DATA.splice(0, context.DATA.length,
  ...[1, 2, 3, 4, 5, 6, 8, 9, 10, 11, -9].map((count) => ({ count })),
);
stats.buildDiscrete(compositeCanvas("count", "count"), "count");
compositeChart = ChartStub.instances[ChartStub.instances.length - 1].config;
equal(compositeChart.options.scales.x.type, "linear", "discrete chart uses a numeric x scale");
const exactCountBars = compositeChart.data.datasets[0].data;
equal(exactCountBars.map((point) => point.x).join("-"), "1-2-3-4-5-6-7-8-9-10-11",
  "discrete exact integer support with zero gap");
equal(exactCountBars[6].y, 0, "discrete zero-frequency integer gap");
equal(compositeChart.options.plugins.discreteMedianGuide.median, 5.5, "discrete median guide");
equal(exactCountBars.reduce((sum, point) => sum + point.raw, 0), 10,
  "exact discrete raw mass conservation");

// Numeric display planning is independent of maxcategories(). A short count
// support remains exact, while a wide integer support receives regular bins
// on a linear axis. Tukey tail observations remain in the summary statistics
// but cannot stretch the visible plotting domain into hundreds of empty bars.
function niceStep(value) {
  if (!(value > 0) || !Number.isFinite(value)) return false;
  const magnitude = 10 ** Math.floor(Math.log10(value));
  const normalized = value / magnitude;
  return [1, 2, 5, 10].some((candidate) => Math.abs(normalized - candidate) < 1e-9);
}
function regularSpacing(values, tolerance = 1e-9) {
  if (values.length < 3) return true;
  const step = values[1] - values[0];
  return values.slice(2).every((value, index) =>
    Math.abs((value - values[index + 1]) - step) <= tolerance);
}

context.META.age = {
  kind: "discrete",
  distributionMode: "discrete",
  nonnegative: true,
  special: ["-4", "-9"],
  missing: [],
  labels: {},
  order: [],
};
const ageRows = [];
for (let age = 18; age <= 80; age += 1) ageRows.push({ age });
ageRows.push({ age: 506 }, { age: -4 }, { age: -9 });
context.DATA.splice(0, context.DATA.length, ...ageRows);
const validAges = stats.numericValues("age", ageRows);
equal(validAges.length, 64, "age negative special codes excluded");
const ageStats = stats.numericStatistics(validAges, ageRows.length);
equal(ageStats.max, 506, "age tail remains in summary statistics");
equal(ageStats.outliers, 1, "age Tukey tail classification");
stats.buildDiscrete(compositeCanvas("age", "age"), "age");
compositeChart = ChartStub.instances[ChartStub.instances.length - 1].config;
const ageGuide = compositeChart.options.plugins.discreteMedianGuide;
const ageBars = compositeChart.data.datasets[0].data;
equal(compositeChart.options.scales.x.type, "linear", "wide discrete age uses a numeric x scale");
equal(ageGuide.highTail, 1, "age high tail is explicitly summarized");
equal(ageGuide.lowTail, 0, "age low tail count");
if (!(ageGuide.maximum < 506)) throw new Error(`age display maximum was stretched to ${ageGuide.maximum}`);
if (ageBars.some((point) => point.x === 506)) throw new Error("age outlier was plotted inside the main distribution");
if (ageBars.length > 40) throw new Error(`age distribution created ${ageBars.length} bars instead of smart bins`);
if (!regularSpacing(ageBars.map((point) => point.x))) throw new Error("age bin centers are not evenly spaced");
equal(ageBars.reduce((sum, point) => sum + point.raw, 0) + ageGuide.lowTail + ageGuide.highTail,
  validAges.length, "age raw mass conservation across visible bins and tails");
close(ageBars.reduce((sum, point) => sum + point.y, 0) + ageGuide.lowTail + ageGuide.highTail,
  validAges.length, 1e-12, "age unweighted mass conservation");

const agePlan = stats.discreteDistributionPlan(validAges, ageStats, true);
equal(agePlan.highTail, 1, "age plan high tail");
if (!(agePlan.binWidth > 1) || agePlan.bins.length > 40) {
  throw new Error(`age plan did not choose compact regular bins: width=${agePlan.binWidth}, bins=${agePlan.bins.length}`);
}
equal(agePlan.bins.reduce((sum, bin) => sum + bin.raw, 0) + agePlan.lowTail + agePlan.highTail,
  validAges.length, "age plan raw mass conservation");

// A highly concentrated count with one extreme code-like positive value is
// the zero-IQR edge case. The robust display must remain usable and still
// account for the clipped observation explicitly.
const concentrated = Array.from({ length: 20 }, () => ({ value: 4, w: 1 }));
concentrated.push({ value: 500, w: 1 });
const concentratedStats = stats.numericStatistics(concentrated, concentrated.length);
const concentratedPlan = stats.discreteDistributionPlan(concentrated, concentratedStats, true);
equal(concentratedPlan.minimum, 4, "zero-IQR robust minimum");
equal(concentratedPlan.maximum, 4, "zero-IQR robust maximum");
equal(concentratedPlan.highTail, 1, "zero-IQR high-tail count");
equal(concentratedPlan.bins.reduce((sum, bin) => sum + bin.raw, 0)
    + concentratedPlan.lowTail + concentratedPlan.highTail,
  concentrated.length, "zero-IQR raw mass conservation");

context.META.years_city = {
  kind: "discrete",
  distributionMode: "discrete",
  nonnegative: true,
  special: ["-4", "-9"],
  missing: [],
  labels: {},
  order: [],
};
const yearsRows = [];
for (let year = 1; year <= 25; year += 1) yearsRows.push({ years_city: year });
for (const year of [9, 9, 9, 10, 10, 10, 10, 15, 15, 15, 17, 17, 23, 23, 25, 33, 41, 49, 57, 69]) {
  yearsRows.push({ years_city: year });
}
yearsRows.push({ years_city: -4 }, { years_city: -9 });
context.DATA.splice(0, context.DATA.length, ...yearsRows);
stats.buildDiscrete(compositeCanvas("years_city", "years_city"), "years_city");
compositeChart = ChartStub.instances[ChartStub.instances.length - 1].config;
const yearBars = compositeChart.data.datasets[0].data;
const yearTicks = compositeChart.options.scales.x.ticks;
equal(compositeChart.options.scales.x.type, "linear", "sparse years distribution uses a numeric x scale");
if (!Number.isInteger(yearTicks.stepSize) || !niceStep(yearTicks.stepSize)) {
  throw new Error(`years-in-city x tick step is not a nice integer: ${yearTicks.stepSize}`);
}
if (!regularSpacing(yearBars.map((point) => point.x))) {
  throw new Error("years-in-city bin centers are not evenly spaced");
}
if (yearBars.length > 40) {
  throw new Error(`years-in-city distribution created ${yearBars.length} bars instead of smart bins`);
}
const validYears = stats.numericValues("years_city", yearsRows);
const yearGuide = compositeChart.options.plugins.discreteMedianGuide;
equal(yearBars.reduce((sum, point) => sum + point.raw, 0) + yearGuide.lowTail + yearGuide.highTail,
  validYears.length, "years-in-city raw mass conservation");

// Continuous histograms use the same numeric-axis grammar: numeric bin
// centers, even spacing, and a robust domain that reports rather than plots
// the extreme tail. This prevents category auto-skip from inventing awkward
// labels for otherwise ordinary ages.
context.META.age.kind = "hist";
context.META.age.distributionMode = "continuous";
context.DATA.splice(0, context.DATA.length, ...ageRows);
stats.buildHistogram(compositeCanvas("age_hist", "age"), "age");
compositeChart = ChartStub.instances[ChartStub.instances.length - 1].config;
const histogramBars = compositeChart.data.datasets[0].data;
const histogramGuide = compositeChart.options.plugins.distributionGuide.stats;
equal(compositeChart.options.scales.x.type, "linear", "continuous histogram uses a numeric x scale");
equal(histogramGuide.highOutliers, 1, "continuous histogram high-tail count");
if (!(histogramGuide.displayHigh < 506)) {
  throw new Error(`continuous histogram display maximum was stretched to ${histogramGuide.displayHigh}`);
}
if (!regularSpacing(histogramBars.map((point) => point.x))) {
  throw new Error("continuous histogram bin centers are not evenly spaced");
}
equal(histogramBars.reduce((sum, point) => sum + point.raw, 0)
    + histogramGuide.lowOutliers + histogramGuide.highOutliers,
  validAges.length, "continuous histogram raw mass conservation");

// Filter matching follows the actual DATA payload shape. Numeric formatting
// differences compare by value, completion filters understand true/null, and
// multiselect chips use any-selected (OR) semantics without matching missing
// codes.
context.META.scalar_filter = { filterMode: "scalar", missing: ["-9"] };
equal(stats.filterMatches("scalar_filter", "2", ["2"]), true, "scalar filter match");
equal(stats.filterMatches("scalar_filter", "-9", ["-9"]), false, "scalar missing code cannot match");
context.META.numeric_filter = { filterMode: "numeric", missing: [], special: ["-4", "-9"] };
equal(stats.filterMatches("numeric_filter", 1, ["1.0"]), true, "numeric equivalent token match");
equal(stats.filterMatches("numeric_filter", 2, ["1.0"]), false, "numeric nonmatch");
equal(stats.filterMatches("numeric_filter", -4, ["-4"]), false, "numeric special code cannot match");
context.META.completion_filter = { filterMode: "completion", missing: [] };
equal(stats.filterMatches("completion_filter", true, ["true"]), true, "completion answered match");
equal(stats.filterMatches("completion_filter", null, ["false"]), true, "completion missing match");
equal(stats.filterMatches("completion_filter", null, ["true"]), false, "completion missing nonmatch");
context.META.multi_filter = { filterMode: "multi", missing: ["-9"] };
equal(stats.filterMatches("multi_filter", ["1", "2"], ["2"]), true, "multiselect any-selected match");
equal(stats.filterMatches("multi_filter", ["-9"], ["-9"]), false, "multiselect missing code cannot match");
equal(stats.filterMatches("multi_filter", null, ["1"]), false, "unanswered multiselect nonmatch");

// The Stata wrapper supplies the exact %t* display format for data-only date
// variables.  Month aggregation must work whether export delimited writes the
// formatted token or the underlying numeric serial.
function month(value, format) {
  context.META.d = { format, special: [], missing: [] };
  return stats.monthKey("d", value);
}
equal(month(0, "%td"), "1960-01", "Stata daily serial");
equal(month("14jul2026", "%tdDDmonCCYY"), "2026-07", "Stata daily display");
equal(month(0, "%tc"), "1960-01", "Stata clock serial");
equal(month("14jul2026 12:30:00", "%tcDDmonCCYY_HH:MM:SS"), "2026-07", "Stata clock display");
equal(month(780, "%tm"), "2025-01", "Stata monthly serial");
equal(month(-1, "%tm"), "1959-12", "negative Stata monthly serial");
equal(month("2025m12", "%tm"), "2025-12", "Stata monthly display");
equal(month(260, "%tq"), "2025-01", "Stata quarterly serial");
equal(month(-1, "%tq"), "1959-10", "negative Stata quarterly serial");
equal(month("2025q4", "%tq"), "2025-10", "Stata quarterly display");
equal(month(0, "%tw"), "1960-01", "Stata weekly serial");
equal(month(-1, "%tw"), "1959-12", "negative Stata weekly serial");
equal(month(3389, "%tw"), "2025-03", "Stata weekly serial after 1960");
equal(month("2025w10", "%tw"), "2025-03", "Stata weekly display");
equal(month(130, "%th"), "2025-01", "Stata half-year serial");
equal(month(-1, "%th"), "1959-07", "negative Stata half-year serial");
equal(month("2025h2", "%th"), "2025-07", "Stata half-year display");
equal(month(2025, "%ty"), "2025-01", "Stata yearly value");
context.META.d = { format: "%td", special: ["999"], missing: ["999"] };
equal(stats.monthKey("d", 999), null, "date configured-missing exclusion");

console.log("PASS Stata-compatible dashboard statistics");
