/**
 * datomic-at-scale-deck.js
 * DATOMIC AT SCALE — Operations, Parallelism and the Cost of a Read
 * Run: node datomic-at-scale-deck.js
 * Out: datomic-at-scale.pptx
 *
 * Companion to datomic-at-scale-deck.md. Every ASCII diagram in the
 * markdown is drawn here with real shapes; the prose of the markdown
 * lives in speaker notes.
 */

const pptxgen = require("pptxgenjs");

// ═══════════════════════════════════════════════════════════════════
// PALETTE — shared with datomic-console-deck.js
// ═══════════════════════════════════════════════════════════════════
const C = {
  bg:     "EFE9DD",
  ink:    "3A2C1A",
  body:   "6B5A44",
  meta:   "9A8A72",
  gold:   "A8864A",
  goldHi: "C6A46A",
  rule:   "C9BDA4",
  panel:  "E6DFCE",
  panel2: "DFD6C0",
  dark:   "2C2416",
  white:  "FCFAF4",
  code:   "3A2C1A",
  // semantic accents, kept in the same earth family
  ok:     "5F7A3A",
  okBg:   "DDE3CC",
  warn:   "B5822B",
  warnBg: "EDE0C4",
  bad:    "9E4227",
  badBg:  "EBD5CB",
  cool:   "3F6470",
  coolBg: "D3DEE1",
};

const F = { serif: "Georgia", sans: "Helvetica Neue", mono: "Courier New" };

const W = 13.333, H = 7.5;

const pres = new pptxgen();
pres.title  = "Datomic at Scale — Operations, Parallelism and the Cost of a Read";
pres.author = "Datomic Classes";
pres.defineLayout({ name: "WIDE", width: W, height: H });
pres.layout = "WIDE";

let page = 0;

// ═══════════════════════════════════════════════════════════════════
// QA INSTRUMENTATION — records every text box so the checks at the
// bottom of this file can find collisions and overflow. Set
// DECK_QA=1 in the environment to print the report.
// ═══════════════════════════════════════════════════════════════════
const QA = [];
const _addSlide = pres.addSlide.bind(pres);
pres.addSlide = function (...a) {
  const sl = _addSlide(...a);
  const idx = QA.push({ texts: [], shapes: [], chrome: false }) - 1;
  sl.__qa = idx;
  const _addShape = sl.addShape.bind(sl);
  sl.addShape = function (t, opt) {
    QA[idx].shapes.push(opt || {});
    return _addShape(t, opt);
  };
  const _addText = sl.addText.bind(sl);
  sl.addText = function (t, o) {
    const str = Array.isArray(t) ? t.map(x => (typeof x === "string" ? x : x.text)).join("\n") : String(t);
    QA[idx].texts.push({ str, o: o || {} });
    return _addText(t, o);
  };
  return sl;
};


// ═══════════════════════════════════════════════════════════════════
// PRIMITIVES
// ═══════════════════════════════════════════════════════════════════

function bar(slide, x, y, w, h, color, opts = {}) {
  slide.addShape(opts.round ? pres.shapes.ROUNDED_RECTANGLE : pres.shapes.RECTANGLE, {
    x, y, w, h,
    fill: opts.fill === false ? { type: "none" } : { color },
    line: opts.line || { type: "none" },
    rectRadius: opts.round ? (opts.radius || 0.08) : undefined,
  });
}

/** Text helper with sane defaults. */
function txt(slide, text, o) {
  slide.addText(text, Object.assign({
    fontFace: F.sans, fontSize: 14, color: C.body, margin: 0, valign: "top",
  }, o));
}

/**
 * A labelled box: the workhorse of every diagram here.
 * opts: {fill, border, label, sub, labelColor, size, subSize, mono, align}
 */
function box(slide, x, y, w, h, opts = {}) {
  const fill = opts.fill || C.panel;
  slide.addShape(opts.square ? pres.shapes.RECTANGLE : pres.shapes.ROUNDED_RECTANGLE, {
    x, y, w, h,
    fill: { color: fill },
    line: opts.border ? { color: opts.border, width: opts.borderWidth || 1.25 } : { type: "none" },
    rectRadius: 0.06,
  });
  const hasSub = !!opts.sub;
  if (opts.label) {
    txt(slide, opts.label, {
      x: x + 0.08, y: hasSub ? y + h / 2 - 0.34 : y, w: w - 0.16, h: hasSub ? 0.34 : h,
      fontFace: opts.mono ? F.mono : F.sans,
      fontSize: opts.size || 13,
      color: opts.labelColor || C.ink,
      bold: opts.bold !== false,
      align: opts.align || "center",
      valign: hasSub ? "bottom" : "middle",
      fit: "shrink",
    });
  }
  if (hasSub) {
    txt(slide, opts.sub, {
      x: x + 0.08, y: y + h / 2 - 0.02, w: w - 0.16, h: opts.subH || 0.42,
      fontFace: opts.subMono ? F.mono : F.sans,
      fontSize: opts.subSize || 10.5,
      color: opts.subColor || C.body,
      align: opts.align || "center", valign: "top", fit: "shrink",
    });
  }
}

/** Straight arrow between two points. dir: 'down' | 'up' | 'right' | 'left' */
function arrow(slide, x1, y1, x2, y2, opts = {}) {
  const dx = x2 - x1, dy = y2 - y1;
  const o = {
    x: Math.min(x1, x2), y: Math.min(y1, y2),
    w: Math.abs(dx), h: Math.abs(dy),
    line: {
      color: opts.color || C.gold,
      width: opts.width || 1.5,
      dashType: opts.dash ? "dash" : "solid",
      endArrowType: opts.head === false ? "none" : "triangle",
      beginArrowType: opts.bothWays ? "triangle" : "none",
    },
    flipH: dx < 0, flipV: dy < 0,
  };
  slide.addShape(pres.shapes.LINE, o);
  if (opts.label) {
    txt(slide, opts.label, {
      x: (x1 + x2) / 2 - (opts.labelW || 1.4) / 2 + (opts.labelDx || 0),
      y: (y1 + y2) / 2 - 0.16 + (opts.labelDy || 0),
      w: opts.labelW || 1.4, h: 0.32,
      fontSize: opts.labelSize || 10, color: opts.labelColor || C.meta,
      align: "center", valign: "middle", fit: "shrink",
    });
  }
}

/** Fat directional arrow shape (for emphasis, not connection). */
function fatArrow(slide, x, y, w, h, dirShape, opts = {}) {
  slide.addShape(dirShape, {
    x, y, w, h,
    fill: { color: opts.fill || C.goldHi },
    line: { type: "none" },
  });
  if (opts.label) {
    txt(slide, opts.label, {
      x, y: y + h / 2 - 0.16, w, h: 0.32,
      fontSize: opts.size || 11, color: opts.color || C.dark,
      bold: true, align: "center", valign: "middle", fit: "shrink",
    });
  }
}

// ═══════════════════════════════════════════════════════════════════
// SLIDE CHROME
// ═══════════════════════════════════════════════════════════════════

function header(slide, chapter) {
  page += 1;
  txt(slide, chapter, { x: 0.6, y: 0.28, w: 10, h: 0.28, fontSize: 11, color: C.meta, charSpacing: 2 });
  txt(slide, String(page), { x: 12.2, y: 0.28, w: 0.7, h: 0.28, fontSize: 11, color: C.meta, align: "right" });
  bar(slide, 0.6, 0.58, 12.1, 0.015, C.goldHi);
}

function titleSlide(kicker, title, subtitle, notes) {
  const s = pres.addSlide();
  bar(s, 0, 0, W, H, C.dark);
  page += 1;
  // decorative tier bars
  [0, 1, 2, 3].forEach(i =>
    bar(s, 0.9 + i * 0.7, 5.6, 0.5, 0.5 + i * 0.35, [C.gold, C.goldHi, C.rule, C.white][i]));
  txt(s, kicker, { x: 0.9, y: 1.9, w: 11.5, h: 0.35, fontSize: 13, color: C.goldHi, charSpacing: 3 });
  txt(s, title, { x: 0.9, y: 2.4, w: 11.5, h: 1.2, fontFace: F.serif, fontSize: 40, color: C.white, bold: true });
  if (subtitle) txt(s, subtitle, { x: 0.9, y: 3.8, w: 11.5, h: 0.8, fontSize: 16, color: C.rule });
  txt(s, String(page), { x: 12.2, y: 7.0, w: 0.7, h: 0.28, fontSize: 11, color: C.meta, align: "right" });
  if (notes) s.addNotes(notes);
  return s;
}

function sectionSlide(num, title, blurb, notes, body) {
  const s = pres.addSlide();
  bar(s, 0, 0, W, H, C.dark);
  page += 1;
  bar(s, 0, 2.2, 0.25, 2.2, C.gold);
  txt(s, "PART " + num, { x: 0.9, y: 2.3, w: 11.5, h: 0.4, fontSize: 15, color: C.goldHi, charSpacing: 3 });
  txt(s, title, { x: 0.9, y: 2.85, w: 11.5, h: 0.9, fontFace: F.serif, fontSize: 34, color: C.white });
  if (blurb) txt(s, blurb, { x: 0.9, y: 3.85, w: 11.5, h: 0.8, fontSize: 16, color: C.rule });
  if (body) prose(s, body, { x: 0.9, y: 4.85, w: 11.5, h: 2.0, size: 14, color: C.rule });
  txt(s, String(page), { x: 12.2, y: 7.0, w: 0.7, h: 0.28, fontSize: 11, color: C.meta, align: "right" });
  if (notes) s.addNotes(notes);
  return s;
}

