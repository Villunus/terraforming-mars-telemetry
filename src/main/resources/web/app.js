const $ = (id) => document.getElementById(id);

function tablePlayers(data) {
  const raw = data.players && data.players.length
    ? data.players
    : [data.you, data.opponent].filter(Boolean);
  const seen = new Set();
  const list = [];
  for (const p of raw) {
    if (!p || seen.has(p.id)) continue;
    seen.add(p.id);
    list.push(p);
  }
  return list;
}

function teamColor(p) {
  return p && p.color ? p.color : "blue";
}

function accountName(p) {
  if (p && p.name && p.name !== "You" && !/^Player \d+$/.test(p.name)) return p.name;
  return "";
}

function displayName(p, fallback) {
  if (p && p.corporation && p.corporation !== "Unknown") return p.corporation;
  const name = accountName(p);
  if (name) return name;
  return (p && p.name && p.name !== "You" && p.name) || fallback || "Player";
}

function boardTitle(p) {
  return displayName(p, "Player");
}

function boardSubtitle(p) {
  if (!p) return "";
  const name = accountName(p);
  if (name && name !== boardTitle(p)) return name;
  return "";
}

function resMark(rawKey) {
  const key = resKey(rawKey);
  return `<span class="res-sym res-${key}"><span class="res-box">${resIcon(key)}</span></span>`;
}

function renderCubes(p) {
  const rows = [
    // TR has no production; it's added to M€ income each generation, so it sits just above M€.
    ["TR", "tr", p.tr ?? 20, null],
    ["M€", "mc", p.megaCredits, p.megaCreditProd],
    ["Steel", "steel", p.steel, p.steelProd],
    ["Titanium", "ti", p.titanium, p.titaniumProd],
    ["Plant", "plant", p.plants, p.plantProd],
    ["Energy", "energy", p.energy, p.energyProd],
    ["Heat", "heat", p.heat, p.heatProd],
  ];
  return `<div class="board-cubes"><table class="cubes">
    <tr><th>Resource</th><th class="num">Prod</th><th class="num">Qty</th></tr>
    ${rows.map(([n, key, q, pr]) => {
      const tr = key === "tr";
      return `<tr${tr ? ` class="tr-row" title="Terraform rating: also added to your M€ income each generation"` : ""}><td><span class="res-name">${resMark(key)}<span>${n}</span></span></td><td class="num prod${tr ? " na" : ""}">${tr ? "–" : pr ?? 0}</td><td class="num">${q ?? 0}</td></tr>`;
    }).join("")}
  </table></div>`;
}

/** Board tiles as hex icons, colored like the tiles on the app icon. */
const TILE_ICONS = {
  city: { fill: "#c8ced6", glyph: '<path fill="#2b3440" d="M7 17V11l3-2v8zm4 0V8l3-2 3 2v9zm2.2-7.5h1.6v1.6h-1.6zm0 3h1.6v1.6h-1.6z"/>' },
  greenery: { fill: "#2e8c3a", glyph: '<path fill="#e6f5d8" d="M12 6.5 8 13h2.6L8.6 16H11v2h2v-2h2.4l-2-3H16z"/>' },
  ocean: { fill: "#2876c8", glyph: '<path fill="none" stroke="#e3f1ff" stroke-width="1.5" stroke-linecap="round" d="M7.5 10.5q1.1-1.2 2.25 0t2.25 0 2.25 0 2.25 0M7.5 14q1.1-1.2 2.25 0t2.25 0 2.25 0 2.25 0"/>' },
  special: { fill: "#8a5a34", glyph: '<path fill="#f3e2cf" d="m12 7.5 1.3 2.8 3 .3-2.3 2 .7 3-2.7-1.6-2.7 1.6.7-3-2.3-2 3-.3z"/>' },
  // Special tiles, for the placing badge.
  mine: { fill: "#8a5a34", glyph: '<g fill="none" stroke="#f3e2cf" stroke-linecap="round"><path stroke-width="1.7" d="M7.8 17.2 14.2 10.4"/><path stroke-width="2" d="M11.2 6.7Q16.7 6.9 17 12.5"/></g>' },
  mohole: { fill: "#6b3a24", glyph: '<ellipse cx="12" cy="14" rx="5" ry="2.6" fill="#1a0b05" stroke="#ff8a3d" stroke-width="1"/><path fill="none" stroke="#ffb36b" stroke-width="1.2" stroke-linecap="round" d="M10 10.5q-.8-1.2 0-2.4M12 10q-.8-1.2 0-2.4M14 10.5q-.8-1.2 0-2.4"/>' },
  preserve: { fill: "#2e6b34", glyph: '<circle cx="12" cy="12.3" r="5.6" fill="none" stroke="#e6f5d8" stroke-width="1.1"/><path fill="#e6f5d8" d="M12 8 9.3 12.3H11l-1.3 2h1.5v1.4h1.6v-1.4h1.5l-1.3-2h1.7z"/>' },
  eco: { fill: "#2e8c3a", glyph: '<g fill="#e6f5d8"><ellipse cx="12" cy="14.6" rx="3" ry="2.4"/><ellipse cx="8.6" cy="11.4" rx="1.1" ry="1.5"/><ellipse cx="10.8" cy="9.2" rx="1.1" ry="1.5"/><ellipse cx="13.2" cy="9.2" rx="1.1" ry="1.5"/><ellipse cx="15.4" cy="11.4" rx="1.1" ry="1.5"/></g>' },
  nuclear: { fill: "#d9b52a", glyph: '<g fill="#1a1208"><circle cx="12" cy="12.5" r="1.1"/><path d="M11.10 10.94L9.50 8.17A5 5 0 0 1 14.50 8.17L12.90 10.94A1.8 1.8 0 0 0 11.10 10.94ZM13.80 12.50L17.00 12.50A5 5 0 0 1 14.50 16.83L12.90 14.06A1.8 1.8 0 0 0 13.80 12.50ZM11.10 14.06L9.50 16.83A5 5 0 0 1 7.00 12.50L10.20 12.50A1.8 1.8 0 0 0 11.10 14.06Z"/></g>' },
  lava: { fill: "#8a2a14", glyph: '<path fill="#3a1a10" d="M6.5 17.5 10 10h4l3.5 7.5z"/><path fill="#ffb02e" d="M10 10h4l-.6 1.4-.9-.6-.6 2.4-.7-1.9-.6.9z"/><path fill="none" stroke="#ff7a2e" stroke-width="1" stroke-linecap="round" d="M11 8.5 10.3 6.8M12 8.3V6.3M13 8.5l.7-1.7"/>' },
  industry: { fill: "#5d6670", glyph: '<path fill="#e8eaed" d="M6.5 17v-5l3.2-2v2l3.2-2v2l3.1-2V7h1.5v10z"/>' },
  restricted: { fill: "#8b1e1e", glyph: '<circle cx="12" cy="12.3" r="5" fill="#f4e6e6"/><rect x="8.8" y="11.3" width="6.4" height="2" fill="#8b1e1e"/>' },
  commercial: { fill: "#c99a2e", glyph: '<circle cx="12" cy="12.3" r="5" fill="#f3d36b" stroke="#6b4a10" stroke-width=".8"/><text x="12" y="14.3" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="5.2" font-weight="700" fill="#3a2a08">M€</text>' },
};

/** The special tile each card places, keyed by lowercase card name. */
const SPECIAL_TILE_KIND = {
  capital: "city", // a city, placed through the generic tile action
  "mining area": "mine",
  "mining rights": "mine",
  "mohole area": "mohole",
  "natural preserve": "preserve",
  "ecological zone": "eco",
  "nuclear zone": "nuclear",
  "lava flows": "lava",
  "industrial center": "industry",
  "restricted area": "restricted",
  "commercial district": "commercial",
};

function tileIcon(kind) {
  const t = TILE_ICONS[kind];
  return `<span class="tile-sym" aria-hidden="true"><svg viewBox="0 0 24 24"><path fill="${t.fill}" stroke="rgba(0,0,0,.45)" stroke-width="1" d="M12 1.8 21 7v10l-9 5.2L3 17V7z"/>${t.glyph}</svg></span>`;
}

function renderPlacements(p) {
  const tiles = [
    ["city", p.citiesOnMars ?? 0, "cities on Mars"],
    ["greenery", p.greeneries ?? 0, "greeneries"],
    ["ocean", p.oceans ?? 0, "oceans"],
  ];
  if (p.specialTiles) tiles.push(["special", p.specialTiles, "special tiles"]);
  return `<div class="board-tiles">
      <h3 class="section-title board-subtitle">Placements</h3>
      <div class="tags">${tiles.map(([kind, n, label]) =>
        `<span class="tag-count${n ? "" : " zero"}" title="${n} ${label}">${tileIcon(kind)}<span class="n">${n}</span></span>`).join("")}</div>
    </div>`;
}

function renderTags(tags) {
  const cells = tags
    ? Object.entries(tags).sort(([a], [b]) => (a === "none") - (b === "none")).map(([k, v]) =>
      `<span class="tag-count${v ? "" : " zero"}" title="${escapeHtml(TAG_LABEL[k] || k)} ${v}">${tagIcon(k)}<span class="n">${v}</span></span>`)
    : [];
  return `<div class="board-tags"><h3 class="section-title board-subtitle">Tags</h3><div class="tags">${cells.join("")}</div></div>`;
}

function extra(text) {
  return (text || "").replace(/\n/g, " · ").replace(/\s+/g, " ").trim();
}

function corpRuleText(raw) {
  if (!raw) return "";
  let text = String(raw).replace(/\s+/g, " ").trim();
  text = text.replace(/\s*-{2,}\s*Ed\. note:.*$/i, "").trim();
  const bits = [...text.matchAll(/\((?:Effect|Action):[^)]+\)/gi)]
    .map((m) => m[0].slice(1, -1).trim())
    .filter(Boolean);
  if (bits.length) return bits.join(" ");
  return extra(text);
}

/** Next to the corporation name: every floater the player has, on any card (what Hoverlord counts). */
function playerFloaterChip(p) {
  const holders = [...(p.blueCards || []), ...(p.greenCards || []), ...(p.events || []), ...(p.corpCard ? [p.corpCard] : [])]
    .filter((c) => c.tokenType === "floater");
  const chips = [];
  if (holders.length) {
    const n = holders.reduce((sum, c) => sum + (c.tokens || 0), 0);
    const where = holders.length === 1 ? holders[0].name : `${holders.length} cards`;
    chips.push(tokenChip({ tokenType: "floater", tokens: n }, `${n} floater${n === 1 ? "" : "s"} in all (${where})`));
  }
  // A corporation holding something else (Arklight's animals, Recyclon's microbes) keeps its own chip.
  if (p.corpCard && p.corpCard.tokenType && p.corpCard.tokenType !== "floater") chips.push(tokenChip(p.corpCard));
  return chips.join("");
}

function tokenChip(c, title) {
  if (!c.tokenType && !(c.tokens > 0)) return "";
  const kind = c.tokenType || "token";
  const n = c.tokens ?? 0;
  const label = n === 1 ? kind : kind + "s";
  const tip = escapeHtml(title || `${n} ${label} on this card`);
  if (kind === "floater") {
    // Like the game's floater: a pale cloud on a striped gold square, with the count in the cloud.
    return `<span class="token token-floater${n ? "" : " zero"}" title="${tip}">`
      + `<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M6.6 18.6h11.2a4 4 0 0 0 .5-7.96 5.4 5.4 0 0 0-10.4-1.5 4.75 4.75 0 0 0-1.3 9.46z"/></svg>`
      + `<span class="n">${n}</span></span>`;
  }
  return `<span class="token token-${kind}${n ? "" : " zero"}" title="${tip}">${n}</span>`;
}

/** A player's card section; its header opens or closes that section on every player's board at once. */
function renderCards(title, cards, cls, generation) {
  if (!cards || !cards.length) return "";
  const key = cls;
  const open = !collapsedCardSections.has(key);
  // Blue cards: actions first (what can I still do this generation?), effects after; play order within each.
  const shown = cls === "blue" ? [...cards].sort((a, b) => hasAction(b) - hasAction(a)) : cards;
  return `<h3 class="section-title cards-title">
      <button type="button" class="cards-toggle" data-cards="${key}" aria-expanded="${open}">
        <span>${title}</span><span class="cards-count">${cards.length}</span>${chevron()}
      </button>
    </h3>
    <div class="cards"${open ? "" : " hidden"}>${shown.map((c) => {
      const used = hasAction(c) && generation != null && c.actionUsedGen === generation;
      return `
      <div class="card ${cls === "hand" ? `hand ${escapeHtml(c.color || "")}` : cls}${used ? " used" : ""}"${used ? ` title="Action already used this generation"` : ""}>
        <div class="card-head">
          ${cls === "hand" && c.cost != null ? costSym(c.cost) : ""}
          <h3>${c.name}</h3>
          <span class="card-head-tags">${(c.tags || []).map(tagIcon).join("")}</span>
          ${used ? `<span class="card-used">Used</span>` : ""}
          ${tokenChip(c)}
        </div>
        ${cardGainsLine(c)}
        ${c.extra ? `<p>${extra(c.extra)}</p>` : ""}
      </div>`;
    }).join("")}</div>`;
}

