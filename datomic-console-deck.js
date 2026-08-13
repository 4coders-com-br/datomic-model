/**
 * datomic-console-deck.js
 * DATOMIC CONSOLE — Wall to Wall (2-hour teaching deck)
 * Run: node datomic-console-deck.js
 * Out: datomic-console.pptx
 */

const pptxgen = require("pptxgenjs");

const C = {
  bg:      "EFE9DD",
  ink:     "3A2C1A",
  body:    "6B5A44",
  meta:    "9A8A72",
  gold:    "A8864A",
  goldHi:  "C6A46A",
  rule:    "C9BDA4",
  panel:   "E6DFCE",
  dark:    "2C2416",
  white:   "FCFAF4",
  code:    "3A2C1A",
  accent:  "8B4513",
};

const F = {
  serif: "Georgia",
  sans:  "Helvetica Neue",
  mono:  "Courier New",
};

const pres = new pptxgen();
pres.title = "Datomic Console — Wall to Wall";
pres.author = "Datomic Classes";
pres.defineLayout({ name: "WIDE", width: 13.333, height: 7.5 });
pres.layout = "WIDE";

let page = 0;

function bar(slide, x, y, w, h, color) {
  slide.addShape(pres.shapes.RECTANGLE, {
    x, y, w, h,
    fill: { color },
    line: { type: "none" },
  });
}

function header(slide, chapter) {
  page += 1;
  slide.addText(chapter, {
    x: 0.6, y: 0.28, w: 10, h: 0.28,
    fontFace: F.sans, fontSize: 11, color: C.meta,
    charSpacing: 2, margin: 0,
  });
  slide.addText(String(page), {
    x: 12.2, y: 0.28, w: 0.7, h: 0.28,
    fontFace: F.sans, fontSize: 11, color: C.meta,
    align: "right", margin: 0,
  });
  bar(slide, 0.6, 0.58, 12.1, 0.015, C.goldHi);
}

function titleSlide(kicker, title, subtitle) {
  const s = pres.addSlide();
  s.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 13.333, h: 7.5,
    fill: { color: C.dark }, line: { type: "none" },
  });
  page += 1;
  s.addText(kicker, {
    x: 0.9, y: 2.0, w: 11.5, h: 0.35,
    fontFace: F.sans, fontSize: 13, color: C.goldHi,
    charSpacing: 3, margin: 0,
  });
  s.addText(title, {
    x: 0.9, y: 2.5, w: 11.5, h: 1.2,
    fontFace: F.serif, fontSize: 40, color: C.white,
    bold: true, margin: 0,
  });
  if (subtitle) {
    s.addText(subtitle, {
      x: 0.9, y: 3.9, w: 11.5, h: 0.6,
      fontFace: F.sans, fontSize: 16, color: C.rule, margin: 0,
    });
  }
  s.addText(String(page), {
    x: 12.2, y: 7.0, w: 0.7, h: 0.28,
    fontFace: F.sans, fontSize: 11, color: C.meta,
    align: "right", margin: 0,
  });
  return s;
}

function sectionSlide(num, title, blurb) {
  const s = pres.addSlide();
  s.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 13.333, h: 7.5,
    fill: { color: C.dark }, line: { type: "none" },
  });
  page += 1;
  s.addText("§" + num, {
    x: 0.9, y: 2.3, w: 11.5, h: 0.4,
    fontFace: F.sans, fontSize: 18, color: C.goldHi,
    charSpacing: 2, margin: 0,
  });
  s.addText(title, {
    x: 0.9, y: 2.85, w: 11.5, h: 0.8,
    fontFace: F.serif, fontSize: 34, color: C.white, margin: 0,
  });
  if (blurb) {
    s.addText(blurb, {
      x: 0.9, y: 3.85, w: 11.5, h: 0.7,
      fontFace: F.sans, fontSize: 16, color: C.rule, margin: 0,
    });
  }
  s.addText(String(page), {
    x: 12.2, y: 7.0, w: 0.7, h: 0.28,
    fontFace: F.sans, fontSize: 11, color: C.meta,
    align: "right", margin: 0,
  });
  return s;
}