function slide(chapter, title, notes) {
  const s = pres.addSlide();
  bar(s, 0, 0, W, H, C.bg);
  header(s, chapter);
  txt(s, title, { x: 0.6, y: 0.74, w: 12.1, h: 0.6, fontFace: F.serif, fontSize: 25, color: C.ink, fit: "shrink" });
  QA[s.__qa].chrome = true;
  if (notes) s.addNotes(notes);
  return s;
}

/** The one-line "what to take away" strip at the bottom of a diagram slide. */
function takeaway(slide, text, y = 6.55) {
  bar(slide, 0.6, y, 0.06, 0.5, C.gold);
  txt(slide, text, {
    x: 0.85, y, w: 11.85, h: 0.5, fontSize: 14.5, color: C.ink, valign: "middle", fit: "shrink",
  });
}

/** The bottom CASES strip: two or three concrete examples, all sourced from
 *  the labs or from the slide above. Deliberately WITHOUT fit: "shrink", so
 *  over-long text trips the QA overflow check instead of silently rendering
 *  at 8pt. If a build reports OVERFLOW here, cut words. */
function cases(slide, items, opts = {}) {
  const y = opts.y === undefined ? 6.98 : opts.y;
  const h = opts.h || 0.40;
  bar(slide, 0.6, y + 0.04, 0.06, h - 0.08, C.gold);
  txt(slide, "CASES", {
    x: 0.78, y, w: 0.72, h, fontSize: 9, color: C.gold, bold: true,
    charSpacing: 1, valign: "middle",
  });
  txt(slide, items.join("   ·   "), {
    x: 1.6, y, w: opts.w || 10.4, h, fontSize: 11, color: C.body, valign: "middle",
  });
}

function note(slide, text, y = 6.7) {
  txt(slide, text, { x: 0.6, y, w: 12.1, h: 0.4, fontSize: 12.5, color: C.meta, italic: true, fit: "shrink" });
}

/** A block of explanatory body text. Like cases(), it deliberately omits
 *  fit: "shrink" — if the text does not fit, the QA overflow check says so
 *  instead of quietly rendering it two points smaller. */
function prose(slide, text, opts = {}) {
  txt(slide, text, {
    x: opts.x === undefined ? 0.6 : opts.x,
    y: opts.y, w: opts.w || 12.1, h: opts.h,
    fontSize: opts.size || 13.5, color: opts.color || C.body,
  });
}

function bullets(slide, items, opts = {}) {
  const lines = items.map(t => (typeof t === "string"
    ? { text: t, options: { bullet: opts.bullet !== false, breakLine: true, paraSpaceAfter: opts.gap || 8 } }
    : t));
  slide.addText(lines, {
    x: opts.x || 0.6, y: opts.y || 1.5, w: opts.w || 12.1, h: opts.h || 4.6,
    fontFace: F.sans, fontSize: opts.size || 15, color: opts.color || C.body,
    valign: "top", margin: 0, fit: "shrink",
  });
}

function codeBlock(slide, code, x, y, w, h, opts = {}) {
  bar(slide, x, y, w, h, opts.dark ? C.dark : C.panel, { round: true });
  txt(slide, code, {
    x: x + 0.22, y: y + 0.16, w: w - 0.44, h: h - 0.32,
    fontFace: F.mono, fontSize: opts.size || 12.5,
    color: opts.dark ? C.white : C.code, fit: "shrink",
  });
}

/** Panel with a coloured heading strip. */
function panel(slide, x, y, w, h, title, opts = {}) {
  bar(slide, x, y, w, h, opts.bg || C.panel, { round: true });
  bar(slide, x, y, w, 0.42, opts.strip || C.panel2, { round: true });
  txt(slide, title, {
    x: x + 0.22, y: y, w: w - 0.44, h: 0.42,
    fontSize: opts.titleSize || 12.5, color: opts.titleColor || C.gold,
    bold: true, charSpacing: 1, valign: "middle", fit: "shrink",
  });
}

/** Horizontal comparison bars. rows: [label, value, displayValue, color] */
function barCompare(slide, x, y, w, rows, opts = {}) {
  const max = opts.max || Math.max(...rows.map(r => r[1]));
  const rowH = opts.rowH || 0.5;
  const labelW = opts.labelW || 1.9;
  const valueW = 1.5;
  const trackW = w - labelW - valueW - 0.3;
  rows.forEach((r, i) => {
    const ry = y + i * (rowH + (opts.gap || 0.18));
    txt(slide, r[0], {
      x, y: ry, w: labelW, h: rowH, fontSize: opts.size || 12.5, color: C.ink,
      align: "right", valign: "middle", fontFace: opts.monoLabel ? F.mono : F.sans, fit: "shrink",
    });
    bar(slide, x + labelW + 0.2, ry + 0.06, trackW, rowH - 0.12, C.panel);
    const frac = Math.max(0.02, r[1] / max);
    bar(slide, x + labelW + 0.2, ry + 0.06, trackW * frac, rowH - 0.12, r[3] || C.gold);
    txt(slide, r[2], {
      x: x + labelW + 0.3 + trackW, y: ry, w: valueW, h: rowH,
      fontFace: F.mono, fontSize: opts.size || 12.5, color: C.body, valign: "middle", fit: "shrink",
    });
  });
  return y + rows.length * (rowH + (opts.gap || 0.18));
}

/** Simple two-column key/value listing. rows: [left, right] */
function defList(slide, x, y, w, rows, opts = {}) {
  const rowH = opts.rowH || 0.52;
  const leftW = opts.leftW || w * 0.45;
  rows.forEach((r, i) => {
    const ry = y + i * rowH;
    if (i % 2 === 0 && opts.zebra !== false) bar(slide, x - 0.1, ry, w + 0.2, rowH, C.panel);
    txt(slide, r[0], {
      x, y: ry, w: leftW, h: rowH, fontFace: opts.mono === false ? F.sans : F.mono,
      fontSize: opts.size || 13, color: C.ink, valign: "middle", fit: "shrink",
    });
    txt(slide, r[1], {
      x: x + leftW + 0.2, y: ry, w: w - leftW - 0.2, h: rowH,
      fontSize: opts.size || 13, color: C.body, valign: "middle", fit: "shrink",
    });
  });
  return y + rows.length * rowH;
}


// ═══════════════════════════════════════════════════════════════════
// COVER + FRAME
// ═══════════════════════════════════════════════════════════════════

titleSlide(
  "A 2-HOUR CLASS",
  "Datomic at Scale",
  "Reads, writes, parallelism and caches — the basics and the catches",
  "Fifth class in the series. It continues from *Datomic in Production*, but the only thing it assumes is the vocabulary: what a peer, a transactor and storage are. Everything else is explained from the basics here.\n\nThe class covers how a Datomic system performs in production, in four topics: reads, writes, parallelism and caches. Each topic follows the same three-step shape — how it works, an easy example, and the main catches you will actually hit.\n\nThe goal is the right mental model, not a tuning reference. Knob tables and environment-specific measurements are deliberately left out; the Production class materials have them."
);

// How the class works
{
  const s = slide("FRAME", "Four topics, three steps each",
    "The whole class is four topics, and every topic follows the same shape.\n\nFirst the basics: how the mechanism works, drawn simply. Then one easy example — small code or a small measurement, chosen to be readable rather than impressive. Then the main catches: the two or three things that actually surprise people in production.\n\nWhat is deliberately NOT here: settings tables, environment-specific benchmarks, and deployment runbooks. Those live in the Production class materials. This class is about carrying the right mental model, so that when a symptom appears you know which of the four topics it belongs to.\n\nThe only assumed knowledge is vocabulary from the Production class: peer, transactor, storage. The opening section re-draws that map in two slides anyway.");

  const topics = [
    ["READS", "run in your process — the cost depends on where the data is", C.coolBg, C.cool],
    ["WRITES", "one door — batch well, and know the two ways it can wait", C.warnBg, C.warn],
    ["PARALLELISM", "reads scale out — writes never do", C.okBg, C.ok],
    ["CACHES", "keep everyday reads near the top of the ladder", C.panel2, C.gold],
  ];
  topics.forEach((t, i) => {
    const y = 1.5 + i * 1.02;
    bar(s, 0.6, y, 7.6, 0.88, t[2], { round: true });
    txt(s, t[0], { x: 0.9, y, w: 2.4, h: 0.88, fontSize: 15, color: t[3], bold: true, charSpacing: 1.5, valign: "middle" });
    txt(s, t[1], { x: 3.3, y, w: 4.75, h: 0.88, fontSize: 12.5, color: C.ink, valign: "middle", fit: "shrink" });
  });

  panel(s, 8.5, 1.5, 4.2, 3.94, "EACH TOPIC, IN 3 STEPS", { strip: C.panel2 });
  [["1", "the basics", "how it works, drawn simply"],
   ["2", "an easy example", "small code, small numbers"],
   ["3", "the catches", "what surprises people"]].forEach((r, i) => {
    const y = 2.15 + i * 1.05;
    txt(s, r[0], { x: 8.75, y, w: 0.5, h: 0.9, fontFace: F.serif, fontSize: 24, color: C.gold, bold: true });
    txt(s, r[1], { x: 9.35, y: y + 0.05, w: 3.2, h: 0.35, fontSize: 14, color: C.ink, bold: true });
    txt(s, r[2], { x: 9.35, y: y + 0.45, w: 3.2, h: 0.35, fontSize: 11.5, color: C.body, fit: "shrink" });
  });

  takeaway(s, "Goal: the right mental model — not a tuning reference. The knob tables live in the Production class.", 5.75);
  cases(s, [
    "Assumed: only the words peer, transactor, storage",
    "Left out on purpose: settings tables, benchmarks, runbooks",
  ]);
}