/** Blue cards with an activated ability; the rest are ongoing effects. */
function hasAction(card) {
  return /\bAction\s*:/i.test(card.extra || "");
}

/** Production, resources, tiles, and printed VP; for many cards this is everything they do. */
function cardGainsLine(card) {
  const { gains, places } = cardEffects(card);
  const bits = [...gains];
  if (card.printedVp) bits.push(resSym("vp", card.printedVp, false));
  if (!bits.length && !places.length) return "";
  return `<p class="card-gains">${bits.join("")}${places.length ? `<span class="card-places">${places.join(" · ")}</span>` : ""}</p>`;
}

function helpMark() {
  return `<span class="corp-help" aria-hidden="true"><svg viewBox="0 0 24 24"><circle cx="12" cy="12" r="9" fill="none" stroke="currentColor" stroke-width="2"/><text x="12" y="17" text-anchor="middle" font-size="13" font-weight="700" fill="currentColor">?</text></svg></span>`;
}

function chevron() {
  return `<span class="chev" aria-hidden="true"><svg viewBox="0 0 24 24"><path fill="currentColor" d="M7.4 8.6 12 13.2l4.6-4.6 1.4 1.4-6 6-6-6z"/></svg></span>`;
}

const GLOSSARY = {
  terraformer: "Requires 35 TR",
  mayor: "Requires 3 city tiles",
  gardener: "Requires 3 greenery tiles",
  builder: "Requires 8 building tags",
  planner: "Requires 16 cards in hand",
  diversifier: "Requires 8 different tags",
  tactician: "Requires 5 cards with requirements",
  polarexplorer: "Requires 3 tiles on the two southernmost rows",
  energizer: "Requires 6 energy production",
  rimsettler: "Requires 3 Jovian tags",
  hoverlord: "Requires 7 floaters on your cards",
  generalist: "Requires at least 1 production in each of the 6 types",
  specialist: "Requires 10 production in a single type",
  ecologist: "Requires 4 plant, microbe, or animal tags",
  tycoon: "Requires 15 green or blue project cards",
  legend: "Requires 5 events played",
  landlord: "Most tiles in play at the end of the game.",
  banker: "Highest M€ production at the end of the game.",
  scientist: "Most science tags at the end of the game.",
  thermalist: "Most heat at the end of the game.",
  miner: "Most steel and titanium at the end of the game.",
  cultivator: "Most greenery tiles at the end of the game.",
  magnate: "Most green cards at the end of the game.",
  spacebaron: "Most space tags at the end of the game.",
  excentric: "Most resources on cards at the end of the game.",
  contractor: "Most building tags at the end of the game.",
  celebrity: "Most cards costing 20 M€ or more at the end of the game.",
  industrialist: "Most steel and energy resources at the end of the game.",
  desertsettler: "Most tiles on the four southernmost rows at the end of the game.",
  estatedealer: "Most tiles adjacent to ocean at the end of the game.",
  benefactor: "Highest TR at the end of the game.",
  venuphile: "Most Venus tags at the end of the game.",
  venusphile: "Most Venus tags at the end of the game.",
};

function glossaryKey(name) {
  return String(name || "").toLowerCase().replace(/[^a-z]/g, "");
}

function glossaryText(name) {
  return GLOSSARY[glossaryKey(name)] || "";
}

function tipWrap(name, inner, extraClass, extraHtml, extraPlain) {
  const base = glossaryText(name).replace(/\.$/, "");
  const cls = ["tip", extraClass].filter(Boolean).join(" ");
  if (!base && !extraHtml) {
    return `<span class="${cls}">${inner}</span>`;
  }
  const plain = extraPlain ? (base ? base + "\n" + extraPlain : extraPlain) : base;
  const body = (base ? `<span class="tip-def">${escapeHtml(base)}</span>` : "") + (extraHtml || "");
  return `<span class="${cls}" tabindex="0" data-tip-key="${escapeHtml(glossaryKey(name))}" title="${escapeHtml(plain)}">${inner}<span class="tip-pop" role="tooltip">${body}</span></span>`;
}

let openTipKey = null;
let tipUiBound = false;

function applyOpenTip() {
  document.querySelectorAll(".tip").forEach((el) => {
    el.classList.toggle("open", !!openTipKey && el.dataset.tipKey === openTipKey);
    if (el.classList.contains("open")) placeTip(el);
  });
}

/** Keep a shown tooltip on screen: slide it left from the right edge, and open it below if there's no room above. */
function placeTip(tip) {
  const pop = tip && tip.querySelector(":scope > .tip-pop");
  if (!pop) return;
  pop.style.left = "";
  pop.style.top = "";
  pop.style.bottom = "";
  const edge = 8;
  const r = pop.getBoundingClientRect();
  if (!r.width) return;
  const over = r.right - (document.documentElement.clientWidth - edge);
  if (over > 0) pop.style.left = `${-Math.min(over, Math.max(0, r.left - edge))}px`;
  if (r.top < edge) {
    pop.style.bottom = "auto";
    pop.style.top = "calc(100% + 8px)";
  }
}

function bindTipUi() {
  if (tipUiBound) return;
  tipUiBound = true;
  const place = (ev) => {
    const tip = ev.target.closest && ev.target.closest(".tip");
    if (tip) placeTip(tip);
  };
  document.addEventListener("mouseover", place);
  document.addEventListener("focusin", place);
  document.addEventListener("click", (ev) => {
    const tip = ev.target.closest(".tip");
    if (tip && tip.dataset.tipKey) {
      openTipKey = openTipKey === tip.dataset.tipKey ? null : tip.dataset.tipKey;
      applyOpenTip();
      ev.stopPropagation();
      return;
    }
    if (openTipKey) {
      openTipKey = null;
      applyOpenTip();
    }
  });
}

function renderPlayer(p, generation) {
  const color = teamColor(p);
  const you = !!p.human;
  const subtitle = boardSubtitle(p);
  const title = escapeHtml(boardTitle(p));
  const rules = corpRuleText(p.corpRules);
  const rulesOpen = corpRulesOpen;
  const collapsed = collapsedBoardIds.has(p.id);
  const info = rules
    ? `<button type="button" class="corp-info" data-corp="${p.id}" aria-expanded="${rulesOpen ? "true" : "false"}" aria-controls="corp-rules-${p.id}" aria-label="${title} corporation rules">
        <span class="corp-name">${title}</span>${helpMark()}
      </button>`
    : `<h2 class="corp-name">${title}</h2>`;
  const rulesBlock = rules
    ? `<p class="corp-rules" id="corp-rules-${p.id}"${rulesOpen && !collapsed ? "" : " hidden"}>${escapeHtml(rules)}</p>`
    : "";
  return `<article class="board color-${color}${you ? " you" : ""}${collapsed ? " collapsed" : ""}">
    <div class="board-head">
      ${info}
      ${playerFloaterChip(p)}
      <button type="button" class="board-toggle" data-corp="${p.id}" aria-expanded="${collapsed ? "false" : "true"}" aria-controls="board-body-${p.id}" aria-label="${collapsed ? "Expand" : "Collapse"} ${title}">
        ${chevron()}
      </button>
    </div>
    <div class="board-body" id="board-body-${p.id}"${collapsed ? " hidden" : ""}>
      <div class="board-intro">
        ${subtitle ? `<p class="corp">${escapeHtml(subtitle)}</p>` : ""}
        ${rulesBlock}
      </div>
      ${renderCubes(p)}
      ${renderPlacements(p)}
      ${renderTags(p.tags)}
      ${renderColonySection(p, lastStateData)}
      <p class="board-awards"></p>
      <div class="board-hand">${renderCards("Hand", p.hand, "hand", generation)}</div>
      <div class="board-blues">${renderCards("Blue cards", p.blueCards, "blue", generation)}</div>
      <div class="board-rest">
        ${renderCards("Automated", p.greenCards, "green", generation)}
        ${renderCards("Events", p.events, "red", generation)}
      </div>
    </div>
  </article>`;
}

function renderMilestones(data) {
  const panel = $("milestone-panel");
  const ms = data.milestones || {};
  const funded = data.fundedAwards || (data.score && data.score.fundedAwards) || [];
  if (!panel || (!data.gameId && !(ms.claimed || []).length && !funded.length)) {
    if (panel) panel.hidden = true;
    return;
  }
  panel.hidden = false;
  const claimed = ms.claimed || [];
  const grab = ms.grab || [];
  const close = ms.close || [];
  $("ms-claimed-kicker").textContent = "Claimed";
  setHtml($("ms-claimed"), claimed.length
    ? claimed.map((c) => tipWrap(c.name,
      `<span class="name">${escapeHtml(c.name)}</span>` +
      `<span class="who">${escapeHtml(c.playerName || "")}</span>`,
      `ms-chip color-${escapeHtml(c.color || "blue")}`)).join("")
    : `<p class="ms-empty">None claimed yet</p>`);

  const awardBlock = $("award-block");
  awardBlock.hidden = funded.length === 0;
  setHtml($("ms-awards"), funded.map(awardChip).join(""));

  const grabBox = $("ms-grab");
  const closeBox = $("ms-close");
  grabBox.hidden = grab.length === 0;
  closeBox.hidden = close.length === 0;
  setHtml($("ms-grab-list"), grab.map((row) => milestoneItem(row, true)).join(""));
  setHtml($("ms-close-list"), close.map((row) => milestoneItem(row, false)).join(""));
  applyOpenTip();
}

function awardChip(row) {
  const leaders = row.leaders || [];
  const tied = !!row.tied || leaders.length > 1;
  const color = !tied && (row.color || (leaders[0] && leaders[0].color));
  const dots = tied
    ? `<span class="ms-dots">${leaders.map((p) =>
      `<span class="ms-dot color-${escapeHtml(p.color || "blue")}" title="${escapeHtml(p.name || "")}"></span>`
    ).join("")}</span>`
    : "";
  const cls = ["ms-chip", color ? `color-${color}` : "", tied ? "tied" : ""].filter(Boolean).join(" ");
  return tipWrap(row.name, `<span class="name">${escapeHtml(row.name)}</span>${dots}`, cls, awardLeadHtml(row), awardLeadText(row));
}

function awardLeadText(row) {
  const standings = row.standings || [];
  if (!standings.length) {
    return "";
  }
  return standings.map((p) => `${p.name} ${p.value ?? 0}`).join("  ");
}

function awardLeadHtml(row) {
  const standings = row.standings || [];
  if (!standings.length) {
    return "";
  }
  return `<span class="tip-standings">${standings.map((p) =>
    `<span class="tip-who color-${escapeHtml(p.color || "blue")}"><span class="nm">${escapeHtml(p.name)}</span> ${p.value ?? 0}</span>`
  ).join("")}</span>`;
}

function milestoneItem(row, urgent) {
  const pay = urgent
    ? (row.canPay
      ? `<span class="ms-pay">${row.cost ?? 8} M€</span>`
      : `<span class="ms-pay short">need ${row.cost ?? 8} M€</span>`)
    : "";
  return tipWrap(row.name,
    `<span class="name">${escapeHtml(row.name)}</span>` +
    `<span class="detail">${escapeHtml(row.detail || "")}</span>${pay}`,
    "ms-item");
}

function setLiveStatus(mode) {
  const el = $("live");
  el.classList.toggle("on", mode === "live");
  el.classList.toggle("bad", mode === "disconnected");
  el.textContent = mode === "live" ? "live" : mode === "disconnected" ? "disconnected" : "idle";
}

function placingLabel(raw) {
  if (!raw) return "";
  const s = String(raw).replace(/[_-]+/g, " ").trim();
  const lower = s.toLowerCase();
  if (lower.includes("ocean") || lower === "aquifer") return "Ocean";
  if (lower.includes("greenery") || lower.includes("forest")) return "Greenery";
  if (lower.includes("city") || lower === "capital") return "City";
  if (lower.includes("generic") || lower.includes("special")) return "Special tile";
  return s.replace(/\b\w/g, (c) => c.toUpperCase());
}

