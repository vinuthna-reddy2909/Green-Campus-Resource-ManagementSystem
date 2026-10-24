const state = {
  user: null, csrf: "", page: 0, reportPage: 0, currentPage: "dashboard", pageSize: 20, charts: [], locations: [], filters: {}
};
const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const titles = {
  dashboard: "Dashboard", records: "Waste records", segregation: "Segregation management",
  collection: "Collection tracking", ewaste: "E-waste inventory", reports: "Reports",
  locations: "Campus locations", awareness: "Environmental awareness", alerts: "Notifications & alerts",
  users: "User management", audit: "Audit log", settings: "Profile & settings"
};
const canEdit = () => ["ADMIN", "STAFF"].includes(state.user?.role);
const isAdmin = () => state.user?.role === "ADMIN";

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body && !(options.body instanceof FormData)) headers.set("Content-Type", "application/json");
  if (options.method && !["GET", "HEAD", "OPTIONS"].includes(options.method.toUpperCase())) {
    if (!state.csrf) await refreshCsrf();
    headers.set("X-XSRF-TOKEN", state.csrf);
  }
  const response = await fetch(path, { credentials: "same-origin", ...options, headers });
  if (response.status === 401) {
    if (path === "/api/auth/login") {
      throw new Error("Username or password was not accepted.");
    }
    showLogin();
    throw new Error("Your session has expired. Please sign in again.");
  }
  if (!response.ok) {
    let message = `Request failed (${response.status}).`;
    try { message = (await response.json()).error || message; } catch {}
    throw new Error(message);
  }
  if (response.status === 204) return null;
  return response.headers.get("content-type")?.includes("application/json") ? response.json() : response.text();
}