// Agenda
{
  const s = slide("FRAME", "Two hours, four topics",
    "Twelve minutes to redraw the map, then roughly 25 minutes per topic, a break in the middle, and a summary that compresses everything into four verbs.\n\nEach section is self-contained: basics first, then an easy example, then the catches. If a section runs long, the catches slide is the one to keep — it is the part people hit in production.");

  const rows = [
    ["0:00", "The map", "three parts — only one of them is fatal", C.panel],
    ["0:12", "Reads", "run in your process; cost = where the data is", C.coolBg],
    ["0:40", "Writes", "one door; batch, and the two ways it waits", C.warnBg],
    ["1:02", "☕ Break (10 min)", "", C.panel2],
    ["1:12", "Parallelism", "reads scale out; writes never do", C.okBg],
    ["1:38", "Caches", "keep reads near the top of the ladder", C.panel2],
    ["1:55", "Summary", "fails · waits · scales · costs", C.panel],
  ];
  rows.forEach((r, i) => {
    const y = 1.5 + i * 0.72;
    bar(s, 0.6, y, 12.1, 0.6, r[3], { round: true });
    txt(s, r[0], { x: 0.9, y, w: 1.0, h: 0.6, fontFace: F.mono, fontSize: 13, color: C.meta, valign: "middle" });
    txt(s, r[1], { x: 2.1, y, w: 3.4, h: 0.6, fontSize: 14.5, color: C.ink, bold: true, valign: "middle" });
    txt(s, r[2], { x: 5.7, y, w: 6.8, h: 0.6, fontSize: 12.5, color: C.body, valign: "middle", fit: "shrink" });
  });
  note(s, "infra/HA.md has the two-transactor setup, if you want to run Part II's failover yourself.", 6.75);
}

// ═══════════════════════════════════════════════════════════════════
// THE MAP
// ═══════════════════════════════════════════════════════════════════

// Three moving parts
{
  const s = slide("THE MAP", "Three moving parts",
    "Before any of the four topics: the map, in its simplest form.\n\nThe peer is a library inside your application — queries run there, in your JVM, on data the peer pulls close. There is no query server.\n\nThe transactor is one process that all writes go through, in order.\n\nStorage — Postgres, DynamoDB, or similar — just keeps bytes. It is the only place the data lives.\n\nNotice what the arrows say: peers read storage directly, and never talk to the transactor to read. That one detail is why reads and writes live and die separately, which is the idea the whole class builds on.");

  [0, 1, 2].forEach(i => {
    const x = 2.4 + i * 2.6;
    box(s, x, 1.5, 1.9, 0.75, { label: "peer", fill: C.coolBg, border: C.cool, size: 14 });
    arrow(s, x + 0.95, 2.25, x + 0.95, 3.35, { color: C.cool });
  });
  txt(s, "queries run HERE\n(inside your app)", { x: 8.9, y: 1.5, w: 3.6, h: 0.8, fontSize: 13.5, color: C.cool, bold: true });

  box(s, 2.4, 3.35, 6.5, 0.9, { label: "STORAGE", fill: C.dark, size: 16, labelColor: C.white });
  txt(s, "keeps the data\n(Postgres, DynamoDB…)", { x: 8.9, y: 3.35, w: 3.6, h: 0.8, fontSize: 13.5, color: C.ink, bold: true });

  box(s, 4.4, 5.05, 2.5, 0.8, { label: "transactor", fill: C.warnBg, border: C.warn, size: 14 });
  arrow(s, 5.65, 5.05, 5.65, 4.25, { color: C.warn, label: "ALL writes", labelDx: 1.3, labelColor: C.warn });
  txt(s, "one process — every\nwrite goes through it", { x: 8.9, y: 5.05, w: 3.6, h: 0.8, fontSize: 13.5, color: C.warn, bold: true });

  takeaway(s, "Peers read storage directly — they never ask the transactor anything. Reads and writes are separate worlds.", 6.15);
  cases(s, [
    "Your app's JVM IS the query engine",
    "Reads never touch the transactor",
    "Storage is the only place the data lives",
  ]);
}

// What breaks
{
  const s = slide("THE MAP", "What happens when each one dies",
    "Three cards, worst first.\n\nStorage down: everything stops eventually. It is the only place the data lives, so this is the one real outage.\n\nTransactor down: writes stop, and reads keep working. This is the failure people expect to be fatal — and it is not, because peers never needed the transactor to read.\n\nOne peer down: only that peer's traffic. The other peers share nothing with it except storage.\n\nOnly one of the three failures is fatal. That reads and writes live and die separately is the single structural difference from a one-server SQL database — and it is the idea the rest of the class keeps returning to.");

  const cards = [
    ["STORAGE", "everything stops", "the one real outage — the data lives only here", C.badBg, C.bad],
    ["TRANSACTOR", "writes stop", "reads keep working — peers never needed it", C.warnBg, C.warn],
    ["ONE PEER", "that peer only", "the others don't notice — nothing shared but storage", C.okBg, C.ok],
  ];
  cards.forEach((c, i) => {
    const x = 0.6 + i * 4.07;
    bar(s, x, 1.5, 3.87, 3.3, c[3], { round: true });
    bar(s, x, 1.5, 3.87, 0.12, c[4], { round: true });
    txt(s, c[0], { x: x + 0.3, y: 1.85, w: 3.3, h: 0.35, fontSize: 14, color: c[4], bold: true, charSpacing: 1.5 });
    txt(s, c[1], { x: x + 0.3, y: 2.35, w: 3.3, h: 0.9, fontFace: F.serif, fontSize: 24, color: C.ink, fit: "shrink" });
    txt(s, c[2], { x: x + 0.3, y: 3.45, w: 3.3, h: 1.1, fontSize: 13, color: C.body });
  });

  takeaway(s, "Only storage is fatal. Reads and writes live and die separately — the rest of the class builds on this.", 5.25);
  prose(s, "This is the structural difference from a single SQL server, where the query engine, the writer and the data share one process and therefore one failure. Here they are three processes with three separate failures — and two of the three are degradations, not outages.",
    { y: 5.95, h: 0.9 });
  cases(s, [
    "Transactor OOM: dashboards keep rendering, saves fail",
    "One peer OOM: one pod restarts, siblings never notice",
  ]);
}

// ═══════════════════════════════════════════════════════════════════
// PART I · READS
// ═══════════════════════════════════════════════════════════════════

sectionSlide("I", "Reads", "A query runs in your process, on data pulled close",
  "The basics first: where a read actually happens, and why its cost is entirely a question of where the data is at that moment. Then one easy example — the same query timed twice — and the three catches: cold peers, reading your own writes, and the one-core query.",
  "A Datomic query does not go to a server. It runs inside your application, on data the peer pulls close and keeps. So the cost of a read has almost nothing to do with the query engine — it is dominated by one question: is the data already here, or does it have to be fetched?");

// How a read works
{
  const s = slide("PART I · READS", "How a read works",
    "Walk the flowchart. Your code asks a query. The peer checks whether the data is already local.\n\nIf yes, the answer comes from memory — effectively instant, and nothing else in the system is involved.\n\nIf no, the peer fetches the data from storage — milliseconds — and, crucially, KEEPS it. The next query over the same data takes the fast path.\n\nThe example is the whole story in two lines: the same query, run twice, is 120 ms and then 2 ms. Nothing about the query changed. Only where the data was.\n\nAnd note what is absent from the picture entirely: the transactor. Reads never touch it.");

  box(s, 0.6, 1.55, 2.6, 0.8, { label: "your query", fill: C.coolBg, border: C.cool, size: 14 });
  arrow(s, 3.2, 1.95, 4.3, 1.95, { color: C.cool });
  box(s, 4.3, 1.55, 3.4, 0.8, { label: "data already here?", fill: C.panel, border: C.rule, size: 14 });
  arrow(s, 7.7, 1.95, 8.8, 1.95, { color: C.ok, label: "yes", labelDy: -0.25, labelColor: C.ok });
  box(s, 8.8, 1.55, 3.7, 0.8, { label: "answer from memory", sub: "~instant", fill: C.okBg, border: C.ok, size: 13.5, subSize: 10.5, subColor: C.ok });
  arrow(s, 6.0, 2.35, 6.0, 3.1, { color: C.warn, label: "no", labelDx: -0.9, labelColor: C.warn });
  box(s, 4.3, 3.1, 3.4, 0.8, { label: "fetch from storage", sub: "milliseconds", fill: C.warnBg, border: C.warn, size: 13.5, subSize: 10.5, subColor: C.warn });
  arrow(s, 7.7, 3.5, 8.8, 3.5, { color: C.warn });
  box(s, 8.8, 3.1, 3.7, 0.8, { label: "KEEP it, then answer", sub: "next time: fast path", fill: C.panel2, border: C.rule, size: 13.5, subSize: 10.5 });

  codeBlock(s,
    "(time (d/q readings-q db))   ;; 1st run: ~120 ms — fetching from storage\n(time (d/q readings-q db))   ;; 2nd run:   ~2 ms — data is already local",
    0.6, 4.35, 12.1, 1.0);

  takeaway(s, "A read costs whatever FETCHING costs. Once the data is local, reads are nearly free.", 5.65);
  note(s, "The transactor appears nowhere in this picture — reads never involve it.", 6.3);
  cases(s, [
    "Same query, twice: 120 ms then 2 ms — only the data's location changed",
    "A read that finds everything local costs storage nothing",
  ]);
}