function contentSlide(chapter, title) {
  const s = pres.addSlide();
  s.addShape(pres.shapes.RECTANGLE, {
    x: 0, y: 0, w: 13.333, h: 7.5,
    fill: { color: C.bg }, line: { type: "none" },
  });
  header(s, chapter);
  s.addText(title, {
    x: 0.6, y: 0.75, w: 12.1, h: 0.55,
    fontFace: F.serif, fontSize: 26, color: C.ink, margin: 0,
  });
  return s;
}

function bullets(slide, items, opts = {}) {
  const x = opts.x || 0.6;
  const y = opts.y || 1.5;
  const w = opts.w || 12.1;
  const size = opts.size || 16;
  const lines = items.map((t, i) => ({
    text: t,
    options: {
      bullet: opts.bullet !== false,
      breakLine: true,
      paraSpaceAfter: 8,
    },
  }));
  slide.addText(lines, {
    x, y, w, h: opts.h || 5.2,
    fontFace: F.sans, fontSize: size, color: C.body,
    valign: "top", margin: 0,
  });
}

function twoCol(slide, leftTitle, leftItems, rightTitle, rightItems) {
  bar(slide, 0.6, 1.45, 5.8, 5.2, C.panel);
  bar(slide, 6.9, 1.45, 5.8, 5.2, C.panel);
  slide.addText(leftTitle, {
    x: 0.85, y: 1.65, w: 5.3, h: 0.4,
    fontFace: F.sans, fontSize: 14, color: C.gold, bold: true,
    charSpacing: 1, margin: 0,
  });
  slide.addText(rightTitle, {
    x: 7.15, y: 1.65, w: 5.3, h: 0.4,
    fontFace: F.sans, fontSize: 14, color: C.gold, bold: true,
    charSpacing: 1, margin: 0,
  });
  bullets(slide, leftItems, { x: 0.85, y: 2.2, w: 5.3, h: 4.2, size: 14 });
  bullets(slide, rightItems, { x: 7.15, y: 2.2, w: 5.3, h: 4.2, size: 14 });
}

function codeBlock(slide, code, x, y, w, h) {
  bar(slide, x, y, w, h, C.panel);
  slide.addText(code, {
    x: x + 0.2, y: y + 0.15, w: w - 0.4, h: h - 0.3,
    fontFace: F.mono, fontSize: 13, color: C.code,
    valign: "top", margin: 0,
  });
}

function note(slide, text, y = 6.6) {
  slide.addText(text, {
    x: 0.6, y, w: 12.1, h: 0.4,
    fontFace: F.sans, fontSize: 13, color: C.meta, italic: true, margin: 0,
  });
}

// ═══════════════════════════════════════════════════════════════════
// SLIDES
// ═══════════════════════════════════════════════════════════════════

titleSlide(
  "A 2-HOUR, CONSOLE-FIRST CLASS",
  "Datomic Console — Wall to Wall",
  "Every pane. Every control. One small store database."
);

// 2 Route
{
  const s = contentSlide("TONIGHT", "Tonight's route — two hours, ten moves");
  const rows = [
    ["§0", "Frame — what Console is", "5 min"],
    ["§1", "Launch + window anatomy", "12 min"],
    ["§2", "Schema tree", "12 min"],
    ["§3", "Query tab wall to wall", "28 min"],
    ["§4", "Entities tab", "12 min"],
    ["§5", "Transactions tab", "12 min"],
    ["§6", "Indexes tab", "12 min"],
    ["§7", "Time — as-of / since / history", "15 min"],
    ["§8", "Data sources + multi-db", "10 min"],
    ["§9–10", "Limits + student exercises", "20 min"],
  ];
  rows.forEach((r, i) => {
    const y = 1.45 + i * 0.48;
    s.addText(r[0], { x: 0.6, y, w: 1.2, h: 0.4, fontFace: F.mono, fontSize: 14, color: C.gold, margin: 0 });
    s.addText(r[1], { x: 2.0, y, w: 8.5, h: 0.4, fontFace: F.sans, fontSize: 15, color: C.ink, margin: 0 });
    s.addText(r[2], { x: 10.8, y, w: 1.8, h: 0.4, fontFace: F.sans, fontSize: 14, color: C.meta, align: "right", margin: 0 });
  });
}