async function refreshCsrf() {
  const result = await apiGet("/api/auth/csrf");
  state.csrf = result.token;
}
async function apiGet(path) {
  const response = await fetch(path, { credentials: "same-origin" });
  if (!response.ok) throw new Error("Could not initialize the secure session.");
  return response.json();
}
function escapeHtml(value) {
  return String(value ?? "").replace(/[&<>"']/g, char => ({
    "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;"
  })[char]);
}
function notify(message, isError = false) {
  const toast = $("#toast");
  toast.textContent = message;
  toast.style.background = isError ? "#8f302c" : "";
  toast.classList.add("show");
  clearTimeout(notify.timer);
  notify.timer = setTimeout(() => toast.classList.remove("show"), 3400);
}
function showLogin() {
  $("#app-view").classList.add("d-none");
  $("#login-view").classList.remove("d-none");
  state.user = null;
}
function showApp(user) {
  state.user = user;
  $("#login-view").classList.add("d-none");
  $("#app-view").classList.remove("d-none");
  $("#user-name").textContent = user.displayName;
  $("#user-role").textContent = user.role.toLowerCase();
  $("#user-avatar").textContent = user.displayName.slice(0, 1).toUpperCase();
  $$(".admin-only").forEach(el => el.classList.toggle("d-none", !isAdmin()));
  navigate(location.hash.slice(1) || "dashboard");
}
function navigate(page) {
  if (!titles[page] || (["users", "audit"].includes(page) && !isAdmin())) page = "dashboard";
  state.currentPage = page;
  $$(".page-section").forEach(section => section.classList.toggle("d-none", section.id !== `page-${page}`));
  $$("#main-nav .nav-link").forEach(link => link.classList.toggle("active", link.dataset.page === page));
  $("#page-title").textContent = titles[page];
  $("#main-nav").classList.remove("open");
  state.page = 0;
  renderPage(page).catch(error => notify(error.message, true));
}
async function renderPage(page) {
  if (page === "dashboard") return renderDashboard();
  if (page === "records") return renderRecords();
  if (page === "segregation") return renderSegregation();
  if (page === "collection") return renderCollection();
  if (page === "ewaste") return renderEwaste();
  if (page === "reports") return renderReports();
  if (page === "locations") return renderLocations();
  if (page === "awareness") return renderAwareness();
  if (page === "alerts") return renderAlerts();
  if (page === "users") return renderUsers();
  if (page === "audit") return renderAudit();
  if (page === "settings") return renderSettings();
}
function heading(title, description, action = "") {
  return `<div class="page-heading"><div><p class="eyebrow mb-1">GREEN CAMPUS RESOURCE MANAGEMENT</p><h1>${title}</h1><p>${description}</p></div>${action}</div>`;
}
function empty(message, detail = "There is nothing to show yet.") {
  return `<div class="empty-state"><div class="fs-3 text-success">♻</div><strong>${message}</strong><span>${detail}</span></div>`;
}
function statusBadge(value) {
  return `<span class="badge-status status-${String(value).toLowerCase()}">${escapeHtml(String(value).replaceAll("_", " ").toLowerCase().replace(/\b\w/g, c => c.toUpperCase()))}</span>`;
}
function tableShell(headers, rows, emptyMessage, footer = "") {
  return `<div class="table-card"><div class="table-responsive"><table class="table table-hover"><thead><tr>${headers.map(h => `<th>${h}</th>`).join("")}</tr></thead><tbody>${rows || `<tr><td colspan="${headers.length}">${empty(emptyMessage)}</td></tr>`}</tbody></table></div>${footer}</div>`;
}
function number(value) { return new Intl.NumberFormat(undefined, { maximumFractionDigits: 3 }).format(value ?? 0); }
function destroyCharts() { state.charts.forEach(chart => chart.destroy()); state.charts = []; }
function chartColors(count) { return Array.from({ length: count }, (_, i) => ["#176b4d", "#53a477", "#8fc99f", "#d3a73d", "#86a6d4", "#9c83c1"][i % 6]); }
function drawChart(id, type, labels, values, label) {
  const canvas = document.getElementById(id);
  if (!canvas) return;
  if (!labels.length) {
    canvas.parentElement.innerHTML = `<div class="empty-state py-5"><strong>No measured data available</strong><span>No records match this view.</span></div>`;
    return;
  }
  if (typeof Chart === "undefined") return;
  state.charts.push(new Chart(canvas, {
    type, data: { labels, datasets: [{ label, data: values, backgroundColor: chartColors(values.length),
      borderColor: "#fff", borderWidth: type === "doughnut" ? 3 : 0, borderRadius: type === "bar" ? 5 : 0 }] },
    options: { responsive: true, maintainAspectRatio: false, plugins: { legend: { display: type === "doughnut", position: "bottom" } },
      scales: type === "bar" ? { y: { beginAtZero: true, grid: { color: "#edf1ee" } }, x: { grid: { display: false } } } : {} }
  }));
}
async function renderDashboard() {
  destroyCharts();
  const target = $("#page-dashboard");
  target.innerHTML = `<div class="loading">Loading measured campus records…</div>`;
  const [data, page, alerts] = await Promise.all([api("/api/dashboard"), api("/api/records?size=100"), api("/api/alerts")]);
  const openAlerts = alerts.filter(alert => !alert.resolved);
  $("#alert-count").textContent = openAlerts.length;
  const totals = data.totalsByCategoryAndUnit || {};
  const plastic = Object.hasOwn(totals, "PLASTIC (KG)") ? `${number(totals["PLASTIC (KG)"])} kg` : "—";
  const ewaste = Object.hasOwn(totals, "E_WASTE (ITEM)") ? `${number(totals["E_WASTE (ITEM)"])} items` : "—";
  const recent = page.content.slice(0, 5).map(recordRow).join("");
  target.innerHTML = `${heading("Campus overview", "Measured records from your campus database. Missing measurements are not treated as zero waste.")}<div class="row g-3 mb-4">
    ${statCard("Plastic waste recorded", plastic, Object.hasOwn(totals, "PLASTIC (KG)") ? "PLASTIC (KG) measured records" : "No plastic measurement recorded", "♻")}
    ${statCard("E-waste inventory", ewaste, Object.hasOwn(totals, "E_WASTE (ITEM)") ? "Item counts; weights kept separate" : "No e-waste item count recorded", "⌘")}
    ${statCard("Awaiting collection", number(data.awaitingCollection), "Records marked pending", "↗")}
    ${statCard("Records needing segregation", number(data.segregationNeedsCorrection), "Require staff follow-up", "⚑")}
    </div><div class="row g-3 mb-4"><div class="col-lg-7"><div class="panel"><h2 class="panel-title">Waste totals by category</h2><p class="panel-subtitle">Categories remain in their recorded units; quantities are not combined across units.</p><div class="chart-wrap"><canvas id="waste-chart" aria-label="Waste total by category chart"></canvas></div></div></div>
    <div class="col-lg-5"><div class="panel h-100"><h2 class="panel-title">Collection status</h2><p class="panel-subtitle">Number of active records by current status</p><div class="chart-wrap"><canvas id="status-chart" aria-label="Collection status chart"></canvas></div></div></div></div>
    <div class="row g-3 mb-4"><div class="col-lg-7"><div class="panel"><h2 class="panel-title">Monthly recorded quantities</h2><p class="panel-subtitle">Monthly totals shown separately for each unit.</p><div class="chart-wrap"><canvas id="month-chart" aria-label="Monthly recorded quantities chart"></canvas></div></div></div>
    <div class="col-lg-5"><div class="panel"><h2 class="panel-title">Recorded quantity by location</h2><p class="panel-subtitle">Each bar retains its unit.</p><div class="chart-wrap"><canvas id="location-chart" aria-label="Quantity by location chart"></canvas></div></div></div></div>
    <div class="row g-3"><div class="col-lg-8"><div class="table-card"><div class="table-toolbar"><div><h2 class="panel-title">Recently recorded</h2><p class="panel-subtitle mb-0">Latest active waste records</p></div><a href="#records" class="btn btn-outline-success btn-sm">All records</a></div>${tableShell(["Record","Category / item","Location","Quantity","Status","Date","Actions"], recent, "No records yet", "")}</div></div>
    <div class="col-lg-4"><div class="panel h-100"><h2 class="panel-title">Data integrity</h2><p class="panel-subtitle">Current database context</p><div class="settings-row"><strong>${number(data.recordCount)}</strong><span class="text-secondary ms-2">active records</span></div><div class="settings-row"><strong>${number(data.demoRecordCount)}</strong><span class="text-secondary ms-2">clearly labelled demo records</span></div><p class="small text-secondary mt-3 mb-0">Environmental impact reductions are not estimated by this system.</p></div></div></div>
    <div class="panel mt-3"><div class="table-toolbar px-0 pt-0"><div><h2 class="panel-title">Alerts requiring attention</h2><p class="panel-subtitle mb-0">${openAlerts.length} open rule-based alerts</p></div><a href="#alerts" class="btn btn-outline-success btn-sm">View all alerts</a></div>
      ${openAlerts.length ? `<div class="d-flex flex-column gap-2">${openAlerts.slice(0, 4).map(alert => `<div class="d-flex align-items-center justify-content-between gap-3 border-top pt-2"><div><span class="badge-status status-scheduled">${escapeHtml(alert.type.replaceAll("_", " "))}</span><span class="ms-2">${escapeHtml(alert.message)}</span></div>${canEdit() ? `<button class="btn btn-outline-success btn-sm text-nowrap" data-action="resolve-alert" data-id="${alert.id}">Resolve</button>` : ""}</div>`).join("")}</div>` : `<p class="small text-secondary mb-0">No open alerts.</p>`}
    </div>`;
  const categoryTotals = data.totalsByCategory || {};
  const categoryLabels = Object.keys(categoryTotals);
  drawChart("waste-chart", "bar", categoryLabels, Object.values(categoryTotals), "Recorded quantity in the unit shown");
  drawChart("status-chart", "doughnut", Object.keys(data.recordsByStatus || {}), Object.values(data.recordsByStatus || {}), "Records");
  drawChart("month-chart", "bar", Object.keys(data.totalsByMonth || {}), Object.values(data.totalsByMonth || {}), "Recorded quantity in the unit shown");
  drawChart("location-chart", "bar", Object.keys(data.totalsByLocation || {}), Object.values(data.totalsByLocation || {}), "Recorded quantity in the unit shown");
}
function statCard(label, value, foot, icon) {
  return `<div class="col-sm-6 col-xl-3"><div class="stat-card"><span class="stat-icon">${icon}</span><div class="stat-label">${label}</div><div class="stat-value">${value}</div><div class="stat-foot">${foot}</div></div></div>`;
}
function recordRow(r) {
  const editAction = r.category === "E_WASTE" ? "edit-ewaste" : "edit";
  const actions = canEdit() ? `<div class="action-buttons">${state.user.role === "ADMIN" ? `<button class="btn btn-outline-danger" data-action="deactivate" data-id="${r.id}">Deactivate</button>` : ""}<button class="btn btn-outline-success" data-action="${editAction}" data-id="${r.id}">Edit</button><button class="btn btn-outline-secondary" data-action="history" data-id="${r.id}">History</button><button class="btn btn-outline-primary" data-action="collect" data-id="${r.id}">Collect</button><button class="btn btn-outline-dark" data-action="handover" data-id="${r.id}">Handover</button></div>` : `<button class="btn btn-outline-secondary btn-sm" data-action="history" data-id="${r.id}">History</button>`;
  return `<tr><td><span class="text-secondary font-monospace">${escapeHtml(r.id.slice(0, 8))}</span>${r.demo ? '<span class="demo-tag">DEMO</span>' : ""}</td><td><strong>${escapeHtml(r.subtype)}</strong><small class="d-block text-secondary">${escapeHtml(r.category.replace("_", "-"))}</small></td><td>${escapeHtml(r.location)}</td><td>${number(r.quantity)} ${r.unit === "KG" ? "kg" : "items"}<small class="d-block text-secondary">Remaining: ${number(r.remainingQuantity)}</small></td><td>${statusBadge(r.collectionStatus)}</td><td>${escapeHtml(r.recordedAt)}</td><td>${actions}</td></tr>`;
}
async function getRecords(params = {}) {
  const query = new URLSearchParams({ page: String(state.page), size: String(state.pageSize), sort: "recordedAt,desc" });
  Object.entries(params).forEach(([key, val]) => { if (val) query.set(key, val); });
  return api(`/api/records?${query}`);
}
function pagination(pageData) {
  const last = Math.max(0, pageData.totalPages - 1);
  return `<div class="pagination-area"><span>${pageData.totalElements ? `Page ${pageData.number + 1} of ${Math.max(1, pageData.totalPages)} · ${pageData.totalElements} records` : "0 records"}</span><div class="d-flex gap-2"><button class="btn btn-outline-secondary" data-action="page-prev" ${pageData.first ? "disabled" : ""}>Previous</button><button class="btn btn-outline-secondary" data-action="page-next" ${pageData.last ? "disabled" : ""}>Next</button></div></div>`;
}
async function renderRecords(mode = "all") {
  const target = $(`#page-${state.currentPage}`);
  const page = state.currentPage;
  target.innerHTML = `<div class="loading">Loading waste records…</div>`;
  const locations = await api("/api/locations");
  state.locations = locations.filter(l => l.active);
  const filterValues = { ...(state.filters[page] || {}) };
  if (mode === "segregation") filterValues.segregation = true;
  if (mode === "ewaste") filterValues.category = "E_WASTE";
  if (mode === "collection") filterValues.collection = true;
  let params = { q: filterValues.q, category: filterValues.category, status: filterValues.status, locationId: filterValues.locationId };
  if (mode === "segregation" || mode === "collection") {
    const result = await api("/api/records?size=100");
    const selected = result.content.filter(r => mode === "segregation" ? r.segregationStatus === "NEEDS_CORRECTION" :
      r.collectionStatus === "PENDING" || r.collectionStatus === "SCHEDULED");
    target.innerHTML = `${heading(mode === "segregation" ? "Segregation follow-up" : "Collection tracking",
      mode === "segregation" ? "Records flagged for staff review and correction." : "Track pending collections and record quantities separately from authorized handovers.",
      canEdit() ? '<button class="btn btn-green" data-action="new-record">＋ Add record</button>' : "")}
      ${tableShell(["Record","Category / item","Location","Quantity","Status","Recorded","Actions"], selected.map(recordRow).join(""), mode === "segregation" ? "No records need correction" : "No records are awaiting collection")}`;
    return;
  }
  const pageData = await getRecords(params);
  let rows = pageData.content;
  if (filterValues.segregation) rows = rows.filter(r => r.segregationStatus === "NEEDS_CORRECTION");
  if (filterValues.category === "E_WASTE" && page === "ewaste") {
    const title = "E-waste inventory";
    target.innerHTML = `${heading(title, "Item counts are tracked separately from weight. Authorised handover is recorded as a separate stage.", canEdit() ? '<button class="btn btn-green" data-action="new-record" data-category="E_WASTE">＋ Add e-waste item</button>' : "")}
      ${filtersMarkup(locations, filterValues, false)}${tableShell(["Record","Item / type","Campus location","Count","Collected","Handover","Status","Actions"], rows.map(r => `<tr><td>${escapeHtml(r.id.slice(0,8))}${r.demo ? '<span class="demo-tag">DEMO</span>' : ""}</td><td><strong>${escapeHtml(r.subtype)}</strong></td><td>${escapeHtml(r.location)}</td><td>${number(r.quantity)} items</td><td>${number(r.collectedQuantity)}</td><td>${number(r.handedOverQuantity)}</td><td>${statusBadge(r.collectionStatus)}</td><td>${canEdit() ? `<button class="btn btn-outline-success btn-sm" data-action="edit" data-id="${r.id}">Edit</button> <button class="btn btn-outline-dark btn-sm" data-action="handover" data-id="${r.id}">Handover</button>` : ""}</td></tr>`).join(""), "No e-waste inventory recorded", pagination(pageData))}`;
    return;
  }
  const headingTitle = page === "records" ? "Waste records" : titles[page];
  const desc = page === "records" ? "Search, filter, and maintain measured campus waste records." : "Waste records can be filtered and reviewed by campus location and collection status.";
  target.innerHTML = `${heading(headingTitle, desc, canEdit() ? '<button class="btn btn-green" data-action="new-record">＋ Add waste record</button>' : "")}
    ${filtersMarkup(locations, filterValues, true)}${tableShell(["Record","Category / item","Location","Quantity","Collection","Date","Actions"], rows.map(recordRow).join(""), "No matching records", pagination(pageData))}`;
}
function filtersMarkup(locations, values, categoryFilter) {
  return `<div class="filter-bar mb-3"><input class="form-control form-control-sm" data-filter="q" value="${escapeHtml(values.q || "")}" placeholder="Search records…" aria-label="Search records">
    ${categoryFilter ? `<select class="form-select form-select-sm" data-filter="category"><option value="">All categories</option><option value="PLASTIC" ${values.category === "PLASTIC" ? "selected" : ""}>Plastic</option><option value="E_WASTE" ${values.category === "E_WASTE" ? "selected" : ""}>E-waste</option></select>` : ""}
    <select class="form-select form-select-sm" data-filter="status"><option value="">All statuses</option>${["PENDING","SCHEDULED","COLLECTED","HANDED_OVER"].map(v => `<option value="${v}" ${values.status === v ? "selected" : ""}>${v.replace("_", " ")}</option>`).join("")}</select>
    <select class="form-select form-select-sm" data-filter="locationId"><option value="">All locations</option>${locations.map(l => `<option value="${l.id}" ${values.locationId === l.id ? "selected" : ""}>${escapeHtml(l.name)}</option>`).join("")}</select>
    <button class="btn btn-outline-secondary btn-sm" data-action="clear-filters">Clear</button></div>`;
}
async function renderSegregation() {
  await renderRecords("segregation");
}
async function renderCollection() {
  await renderRecords("collection");
}
async function renderEwaste() {
  const target = $("#page-ewaste");
  const [items, records] = await Promise.all([api("/api/ewaste"), api("/api/records?category=E_WASTE&size=100")]);
  const knownRecords = new Set(items.map(item => item.recordId));
  const incomplete = records.content.filter(record => !knownRecords.has(record.id));
  target.innerHTML = `${heading("E-waste inventory", "Track item counts, condition, storage location, collection, and authorized handover separately.", canEdit() ? '<button class="btn btn-green" data-action="new-record" data-category="E_WASTE">＋ Add e-waste item</button>' : "")}
    ${incomplete.length ? `<div class="alert alert-warning small">Some e-waste records need condition and storage information. Complete each item below so the inventory is accurate.</div>` : ""}
    ${tableShell(["Item","Condition","Storage","Campus location","Count","Collected","Handed over","Handover recipient","Reported","Actions"], [
      ...items.map(item => `<tr><td><strong>${escapeHtml(item.itemType)}</strong>${item.demo ? '<span class="demo-tag">DEMO</span>' : ""}</td><td>${statusBadge(item.condition)}</td><td>${escapeHtml(item.storageLocation)}</td><td>${escapeHtml(item.location)}</td><td>${number(item.quantity)} items</td><td>${number(item.collectedQuantity)}</td><td>${number(item.handedOverQuantity)}</td><td>${escapeHtml(item.authorizedRecipient || "—")}</td><td>${escapeHtml(item.reportedAt)}</td><td>${canEdit() ? `<button class="btn btn-outline-success btn-sm" data-action="edit-ewaste" data-id="${item.recordId}">Edit</button> <button class="btn btn-outline-primary btn-sm" data-action="collect" data-id="${item.recordId}">Collect</button> <button class="btn btn-outline-dark btn-sm" data-action="handover" data-id="${item.recordId}">Handover</button> ${isAdmin() ? `<button class="btn btn-outline-danger btn-sm" data-action="deactivate-ewaste" data-id="${item.recordId}">Deactivate</button>` : ""}` : ""}</td></tr>`),
      ...incomplete.map(record => `<tr><td><strong>${escapeHtml(record.subtype)}</strong>${record.demo ? '<span class="demo-tag">DEMO</span>' : ""}</td><td>${statusBadge("NOT_VERIFIED")}</td><td><span class="text-danger">Missing details</span></td><td>${escapeHtml(record.location)}</td><td>${number(record.quantity)} items</td><td>${number(record.collectedQuantity)}</td><td>${number(record.handedOverQuantity)}</td><td>—</td><td>${escapeHtml(record.recordedAt)}</td><td>${canEdit() ? `<button class="btn btn-outline-warning btn-sm" data-action="complete-ewaste" data-id="${record.id}">Complete details</button>` : ""}</td></tr>`)
    ].join(""), "No e-waste inventory", "Add an item with condition and designated storage details.")}`;
}
async function renderReports() {
  const target = $("#page-reports");
  const [locations, inventory] = await Promise.all([api("/api/locations"), api("/api/ewaste")]);
  const itemTypes = [...new Set(inventory.map(item => item.itemType))].sort((a, b) => a.localeCompare(b));
  target.innerHTML = `${heading("Reports & exports", "Filter measured records by date, waste type, location, collection status, and segregation status.")}
    <div class="panel mb-3"><div class="row g-3 align-items-end">
      <div class="col-sm-6 col-lg-2"><label class="form-label" for="report-from">From</label><input class="form-control" id="report-from" type="date"></div>
      <div class="col-sm-6 col-lg-2"><label class="form-label" for="report-to">To</label><input class="form-control" id="report-to" type="date"></div>
      <div class="col-sm-6 col-lg-2"><label class="form-label" for="report-category">Category</label><select class="form-select" id="report-category"><option value="">All</option><option value="PLASTIC">Plastic</option><option value="E_WASTE">E-waste</option></select></div>
      <div class="col-sm-6 col-lg-2"><label class="form-label" for="report-status">Collection status</label><select class="form-select" id="report-status"><option value="">All</option>${["PENDING","SCHEDULED","COLLECTED","HANDED_OVER"].map(s => `<option value="${s}">${s.replace("_", " ")}</option>`).join("")}</select></div>
      <div class="col-sm-6 col-lg-2"><label class="form-label" for="report-segregation">Segregation</label><select class="form-select" id="report-segregation"><option value="">All</option>${["NOT_VERIFIED","PROPER","NEEDS_CORRECTION"].map(s => `<option value="${s}">${s.replaceAll("_", " ")}</option>`).join("")}</select></div>
      <div class="col-sm-6 col-lg-2"><label class="form-label" for="report-item-type">E-waste item type</label><select class="form-select" id="report-item-type"><option value="">All item types</option>${itemTypes.map(type => `<option value="${escapeHtml(type)}">${escapeHtml(type)}</option>`).join("")}</select></div>
      <div class="col-sm-6 col-lg-2"><label class="form-label" for="report-location">Location</label><select class="form-select" id="report-location"><option value="">All locations</option>${locations.filter(l => l.active).map(l => `<option value="${l.id}">${escapeHtml(l.name)}</option>`).join("")}</select></div>
      <div class="col-sm-6 col-lg-2"><button class="btn btn-green w-100" data-action="report-filter">Apply filters</button></div>
      <div class="col-sm-6 col-lg-2"><button class="btn btn-outline-success w-100" data-action="export-csv">Export CSV</button></div>
    </div><div id="report-generated" class="small text-secondary mt-3">Choose filters and apply to view a database-backed report.</div></div>
    <div class="row g-3 mb-3"><div class="col-lg-6"><div class="panel"><h2 class="panel-title">Recorded quantity by month</h2><div class="chart-wrap"><canvas id="report-month-chart"></canvas></div></div></div><div class="col-lg-6"><div class="panel"><h2 class="panel-title">Recorded quantity by location</h2><div class="chart-wrap"><canvas id="report-location-chart"></canvas></div></div></div></div>
    <div id="report-table">${empty("No report generated", "Select filters and apply to query database records.")}</div>`;
  await applyReportFilters();
}
async function renderLocations() {
  const target = $("#page-locations");
  const locations = await api("/api/locations");
  target.innerHTML = `${heading("Campus locations", "Locations are administrator-configured. Examples must be clearly labelled as demo data.", isAdmin() ? '<button class="btn btn-green" data-action="new-location">＋ Add location</button>' : "")}
    <div class="row g-3">${locations.map(l => `<div class="col-md-6 col-xl-4"><div class="info-card"><div class="d-flex justify-content-between"><div class="awareness-icon">⌖</div>${l.demo ? '<span class="demo-tag align-self-start">DEMO</span>' : ""}</div><h3>${escapeHtml(l.name)}</h3><p>${escapeHtml(l.description || "No description provided.")}</p><div class="d-flex justify-content-between align-items-center"><span class="badge-status ${l.active ? "status-proper" : "status-not_verified"}">${l.active ? "Active" : "Inactive"}</span>${isAdmin() && l.active ? `<span><button class="btn btn-outline-success btn-sm" data-action="edit-location" data-id="${l.id}" data-name="${escapeHtml(l.name)}" data-description="${escapeHtml(l.description || "")}" data-demo="${l.demo}">Edit</button> <button class="btn btn-outline-danger btn-sm" data-action="deactivate-location" data-id="${l.id}">Deactivate</button></span>` : ""}</div></div></div>`).join("") || `<div class="col-12">${empty("No campus locations configured", "An administrator can add verified locations.")}</div>`}</div>`;
}
async function renderAwareness() {
  const target = $("#page-awareness");
  const data = await api("/api/awareness");
  const icons = ["♻", "⌘", "✓"];
  target.innerHTML = `${heading(data.title, "Practical guidance for students, faculty, and campus staff. Educational guidance is separate from measured waste statistics.")}
    <div class="row g-3">${data.sections.map((section, i) => `<div class="col-md-6 col-xl-4"><article class="info-card"><div class="awareness-icon">${icons[i % icons.length]}</div><h3>${escapeHtml(section.title)}</h3><ul class="mb-0">${section.items.map(item => `<li>${escapeHtml(item)}</li>`).join("")}</ul></article></div>`).join("")}</div>
    <div class="alert alert-success mt-4 small"><strong>Local guidance matters.</strong> Follow campus facilities instructions and applicable local regulations. This application monitors records; it does not physically segregate waste.</div>`;
}
async function renderAlerts() {
  const target = $("#page-alerts");
  const alerts = await api("/api/alerts");
  const open = alerts.filter(a => !a.resolved).length;
  $("#alert-count").textContent = open;
  target.innerHTML = `${heading("Notifications & alerts", `${open} unresolved rule-based alert${open === 1 ? "" : "s"}. Collection overdue threshold is configured by the administrator.`)}
    <div class="d-flex flex-column gap-2">${alerts.map(a => `<div class="panel status-alert ${a.resolved ? "opacity-75" : ""}"><div class="d-flex justify-content-between align-items-start gap-3"><div><div class="d-flex align-items-center gap-2"><span class="badge-status status-scheduled">${escapeHtml(a.type.replaceAll("_", " "))}</span><strong>${escapeHtml(a.message)}</strong>${a.resolved ? '<span class="badge-status status-proper">Resolved</span>' : '<span class="badge-status status-pending">Open</span>'}</div><div class="small text-secondary mt-2">${a.recordId ? `Record ${escapeHtml(a.recordId.slice(0,8))} · ` : ""}Created ${new Date(a.createdAt).toLocaleString()}</div></div>${!a.resolved && canEdit() ? `<button class="btn btn-outline-success btn-sm text-nowrap" data-action="resolve-alert" data-id="${a.id}">Mark resolved</button>` : ""}</div></div>`).join("") || empty("No alerts", "Rule-based alerts will appear when records meet a configured condition.")}</div>`;
}
async function renderUsers() {
  const users = await api("/api/users");
  $("#page-users").innerHTML = `${heading("User management", "Create accounts, change roles, reset passwords, and deactivate access. Passwords are BCrypt-hashed and never returned.", '<button class="btn btn-green" data-action="new-user">＋ Add user</button>')}
    ${tableShell(["Username","Display name","Role","Status","Actions"], users.map(u => `<tr><td>${escapeHtml(u.username)}</td><td>${escapeHtml(u.displayName)}</td><td>${escapeHtml(u.role)}</td><td>${u.active ? "Active" : "Inactive"}</td><td><button class="btn btn-outline-success btn-sm" data-action="edit-user" data-id="${u.id}" data-name="${escapeHtml(u.displayName)}" data-role="${u.role}" data-active="${u.active}">Edit access</button></td></tr>`).join(""), "No user accounts")}`;
}
async function renderAudit() {
  const result = await api(`/api/audit-logs?page=${state.page}&size=${state.pageSize}`);
  const footer = pagination(result);
  $("#page-audit").innerHTML = `${heading("Audit log", "Administrator-only history of important account, record, collection, location, and handover changes.")}
    ${tableShell(["Time","User","Action","Entity","Entity ID","Details"], result.content.map(entry => `<tr><td>${new Date(entry.occurredAt).toLocaleString()}</td><td>${escapeHtml(entry.displayName)} <small class="d-block text-secondary">${escapeHtml(entry.username)}</small></td><td>${escapeHtml(entry.action)}</td><td>${escapeHtml(entry.entityType)}</td><td class="font-monospace">${escapeHtml(entry.entityId || "—")}</td><td>${escapeHtml(entry.details || "")}</td></tr>`).join(""), "No audit events", footer)}`;
}
function renderSettings() {
  $("#page-settings").innerHTML = `${heading("Profile & settings", "Your authenticated account and application configuration.")}
    <div class="panel profile-card"><div class="d-flex align-items-center gap-3 mb-3"><div class="avatar">${escapeHtml(state.user.displayName.slice(0,1).toUpperCase())}</div><div><strong>${escapeHtml(state.user.displayName)}</strong><div class="text-secondary small">${escapeHtml(state.user.username)}</div></div></div>
    <div class="settings-row"><span class="text-secondary">Role</span><strong class="float-end">${escapeHtml(state.user.role)}</strong></div>
    <div class="settings-row"><span class="text-secondary">Authentication</span><strong class="float-end">Secure session</strong></div>
    <div class="settings-row"><span class="text-secondary">Password storage</span><strong class="float-end">BCrypt hash</strong></div>
    <p class="small text-secondary mt-3 mb-0">Administrator alert thresholds are set through the <code>COLLECTION_OVERDUE_DAYS</code> environment variable.</p></div>`;
}