// Warm vs cold
{
  const s = slide("PART I · READS", "The broad picture: warm vs cold",
    "This is the only performance distinction worth carrying around for reads.\n\nA warm peer — data already local — answers in milliseconds or less. A cold peer — nothing local yet — pays a storage fetch for everything it touches, and the same query can be a hundred times slower.\n\nSame query, same data, same code. The difference is only where the data is when the query runs.\n\nThe consequence: most read-performance work in Datomic is really cache work, which is why caches get their own section (Part IV). When a read is slow, the first question is never 'is the query bad' — it is 'which of these two bars am I on, and why'.");

  barCompare(s, 0.9, 1.7, 11.4, [
    ["warm peer", 4, "~ms", C.ok],
    ["cold peer", 100, "~100×", C.bad],
  ], { labelW: 2.0, rowH: 0.7, gap: 0.3 });

  txt(s, "same query · same data · same code — only the data's location differs",
    { x: 0.6, y: 3.5, w: 12.1, h: 0.4, fontSize: 14.5, color: C.ink, align: "center", italic: true });

  box(s, 0.6, 4.2, 5.85, 1.3, { label: "warm", sub: "data already local — the peer answers\nfrom memory, storage is not consulted", fill: C.okBg, border: C.ok, size: 15, subSize: 12, subH: 0.7 });
  box(s, 6.85, 4.2, 5.85, 1.3, { label: "cold", sub: "everything is fetched first —\nthe query time IS the fetch time", fill: C.badBg, border: C.bad, size: 15, subSize: 12, subH: 0.7 });

  takeaway(s, "Most read-performance work in Datomic is really cache work — Part IV.", 5.85);
  cases(s, [
    "Slow read? First question: warm or cold — not 'is the query bad'",
    "The 100× is illustrative; the shape is what matters",
  ]);
}

// Catches of reads
{
  const s = slide("PART I · READS", "The catches of reads",
    "Three catches, in the order people hit them.\n\nOne: a restarted peer forgets everything it had pulled close. Its first requests re-fetch its whole world, so a fresh process is a slow process. This is why deploys hurt — Part IV picks it up.\n\nTwo: peers learn about writes independently. If you write through one peer and read through another — a load balancer will happily do this to you — the reading peer may not have seen the write yet. The fix is a contract: pass the t of the write along with the request, and d/sync waits until the peer has caught up to it. Not a bug: a multi-peer fact of life.\n\nThree: one query runs on one core. There is no parallel query engine and no setting that changes it. A slow query is fixed by its shape or by warmer data — never by more CPUs. Part III shows what CAN be parallelized.");

  const catches = [
    ["1 · a restarted peer forgets everything", "first requests re-fetch its whole world — a fresh process is a slow process  (→ Part IV: deploys)", C.warnBg, C.warn],
    ["2 · a new peer may not see your newest write", "write through peer A, read through peer B: pass the t along and wait for it", C.coolBg, C.cool],
    ["3 · one query runs on one core", "no setting changes this — fix the query's shape or warm the data, not the CPU count  (→ Part III)", C.badBg, C.bad],
  ];
  catches.forEach((c, i) => {
    const y = 1.5 + i * 1.22;
    bar(s, 0.6, y, 12.1, 1.05, c[2], { round: true });
    bar(s, 0.6, y, 0.09, 1.05, c[3]);
    txt(s, c[0], { x: 0.95, y: y + 0.12, w: 11.4, h: 0.4, fontSize: 15.5, color: C.ink, bold: true });
    txt(s, c[1], { x: 0.95, y: y + 0.56, w: 11.4, h: 0.4, fontSize: 12.5, color: C.body, fit: "shrink" });
  });

  codeBlock(s, "@(d/sync conn t)    ;; a db guaranteed to include the write at t", 0.6, 5.35, 12.1, 0.62);
  takeaway(s, "Cold, stale, single-threaded — the three ways a correct read still surprises you.", 6.2);
  cases(s, [
    "Save on one pod, refresh lands on another: pass the t",
    "Post-deploy slowness is catch 1 at fleet scale — Part IV",
  ]);
}

// ═══════════════════════════════════════════════════════════════════
// PART II · WRITES
// ═══════════════════════════════════════════════════════════════════

sectionSlide("II", "Writes", "Everything goes through one door",
  "The basics: one transactor applies every transaction, in order — that is what buys ACID with no locks. The easy example: batching, which is most of write throughput. Then the two catches: the writer can pause (failover), and the writer can push back (back-pressure).",
  "Every write in the system goes through one process, in order. That single door is what gives Datomic transactions with no locks and no conflicts to resolve — and it means write capacity is fixed. The skill is using the one door well, and knowing the two ways it can make you wait.");

// How a write works
{
  const s = slide("PART II · WRITES", "How a write works",
    "Every peer submits its transactions to the same single transactor, which applies them one at a time, in order, and writes them durably to storage.\n\nWhy one door: because there is exactly one writer applying transactions in one order, there are no locks, no deadlocks, and no write conflicts to resolve — ever. The serialization is not a limitation that Datomic tolerates; it is the mechanism that makes transactions simple.\n\nThe flip side is on the right: write capacity is fixed. There is no second write machine to add — Part III returns to this. What you can do is use the one door well, which is the next slide.");

  [0, 1, 2].forEach(i => {
    box(s, 0.9, 1.6 + i * 1.0, 1.9, 0.72, { label: "peer", fill: C.panel, border: C.rule, size: 13 });
    arrow(s, 2.8, 1.96 + i * 1.0, 4.6, 2.9, { color: C.warn });
  });
  box(s, 4.6, 2.5, 3.2, 0.9, { label: "ONE transactor", sub: "in order, one at a time", fill: C.warnBg, border: C.warn, size: 14, subSize: 10.5, subColor: C.warn });
  arrow(s, 7.8, 2.95, 9.0, 2.95, { color: C.warn });
  box(s, 9.0, 2.5, 3.4, 0.9, { label: "storage", sub: "durable, immediately", fill: C.dark, labelColor: C.white, size: 14, subSize: 10.5, subColor: C.rule });

  box(s, 0.6, 4.15, 5.85, 1.5, { label: "what the one door BUYS", sub: "ACID transactions with no locks,\nno deadlocks, no conflicts to resolve", fill: C.okBg, border: C.ok, size: 14, subSize: 12.5, subH: 0.75 });
  box(s, 6.85, 4.15, 5.85, 1.5, { label: "what the one door COSTS", sub: "write capacity is fixed —\nthere is no second machine to add", fill: C.badBg, border: C.bad, size: 14, subSize: 12.5, subH: 0.75 });

  takeaway(s, "Serialized writes are not a limitation Datomic tolerates — they are what makes transactions simple.", 6.0);
  cases(s, [
    "No lock ever taken, no deadlock ever possible",
    "Write capacity: fixed. Use the door well — next slide",
  ]);
}

// Batching
{
  const s = slide("PART II · WRITES", "Using the one door well: batch",
    "The easy example for writes, and it is most of the story.\n\nTop: a thousand rows, transacted one at a time. Every row pays a full trip through the door — a network round trip and a durable write.\n\nBottom: the same thousand rows in batches of a hundred. Ten trips instead of a thousand. Same data, same guarantees, an order of magnitude less overhead.\n\nThe second lever, in one sentence: d/transact returns a future, and code that derefs each one immediately waits out every round trip before starting the next. Keeping a few batches in flight — 'pipelining' — overlaps those waits. It matters over real networks; it is invisible on a laptop.\n\nBut batching comes first. It is 90% of the win and requires nothing but partition-all.");

  txt(s, "slow — 1,000 trips through the door", { x: 0.6, y: 1.45, w: 6.0, h: 0.35, fontSize: 13.5, color: C.bad, bold: true });
  codeBlock(s, "(doseq [r rows]\n  @(d/transact conn [r]))", 0.6, 1.85, 5.85, 1.05);

  txt(s, "fast — 10 trips carrying 100 each", { x: 6.85, y: 1.45, w: 6.0, h: 0.35, fontSize: 13.5, color: C.ok, bold: true });
  codeBlock(s, "(doseq [batch (partition-all 100 rows)]\n  @(d/transact conn batch))", 6.85, 1.85, 5.85, 1.05);

  barCompare(s, 0.9, 3.4, 11.4, [
    ["1 per tx", 100, "1,000 trips", C.bad],
    ["100 per tx", 10, "10 trips", C.ok],
  ], { labelW: 2.0, rowH: 0.6, gap: 0.25 });

  bar(s, 0.6, 5.0, 12.1, 1.0, C.panel, { round: true });
  txt(s, "Second lever: don't wait for each acknowledgement — keep a few batches in flight (\"pipelining\").",
    { x: 0.9, y: 5.15, w: 11.5, h: 0.4, fontSize: 14, color: C.ink });
  txt(s, "It hides network round trips, so it matters over real networks and does nothing on a laptop.",
    { x: 0.9, y: 5.55, w: 11.5, h: 0.35, fontSize: 12.5, color: C.body });

  takeaway(s, "Batching is 90% of write throughput, and it needs nothing but partition-all.", 6.25);
  cases(s, [
    "Bulk import crawling at 1 row per transaction: batch it",
    "Pipelining helps over a network; invisible on datomic:mem",
  ]);
}