// 3 What it is
{
  const s = contentSlide("§0 · FRAME", "What Console is — and is not");
  twoCol(s,
    "IS",
    [
      "A graphical peer for on-prem Datomic",
      "Schema browser, query builder, entity walker",
      "Transaction log zoom + raw index browser",
      "as-of / since / history on every tab",
      "Named data sources, including multi-db",
      "Read-only by design (no transact UI)",
    ],
    "IS NOT",
    [
      "Datomic Cloud's operational UI",
      "A write console or admin panel",
      "A pull / rules / d/with builder",
      "Durable saved-query storage",
      "A substitute for the REPL in CI",
      "Safe to unbounded-scan prod casually",
    ]
  );
}

// 4 One sentence
{
  const s = contentSlide("§0 · FRAME", "The bet of the class");
  s.addText("Console is a read-only peer UI over every Datomic read surface.", {
    x: 0.6, y: 2.5, w: 12.1, h: 1.0,
    fontFace: F.serif, fontSize: 28, color: C.ink, margin: 0,
  });
  s.addText("Use it to see. Use the REPL to script.", {
    x: 0.6, y: 3.7, w: 12.1, h: 0.6,
    fontFace: F.sans, fontSize: 20, color: C.gold, margin: 0,
  });
  note(s, "Companion: src/datomic_console/labs.clj  ·  (seed!) once, then live in the browser");
}

// ── §1 Launch ──────────────────────────────────────────────────
sectionSlide("1", "Launch + window anatomy", "Storage URI · alias · browser · the five panes");

{
  const s = contentSlide("§1 · LAUNCH", "Same infra as Production");
  bullets(s, [
    "Docker Postgres + Pro transactor (infra/)",
    "clj -M:infra:repl  →  (require 'datomic-console.labs)  →  (seed!)",
    "Creates two DBs: store (rich history) and warehouse (tiny multi-db peer)",
    "seed! is idempotent — re-run if someone mutates the data mid-class",
  ]);
  codeBlock(s,
    "docker compose -f infra/docker-compose.yml up -d\n$DATOMIC/bin/transactor infra/pg-transactor.properties\nclj -M:infra:repl   ; (datomic-console.labs/seed!)",
    0.6, 4.0, 12.1, 1.8);
}

{
  const s = contentSlide("§1 · LAUNCH", "bin/console — the one command that trips everyone");
  codeBlock(s,
    "$DATOMIC/bin/console -p 8080 \\\n  pg \"datomic:sql://?jdbc:postgresql://localhost:5432/datomic?user=datomic&password=datomic\"\n\n# Open http://localhost:8080/browse/   ·  Chrome recommended",
    0.6, 1.5, 12.1, 2.4);
  bullets(s, [
    "URI has NO database name — Console lists every DB in that storage",
    "Alias pg is the label in the Storage dropdown",
    "You may pass multiple alias / URI pairs",
    "Console is a peer: object cache, reads storage directly",
  ], { y: 4.2, h: 2.5, size: 15 });
}

{
  const s = contentSlide("§1 · LAUNCH", "Five things to say out loud at start");
  bullets(s, [
    "1. Storage URI ≠ database URI — no db name on the command line",
    "2. storage: pg  ·  DB: store  — pick both before querying",
    "3. Stopping the transactor does NOT stop Console reads",
    "4. Console never writes — still treat queries as load on prod",
    "5. Saved queries die with the process — they are not durable",
  ]);
}