/** What the placing badge shows: a tile icon and a name, using the card's own tile when it has one. */
function placingBadge(play) {
  const label = placingLabel(play && play.placing);
  if (!label) return null;
  if (label === "Ocean" || label === "Greenery" || label === "City") return { kind: label.toLowerCase(), label };
  const card = String(play.cardName || "").replace(/^(Placing|Using) /, "").trim();
  const kind = SPECIAL_TILE_KIND[card.toLowerCase()];
  return kind ? { kind, label: card } : { kind: "special", label };
}

function escapeHtml(s) {
  return String(s).replace(/[&<>"']/g, (c) => ({
    "&": "&amp;",
    "<": "&lt;",
    ">": "&gt;",
    '"': "&quot;",
    "'": "&#39;",
  }[c]));
}

const PLANT_LEAF = '<g transform="translate(12 12) rotate(45) scale(.86) translate(-12 -14.1)" fill="#185102"><path d="M12 1.6 20 13.4H4z"/><circle cx="8.1" cy="15.5" r="4.5"/><circle cx="15.9" cy="15.5" r="4.5"/><path d="M11.2 18.1 9.6 26.6h4.8L12.8 18.1z"/></g>';
const TAG_GLYPH = {
  building: '<path fill="#c07340" d="M4 20V9l8-5 8 5v11h-6v-6H10v6z"/>',
  space: '<g fill="#f2d020"><path d="M22.2 12 14.4 10.85 14.4 13.15z"/><path d="M22.2 12 14.4 10.85 14.4 13.15z" transform="rotate(45 12 12)"/><path d="M22.2 12 14.4 10.85 14.4 13.15z" transform="rotate(90 12 12)"/><path d="M22.2 12 14.4 10.85 14.4 13.15z" transform="rotate(135 12 12)"/><path d="M22.2 12 14.4 10.85 14.4 13.15z" transform="rotate(180 12 12)"/><path d="M22.2 12 14.4 10.85 14.4 13.15z" transform="rotate(225 12 12)"/><path d="M22.2 12 14.4 10.85 14.4 13.15z" transform="rotate(270 12 12)"/><path d="M22.2 12 14.4 10.85 14.4 13.15z" transform="rotate(315 12 12)"/><circle cx="12" cy="12" r="2.15"/></g>',
  science: '<g fill="none" stroke="#967b65" stroke-width="1.45"><ellipse cx="12" cy="12" rx="8.4" ry="3.15"/><ellipse cx="12" cy="12" rx="8.4" ry="3.15" transform="rotate(60 12 12)"/><ellipse cx="12" cy="12" rx="8.4" ry="3.15" transform="rotate(-60 12 12)"/></g><circle cx="12" cy="12" r="2.15" fill="#967b65"/>',
  power: '<path fill="#a855f7" d="M13 2 4 14h7l-1 8 10-14h-7z"/>',
  earth: '<g style="clip-path:circle(34.4% at 50% 50%)"><rect width="24" height="24" fill="none"/><g transform="translate(12 12) scale(1.28) translate(-12 -12)" fill="#00d0a0"><path d="M2.3 9.1 4 9.1 7.4 10.7 9.1 12.4 8.2 14.1 9.1 16.6 10.7 19.1 9.9 20 6.5 19.1 4 16.6 2.3 14.1 2.3 10.7Z"/><path d="M11.6 4 13.3 4 14.9 4.9 16.6 6.5 17.5 8.2 18.3 9.9 18.3 11.6 18.3 13.3 17.5 14.9 16.6 16.6 14.9 18.3 15.8 16.6 15.8 14.9 14.1 13.3 13.3 11.6 11.6 10.7 9.9 10.7 10.7 9.1 12.4 9.1 13.3 7.4 13.3 6.5 14.1 4.9 12.4 4Z"/></g></g><circle cx="12" cy="12" r="8.8" fill="none" stroke="#00d0a0" stroke-width="1.15"/>',
  jovian: '<g transform="rotate(-8 12 12)"><circle cx="12" cy="12" r="10.4" fill="#c09000"/><rect x="-2" y="6.8" width="28" height="1.7" fill="#080604"/><rect x="-2" y="15.2" width="28" height="3.4" fill="#080604"/></g>',
  plant: '<g transform="translate(12 12) rotate(45) scale(.86) translate(-12 -14.1)" fill="#8ced05"><path d="M12 1.6 20 13.4H4z"/><circle cx="8.1" cy="15.5" r="4.5"/><circle cx="15.9" cy="15.5" r="4.5"/><path d="M11.2 18.1 9.6 26.6h4.8L12.8 18.1z"/></g>',
  microbe: '<circle cx="9.6" cy="12" r="4" fill="#98c828"/><path d="M13.6 6.2 18.2 10.2" fill="none" stroke="#98c828" stroke-width="2.5" stroke-linecap="round"/><path d="M17.5 13.5 11.4 18.8" fill="none" stroke="#98c828" stroke-width="2.6" stroke-linecap="round"/>',
  animal: '<g fill="#00e040"><circle cx="6.6" cy="6.8" r="1.85"/><circle cx="10.2" cy="4.7" r="1.85"/><circle cx="14.2" cy="4.7" r="1.85"/><circle cx="17.6" cy="7" r="1.75"/><rect x="6.3" y="11.6" width="11.6" height="6.4" rx="3.2"/></g>',
  city: '<g fill="#b2d8d6"><path d="M1.2 10.8A10.8 10.6 0 0 0 22.8 10.8Z"/><rect x="4.8" y="8" width="2.6" height="3.2"/><rect x="10.2" y="2" width="3.6" height="9.2"/><rect x="16.2" y="5.2" width="2.8" height="6"/></g>',
  event: '<path fill="#f2d020" d="M8 3h8v8.5h4L12 22 4 11.5h4z"/>',
  venus: '<g style="clip-path:circle(46% at 50% 50%)" fill="#2aa7e6" opacity=".14"><rect x="0" y="3" width="24" height=".7"/><rect x="0" y="5" width="24" height=".7"/><rect x="0" y="7" width="24" height=".7"/><rect x="0" y="9" width="24" height=".7"/><rect x="0" y="11" width="24" height=".7"/><rect x="0" y="13" width="24" height=".7"/><rect x="0" y="15" width="24" height=".7"/><rect x="0" y="17" width="24" height=".7"/><rect x="0" y="19" width="24" height=".7"/><rect x="0" y="21" width="24" height=".7"/></g><path fill="#2aa7e6" d="M4.6 4.4h4.3L12 14.6l3.1-10.2h4.3L14.2 19.6h-4.4z"/><path fill="none" stroke="#8fdcff" stroke-width=".6" stroke-linejoin="round" d="M5.6 5.1h2.8L12 16.8l3.6-11.7h2.8"/>',
  wild: '<path fill="currentColor" d="M12 3 14 9h6l-5 3.6L17 19l-5-3.4L7 19l2-6.4L4 9h6z"/>',
  mars: '<circle cx="10" cy="13" r="6" fill="none" stroke="currentColor" stroke-width="1.8"/><path fill="none" stroke="currentColor" stroke-width="1.8" d="M14 9 20 3M15 3h5v5"/>',
  moon: '<path fill="currentColor" d="M14 4a8 8 0 1 0 6 12 7 7 0 0 1-6-12z"/>',
  none: '<path fill="none" stroke="#080604" stroke-width="2.2" stroke-linecap="round" d="M5.6 5.6 18.4 18.4M18.4 5.6 5.6 18.4"/>',
};
const TAG_LABEL = { none: "No tag" };

function tagIcon(tag) {
  const key = String(tag || "").toLowerCase();
  const glyph = TAG_GLYPH[key];
  const cls = TAG_GLYPH[key] ? key : "unknown";
  const inner = glyph || `<text x="12" y="16" text-anchor="middle" font-size="11" fill="currentColor">${escapeHtml(key.slice(0, 1).toUpperCase())}</text>`;
  return `<span class="sym tag-sym tag-${cls}" title="${escapeHtml(TAG_LABEL[key] || key)}"><svg viewBox="0 0 24 24">${inner}</svg></span>`;
}

function costSym(n) {
  return `<span class="sym cost-sym" title="Cost ${n} M€">${n}</span>`;
}

function resKey(raw) {
  const k = String(raw || "").toLowerCase();
  if (k === "titanium" || k === "ti") return "ti";
  if (k === "megacredit" || k === "mc" || k === "mé" || k === "m€") return "mc";
  return k;
}

function resLabel(key) {
  return ({
    mc: "M€",
    steel: "steel",
    ti: "titanium",
    plant: "plant",
    energy: "energy",
    heat: "heat",
    tr: "TR",
    o2: "O₂",
    temp: "temp",
    ocean: "ocean",
    card: "card",
    vp: "VP",
  })[key] || key;
}