async function openRecord(record = null, category = null) {
  const locations = await api("/api/locations");
  state.locations = locations.filter(l => l.active);
  $("#record-location").innerHTML = state.locations.map(l => `<option value="${l.id}">${escapeHtml(l.name)}${l.demo ? " (demo)" : ""}</option>`).join("");
  if (!state.locations.length) { notify("Configure an active campus location before adding records.", true); return; }
  $("#record-id").value = record?.id || "";
  $("#record-modal-title").textContent = record ? (record.category === "E_WASTE" ? "Edit e-waste item" : "Edit waste record")
    : (category === "E_WASTE" ? "Add e-waste item" : "Add waste record");
  $("#record-category").value = record?.category || category || "PLASTIC";
  const isEWaste = $("#record-category").value === "E_WASTE";
  $("#record-subtype").value = record?.subtype || "";
  $("#record-quantity").value = record?.quantity ?? "";
  $("#record-quantity").step = isEWaste ? "1" : "0.001";
  $("#record-quantity").min = isEWaste ? "1" : "0.001";
  $("#record-unit").value = record?.unit || ($("#record-category").value === "PLASTIC" ? "KG" : "ITEM");
  $("#record-date").value = record?.recordedAt || new Date().toISOString().slice(0,10);
  $("#record-location").value = record?.locationId || state.locations[0].id;
  $("#record-segregation").value = record?.segregationStatus || "NOT_VERIFIED";
  $("#record-notes").value = record?.notes || "";
  $("#record-demo").checked = record?.demo || false;
  $("#ewaste-condition").value = record?.ewasteCondition || "";
  $("#ewaste-storage").value = record?.storageLocation || "";
  $$(".ewaste-only").forEach(field => field.classList.toggle("d-none", !isEWaste));
  $("#record-form-error").textContent = "";
  $("#record-category").disabled = Boolean(record);
  updateUnitHelp();
  bootstrap.Modal.getOrCreateInstance($("#record-modal")).show();
}
function openLocationModal(location = null) {
  $("#location-id").value = location?.id || "";
  $("#location-modal-title").textContent = location ? "Edit campus location" : "Add campus location";
  $("#location-name").value = location?.name || "";
  $("#location-description").value = location?.description || "";
  $("#location-demo").checked = location?.demo || false;
  $("#location-demo-field").classList.toggle("d-none", Boolean(location));
  $("#location-form-error").textContent = "";
  bootstrap.Modal.getOrCreateInstance($("#location-modal")).show();
}
function openCollectionAction(record, action) {
  const handover = action === "handover";
  const available = handover ? record.awaitingHandoverQuantity : record.remainingQuantity;
  const unit = record.unit === "ITEM" ? "items" : "kg";
  $("#collection-action-id").value = record.id;
  $("#collection-action-type").value = action;
  $("#collection-action-title").textContent = handover ? "Record authorized handover" : "Record collection";
  $("#collection-action-available").textContent = handover
    ? `${available} ${unit} collected and awaiting handover.`
    : `${available} ${unit} remain available for collection.`;
  $("#collection-action-quantity-label").textContent = `Quantity (${unit})`;
  const quantity = $("#collection-action-quantity");
  quantity.min = record.unit === "ITEM" ? "1" : "0.001";
  quantity.step = record.unit === "ITEM" ? "1" : "0.001";
  quantity.max = String(available);
  quantity.value = "";
  $("#collection-action-notes").value = "";
  $("#collection-action-recipient").value = "";
  $("#collection-action-notes-field").classList.toggle("d-none", handover);
  $("#collection-action-recipient-field").classList.toggle("d-none", !handover);
  $("#collection-action-recipient").required = handover;
  $("#collection-action-error").textContent = "";
  bootstrap.Modal.getOrCreateInstance($("#collection-action-modal")).show();
}
function updateUnitHelp() {
  const plastic = $("#record-category").value === "PLASTIC";
  $$(".ewaste-only").forEach(field => field.classList.toggle("d-none", plastic));
  $("#ewaste-condition").required = !plastic;
  $("#ewaste-storage").required = !plastic;
  $("#record-unit").value = plastic ? "KG" : "ITEM";
  $("#record-unit").disabled = true;
  $("#unit-help").textContent = plastic ? "Plastic waste is tracked in kilograms." : "E-waste counts are tracked as items; weight is not combined.";
}
async function runAction(button) {
  const id = button.dataset.id;
  switch (button.dataset.action) {
    case "new-record": await openRecord(null, button.dataset.category); break;
    case "edit": {
      const record = await api(`/api/records/${id}`);
      await openRecord(record);
      break;
    }
    case "edit-ewaste": {
      const items = await api("/api/ewaste");
      const item = items.find(value => value.recordId === id);
      if (!item) {
        const record = await api(`/api/records/${id}`);
        await openRecord({ ...record, ewasteCondition: "", storageLocation: "" });
        break;
      }
      await openRecord({
        id: item.recordId, category: "E_WASTE", subtype: item.itemType, quantity: item.quantity,
        unit: "ITEM", recordedAt: item.reportedAt, locationId: item.locationId,
        segregationStatus: item.segregationStatus, notes: item.notes, demo: item.demo,
        ewasteCondition: item.condition, storageLocation: item.storageLocation
      });
      break;
    }
    case "complete-ewaste": {
      const record = await api(`/api/records/${id}`);
      await openRecord({ ...record, ewasteCondition: "", storageLocation: "" });
      break;
    }
    case "deactivate":
      if (confirm("Deactivate this record? It will be excluded from current reports but retained in the database history.")) {
        await api(`/api/records/${id}`, { method: "DELETE" }); notify("Record deactivated."); await renderPage(state.currentPage);
      }
      break;
    case "deactivate-ewaste":
      if (confirm("Deactivate this e-waste record? Its collection and handover history will remain.")) {
        await api(`/api/ewaste/${id}`, { method: "DELETE" });
        notify("E-waste item deactivated.");
        await renderEwaste();
      }
      break;
    case "collect": {
      const record = await api(`/api/records/${id}`);
      if (!record.remainingQuantity) return notify("No remaining quantity to collect.", true);
      openCollectionAction(record, "collect");
      break;
    }
    case "handover": {
      const record = await api(`/api/records/${id}`);
      if (!record.awaitingHandoverQuantity) return notify("No collected quantity is awaiting handover.", true);
      openCollectionAction(record, "handover");
      break;
    }
    case "history": {
      const events = await api(`/api/records/${id}/history`);
      alert(events.length ? events.map(e => `${new Date(e.occurredAt).toLocaleString()} · ${e.action} · ${e.quantity} · ${e.performedBy}${e.recipient ? ` · ${e.recipient}` : ""}`).join("\n") : "No collection or handover history for this record.");
      break;
    }
    case "page-prev": state.page = Math.max(0, state.page - 1); await renderPage(state.currentPage); break;
    case "page-next": state.page += 1; await renderPage(state.currentPage); break;
    case "clear-filters": state.filters[state.currentPage] = {}; state.page = 0; await renderPage(state.currentPage); break;
    case "resolve-alert": await api(`/api/alerts/${id}/resolve`, { method: "POST" }); notify("Alert marked resolved."); await renderPage(state.currentPage); break;
    case "new-location": openLocationModal(); break;
    case "edit-location":
      openLocationModal({
        id,
        name: button.dataset.name,
        description: button.dataset.description,
        demo: button.dataset.demo === "true"
      });
      break;
    case "deactivate-location":
      if (confirm("Deactivate this location? Historical records will remain.")) {
        await api(`/api/locations/${id}`, { method: "DELETE" }); notify("Location deactivated."); await renderLocations();
      }
      break;
    case "report-filter": state.reportPage = 0; await applyReportFilters(); break;
    case "report-page-prev": state.reportPage = Math.max(0, state.reportPage - 1); await applyReportFilters(); break;
    case "report-page-next": state.reportPage += 1; await applyReportFilters(); break;
    case "export-csv": await exportReport(); break;
    case "new-user": {
      const username = prompt("Username for the new account:");
      if (!username?.trim()) return;
      const displayName = prompt("Display name:");
      if (!displayName?.trim()) return;
      const role = prompt("Role (ADMIN, STAFF, VIEWER):", "VIEWER")?.toUpperCase();
      if (!["ADMIN", "STAFF", "VIEWER"].includes(role)) return notify("Choose ADMIN, STAFF, or VIEWER.", true);
      const password = prompt("Initial password (at least 12 characters):");
      if (!password || password.length < 12) return notify("Password must be at least 12 characters.", true);
      await api("/api/users", { method: "POST", body: JSON.stringify({ username, displayName, role, password }) });
      notify("User account created.");
      await renderUsers();
      break;
    }
    case "edit-user": {
      const displayName = prompt("Display name:", button.dataset.name);
      if (!displayName?.trim()) return;
      const role = prompt("Role (ADMIN, STAFF, VIEWER):", button.dataset.role)?.toUpperCase();
      if (!["ADMIN", "STAFF", "VIEWER"].includes(role)) return notify("Choose ADMIN, STAFF, or VIEWER.", true);
      const active = confirm("Keep this account active? Choose Cancel to deactivate.");
      const password = prompt("New password (leave blank to keep current password):");
      if (password && password.length < 12) return notify("New password must be at least 12 characters.", true);
      const body = { displayName, role, active };
      if (password) body.password = password;
      await api(`/api/users/${id}`, { method: "PUT", body: JSON.stringify(body) });
      notify("User account updated.");
      await renderUsers();
      break;
    }
  }
}
function reportParams() {
  const params = new URLSearchParams();
  if ($("#report-from").value) params.set("from", $("#report-from").value);
  if ($("#report-to").value) params.set("to", $("#report-to").value);
  if ($("#report-category").value) params.set("category", $("#report-category").value);
  if ($("#report-status").value) params.set("status", $("#report-status").value);
  if ($("#report-segregation").value) params.set("segregationStatus", $("#report-segregation").value);
  if ($("#report-location").value) params.set("locationId", $("#report-location").value);
  if ($("#report-item-type").value) params.set("itemType", $("#report-item-type").value);
  return params;
}
async function applyReportFilters() {
  const p = reportParams();
  p.set("page", String(state.reportPage));
  p.set("size", String(state.pageSize));
  const summary = await api(`/api/reports/summary?${p}`);
  const rows = summary.records.map(r => `<tr><td>${escapeHtml(r.id.slice(0,8))}${r.demo ? '<span class="demo-tag">DEMO</span>' : ""}</td><td>${escapeHtml(r.category)} · ${escapeHtml(r.subtype)}</td><td>${escapeHtml(r.location)}</td><td>${number(r.quantity)} ${r.unit === "KG" ? "kg" : "items"}</td><td>${statusBadge(r.collectionStatus)}</td><td>${statusBadge(r.segregationStatus)}</td><td>${escapeHtml(r.recordedAt)}</td></tr>`).join("");
  const reportFooter = `<div class="pagination-area"><span>${summary.recordCount ? `Page ${summary.page + 1} of ${Math.max(1, summary.totalPages)} · ${summary.recordCount} records` : "0 records"}</span><div class="d-flex gap-2"><button class="btn btn-outline-secondary" data-action="report-page-prev" ${summary.page <= 0 ? "disabled" : ""}>Previous</button><button class="btn btn-outline-secondary" data-action="report-page-next" ${summary.page + 1 >= summary.totalPages ? "disabled" : ""}>Next</button></div></div>`;
  $("#report-table").innerHTML = tableShell(["Record","Category / item","Location","Quantity","Collection","Segregation","Date"], rows, "No matching records", reportFooter);
  $("#report-generated").textContent = `Generated ${new Date().toLocaleString()} · ${summary.recordCount} matching database records · charts retain recorded units`;
  destroyCharts();
  drawChart("report-month-chart", "bar", Object.keys(summary.totalsByMonth || {}), Object.values(summary.totalsByMonth || {}), "Recorded quantity in the unit shown");
  drawChart("report-location-chart", "bar", Object.keys(summary.totalsByLocation || {}), Object.values(summary.totalsByLocation || {}), "Recorded quantity in the unit shown");
  notify(`${summary.recordCount} report rows matched.`);
}
async function exportReport() {
  const response = await fetch(`/api/reports.csv?${reportParams()}`, { credentials: "same-origin" });
  if (!response.ok) throw new Error("CSV export failed.");
  const blob = await response.blob();
  const link = document.createElement("a");
  link.href = URL.createObjectURL(blob);
  link.download = "green-campus-report.csv";
  link.click();
  URL.revokeObjectURL(link.href);
}