{
  const s = contentSlide("§1 · ANATOMY", "The window — project once, then stop narrating chrome");
  // ascii layout as text blocks
  bar(s, 0.6, 1.5, 12.1, 0.7, C.panel);
  s.addText("storage  |  DB  |  as-of  |  since  |  history", {
    x: 0.8, y: 1.65, w: 11.7, h: 0.4,
    fontFace: F.mono, fontSize: 16, color: C.ink, align: "center", margin: 0,
  });
  bar(s, 0.6, 2.35, 3.2, 3.6, C.panel);
  s.addText("schema\ntree", {
    x: 0.8, y: 3.5, w: 2.8, h: 1.0,
    fontFace: F.sans, fontSize: 18, color: C.ink, align: "center", margin: 0,
  });
  bar(s, 4.0, 2.35, 8.7, 1.6, C.panel);
  s.addText("Query  ·  Entities  ·  Transactions  ·  Indexes", {
    x: 4.2, y: 2.9, w: 8.3, h: 0.5,
    fontFace: F.sans, fontSize: 18, color: C.ink, align: "center", margin: 0,
  });
  bar(s, 4.0, 4.15, 8.7, 1.8, C.panel);
  s.addText("data set  (results)", {
    x: 4.2, y: 4.75, w: 8.3, h: 0.5,
    fontFace: F.sans, fontSize: 18, color: C.ink, align: "center", margin: 0,
  });
  bar(s, 0.6, 6.1, 3.2, 0.7, C.panel);
  s.addText("data sources", {
    x: 0.8, y: 6.25, w: 2.8, h: 0.4,
    fontFace: F.sans, fontSize: 14, color: C.ink, align: "center", margin: 0,
  });
  note(s, "Every later section is one of these panes, full depth.");
}

{
  const s = contentSlide("§1 · ANATOMY", "◆ CONSOLE · Open store and confirm the seed");
  bullets(s, [
    "Storage: pg  ·  DB: store",
    "Schema tree shows product / customer / order / line / tx",
    "Four products: TEE-001, MUG-001, EBOOK-01, HOOD-001",
    "Three orders: ORD-1001 … ORD-1003",
    "REPL verifier: see labs.clj §1 slide 9",
  ]);
}

// ── §2 Schema ──────────────────────────────────────────────────
sectionSlide("2", "Schema tree", "Attributes are entities — visible in a tree");

{
  const s = contentSlide("§2 · SCHEMA", "◆ CONSOLE · Expand the tree, attribute by attribute");
  bullets(s, [
    "Expand product → list of attributes",
    "product/sku → valueType string, cardinality one, unique identity, doc",
    "product/tags → cardinality many (contrast with sku)",
    "product/category → valueType ref",
    "Find :category/apparel — enums are entities with :db/ident",
    "order/lines → isComponent true",
  ]);
  note(s, "Teaching point: the tree IS a query over attributes-as-entities. No external metadata.");
}

{
  const s = contentSlide("§2 · SCHEMA", "Why this schema exists for class");
  const rows = [
    [":product/sku unique/identity", "AVET + identity demos"],
    [":product/tags card-many", "entity tree + :with trap"],
    [":product/category → idents", "enums in the schema tree"],
    [":order/lines component", "nested Entities expand"],
    [":order/customer, :line/product", "reverse nav (VAET)"],
    [":tx/user, :tx/note", "annotated txs in Transactions"],
    ["two DBs store + warehouse", "multi-db data sources"],
  ];
  rows.forEach((r, i) => {
    const y = 1.5 + i * 0.65;
    s.addText(r[0], { x: 0.6, y, w: 6.5, h: 0.5, fontFace: F.mono, fontSize: 15, color: C.ink, margin: 0 });
    s.addText(r[1], { x: 7.3, y, w: 5.4, h: 0.5, fontFace: F.sans, fontSize: 15, color: C.body, margin: 0 });
  });
}

{
  const s = contentSlide("§2 · SCHEMA", "REPL mirror of the tree");
  codeBlock(s,
    ";; product attributes as the schema tree shows them\n(d/q '[:find ?a ?vt ?card ?uniq :where ...]\n     (store-db))\n\n(d/pull (store-db)\n  '[:db/ident :db/isComponent :db/doc :db/cardinality]\n  :order/lines)",
    0.6, 1.5, 12.1, 4.5);
}

// ── §3 Query ───────────────────────────────────────────────────
sectionSlide("3", "Query tab — wall to wall", "Builder · text · :in · :with · save · dataset");

{
  const s = contentSlide("§3 · QUERY", "A · Minimal query in the builder");
  bullets(s, [
    "find:  ?sku",
    "where:  _  |  :product/sku  |  ?sku",
    "Run query",
    "Dataset caption: shape + count (4)",
    "Sort by clicking column headers",
  ]);
  note(s, "Leave this query open — we build on it.");
}