// Catch 1: failover
{
  const s = slide("PART II · WRITES", "Catch 1 · the writer can pause",
    "One process doing all the writes sounds like a single point of failure, and the fix is simple: run two transactors. One is active; the other is a standby that takes over automatically if the active one dies.\n\nThe timeline shows what a failover looks like from your application. Writes fail for a short window — seconds — until the standby has taken over. Reads never notice, because reads never involved the transactor in the first place.\n\nPeers reconnect by themselves: no URI change, no restart, no load balancer to flip.\n\nThe fine print, bottom right, is the part people get wrong: a standby is NOT a backup. Both transactors write to the same storage. Data safety comes from storage replication and backups — the standby only shortens the write pause.");

  txt(s, "WRITES", { x: 0.6, y: 1.72, w: 1.4, h: 0.4, fontSize: 13, color: C.ink, bold: true, charSpacing: 1 });
  const tx = 2.2, tw = 10.2;
  for (let i = 0; i < 16; i++) {
    const failing = i >= 6 && i <= 9;
    bar(s, tx + i * (tw / 16), 1.68, tw / 16 - 0.07, 0.5, failing ? C.bad : C.ok, { round: true });
  }
  txt(s, "a short pause (seconds) while the standby takes over", { x: tx, y: 2.25, w: tw, h: 0.32, fontSize: 12, color: C.bad });

  txt(s, "READS", { x: 0.6, y: 2.82, w: 1.4, h: 0.4, fontSize: 13, color: C.ink, bold: true, charSpacing: 1 });
  for (let i = 0; i < 16; i++) {
    bar(s, tx + i * (tw / 16), 2.78, tw / 16 - 0.07, 0.5, C.cool, { round: true });
  }
  txt(s, "never interrupted — reads don't involve the transactor", { x: tx, y: 3.35, w: tw, h: 0.32, fontSize: 12, color: C.cool });

  box(s, 0.6, 4.05, 5.85, 1.5, { label: "automatic", sub: "peers reconnect by themselves —\nno URI change, no restart, no manual step", fill: C.okBg, border: C.ok, size: 14.5, subSize: 12, subH: 0.75 });
  box(s, 6.85, 4.05, 5.85, 1.5, { label: "a standby is NOT a backup", sub: "both write the same storage —\ndata safety comes from storage replication", fill: C.badBg, border: C.bad, size: 14.5, subSize: 12, subH: 0.75 });

  takeaway(s, "HA turns a dead writer into a short write pause. Reads never even notice.", 5.95);
  cases(s, [
    "infra/HA.md: the two-transactor setup, to run this yourself",
    "Standby + unreplicated storage = short pauses, zero data safety",
  ]);
}

// Catch 2: back-pressure
{
  const s = slide("PART II · WRITES", "Catch 2 · the writer can push back",
    "The transactor has a background job: indexing. Recent writes accumulate in its memory and are periodically reorganized into storage. Durability is never deferred — every write hits the log immediately — but the reorganizing takes time.\n\nIf writes arrive faster than indexing can drain them, the buffer fills, and the transactor deliberately slows all writers down. That is back-pressure: latency instead of an out-of-memory crash.\n\nThe gauge shows the three states. Normal and draining are both healthy. Throttled is the one that appears on a dashboard as a mystery: write latency rising, and not a single error anywhere.\n\nWhen you see it, the question is not 'how do I turn throttling off' — it is 'why is indexing slower than my write rate'. The usual answer is that storage writes are slow.");

  const states = [
    ["NORMAL", 0.35, "buffer low · nothing special happening", C.ok, C.okBg, "writes fast"],
    ["DRAINING", 0.7, "indexing runs in the background · writes at full speed", C.warn, C.warnBg, "writes fast"],
    ["THROTTLED", 1.0, "buffer full · the transactor slows every writer on purpose", C.bad, C.badBg, "latency rises"],
  ];
  states.forEach((st, i) => {
    const y = 1.65 + i * 1.28;
    txt(s, st[0], { x: 0.6, y, w: 2.0, h: 0.65, fontSize: 13.5, color: st[3], bold: true, charSpacing: 1, valign: "middle" });
    const gx = 2.8, gw = 7.4;
    bar(s, gx, y, gw, 0.65, C.panel);
    bar(s, gx, y, gw * st[1], 0.65, st[4]);
    bar(s, gx + gw - 0.04, y - 0.1, 0.04, 0.85, C.bad);
    if (i === 0) txt(s, "full", { x: gx + gw - 0.9, y: y - 0.42, w: 1.8, h: 0.3, fontSize: 10.5, color: C.bad, align: "center" });
    txt(s, st[2], { x: 2.8, y: y + 0.68, w: 7.4, h: 0.34, fontSize: 11.5, color: C.body, fit: "shrink" });
    bar(s, 10.5, y, 2.2, 0.65, st[4], { round: true });
    txt(s, st[5], { x: 10.5, y, w: 2.2, h: 0.65, fontSize: 12, color: st[3], align: "center", valign: "middle", bold: true });
  });

  bar(s, 0.6, 5.65, 12.1, 0.7, C.panel, { round: true });
  txt(s, "The symptom: write latency rising with NO errors anywhere. Not a failure — the system protecting itself.",
    { x: 0.9, y: 5.65, w: 11.5, h: 0.7, fontSize: 13.5, color: C.ink, valign: "middle", fit: "shrink" });

  takeaway(s, "Ask why indexing is slow — usually storage — before touching any knob.", 6.45);
  cases(s, [
    "p99 writes up, error rate flat: back-pressure, look at storage",
    "Durability never waits — only indexing lags",
  ]);
}

// Break
{
  const s = pres.addSlide();
  bar(s, 0, 0, W, H, C.dark);
  page += 1;
  txt(s, "☕", { x: 0.9, y: 2.2, w: 2, h: 1.0, fontSize: 54, color: C.goldHi });
  txt(s, "Break — 10 minutes", { x: 0.9, y: 3.3, w: 11.5, h: 0.9, fontFace: F.serif, fontSize: 36, color: C.white });
  txt(s, "Next: what actually gets faster with more cores — and what never will.",
    { x: 0.9, y: 4.3, w: 11.5, h: 0.5, fontSize: 17, color: C.rule });
  txt(s, String(page), { x: 12.2, y: 7.0, w: 0.7, h: 0.28, fontSize: 11, color: C.meta, align: "right" });
  s.addNotes("Halfway. So far: the map, reads (warm vs cold), writes (one door, batching, the two waits). After the break: parallelism and caches.");
}

// ═══════════════════════════════════════════════════════════════════
// PART III · PARALLELISM
// ═══════════════════════════════════════════════════════════════════

sectionSlide("III", "Parallelism", "Reads scale out — writes never do",
  "The basics: 'parallel' means opposite things on the two sides of the system. Two easy examples on the read side — splitting a big read, and trying a transaction without committing it. Then the catches, all three of which are versions of 'you looked for parallelism on the wrong side'.",
  "Reads parallelize freely: every peer, every core, over an immutable value that nothing can change mid-read. Writes never parallelize: one door, by design. Most parallelism mistakes are just looking for it on the wrong side.");

// One word two sides
{
  const s = slide("PART III · PARALLELISM", "One word, two sides",
    "The write side: one process, serial, by design — Part II. It cannot be parallelized; it can only be kept busy, with batching and by keeping a few transactions in flight.\n\nThe read side: every peer, every core, in parallel, by default. No locks and no coordination — and the reason is worth saying precisely: a Datomic db is an immutable VALUE. Nothing can change under a reader, so there is nothing to lock, no snapshot to manage, and no way for two readers to interfere.\n\nSo when the word 'parallelism' comes up, first ask which side you are on. On the write side the lever is keeping the one door busy. On the read side the lever is splitting the work up — which is the next slide.");

  panel(s, 0.6, 1.5, 5.85, 3.7, "WRITES", { strip: C.warnBg, titleColor: C.warn });
  box(s, 0.9, 2.15, 5.25, 0.8, { label: "one process, serial", fill: C.warnBg, border: C.warn, size: 15 });
  bullets(s, ["cannot be parallelized — by design", "lever: batch, and keep it busy"], { x: 1.0, y: 3.2, w: 5.1, h: 1.0, size: 13 });
  txt(s, "(Part II)", { x: 0.9, y: 4.55, w: 5.25, h: 0.35, fontSize: 12, color: C.meta, align: "center", italic: true });

  panel(s, 6.85, 1.5, 5.85, 3.7, "READS", { strip: C.coolBg, titleColor: C.cool });
  [0, 1, 2, 3].forEach(i =>
    box(s, 7.15 + i * 1.35, 2.15, 1.2, 0.8, { label: "core", fill: C.coolBg, border: C.cool, size: 11 }));
  bullets(s, ["parallel by default — no locks", "lever: split the work up"], { x: 7.25, y: 3.2, w: 5.1, h: 1.0, size: 13 });
  txt(s, "(next two slides)", { x: 7.15, y: 4.55, w: 5.25, h: 0.35, fontSize: 12, color: C.meta, align: "center", italic: true });

  bar(s, 0.6, 5.45, 12.1, 0.95, C.panel, { round: true });
  txt(s, "Why reads are safe to parallelize: a db is an immutable VALUE — nothing can change under a reader.",
    { x: 0.9, y: 5.45, w: 11.5, h: 0.95, fontSize: 14.5, color: C.ink, valign: "middle", fit: "shrink" });
  cases(s, [
    "\"Can we parallelize this?\" — first ask: read side or write side?",
    "No locks, no snapshots, no read transactions — a value can't move",
  ]);
}