const TI_STAR = '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="currentColor" d="M12 2.2 14.7 8.6h6.8l-5.5 4.1 2.1 6.7L12 15.8 5.9 19.4l2.1-6.7L2.5 8.6h6.8z"/></svg>';
const RES_ICONS = {
  mc: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="8" fill="none" stroke="currentColor" stroke-width="2"/><path fill="currentColor" d="M7.2 16V8h1.5l3.3 5.2L15.3 8H16.8v8h-1.6v-5.1L12 14.2l-3.2-4.3V16z"/></svg>',
  steel: '<svg viewBox="0 0 24 24" aria-hidden="true"><g transform="translate(12 12) rotate(-30) translate(-9 -12.3)"><path fill="currentColor" d="M2.2 3.6h13.6v4.8H12v12.6H6.4V8.4H2.2z"/></g></svg>',
  ti: TI_STAR,
  plant: '<svg viewBox="0 0 24 24" aria-hidden="true">' + PLANT_LEAF + '</svg>',
  energy: '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="#fff" d="M13 2 4 14h7l-1 8 10-14h-7z"/></svg>',
  heat: '<svg viewBox="0 0 24 24" aria-hidden="true"><path fill="none" stroke="#ffe08a" stroke-width="1.7" stroke-linecap="round" d="M5 2.5c2.6 2.2 2.6 3.8 0 6s-2.6 3.8 0 6 2.6 3.8 0 6M12 2.5c2.6 2.2 2.6 3.8 0 6s-2.6 3.8 0 6 2.6 3.8 0 6M19 2.5c2.6 2.2 2.6 3.8 0 6s-2.6 3.8 0 6 2.6 3.8 0 6"/></svg>',
  // Global parameters, after the game's icons: O₂ as two red atoms on yellow, ocean as a water drop, a thermometer.
  o2: '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="8" cy="14" r="7.6" fill="#d23a22" stroke="#7a1a0c" stroke-width="1"/><circle cx="16" cy="10" r="7.6" fill="#e5532f" stroke="#7a1a0c" stroke-width="1"/><path fill="none" stroke="#f7d35c" stroke-width="1.6" stroke-linecap="round" d="M10.16 8.61A6 6 0 0 0 13.61 15.51"/></svg>',
  ocean: '<svg viewBox="0 0 24 24" aria-hidden="true"><defs><linearGradient id="@ID@" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#ffffff"/><stop offset=".45" stop-color="#b9e3ff"/><stop offset="1" stop-color="#3d9ce8"/></linearGradient></defs><path fill="url(#@ID@)" stroke="#d6efff" stroke-width=".6" d="M12 2.5C9.2 7 6 10.6 6 14.4a6 6 0 0 0 12 0C18 10.6 14.8 7 12 2.5z"/><path fill="none" stroke="#fff" stroke-width="1.4" stroke-linecap="round" d="M9.9 8.6C8.9 10.3 8.1 11.9 8.1 13.6"/></svg>',
  temp: '<svg viewBox="0 0 24 24" aria-hidden="true"><defs><linearGradient id="@ID@" gradientUnits="userSpaceOnUse" x1="0" y1="21.9" x2="0" y2="2"><stop offset="0" stop-color="#1f9ca8"/><stop offset=".25" stop-color="#1f9ca8"/><stop offset=".38" stop-color="#4a82c4"/><stop offset=".5" stop-color="#8a5fd0"/><stop offset=".68" stop-color="#d8306e"/><stop offset="1" stop-color="#ff3b1f"/></linearGradient></defs><path fill="url(#@ID@)" stroke="#9fd8ff" stroke-width="1.2" d="M9.5 4.5a2.5 2.5 0 0 1 5 0v9.2a4.5 4.5 0 1 1-5 0z"/><path fill="none" stroke="#9fd8ff" stroke-width=".8" stroke-linecap="round" d="M15.6 6h1.4M15.6 9h1.4M15.6 12h1.4"/><path fill="none" stroke="#fff" stroke-opacity=".7" stroke-width=".8" stroke-linecap="round" d="M10.9 16.1a1.9 1.9 0 0 0-.4 1.6"/></svg>',
  // A card to draw, after the game's icon: an orange-edged card back with Mars on it.
  card: '<svg viewBox="0 0 24 24" aria-hidden="true"><defs><radialGradient id="@ID@" cx=".38" cy=".35" r=".7"><stop offset="0" stop-color="#f0a060"/><stop offset=".55" stop-color="#c4502a"/><stop offset="1" stop-color="#5e1c0e"/></radialGradient></defs><rect x="4.6" y="1.2" width="14.8" height="21.6" rx="2" fill="#1b1722" stroke="#f39a2b" stroke-width="1.3"/><circle cx="12" cy="12" r="4.8" fill="url(#@ID@)"/><g fill="#fff" opacity=".8"><circle cx="7.6" cy="4.6" r=".4"/><circle cx="16.6" cy="5.4" r=".35" fill="#ffc48a" opacity=".75"/><circle cx="16.2" cy="19.2" r=".4"/></g><circle cx="17.9" cy="13.6" r=".45" fill="#fff" opacity=".6"/></svg>',
  // Terraform rating, after the game's icon: gridded globe with the Mars arrow in a laurel wreath, "TM" below,
  // on the orange .res-tr box. Generated by scripts/generate-tr-icon.py.
  tr: '<svg viewBox="0 0 24 24" aria-hidden="true"><g fill="none" stroke="#1a0f08" stroke-width="1.1"><circle cx="12" cy="10.4" r="5"/><ellipse cx="12" cy="10.4" rx="2.05" ry="5"/><path d="M7 10.4h10M7.8 7.85h8.4M7.8 12.95h8.4"/></g><path fill="none" stroke="#1a0f08" stroke-width="1.25" stroke-linecap="round" d="M15.54 6.86L17.44 4.96"/><path fill="#1a0f08" stroke="#1a0f08" stroke-width=".4" stroke-linejoin="round" d="M18.79 3.61L18.19 6.12L16.28 4.21Z"/><path fill="none" stroke="#1a0f08" stroke-width=".6" stroke-linecap="round" d="M8.10 18.13 6.27 16.80 4.86 15.03 3.98 12.95 3.70 10.70 4.03 8.47M15.90 18.13 17.73 16.80 19.14 15.03 20.02 12.95 20.30 10.70 19.97 8.47"/><path fill="#1a0f08" d="M6.27 16.80Q5.22 15.75 3.98 16.58Q5.03 17.64 6.27 16.80ZM6.27 16.80Q7.04 15.53 5.94 14.53Q5.16 15.80 6.27 16.80ZM4.86 15.03Q4.14 13.73 2.72 14.20Q3.44 15.50 4.86 15.03ZM4.86 15.03Q5.95 14.01 5.15 12.75Q4.07 13.77 4.86 15.03ZM3.98 12.95Q3.64 11.50 2.15 11.56Q2.49 13.01 3.98 12.95ZM3.98 12.95Q5.31 12.26 4.88 10.83Q3.56 11.52 3.98 12.95ZM3.70 10.70Q3.76 9.21 2.31 8.87Q2.25 10.37 3.70 10.70ZM3.70 10.70Q5.16 10.40 5.14 8.91Q3.68 9.21 3.70 10.70ZM4.03 8.47Q4.49 7.05 3.19 6.33Q2.73 7.75 4.03 8.47ZM4.03 8.47Q5.52 8.57 5.90 7.12Q4.42 7.02 4.03 8.47ZM4.03 8.47Q5.27 7.63 4.68 6.26Q3.45 7.10 4.03 8.47ZM17.73 16.80Q18.97 17.64 20.02 16.58Q18.78 15.75 17.73 16.80ZM17.73 16.80Q18.84 15.80 18.06 14.53Q16.96 15.53 17.73 16.80ZM19.14 15.03Q20.56 15.50 21.28 14.20Q19.86 13.73 19.14 15.03ZM19.14 15.03Q19.93 13.77 18.85 12.75Q18.05 14.01 19.14 15.03ZM20.02 12.95Q21.51 13.01 21.85 11.56Q20.36 11.50 20.02 12.95ZM20.02 12.95Q20.44 11.52 19.12 10.83Q18.69 12.26 20.02 12.95ZM20.30 10.70Q21.75 10.37 21.69 8.87Q20.24 9.21 20.30 10.70ZM20.30 10.70Q20.32 9.21 18.86 8.91Q18.84 10.40 20.30 10.70ZM19.97 8.47Q21.27 7.75 20.81 6.33Q19.51 7.05 19.97 8.47ZM19.97 8.47Q19.58 7.02 18.10 7.12Q18.48 8.57 19.97 8.47ZM19.97 8.47Q20.55 7.10 19.32 6.26Q18.73 7.63 19.97 8.47Z"/><text x="12" y="19.5" text-anchor="middle" font-family="Arial, Helvetica, sans-serif" font-size="3.3" font-weight="700" fill="#1a0f08">TM</text></svg>',
};

let resIconSeq = 0;

function resIcon(key) {
  // Gradient ids must be unique per copy of an icon on the page.
  const svg = RES_ICONS[key] || "";
  return svg.includes("@ID@") ? svg.replace(/@ID@/g, `res-grad-${++resIconSeq}`) : svg;
}

function resSym(rawKey, value, prod) {
  const key = resKey(rawKey);
  const n = Number(value);
  const shown = Number.isFinite(n) ? (n > 0 ? "+" + n : String(n)) : String(value);
  const mark = resIcon(key);
  return `<span class="res-sym res-${key}${prod ? " prod" : ""}" title="${prod ? "Production " : ""}${shown} ${resLabel(key)}"><span class="res-box">${mark}</span>${shown}</span>`;
}

function benefitHtml(play) {
  const bits = [];
  for (const [k, v] of Object.entries(play.resources || {})) {
    if (v != null && v !== 0) bits.push(resSym(k, v, false));
  }
  for (const [k, v] of Object.entries(play.production || {})) {
    if (v != null && v !== 0) bits.push(resSym(k, v, true));
  }
  if (play.vp) bits.push(resSym("vp", play.vp, false));
  if (bits.length) return bits.join("");
  if (play.effect) {
    const text = play.effect.length > 110 ? play.effect.slice(0, 107) + "…" : play.effect;
    return `<span class="banner-benefit-text">${escapeHtml(text)}</span>`;
  }
  return "";
}

function applyBannerOpen() {
  $("banner-details").hidden = !bannerOpen;
  $("banner-toggle").setAttribute("aria-expanded", bannerOpen ? "true" : "false");
  const benefit = $("banner-benefit");
  benefit.hidden = benefit.innerHTML.trim().length === 0;
}

function bindBannerUi() {
  if (bannerUiBound) return;
  bannerUiBound = true;
  $("banner-toggle").addEventListener("click", (ev) => {
    if ($("banner-toggle").disabled) return;
    if (ev.target.closest(".tip")) return;
    bannerOpen = !bannerOpen;
    saveSections();
    applyBannerOpen();
  });
  $("game-log").addEventListener("click", (ev) => {
    const btn = ev.target.closest("[data-log-filter]");
    if (!btn) return;
    logFilter = btn.dataset.logFilter === "" ? null : Number(btn.dataset.logFilter);
    if (lastLogData) renderGameLog(lastLogData);
  });
}

function formatReq(req) {
  if (!req || typeof req !== "object") return "";
  const parts = [];
  if (req.temp != null) parts.push(`${req.temp} °C`);
  if (req.o2 != null) parts.push(`${req.o2}% oxygen`);
  if (req.ocean != null) parts.push(`${req.ocean} ocean${Number(req.ocean) === 1 ? "" : "s"}`);
  if (req.venus != null) parts.push(`${req.venus}% Venus`);
  if (req.tr != null) parts.push(`${req.tr} TR`);
  if (req.note) parts.push(req.note);
  for (const [k, v] of Object.entries(req)) {
    if (["temp", "o2", "ocean", "venus", "tr", "note"].includes(k) || v == null || v === "") continue;
    parts.push(`${k} ${v}`);
  }
  return parts.length ? "Requires " + parts.join(", ") : "";
}

function drawnGainsText(c) {
  const bits = [];
  for (const [k, v] of Object.entries(c.production || {})) {
    if (v != null && v !== 0) bits.push(`${Number(v) > 0 ? "+" : ""}${v} ${resLabel(resKey(k))} prod`);
  }
  for (const [k, v] of Object.entries(c.resources || {})) {
    if (v != null && v !== 0) bits.push(`${Number(v) > 0 ? "+" : ""}${v} ${resLabel(resKey(k))}`);
  }
  if (c.vp != null) bits.push(`${c.vp} VP`);
  return bits.join(" · ");
}

function drawnTipText(c) {
  const extraText = (c.extra || "").replace(/\s+$/g, "").trim();
  if (extraText) return extraText;
  return [formatReq(c.req), drawnGainsText(c)].filter(Boolean).join("\n");
}

function renderDrawn(play) {
  const drawn = play && play.drawn ? play.drawn : [];
  if (!drawn.length) return "";
  return `<span class="banner-drawn"><span class="drawn-label">Drew</span>${drawn.map((c) => {
    const inner = `<span class="drawn-name">${escapeHtml(c.name)}</span>
      <span class="drawn-meta">${c.cost != null ? costSym(c.cost) : ""}${(c.tags || []).map(tagIcon).join("")}</span>`;
    const cls = `drawn-card ${escapeHtml((c.color || "").toLowerCase())}`;
    const text = drawnTipText(c);
    if (!text) {
      return `<span class="${cls}">${inner}</span>`;
    }
    return tipWrap("drawn-" + c.name, inner, cls, `<span class="tip-def">${escapeHtml(text)}</span>`, text);
  }).join("")}</span>`;
}

/**
 * While you confirm a card: would paying for it (the game fills a short M€ payment with steel, titanium,
 * or Helion's heat on its own) cost you the lead in a funded award that counts those resources?
 */
function awardSpendWarnings(data, play) {
  if (!play || !play.yours || !play.preview || play.cost == null) return [];
  const you = data.you || {};
  const out = [];
  const tags = play.tags || [];
  const check = (awardKey, mine, spend, what) => {
    const award = (data.fundedAwards || []).find((a) => glossaryKey(a.name) === awardKey);
    if (!award || spend <= 0) return;
    const rivals = (award.standings || []).filter((s) => s.id !== you.id);
    if (!rivals.length) return;
    const best = rivals.reduce((a, b) => (b.value > a.value ? b : a));
    const after = mine - spend;
    // Only when this payment is what puts you behind: you're level or ahead now, and below them after.
    if (mine < best.value || after >= best.value) return;
    out.push(`${award.name}: paying with ${what} drops you from ${mine} to ${after}, behind ${best.name} (${best.value}). Pay in M€ if you can.`);
  };
  // Pressing Use logs exactly what the game will take; without that, assume the most it could take.
  const pay = play.payment && Object.keys(play.payment).length ? play.payment : null;
  const tiValue = /phobo/i.test(you.corporation || "") ? 4 : 3;
  const steel = pay ? (pay.steel || 0) : tags.includes("building") ? Math.min(you.steel || 0, Math.ceil(play.cost / 2)) : 0;
  const ti = pay ? (pay.titanium || 0) : tags.includes("space") ? Math.min(you.titanium || 0, Math.ceil(play.cost / tiValue)) : 0;
  const heat = pay ? (pay.heat || 0) : /helion/i.test(you.corporation || "") ? Math.min(you.heat || 0, play.cost) : 0;
  const onCards = pay ? (pay.microbe || 0) + (pay.floater || 0) : 0;
  const parts = [steel && `${steel} steel`, ti && `${ti} titanium`].filter(Boolean).join(" and ");
  check("miner", (you.steel || 0) + (you.titanium || 0), steel + ti, parts);
  check("thermalist", you.heat || 0, heat, `${heat} heat`);
  const held = [...(you.blueCards || []), ...(you.greenCards || []), ...(you.corpCard ? [you.corpCard] : [])]
    .reduce((sum, c) => sum + (c.tokens || 0), 0);
  check("excentric", held, onCards, [pay && pay.microbe && `${pay.microbe} microbes`, pay && pay.floater && `${pay.floater} floaters`]
    .filter(Boolean).join(" and "));
  return out;
}

/** "This payment uses 4 steel": what the game took besides M€ when you pressed Use. */
function paymentTips(play) {
  if (!play || !play.yours || !play.preview || !play.payment) return [];
  const words = { steel: "steel", titanium: "titanium", heat: "heat", microbe: "microbes", floater: "floaters" };
  const parts = Object.entries(play.payment).filter(([, n]) => n > 0)
    .map(([what, n]) => `${n} ${n === 1 ? what : words[what] || what}`);
  return parts.length ? [`This payment uses ${parts.join(" and ")}.`] : [];
}