{
  const s = contentSlide("§3 · QUERY", "B · Two views, one query");
  bullets(s, [
    "Edit the TEXT box on the right — builder fields sync",
    "Edit a builder field — text syncs",
    "Copy from application code; paste back",
    "This is the whole point of the dual UI",
  ]);
  codeBlock(s,
    "[:find ?sku\n :where [_ :product/sku ?sku]]",
    0.6, 4.0, 12.1, 1.8);
}

{
  const s = contentSlide("§3 · QUERY", "C · Joins in the where table");
  bullets(s, [
    "Add rows with + ; reorder with arrows",
    "?e | :product/sku     | ?sku",
    "?e | :product/price   | ?price",
    "?e | :product/active? | true",
    "find: ?sku ?price",
    "Only active products remain (EBOOK-01 drops out later in history)",
  ]);
}

{
  const s = contentSlide("§3 · QUERY", "D · :in parameters");
  bullets(s, [
    "in table:  ?min  |  2000",
    "Add a predicate row: [(> ?price ?min)]",
    "find: ?sku ?price",
    "$ always means the current DB (respects as-of/since/history)",
  ]);
  codeBlock(s,
    "[:find ?sku ?price\n :in $ ?min\n :where [?e :product/sku ?sku]\n        [?e :product/price ?price]\n        [(> ?price ?min)]]",
    0.6, 4.0, 12.1, 2.2);
}

{
  const s = contentSlide("§3 · QUERY", "E · The :with trap (card-many)");
  twoCol(s,
    ":find ?sku  (tags joined)",
    [
      "where sku + tags",
      "→ still 4 rows",
      "?tag is projected away",
      "Set semantics hide card-many",
      "Looks 'fine' — and lies",
    ],
    ":find ?sku ?tag   or   :with",
    [
      ":find ?sku ?tag → 8 rows",
      ":with ?tag also yields 8",
      ":with = do not dedupe on these",
      "It is NOT group-by",
      "Domain-modeling Part 2 same trap",
    ]
  );
}

{
  const s = contentSlide("§3 · QUERY", "F · Dataset → Entities");
  bullets(s, [
    "Return entity ids in the find clause",
    "Click an eid in the dataset → Entities tab opens on that entity",
    "This is the primary navigation path into the graph",
    "Console Entity ID field wants a numeric eid or :db/ident",
    "Lookup refs like [:order/id \"ORD-1001\"] are NOT accepted there — query first",
  ]);
}

{
  const s = contentSlide("§3 · QUERY", "G · Save queries (and their limit)");
  bullets(s, [
    "+ / − and combobox above the query text",
    "Save the join you just built; reload it",
    "Saved queries are process-local — Console restart = gone",
    "Not a team knowledge base; not durable configuration",
  ]);
  note(s, "When you need durable queries, put them in code.");
}

{
  const s = contentSlide("§3 · QUERY", "H · Live join — Bruna's orders");
  codeBlock(s,
    "[:find ?oid ?status\n :in $ ?email\n :where\n   [?c :customer/email ?email]\n   [?o :order/customer ?c]\n   [?o :order/id ?oid]\n   [?o :order/status ?s]\n   [?s :db/ident ?status]]\n\n; ?email = \"bruna@example.com\"\n; => #{[\"ORD-1001\" :status/shipped]}",
    0.6, 1.5, 12.1, 5.0);
}

{
  const s = contentSlide("§3 · QUERY", "Query-centric, not pull-centric");
  bullets(s, [
    "There is no pull builder in Console",
    "No rules editor, no d/with",
    "When the UI runs out, drop to labs.clj against the same DB",
    "Console shows; REPL scripts",
  ]);
}

// ── §4 Entities ────────────────────────────────────────────────
sectionSlide("4", "Entities tab", "Graph walk both directions");

{
  const s = contentSlide("§4 · ENTITIES", "◆ CONSOLE · Walk ORD-1001");
  bullets(s, [
    "From Query results, click the eid of ORD-1001",
    "Expand :order/customer → Bruna",
    "Expand :order/lines → each line → :line/product → product",
    "On a product, find reverse refs — who points HERE?",
    "Walk product → lines → orders → other customers",
  ]);
  note(s, "Teaching point: the graph database costume. Refs are bidirectional at read time.");
}