document.addEventListener("click", event => {
  const button = event.target.closest("[data-action]");
  if (button) runAction(button).catch(error => notify(error.message, true));
});
document.addEventListener("change", event => {
  if (event.target.id === "record-category") updateUnitHelp();
  if (event.target.matches("[data-filter]")) {
    state.filters[state.currentPage] ||= {};
    state.filters[state.currentPage][event.target.dataset.filter] = event.target.value;
    state.page = 0;
    renderPage(state.currentPage).catch(error => notify(error.message, true));
  }
});
document.addEventListener("keydown", event => {
  if (event.target.matches('[data-filter="q"]') && event.key === "Enter") {
    state.filters[state.currentPage] ||= {};
    state.filters[state.currentPage].q = event.target.value;
    state.page = 0;
    renderPage(state.currentPage).catch(error => notify(error.message, true));
  }
});
$("#login-form").addEventListener("submit", async event => {
  event.preventDefault();
  $("#login-error").textContent = "";
  try {
    await refreshCsrf();
    const user = await api("/api/auth/login", { method: "POST", body: JSON.stringify({ username: $("#username").value, password: $("#password").value }) });
    await refreshCsrf();
    showApp(user);
  } catch (error) { $("#login-error").textContent = error.message; }
});
$("#record-form").addEventListener("submit", async event => {
  event.preventDefault();
  const id = $("#record-id").value;
  const eWaste = $("#record-category").value === "E_WASTE";
  const body = eWaste ? {
    itemType: $("#record-subtype").value.trim(), quantity: Number($("#record-quantity").value),
    locationId: $("#record-location").value, reportedAt: $("#record-date").value,
    condition: $("#ewaste-condition").value, storageLocation: $("#ewaste-storage").value.trim(),
    segregationStatus: $("#record-segregation").value, notes: $("#record-notes").value,
    demo: $("#record-demo").checked
  } : {
    category: "PLASTIC", subtype: $("#record-subtype").value.trim(),
    quantity: Number($("#record-quantity").value), unit: "KG",
    locationId: $("#record-location").value, recordedAt: $("#record-date").value,
    segregationStatus: $("#record-segregation").value, notes: $("#record-notes").value,
    demo: $("#record-demo").checked
  };
  try {
    await api(eWaste ? (id ? `/api/ewaste/${id}` : "/api/ewaste") : (id ? `/api/records/${id}` : "/api/records"),
      { method: id ? "PUT" : "POST", body: JSON.stringify(body) });
    bootstrap.Modal.getInstance($("#record-modal")).hide();
    notify(id ? "Waste record updated." : "Waste record added.");
    await renderPage(state.currentPage);
  } catch (error) { $("#record-form-error").textContent = error.message; }
});
$("#location-form").addEventListener("submit", async event => {
  event.preventDefault();
  const id = $("#location-id").value;
  const name = $("#location-name").value.trim();
  if (!name) {
    $("#location-form-error").textContent = "Enter a campus location name.";
    $("#location-name").focus();
    return;
  }
  const body = {
    name,
    description: $("#location-description").value.trim(),
    demo: id ? false : $("#location-demo").checked
  };
  const submit = $('#location-form button[type="submit"]');
  submit.disabled = true;
  $("#location-form-error").textContent = "";
  try {
    await api(id ? `/api/locations/${id}` : "/api/locations", {
      method: id ? "PUT" : "POST",
      body: JSON.stringify(body)
    });
    bootstrap.Modal.getInstance($("#location-modal")).hide();
    notify(id ? "Location updated." : "Location added.");
    await renderLocations();
  } catch (error) {
    $("#location-form-error").textContent = error.message;
  } finally {
    submit.disabled = false;
  }
});
$("#collection-action-form").addEventListener("submit", async event => {
  event.preventDefault();
  const id = $("#collection-action-id").value;
  const handover = $("#collection-action-type").value === "handover";
  const quantity = Number($("#collection-action-quantity").value);
  const recipient = $("#collection-action-recipient").value.trim();
  if (!Number.isFinite(quantity) || quantity <= 0) {
    $("#collection-action-error").textContent = "Enter a quantity greater than zero.";
    return;
  }
  if (handover && !recipient) {
    $("#collection-action-error").textContent = "Enter the authorized recipient or recycler.";
    $("#collection-action-recipient").focus();
    return;
  }
  const submit = $('#collection-action-form button[type="submit"]');
  submit.disabled = true;
  $("#collection-action-error").textContent = "";
  try {
    const body = handover
      ? { quantity, recipient }
      : { quantity, notes: $("#collection-action-notes").value.trim() };
    await api(`/api/records/${id}/${handover ? "handovers" : "collections"}`, {
      method: "POST",
      body: JSON.stringify(body)
    });
    bootstrap.Modal.getInstance($("#collection-action-modal")).hide();
    notify(handover
      ? "Authorized handover recorded separately from collection."
      : "Collection quantity recorded; remaining waste stays pending.");
    await renderPage(state.currentPage);
  } catch (error) {
    $("#collection-action-error").textContent = error.message;
  } finally {
    submit.disabled = false;
  }
});
$("#logout").addEventListener("click", async () => {
  try { await api("/api/auth/logout", { method: "POST" }); } catch (error) { if (state.user) notify(error.message, true); }
  showLogin();
});
$("#menu-toggle").addEventListener("click", () => {
  const sidebar = $("#sidebar");
  const isOpen = sidebar.classList.toggle("open");
  $("#menu-toggle").setAttribute("aria-expanded", String(isOpen));
});
$("#main-nav").addEventListener("click", event => {
  const link = event.target.closest("[data-page]");
  if (!link) return;
  event.preventDefault();
  $("#sidebar").classList.remove("open");
  $("#menu-toggle").setAttribute("aria-expanded", "false");
  location.hash = link.dataset.page;
});
window.addEventListener("hashchange", () => { if (state.user) navigate(location.hash.slice(1)); });
async function initialize() {
  try {
    await refreshCsrf();
    const user = await api("/api/auth/me");
    showApp(user);
  } catch { showLogin(); }
}
initialize();