function renderBanner(data) {
  const play = data.activePlay;
  const banner = $("banner");
  banner.classList.remove("yours", "color-blue", "color-green", "color-purple", "color-yellow", "color-red", "color-black");
  const log = data.log || [];
  let tips = [];
  let warnings = [];
  const placing = placingBadge(play);
  const placingEl = $("banner-placing");
  if (placing) {
    placingEl.hidden = false;
    placingEl.innerHTML = `${tileIcon(placing.kind)}<span>Placing ${escapeHtml(placing.label)}</span>`;
  } else {
    placingEl.hidden = true;
    placingEl.textContent = "";
  }

  if (play && play.cardName) {
    const playColor = play.playerColor || (play.yours ? teamColor(data.you) : teamColor((data.opponents || [])[0]));
    banner.classList.add("color-" + (playColor || "blue"));
    if (play.yours) banner.classList.add("yours");
    const who = (play.yours && accountName(data.you)) || play.playerLabel;
    $("banner-kicker").textContent = `${who}${play.colorLabel ? " · " + play.colorLabel : ""}`;
    setHtml($("banner-line"), `<span class="banner-name">${escapeHtml(play.cardName)}</span>${
      play.cost != null ? costSym(play.cost) : ""
    }${(play.tags || []).map(tagIcon).join("")}${renderDrawn(play)}`);
    setHtml($("banner-benefit"), benefitHtml(play));
    warnings = awardSpendWarnings(data, play);
    tips = [...paymentTips(play), ...(play.remember || [])];
  } else {
    $("banner-kicker").textContent = "No card in flight";
    setHtml($("banner-line"), log.length
      ? `<span class="banner-log-summary">Game log · ${log.length} move${log.length === 1 ? "" : "s"}</span>`
      : "");
    setHtml($("banner-benefit"), "");
  }
  // Placement tips stay visible under the header; they aren't behind the arrow.
  setHtml($("banner-tips"), warnings.map((t) => `<li class="warn">${escapeHtml(t)}</li>`).join("")
    + tips.map((t) => `<li>${escapeHtml(t)}</li>`).join(""));
  $("banner-tips").hidden = tips.length + warnings.length === 0;
  // The arrow always opens the game log, whether or not a card is in flight.
  $("banner-toggle").disabled = log.length === 0;
  $("banner-chevron").hidden = log.length === 0;
  $("game-log").hidden = log.length === 0;
  lastLogData = data;
  renderGameLog(data);
  applyBannerOpen();
}

let logFilter = null;
let lastLogData = null;

function prettyName(name) {
  return String(name).replace(/([a-z])([A-Z])/g, "$1 $2");
}

function logWhat(e) {
  switch (e.kind) {
    case "card":
      return `played <span class="log-card ${escapeHtml(e.color)}"></span>${cardRef(e.name)}`;
    case "action":
      return `used ${cardRef(e.name)}`;
    case "corp-action":
      return `used ${escapeHtml(e.name)}`;
    case "project":
      return `standard project: ${escapeHtml(prettyName(e.name))}`;
    case "convert":
      return /heat/i.test(e.name) ? "converted heat into temperature" : "converted plants into a greenery";
    case "milestone":
      return `claimed <strong>${escapeHtml(e.name)}</strong>`;
    case "award":
      return `funded <strong>${escapeHtml(e.name)}</strong>`;
    case "colony":
      return `built a colony on <strong>${escapeHtml(e.name)}</strong>${e.detail ? ` (${escapeHtml(e.detail)})` : ""}`;
    case "trade":
      return `traded with <strong>${escapeHtml(e.name)}</strong>${e.detail ? ` (${escapeHtml(e.detail)})` : ""}`;
    case "bonus":
      return `got the <strong>${escapeHtml(e.name)}</strong> colony bonus${e.detail ? ` (${escapeHtml(e.detail)})` : ""}`;
    default:
      return escapeHtml(e.name);
  }
}

/** "3 floaters", "1 floater": counts for colony payouts, whichever form the tile data uses. */
function colonyAmount(n, word) {
  const w = String(word || "");
  const countable = /^(floater|microbe|animal|plant|card)s?\b/.test(w);
  if (!countable) return `${n} ${w}`;
  const one = w.replace(/^(\w+?)s\b/, "$1");
  return `${n} ${n === 1 ? one : one.replace(/^(\w+)/, "$1s")}`;
}

/** "floaters", "floater", "M€ production" → one key per kind of payout. */
function colonyKind(resource) {
  const r = String(resource || "").toLowerCase();
  if (r.includes("production")) return "mcprod";
  if (r.startsWith("m€")) return "mc";
  if (r.startsWith("card")) return "card";
  return r.replace(/s$/, "");
}

/**
 * Rough M€ worth of one unit, for picking the best trade. Floaters, microbes and animals are worth
 * nothing unless you have a card that holds them (the game drops them otherwise).
 */
function colonyUnitValue(kind, you) {
  const holds = (type) => [...(you.blueCards || []), ...(you.greenCards || []), ...(you.corpCard ? [you.corpCard] : [])]
    .some((c) => c.tokenType === type);
  switch (kind) {
    case "mc": return 1;
    case "steel": return 2;
    case "titanium": return 3;
    case "plant": return 1.5;
    case "energy": return 1;
    case "heat": return 1;
    case "card": return 3;
    case "mcprod": return 4;
    case "floater": case "microbe": case "animal": return holds(kind) ? 1.5 : 0;
    default: return 1;
  }
}

/**
 * A player's Colonies section, between their tags and blue cards: their colonies and trade fleets,
 * then (collapsible) every tile's track and what a trade would give this player right now.
 */
function renderColonySection(p, data) {
  const tiles = coloniesOn && data ? (data.colonyTiles || []) : [];
  if (!coloniesOn) return "";
  const counts = new Map();
  for (const tile of p.colonies || []) counts.set(tile, (counts.get(tile) || 0) + 1);
  const chips = [...counts].map(([tile, n]) =>
    `<span class="colony-chip">${escapeHtml(tile)}${n > 1 ? ` ×${n}` : ""}</span>`);
  const fleets = p.tradeFleets ?? 1;
  const used = Math.min(p.fleetsUsed ?? 0, fleets);
  const fleetText = `${fleets} trade fleet${fleets === 1 ? "" : "s"}${used ? ` · ${used} out this generation` : ""}`;
  const mineLine = `<div class="colony-list">${chips.length ? chips.join("") : `<span class="colony-none">No colonies yet</span>`}`
    + `<span class="colony-fleets">${fleetText}</span></div>`;

  const byId = new Map(tablePlayers(data).map((pl) => [pl.id, pl]));
  const rows = tiles.map((t) => {
    const now = (t.track || [])[t.position];
    const mine = (t.owners || []).filter((id) => id === p.id).length;
    const tradeable = t.active && !t.fleetHere && now != null;
    const value = tradeable
      ? now * colonyUnitValue(colonyKind(t.resource), p) + mine * (t.bonus || 0) * colonyUnitValue(colonyKind(t.bonusResource), p)
      : -1;
    return { t, now, mine, tradeable, value };
  });
  const best = rows.filter((r) => r.tradeable && r.value > 0).sort((a, b) => b.value - a.value)[0];
  const who = p.human ? "you" : displayName(p, "them");
  const bestText = best ? `Best for ${who}: ${best.t.name}, +${colonyAmount(best.now, best.t.resource)}` : "";
  const body = rows.map((r) => {
    const t = r.t;
    const steps = (t.track || []).map((v, i) => {
      const owner = byId.get((t.owners || [])[i]);
      const slot = i < 3 && owner ? `<span class="slot color-${teamColor(owner)}" title="${escapeHtml(displayName(owner, ""))}'s colony"></span>` : "";
      return `<span class="colony-step${i === t.position ? " here" : ""}">${v}${slot}</span>`;
    }).join("");
    let status;
    if (!t.active) {
      status = `<span class="extra">Inactive until someone plays a card that holds ${escapeHtml(t.activatedBy || "")}s</span>`;
    } else if (t.fleetHere) {
      status = `<span class="extra">A trade fleet is here until the Solar phase</span>`;
    } else {
      const own = r.mine ? ` <span class="extra">+ ${p.human ? "your" : "their"} colony bonus ${escapeHtml(colonyAmount(r.mine * t.bonus, t.bonusResource))}</span>` : "";
      status = `Trade now: <span class="gain">+${escapeHtml(colonyAmount(r.now, t.resource))}</span>${own}`;
    }
    return `<div class="colony-row${r === best ? " best" : ""}${t.active ? "" : " off"}">
        <span class="colony-name">${escapeHtml(t.name)}<small>bonus ${escapeHtml(colonyAmount(t.bonus, t.bonusResource))}</small></span>
        <span class="colony-track">${steps}</span>
        <span class="colony-trade">${status}${r === best
          ? `<span class="best-tag" title="Highest rough M€ value; floaters, microbes and animals count only with a card to hold them">Best for ${escapeHtml(who)}</span>`
          : ""}</span>
      </div>`;
  }).join("");

  return `<div class="board-colonies">
      <h3 class="section-title board-subtitle">
        <button type="button" class="colony-toggle" aria-expanded="${colonyOpen}">
          <span>Colonies</span>${colonyOpen ? "" : `<span class="colony-summary">${escapeHtml(bestText)}</span>`}${chevron()}
        </button>
      </h3>
      ${mineLine}
      ${tiles.length ? `<div class="colony-rows"${colonyOpen ? "" : " hidden"}>${body}</div>` : ""}
    </div>`;
}

function loadColonyOpen() {
  try {
    return localStorage.getItem("colonyPanelOpen") !== "false";
  } catch {
    return true;
  }
}

/** One switch for every board's colony tracks, like the card sections. */
function toggleColonies() {
  colonyOpen = !colonyOpen;
  try {
    localStorage.setItem("colonyPanelOpen", String(colonyOpen));
  } catch {
    // Private windows can refuse storage; the tracks still fold for this visit.
  }
  if (lastStateData) render(lastStateData);
}

/** Cards this move drew (yours only; opponents' draws would reveal their hand). */
function logDrew(e) {
  const drawn = e.drawn || [];
  if (!drawn.length) return "";
  return ` <span class="log-drew">· drew ${drawn.map((c) =>
    `<span class="log-drawn-card">${cardPopover(c, c.name)}</span>`).join(", ")}</span>`;
}

/** Every move so far, newest first, grouped by generation; filterable to one player. */
function renderGameLog(data) {
  const players = tablePlayers(data);
  const byId = new Map(players.map((p) => [p.id, p]));
  indexChartCards(players);
  beginCardRefs("logcard");
  const log = (data.log || []).filter((e) => logFilter == null || e.playerId === logFilter);
  const filters = [`<button type="button" class="log-filter${logFilter == null ? " on" : ""}" data-log-filter="">All</button>`]
    .concat(players.map((p) =>
      `<button type="button" class="log-filter color-${teamColor(p)}${logFilter === p.id ? " on" : ""}" data-log-filter="${p.id}">${escapeHtml(displayName(p, "P" + p.id))}</button>`));
  const groups = [];
  for (let i = log.length - 1; i >= 0; i--) {
    const e = log[i];
    if (!groups.length || groups[groups.length - 1].gen !== e.generation) groups.push({ gen: e.generation, rows: [] });
    const p = byId.get(e.playerId);
    groups[groups.length - 1].rows.push(`<li class="log-entry color-${p ? teamColor(p) : "blue"}">
        <span class="log-who">${escapeHtml(p ? displayName(p, "P" + p.id) : "P" + e.playerId)}</span> ${logWhat(e)}${logDrew(e)}
      </li>`);
  }
  setHtml($("game-log"), `<div class="log-filters">${filters.join("")}</div>`
    + (groups.length
      ? groups.map((g) => `<h4 class="log-gen">Generation ${g.gen}</h4><ol class="log-list">${g.rows.join("")}</ol>`).join("")
      : `<p class="log-empty">No moves yet.</p>`));
}