{
  const s = contentSlide("§4 · ENTITIES", "Components and reverse refs");
  twoCol(s,
    "isComponent",
    [
      "order/lines is component true",
      "Affects cascading retract + default pull",
      "Tree still expands either way in Console",
      "Does not create a separate \"document store\"",
    ],
    "Reverse navigation",
    [
      "VAET makes \"who points at me?\" free",
      "Entities tab surfaces reverse refs",
      "Same idea as _ in pull syntax",
      "REPL: query line/product → order/lines",
    ]
  );
}

{
  const s = contentSlide("§4 · ENTITIES", "REPL mirror — pull tree for ORD-1001");
  codeBlock(s,
    "(d/pull (store-db)\n  '[:order/id\n    {:order/status [:db/ident]}\n    {:order/customer [:customer/email :customer/name]}\n    {:order/lines [:line/qty :line/unit-price\n                   {:line/product [:product/sku :product/name]}]}]\n  [:order/id \"ORD-1001\"])",
    0.6, 1.5, 12.1, 4.8);
}

// ── §5 Transactions ────────────────────────────────────────────
sectionSlide("5", "Transactions tab", "Zoom from day to datom");

{
  const s = contentSlide("§5 · TRANSACTIONS", "Why the chart has shape");
  bullets(s, [
    "seed! backdated :db/txInstant from 2024-01 through 2024-07",
    "Without that, every tx lands in one wall-clock second → one spike",
    "Day / Hour / Minute: bars = transaction counts",
    "Second scale: bars = datom counts",
    "Click a second → dataset fills with raw datoms",
  ]);
}

{
  const s = contentSlide("§5 · TRANSACTIONS", "◆ CONSOLE · Find the price change");
  bullets(s, [
    "Open Transactions — bars span Jan → Jul 2024",
    "Drill into May (busy month)",
    "Hour → Minute → Second",
    "Find the pricing tx (\"Q2 price list — TEE-001\")",
    "Dataset shows: retract old price + assert new + :tx/user + :tx/note + :db/txInstant",
    "Pan with arrows; zoom out with up",
  ]);
  note(s, "Teaching point: the log IS browsable. Transactions are entities.");
}

{
  const s = contentSlide("§5 · TRANSACTIONS", "Annotated transactions");
  bullets(s, [
    ":tx/user and :tx/note are ordinary attributes on the tx entity",
    "Same idea as domain-modeling §4.2 — Console just renders them",
    "Every seeded batch has a note: schema, catalog, checkouts, pricing, ops, support, merch",
    "REPL: query ?tx :tx/note + :db/txInstant; or d/tx-range on the log",
  ]);
}

// ── §6 Indexes ─────────────────────────────────────────────────
sectionSlide("6", "Indexes tab", "EAVT · AEVT · AVET · VAET — raw");

{
  const s = contentSlide("§6 · INDEXES", "Four sorts of the same datoms");
  const rows = [
    ["EAVT", "entity → attr → value", "\"row\" of an entity"],
    ["AEVT", "attr → entity → value", "column store"],
    ["AVET", "attr → value → entity", "key-value / unique lookup"],
    ["VAET", "value → attr → entity", "reverse refs / graph"],
  ];
  rows.forEach((r, i) => {
    const y = 1.55 + i * 1.05;
    bar(s, 0.6, y, 12.1, 0.9, C.panel);
    s.addText(r[0], { x: 0.85, y: y + 0.25, w: 1.5, h: 0.4, fontFace: F.mono, fontSize: 18, color: C.gold, bold: true, margin: 0 });
    s.addText(r[1], { x: 2.6, y: y + 0.25, w: 5.5, h: 0.4, fontFace: F.sans, fontSize: 16, color: C.ink, margin: 0 });
    s.addText(r[2], { x: 8.3, y: y + 0.25, w: 4.1, h: 0.4, fontFace: F.sans, fontSize: 16, color: C.body, margin: 0 });
  });
}