// Split a big read
{
  const s = slide("PART III · PARALLELISM", "Easy example · split a big read",
    "One hundred thousand readings to scan. Cut the range into 8 slices along the index, pmap across cores — each slice is a completely independent read over the same immutable db value.\n\nSerial: 78.7 ms. Eight slices: 24.7 ms. About 3× on this laptop.\n\nTwo rules of thumb are all you need. Slice roughly to the number of cores — hundreds of tiny slices just add hand-off overhead and slowly eat the gain. And notice there is no cleanup: no snapshot was opened, no lock taken, nothing leaks. Splitting a read is a purely local decision by the code doing the reading.\n\nNo transactor involved anywhere — this is read-side parallelism, and it scales with cores and peers.");

  txt(s, "100,000 readings, cut into 8 slices along the index", { x: 0.6, y: 1.45, w: 12.1, h: 0.3, fontSize: 13, color: C.meta });
  for (let i = 0; i < 8; i++) {
    const x = 0.6 + i * 1.52;
    bar(s, x, 1.85, 1.4, 0.58, C.coolBg, { round: true });
    txt(s, "slice " + (i + 1), { x, y: 1.85, w: 1.4, h: 0.58, fontSize: 11.5, color: C.cool, align: "center", valign: "middle" });
    arrow(s, x + 0.7, 2.43, x + 0.7, 2.8, { color: C.cool, width: 1.1 });
    bar(s, x, 2.8, 1.4, 0.32, C.cool, { round: true });
    txt(s, "core", { x, y: 2.8, w: 1.4, h: 0.32, fontSize: 10, color: C.white, align: "center", valign: "middle" });
  }
  txt(s, "one immutable db value · pmap across cores", { x: 0.6, y: 3.2, w: 12.1, h: 0.3, fontSize: 12.5, color: C.cool, align: "center", italic: true });

  barCompare(s, 0.6, 3.75, 7.6, [
    ["serial", 78.7, "78.7 ms", C.panel2],
    ["8 slices", 24.7, "24.7 ms", C.cool],
  ], { labelW: 1.6, rowH: 0.6 });
  bar(s, 8.5, 3.75, 4.2, 1.28, C.coolBg, { round: true });
  txt(s, "≈ 3×", { x: 8.5, y: 3.9, w: 4.2, h: 0.65, fontFace: F.serif, fontSize: 30, color: C.cool, align: "center" });
  txt(s, "on this laptop", { x: 8.5, y: 4.6, w: 4.2, h: 0.3, fontSize: 11.5, color: C.meta, align: "center" });

  box(s, 0.6, 5.3, 5.85, 1.1, { label: "slice ≈ number of cores", sub: "hundreds of tiny slices just add overhead", fill: C.panel, border: C.rule, size: 13.5, subSize: 11.5 });
  box(s, 6.85, 5.3, 5.85, 1.1, { label: "nothing to clean up", sub: "no snapshot, no lock, no transactor involved", fill: C.panel, border: C.rule, size: 13.5, subSize: 11.5 });

  cases(s, [
    "78.7 → 24.7 ms with 8 slices and pmap",
    "Every slice reads the same db value — nothing to coordinate",
  ]);
}

// d/with
{
  const s = slide("PART III · PARALLELISM", "Easy example · try before you commit",
    "d/with applies a transaction to a db VALUE and hands back a new value: 'what would the db look like if…'. No transactor, nothing durable, nothing shared.\n\nBecause each what-if is just a value computed locally, you can run many at once with pmap — three scenarios in the diagram, each seeing its own future, while the real db is untouched.\n\nThe two production uses worth remembering: validating a big import BEFORE spending transactor time on it — a uniqueness violation surfaces in the dry run, not halfway through the real load — and comparing what-if scenarios in parallel.\n\nThis is the write-shaped operation that lives on the read side: all the transaction machinery, none of the door.");

  box(s, 0.6, 1.6, 2.6, 1.0, { label: "db", sub: "the real value", fill: C.dark, labelColor: C.white, size: 18, subSize: 11, subColor: C.rule });
  ["scenario A", "scenario B", "scenario C"].forEach((sc, i) => {
    const y = 1.55 + i * 1.05;
    arrow(s, 3.2, 2.1, 4.6, y + 0.35, { color: C.gold });
    box(s, 4.6, y, 3.6, 0.7, { label: "d/with " + sc, fill: C.panel, border: C.rule, size: 12.5 });
    box(s, 8.6, y, 4.1, 0.7, { label: "a what-if value", fill: C.coolBg, border: C.cool, size: 12.5 });
  });
  txt(s, "no transactor · nothing durable · the real db is untouched", { x: 4.6, y: 4.7, w: 8.1, h: 0.3, fontSize: 12.5, color: C.cool, italic: true, align: "center" });

  codeBlock(s,
    "(d/with db proposed-tx)                    ;; => a new value; real db unchanged\n(pmap #(check (d/with db %)) scenarios)    ;; many at once, safely",
    0.6, 5.15, 8.0, 1.0);
  bar(s, 8.9, 5.15, 3.8, 1.0, C.panel, { round: true });
  txt(s, "USE FOR", { x: 9.15, y: 5.25, w: 3.3, h: 0.28, fontSize: 11, color: C.gold, bold: true, charSpacing: 1.5 });
  txt(s, "import dry runs · what-if scenarios", { x: 9.15, y: 5.55, w: 3.3, h: 0.55, fontSize: 12, color: C.body });

  takeaway(s, "All the transaction machinery, none of the door: validate cheaply, commit once.", 6.35);
  cases(s, [
    "Dry-run a bulk import before spending transactor time on it",
    "A uniqueness violation surfaces in the what-if, not mid-load",
  ]);
}

// Catches of parallelism
{
  const s = slide("PART III · PARALLELISM", "The catches of parallelism",
    "All three catches are versions of one mistake: looking for parallelism on the wrong side.\n\nOne: a single d/q runs on one core, and no setting changes that. Parallelism is always across queries, or across slices you cut yourself. One slow query next to eight idle cores stays slow.\n\nTwo: a second transactor adds zero write throughput. It is a standby for failover — Part II — not a second worker. It is idle by design and will stay idle.\n\nThree: on a cold peer, a parallel-read speedup is mostly overlapping storage fetches, not dividing CPU work. That is still useful — but if the numbers look too good, check whether the real fix is a warm cache rather than more threads.");

  const catches = [
    ["1 · one d/q = one core", "no setting changes this — parallelism is across queries, or across slices you cut", C.badBg, C.bad],
    ["2 · a second transactor adds ZERO write throughput", "it is a standby for failover (Part II), not a second worker — idle by design", C.warnBg, C.warn],
    ["3 · on a cold peer, the speedup is mostly overlapped waiting", "you are overlapping fetches, not dividing CPU work — the real fix may be a warm cache", C.coolBg, C.cool],
  ];
  catches.forEach((c, i) => {
    const y = 1.55 + i * 1.35;
    bar(s, 0.6, y, 12.1, 1.15, c[2], { round: true });
    bar(s, 0.6, y, 0.09, 1.15, c[3]);
    txt(s, c[0], { x: 0.95, y: y + 0.14, w: 11.4, h: 0.42, fontSize: 15.5, color: C.ink, bold: true, fit: "shrink" });
    txt(s, c[1], { x: 0.95, y: y + 0.6, w: 11.4, h: 0.45, fontSize: 12.5, color: C.body, fit: "shrink" });
  });

  takeaway(s, "Before parallelizing, ask which side you are on. The write side has one answer: batch.", 5.85);
  cases(s, [
    "One slow query, eight idle cores: the cores stay idle",
    "Write throughput problem 'solved' with a second transactor: still 1× ",
  ]);
}

// ═══════════════════════════════════════════════════════════════════
// PART IV · CACHES
// ═══════════════════════════════════════════════════════════════════

sectionSlide("IV", "Caches", "Where the read cost actually goes",
  "Part I said a read costs whatever fetching costs. This section is about not fetching. The basics: a ladder of four tiers, and the block-not-row unit of caching. Then the one big catch — deploys empty the caches — and two small ones.",
  "Part I established that a read costs whatever fetching costs. Caches are how you stop fetching. The model is a ladder of four tiers; the unit moving through it is a block of the index, not a row; and the big catch is that a deploy empties most of the ladder at the worst possible moment.");