function render(data) {
  if (data.gameId !== lastGameId) {
    lastGameId = data.gameId || "";
    logFilter = null;
    selectedGen = null;
  }
  coloniesOn = !!data.colonies;
  lastStateData = data;
  const started = startedLabel(data.startedAt);
  $("meta").textContent = data.gameId
    ? [`Gen ${data.generation ?? "?"}`, data.phase, data.board, started && `Started ${started}`].filter(Boolean).join(" · ")
    : "No game yet";
  const update = $("update");
  if (data.updateAvailable) {
    update.hidden = false;
    update.textContent = "Update " + data.updateAvailable + " available";
  } else {
    update.hidden = true;
    update.textContent = "";
  }
  setLiveStatus(data.live ? "live" : "idle");
  if (data.url) {
    $("qr-link").href = data.url;
    $("qr-link").title = data.url;
    $("qr-url").textContent = data.url.replace(/^https?:\/\//, "").replace(/\/$/, "");
  }
  renderPhoneAccess(data);

  renderBanner(data);
  renderMilestones(data);

  const players = tablePlayers(data);
  const boards = $("boards");
  boards.className = "boards players-" + Math.max(1, Math.min(5, players.length));
  setHtml(boards, players.map((p) => renderPlayer(p, data.generation)).join(""));
  renderScore(data, players);
  alignBoardSections();
  applyOpenTip();
}

/** Only this PC can raise the Windows admin prompt; a phone that got here doesn't need the button. */
function onThisPc() {
  return ["127.0.0.1", "localhost", "[::1]"].includes(location.hostname);
}

function renderPhoneAccess(data) {
  const blocked = data.firewallOpen === false;
  $("qr-link").classList.toggle("blocked", blocked);
  $("phone-blocked").hidden = !blocked;
  $("phone-fix").hidden = !onThisPc();
}

function bindPhoneAccessUi() {
  const btn = $("phone-fix");
  btn.addEventListener("click", async () => {
    btn.disabled = true;
    btn.textContent = "Answer the Windows prompt…";
    let open = false;
    try {
      const res = await fetch("/api/firewall/fix", { method: "POST" });
      open = res.ok && (await res.json()).open === true;
    } catch {
      open = false;
    }
    btn.disabled = false;
    btn.textContent = open ? "Allow phones" : "Still blocked — try again";
    lastStateText = "";
    tick();
  });
}

function scoreOf(data, p) {
  const byId = (data.score && data.score.byId) || {};
  return byId[p.id] || byId[String(p.id)] || (p.human ? data.score?.you : {}) || {};
}

function creditsOf(p, b) {
  return b.mc ?? p.megaCredits ?? 0;
}

function renderScore(data, players) {
  const box = $("scoreboard");
  const score = data.score;
  if (!score || !players.length) {
    box.hidden = true;
    return;
  }
  box.hidden = false;
  lastScoreData = data;
  lastScorePlayers = players;
  const ended = /endgame/i.test(data.phase || "");
  $("score-kicker").textContent = ended ? "Total VP" : "If the game ended now";
  const breakdowns = players
    .map((p) => ({ p, b: scoreOf(data, p) }))
    .sort((a, c) => (c.b.total ?? 0) - (a.b.total ?? 0)
      || creditsOf(c.p, c.b) - creditsOf(a.p, a.b)
      || a.p.id - c.p.id);
  const topVp = breakdowns[0]?.b.total ?? 0;
  const vpCounts = new Map();
  for (const { b } of breakdowns) vpCounts.set(b.total ?? 0, (vpCounts.get(b.total ?? 0) || 0) + 1);
  const isTied = (b) => vpCounts.get(b.total ?? 0) > 1;
  const winnerId = breakdowns[0]?.p.id;
  setHtml($("score-line"), breakdowns.map(({ p, b }, i) => {
    const name = displayName(p, "P" + p.id);
    const ahead = p.id === winnerId && topVp > 0;
    const sep = i === 0 ? "" : `<span class="score-vs">—</span>`;
    const credits = creditsOf(p, b);
    const mc = isTied(b) ? `<span class="sym cost-sym" title="${credits} M€ — most M€ wins a VP tie">${credits}</span>` : "";
    return `${sep}<span class="score-chip color-${teamColor(p)}${ahead ? " ahead" : ""}"><span class="name">${name}</span> <strong>${b.total ?? 0}</strong>${mc}</span>`;
  }).join("") + `<span class="chev" aria-hidden="true"><svg viewBox="0 0 24 24"><path fill="currentColor" d="M7.4 8.6 12 13.2l4.6-4.6 1.4 1.4-6 6-6-6z"/></svg></span>`);

  setHtml($("score-head"), `<tr><th></th>${breakdowns.map(({ p }) =>
    `<th class="team color-${teamColor(p)}">${displayName(p, "P" + p.id)}</th>`
  ).join("")}</tr>`);

  const history = score.history || [];
  const showCharts = (data.generation ?? 0) >= 3 || ended;
  const rows = [
    ["TR", (b) => b.tr, false],
    ["Milestones", (b) => b.milestones, false],
    ["Awards", (b) => b.awards, false],
    ["Greeneries", (b) => b.greeneries, false],
    ["Cities", (b) => b.cities, false],
    ["Cards + tokens", (b) => b.cards, true],
  ];
  setHtml($("score-lines"), rows.map(([n, pick, drill]) =>
    `<tr${drill ? ` class="clickable${cardsOpen ? " open" : ""}" data-drill="cards" title="Click for card VP"` : ""}><td>${n}</td>${
      breakdowns.map(({ b }) => `<td>${pick(b) ?? 0}</td>`).join("")
    }</tr>`
  ).join(""));

  indexChartCards(players);
  beginCardRefs("vpcard");
  setHtml($("score-cards"), breakdowns.map(({ p, b }) => `
    <div class="color-${teamColor(p)}">
      <h3 class="section-title">${displayName(p, "P" + p.id)} cards</h3>
      <ul>${(b.cardDetails || []).map(cardDetailItem).join("") || "<li>None</li>"}</ul>
    </div>`).join(""));
  renderScoreCharts(data, players, history, ended);
  const notes = [];
  if (score.note) notes.push(score.note);
  if (breakdowns.some(({ b }) => isTied(b) && (b.total ?? 0) > 0)) notes.push("VP tie — most M€ wins.");
  $("score-note").textContent = notes.join(" ");
  $("score-note").hidden = notes.length === 0;
  $("score-details").hidden = !scoreOpen;
  $("score-cards").hidden = !cardsOpen;
  $("score-chart-panel").hidden = !showCharts;
  $("score-charts").hidden = !chartsOpen || !showCharts;
  $("score-chart-toggle").setAttribute("aria-expanded", chartsOpen ? "true" : "false");
  $("score-toggle").setAttribute("aria-expanded", scoreOpen ? "true" : "false");
}

/** "+3 Birds" from the score breakdown, with the card name hoverable. */
function cardDetailItem(detail) {
  const m = String(detail).match(/^([+-]?\d+) (.+)$/);
  if (!m) return `<li>${escapeHtml(detail)}</li>`;
  return `<li><strong>${m[1]}</strong> ${cardRef(m[2])}</li>`;
}

const TEAM_HEX = { blue: "#3d7ec9", green: "#2f9e44", purple: "#9b59b6", yellow: "#d4b429", red: "#c44532" };

function historyValue(point, playerId, key) {
  const by = point.byId || {};
  const b = by[playerId] || by[String(playerId)] || {};
  return Number(b[key] ?? 0);
}

const PROD_RES = ["mc", "steel", "ti", "plant", "energy", "heat"];
const CHART_METRICS = [
  { id: "vp", key: "total", label: "VP" },
  { id: "tr", key: "tr", label: "TR", kind: "tr" },
  ...PROD_RES.map((r) => {
    const name = resLabel(r);
    return { id: "prod-" + r, key: "prod-" + r, res: r, kind: "prod-" + r, row: "prod",
      label: name.charAt(0).toUpperCase() + name.slice(1) + " production" };
  }),
  // On hand at each generation's end, after production.
  ...PROD_RES.map((r) => {
    const name = resLabel(r);
    return { id: "qty-" + r, key: "qty-" + r, res: r, kind: "qty-" + r, row: "qty",
      label: name.charAt(0).toUpperCase() + name.slice(1) + " on hand" };
  }),
];

function chartMetric() {
  return CHART_METRICS.find((m) => m.id === chartMetricId) || CHART_METRICS[0];
}

function chartMetricTitle(metric, ended) {
  if (metric.id === "vp") return ended ? "Total VP" : "VP if the game ended now";
  return metric.label;
}

function chartChip(metric) {
  const on = metric.id === chartMetricId ? " on" : "";
  const face = metric.res ? resMark(metric.res) : metric.label;
  return `<button type="button" class="chart-chip${on}${metric.res ? " icon" : ""}" data-metric="${metric.id}" title="${metric.label}" aria-label="${metric.label}" aria-pressed="${on ? "true" : "false"}">${face}</button>`;
}

function renderScoreCharts(data, players, history, ended) {
  const box = $("score-charts");
  if (!history || history.length < 2) {
    setHtml(box, `<p class="score-note">Not enough generations yet.</p>`);
    return;
  }
  const metric = chartMetric();
  const score = CHART_METRICS.filter((m) => !m.res);
  const prod = CHART_METRICS.filter((m) => m.row === "prod");
  const qty = CHART_METRICS.filter((m) => m.row === "qty");
  setHtml(box, `
    <div class="chart-picker">
      <div class="chart-picker-row"><span class="chart-picker-label">Score</span>${score.map(chartChip).join("")}</div>
      <div class="chart-picker-row"><span class="chart-picker-label">Production</span>${prod.map(chartChip).join("")}</div>
      <div class="chart-picker-row"><span class="chart-picker-label">Quantity</span>${qty.map(chartChip).join("")}</div>
    </div>
    <div class="score-chart-block">
      <h3 class="section-title">${chartMetricTitle(metric, ended)}</h3>
      ${lineChartSvg(history, players, metric.key, metric.label)}
    </div>
    <div class="score-gen" id="score-gen"></div>`);
  renderGenExplain(data, players, history);
}

function lineChartSvg(history, players, key, label) {
  const w = 320;
  const h = 108;
  const padL = 28;
  const padR = 8;
  const padT = 8;
  const padB = 20;
  const n = history.length;
  const xs = history.map((_, i) => padL + (i * (w - padL - padR)) / Math.max(1, n - 1));
  let ymin = Infinity;
  let ymax = -Infinity;
  for (const pt of history) {
    for (const p of players) {
      const v = historyValue(pt, p.id, key);
      ymin = Math.min(ymin, v);
      ymax = Math.max(ymax, v);
    }
  }
  if (!Number.isFinite(ymin) || ymin === ymax) {
    ymin = (ymin || 0) - 1;
    ymax = ymin + 2;
  }
  const yAt = (v) => padT + (1 - (v - ymin) / (ymax - ymin)) * (h - padT - padB);
  const cols = xs.map((x, i) => {
    const gen = history[i].generation;
    const x0 = i === 0 ? padL - 6 : (xs[i - 1] + x) / 2;
    const x1 = i === n - 1 ? w - padR + 6 : (x + xs[i + 1]) / 2;
    const sel = selectedGen === gen ? " selected" : "";
    return `<rect class="chart-col${sel}" data-gen="${gen}" x="${x0.toFixed(1)}" y="0" width="${Math.max(8, x1 - x0).toFixed(1)}" height="${h}"/>`;
  }).join("");
  const lines = players.map((p) => {
    const color = TEAM_HEX[teamColor(p)] || "#b89f88";
    const d = history.map((pt, i) => `${i === 0 ? "M" : "L"}${xs[i].toFixed(1)},${yAt(historyValue(pt, p.id, key)).toFixed(1)}`).join(" ");
    const dots = history.map((pt, i) =>
      `<circle class="chart-dot" cx="${xs[i].toFixed(1)}" cy="${yAt(historyValue(pt, p.id, key)).toFixed(1)}" r="2.6" fill="${color}"/>`
    ).join("");
    return `<path class="chart-line" stroke="${color}" d="${d}"/>${dots}`;
  }).join("");
  const axis = `<text class="chart-axis" x="2" y="${yAt(ymax) + 3}">${Math.round(ymax)}</text>`
    + `<text class="chart-axis" x="2" y="${yAt(ymin) + 3}">${Math.round(ymin)}</text>`
    + xs.map((x, i) => {
      const pt = history[i];
      const tick = pt.now ? "now" : String(pt.generation);
      return `<text class="chart-axis" text-anchor="middle" x="${x.toFixed(1)}" y="${h - 4}">${tick}</text>`;
    }).join("");
  return `<svg class="score-chart-svg" viewBox="0 0 ${w} ${h}" preserveAspectRatio="xMidYMid meet" role="img" aria-label="${label} by generation">${cols}${lines}${axis}</svg>`;
}

function signed(n) {
  if (n > 0) return "+" + n;
  return String(n);
}

function renderGenExplain(data, players, history) {
  const el = $("score-gen");
  if (!el) return;
  indexChartCards(players);
  beginCardRefs("gencard");
  if (selectedGen == null) {
    setHtml(el, `<p class="score-gen-kicker">Tap a generation to see what moved.</p>`);
    return;
  }
  const idx = history.findIndex((pt) => pt.generation === selectedGen);
  if (idx < 0) {
    setHtml(el, `<p class="score-gen-kicker">Tap a generation to see what moved.</p>`);
    return;
  }
  const cur = history[idx];
  const prev = idx > 0 ? history[idx - 1] : null;
  const metric = chartMetric();
  const title = `${cur.now ? `Now (gen ${selectedGen})` : `Generation ${selectedGen}`} · ${metric.label}`;
  if (metric.kind) {
    setHtml(el, `<p class="score-gen-kicker">${title}</p>`
      + players.map((p) => renderContributors(data, p, metric, cur, prev)).join(""));
    return;
  }
  const events = (data.score.events || []).filter((e) => e.generation === selectedGen && !e.kind.startsWith("prod-"));
  const buckets = [
    ["TR", "tr"],
    ["Milestones", "milestones"],
    ["Awards", "awards"],
    ["Greeneries", "greeneries"],
    ["Cities", "cities"],
    ["Cards", "cards"],
  ];
  setHtml(el, `<p class="score-gen-kicker">${title}</p>` + players.map((p) => {
    const total = historyValue(cur, p.id, "total") - (prev ? historyValue(prev, p.id, "total") : 0);
    const parts = buckets.map(([name, key]) => {
      const d = historyValue(cur, p.id, key) - (prev ? historyValue(prev, p.id, key) : 0);
      return d ? `${name} ${signed(d)}` : "";
    }).filter(Boolean);
    const named = vpSources(events.filter((e) => e.playerId === p.id)).join("");
    return `<div class="score-gen-player color-${teamColor(p)}">
      <strong>${escapeHtml(displayName(p, "P" + p.id))} ${historyValue(cur, p.id, "total")}</strong>${prev ? ` <span class="gen-moved">${signed(total)}</span>` : ""}
      ${parts.length ? `<span class="buckets">${parts.join(" · ")}</span>` : ""}
      ${named ? `<ul>${named}</ul>` : ""}
    </div>`;
  }).join(""));
}

let chartCards = new Map();
let cardRefScope = "card";
let cardRefSeq = 0;

/** Start numbering card popovers for one section, so tips in different sections never share a key. */
function beginCardRefs(scope) {
  cardRefScope = scope;
  cardRefSeq = 0;
}

/** Every played card (and corporation) at the table, by lower-case name. */
function indexChartCards(players) {
  chartCards = new Map();
  for (const p of players) {
    for (const c of [...(p.blueCards || []), ...(p.greenCards || []), ...(p.events || [])]) {
      chartCards.set(String(c.name).toLowerCase(), c);
    }
    if (p.corporation && p.corporation !== "Unknown") {
      chartCards.set(p.corporation.toLowerCase(), { name: p.corporation, tags: [], extra: corpRuleText(p.corpRules) });
    }
  }
}

/**
 * The printed effects the card catalog keeps as data rather than text (Power Plant's "+1 energy production"
 * is only a production value): requirement, gain chips as HTML, and tile placements not already in the gains.
 */
function cardEffects(card) {
  // Anything without an icon is spelled out rather than shown as a bare colored box.
  const gain = (k, v, prod) => resIcon(resKey(k))
    ? resSym(k, v, prod)
    : `<span class="tip-card-gain">${escapeHtml(`${Number(v) > 0 ? "+" : ""}${v} ${resLabel(resKey(k))}`)}</span>`;
  const gains = [
    ...Object.entries(card.production || {}).filter(([, v]) => v != null && v !== 0).map(([k, v]) => gain(k, v, true)),
    ...Object.entries(card.resources || {}).filter(([, v]) => v != null && v !== 0).map(([k, v]) => gain(k, v, false)),
  ];
  const resourceKeys = Object.keys(card.resources || {});
  const places = (card.place || []).filter((k) => !resourceKeys.includes(k))
    .map((k) => `Place ${escapeHtml(k)} tile`);
  return { req: formatReq(card.req), gains, places };
}

/** A chart label, with a card popover when it names a played card ("X", "X action", "X (Player)"). */
function cardRef(label) {
  const m = String(label).match(/^(.*?)(?: action)?(?: \([^)]*\))?$/);
  const card = m && chartCards.get(m[1].toLowerCase());
  return card ? cardPopover(card, label) : escapeHtml(label);
}

