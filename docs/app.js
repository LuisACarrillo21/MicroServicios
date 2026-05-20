const API_BASE = (window.APP_CONFIG?.API_BASE || "").trim();
const POST_SOL = API_BASE ? `${API_BASE}/solicitudes` : null;
const GET_STATUS = API_BASE ? `${API_BASE}/status` : null;

const netPill = document.getElementById("netPill");
const apiBaseLabel = document.getElementById("apiBaseLabel");
apiBaseLabel.textContent = API_BASE || "(offline)";

const tipoClienteEl = document.getElementById("tipoCliente");
const asuntoEl = document.getElementById("asunto");
const descEl = document.getElementById("descripcion");

const btnCrear = document.getElementById("btnCrear");
const btnSync = document.getElementById("btnSync");
const btnRefresh = document.getElementById("btnRefresh");

const createHint = document.getElementById("createHint");
const statusBox = document.getElementById("statusBox");

const outboxList = document.getElementById("outboxList");
const historyList = document.getElementById("historyList");
const outboxCount = document.getElementById("outboxCount");
const historyCount = document.getElementById("historyCount");

// Service Worker
if ("serviceWorker" in navigator) {
  navigator.serviceWorker.register("./servicios.js").catch(console.error);
}

// Net pill
function updateNetPill() {
  const online = navigator.onLine;
  netPill.className = "pill " + (online ? "ok" : "bad");
  netPill.textContent = online ? "Online" : "Offline";
}
window.addEventListener("online", () => { updateNetPill(); flushOutbox(); });
window.addEventListener("offline", updateNetPill);
updateNetPill();

// IndexedDB
const DB_NAME = "soportePWA";
const DB_VERSION = 1;

function openDb() {
  return new Promise((resolve, reject) => {
    const req = indexedDB.open(DB_NAME, DB_VERSION);
    req.onupgradeneeded = () => {
      const db = req.result;
      if (!db.objectStoreNames.contains("outbox")) db.createObjectStore("outbox", { keyPath: "id" });
      if (!db.objectStoreNames.contains("history")) db.createObjectStore("history", { keyPath: "id" });
      if (!db.objectStoreNames.contains("meta")) db.createObjectStore("meta", { keyPath: "key" });
    };
    req.onsuccess = () => resolve(req.result);
    req.onerror = () => reject(req.error);
  });
}

async function dbPut(store, value) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, "readwrite");
    tx.objectStore(store).put(value);
    tx.oncomplete = () => resolve(true);
    tx.onerror = () => reject(tx.error);
  });
}

async function dbGet(store, key) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, "readonly");
    const req = tx.objectStore(store).get(key);
    req.onsuccess = () => resolve(req.result || null);
    req.onerror = () => reject(req.error);
  });
}

async function dbDelete(store, key) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, "readwrite");
    tx.objectStore(store).delete(key);
    tx.oncomplete = () => resolve(true);
    tx.onerror = () => reject(tx.error);
  });
}

async function dbGetAll(store) {
  const db = await openDb();
  return new Promise((resolve, reject) => {
    const tx = db.transaction(store, "readonly");
    const req = tx.objectStore(store).getAll();
    req.onsuccess = () => resolve(req.result || []);
    req.onerror = () => reject(req.error);
  });
}

// ID generation
const tipoCode = { VIP: 1, NORMAL: 2, NO_CLIENTE: 3 };
const asuntoCode = { DUDA: 1, QUEJA: 2, PROBLEMA: 3 };

async function nextSeq() {
  const meta = await dbGet("meta", "seq");
  const current = meta?.value ?? 1;
  await dbPut("meta", { key: "seq", value: current + 1 });
  return current;
}

async function generateId(tipo, asunto) {
  const seq = await nextSeq();
  return tipoCode[tipo] * 1_000_000 + asuntoCode[asunto] * 100_000 + seq;
}