// The ladder
{
  const s = slide("PART IV · CACHES", "The ladder — a read walks down until a tier answers",
    "Four tiers, fastest first.\n\nTier 1, the object cache, lives in the peer's own memory. It comes for free — every peer has one.\n\nTier 2, valcache, is a local SSD cache. Its superpower is not speed: it SURVIVES RESTARTS, which — remember catch 1 of reads — is exactly the event that empties tier 1.\n\nTier 3, memcached, is shared between peers: if one peer fetched something, its siblings find it here without touching storage.\n\nTier 4 is storage itself — the slowest, and on DynamoDB the one that costs money per read.\n\nTiers 2 and 3 are optional add-ons; you can run with tier 1 only. And the whole job of cache configuration, in one sentence: keep everyday reads answering from as high up the ladder as possible.");

  const tiers = [
    ["1", "object cache", "in the peer's memory — free, every peer has one", "~instant", C.dark, C.white],
    ["2", "valcache", "local SSD — survives restarts!", "fast", C.gold, C.white],
    ["3", "memcached", "shared between peers", "~1 ms", C.panel2, C.ink],
    ["4", "storage", "the database itself", "slow, $", C.panel, C.ink],
  ];
  let ty = 1.5;
  tiers.forEach((t, i) => {
    const h = 0.92;
    const indent = i * 0.35;
    bar(s, 1.0 + indent, ty, 8.7 - indent, h, t[4], { round: true });
    txt(s, t[0], { x: 1.25 + indent, y: ty, w: 0.5, h, fontFace: F.serif, fontSize: 20, color: t[5], bold: true, valign: "middle" });
    txt(s, t[1], { x: 1.85 + indent, y: ty + 0.12, w: 3.2, h: 0.38, fontSize: 15, color: t[5], bold: true });
    txt(s, t[2], { x: 1.85 + indent, y: ty + 0.5, w: 6.4 - indent, h: 0.34, fontSize: 11.5, color: t[5], fit: "shrink" });
    txt(s, t[3], { x: 10.0, y: ty, w: 2.6, h, fontFace: F.mono, fontSize: 13.5, color: C.body, valign: "middle" });
    ty += h + 0.14;
  });
  arrow(s, 0.75, 1.7, 0.75, 5.4, { color: C.meta, width: 1.2 });
  txt(s, "a read walks down", { x: 0.18, y: 5.5, w: 2.2, h: 0.3, fontSize: 10.5, color: C.meta });

  takeaway(s, "Your whole job: keep everyday reads answering from as high up as possible. Tiers 2–3 are optional add-ons.", 5.95);
  cases(s, [
    "Nothing ever needs invalidating — immutable data is correct forever",
    "That is why four stacked caches need zero coordination",
  ]);
}

// Segments
{
  const s = slide("PART IV · CACHES", "The unit is a block, not a row",
    "What actually moves through the ladder is not an entity and not a row. It is a SEGMENT: a block of the index holding thousands of neighboring datoms.\n\nAsk for one entity, and the peer fetches the whole block it lives in — the neighbors come along free.\n\nThe consequence is the one sizing intuition worth having: data read together is cheap if it lives together in an index. Scanning a range of readings by time touches a few blocks. Fetching a thousand entities scattered all over the index can touch a thousand blocks. Same answer size — about a hundred times the fetching.\n\nSo when a workload seems mysteriously expensive despite a healthy cache, look at its access pattern: scattered reads defeat every tier of the ladder at once.");

  txt(s, "you ask for ONE entity …", { x: 0.6, y: 1.5, w: 5.8, h: 0.35, fontSize: 14, color: C.ink, bold: true });
  txt(s, "… a whole BLOCK arrives, neighbours included", { x: 6.5, y: 1.5, w: 6.0, h: 0.35, fontSize: 14, color: C.ink, bold: true });

  const segs = 5, sw = 2.34;
  for (let i = 0; i < segs; i++) {
    const x = 0.6 + i * sw;
    const hot = i === 2;
    bar(s, x, 2.1, sw - 0.12, 1.15, hot ? C.goldHi : C.panel, { round: true });
    txt(s, "block " + (i + 1), { x, y: 2.17, w: sw - 0.12, h: 0.3, fontSize: 11, color: hot ? C.dark : C.meta, align: "center" });
    for (let k = 0; k < 5; k++) {
      bar(s, x + 0.18 + k * 0.4, 2.6, 0.3, 0.45, hot ? C.dark : C.panel2);
    }
  }
  bar(s, 5.28, 3.35, 2.22, 0.1, C.gold);
  txt(s, "one block fetched — thousands of neighbours came along free", { x: 3.3, y: 3.5, w: 8.0, h: 0.35, fontSize: 13, color: C.gold, bold: true, align: "center" });

  box(s, 0.6, 4.2, 5.85, 1.4, { label: "together in the index", sub: "a time-range scan touches a few blocks\n→ cheap", fill: C.okBg, border: C.ok, size: 14, subSize: 12, subH: 0.7 });
  box(s, 6.85, 4.2, 5.85, 1.4, { label: "scattered across the index", sub: "1,000 scattered entities ≈ 1,000 blocks\n→ ~100× the fetching, same answer", fill: C.badBg, border: C.bad, size: 14, subSize: 12, subH: 0.7 });

  takeaway(s, "Data read together is cheap if it lives together. Scattered reads defeat every tier at once.", 5.95);
  cases(s, [
    "Range scan by time: a few blocks",
    "Random lookups all over: a block per lookup — no cache tier fixes it",
  ]);
}

// Catch: deploys
{
  const s = slide("PART IV · CACHES", "Catch 1 · deploys empty the caches",
    "This is catch 1 of reads — a restarted peer forgets everything — at fleet scale, and it is the most common Datomic performance incident in the wild.\n\nRestart all twenty peers at once and twenty empty caches ask storage the same questions at the same moment. Latency spikes, there are zero errors, and it resolves itself in a few minutes as the caches refill.\n\nBecause it looks like a mystery, it routinely gets misread as a bad release — and rolled back, which restarts every peer again and repeats the spike.\n\nThree fixes, cheapest first. Rolling deploys: replace a few peers at a time, so most of the fleet stays warm — needs nothing. Memcached: twenty cold peers cause one shared miss instead of twenty — needs a server. Valcache: the SSD tier survives the restart entirely — needs a disk.");

  bar(s, 0.6, 1.5, 12.1, 1.5, C.badBg, { round: true });
  txt(s, "restart 20 peers at once  →  20 empty caches  →  everyone fetches the same things at the same moment",
    { x: 0.9, y: 1.65, w: 11.5, h: 0.45, fontSize: 15, color: C.ink, bold: true, fit: "shrink" });
  txt(s, "latency spikes · zero errors · resolves itself in minutes — and often gets misread as a bad release and rolled back (which restarts everything again)",
    { x: 0.9, y: 2.2, w: 11.5, h: 0.65, fontSize: 12.5, color: C.body });

  txt(s, "Three fixes, cheapest first:", { x: 0.6, y: 3.35, w: 12.1, h: 0.35, fontSize: 14, color: C.ink, bold: true });
  const mits = [
    ["rolling deploys", "replace a few peers at a time — most of the fleet stays warm", "needs nothing", C.okBg, C.ok],
    ["memcached", "20 cold peers cause 1 shared miss, not 20", "needs a server", C.coolBg, C.cool],
    ["valcache", "the SSD tier survives the restart entirely", "needs a disk", C.warnBg, C.warn],
  ];
  mits.forEach((m, i) => {
    const x = 0.6 + i * 4.07;
    bar(s, x, 3.8, 3.87, 1.8, m[3], { round: true });
    txt(s, String(i + 1), { x: x + 0.2, y: 3.95, w: 0.4, h: 0.35, fontFace: F.serif, fontSize: 17, color: m[4], bold: true });
    txt(s, m[0], { x: x + 0.7, y: 3.97, w: 3.0, h: 0.35, fontSize: 14.5, color: C.ink, bold: true });
    txt(s, m[1], { x: x + 0.7, y: 4.4, w: 3.0, h: 0.75, fontSize: 12, color: C.body });
    txt(s, "(" + m[2] + ")", { x: x + 0.7, y: 5.2, w: 3.0, h: 0.3, fontSize: 11.5, color: m[4], italic: true });
  });

  takeaway(s, "Post-deploy latency spike with zero errors: cold caches, not a bad release. Don't roll back.", 5.95);
  cases(s, [
    "The rollback restarts every peer and repeats the spike",
    "Rolling deploys fix most of it and need nothing",
  ]);
}