/** `label` with a hover/tap popover describing `card` (a played card, or a drawn card from the server). */
function cardPopover(card, label) {
  const text = escapeHtml(label);
  const body = String(card.extra || "").split("\n")
    .map((line) => line.trim())
    .filter((line) => line && !/^-+$/.test(line))
    .join("\n");
  const tags = (card.tags || []).map(tagIcon).join("");
  const cost = card.cost != null ? costSym(card.cost) : "";
  const { req, gains, places } = cardEffects(card);
  const printedVp = card.printedVp ?? card.vp; // played cards carry printedVp, drawn cards vp
  const vp = printedVp ? `<span class="tip-card-vp">${printedVp} VP</span>` : "";
  const tokens = card.tokens ? `<span class="tip-card-vp">${card.tokens} ${tokenWord(card.tokenType, card.tokens)} now</span>` : "";
  const pop = `<span class="tip-card-head"><strong>${escapeHtml(card.name)}</strong>${cost}${tags}</span>`
    + (req ? `<span class="tip-card-req">${escapeHtml(req)}</span>` : "")
    + (gains.length ? `<span class="tip-card-gains">${gains.join("")}</span>` : "")
    + (places.length ? `<span class="tip-def">${places.join(" · ")}</span>` : "")
    + (body ? `<span class="tip-def">${escapeHtml(body)}</span>` : "")
    + vp + tokens;
  return `<span class="tip card-ref" tabindex="0" data-tip-key="${cardRefScope}${++cardRefSeq}">${text}<span class="tip-pop" role="tooltip">${pop}</span></span>`;
}

function tokenWord(type, n) {
  const word = type || "resource";
  return Math.abs(n) === 1 ? word : word + "s";
}

/** Short name for what moved tokens on {@code card}: its own action reads as "action". */
function tokenCause(cause, card) {
  if (!cause || cause === "Other") return "";
  if (cause === card + " action") return "action";
  if (cause === card) return "when played";
  return cause;
}

/** One row per card or action: VP it earned this generation, with token and TR notes. */
function vpSources(events) {
  const bySource = new Map();
  for (const e of events) {
    const key = e.source || e.label;
    const row = bySource.get(key)
      || { source: key, vp: 0, tr: 0, tokens: 0, tokenType: "", funded: false, causes: new Map() };
    row.vp += e.delta;
    if (e.kind === "tr") row.tr += e.delta;
    if (e.kind === "award") row.funded = true;
    if (e.tokens) {
      row.tokens += e.tokens;
      row.tokenType = e.tokenType || row.tokenType;
      const cause = tokenCause(e.cause, key);
      row.causes.set(cause, (row.causes.get(cause) || 0) + e.tokens);
    }
    bySource.set(key, row);
  }
  return [...bySource.values()]
    .filter((r) => r.vp !== 0 || r.tokens !== 0 || r.funded)
    .sort((a, b) => b.vp - a.vp || a.source.localeCompare(b.source))
    .map((r) => {
      const notes = [];
      if (r.tr) notes.push(r.tr === r.vp ? "TR" : `${signed(r.tr)} TR`);
      const moves = [...r.causes].filter(([, n]) => n !== 0);
      for (const [cause, n] of moves) {
        notes.push(`${signed(n)} ${escapeHtml(tokenWord(r.tokenType, n))}${cause ? ` (${cardRef(cause)})` : ""}`);
      }
      const vp = r.vp || r.funded ? (r.vp ? ` <strong>${signed(r.vp)}</strong>` : "") : " <strong>0</strong>";
      return `<li>${cardRef(r.source)}${vp}${notes.length ? ` · ${notes.join(", ")}` : ""}</li>`;
    });
}

/** Everything that built one player's TR or production up to the selected generation. */
function renderContributors(data, p, metric, cur, prev) {
  const value = historyValue(cur, p.id, metric.key);
  const moved = prev ? value - historyValue(prev, p.id, metric.key) : 0;
  const bySource = new Map();
  for (const e of data.score.events || []) {
    if (e.kind !== metric.kind || e.playerId !== p.id || e.generation > selectedGen) continue;
    const row = bySource.get(e.label) || { label: e.label, total: 0, now: 0 };
    row.total += e.delta;
    if (e.generation === selectedGen) row.now += e.delta;
    bySource.set(e.label, row);
  }
  const rows = [...bySource.values()]
    .filter((r) => r.total !== 0 || r.now !== 0)
    .sort((a, b) => Math.abs(b.now) - Math.abs(a.now) || Math.abs(b.total) - Math.abs(a.total));
  const base = metric.row === "qty" ? 0 : value - rows.reduce((sum, r) => sum + r.total, 0);
  const items = metric.row === "qty" ? [] : rows.map((r) => {
    const note = r.now === 0 ? "" : r.now === r.total ? " (new)" : ` (${signed(r.now)} this gen)`;
    return `<li${r.now ? ` class="moved"` : ""}>${cardRef(r.label)} <strong>${signed(r.total)}</strong>${note}</li>`;
  });
  if (base !== 0) items.push(`<li>${metric.kind === "tr" ? "Starting TR" : "Base"} <strong>${base}</strong></li>`);
  return `<div class="score-gen-player color-${teamColor(p)}">
    <strong>${escapeHtml(displayName(p, "P" + p.id))} ${value}</strong>${prev && moved ? ` <span class="gen-moved">${signed(moved)}</span>` : ""}
    ${items.length ? `<ul>${items.join("")}</ul>` : ""}
  </div>`;
}

function loadChartMetric() {
  try {
    return localStorage.getItem("chartMetric") || "vp";
  } catch {
    return "vp";
  }
}

/** Open and closed sections from the last visit, so a reload or a new game keeps the page as you left it. */
function loadSections() {
  try {
    return JSON.parse(localStorage.getItem("sections")) || {};
  } catch {
    return {};
  }
}

function saveSections() {
  try {
    localStorage.setItem("sections", JSON.stringify({
      scoreOpen, cardsOpen, chartsOpen, bannerOpen, corpRulesOpen,
      collapsedBoards: [...collapsedBoardIds],
      collapsedCards: [...collapsedCardSections],
    }));
  } catch {
    // Private windows can refuse storage; the sections still fold for this visit.
  }
}

const savedSections = loadSections();
let chartMetricId = loadChartMetric();
let scoreOpen = savedSections.scoreOpen === true;
let cardsOpen = savedSections.cardsOpen === true;
let chartsOpen = savedSections.chartsOpen === true;
let selectedGen = null;
let lastScoreData = null;
let lastScorePlayers = [];
let scoreUiBound = false;
// The game log under the banner; closed until opened by hand.
let bannerOpen = savedSections.bannerOpen === true;
let coloniesOn = false;
let colonyOpen = loadColonyOpen();
let lastStateData = null;
let bannerUiBound = false;
let corpRulesOpen = savedSections.corpRulesOpen === true;
let collapsedBoardIds = new Set(savedSections.collapsedBoards || []);
/** Collapsed card sections (hand, blue, green, red); each applies to every player's board. */
let collapsedCardSections = new Set(savedSections.collapsedCards || []);
let corpUiBound = false;
let lastGameId = "";

function applyBoardUi() {
  document.querySelectorAll(".board").forEach((board) => {
    const toggle = board.querySelector(".board-toggle");
    const info = board.querySelector(".corp-info");
    const id = Number((toggle || info)?.dataset.corp);
    if (!id) return;
    const collapsed = collapsedBoardIds.has(id);
    const rulesOpen = corpRulesOpen;
    board.classList.toggle("collapsed", collapsed);
    const body = board.querySelector(".board-body");
    if (body) body.hidden = collapsed;
    if (toggle) {
      const title = board.querySelector(".corp-name")?.textContent || "board";
      toggle.setAttribute("aria-expanded", collapsed ? "false" : "true");
      toggle.setAttribute("aria-label", `${collapsed ? "Expand" : "Collapse"} ${title}`);
    }
    if (info) info.setAttribute("aria-expanded", rulesOpen ? "true" : "false");
    const rules = board.querySelector(".corp-rules");
    if (rules) rules.hidden = collapsed || !rulesOpen;
  });
  alignBoardSections();
}

const BOARD_ALIGN = [".board-head", ".board-intro", ".board-cubes", ".board-tiles", ".board-tags", ".board-colonies", ".board-awards"];