// UI render
function escapeHtml(str){
  return str.replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));
}
function badgeAsunto(a) {
  const cls = a === "PROBLEMA" ? "problema" : (a === "QUEJA" ? "queja" : "duda");
  return `<span class="badge ${cls}">${a}</span>`;
}
function badgeEstado(e) {
  return `<span class="badge estado">${e}</span>`;
}
function renderItem(s) {
  const when = new Date(s.timestampMs).toLocaleString();
  const desc = (s.descripcion || "").trim();
  return `
  <div class="item">
    <div class="meta">
      <b>#${s.id}</b>
      ${badgeAsunto(s.asunto)}
      <span class="badge">${s.tipoCliente}</span>
      ${badgeEstado(s.estado)}
      <small>${when}</small>
    </div>
    ${desc ? `<div style="margin-top:6px"><small>${escapeHtml(desc)}</small></div>` : ""}
  </div>`;
}

async function refreshLists() {
  const out = await dbGetAll("outbox");
  const hist = await dbGetAll("history");
  out.sort((a,b) => b.timestampMs - a.timestampMs);
  hist.sort((a,b) => b.timestampMs - a.timestampMs);

  outboxCount.textContent = out.length;
  historyCount.textContent = hist.length;

  outboxList.innerHTML = out.length ? out.map(renderItem).join("") : `<p class="muted">Sin pendientes.</p>`;
  historyList.innerHTML = hist.length ? hist.map(renderItem).join("") : `<p class="muted">Sin historial.</p>`;
}

async function renderLocalStatus(note) {
  const out = await dbGetAll("outbox");
  const hist = await dbGetAll("history");
  const status = {
    note,
    time: new Date().toISOString(),
    local: true,
    outbox: out.length,
    history: hist.length
  };
  statusBox.textContent = JSON.stringify(status, null, 2);
}

async function refreshStatus() {
  if (!GET_STATUS) {
    await renderLocalStatus("Sin backend configurado (modo offline).");
    return;
  }
  try {
    const res = await fetch(GET_STATUS, { cache: "no-store" });
    const txt = await res.text();
    statusBox.textContent = txt;
  } catch (e) {
    await renderLocalStatus("No se pudo consultar /status. Mostrando estado local.");
  }
}

btnCrear.addEventListener("click", async () => {
  const tipo = tipoClienteEl.value;
  const asunto = asuntoEl.value;
  const descripcion = descEl.value;

  const id = await generateId(tipo, asunto);
  const solicitud = {
    id, tipoCliente: tipo, asunto,
    timestampMs: Date.now(),
    descripcion,
    estado: navigator.onLine ? "pendiente_envio" : "pendiente_offline"
  };

  await dbPut("outbox", solicitud);
  createHint.textContent = navigator.onLine
    ? "Guardada. Intentando enviar..."
    : "Sin conexión. Guardada para enviar después.";

  descEl.value = "";
  await refreshLists();
  flushOutbox();
});

btnSync.addEventListener("click", () => flushOutbox());
btnRefresh.addEventListener("click", () => refreshStatus());

setInterval(() => {
  if (navigator.onLine) flushOutbox();
}, 5000);

async function flushOutbox() {
  if (!navigator.onLine) return;
  if (!POST_SOL) return; // sin backend

  const items = await dbGetAll("outbox");
  if (!items.length) return;
  items.sort((a,b) => a.timestampMs - b.timestampMs);

  for (const s of items) {
    try {
      const body = JSON.stringify({
        id: s.id,
        tipoCliente: s.tipoCliente,
        asunto: s.asunto,
        timestampMs: s.timestampMs
      });

      const res = await fetch(POST_SOL, {
        method: "POST",
        headers: { "Content-Type": "application/json; charset=utf-8" },
        body
      });

      if (res.status === 200) {
        s.estado = "enviada";
        await dbPut("history", s);
        await dbDelete("outbox", s.id);
      } else if (res.status === 409) {
        s.estado = "duplicada";
        await dbPut("history", s);
        await dbDelete("outbox", s.id);
      } else {
        s.estado = "error_http_" + res.status;
        await dbPut("outbox", s);
      }
    } catch (e) {
      s.estado = "error_red";
      await dbPut("outbox", s);
      break;
    }
  }

  await refreshLists();
  await refreshStatus();
}

// init
refreshLists();
refreshStatus();