// Small catches
{
  const s = slide("PART IV · CACHES", "Catches 2 & 3 · small but common",
    "Two smaller catches to file away.\n\nMemcached dying breaks nothing. Every tier of the ladder is optional except storage, so losing the shared tier means every read that would have hit it walks further down — everything gets slower, nothing gets wrong. Treat it as a degradation to fix during business hours, not a page.\n\nAnd you cannot ask the cache anything. No peer API reports what is cached or how full any tier is — d/db-stats counts the data, not the cache. Cache behaviour is observed from the outside: time the same query twice and compare, or watch storage-read metrics and the memcached hit ratio.");

  panel(s, 0.6, 1.5, 5.85, 4.3, "2 · MEMCACHED DYING BREAKS NOTHING", { strip: C.okBg, titleColor: C.ok });
  prose(s, "Every tier except storage is optional. Lose the shared tier and reads walk further down the ladder: everything slower, nothing wrong.",
    { x: 0.9, y: 2.15, w: 5.3, h: 1.3, size: 13 });
  box(s, 0.9, 3.6, 5.25, 1.0, { label: "a degradation, not a page", sub: "fix it during business hours", fill: C.okBg, border: C.ok, size: 14, subSize: 11.5 });

  panel(s, 6.85, 1.5, 5.85, 4.3, "3 · YOU CAN'T ASK THE CACHE", { strip: C.coolBg, titleColor: C.cool });
  prose(s, "No API reports what is cached — d/db-stats counts the data, not the cache. Observe from outside:",
    { x: 7.15, y: 2.15, w: 5.3, h: 1.0, size: 13 });
  box(s, 7.15, 3.25, 5.25, 0.72, { label: "time the same query twice", fill: C.coolBg, border: C.cool, size: 13 });
  box(s, 7.15, 4.1, 5.25, 0.72, { label: "watch storage reads & hit ratios", fill: C.coolBg, border: C.cool, size: 13 });

  takeaway(s, "Optional tiers fail soft. And cache behaviour is observed, never queried.", 6.15);
  cases(s, [
    "Memcached restart: latency up, correctness untouched",
    "\"Did the cache help?\" — run it twice and compare",
  ]);
}

// ═══════════════════════════════════════════════════════════════════
// SUMMARY + CLOSE
// ═══════════════════════════════════════════════════════════════════

// Summary
{
  const s = slide("SUMMARY", "Four verbs",
    "The whole class compresses into four rows, and any performance symptom lands on one of them.\n\nFails: only storage is fatal. A dead transactor pauses writes; a dead peer is local.\n\nWaits: writers wait, in exactly two places — during a failover, and during back-pressure. If writes are slow with no errors, it is one of those two.\n\nScales: reads scale with peers and cores; writes scale only by batching. Nothing scales inside a single query.\n\nCosts: storage, whenever a cache is cold. If reads are slow, ask which tier answered.\n\nUse it as a diagnosis path: writes failing is FAILS; writes slow is WAITS; reads slow is COSTS; and 'can we throw cores at it' is SCALES.");

  const rows = [
    ["FAILS",  "only storage is fatal · transactor = writes pause · a peer = local", C.bad, C.badBg, "the map · II"],
    ["WAITS",  "writers — during failover, and during back-pressure", C.warn, C.warnBg, "Part II"],
    ["SCALES", "reads: peers × cores · writes: only batching", C.ok, C.okBg, "Part III"],
    ["COSTS",  "storage, whenever a cache is cold", C.cool, C.coolBg, "Parts I & IV"],
  ];
  rows.forEach((r, i) => {
    const y = 1.6 + i * 1.15;
    bar(s, 0.6, y, 12.1, 1.0, r[3], { round: true });
    bar(s, 0.6, y, 0.09, 1.0, r[2]);
    txt(s, r[0], { x: 0.95, y, w: 2.3, h: 1.0, fontSize: 16, color: r[2], bold: true, charSpacing: 1.5, valign: "middle" });
    txt(s, r[1], { x: 3.4, y, w: 7.3, h: 1.0, fontSize: 14.5, color: C.ink, valign: "middle", fit: "shrink" });
    txt(s, r[4], { x: 10.8, y, w: 1.7, h: 1.0, fontSize: 11.5, color: C.meta, align: "right", valign: "middle" });
  });

  takeaway(s, "Any performance symptom lands on one of these four rows. Start there.", 6.35);
  cases(s, [
    "Writes failing → FAILS · writes slow → WAITS · reads slow → COSTS",
  ]);
}

// Where to go next
{
  const s = slide("CLOSE", "Where to go next",
    "For depth on everything this class kept broad: the Production class has the write path, read path, settings and the backup drill in full detail.\n\ninfra/HA.md has the two-transactor setup, to run Part II's failover yourself — worth doing once on your own staging environment.\n\nOn Datomic Cloud the model is the same and the platform does the operating: failover is managed for you, and query groups are Part III's read scaling packaged as an autoscaling group.");

  const items = [
    ["src/datomic_infra/labs.clj", "Datomic in Production — the write path, read path, settings and backup drill in full detail", C.panel],
    ["infra/HA.md", "the two-transactor setup — run Part II's failover yourself", C.panel],
    ["Datomic Cloud", "same model, managed operations: failover handled by the platform; query groups = Part III's read scaling as an autoscaling group", C.coolBg],
  ];
  items.forEach((it, i) => {
    const y = 1.6 + i * 1.3;
    bar(s, 0.6, y, 12.1, 1.15, it[2], { round: true });
    txt(s, it[0], { x: 0.9, y: y + 0.15, w: 11.5, h: 0.38, fontFace: F.mono, fontSize: 14, color: C.gold, bold: true });
    txt(s, it[1], { x: 0.9, y: y + 0.57, w: 11.5, h: 0.5, fontSize: 13.5, color: C.body, fit: "shrink" });
  });

  bar(s, 0.6, 5.6, 12.1, 1.0, C.okBg, { round: true });
  bar(s, 0.6, 5.6, 0.09, 1.0, C.ok);
  txt(s, "Carry the model, look up the knobs.", { x: 0.95, y: 5.72, w: 11.5, h: 0.4, fontSize: 16, color: C.ink, bold: true });
  txt(s, "Four topics, three steps each: how it works, an easy example, the catches.",
    { x: 0.95, y: 6.14, w: 11.5, h: 0.4, fontSize: 13, color: C.body });
  cases(s, [
    "Run the failover drill once on your own staging environment",
  ]);
}

// ═══════════════════════════════════════════════════════════════════
// QA CHECKS
// ═══════════════════════════════════════════════════════════════════
if (process.env.DECK_QA) {
  const CPI = f => (72 / (f * 0.52));           // chars per inch at font size f
  const LH  = f => (f * 1.22 / 72);             // line height in inches
  const rect = t => {
    const o = t.o, fs = o.fontSize || 14, w = o.w || 1, h = o.h || 0.3;
    const mono = o.fontFace === "Courier New";
    const cpi = mono ? 72 / (fs * 0.60) : CPI(fs);
    const cpl = Math.max(1, Math.floor(w * cpi));
    const lines = t.str.split("\n").reduce((n, ln) => n + Math.max(1, Math.ceil(ln.length / cpl)), 0);
    const longest = Math.max(...t.str.split("\n").map(l => l.length));
    const rw = Math.min(w, longest / cpi + 0.08);
    const need = lines * LH(fs);
    let x = o.x || 0;
    if (o.align === "center") x = x + (w - rw) / 2;
    else if (o.align === "right") x = x + (w - rw);
    return { x, y: o.y || 0, w: rw, h: Math.min(need, h), need, boxH: h, lines, fs };
  };
  const overlap = (a, b) => a.x < b.x + b.w - 0.05 && b.x < a.x + a.w - 0.05 &&
                            a.y < b.y + b.h - 0.05 && b.y < a.y + a.h - 0.05;
  let issues = 0;
  QA.forEach((sl, i) => {
    const rs = sl.texts.map(rect);
    rs.forEach((r, k) => {
      if (r.need > r.boxH && r.boxH > 0.2) {
        issues++;
        console.log(`slide ${i + 1} OVERFLOW  needs ${r.need.toFixed(2)}" in ${r.boxH.toFixed(2)}"  «${sl.texts[k].str.slice(0, 58).replace(/\n/g, "⏎")}»`);
      }
    });
    if (sl.chrome) sl.shapes.forEach(sh => {
      const y = sh.y || 0;
      if (y > 0.62 && y < 1.40) {
        issues++;
        console.log(`slide ${i + 1} TITLEBAND shape at y=${y.toFixed(2)} (content must start at y ≥ 1.40)`);
      }
    });
    for (let a = 0; a < rs.length; a++)
      for (let b = a + 1; b < rs.length; b++)
        if (overlap(rs[a], rs[b])) {
          issues++;
          console.log(`slide ${i + 1} COLLIDE   «${sl.texts[a].str.slice(0, 34).replace(/\n/g, "⏎")}»  ×  «${sl.texts[b].str.slice(0, 34).replace(/\n/g, "⏎")}»`);
        }
  });
  if (process.env.DECK_QA === "space") {
    QA.forEach((sl, i) => {
      // ignore the CASES band (y >= 6.9) and the page-number box, so this
      // reports the room available for body content, not the room below the strip
      const bot = [...sl.texts.filter(t => (t.o.w || 1) > 0.9 && (t.o.y || 0) < 6.9).map(t => (t.o.y || 0) + (t.o.h || 0.3)),
                   ...sl.shapes.filter(sh => (sh.h || 0) < 7 && (sh.y || 0) < 6.9).map(sh => (sh.y || 0) + (sh.h || 0))]
                  .reduce((m, v) => Math.max(m, v), 0);
      console.log(`slide ${String(i + 1).padStart(2)}  content ends ${bot.toFixed(2)}"  room ${(6.95 - bot).toFixed(2)}"`);
    });
  }
  console.log(issues ? `\n${issues} QA issues` : "\nQA clean");
}

pres.writeFile({ fileName: "datomic-at-scale.pptx" }).then(f => console.log("wrote", f, "· slides:", page));
