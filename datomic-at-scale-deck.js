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

function sectionSlide(num, title, blurb, notes) {
  const s = pres.addSlide();
  bar(s, 0, 0, W, H, C.dark);
  page += 1;
  bar(s, 0, 2.2, 0.25, 2.2, C.gold);
  txt(s, "PART " + num, { x: 0.9, y: 2.3, w: 11.5, h: 0.4, fontSize: 15, color: C.goldHi, charSpacing: 3 });
  txt(s, title, { x: 0.9, y: 2.85, w: 11.5, h: 0.9, fontFace: F.serif, fontSize: 34, color: C.white });
  if (blurb) txt(s, blurb, { x: 0.9, y: 3.85, w: 11.5, h: 0.8, fontSize: 16, color: C.rule });
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

function note(slide, text, y = 6.7) {
  txt(slide, text, { x: 0.6, y, w: 12.1, h: 0.4, fontSize: 12.5, color: C.meta, italic: true, fit: "shrink" });
}

/** The recurring ⚑ waypoint panel: where the class leaves the slides for the REPL. */
function waypoint(slide, ref, tag, lines, opts = {}) {
  const y = opts.y === undefined ? 5.5 : opts.y;
  const h = opts.h || 1.35;
  const x = opts.x === undefined ? 0.6 : opts.x;
  const w = opts.w || 12.1;
  bar(slide, x, y, w, h, C.panel2, { round: true });
  bar(slide, x, y, 0.09, h, C.gold);
  txt(slide, "⚑  " + ref, {
    x: x + 0.3, y: y + 0.12, w: w - 2.2, h: 0.32,
    fontSize: 13, color: C.gold, bold: true, charSpacing: 1,
  });
  const tagColor = tag === "PRO" ? C.bad : C.ok;
  const tagBg    = tag === "PRO" ? C.badBg : C.okBg;
  if (tag) {
    bar(slide, x + w - 1.15, y + 0.12, 0.85, 0.32, tagBg, { round: true });
    txt(slide, "[" + tag + "]", {
      x: x + w - 1.15, y: y + 0.12, w: 0.85, h: 0.32,
      fontSize: 11, color: tagColor, bold: true, align: "center", valign: "middle",
    });
  }
  txt(slide, lines, {
    x: x + 0.3, y: y + 0.5, w: w - 0.6, h: h - 0.62,
    fontSize: 12.5, color: C.body, fontFace: opts.mono ? F.mono : F.sans, fit: "shrink",
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
  "Operations, parallelism, and the cost of a read",
  "Fifth class in the series. It continues from *Datomic in Production*: the deployment map, the write path, the read path and the backup/restore drill are covered there and are not repeated here.\n\nThe question this class answers: how does a Datomic system behave in production — what each component does when it fails, how failover works, what a read costs at each cache tier, where parallelism applies, which settings matter, and how peers are shipped.\n\nTwo hours, six parts, one part per question. The labs are reached from waypoint boxes and are optional at every point: the slides carry the model on their own."
);

// How the deck works
{
  const s = slide("FRAME", "How this deck works",
    "Slides carry the model and the diagrams. REPL work lives in src/datomic_ops/labs.clj (§0–§6) and is reached through waypoint boxes. The coupling is loose on purpose: a waypoint can be taken early, late, or skipped entirely without breaking the thread of the class.\n\nLabs marked [MEM] run on datomic:mem:// with nothing installed — every laptop in the room can run them, and the numbers on the slides came from those labs. Labs marked [PRO] need Postgres, one or two transactors and $DATOMIC — see infra/HA.md — and are demonstrated from the front, so nobody is blocked on infrastructure.");

  panel(s, 0.6, 1.45, 5.85, 2.5, "SLIDES");
  txt(s, "The model and the diagrams.\n\nEverything needed to follow the class is\nprojected — the labs add measurement,\nnot content.",
    { x: 0.85, y: 2.05, w: 5.35, h: 1.7, fontSize: 13.5, color: C.body });

  panel(s, 6.85, 1.45, 5.85, 2.5, "LABS · src/datomic_ops/labs.clj");
  txt(s, "§0–§6, reached from the ⚑ waypoint boxes.\n\nLoosely coupled: take one early, late,\nor not at all.",
    { x: 7.1, y: 2.05, w: 5.35, h: 1.7, fontSize: 13.5, color: C.body });

  // legend
  bar(s, 0.6, 4.25, 5.85, 1.05, C.okBg, { round: true });
  bar(s, 0.85, 4.5, 0.85, 0.32, C.ok, { round: true });
  txt(s, "[MEM]", { x: 0.85, y: 4.5, w: 0.85, h: 0.32, fontSize: 11, color: C.white, bold: true, align: "center", valign: "middle" });
  txt(s, "runs on datomic:mem:// — nothing installed, works on every laptop",
    { x: 1.95, y: 4.5, w: 4.4, h: 0.6, fontSize: 12.5, color: C.ink, valign: "middle", fit: "shrink" });

  bar(s, 6.85, 4.25, 5.85, 1.05, C.badBg, { round: true });
  bar(s, 7.1, 4.5, 0.85, 0.32, C.bad, { round: true });
  txt(s, "[PRO]", { x: 7.1, y: 4.5, w: 0.85, h: 0.32, fontSize: 11, color: C.white, bold: true, align: "center", valign: "middle" });
  txt(s, "needs Postgres + transactor(s) + $DATOMIC — see infra/HA.md",
    { x: 8.2, y: 4.5, w: 4.4, h: 0.6, fontSize: 12.5, color: C.ink, valign: "middle", fit: "shrink" });

  takeaway(s, "Assumed: Datomic in Production. Not repeated here: deployment map, write path, read path, backup/restore.", 5.6);
  note(s, "Setup: clj -M:repl covers every [MEM] lab.", 6.3);
}

// Agenda
{
  const s = slide("FRAME", "Two hours, six questions",
    "Each part answers one operational question, and the six answers compose into a single model: what fails, who takes over, what a read costs, what scales, which knob, and how it ships.\n\nThe minute marks are a plan, not a contract — the failover drill in Part II and the parallelism measurements in Part IV are the two places where a class typically runs long, and both are worth the overrun. Parts V and VI are lookups and compress well if the earlier parts ran over.");
  const rows = [
    ["0:00", "The failure map",     "what breaks, and how?",              C.gold],
    ["0:12", "High availability",   "who takes over, and how fast?",      C.gold],
    ["0:38", "The cost of a read",  "which tier answered?",               C.gold],
    ["1:02", "☕ Break — 10 min",    "—",                                  C.meta],
    ["1:12", "Parallelism",         "what scales, and what does not?",    C.gold],
    ["1:38", "Settings & signals",  "which knob, and what does it affect?", C.gold],
    ["1:52", "Deployment",          "how are peers shipped?",             C.gold],
  ];
  rows.forEach((r, i) => {
    const y = 1.5 + i * 0.62;
    if (i % 2 === 0) bar(s, 0.6, y, 12.1, 0.56, C.panel);
    txt(s, r[0], { x: 0.8, y, w: 1.0, h: 0.56, fontFace: F.mono, fontSize: 14, color: r[3], valign: "middle" });
    txt(s, r[1], { x: 2.0, y, w: 5.2, h: 0.56, fontSize: 15.5, color: C.ink, bold: true, valign: "middle", fit: "shrink" });
    txt(s, r[2], { x: 7.3, y, w: 5.2, h: 0.56, fontSize: 14, color: C.body, valign: "middle", fit: "shrink" });
  });
  note(s, "Setup: infra/HA.md for the live Part II lab. Otherwise clj -M:repl covers every [MEM] lab.", 6.0);
}

// ═══════════════════════════════════════════════════════════════════
// PART I · THE FAILURE MAP
// ═══════════════════════════════════════════════════════════════════

sectionSlide("I", "The failure map", "Four components · four different failures · one of them an outage",
  "The point of this part is structural, not procedural. In a single-server SQL deployment, everything that can die takes the application with it. In Datomic, three of the four things that can die do not.\n\nThe reason is the split: query, write, cache and durability live in four processes with four lifetimes. A failure takes down a responsibility rather than the system. Everything after this part is a consequence of the split — Part II covers the writer, Part III the reader, and neither mechanism helps the other.");

// The architecture
{
  const s = slide("PART I · THE FAILURE MAP", "Four components, and where the work happens",
    "Walk the picture top to bottom.\n\nPeers are libraries inside your JVM. The query engine runs there — not in a server. Each peer holds its own object cache of decoded segments.\n\nMemcached is optional and shared: peers consult it before storage, so N cold peers cause one storage miss instead of N.\n\nStorage is the source of truth AND the arbiter of who may write — that second role is what Part II is about.\n\nTransactors: exactly one writes at a time. The standby is a parked process holding no lease.\n\nTwo details decide most operational questions later. A peer is a library, so adding a peer means starting another instance of the application: there is no server to size, and query capacity scales with the number of application instances you already run.\n\nAnd the transactor is not in the read path at all. Peers read storage directly. That is why a dead transactor is a write outage and nothing more, and why no amount of transactor capacity makes reads faster.\n\nFailure signature: if reads slow down while the transactor is busy, the two are contending for storage, not for the transactor.");

  // peers
  [0, 1, 2].forEach(i => {
    const x = 2.4 + i * 2.6;
    box(s, x, 1.44, 1.9, 0.72, { label: "peer", fill: C.coolBg, border: C.cool, size: 14 });
    arrow(s, x + 0.95, 2.16, x + 0.95, 2.72, { color: C.cool, head: false });
    arrow(s, x + 0.95, 3.08, x + 0.95, 3.85, { color: C.cool });
  });
  txt(s, "query runs HERE\n(your JVM)", { x: 8.9, y: 1.44, w: 3.6, h: 0.8, fontSize: 13, color: C.cool, bold: true });

  // memcached
  box(s, 4.35, 2.72, 4.6, 0.62, { label: "memcached", fill: C.panel2, border: C.rule, size: 13 });
  txt(s, "optional · shared", { x: 8.9, y: 2.78, w: 3.6, h: 0.4, fontSize: 12.5, color: C.meta });

  // storage
  box(s, 2.4, 3.85, 6.5, 0.85, { label: "STORAGE", fill: C.dark, size: 16, labelColor: C.white });
  txt(s, "source of truth\n…and the arbiter", { x: 8.9, y: 3.85, w: 3.6, h: 0.8, fontSize: 13, color: C.ink, bold: true });

  // transactors
  box(s, 3.1, 5.5, 2.3, 0.8, { label: "transactor", sub: "ACTIVE — holds the lease", fill: C.okBg, border: C.ok, size: 13, subSize: 9.5, subColor: C.ok });
  box(s, 5.9, 5.5, 2.3, 0.8, { label: "standby", sub: "parked — no lease", fill: C.panel, border: C.rule, size: 13, subSize: 9.5 });
  arrow(s, 4.25, 5.5, 4.25, 4.7, { color: C.ok });
  arrow(s, 7.05, 5.5, 7.05, 4.7, { color: C.rule, dash: true });
  txt(s, "exactly ONE writes", { x: 8.9, y: 5.6, w: 3.6, h: 0.4, fontSize: 13, color: C.ink, bold: true });

  note(s, "Arrows point the way data is read. Only the active transactor writes.", 6.65);
}

// Failure table as cards
{
  const s = slide("PART I · THE FAILURE MAP", "What each failure actually costs you",
    "Read the four cards left to right, worst first.\n\nStorage: a total outage, and reads go down too — but only once the caches miss. A peer with a warm object cache keeps answering queries it can answer from cache for as long as its working set holds.\n\nTransactor: writes fail, reads continue. This is the failure people expect to be fatal and it is not.\n\nOne peer: that peer's traffic only. Siblings are unaffected because peers share nothing but storage.\n\nMemcached: nothing fails. Storage load goes up, latency goes up, correctness does not move.\n\nThe blast radius of a component is the blast radius of the state only it holds. Storage holds the only copy, so losing it loses everything. The transactor holds the right to write, which another process can take. A peer holds a cache, which rebuilds. Memcached holds a copy of a cache, which also rebuilds.\n\nStorage is the single component here whose state exists nowhere else, and that is exactly the one card marked as an outage.");

  const cards = [
    ["STORAGE", "total outage", "reads too, once the caches miss", C.badBg, C.bad, 4],
    ["TRANSACTOR", "writes fail", "reads continue, unaffected", C.warnBg, C.warn, 2],
    ["ONE PEER", "that peer only", "siblings unaffected", C.okBg, C.ok, 1],
    ["MEMCACHED", "nothing fails", "storage load increases", C.okBg, C.ok, 1],
  ];
  cards.forEach((c, i) => {
    const x = 0.6 + i * 3.1;
    bar(s, x, 1.45, 2.9, 3.5, c[3], { round: true });
    bar(s, x, 1.45, 2.9, 0.12, c[4], { round: true });
    txt(s, c[0], { x: x + 0.25, y: 1.75, w: 2.4, h: 0.35, fontSize: 13, color: c[4], bold: true, charSpacing: 1.5 });
    txt(s, c[1], { x: x + 0.25, y: 2.25, w: 2.4, h: 0.9, fontFace: F.serif, fontSize: 21, color: C.ink, fit: "shrink" });
    txt(s, c[2], { x: x + 0.25, y: 3.25, w: 2.4, h: 0.9, fontSize: 13, color: C.body });
    // severity meter
    for (let k = 0; k < 4; k++) {
      bar(s, x + 0.25 + k * 0.42, 4.45, 0.34, 0.16, k < c[5] ? c[4] : C.panel2);
    }
    txt(s, "blast radius", { x: x + 0.25, y: 4.65, w: 2.4, h: 0.25, fontSize: 10, color: C.meta });
  });

  takeaway(s, "Three of the four are not outages — that is the structural difference from a single-server SQL deployment.", 5.35);
  waypoint(s, "waypoint — labs §1", "MEM",
    "(d/basis-t (d/db conn))  ·  (d/q all-readings (d/db conn))  ·  (:datoms (d/db-stats (d/db conn)))\nA peer's position in time, its data, and its size — the three things you check first on any peer.",
    { y: 6.05, h: 1.15 });
}

// SQL contrast
{
  const s = slide("PART I · THE FAILURE MAP", "Why that is different from one SQL server",
    "On the left, one process is the query engine, the writer, the cache and the durability boundary at once, so every failure is the same failure. On the right, those four responsibilities live in different processes and fail separately.\n\nThe rest of the class follows from the split: Part II protects the write path only, Part III accelerates the read path only, and Part IV finds that the two words for 'parallelism' mean different mechanisms on each side.\n\nThe practical form of this is the on-call runbook. A single SQL server has one entry: the server is down, everything is down. Here there are four entries and three of them are degradations rather than outages — which means three of them can wait until business hours.");

  panel(s, 0.6, 1.45, 5.85, 4.0, "ONE SQL SERVER", { strip: C.badBg, titleColor: C.bad });
  box(s, 1.6, 2.2, 3.85, 2.6, { label: "the server", sub: "query engine + writer + cache + durability", fill: C.badBg, border: C.bad, size: 16, subSize: 11 });
  txt(s, "one process, four responsibilities\n→ one failure mode", { x: 1.6, y: 4.95, w: 3.85, h: 0.5, fontSize: 13, color: C.bad, align: "center" });

  panel(s, 6.85, 1.45, 5.85, 4.0, "DATOMIC", { strip: C.okBg, titleColor: C.ok });
  const parts = [
    ["query engine", "peer — local", C.coolBg, C.cool],
    ["writer", "transactor — HA", C.okBg, C.ok],
    ["cache", "4 tiers — Part III", C.panel, C.rule],
    ["durability", "storage — replicate it", C.panel2, C.meta],
  ];
  parts.forEach((p, i) => {
    box(s, 7.15, 2.1 + i * 0.72, 5.25, 0.6, {
      label: p[0], fill: p[2], border: p[3], size: 13, align: "left",
    });
    txt(s, p[1], { x: 9.6, y: 2.1 + i * 0.72, w: 2.65, h: 0.6, fontSize: 11.5, color: C.body, align: "right", valign: "middle" });
  });
  txt(s, "four processes, four failure modes\n→ three of them survivable", { x: 7.15, y: 4.95, w: 5.25, h: 0.5, fontSize: 13, color: C.ok, align: "center" });

  takeaway(s, "Separate processes fail separately. That is the whole operational thesis of this class.", 5.75);
}

// The asymmetry
{
  const s = slide("PART I · THE FAILURE MAP", "The asymmetry: reads fan out, writes funnel in",
    "Reads are served by the peer itself, from caches and from storage. They are parallel and independent of the transactor — a peer whose transactor is dead still answers queries.\n\nWrites are serialised through one process. That is what buys you ACID transactions with no coordination protocol, and it is the reason the write path needs HA while the read path needs caching.\n\nHold on to this diagram: Part II is the left arrow, Part III the right one, and Part IV shows that 'parallelism' names a different mechanism on each side.\n\nWhy writes funnel, stated precisely: transaction functions, uniqueness and cardinality checks all run against the current value of the database, so the transactor has to know the exact predecessor of every transaction. That is a serial dependency in the semantics, not an implementation limit. What it buys is transactions with no two-phase commit, no consensus round and no lock manager, because there is nothing to coordinate. What it costs is a throughput ceiling and a component that needs HA.\n\nWhy reads fan out: a database value is immutable, so any number of readers can hold any number of points in time with no coordination. Nothing one reader does can change what another sees.");

  // reads
  panel(s, 0.6, 1.45, 5.85, 4.3, "READS  —  fan out");
  box(s, 2.55, 2.05, 1.95, 0.62, { label: "clients", fill: C.panel2, size: 13 });
  [0, 1, 2].forEach(i => {
    const x = 0.95 + i * 1.85;
    arrow(s, 3.5, 2.67, x + 0.75, 3.2, { color: C.cool });
    box(s, x, 3.2, 1.5, 0.62, { label: "peer", fill: C.coolBg, border: C.cool, size: 12 });
    arrow(s, x + 0.75, 3.82, x + 0.75, 4.45, { color: C.cool, dash: true });
  });
  box(s, 0.95, 4.45, 5.15, 0.6, { label: "caches, then storage", fill: C.dark, labelColor: C.white, size: 12 });
  txt(s, "parallel · independent of the transactor", { x: 0.95, y: 5.15, w: 5.15, h: 0.4, fontSize: 12.5, color: C.cool, align: "center", bold: true });

  // writes
  panel(s, 6.85, 1.45, 5.85, 4.3, "WRITES  —  funnel in");
  [0, 1, 2].forEach(i => {
    const x = 7.2 + i * 1.85;
    box(s, x, 2.05, 1.5, 0.62, { label: "peer", fill: C.panel, border: C.rule, size: 12 });
    arrow(s, x + 0.75, 2.67, 9.78, 3.2, { color: C.warn });
  });
  box(s, 8.5, 3.2, 2.55, 0.62, { label: "ONE transactor", fill: C.warnBg, border: C.warn, size: 13 });
  arrow(s, 9.78, 3.82, 9.78, 4.45, { color: C.warn });
  box(s, 7.2, 4.45, 5.15, 0.6, { label: "log, then indexes", fill: C.dark, labelColor: C.white, size: 12 });
  txt(s, "serialised · ordered · transactional", { x: 7.2, y: 5.15, w: 5.15, h: 0.4, fontSize: 12.5, color: C.warn, align: "center", bold: true });

  takeaway(s, "HA protects the write path. Caching accelerates the read path. Neither one helps the other.", 6.0);
}

// db-stats clarification
{
  const s = slide("PART I · THE FAILURE MAP", "One clarification before anyone tunes anything",
    "People reach for d/db-stats expecting a cache report. It is a database report: how many datoms exist, by attribute. It says nothing about what is resident in any cache.\n\nThere is no peer API that reports cache occupancy. Cache behaviour is observed one of two ways: through the metrics callback (Part V), or by timing the same query twice and comparing. Both appear later in this class.\n\nThe failure signature this prevents: we added a cache tier and db-stats did not change. It would not. db-stats counts datoms, and the datom count does not depend on caching. The measurements that would show the change are query timing and the memcached hit ratio in Part V.");

  panel(s, 0.6, 1.45, 5.85, 2.6, "WHAT d/db-stats TELLS YOU");
  codeBlock(s, "(:datoms (d/db-stats (d/db conn)))\n;; => 20301", 0.85, 2.05, 5.35, 1.0);
  txt(s, "The size of the DATABASE — how many\ndatoms exist, and by which attribute.",
    { x: 0.85, y: 3.2, w: 5.35, h: 0.7, fontSize: 13.5, color: C.body });

  panel(s, 6.85, 1.45, 5.85, 2.6, "WHAT NO API TELLS YOU", { strip: C.badBg, titleColor: C.bad });
  box(s, 7.1, 2.05, 5.35, 1.0, { label: "cache occupancy", sub: "not reported by any peer API", fill: C.badBg, border: C.bad, size: 15, subSize: 11.5, subColor: C.bad });
  txt(s, "How much of the working set is resident,\nand in which tier.",
    { x: 7.1, y: 3.2, w: 5.35, h: 0.7, fontSize: 13.5, color: C.body });

  txt(s, "So cache behaviour is OBSERVED, not queried:", { x: 0.6, y: 4.35, w: 12.1, h: 0.4, fontSize: 14, color: C.ink, bold: true });
  box(s, 0.6, 4.85, 5.85, 0.85, { label: "metrics callback  →  Part V", fill: C.panel, border: C.rule, size: 14 });
  box(s, 6.85, 4.85, 5.85, 0.85, { label: "time the same query twice", fill: C.panel, border: C.rule, size: 14 });

  takeaway(s, "d/db-stats measures the database. Timing and metrics measure the cache.", 6.1);
}

// ═══════════════════════════════════════════════════════════════════
// PART II · HIGH AVAILABILITY
// ═══════════════════════════════════════════════════════════════════

sectionSlide("II", "High availability", "Two transactors · one lease · storage as the arbiter",
  "The whole of Datomic HA is one idea: the right to write is a lease, and the lease lives in storage. Everything else — no quorum, no consensus protocol, no split brain, no virtual IP, no failover controller — is a consequence of that one decision.\n\nA lease is a claim on storage with an expiry. The holder renews it; when renewal stops, the claim expires and another process may take it. The correctness argument is one sentence: only storage grants the lease, and storage does not disagree with itself, so two processes cannot both hold it. No quorum is needed because there is no distributed decision to make.");

// The lease
{
  const s = slide("PART II · HIGH AVAILABILITY", "Datomic HA is a lease, not a cluster",
    "Two identical transactor processes point at the same storage. One of them holds a lease; the other polls and waits.\n\nStorage is the arbiter. That matters because the lease lives where the data lives — so 'who is the writer' and 'what is committed' are decided by the same system and cannot disagree. A split brain would require storage to disagree with itself.\n\nThere is no separate failover controller, no virtual IP, and nothing for you to operate between the two processes.\n\nOperational note: a standby that is working correctly prints nothing after startup. A silent standby log is the expected state, not a symptom.\n\nThe mechanism in full: the active transactor refreshes a heartbeat in storage on an interval, and the standby polls the same location. When the heartbeat goes stale for long enough, the standby takes the lease and begins writing. That is the entire protocol.\n\nWhy storage rather than a consensus protocol: every transaction already funnels through storage, so storage is already the serialisation point. Making it the arbiter adds no new dependency and no new failure mode.\n\nFailure signature: two transactors that never fail over are usually pointed at different storage. The identical sql-url in the standby properties file is not a copy-paste convenience — it is the mechanism.");

  box(s, 1.0, 1.8, 2.6, 0.9, { label: "transactor A", sub: "holds the lease", fill: C.okBg, border: C.ok, size: 14, subSize: 11, subColor: C.ok });
  box(s, 1.0, 4.3, 2.6, 0.9, { label: "transactor B", sub: "waits", fill: C.panel, border: C.rule, size: 14, subSize: 11 });

  box(s, 5.1, 2.7, 3.2, 1.6, { label: "STORAGE", sub: "the lease lives here", fill: C.dark, labelColor: C.white, size: 18, subSize: 12, subColor: C.goldHi });

  arrow(s, 3.6, 2.25, 5.1, 3.1, { color: C.ok, label: "writes", labelDy: -0.22 });
  arrow(s, 3.6, 4.75, 5.1, 3.95, { color: C.rule, dash: true, label: "polls", labelDy: 0.22 });

  [0, 1, 2].forEach(i => {
    box(s, 10.0, 1.8 + i * 0.75, 1.6, 0.6, { label: "peer", fill: C.coolBg, border: C.cool, size: 12 });
    arrow(s, 10.0, 2.1 + i * 0.75, 8.3, 3.2, { color: C.cool, head: false });
  });
  txt(s, "peers find the active\ntransactor HERE", { x: 8.7, y: 4.45, w: 4.0, h: 0.7, fontSize: 13, color: C.cool, bold: true, align: "center" });

  const nots = ["no quorum", "no consensus protocol", "no split brain", "no virtual IP"];
  nots.forEach((n, i) => {
    bar(s, 0.6 + i * 3.1, 5.55, 2.9, 0.5, C.panel2, { round: true });
    txt(s, "✕  " + n, { x: 0.6 + i * 3.1, y: 5.55, w: 2.9, h: 0.5, fontSize: 13, color: C.meta, align: "center", valign: "middle" });
  });

  takeaway(s, "The lease lives where the data lives, so the writer and the commit log cannot disagree.", 6.25);
}

// Failover timeline
{
  const s = slide("PART II · HIGH AVAILABILITY", "What a failover looks like from outside",
    "Two tracks, same wall clock.\n\nThe write track has a gap: from the moment A dies to the moment B has the lease, transactions fail. The read track has no gap at all — peers are reading storage and their own caches, and neither one noticed.\n\nThe peer reconnects on its own. No URI change, no restart, no load balancer entry to flip.\n\nThe width of that window depends on heartbeat-interval-msec, on storage latency, and on how warm the standby's JVM is. That is three environment-specific variables, so the width is a measured number from a given environment rather than a quoted constant.\n\nWhy the read track has no gap: peers never asked the transactor anything to begin with. They read storage and their own caches, and they notice the transactor only when they submit a transaction.\n\nWhat a caller sees during the window depends on which side it is on. A writer fails or hangs, decided by txTimeoutMsec two slides on. A reader gets the last t the peer knows about, which is a real and consistent database value — an answer to a slightly older question, not an error.");

  // write track
  txt(s, "WRITES", { x: 0.6, y: 1.85, w: 1.4, h: 0.4, fontSize: 13, color: C.ink, bold: true, charSpacing: 1 });
  const tx = 2.2, tw = 10.2;
  for (let i = 0; i < 20; i++) {
    const failing = i >= 7 && i <= 11;
    bar(s, tx + i * (tw / 20), 1.80, tw / 20 - 0.06, 0.5, failing ? C.bad : C.ok, { round: true });
  }
  bar(s, tx + 7 * (tw / 20), 2.40, 5 * (tw / 20) - 0.06, 0.1, C.bad);
  txt(s, "the window — writes fail", {
    x: tx + 6.4 * (tw / 20), y: 2.55, w: 3.4, h: 0.35, fontSize: 12.5, color: C.bad, align: "center", bold: true,
  });
  txt(s, "A dies", { x: tx + 5.6 * (tw / 20), y: 1.45, w: 1.6, h: 0.35, fontSize: 12, color: C.bad, align: "center" });
  txt(s, "B has the lease", { x: tx + 10.4 * (tw / 20), y: 1.45, w: 2.4, h: 0.35, fontSize: 12, color: C.ok, align: "center" });

  // read track
  txt(s, "READS", { x: 0.6, y: 3.05, w: 1.4, h: 0.4, fontSize: 13, color: C.ink, bold: true, charSpacing: 1 });
  for (let i = 0; i < 20; i++) {
    bar(s, tx + i * (tw / 20), 3.0, tw / 20 - 0.06, 0.5, C.cool, { round: true });
  }
  txt(s, "uninterrupted — peers never asked the transactor anything", {
    x: tx, y: 3.6, w: tw, h: 0.35, fontSize: 12.5, color: C.cool,
  });

  txt(s, "The peer reconnects by itself:", { x: 0.6, y: 4.15, w: 12.1, h: 0.35, fontSize: 14, color: C.ink, bold: true });
  ["no URI change", "no peer restart", "no load balancer"].forEach((t, i) => {
    box(s, 0.6 + i * 4.05, 4.55, 3.85, 0.55, { label: "✓  " + t, fill: C.okBg, border: C.ok, size: 13, labelColor: C.ok });
  });

  waypoint(s, "waypoint — labs §2", "PRO",
    "Start (writer-loop!), then  pkill -f pg-transactor.properties, then read (failover-report @timeline).\nThat number is the write-availability SLO for this environment.",
    { y: 5.35, h: 1.2 });
  note(s, "The window depends on heartbeat-interval-msec, storage latency, and standby JVM warmth. Measure it where you run it.", 6.7);
}

// What a standby covers
{
  const s = slide("PART II · HIGH AVAILABILITY", "What a standby covers — and what it does not",
    "The left column is what you get. The right column is what people assume they got and did not.\n\nA standby is not a second copy of the data: both transactors write to the same storage, so storage loss loses everything either of them wrote. Data redundancy is storage replication's job — Postgres streaming replication, DynamoDB's own durability, whatever your storage offers.\n\nA standby also does not protect you from a bad transaction. Once it is committed, it is committed; recovery is restore, covered in the Production class §4.\n\nThe rolling-upgrade line on the left is the one people undersell: the same mechanism that survives a crash lets you upgrade the transactor on purpose for the cost of one bounded write pause.\n\nWorth being precise about three words that get used interchangeably. Availability is what HA buys — someone can still write. Durability is what storage replication buys — the bytes survive. Recoverability is what backup buys — you can go back to a state before a mistake. Three mechanisms, three different failures.\n\nFailure signature: a deployment with a standby transactor and unreplicated storage has bought a shorter write pause and no protection whatsoever against losing the database.");

  panel(s, 0.6, 1.45, 5.85, 3.6, "COVERS", { strip: C.okBg, titleColor: C.ok });
  [["a bounded write pause", "measured, not unbounded"],
   ["unattended recovery", "no operator in the loop"],
   ["rolling transactor upgrades", "on purpose, same mechanism"]].forEach((r, i) => {
    box(s, 0.85, 2.05 + i * 0.95, 5.35, 0.8, { label: "✓  " + r[0], sub: r[1], fill: C.okBg, border: C.ok, size: 14, subSize: 11, labelColor: C.ink, align: "left" });
  });

  panel(s, 6.85, 1.45, 5.85, 3.6, "DOES NOT COVER", { strip: C.badBg, titleColor: C.bad });
  [["a second copy of the data", "both write the same storage"],
   ["storage loss", "storage replication's job"],
   ["a bad transaction", "restore — Production class §4"]].forEach((r, i) => {
    box(s, 7.1, 2.05 + i * 0.95, 5.35, 0.8, { label: "✕  " + r[0], sub: r[1], fill: C.badBg, border: C.bad, size: 14, subSize: 11, labelColor: C.ink, align: "left" });
  });

  takeaway(s, "HA buys you a bounded pause. Durability is bought separately, from your storage.", 5.35);
  note(s, "Both transactors write the same storage — that is the point of the lease, and the limit of the guarantee.", 6.15);
}

// txTimeoutMsec dial
{
  const s = slide("PART II · HIGH AVAILABILITY", "The peer-side dial that shapes the pause",
    "During the failover window a write has to do something. datomic.txTimeoutMsec decides what: how long a peer's write waits for a writer to exist before giving up.\n\nThere is no correct value, only a choice about which failure your application handles better. A short timeout gives you fast, explicit failures and free threads — your callers see errors during the window. A long timeout gives you writes that survive the window — at the cost of threads parked in the peer, which under load is its own outage.\n\nPick the end of the dial your caller can actually handle, then measure the window (previous waypoint) and make sure a long timeout is actually longer than it.\n\nMechanism: the flag bounds how long a peer blocks in d/transact when no transactor holds the lease. It does not retry faster or make failover happen sooner. It only decides when the peer gives up.\n\nFailure signature for a value set too long: during a failover the peer's threads fill with parked writers, the pool exhausts, and the process stops serving reads as well. A bounded write-path pause has become a read-path outage. That is the failure people do not anticipate, and the reason a long timeout needs a bounded pool behind it.");

  codeBlock(s, "-Ddatomic.txTimeoutMsec=<ms>       ;; how long a write waits for a writer to exist", 0.6, 1.45, 12.1, 0.62);

  // the dial
  bar(s, 2.0, 2.9, 9.3, 0.16, C.panel2);
  arrow(s, 2.0, 2.98, 11.3, 2.98, { color: C.rule, head: false });
  box(s, 1.3, 2.5, 1.4, 0.95, { label: "SHORT", fill: C.warnBg, border: C.warn, size: 13 });
  box(s, 10.6, 2.5, 1.4, 0.95, { label: "LONG", fill: C.coolBg, border: C.cool, size: 13 });

  panel(s, 1.3, 3.75, 5.1, 2.2, "IF SHORT", { strip: C.warnBg, titleColor: C.warn });
  bullets(s, ["more failed writes, visible to the caller", "fewer stalled peer threads", "the window becomes an error rate"],
    { x: 1.55, y: 4.3, w: 4.6, h: 1.5, size: 13 });

  panel(s, 6.9, 3.75, 5.1, 2.2, "IF LONG", { strip: C.coolBg, titleColor: C.cool });
  bullets(s, ["writes survive the window", "threads park in the peer", "the window becomes a latency spike"],
    { x: 7.15, y: 4.3, w: 4.6, h: 1.5, size: 13 });

  takeaway(s, "Tuning this is choosing which failure your callers handle better — not finding a right value.", 6.2);
}

// ═══════════════════════════════════════════════════════════════════
// PART III · THE COST OF A READ
// ═══════════════════════════════════════════════════════════════════

sectionSlide("III", "The cost of a read", "Four tiers · the segment · and the cold peer after a deploy",
  "Every read walks down a ladder until some tier answers. Knowing which tier answered is the whole skill: the same query is a hundred nanoseconds or ten milliseconds depending on nothing but residency.\n\nOne thing to establish before the ladder: there is no query cache and no result cache anywhere in Datomic. The only thing cached is the segment, a block of the index. So caching is entirely a question of which blocks are resident, and never a question of which queries were asked before.");

// Four tiers
{
  const s = slide("PART III · THE COST OF A READ", "Four tiers — a read walks down until one answers",
    "Read the ladder top to bottom, and read the right-hand column as orders of magnitude rather than as benchmark numbers.\n\nTier 1, the object cache, is on-heap and holds decoded segments — nothing to deserialise, which is why it is nanoseconds to microseconds.\n\nTier 2, valcache, is the addition in this class: local SSD, per peer. Its latency is not the interesting part. The interesting part is that it is the only tier that survives a process restart, which is exactly the event that empties the others.\n\nTier 3, memcached, is shared over the network. Its value is not its speed but its sharing: twenty cold peers cause one storage miss instead of twenty.\n\nTier 4 is storage, and storage is metered — in dollars on DynamoDB, in connections and IOPS on Postgres.\n\nWhy a ladder rather than one cache: each tier trades latency for a property the tier above lacks. Tier 1 is the fastest and dies with the process. Tier 2 is slower and survives a restart. Tier 3 is slower still and is shared between processes. Storage is the slowest and is the only one that is authoritative.\n\nThe reason the ladder needs no coherence protocol: segments are immutable and content-addressed. A segment that is present is correct, at every tier, forever. Nothing is ever invalidated, and no tier can serve a stale answer. That is what makes it safe to stack four independent caches with no protocol between them.");

  const tiers = [
    ["1", "object cache", "on-heap, decoded segments", "ns … µs", C.dark, C.white, 0.9],
    ["2", "valcache", "local SSD, per peer", "~100s of µs", C.gold, C.white, 1.0],
    ["3", "memcached", "shared, over the network", "~1 ms", C.panel2, C.ink, 1.0],
    ["4", "storage", "Postgres / DynamoDB / S3", "ms, and metered", C.panel, C.ink, 1.0],
  ];
  let y = 1.5;
  tiers.forEach((t, i) => {
    const h = 0.85;
    const indent = i * 0.35;
    bar(s, 1.5 + indent, y, 8.2 - indent, h, t[4], { round: true });
    txt(s, t[0], { x: 1.75 + indent, y, w: 0.5, h, fontSize: 20, fontFace: F.serif, color: t[5], valign: "middle" });
    txt(s, t[1], { x: 2.35 + indent, y, w: 2.9, h, fontSize: 16, color: t[5], bold: true, valign: "middle", fit: "shrink" });
    txt(s, t[2], { x: 5.3, y, w: 4.3, h, fontSize: 12.5, color: t[5] === C.white ? C.rule : C.body, valign: "middle", fit: "shrink" });
    txt(s, t[3], { x: 10.0, y, w: 2.7, h, fontFace: F.mono, fontSize: 14, color: C.ink, valign: "middle", align: "right" });
    if (i < 3) arrow(s, 1.15, y + h, 1.15, y + h + 0.2, { color: C.gold, width: 1.2 });
    y += h + 0.2;
  });
  txt(s, "miss\n↓", { x: 0.55, y: 3.0, w: 0.6, h: 0.7, fontSize: 11, color: C.gold, align: "center" });

  bar(s, 1.85, 5.3, 8.5, 0.55, C.coolBg, { round: true });
  txt(s, "Tier 2 is the addition in this class — and it is the only tier that survives a process restart.",
    { x: 2.05, y: 5.3, w: 8.1, h: 0.55, fontSize: 13, color: C.cool, valign: "middle", bold: true, fit: "shrink" });

  takeaway(s, "Latency is not a property of the query. It is a property of which tier had the segment.", 6.15);
}

// Tier properties
{
  const s = slide("PART III · THE COST OF A READ", "Choosing tiers: what each one actually buys",
    "Use this as the sizing conversation. Four columns, four different reasons to add a tier.\n\nThe object cache is the only one that is always there, and it is the only one that is free. It is also the one most often left at its default.\n\nValcache buys restart survival. Memcached buys sharing across peers. Storage is the tier you are trying not to reach.\n\nNote the last row: only memcached is shared. That is the whole reason it exists, and the reason its hit ratio is a signal worth alerting on in Part V.\n\nSizing order follows what each tier costs. Object cache first, because it costs heap you have already allocated and is the only free tier. Then choose between the two paid tiers by the failure being addressed: frequent restarts point at valcache, a large fleet points at memcached.\n\nFailure signature for an undersized object cache: latency that is bimodal rather than uniformly high — most calls fast, a minority slow, with the proportion moving as the working set shifts. A single average hides it completely.");

  const cols = ["object cache", "valcache", "memcached", "storage"];
  const rows = [
    ["where",              ["JVM heap", "local SSD", "network", "the database"]],
    ["survives restart?",  ["no", "YES", "yes (shared)", "n/a"]],
    ["shared across peers?", ["no", "no", "YES", "yes"]],
    ["costs",              ["heap", "disk", "a server", "money / IOPS"]],
    ["turned on by",       ["objectCacheMax", "valcachePath", "memcachedServers", "always on"]],
  ];
  const x0 = 3.4, colW = 2.35;
  cols.forEach((c, i) => {
    bar(s, x0 + i * colW, 1.45, colW - 0.12, 0.55, i === 0 ? C.dark : C.panel2, { round: true });
    txt(s, c, { x: x0 + i * colW, y: 1.45, w: colW - 0.12, h: 0.55, fontSize: 12.5, bold: true,
      color: i === 0 ? C.white : C.ink, align: "center", valign: "middle", fit: "shrink" });
  });
  rows.forEach((r, ri) => {
    const y = 2.15 + ri * 0.72;
    bar(s, 0.6, y, 12.1, 0.62, ri % 2 ? C.bg : C.panel);
    txt(s, r[0], { x: 0.8, y, w: 2.5, h: 0.62, fontSize: 13, color: C.ink, bold: true, valign: "middle", fit: "shrink" });
    r[1].forEach((v, ci) => {
      const hot = v === "YES";
      txt(s, v, {
        x: x0 + ci * colW, y, w: colW - 0.12, h: 0.62,
        fontSize: ri === 4 ? 11 : 13, fontFace: ri === 4 ? F.mono : F.sans,
        color: hot ? C.ok : C.body, bold: hot, align: "center", valign: "middle", fit: "shrink",
      });
    });
  });

  takeaway(s, "Size the object cache first — it is free. Add valcache for restarts, memcached for fleets.", 6.05);
}

// The segment
{
  const s = slide("PART III · THE COST OF A READ", "The unit of caching is a segment — not an entity, not a row",
    "You ask for one entity. The peer does not fetch one entity: it fetches the segment that entity's datoms live in, and neighbours in the index come along for free. The index-range call on the next line proves it — twenty readings, one segment, one storage hit.\n\nThe consequence for sizing is direct: the working set is measured in segments, not in entities. A workload that touches a thousand entities scattered across the index is expensive; a workload that touches ten thousand adjacent ones may be cheap. Cache sizing follows the segments a workload touches rather than the entities it names.\n\nIt also explains why sorted access patterns — index-range, reverse chronological scans — behave differently from random entity lookup.\n\nThe mechanism underneath: an index is a sorted tree whose leaves are segments holding many datoms. The peer's unit of transfer and of caching is the leaf. Nothing in the system can fetch a single datom.\n\nSo access-pattern shape matters more than data volume. A query touching a thousand entities adjacent in the index may load a handful of segments; the same thousand entities scattered across it may load a thousand. Same result, same datom count, an order of magnitude apart in storage traffic.\n\nFailure signature: adding a cache tier changed nothing, and the access pattern is random along a dimension nothing is indexed by. Cache tiers do not fix a scattered access pattern. Changing what is indexed does.");

  txt(s, "You ask for one entity …", { x: 0.6, y: 1.4, w: 5.8, h: 0.35, fontSize: 14, color: C.ink, bold: true });
  codeBlock(s, "(d/pull db '[*] some-e)", 0.6, 1.8, 5.8, 0.6);

  txt(s, "… the peer fetches the segment it lives in.", { x: 6.9, y: 1.4, w: 5.8, h: 0.35, fontSize: 14, color: C.ink, bold: true });
  codeBlock(s, "(count (seq (d/index-range db\n  :reading/t 4240 4260)))   ;; => 20", 6.9, 1.8, 5.8, 0.85);

  // index strip
  txt(s, "AVET index, ordered by :reading/t", { x: 0.6, y: 3.0, w: 6.0, h: 0.3, fontSize: 12, color: C.meta });
  const segs = 5, sw = 2.34;
  for (let i = 0; i < segs; i++) {
    const x = 0.6 + i * sw;
    const hot = i === 2;
    bar(s, x, 3.35, sw - 0.12, 1.15, hot ? C.goldHi : C.panel, { round: true });
    txt(s, "segment " + (i + 1), { x, y: 3.42, w: sw - 0.12, h: 0.3, fontSize: 11, color: hot ? C.dark : C.meta, align: "center" });
    for (let k = 0; k < 5; k++) {
      bar(s, x + 0.18 + k * 0.4, 3.85, 0.3, 0.45, hot ? C.dark : C.panel2);
    }
  }
  arrow(s, 3.1, 2.55, 3.1, 3.3, { color: C.gold, label: "asked for one datom", labelW: 2.6, labelDx: -1.5 });
  bar(s, 5.28, 4.6, 2.22, 0.1, C.gold);
  txt(s, "one segment loaded — 20 neighbours came along free", { x: 4.3, y: 4.75, w: 6.0, h: 0.35, fontSize: 13, color: C.gold, bold: true, align: "center" });

  takeaway(s, "Working sets are measured in segments. Size caches for the segments a workload touches, not the entities it names.", 5.25);
  waypoint(s, "waypoint — labs §3", "MEM",
    "The segment demo needs no infrastructure — segmentation is a property of the index, not of the cache.\nRun it before adding any tier — a scattered access pattern is not fixed by a cache tier.",
    { y: 5.95, h: 1.2 });
}

// Cold peers
{
  const s = slide("PART III · THE COST OF A READ", "The cold-peer thundering herd, right after a deploy",
    "Twenty peers restart together. Twenty object caches are empty at the same instant. Twenty peers ask storage the same questions in the same second.\n\nOn Postgres you watch it in pg_stat_database: blks_hit collapses and blks_read spikes. On DynamoDB you watch it on the bill, or in throttling.\n\nNothing is broken. Every peer is correct, every query returns, and p99 goes through the roof — which is why this shows up as a mysterious post-deploy latency incident rather than as an error.\n\nThe three mitigations are ordered by what they require. Rolling deploys require no infrastructure at all.\n\nWhy this is a herd rather than N independent slow starts: the peers are identical, restarted together, serving the same traffic, so they miss on the same segments in the same order at the same moment. Storage sees N times its normal miss rate for the length of the warm-up, which is precisely when its own latency is worst. The peers make each other slower.\n\nFailure signature: latency spikes correlated with deploys, no errors anywhere, and the problem resolves by itself in a few minutes. It is routinely diagnosed as a bad release and rolled back, which restarts every peer again.");

  txt(s, "BEFORE DEPLOY", { x: 1.2, y: 1.45, w: 5.0, h: 0.35, fontSize: 13, color: C.ok, bold: true, charSpacing: 1 });
  barCompare(s, 1.2, 1.9, 5.0, [
    ["blks_hit",  90, "warm", C.ok],
    ["blks_read", 10, "few",  C.panel2],
  ], { labelW: 1.5, rowH: 0.55 });

  txt(s, "AFTER DEPLOY", { x: 7.2, y: 1.45, w: 5.0, h: 0.35, fontSize: 13, color: C.bad, bold: true, charSpacing: 1 });
  barCompare(s, 7.2, 1.9, 5.0, [
    ["blks_hit",  12, "cold",  C.panel2],
    ["blks_read", 100, "spike", C.bad],
  ], { labelW: 1.5, rowH: 0.55 });

  fatArrow(s, 6.3, 2.1, 0.75, 0.7, pres.shapes.RIGHT_ARROW, { fill: C.rule });
  txt(s, "every peer, cold, simultaneously — nothing is broken, and p99 triples",
    { x: 0.6, y: 3.15, w: 12.1, h: 0.35, fontSize: 13.5, color: C.ink, align: "center", italic: true });

  const mits = [
    ["memcached", "one shared miss instead of N", "needs a server", C.coolBg, C.cool],
    ["valcache", "survives the restart that caused it", "needs a disk", C.warnBg, C.warn],
    ["rolling deploys", "don't replace 20 peers at once", "needs nothing", C.okBg, C.ok],
  ];
  mits.forEach((m, i) => {
    const x = 0.6 + i * 4.07;
    bar(s, x, 3.65, 3.87, 1.55, m[3], { round: true });
    txt(s, String(i + 1), { x: x + 0.2, y: 3.78, w: 0.4, h: 0.35, fontFace: F.serif, fontSize: 17, color: m[4], bold: true });
    txt(s, m[0], { x: x + 0.7, y: 3.8, w: 3.0, h: 0.35, fontSize: 15, color: C.ink, bold: true });
    txt(s, m[1], { x: x + 0.7, y: 4.2, w: 3.0, h: 0.6, fontSize: 12.5, color: C.body });
    txt(s, "(" + m[2] + ")", { x: x + 0.7, y: 4.85, w: 3.0, h: 0.3, fontSize: 11.5, color: m[4], italic: true });
  });

  waypoint(s, "waypoint — labs §3", "PRO",
    "Add valcache with two -D flags, restart the REPL, re-run the cold query. Then set datomic.objectCacheMax=32m\nand re-run the warm one. The gap between those two measurements is the object-cache sizing signal for this dataset.",
    { y: 5.4, h: 1.25 });
}

// Break
{
  const s = pres.addSlide();
  bar(s, 0, 0, W, H, C.dark);
  page += 1;
  txt(s, "☕", { x: 0.9, y: 2.2, w: 2, h: 1.0, fontSize: 54, color: C.goldHi });
  txt(s, "Break — 10 minutes", { x: 0.9, y: 3.3, w: 11.5, h: 0.9, fontFace: F.serif, fontSize: 36, color: C.white });
  txt(s, "Next: which half of the system scales, and which one does not.",
    { x: 0.9, y: 4.3, w: 11.5, h: 0.5, fontSize: 17, color: C.rule });
  txt(s, String(page), { x: 12.2, y: 7.0, w: 0.7, h: 0.28, fontSize: 11, color: C.meta, align: "right" });
  s.addNotes("Halfway marker. Parts I–III were the model: what fails, who takes over, what a read costs. Parts IV–VI are what you do with it: scale it, tune it, ship it.");
}

// ═══════════════════════════════════════════════════════════════════
// PART IV · PARALLELISM
// ═══════════════════════════════════════════════════════════════════

sectionSlide("IV", "Parallelism", "One writer, many readers — two different mechanisms, one word",
  "Both halves of the system are described as parallel, and the word means something different on each side. On the write side it means pipelining: keeping one serial writer busy. On the read side it means actual concurrency: many cores over one immutable value.\n\nKeeping the two apart matters because they have different limits and different levers. Pipelining adds no concurrency at all — it removes idleness from a serial resource, and it is bounded by the round trip it hides. Read parallelism is ordinary concurrency, and it is bounded by cores and by cache misses. Conflating them leads to looking for threads where there are none.");

// One writer many readers
{
  const s = slide("PART IV · PARALLELISM", "One word, two mechanisms",
    "Writes cannot be parallelised. One transactor, one serial order — that is what makes the transaction guarantees cheap. What writes can be is pipelined: send the next transaction before the previous one's result has come back, so the transactor never sits idle waiting on a round trip.\n\nReads are parallel in the ordinary sense. Every peer, every core, over an immutable database value. Once the segments are warm those reads cost storage nothing.\n\nThe levers therefore differ. On the left you are fighting idleness. On the right you are cutting work along the index. The two are separate mechanisms; only the read side involves concurrency.\n\nWhy writes cannot be parallelised is a statement about semantics, not implementation: transaction functions and the uniqueness and cardinality checks all evaluate against the current value of the database, so every transaction has to know its exact predecessor. Two writers would have to agree on that order, which is the coordination the single writer exists to avoid.\n\nFailure signature: a write-throughput problem attacked by adding transactors. There is only ever one writer. The second process is idle by design and will stay idle.");

  panel(s, 0.6, 1.45, 5.85, 4.4, "WRITES", { strip: C.warnBg, titleColor: C.warn });
  box(s, 0.9, 2.05, 5.25, 0.75, { label: "one transactor", fill: C.warnBg, border: C.warn, size: 15 });
  defList(s, 0.95, 3.0, 5.15, [
    ["cannot be parallelised", "serial order is the guarantee"],
    ["can be pipelined", "overlap the round trips"],
  ], { mono: false, rowH: 0.62, leftW: 2.5, size: 12, zebra: false });
  bar(s, 0.9, 4.35, 5.25, 1.2, C.warnBg, { round: true });
  txt(s, "LEVER", { x: 1.1, y: 4.5, w: 4.9, h: 0.3, fontSize: 11, color: C.warn, bold: true, charSpacing: 1.5 });
  txt(s, "keep the writer from going idle", { x: 1.1, y: 4.82, w: 4.9, h: 0.6, fontSize: 15, color: C.ink, fit: "shrink" });

  panel(s, 6.85, 1.45, 5.85, 4.4, "READS", { strip: C.coolBg, titleColor: C.cool });
  [0, 1, 2, 3].forEach(i =>
    box(s, 7.15 + i * 1.35, 2.05, 1.2, 0.75, { label: "core", fill: C.coolBg, border: C.cool, size: 11 }));
  defList(s, 7.2, 3.0, 5.15, [
    ["every peer, every core", "no locks, no coordination"],
    ["free once warm", "cached segments cost storage nothing"],
  ], { mono: false, rowH: 0.62, leftW: 2.5, size: 12, zebra: false });
  bar(s, 7.15, 4.35, 5.25, 1.2, C.coolBg, { round: true });
  txt(s, "LEVER", { x: 7.35, y: 4.5, w: 4.9, h: 0.3, fontSize: 11, color: C.cool, bold: true, charSpacing: 1.5 });
  txt(s, "cut the work up along the index", { x: 7.35, y: 4.82, w: 4.9, h: 0.6, fontSize: 15, color: C.ink, fit: "shrink" });

  takeaway(s, "Same word, different machinery. Do not go looking for threads inside a single query.", 6.05);
}

// Pipelining mechanism
{
  const s = slide("PART IV · PARALLELISM", "Pipelining: overlapping the round trips",
    "Draw the mechanism before showing the measurement, because the measurement is a null result and only makes sense once the mechanism is clear.\n\nSerial: submit, wait for the round trip, get the result, submit the next. The transactor spends most of the wall clock idle, waiting on the network.\n\nPipelined: submit N transactions before collecting any of them. The transactor's work is the same; the idle gaps are gone.\n\nSo pipelining does not make the transactor faster. It stops it from waiting. That is exactly what the next slide's measurement isolates.\n\nConcretely: d/transact returns a future. Serial code derefs it immediately, so the peer waits out a network round trip and a durable write before it submits the next one. Pipelined code submits several and derefs later, so those round trips overlap.\n\nWhy depth stops paying: once enough transactions are in flight to cover the round trip, there is no idleness left to remove. Further depth only queues work, and the queue costs heap in the peer and latency per transaction with no throughput to show for it. 8 to 16 is the documented starting point; the depth that covers your round trip is a measurement, like the gap itself.\n\nFailure signature for unbounded pipelining: peer heap pressure and transaction latencies that grow with queue depth, with no throughput gain to show for it.");

  txt(s, "SERIAL", { x: 0.6, y: 1.53, w: 2.0, h: 0.35, fontSize: 13, color: C.ink, bold: true, charSpacing: 1 });
  for (let i = 0; i < 4; i++) {
    const x = 2.4 + i * 2.55;
    box(s, x, 1.48, 0.9, 0.5, { label: "tx", fill: C.warnBg, border: C.warn, size: 11 });
    bar(s, x + 0.95, 1.63, 1.5, 0.2, C.panel2);
    txt(s, "idle", { x: x + 0.95, y: 1.61, w: 1.5, h: 0.25, fontSize: 9.5, color: C.meta, align: "center" });
  }
  txt(s, "the writer waits on the round trip between every transaction",
    { x: 2.4, y: 2.08, w: 10.3, h: 0.35, fontSize: 12.5, color: C.meta, italic: true });

  txt(s, "PIPELINED", { x: 0.6, y: 3.0, w: 2.0, h: 0.35, fontSize: 13, color: C.ink, bold: true, charSpacing: 1 });
  for (let i = 0; i < 8; i++) {
    box(s, 2.4 + i * 1.0, 2.95, 0.9, 0.5, { label: "tx", fill: C.okBg, border: C.ok, size: 11 });
  }
  txt(s, "8–16 in flight covers most of the benefit — unbounded trades latency for heap",
    { x: 2.4, y: 3.55, w: 10.3, h: 0.35, fontSize: 12.5, color: C.ok, italic: true });

  bar(s, 0.6, 4.25, 12.1, 1.35, C.panel, { round: true });
  txt(s, "Pipelining does not make the transactor faster.", { x: 0.9, y: 4.45, w: 11.5, h: 0.45, fontFace: F.serif, fontSize: 21, color: C.ink });
  txt(s, "It keeps it from idling between round trips. Whether that is worth anything depends entirely on how big the round trip is.",
    { x: 0.9, y: 4.95, w: 11.5, h: 0.5, fontSize: 14, color: C.body });

  takeaway(s, "The benefit is proportional to the round trip you are hiding — so it is measured per environment.", 5.85);
}

// The mem measurement
{
  const s = slide("PART IV · PARALLELISM", "Measured on mem: serial and pipelined are identical",
    "Two runs on datomic:mem://: 500,000 datoms serial, 600,000 pipelined. 24.69 ms and 24.31 ms. No difference.\n\nThat is not a failed experiment, it is a controlled one. datomic:mem:// runs the transactor inside the same JVM, so there is no round trip. Remove the round trip and pipelining has nothing to overlap — which confirms that overlapping the round trip is all it ever did.\n\nRun the identical two lines against a transactor over a socket and a gap appears, proportional to that round trip. So the number is environment-specific: the gap equals the round trip being hidden, and a multiplier from a slide does not transfer.\n\nHow to read a null result: the experiment removed exactly one variable, the round trip, and the effect vanished with it. That is the strongest evidence available that the round trip was the mechanism — stronger than a positive measurement, which would not have isolated anything.\n\nThe consequence for quoting numbers: the pipelining speed-up is a property of the network between peer and transactor, not a property of Datomic. A figure from another environment describes that environment's network.");

  codeBlock(s, "[(serial! 500000) (pipeline! 600000)]\n;; => [24.69 24.31]        ms — no difference", 0.6, 1.45, 6.0, 1.1);

  barCompare(s, 6.9, 1.5, 5.8, [
    ["serial!", 24.69, "24.69 ms", C.warn],
    ["pipeline!", 24.31, "24.31 ms", C.ok],
  ], { labelW: 1.6, rowH: 0.55 });
  txt(s, "identical — on purpose", { x: 6.9, y: 2.7, w: 5.8, h: 0.3, fontSize: 12, color: C.meta, italic: true, align: "center" });

  box(s, 0.6, 3.1, 5.85, 1.9, { label: "datomic:mem://", sub: "transactor in the SAME JVM\n→ no round trip to overlap\n→ pipelining has nothing to do", fill: C.panel, border: C.rule, size: 16, subSize: 13, subH: 0.95 });
  box(s, 6.85, 3.1, 5.85, 1.9, { label: "transactor over a socket", sub: "a real round trip exists\n→ the gap appears\n→ proportional to that round trip", fill: C.coolBg, border: C.cool, size: 16, subSize: 13, subH: 0.95 });

  takeaway(s, "Pipelining hides round trips. On mem there was no round trip to hide, so there is no gap.", 5.2);
  note(s, "Unbounded pipelining trades latency for heap. In production, 8–16 transactions in flight covers most of the benefit.", 5.95);
}

// Slicing the index
{
  const s = slide("PART IV · PARALLELISM", "Parallel reads: slicing one index range across cores",
    ":reading/t is indexed, so AVET can be sliced by value range. Each slice is an independent read — no locks, no coordination, and the transactor is not involved at all.\n\n100,000 readings, 8 slices, pmap across 10 cores: 78.70 ms serial becomes 24.68 ms. About 3.2x, median of five runs.\n\nTwo constraints matter more than the multiplier.\n\nFirst, db is a value. Every slice reads the same immutable database, so there is no snapshot to hold open, no read transaction to leak, and no possibility of slices disagreeing. This is the thing that is genuinely hard in a mutable database and free here.\n\nSecond, the speed-up is bounded by cache misses rather than by cores. On a warm peer you are dividing CPU work. On a cold peer you are overlapping storage waits — which is still a win, just a different one.\n\nWhy no coordination is needed: db is a value, and a value cannot change while it is being read. There is no snapshot to open, no read lock to take, no MVCC version to keep alive, and no way for two slices to disagree. In a mutable database this is the hard part of parallel reads; here it costs nothing.\n\nFailure signature: pmap over slices that do almost no work is slower than serial. The coordination cost per slice is fixed, so when a slice's work is smaller than that cost, parallelism loses. Counting datoms in 250-datom slices is the reproducible example — the same code that gives 3.2x on real per-slice work.");

  txt(s, "d/index-range over 100,000 readings, cut into 8 slices", { x: 0.6, y: 1.4, w: 12.1, h: 0.3, fontSize: 13, color: C.meta });
  for (let i = 0; i < 8; i++) {
    const x = 0.6 + i * 1.52;
    bar(s, x, 1.75, 1.4, 0.62, C.coolBg, { round: true });
    txt(s, "slice " + (i + 1), { x, y: 1.75, w: 1.4, h: 0.62, fontSize: 11.5, color: C.cool, align: "center", valign: "middle" });
    arrow(s, x + 0.7, 2.37, x + 0.7, 2.75, { color: C.cool, width: 1.1 });
    bar(s, x, 2.75, 1.4, 0.32, C.cool, { round: true });
    txt(s, "core", { x, y: 2.75, w: 1.4, h: 0.32, fontSize: 10, color: C.white, align: "center", valign: "middle" });
  }
  txt(s, "one immutable db value  ·  pmap across 10 cores", { x: 0.6, y: 3.15, w: 12.1, h: 0.3, fontSize: 12.5, color: C.cool, align: "center", italic: true });

  barCompare(s, 0.6, 3.6, 7.6, [
    ["serial", 78.70, "78.70 ms", C.panel2],
    ["pmap ×8", 24.68, "24.68 ms", C.cool],
  ], { labelW: 1.6, rowH: 0.6 });
  bar(s, 8.5, 3.6, 4.2, 1.28, C.coolBg, { round: true });
  txt(s, "≈ 3.2×", { x: 8.5, y: 3.75, w: 4.2, h: 0.65, fontFace: F.serif, fontSize: 30, color: C.cool, align: "center" });
  txt(s, "median of five runs", { x: 8.5, y: 4.45, w: 4.2, h: 0.3, fontSize: 11.5, color: C.meta, align: "center" });

  box(s, 0.6, 5.1, 5.85, 1.25, { label: "db is a VALUE", sub: "every slice reads the same immutable database —\nno snapshot to hold open, no transaction to leak", fill: C.panel, border: C.rule, size: 14, subSize: 11.5 });
  box(s, 6.85, 5.1, 5.85, 1.25, { label: "bounded by cache misses, not cores", sub: "warm: you divide CPU work\ncold: you overlap storage waits", fill: C.panel, border: C.rule, size: 14, subSize: 11.5 });

  note(s, "No locks, no coordination, no transactor — slicing a read is a purely peer-local decision.", 6.55);
}

// Slice degradation
{
  const s = slide("PART IV · PARALLELISM", "There is a floor: re-cutting the same 100,000 readings",
    "Same data, same cores, only the slice count changes. 8 slices: 24.68 ms. 100: 27.62. 1,000: 30.12. 10,000: 54.41.\n\nTwo things to notice.\n\nThe degradation is gradual. Going from 8 to 1,000 slices — a 125-fold increase in coordination — costs about 22%. The curve is flat enough that an optimal slice count is not required.\n\nOnly at 10,000 slices does the parallel version start approaching the 78.70 ms serial baseline, and even there it is still faster. Over-slicing degrades the result gradually rather than reversing it.\n\nIn practice: slice to roughly the core count, or a small multiple of it.\n\nWhy the curve is gradual rather than a cliff: the cost per slice is small and constant — a thread hand-off and a range set-up — so total overhead grows linearly with slice count while the useful work stays fixed. There is no threshold and no change of mode, which is why nothing dramatic happens anywhere on the curve.\n\nThe practical reading is that slice count is not worth tuning. Any value near the core count sits in the flat part.");

  const rows = [
    ["8 slices",      24.68, "24.68 ms", C.cool],
    ["100 slices",    27.62, "27.62 ms", C.cool],
    ["1000 slices",   30.12, "30.12 ms", C.warn],
    ["10000 slices",  54.41, "54.41 ms", C.bad],
  ];
  barCompare(s, 0.6, 1.6, 11.0, rows, { max: 78.70, labelW: 2.2, rowH: 0.62, gap: 0.22 });

  // serial baseline marker
  const trackX = 0.6 + 2.2 + 0.2, trackW = 11.0 - 2.2 - 1.5 - 0.3;
  const bx = trackX + trackW * (78.70 / 78.70);
  bar(s, bx - 0.02, 1.5, 0.04, 3.5, C.bad);
  txt(s, "serial baseline  78.70 ms", { x: bx - 3.3, y: 5.05, w: 3.4, h: 0.35, fontSize: 12, color: C.bad, align: "right", bold: true });

  bar(s, 0.6, 5.5, 5.85, 0.95, C.okBg, { round: true });
  txt(s, "8 → 1000 slices costs ~22%", { x: 0.85, y: 5.62, w: 5.4, h: 0.35, fontSize: 14, color: C.ink, bold: true });
  txt(s, "a forgiving curve — no optimum to hunt for", { x: 0.85, y: 5.98, w: 5.4, h: 0.35, fontSize: 12.5, color: C.body });

  bar(s, 6.85, 5.5, 5.85, 0.95, C.warnBg, { round: true });
  txt(s, "even 10,000 slices beats serial", { x: 7.1, y: 5.62, w: 5.4, h: 0.35, fontSize: 14, color: C.ink, bold: true });
  txt(s, "over-slicing degrades gradually", { x: 7.1, y: 5.98, w: 5.4, h: 0.35, fontSize: 12.5, color: C.body });

  note(s, "Slice to about the core count, or a small multiple of it.", 6.6);
}

// d/with what-ifs
{
  const s = slide("PART IV · PARALLELISM", "Parallel what-ifs: d/with branches a value",
    "d/with applies a transaction to a database value and hands back a new value. No transactor is involved, nothing is durable, and nothing is shared — so any number of them can run at once.\n\nThe second form is the important one: after three concurrent what-ifs each reporting 105,005 readings, the base db still reports 105,000. Immutability is what makes the concurrency safe without locking.\n\nUses in production: validation before submitting, scenario analysis, and import dry runs — checking that a large load would not violate an invariant before spending transactor time on it.\n\nMechanism: d/with runs the same transaction machinery the transactor runs, but inside the peer and against a value. It returns db-before, db-after, tx-data and tempids — everything the transactor would have reported, minus durability.\n\nFailure signature it prevents: discovering a uniqueness violation or a failing transaction function at commit time, on the transactor, halfway through a bulk import.");

  box(s, 0.6, 1.5, 2.6, 1.0, { label: "db", sub: "the base value", fill: C.dark, labelColor: C.white, size: 18, subSize: 11, subColor: C.rule });
  ["scenario A", "scenario B", "scenario C"].forEach((sc, i) => {
    const y = 1.45 + i * 1.05;
    arrow(s, 3.2, 2.0, 4.6, y + 0.35, { color: C.gold });
    box(s, 4.6, y, 3.4, 0.7, { label: "d/with " + sc, fill: C.panel, border: C.rule, size: 12.5, mono: false });
    box(s, 8.4, y, 4.3, 0.7, { label: "→ [[105005]]", fill: C.coolBg, border: C.cool, size: 12.5, mono: true });
  });
  txt(s, "no transactor · nothing durable · nothing shared", { x: 4.6, y: 4.55, w: 8.1, h: 0.3, fontSize: 12.5, color: C.cool, italic: true, align: "center" });

  codeBlock(s,
    "(doall (pmap #(d/q all-readings (:db-after (d/with db %))) scenarios))\n;; => ([[105005]] [[105005]] [[105005]])\n\n(d/q all-readings db)\n;; => [[105000]]          the base db is unchanged",
    0.6, 5.0, 8.0, 1.65);
  bar(s, 8.9, 5.0, 3.8, 1.65, C.panel, { round: true });
  txt(s, "USE FOR", { x: 9.15, y: 5.15, w: 3.3, h: 0.3, fontSize: 11, color: C.gold, bold: true, charSpacing: 1.5 });
  bullets(s, ["validation", "scenario analysis", "import dry runs"], { x: 9.15, y: 5.5, w: 3.3, h: 1.05, size: 12.5, gap: 4 });
}

// Where it does not apply
{
  const s = slide("PART IV · PARALLELISM", "Where parallelism does not apply",
    "A single d/q is single-threaded inside the peer. There is no query planner hint, no degree-of-parallelism setting, and no thread pool to grow.\n\nSo parallelism applies across queries, or across index slices that the caller cut — never within one query. If a single query is slow, the answer is its shape or its cache, not more cores.\n\nParallel SQL engines behave differently here, which is why the expectation is stated explicitly.\n\nWhy no knob exists: a datalog query is a join over sorted index traversals, executed in one thread against local structures. Parallelising within a query would mean partitioning the join, which the engine does not do.\n\nFailure signature: one slow query and a peer with idle cores. The cores will not be used. The levers that remain are the query shape — clause order, indexed attributes, narrower ranges — and cache residency.");

  box(s, 0.6, 1.6, 12.1, 1.15, { label: "a single (d/q …) is SINGLE-THREADED inside the peer", fill: C.badBg, border: C.bad, size: 20, labelColor: C.ink });

  txt(s, "So parallelism applies …", { x: 0.6, y: 3.05, w: 12.1, h: 0.35, fontSize: 14, color: C.ink, bold: true });
  box(s, 0.6, 3.5, 3.87, 1.3, { label: "✓  ACROSS queries", sub: "many requests, many cores", fill: C.okBg, border: C.ok, size: 14, subSize: 12 });
  box(s, 4.72, 3.5, 3.87, 1.3, { label: "✓  ACROSS slices", sub: "cut by the caller, as on the last slide", fill: C.okBg, border: C.ok, size: 14, subSize: 12 });
  box(s, 8.84, 3.5, 3.87, 1.3, { label: "✕  WITHIN one query", sub: "no knob exists, and none is coming", fill: C.badBg, border: C.bad, size: 14, subSize: 12 });

  takeaway(s, "A slow single query is fixed by its shape or its cache — never by more cores.", 5.05);
  waypoint(s, "waypoint — labs §4", "MEM",
    "All four experiments run in memory, including the one that shows no difference; that null result is the control.\nThe two counter-intuitive results are pipelining on mem and the 10,000-slice floor.",
    { y: 5.75, h: 1.25 });
}

// ═══════════════════════════════════════════════════════════════════
// PART V · SETTINGS AND SIGNALS
// ═══════════════════════════════════════════════════════════════════

sectionSlide("V", "Settings and signals", "The memory-index loop · the knobs · what to alert on",
  "Settings are lookups. The loop they all act on is what makes a red dashboard readable — it identifies which knob is relevant.\n\nEvery setting in this part acts on one of two loops: the memory-index loop on the write side, or the cache ladder from Part III on the read side. Placing a setting on a loop is what makes it diagnosable. Read on its own, a settings table is a list of names.");

// The loop
{
  const s = slide("PART V · SETTINGS AND SIGNALS", "The memory-index loop",
    "Writes land in the memory index and, always and immediately, in the durable log. Durability never waits for indexing: the rest of the loop is an indexing path, not a write path.\n\nWhen the memory index passes memory-index-threshold, an indexing job starts draining it into storage.\n\nIf writes keep outrunning that drain, the memory index reaches memory-index-max, and the transactor throttles the writers. p99 write latency rises.\n\nThe throttle is intentional back-pressure protecting the transactor's heap, not a failure. But it is what appears on your dashboard as a latency incident, so knowing the loop is what stops you from tuning at random when it happens.\n\nThreshold starts the job. Max starts the throttling. Those two sentences describe the whole loop.\n\nWhy the loop exists at all: appending to the durable log is cheap, while updating the sorted indexes is expensive — it rewrites tree segments. Doing the second on every transaction would make every write pay index-maintenance cost. So recent writes live in an in-memory index, get merged into the durable indexes in batches, and queries read the union of the two. Durability is never deferred; only indexing is.\n\nFailure signature: query latency that climbs slowly between indexing jobs and drops when one finishes. That is the memory index growing and then being merged — reads are paying for the union.");

  box(s, 0.6, 1.5, 2.2, 0.7, { label: "writes", fill: C.panel2, size: 14 });
  arrow(s, 2.8, 1.85, 4.0, 1.85, { color: C.gold });

  box(s, 4.0, 1.5, 4.4, 0.7, { label: "memory index", fill: C.coolBg, border: C.cool, size: 15 });
  arrow(s, 8.4, 1.85, 9.7, 1.85, { color: C.ok });
  box(s, 9.7, 1.5, 3.0, 0.7, { label: "durable log", sub: "always, immediately", fill: C.okBg, border: C.ok, size: 13, subSize: 10, subColor: C.ok });

  arrow(s, 6.2, 2.2, 6.2, 2.95, { color: C.gold, label: "past memory-index-threshold", labelW: 4.2, labelDx: 2.4, labelSize: 11, labelColor: C.gold });
  box(s, 4.0, 2.95, 4.4, 0.7, { label: "indexing job", fill: C.panel, border: C.rule, size: 15 });
  arrow(s, 8.4, 3.3, 9.7, 3.3, { color: C.rule });
  box(s, 9.7, 2.95, 3.0, 0.7, { label: "storage", fill: C.dark, labelColor: C.white, size: 13 });

  arrow(s, 6.2, 3.65, 6.2, 4.4, { color: C.warn, label: "if writes keep outrunning it", labelW: 4.2, labelDx: 2.4, labelSize: 11, labelColor: C.warn });
  bar(s, 3.2, 4.4, 6.0, 1.5, C.badBg, { round: true });
  txt(s, "memory-index-max reached", { x: 3.45, y: 4.55, w: 5.5, h: 0.35, fontFace: F.mono, fontSize: 13.5, color: C.bad, bold: true });
  txt(s, "→  the transactor throttles writers\n→  p99 write latency rises", { x: 3.45, y: 4.95, w: 5.5, h: 0.8, fontSize: 13, color: C.ink });

  bar(s, 0.6, 6.15, 12.1, 0.65, C.panel, { round: true });
  txt(s, "threshold  starts the indexing job          ·          max  starts the throttling",
    { x: 0.85, y: 6.15, w: 11.6, h: 0.65, fontSize: 14.5, color: C.ink, valign: "middle", align: "center", fit: "shrink" });
}

// Throttle is not a failure
{
  const s = slide("PART V · SETTINGS AND SIGNALS", "The throttle is back-pressure, not a failure",
    "Same gauge, three states. The third is the one commonly misread as a failure.\n\nNormal: the memory index sits below threshold, no indexing job running.\n\nDraining: past threshold, an indexing job is running, writes continue at full speed. This is the healthy steady state under sustained load, not a warning.\n\nThrottled: at max. The transactor deliberately slows the writers so the memory index cannot grow past what its heap can hold. Nothing is lost, nothing errors, and p99 rises.\n\nWhen you see the third state, the question is not 'how do I turn off throttling' — it is 'why is the drain slower than the write rate'. Usually that is storage write latency, or an indexing job competing with a heavy write burst.\n\nWhy throttling rather than failing: the memory index is bounded by the transactor's heap. Without back-pressure a sustained burst would exhaust it, which is an outage. Slowing the writers converts an outage into latency, which is the trade being made deliberately.\n\nWhere to look, in order: the drain is storage writes plus index maintenance, so check storage write latency first and overlapping indexing jobs second. Raising memory-index-max buys a longer runway; it does not make the drain faster.");

  const states = [
    ["NORMAL", 0.35, "below threshold · no job running", C.ok, C.okBg],
    ["DRAINING", 0.7, "past threshold · indexing job running · writes at full speed", C.warn, C.warnBg],
    ["THROTTLED", 1.0, "at max · transactor slows the writers on purpose", C.bad, C.badBg],
  ];
  states.forEach((st, i) => {
    const y = 1.9 + i * 1.5;
    txt(s, st[0], { x: 0.6, y, w: 2.0, h: 0.5, fontSize: 14, color: st[3], bold: true, charSpacing: 1, valign: "middle" });
    const gx = 2.8, gw = 7.4;
    bar(s, gx, y, gw, 0.75, C.panel);
    bar(s, gx, y, gw * st[1], 0.75, st[4]);
    bar(s, gx + gw * 0.55 - 0.02, y - 0.12, 0.04, 0.99, C.warn);
    bar(s, gx + gw - 0.04, y - 0.12, 0.04, 0.99, C.bad);
    if (i === 0) {
      txt(s, "threshold", { x: gx + gw * 0.55 - 0.9, y: y - 0.45, w: 1.8, h: 0.3, fontSize: 10.5, color: C.warn, align: "center" });
      txt(s, "max", { x: gx + gw - 0.9, y: y - 0.45, w: 1.8, h: 0.3, fontSize: 10.5, color: C.bad, align: "center" });
    }
    txt(s, st[2], { x: 2.8, y: y + 0.8, w: 9.9, h: 0.4, fontSize: 12.5, color: C.body });
    bar(s, 10.5, y, 2.2, 0.75, st[4], { round: true });
    txt(s, i === 2 ? "p99 rises" : "p99 flat", { x: 10.5, y, w: 2.2, h: 0.75, fontSize: 12.5, color: st[3], align: "center", valign: "middle", bold: true });
  });

  takeaway(s, "Throttled is not broken. Ask why the drain is slower than the write rate — usually storage.", 6.3);
}

// Transactor settings
{
  const s = slide("PART V · SETTINGS AND SIGNALS", "Transactor settings — properties file",
    "Read this table as a diagnosis aid rather than as a configuration guide: symptom on the right, knob on the left.\n\nThe first two are the loop from two slides ago. object-cache-max is the transactor's own read cache — a transactor reads while it indexes. memcached unset means every peer misses separately, which is Part III's thundering herd. heartbeat-interval-msec directly widens or narrows Part II's failover window.\n\nThe warning at the bottom: passing ANY JVM flag to bin/transactor makes it drop its own GC defaults. If you pass anything at all, re-specify the GC flags yourself.\n\nNote what the table does not contain: there is no setting that makes indexing faster. Every knob here changes when work happens or how much is buffered. Making the drain faster is a storage question, not a properties-file question.\n\nThe GC line is a real failure signature: a transactor that starts pausing after an unrelated flag was added to bin/transactor is running on default JVM GC, not because that flag was wrong but because passing any flag at all drops the shipped defaults.");

  const hdr = ["SETTING", "IF", "EFFECT"];
  const rows = [
    ["memory-index-threshold", "too high", "long, bursty indexing jobs"],
    ["memory-index-max", "too low", "early throttling under load"],
    ["object-cache-max", "too low", "warm reads behave like cold ones"],
    ["memcached", "unset", "each peer misses storage separately"],
    ["heartbeat-interval-msec", "too high", "a longer Part II failover window"],
  ];
  bar(s, 0.6, 1.45, 12.1, 0.5, C.dark);
  [[0.85, 4.5], [5.5, 1.8], [7.5, 5.0]].forEach((c, i) =>
    txt(s, hdr[i], { x: c[0], y: 1.45, w: c[1], h: 0.5, fontSize: 11.5, color: C.goldHi, bold: true, charSpacing: 1.5, valign: "middle" }));
  rows.forEach((r, i) => {
    const y = 2.05 + i * 0.68;
    bar(s, 0.6, y, 12.1, 0.6, i % 2 ? C.bg : C.panel);
    txt(s, r[0], { x: 0.85, y, w: 4.5, h: 0.6, fontFace: F.mono, fontSize: 13, color: C.ink, valign: "middle", fit: "shrink" });
    bar(s, 5.5, y + 0.13, 1.5, 0.34, C.warnBg, { round: true });
    txt(s, r[1], { x: 5.5, y: y + 0.13, w: 1.5, h: 0.34, fontSize: 11, color: C.warn, align: "center", valign: "middle" });
    txt(s, r[2], { x: 7.5, y, w: 5.0, h: 0.6, fontSize: 13, color: C.body, valign: "middle", fit: "shrink" });
  });

  bar(s, 0.6, 5.6, 12.1, 1.1, C.badBg, { round: true });
  bar(s, 0.6, 5.6, 0.09, 1.1, C.bad);
  txt(s, "⚠  Passing ANY JVM flag to bin/transactor makes it drop its own GC defaults.",
    { x: 0.9, y: 5.72, w: 11.6, h: 0.35, fontSize: 14, color: C.bad, bold: true });
  txt(s, "Re-specify  -XX:+UseG1GC  -XX:MaxGCPauseMillis=50  whenever you pass anything at all.",
    { x: 0.9, y: 6.12, w: 11.6, h: 0.4, fontFace: F.mono, fontSize: 12.5, color: C.ink });
}

// Peer flags
{
  const s = slide("PART V · SETTINGS AND SIGNALS", "Peer flags — and where each one lands on the ladder",
    "The peer flags map one-to-one onto Part III's tier ladder, which is the easiest way to remember them: the first four are tiers 1, 3, 2 and 2's size limit. The fifth is Part II's dial.\n\nDefaults change between releases. Confirm the values against the distribution in use — the next slide is how.\n\nWhy the list is this short: a peer has almost nothing to tune. It has a cache ladder and one timeout. Everything else about how a peer behaves is a property of the code it runs — which queries it issues and what access pattern those produce.\n\nA misconfiguration worth naming: valcachePath pointed at a directory two peers share. Valcache is a per-peer cache, so a shared path buys no sharing and is not a supported way to build one. The tier that is shared on purpose is memcached.");

  const flags = [
    ["datomic.objectCacheMax", "the peer's own heap cache", "tier 1", C.dark, C.white],
    ["datomic.memcachedServers", "join the shared tier", "tier 3", C.panel2, C.ink],
    ["datomic.valcachePath", "the SSD tier", "tier 2", C.gold, C.white],
    ["datomic.valcacheMaxGb", "how much of that disk to use", "tier 2", C.gold, C.white],
    ["datomic.txTimeoutMsec", "how long a write waits for a writer", "Part II", C.warnBg, C.warn],
  ];
  flags.forEach((f, i) => {
    const y = 1.5 + i * 0.85;
    bar(s, 0.6, y, 12.1, 0.72, i % 2 ? C.bg : C.panel);
    txt(s, "-D" + f[0], { x: 0.85, y, w: 5.0, h: 0.72, fontFace: F.mono, fontSize: 13.5, color: C.ink, valign: "middle", fit: "shrink" });
    txt(s, f[1], { x: 6.1, y, w: 5.0, h: 0.72, fontSize: 13.5, color: C.body, valign: "middle", fit: "shrink" });
    bar(s, 11.35, y + 0.16, 1.35, 0.4, f[3], { round: true });
    txt(s, f[2], { x: 11.35, y: y + 0.16, w: 1.35, h: 0.4, fontSize: 11, color: f[4], align: "center", valign: "middle", bold: true });
  });

  takeaway(s, "Four of the five flags are just the cache ladder. The fifth is the failover dial.", 5.95);
  note(s, "Defaults change between releases — confirm against the distribution you run, not against this slide.", 6.7);
}

// Read the defaults
{
  const s = slide("PART V · SETTINGS AND SIGNALS", "Read the defaults from the distribution, not from a deck",
    "Three commands, and they are the actual answer to 'what is the default for X'.\n\nThe samples directory is the authoritative starting point for a properties file. The grep strips comments and blank lines so you see only what is actually set. The third one finds where a feature is wired in the scripts — useful for valcache in particular, whose flags moved between releases.\n\nWhere the distribution and this deck disagree, the distribution is current.\n\nThe reason to read defaults off the distribution rather than out of documentation is that defaults are release-specific, and the distribution on disk is the one that will run. It is a two-minute check that resolves most questions of the form why is this setting not taking effect before they turn into debugging.");

  codeBlock(s,
    "ls $DATOMIC/config/samples/\n\ngrep -vE '^\\s*(#|$)' $DATOMIC/config/samples/sql-transactor-template.properties\n\ngrep -rn \"valcache\" $DATOMIC/bin $DATOMIC/config",
    0.6, 1.6, 12.1, 2.3, { dark: true, size: 15 });

  const why = [
    ["ls samples/", "the authoritative starting point for a properties file"],
    ["grep -vE '#|$'", "shows only what is actually set, not the commentary"],
    ["grep -rn valcache", "finds where a feature is wired — flags move between releases"],
  ];
  why.forEach((w, i) => {
    const y = 4.2 + i * 0.7;
    bar(s, 0.6, y, 12.1, 0.6, i % 2 ? C.bg : C.panel);
    txt(s, w[0], { x: 0.85, y, w: 3.4, h: 0.6, fontFace: F.mono, fontSize: 12.5, color: C.gold, valign: "middle" });
    txt(s, w[1], { x: 4.5, y, w: 8.0, h: 0.6, fontSize: 13, color: C.body, valign: "middle", fit: "shrink" });
  });

  takeaway(s, "Where the distribution and this deck disagree, the distribution is current.", 6.4);
}

// Two instruments
{
  const s = slide("PART V · SETTINGS AND SIGNALS", "Two instruments the peer already provides",
    "Both of these ship with the peer library and cost nothing to adopt.\n\nd/sync-index returns a database whose INDEX — not merely whose log — includes a given t. d/sync waits on the log alone. Under light load the two look identical; under load, when indexing lags behind the log, they diverge, and code that assumed d/sync was enough starts reading through the memory index instead of the index it expected.\n\nThe tx-report queue lets any peer observe transactions as they land. The datom count in the example decomposes: 401 = 100 readings × 4 attributes + 1 :db/txInstant. A count that does not decompose is a signal about transaction shape.\n\nIn production the queue is used for audit, cache invalidation and CDC. In class, as a throughput monitor.\n\nWhy sync and sync-index differ: the log receives a transaction immediately, the durable index receives it at the next merge. A database whose log includes t answers queries correctly — it just answers them by reading the memory index as well as the durable one. Under indexing lag that is a difference in cost, not in correctness.\n\nSo sync is enough for read-your-writes, which is about a single recent transaction. sync-index is what a bulk read wants, because it wants the work already merged into the sorted index rather than merged again per query.");

  panel(s, 0.6, 1.45, 5.85, 2.5, "d/sync  vs  d/sync-index");
  box(s, 0.9, 2.1, 5.25, 0.65, { label: "d/sync  →  waits on the LOG", fill: C.panel2, size: 13.5, mono: true });
  box(s, 0.9, 2.9, 5.25, 0.65, { label: "d/sync-index  →  waits on the INDEX", fill: C.goldHi, size: 13.5, mono: true });
  txt(s, "identical under light load · they diverge under load", { x: 0.9, y: 3.6, w: 5.25, h: 0.3, fontSize: 11.5, color: C.meta, italic: true, align: "center" });

  panel(s, 6.85, 1.45, 5.85, 2.5, "THE TX-REPORT QUEUE");
  codeBlock(s, ":t 1001  :datoms 401\n:t 1102  :datoms 401", 7.1, 2.05, 5.35, 0.85);
  txt(s, "401 = 100 readings × 4 attrs + 1 :db/txInstant\naudit · cache invalidation · CDC · throughput monitor",
    { x: 7.1, y: 3.0, w: 5.35, h: 0.8, fontSize: 12, color: C.body });

  txt(s, "SIGNALS TO ALERT ON — each one maps onto the loop", { x: 0.6, y: 4.15, w: 12.1, h: 0.35, fontSize: 13, color: C.gold, bold: true, charSpacing: 1 });
  const sig = [
    ["alarms of any kind", "the transactor is telling you directly"],
    ["indexing job duration", "the drain is falling behind"],
    ["transaction latency p99", "throttling"],
    ["storage read/write time", "the bottleneck is storage"],
    ["memcached hit ratio", "the shared tier is not being shared"],
  ];
  sig.forEach((g, i) => {
    const y = 4.55 + i * 0.42;
    txt(s, "▸  " + g[0], { x: 0.8, y, w: 4.6, h: 0.4, fontSize: 12.5, color: C.ink, valign: "middle", fit: "shrink" });
    txt(s, g[1], { x: 5.6, y, w: 6.9, h: 0.4, fontSize: 12.5, color: C.body, valign: "middle", fit: "shrink" });
  });

  waypoint(s, "waypoint — labs §5", "MEM",
    "The memory-index demo is [MEM]; the metrics-callback contract is read from the distribution.",
    { y: 6.7, h: 0.75 });
}

// ═══════════════════════════════════════════════════════════════════
// PART VI · DEPLOYMENT
// ═══════════════════════════════════════════════════════════════════

sectionSlide("VI", "Deployment", "A peer carries a cache — so shipping one is not like shipping a stateless service",
  "Everything in this part follows from one fact: a peer is not stateless. It carries a cache, and that cache is the difference between a 10 ms read and a 1,000 ms one. A deployment procedure that treats peers as interchangeable stateless containers reproduces the Part III cold-cache burst on every release.\n\nA stateless service can be replaced instance for instance with no warm-up, because nothing was accumulated. A peer accumulated a cache, and that cache took time and storage traffic to build. Everything in this part follows from that one difference.");

// Ready but cold
{
  const s = slide("PART VI · DEPLOYMENT", "\"Up\" and \"ready\" are not the same thing",
    "A readiness probe that succeeds at process start reports a peer that is up and cold. It will accept traffic and serve every one of the first requests from storage.\n\nThe fix is to make readiness mean warm. Warm the segments the service actually reads — index ranges over the attributes it queries — and only then report ready. That moves the cold-read cost ahead of the first request instead of onto it.\n\nNote pmap in warm!: warming uses the parallel-slice pattern from Part IV for a different purpose. And note that it returns basis-t alongside :ready? — useful for the read-your-writes contract on the next slide.\n\nWhy the default probe is wrong here rather than merely imprecise: it is not reporting the wrong thing about the process, it is reporting about the wrong subject. The process genuinely is ready. The cache is not, and the cache is what decides the latency the caller sees.\n\nFailure signature: the first few hundred requests to a fresh instance are slow, and the effect is gone before anyone can attach to it. It never reproduces on a warm instance, which is where people go looking.");

  box(s, 0.6, 1.45, 5.85, 1.9, { label: "process started  →  READY", sub: "cache empty · every read goes to storage · p99 in the seconds", fill: C.badBg, border: C.bad, size: 16, subSize: 12.5 });
  fatArrow(s, 6.55, 2.05, 0.7, 0.7, pres.shapes.RIGHT_ARROW, { fill: C.rule });
  box(s, 7.4, 1.45, 5.3, 1.9, { label: "warmed  →  READY", sub: "segments resident · the first request is as fast as the hundredth", fill: C.okBg, border: C.ok, size: 16, subSize: 12.5, subH: 0.62 });

  codeBlock(s,
    "(defn warm! [conn]\n  (let [db (d/db conn)]\n    (doall (pmap (fn [[lo hi]] (count (seq (d/index-range db :reading/t lo hi))))\n                 (partition 2 1 (range 0 20001 2500))))\n    {:ready? true :basis-t (d/basis-t db)}))",
    0.6, 3.55, 12.1, 1.85);

  takeaway(s, "Warm the segments the service reads, then report ready. Readiness should mean warm, not alive.", 5.55);
  waypoint(s, "waypoint — labs §6", "MEM",
    "Run (warm!), then the d/sync read-your-writes check on the next slide.",
    { y: 6.25, h: 0.75 });
}

// Read your own writes
{
  const s = slide("PART VI · DEPLOYMENT", "Reading your own writes across a fleet of peers",
    "A fresh peer starts at whatever t storage gives it. So a request that arrives just after a write made through a DIFFERENT peer can legitimately be served a database value that does not include that write. Nothing is broken; the peer is simply behind.\n\nThe contract that fixes it is to pass the t along with the request and have the reading peer d/sync to it before answering. That is the consistency contract of a multi-peer deployment, and it belongs in the request envelope — a header or a field — rather than in a sleep.\n\nIf what you need is the index rather than the log, sync-index, per Part V.\n\nWhy the peer is behind rather than wrong: its database value is a consistent snapshot at some t. Answering from t-1 is a correct answer to a slightly older question. Nothing is corrupt and nothing will be logged as an error, which is what makes this hard to find.\n\nWhy a sleep does not fix it: a sleep guesses at a delay that varies with load. d/sync waits on the actual condition, and a deref with a timeout also bounds the wait.\n\nFailure signature: a write followed by a read that does not see it, reproducible only under load or with more than one peer — and never in a single-peer test environment, which is where the code was written.");

  txt(s, "WITHOUT the contract", { x: 0.6, y: 1.4, w: 5.85, h: 0.35, fontSize: 13, color: C.bad, bold: true, charSpacing: 1 });
  box(s, 0.6, 1.8, 2.7, 0.7, { label: "peer A", sub: "writes at t=21011", fill: C.warnBg, border: C.warn, size: 13, subSize: 10.5 });
  box(s, 3.75, 1.8, 2.7, 0.7, { label: "peer B", sub: "still at t=20990", fill: C.badBg, border: C.bad, size: 13, subSize: 10.5 });
  arrow(s, 0.9, 2.6, 0.9, 3.15, { color: C.warn, head: false });
  arrow(s, 4.05, 2.6, 4.05, 3.15, { color: C.bad, head: false });
  box(s, 0.6, 3.15, 5.85, 0.65, { label: "the caller reads back … and the write is missing", fill: C.badBg, border: C.bad, size: 13 });

  txt(s, "WITH the contract", { x: 6.85, y: 1.4, w: 5.85, h: 0.35, fontSize: 13, color: C.ok, bold: true, charSpacing: 1 });
  box(s, 6.85, 1.8, 2.7, 0.7, { label: "peer A", sub: "writes, returns t", fill: C.warnBg, border: C.warn, size: 13, subSize: 10.5 });
  box(s, 10.0, 1.8, 2.7, 0.7, { label: "peer B", sub: "d/sync to that t", fill: C.okBg, border: C.ok, size: 13, subSize: 10.5 });
  arrow(s, 9.55, 2.15, 10.0, 2.15, { color: C.ok, label: "t travels with the request", labelW: 3.0, labelDy: -0.35 });
  arrow(s, 7.15, 2.6, 7.15, 3.15, { color: C.ok, head: false });
  arrow(s, 10.3, 2.6, 10.3, 3.15, { color: C.ok, head: false });
  box(s, 6.85, 3.15, 5.85, 0.65, { label: "the caller reads back … and sees the write", fill: C.okBg, border: C.ok, size: 13 });

  codeBlock(s, "(d/basis-t (deref (d/sync conn t) 5000 nil))\n;; => 21011      the t that was written", 0.6, 4.1, 7.4, 1.15);
  bar(s, 8.2, 4.1, 4.5, 1.15, C.panel, { round: true });
  txt(s, "Pass the t with the request.\nFor the index rather than the log:\nd/sync-index.",
    { x: 8.45, y: 4.25, w: 4.0, h: 0.9, fontSize: 12.5, color: C.body });

  takeaway(s, "Passing the t with the request IS the consistency contract of a multi-peer deployment.", 5.55);
  note(s, "Deref with a timeout: an unbounded sync is a stalled thread waiting on a transactor that may not exist.", 6.35);
}

// Rollout order
{
  const s = slide("PART VI · DEPLOYMENT", "Rollout order — and why it is safe in this order",
    "Four steps, and step 1 is the one that differs from SQL.\n\nDatomic schema is additive. A new attribute is a new entity; an old peer simply never asks about it. So you ship schema first, with no lock, no ALTER, no migration window and no coordination with the code rollout. That is what makes the rest of the order safe: by the time new code arrives, the schema it needs is already there.\n\nStep 2 is Part III's mitigation number 3. Step 3 is the previous slide's warm!. Step 4 is Part II's failover, this time on purpose: start the new-version standby, stop the old active, spend one bounded write pause.\n\nFrom a peer's point of view, a rolling transactor upgrade and a transactor crash are indistinguishable. The same mechanism handles both, which is what makes the measured window from Part II the relevant number.\n\nWhy additive schema removes the migration window: a new attribute is a new entity in the same database, asserted by an ordinary transaction. Nothing is rewritten, nothing is locked, and no existing datom is touched. A peer that does not know the attribute never queries it. So schema can ship days ahead of the code that uses it.\n\nThe order is not a convention — each step depends on the previous one already being true. Schema before code, so the code finds what it needs. Peers in waves before traffic, so the caches are never all cold at once. Warm before ready, so the first request is not the one that pays. Transactor last, because it is the only step that costs a write pause.");

  const steps = [
    ["1", "ship the SCHEMA", "additive → old peers ignore what's new", "no lock · no ALTER · no migration window", C.gold],
    ["2", "roll peers in WAVES", "avoids the cold-cache burst", "Part III, mitigation 3", C.gold],
    ["3", "WARM, then report ready", "the cost moves ahead of the first request", "the previous slide's warm!", C.gold],
    ["4", "roll the TRANSACTOR", "one bounded write pause, on purpose", "Part II's failover, deliberately", C.bad],
  ];
  steps.forEach((st, i) => {
    const y = 1.5 + i * 1.12;
    bar(s, 0.6, y, 12.1, 0.95, i % 2 ? C.bg : C.panel, { round: true });
    bar(s, 0.6, y, 0.85, 0.95, st[4], { round: true });
    txt(s, st[0], { x: 0.6, y, w: 0.85, h: 0.95, fontFace: F.serif, fontSize: 26, color: C.white, align: "center", valign: "middle" });
    txt(s, st[1], { x: 1.7, y: y + 0.14, w: 4.4, h: 0.4, fontSize: 16, color: C.ink, bold: true, fit: "shrink" });
    txt(s, st[3], { x: 1.7, y: y + 0.54, w: 4.4, h: 0.32, fontSize: 11.5, color: C.meta, italic: true });
    txt(s, st[2], { x: 6.4, y, w: 6.1, h: 0.95, fontSize: 14, color: C.body, valign: "middle", fit: "shrink" });
    if (i < 3) arrow(s, 1.02, y + 0.95, 1.02, y + 1.12, { color: C.rule, width: 1.2 });
  });

  bar(s, 0.6, 6.05, 12.1, 0.85, C.panel2, { round: true });
  txt(s, "From a peer, a transactor upgrade and a transactor outage are indistinguishable — the same mechanism handles both.",
    { x: 0.9, y: 6.05, w: 11.6, h: 0.85, fontSize: 14.5, color: C.ink, valign: "middle", fit: "shrink" });
}

// ═══════════════════════════════════════════════════════════════════
// CLOSE
// ═══════════════════════════════════════════════════════════════════

// Summary
{
  const s = slide("SUMMARY", "The model the settings operate on",
    "Four verbs. Every part of the class attaches to one of them.\n\nFails: storage totally, the transactor for writes only, peers locally.\nWaits: writers wait — during failover, and during throttling. Those are the only two places anything waits.\nScales: reads by peers × cores, writes by pipelining, and nothing scales inside one query.\nCosts: storage, whenever a cache is cold.\n\nThe settings tables in Part V are lookups. This table is the model they operate on.\n\nUsed as a diagnosis path: any symptom lands on one of the four rows. Writes failing is FAILS, and the question is which component. Writes slow is WAITS, and there are only two places anything waits. Reads slow is COSTS, so ask which tier answered — unless it is a single query, in which case SCALES already says that more cores will not help.");

  const rows = [
    ["FAILS",  "storage totally  ·  transactor for writes  ·  peers locally", C.bad, C.badBg, "Parts I–II"],
    ["WAITS",  "writers — during failover, and during throttling",            C.warn, C.warnBg, "Parts II & V"],
    ["SCALES", "reads: peers × cores  ·  writes: by pipelining",              C.ok, C.okBg, "Part IV"],
    ["COSTS",  "storage, whenever a cache is cold",                           C.cool, C.coolBg, "Parts III & VI"],
  ];
  rows.forEach((r, i) => {
    const y = 1.6 + i * 1.15;
    bar(s, 0.6, y, 12.1, 1.0, r[3], { round: true });
    bar(s, 0.6, y, 0.09, 1.0, r[2]);
    txt(s, r[0], { x: 0.95, y, w: 2.3, h: 1.0, fontSize: 16, color: r[2], bold: true, charSpacing: 1.5, valign: "middle" });
    txt(s, r[1], { x: 3.4, y, w: 7.3, h: 1.0, fontSize: 15, color: C.ink, valign: "middle", fit: "shrink" });
    txt(s, r[4], { x: 10.8, y, w: 1.7, h: 1.0, fontSize: 11.5, color: C.meta, align: "right", valign: "middle" });
  });

  takeaway(s, "The Part V settings are lookups. This table is the model they operate on.", 6.35);
}

// Where to go next
{
  const s = slide("CLOSE", "Where to go next",
    "The Production class covers the paths and storage layers this class assumed. infra/HA.md has the two-transactor setup if Part II was not run live.\n\nOn Cloud: the model is the same and the operational surface is different. Query groups are Part IV's read scaling packaged as an autoscaling group, and failover is managed by the platform rather than by you. Everything in Parts I, III, IV and V still applies.\n\nThe failover drill is worth repeating on your own staging environment: the window depends on that infrastructure, so the applicable number is the measured one.");

  const items = [
    ["src/datomic_infra/labs.clj", "Datomic in Production — the paths and storage layers this class assumed", C.panel],
    ["infra/HA.md", "the two-transactor setup, if Part II was not run live", C.panel],
    ["Datomic Cloud", "query groups = Part IV's read scaling as an autoscaling group; failover is managed by the platform. Same model, different operational surface.", C.coolBg],
  ];
  items.forEach((it, i) => {
    const y = 1.5 + i * 1.25;
    bar(s, 0.6, y, 12.1, 1.1, it[2], { round: true });
    txt(s, it[0], { x: 0.9, y: y + 0.14, w: 11.5, h: 0.38, fontFace: F.mono, fontSize: 14, color: C.gold, bold: true });
    txt(s, it[1], { x: 0.9, y: y + 0.55, w: 11.5, h: 0.5, fontSize: 13.5, color: C.body, fit: "shrink" });
  });

  bar(s, 0.6, 5.3, 12.1, 1.15, C.okBg, { round: true });
  bar(s, 0.6, 5.3, 0.09, 1.15, C.ok);
  txt(s, "Repeat the failover drill on your own staging environment.", { x: 0.95, y: 5.45, w: 11.5, h: 0.4, fontSize: 16, color: C.ink, bold: true });
  txt(s, "The window depends on that infrastructure — the only number worth quoting is the one you measured.",
    { x: 0.95, y: 5.88, w: 11.5, h: 0.4, fontSize: 13.5, color: C.body });
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
  console.log(issues ? `\n${issues} QA issues` : "\nQA clean");
}

pres.writeFile({ fileName: "datomic-at-scale.pptx" }).then(f => console.log("wrote", f, "· slides:", page));