{
  const s = contentSlide("§6 · INDEXES", "◆ CONSOLE · Five lookups");
  bullets(s, [
    "AVET + :product/sku + \"TEE-001\" → unique identity lookup",
    "AVET + :product/price (no value) → prices as a sorted column",
    "EAVT + eid of ORD-1001 → every attr on that order",
    "AEVT + :order/status → status column across orders",
    "VAET + customer eid + :order/customer → who points at Bruna?",
  ]);
  note(s, "Same four indexes as domain-modeling Part 6 — Console pages them without d/datoms.");
}

// ── §7 Time ────────────────────────────────────────────────────
sectionSlide("7", "Time filters", "as-of · since · history — top bar rebinds $");

{
  const s = contentSlide("§7 · TIME", "The top bar rebinds every tab");
  bullets(s, [
    "as-of / since / history are database-value features",
    "Not query syntax — they change what $ means",
    "Apply to Query, Entities, Transactions, Indexes alike",
    "Under the hood: d/as-of, d/since, d/history",
  ]);
}

{
  const s = contentSlide("§7 · TIME", "◆ CONSOLE · A · as-of");
  bullets(s, [
    "Set as-of to 2024-04-15 (calendar picker)",
    "Query TEE-001 price → 2900 (pre-May hike)",
    "Clear as-of → 2500 (after summer sale)",
    "You can also enter a t value or tx entity id",
  ]);
  codeBlock(s,
    "{:apr-15 2900   ; as-of 2024-04-15\n :may-15 3400   ; as-of 2024-05-15\n :today  2500}  ; present",
    0.6, 4.2, 12.1, 1.8);
}

{
  const s = contentSlide("§7 · TIME", "◆ CONSOLE · B · since");
  bullets(s, [
    "since 2024-06-01 → only novelty after that point is visible",
    "Good for \"what changed this quarter?\"",
    "Sparse by design — most entities disappear",
    "Combine with history carefully; explain the intersection",
  ]);
}

{
  const s = contentSlide("§7 · TIME", "◆ CONSOLE · C · history checkbox");
  bullets(s, [
    "Check history",
    "Query price for TEE-001 with ?price ?tx ?added",
    "See assert/retract pairs: 2900 → 3400 → 2500",
    "Uncheck → only the present value",
  ]);
  codeBlock(s,
    ";; history db — every price assertion and retraction\n([2900 #inst \"2024-01-05\" true]\n [2900 #inst \"2024-05-01\" false]\n [3400 #inst \"2024-05-01\" true]\n [3400 #inst \"2024-07-01\" false]\n [2500 #inst \"2024-07-01\" true])",
    0.6, 3.8, 12.1, 2.5);
}

{
  const s = contentSlide("§7 · TIME", "◆ CONSOLE · D · as-of + Entities");
  bullets(s, [
    "as-of 2024-05-09 on TEE-001's entity",
    "Tags still include \"bestseller\"",
    "as-of 2024-05-11 → bestseller gone (retracted May 10)",
    "Same entity, different database values",
  ]);
}

// ── §8 Data sources ────────────────────────────────────────────
sectionSlide("8", "Data sources", "Named dbs · multi-db joins · datasets as inputs");

{
  const s = contentSlide("§8 · DATA SOURCES", "◆ CONSOLE · Save two databases");
  bullets(s, [
    "Data sources pane — lower left — use +",
    "With DB=store, save source \"store-now\"",
    "Switch DB dropdown to warehouse, save \"warehouse\"",
    "$ is just the default input; named sources are extra inputs",
  ]);
}

{
  const s = contentSlide("§8 · DATA SOURCES", "◆ CONSOLE · Multi-db join");
  codeBlock(s,
    "[:find ?sku ?price ?n\n :in $store $stock\n :where\n   [$store ?p :product/sku ?sku]\n   [$store ?p :product/price ?price]\n   [$stock ?s :sku/code ?sku]\n   [$stock ?s :sku/on-hand ?n]]\n\n; bind $store → store-now, $stock → warehouse",
    0.6, 1.5, 12.1, 4.5);
  note(s, "Join store prices to warehouse on-hand on the sku string.");
}