function alignBoardSections() {
  document.querySelectorAll(BOARD_ALIGN.join(",")).forEach((el) => {
    el.style.minHeight = "";
  });
  const root = $("boards");
  if (!root) return;
  const colCount = getComputedStyle(root).gridTemplateColumns.split(/\s+/).filter(Boolean).length;
  if (colCount < 2) return;
  const groups = new Map();
  for (const board of root.querySelectorAll(".board:not(.collapsed)")) {
    const top = Math.round(board.offsetTop);
    if (!groups.has(top)) groups.set(top, []);
    groups.get(top).push(board);
  }
  for (const group of groups.values()) {
    if (group.length < 2) continue;
    for (const sel of BOARD_ALIGN) {
      const els = group.map((b) => b.querySelector(sel)).filter(Boolean);
      if (els.length < 2) continue;
      const h = Math.max(...els.map((el) => el.offsetHeight));
      els.forEach((el) => { el.style.minHeight = `${h}px`; });
    }
  }
}

function bindCorpUi() {
  if (corpUiBound) return;
  corpUiBound = true;
  window.addEventListener("resize", alignBoardSections);
  $("boards").addEventListener("click", (ev) => {
    if (ev.target.closest(".colony-toggle")) {
      toggleColonies();
      return;
    }
    const section = ev.target.closest(".cards-toggle");
    if (section) {
      // When boards stack (narrow screens, 4-5 players), sections above the clicked one change height too;
      // keep the clicked header where it was so the page doesn't jump out from under the pointer.
      const before = section.getBoundingClientRect().top;
      const key = section.dataset.cards;
      const open = collapsedCardSections.has(key);
      if (open) collapsedCardSections.delete(key);
      else collapsedCardSections.add(key);
      saveSections();
      document.querySelectorAll(`.cards-toggle[data-cards="${key}"]`).forEach((btn) => {
        btn.setAttribute("aria-expanded", String(open));
        const cards = btn.closest(".cards-title")?.nextElementSibling;
        if (cards) cards.hidden = !open;
      });
      alignBoardSections();
      window.scrollBy(0, section.getBoundingClientRect().top - before);
      return;
    }
    const info = ev.target.closest(".corp-info");
    if (info) {
      corpRulesOpen = !corpRulesOpen;
      if (corpRulesOpen) collapsedBoardIds.clear();
      saveSections();
      applyBoardUi();
      return;
    }
    const toggle = ev.target.closest(".board-toggle");
    if (!toggle) return;
    const id = Number(toggle.dataset.corp);
    if (collapsedBoardIds.has(id)) collapsedBoardIds.delete(id);
    else collapsedBoardIds.add(id);
    saveSections();
    applyBoardUi();
  });
}

function bindScoreUi() {
  if (scoreUiBound) return;
  scoreUiBound = true;
  $("score-toggle").addEventListener("click", () => {
    scoreOpen = !scoreOpen;
    if (!scoreOpen) {
      cardsOpen = false;
      chartsOpen = false;
    }
    saveSections();
    $("score-details").hidden = !scoreOpen;
    $("score-cards").hidden = !cardsOpen;
    $("score-charts").hidden = !chartsOpen;
    $("score-chart-toggle").setAttribute("aria-expanded", chartsOpen ? "true" : "false");
    $("score-toggle").setAttribute("aria-expanded", scoreOpen ? "true" : "false");
  });
  $("score-lines").addEventListener("click", (ev) => {
    const row = ev.target.closest("tr[data-drill='cards']");
    if (!row) return;
    cardsOpen = !cardsOpen;
    saveSections();
    $("score-cards").hidden = !cardsOpen;
    row.classList.toggle("open", cardsOpen);
  });
  $("score-chart-toggle").addEventListener("click", () => {
    chartsOpen = !chartsOpen;
    saveSections();
    $("score-charts").hidden = !chartsOpen;
    $("score-chart-toggle").setAttribute("aria-expanded", chartsOpen ? "true" : "false");
  });
  $("score-charts").addEventListener("click", (ev) => {
    const chip = ev.target.closest("[data-metric]");
    if (chip) {
      chartMetricId = chip.dataset.metric;
      try {
        localStorage.setItem("chartMetric", chartMetricId);
      } catch {
        // Private windows can refuse storage; the choice just won't persist.
      }
      const data = lastScoreData || {};
      const ended = /endgame/i.test(data.phase || "");
      renderScoreCharts(data, lastScorePlayers, data.score?.history || [], ended);
      return;
    }
    const col = ev.target.closest("[data-gen]");
    if (!col) return;
    const gen = Number(col.dataset.gen);
    selectedGen = selectedGen === gen ? null : gen;
    $("score-charts").querySelectorAll("[data-gen]").forEach((el) => {
      el.classList.toggle("selected", Number(el.dataset.gen) === selectedGen);
    });
    const history = lastScoreData?.score?.history || [];
    renderGenExplain(lastScoreData || {}, lastScorePlayers, history);
  });
}

let lastStateText = "";
let lastLive = false;

/** Replace an element's HTML only when it changed, so a click in progress isn't lost to a no-op rebuild. */
function setHtml(el, html) {
  if (!el || el._html === html) return;
  el.innerHTML = html;
  el._html = html;
}

/**
 * Makes the wordmark's two lines ("TERRAFORMING MARS", "TELEMETRY") exactly the same width in whatever font the
 * browser uses, by adjusting the narrower line's letter spacing. Letter spacing also trails the last letter, so
 * a matching negative right margin keeps the right edges flush.
 */
function fitWordmark() {
  const lines = [...document.querySelectorAll(".brand-game, .brand-app")];
  if (lines.length !== 2) return;
  lines.forEach((el) => { el.style.letterSpacing = ""; el.style.marginRight = ""; });
  const spacing = (el) => parseFloat(getComputedStyle(el).letterSpacing) || 0;
  const visible = (el) => el.getBoundingClientRect().width - spacing(el);
  const target = Math.max(...lines.map(visible));
  for (const el of lines) {
    const ls = spacing(el) + (target - visible(el)) / (el.textContent.length - 1);
    el.style.letterSpacing = `${ls}px`;
    el.style.marginRight = `${-ls}px`;
  }
}

/** A saved game being shown instead of the live one; live polling pauses while it's set. */
let viewingGame = null;
let liveGameId = "";

/** "9:38 PM" for a game started today, "Sep 27, 9:38 PM" otherwise; "" when unknown. */
function startedLabel(iso) {
  const d = iso ? new Date(iso) : null;
  if (!d || Number.isNaN(d.getTime())) return "";
  const time = d.toLocaleTimeString(undefined, { hour: "numeric", minute: "2-digit" });
  if (d.toDateString() === new Date().toDateString()) return time;
  return `${d.toLocaleDateString(undefined, { month: "short", day: "numeric" })}, ${time}`;
}

function formatPlayedAt(iso) {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? "" : d.toLocaleString(undefined, { dateStyle: "medium", timeStyle: "short" });
}

async function toggleGamesPanel(open) {
  const panel = $("games-panel");
  panel.hidden = !open;
  $("games-btn").setAttribute("aria-expanded", String(open));
  if (!open) return;
  panel.innerHTML = `<p class="games-empty">Loading…</p>`;
  try {
    const list = await (await fetch("/api/games", { cache: "no-store" })).json();
    panel.innerHTML = renderGamesList(list);
  } catch {
    panel.innerHTML = `<p class="games-empty">Couldn't load past games.</p>`;
  }
}

function renderGamesList(list) {
  if (!list.length) {
    return `<p class="games-empty">No saved games yet. Games are saved at the end of each generation.</p>`;
  }
  return `<ol class="games-list">${list.map((g) => {
    const top = Math.max(...g.players.map((p) => p.total));
    const players = g.players.map((p) => {
      const name = p.corporation && p.corporation !== "Unknown" ? p.corporation : p.name;
      const win = g.finished && p.total === top;
      return `<span class="games-player color-${escapeHtml(p.color || "blue")}${win ? " won" : ""}">${escapeHtml(name)}${
        p.human ? ` <span class="games-you">you</span>` : ""} <strong>${p.total}</strong></span>`;
    }).join("");
    const live = g.gameId === liveGameId ? `<span class="games-tag">current</span>` : "";
    const status = (g.finished ? "" : `<span class="games-tag">unfinished</span>`)
      + (g.imported ? `<span class="games-tag" title="Recovered from an older game log${
        g.approxDate ? "; the date is approximate" : ""}">imported</span>` : "");
    return `<li><button type="button" class="games-row" data-game-id="${escapeHtml(g.gameId)}">
        <span class="games-when">${escapeHtml(formatPlayedAt(g.playedAt))}${live}${status}</span>
        <span class="games-meta">${escapeHtml(g.board || "")} · Gen ${g.generation}</span>
        <span class="games-players">${players}</span>
      </button></li>`;
  }).join("")}</ol>`;
}

async function viewGame(id) {
  toggleGamesPanel(false);
  if (id === liveGameId) {
    backToLive();
    return;
  }
  try {
    const res = await fetch(`/api/games/${encodeURIComponent(id)}`, { cache: "no-store" });
    if (!res.ok) throw new Error(res.status);
    viewingGame = await res.json();
  } catch {
    return;
  }
  render(viewingGame);
  $("live").textContent = "past game";
  $("live").classList.remove("on", "bad");
  const when = formatPlayedAt(viewingGame.playedAt);
  $("viewing-text").textContent = `Viewing a past game${when ? " from " + when : ""}${viewingGame.finished ? "" : " (unfinished)"}`;
  $("viewing-bar").hidden = false;
  window.scrollTo(0, 0);
}

let keepAwake = false;
let wakeLock = null;
let wakeRequest = null;

/** Takes the screen wake lock while the toggle is on; browsers drop it whenever the tab is hidden. */
async function syncWakeLock() {
  if (wakeRequest) await wakeRequest.catch(() => {}); // the button click also reaches the document listener
  if (keepAwake && !wakeLock && document.visibilityState === "visible") {
    wakeRequest = navigator.wakeLock.request("screen");
    try {
      const lock = await wakeRequest;
      lock.addEventListener("release", () => { if (wakeLock === lock) wakeLock = null; });
      wakeLock = lock;
    } catch {
      // Battery saver or a missing user gesture can refuse it; the next tap or tab return retries.
    } finally {
      wakeRequest = null;
    }
    if (!keepAwake) syncWakeLock(); // switched off while the request was pending
  } else if (!keepAwake && wakeLock) {
    wakeLock.release();
    wakeLock = null;
  }
}

function setKeepAwake(on) {
  keepAwake = on;
  $("awake-btn").setAttribute("aria-pressed", String(on));
  try {
    localStorage.setItem("keepAwake", String(on));
  } catch {
    // Private windows can refuse storage; the toggle still works for this visit.
  }
  syncWakeLock();
}

function bindWakeUi() {
  if (!("wakeLock" in navigator)) return; // plain-http pages and older browsers: leave the button hidden
  const btn = $("awake-btn");
  btn.hidden = false;
  btn.addEventListener("click", () => setKeepAwake(!keepAwake));
  document.addEventListener("visibilitychange", syncWakeLock);
  // iOS only grants the lock from a tap, so a remembered "on" retries on the first one.
  document.addEventListener("click", syncWakeLock);
  let saved = false;
  try {
    saved = localStorage.getItem("keepAwake") === "true";
  } catch {
    // no storage: start off
  }
  setKeepAwake(saved);
}

function backToLive() {
  viewingGame = null;
  $("viewing-bar").hidden = true;
  lastStateText = "";
  tick();
}

function bindGamesUi() {
  $("games-btn").addEventListener("click", () => toggleGamesPanel($("games-panel").hidden));
  $("games-panel").addEventListener("click", (ev) => {
    const row = ev.target.closest("[data-game-id]");
    if (row) viewGame(row.dataset.gameId);
  });
  $("viewing-back").addEventListener("click", backToLive);
}

async function tick() {
  if (viewingGame) return;
  try {
    const res = await fetch("/api/state", { cache: "no-store" });
    if (!res.ok) throw new Error(res.status);
    const text = await res.text();
    if (viewingGame) return; // a past game was opened while this poll was in flight
    // Rebuilding the DOM between mousedown and mouseup swallows the click, so skip identical polls.
    if (text === lastStateText) {
      setLiveStatus(lastLive ? "live" : "idle");
      return;
    }
    lastStateText = text;
    const data = JSON.parse(text);
    lastLive = !!data.live;
    liveGameId = data.gameId || "";
    render(data);
  } catch (err) {
    setLiveStatus("disconnected");
  }
}

tick();
setInterval(tick, 500);
bindScoreUi();
bindBannerUi();
bindCorpUi();
bindTipUi();
bindPhoneAccessUi();
bindGamesUi();
bindWakeUi();
fitWordmark();
document.fonts?.ready.then(fitWordmark);
window.addEventListener("resize", fitWordmark);