{
  const s = contentSlide("§8 · DATA SOURCES", "Datasets as sources");
  bullets(s, [
    "Run a query; save its result set as a named data source",
    "Feed it into a later query as a tuple binding",
    "Console standing in for application-level pipelines",
    "Still process-local — restart clears them",
  ]);
}

// ── §9 Limits ──────────────────────────────────────────────────
sectionSlide("9", "Limits, safety, judgment", "When Console · when REPL");

{
  const s = contentSlide("§9 · LIMITS", "Surface coverage");
  twoCol(s,
    "CONSOLE COVERS",
    [
      "schema tree",
      "q (builder + text)",
      "entities graph walk",
      "tx log browse",
      "datoms / indexes",
      "as-of / since / history",
      "data sources / multi-db",
    ],
    "CONSOLE MISSES",
    [
      "transact",
      "pull builder",
      "rules",
      "d/with",
      "d/filter",
      "tx-report-queue",
      "durable saved queries",
    ]
  );
}

{
  const s = contentSlide("§9 · LIMITS", "Safety — say this before prod tomorrow");
  bullets(s, [
    "Console is a peer — it caches segments in the object cache",
    "Unbounded queries against a huge prod DB from a laptop can hurt storage",
    "Read-only UI ≠ free load; treat exploration as production traffic",
    "Chrome recommended; other browsers can mis-draw the Transactions chart",
    "On-prem peer tool — Cloud has different operational surfaces",
  ]);
}

{
  const s = contentSlide("§9 · LIMITS", "When each tool wins");
  twoCol(s,
    "CONSOLE SHINES",
    [
      "Onboarding a colleague to a schema",
      "Auditing what happened to entity X",
      "Showing non-Clojure stakeholders",
      "Teaching (this class)",
      "Exploratory time travel",
    ],
    "REPL WINS",
    [
      "Rules, pull, d/with, filters",
      "Tx functions, report queue",
      "Repeatable scripts and tests",
      "CI and automation",
      "Large analytical queries you time and tune",
    ]
  );
}

// ── §10 Exercises ──────────────────────────────────────────────
sectionSlide("10", "Exercises", "Students drive — instructor only unsticks");

{
  const s = contentSlide("§10 · EXERCISES", "Four problems · fifteen minutes");
  bullets(s, [
    "1. Schema — uniqueness of :customer/email? valueType of :order/status?",
    "2. Query — paid/shipped orders with customer name + line count",
    "3. Time — ORD-1001 status as-of 2024-05-02 vs 2024-05-04",
    "4. Multi-db — which active product has the lowest on-hand?",
  ]);
  note(s, "Solutions live at the bottom of labs.clj §10.");
}

{
  const s = contentSlide("§10 · EXERCISES", "Expected answers (reveal after)");
  bullets(s, [
    "1. :db.unique/identity · :db.type/ref",
    "2. ORD-1001 Bruna 2 · ORD-1003 Nikita 2  (ORD-1002 cancelled)",
    "3. :status/paid → :status/shipped",
    "4. HOOD-001 with 18 units (EBOOK is inactive)",
  ]);
}

// Closing
{
  const s = contentSlide("CLOSE", "The whole class in one sentence");
  s.addText("Console is a read-only peer UI over every Datomic read surface — use it to see; use the REPL to script.", {
    x: 0.6, y: 2.6, w: 12.1, h: 1.5,
    fontFace: F.serif, fontSize: 26, color: C.ink, margin: 0,
  });
}

{
  const s = contentSlide("CLOSE", "Go deeper");
  bullets(s, [
    "docs.datomic.com/resources/console.html — official Console reference",
    "src/datomic_console/labs.clj — seed, click paths, REPL verifiers",
    "domain-modeling-with-datoms — why the four indexes matter",
    "datomic-infrastructure — the peer you just pointed Console at",
    "bin/console -p port alias uri [alias uri]+",
  ]);
}

titleSlide(
  "END OF CLASS",
  "Go open a production schema\nyou've never seen.",
  "Start with the tree. Stay curious. Drop to the REPL when the UI runs out."
);

pres.writeFile({ fileName: "datomic-console.pptx" })
  .then(() => console.log("wrote datomic-console.pptx, pages:", page))
  .catch((e) => { console.error(e); process.exit(1); });
