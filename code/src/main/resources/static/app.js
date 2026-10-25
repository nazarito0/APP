const state = { user: null, view: 'catalog', authMode: 'login', search: '' };
const root = document.querySelector('#view-root');
const toast = document.querySelector('#toast');
const dialogs = ['auth-dialog', 'book-dialog', 'loan-dialog', 'reader-dialog', 'history-dialog', 'staff-dialog', 'password-dialog', 'staff-password-dialog'];
let toastTimer;
let editingBookId = null;
let editingReaderId = null;
let resettingStaffId = null;

function escapeHtml(value) {
  return String(value ?? '').replace(/[&<>"']/g, character => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[character]);
}

async function api(path, options = {}) {
  const headers = new Headers(options.headers || {});
  if (options.body) headers.set('Content-Type', 'application/json');
  const response = await fetch(path, { ...options, headers, credentials: 'same-origin' });
  if (response.status === 204) return null;
  const payload = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(payload.error || 'Не вдалося виконати запит');
  return payload;
}

function notify(message, isError = false) {
  toast.textContent = message;
  toast.classList.toggle('error', isError);
  toast.classList.add('visible');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toast.classList.remove('visible'), 3200);
}

function roleIs(...roles) {
  return roles.includes(state.user?.role);
}

function updateShell() {
  const roleNames = { READER: 'Читач', LIBRARIAN: 'Бібліотекар', ADMIN: 'Адміністратор' };
  document.querySelector('#account-label').textContent = state.user
    ? `${state.user.login} · ${roleNames[state.user.role] || state.user.role}` : 'Гість';
  document.querySelector('#auth-open').classList.toggle('hidden', Boolean(state.user));
  document.querySelector('#password-open').classList.toggle('hidden', !state.user);
  document.querySelector('#logout-button').classList.toggle('hidden', !state.user);
  document.querySelectorAll('#main-nav [data-view]').forEach(button => {
    const roles = button.dataset.roles?.split(',');
    const visible = !roles || roles.some(role => roleIs(role));
    button.classList.toggle('hidden', !visible || (!state.user && button.dataset.view === 'loans'));
    button.classList.toggle('active', button.dataset.view === state.view);
  });
  document.querySelector('#footer-role').textContent = state.user
    ? roleNames[state.user.role] : 'Публічний каталог';
  document.querySelector('#add-book-open').classList.toggle('hidden', !roleIs('LIBRARIAN'));
  document.querySelector('#add-reader-open').classList.toggle('hidden', !roleIs('LIBRARIAN'));
  document.querySelector('#issue-open').classList.toggle('hidden', !roleIs('LIBRARIAN'));
  if (state.user?.role === 'LIBRARIAN') {
    document.querySelector('[data-view="loans"]').textContent = '↗ Видачі';
  } else {
    document.querySelector('[data-view="loans"]').textContent = '↗ Мої видачі';
  }
}

function setPage(title, eyebrow, note) {
  document.querySelector('#page-title').textContent = title;
  document.querySelector('#breadcrumb-view').textContent = title;
  document.querySelector('#page-eyebrow').textContent = eyebrow;
  document.querySelector('#page-note').textContent = note;
}

function showError(message) {
  root.innerHTML = `<div class="empty-state"><strong>Не вдалося завантажити дані</strong><span>${escapeHtml(message)}</span></div>`;
}

function emptyState(title, note) {
  return `<div class="empty-state"><strong>${escapeHtml(title)}</strong><span>${escapeHtml(note)}</span></div>`;
}

async function renderCatalog() {
  const searchWasFocused = document.activeElement?.id === 'catalog-search';
  const searchSelection = searchWasFocused ? document.activeElement.selectionStart : null;
  setPage('Каталог', 'ФОНД БІБЛІОТЕКИ', 'Знайдіть видання та перевірте доступність примірників.');
  root.setAttribute('aria-busy', 'true');
  try {
    const query = state.search.trim();
    if (query.length > 0 && query.length < 3) {
      const remaining = 3 - query.length;
      root.innerHTML = `<div class="toolbar"><label class="search-box"><span aria-hidden="true">⌕</span><input id="catalog-search" type="search" placeholder="Назва, автор або ISBN" value="${escapeHtml(query)}" aria-label="Пошук у каталозі"></label><span class="result-count">Ще ${remaining} ${remaining === 1 ? 'символ' : 'символи'}</span></div>
        ${emptyState('Уточніть пошук', 'Введіть щонайменше 3 символи.')}`;
      document.querySelector('#catalog-search').addEventListener('input', event => {
        state.search = event.target.value;
        clearTimeout(renderCatalog.searchTimer);
        renderCatalog.searchTimer = setTimeout(() => renderCatalog(), 220);
      });
      if (searchWasFocused) {
        const input = document.querySelector('#catalog-search');
        input.focus();
        input.setSelectionRange(searchSelection, searchSelection);
      }
      return;
    }
    const books = await api(`/api/books${query ? `?search=${encodeURIComponent(query)}` : ''}`);
    const rows = books.map((book, index) => {
      const available = Number(book.available_copies || 0);
      const total = Number(book.total_copies || 0);
      const detail = [book.genre, book.publication_year, book.publisher,
        book.isbn ? `ISBN ${book.isbn}` : null].filter(Boolean).join(' · ');
      const actions = roleIs('LIBRARIAN') ? `<div class="book-actions">
        <button class="book-action" data-edit-book="${Number(book.id)}" type="button" aria-label="Редагувати ${escapeHtml(book.title)}" title="Редагувати">✎</button>
        <button class="book-action remove" data-deactivate-book="${Number(book.id)}" type="button" aria-label="Вилучити ${escapeHtml(book.title)}" title="Вилучити">×</button>
      </div>` : '';
      return `<article class="book-row" style="animation-delay:${Math.min(index, 8) * 35}ms">
        <div><h2 class="book-title">${escapeHtml(book.title)}</h2><div class="book-author">${escapeHtml(book.author)}</div></div>
        <div class="book-meta"><span>${escapeHtml(detail || 'Видання бібліотеки')}</span><span>${total} ${total === 1 ? 'примірник' : 'примірників'} у фонді</span></div>
        <span class="availability ${available === 0 ? 'none' : ''}">${available > 0 ? `Доступно: ${available}` : 'Немає в наявності'}</span>
        ${actions}
      </article>`;
    }).join('');
    root.innerHTML = `<div class="toolbar"><label class="search-box"><span aria-hidden="true">⌕</span><input id="catalog-search" type="search" placeholder="Назва, автор або ISBN" value="${escapeHtml(query)}" aria-label="Пошук у каталозі"></label><span class="result-count">${books.length} ${books.length === 1 ? 'видання' : 'видань'}</span></div>
      <div class="book-list">${rows || emptyState(query ? 'Нічого не знайдено' : 'Каталог поки порожній', query ? 'Спробуйте інший запит.' : 'Книги з’являться тут після додавання до фонду.')}</div>`;
    document.querySelector('#catalog-search').addEventListener('input', event => {
      state.search = event.target.value;
      clearTimeout(renderCatalog.searchTimer);
      renderCatalog.searchTimer = setTimeout(() => renderCatalog(), 220);
    });
    root.querySelectorAll('[data-edit-book]').forEach(button => button.addEventListener('click', () => {
      const selectedBook = books.find(book => Number(book.id) === Number(button.dataset.editBook));
      if (selectedBook) openBookDialog(selectedBook);
    }));
    root.querySelectorAll('[data-deactivate-book]').forEach(button => button.addEventListener('click', async () => {
      if (!confirm('Вилучити видання з каталогу? Книги з активними видачами вилучити не можна.')) return;
      try {
        await api(`/api/books/${button.dataset.deactivateBook}`, { method: 'DELETE' });
        notify('Видання вилучено з каталогу');
        renderCatalog();
      } catch (error) { notify(error.message, true); }
    }));
    if (searchWasFocused) {
      const input = document.querySelector('#catalog-search');
      input.focus();
      input.setSelectionRange(searchSelection, searchSelection);
    }
  } catch (error) {
    showError(error.message);
  } finally {
    root.setAttribute('aria-busy', 'false');
  }
}

function dateText(value) {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value) : new Intl.DateTimeFormat('uk-UA').format(date);
}

async function renderLoans() {
  const librarian = roleIs('LIBRARIAN');
  setPage(librarian ? 'Видачі' : 'Мої видачі', 'ОБІГ ФОНДУ', librarian
    ? 'Контроль активних видач, термінів і повернень.'
    : 'Ваші поточні та попередні видачі з бібліотеки.');
  root.setAttribute('aria-busy', 'true');
  try {
    const loans = await api('/api/loans');
    const rows = loans.map(loan => `<tr>
      <td><strong>${escapeHtml(loan.title)}</strong><br><span class="book-author">${escapeHtml(loan.author)}</span></td>
      ${librarian ? `<td>${escapeHtml(loan.reader_name)}<br><span class="book-author">${escapeHtml(loan.card_number)}</span></td>` : ''}
      <td>${escapeHtml(loan.inventory_number)}</td>
      <td>${dateText(loan.issued_at)}</td><td>${dateText(loan.due_date)}</td>
      <td><span class="status-pill ${loan.overdue ? 'overdue' : ''}">${loan.returned_at ? `Повернено ${dateText(loan.returned_at)}` : loan.overdue ? 'Прострочено' : 'На руках'}</span></td>
      ${librarian ? `<td>${loan.returned_at ? '' : `<button class="table-action" data-return="${Number(loan.id)}">Прийняти</button>`}</td>` : ''}
    </tr>`).join('');
    const columns = `${librarian ? '<th>Читач</th>' : ''}<th>Примірник</th><th>Видано</th><th>Повернути до</th><th>Стан</th>${librarian ? '<th></th>' : ''}`;
    root.innerHTML = loans.length ? `<div class="table-wrap"><table><thead><tr><th>Книга</th>${columns}</tr></thead><tbody>${rows}</tbody></table></div>`
      : emptyState('Видач поки немає', librarian ? 'Оформіть першу видачу читачеві.' : 'Ваші видачі відобразяться тут.');
    root.querySelectorAll('[data-return]').forEach(button => button.addEventListener('click', async () => {
      if (!confirm('Підтвердити повернення примірника?')) return;
      try {
        await api(`/api/loans/${button.dataset.return}/return`, { method: 'POST' });
        notify('Повернення зареєстровано');
        renderLoans();
      } catch (error) { notify(error.message, true); }
    }));
  } catch (error) {
    showError(error.message);
  } finally {
    root.setAttribute('aria-busy', 'false');
  }
}

async function renderReaders() {
  setPage('Читачі', 'ЧИТАЦЬКІ ФОРМУЛЯРИ', 'Облікові записи читачів та стан формулярів.');
  root.setAttribute('aria-busy', 'true');
  try {
    const readers = await api('/api/readers');
    const rows = readers.map(reader => `<tr>
      <td><strong>${escapeHtml(reader.full_name)}</strong><br><span class="book-author">${escapeHtml(reader.login)}</span></td>
      <td>${escapeHtml(reader.card_number)}</td><td>${escapeHtml(reader.email || '—')}</td><td>${escapeHtml(reader.phone || '—')}</td>
      <td><span class="status-pill ${reader.active && reader.account_status === 'ACTIVE' ? '' : 'inactive'}">${reader.active && reader.account_status === 'ACTIVE' ? 'Активний' : 'Неактивний'}</span></td>
      <td class="book-actions">
        <button class="book-action" data-reader-history="${Number(reader.id)}" type="button" aria-label="Історія читача" title="Історія">↗</button>
        ${reader.active ? `<button class="book-action" data-edit-reader="${Number(reader.id)}" type="button" aria-label="Редагувати читача" title="Редагувати">✎</button>
        <button class="table-action" data-deactivate-reader="${Number(reader.id)}">Деактивувати</button>` : ''}
      </td>
    </tr>`).join('');
    root.innerHTML = readers.length ? `<div class="table-wrap"><table><thead><tr><th>Читач</th><th>Квиток</th><th>Email</th><th>Телефон</th><th>Стан</th><th></th></tr></thead><tbody>${rows}</tbody></table></div>`
      : emptyState('Читачів ще немає', 'Нові читачі можуть зареєструватися через форму входу.');
    root.querySelectorAll('[data-deactivate-reader]').forEach(button => button.addEventListener('click', async () => {
      if (!confirm('Деактивувати читацький формуляр?')) return;
      try {
        await api(`/api/readers/${button.dataset.deactivateReader}`, { method: 'DELETE' });
        notify('Формуляр деактивовано');
        renderReaders();
      } catch (error) { notify(error.message, true); }
    }));
    root.querySelectorAll('[data-edit-reader]').forEach(button => button.addEventListener('click', () => {
      const reader = readers.find(item => Number(item.id) === Number(button.dataset.editReader));
      if (reader) openReaderDialog(reader);
    }));
    root.querySelectorAll('[data-reader-history]').forEach(button => button.addEventListener('click', () => {
      const reader = readers.find(item => Number(item.id) === Number(button.dataset.readerHistory));
      if (reader) openReaderHistory(reader);
    }));
  } catch (error) {
    showError(error.message);
  } finally {
    root.setAttribute('aria-busy', 'false');
  }
}

async function renderReports() {
  setPage('Звіти й персонал', 'АДМІНІСТРУВАННЯ', 'Огляд фонду та облікових записів працівників.');
  root.setAttribute('aria-busy', 'true');
  try {
    const [summary, staff] = await Promise.all([api('/api/reports/summary'), api('/api/users')]);
    const metrics = [
      ['Активні видання', summary.active_titles],
      ['Доступні примірники', summary.available_copies],
      ['Активні видачі', summary.active_loans],
      ['Прострочені', summary.overdue_loans],
      ['Активні читачі', summary.active_readers]
    ].map(([label, value]) => `<div class="metric"><span class="metric-label">${label}</span><strong class="metric-value">${Number(value)}</strong></div>`).join('');
    const roleNames = { ADMIN: 'Адміністратор', LIBRARIAN: 'Бібліотекар', READER: 'Читач' };
    const staffRows = staff.map(person => `<tr>
      <td><strong>${escapeHtml(person.reader_name || person.login)}</strong><br><span class="book-author">${escapeHtml(person.login)}${person.card_number ? ` · ${escapeHtml(person.card_number)}` : ''}</span></td>
      <td>${roleNames[person.role] || escapeHtml(person.role)}</td>
      <td><span class="status-pill ${person.status === 'ACTIVE' ? '' : 'inactive'}">${person.status === 'ACTIVE' ? 'Активний' : 'Деактивований'}</span></td>
      <td>${dateText(person.last_login_at)}</td>
      <td class="book-actions"><button class="table-action" data-reset-user-password="${Number(person.id)}" data-login="${escapeHtml(person.login)}">Скинути пароль</button>
        ${Number(person.id) === Number(state.user?.id) ? '' : `<button class="table-action" data-staff-id="${Number(person.id)}" data-next-status="${person.status === 'ACTIVE' ? 'DEACTIVATED' : 'ACTIVE'}">${person.status === 'ACTIVE' ? 'Деактивувати' : 'Активувати'}</button>`}</td>
    </tr>`).join('');
    root.innerHTML = `<div class="metric-grid">${metrics}</div><div class="section-bar"><h2>Звіти</h2></div>
      <div class="segmented report-tabs" role="tablist" aria-label="Тип звіту">
        <button class="segment active" type="button" data-report-type="INVENTORY">Книжковий фонд</button>
        <button class="segment" type="button" data-report-type="READERS">Читачі</button>
        <button class="segment" type="button" data-report-type="LOANS">Видачі</button>
      </div><div id="report-table"></div>
      <div class="section-bar"><h2>Облікові записи</h2><button id="staff-open" class="button button-outline" type="button">＋ Додати працівника</button></div>
      <div class="table-wrap"><table><thead><tr><th>Ім’я / логін</th><th>Роль</th><th>Стан</th><th>Останній вхід</th><th></th></tr></thead><tbody>${staffRows}</tbody></table></div>`;
    document.querySelector('#staff-open').addEventListener('click', () => document.querySelector('#staff-dialog').showModal());
    root.querySelectorAll('[data-staff-id]').forEach(button => button.addEventListener('click', async () => {
      const activating = button.dataset.nextStatus === 'ACTIVE';
      if (!confirm(`${activating ? 'Активувати' : 'Деактивувати'} обліковий запис працівника?`)) return;
      try {
        await api(`/api/users/${button.dataset.staffId}/status`, {
          method: 'PUT', body: JSON.stringify({ status: button.dataset.nextStatus })
        });
        notify(activating ? 'Обліковий запис активовано' : 'Обліковий запис деактивовано');
        renderReports();
      } catch (error) { notify(error.message, true); }
    }));
    root.querySelectorAll('[data-reset-user-password]').forEach(button => button.addEventListener('click', () => {
      resettingStaffId = Number(button.dataset.resetUserPassword);
      document.querySelector('#staff-password-title').textContent = `Скидання пароля: ${button.dataset.login}`;
      document.querySelector('#staff-password-form').reset();
      document.querySelector('#staff-password-dialog').showModal();
    }));
    root.querySelectorAll('[data-report-type]').forEach(button => button.addEventListener('click', () => {
      root.querySelectorAll('[data-report-type]').forEach(tab => tab.classList.toggle('active', tab === button));
      loadReportRows(button.dataset.reportType);
    }));
    loadReportRows('INVENTORY');
  } catch (error) {
    showError(error.message);
  } finally {
    root.setAttribute('aria-busy', 'false');
  }
}

function reportTable(type, rows) {
  const layouts = {
    INVENTORY: {
      headers: ['Назва', 'Автор', 'Рік', 'Видавництво', 'У фонді', 'Доступні', 'Видані'],
      cells: row => [row.title, row.author, row.publication_year || '—', row.publisher || '—',
        row.total_copies, row.available_copies, row.issued_copies]
    },
    READERS: {
      headers: ['Читач', 'Квиток', 'Email', 'Телефон', 'Стан', 'Активні видачі', 'Усього видач'],
      cells: row => [row.full_name, row.card_number, row.email || '—', row.phone || '—',
        row.active && row.account_status === 'ACTIVE' ? 'Активний' : 'Неактивний', row.active_loans, row.total_loans]
    },
    LOANS: {
      headers: ['Книга', 'Читач', 'Примірник', 'Видано', 'Повернути до', 'Повернено', 'Стан'],
      cells: row => [row.title, `${row.reader_name} · ${row.card_number}`, row.inventory_number,
        dateText(row.issued_at), dateText(row.due_date), dateText(row.returned_at),
        row.returned_at ? 'Повернено' : row.overdue ? 'Прострочено' : 'На руках']
    }
  };
  const layout = layouts[type];
  const header = layout.headers.map(label => `<th>${label}</th>`).join('');
  const body = rows.map(row => `<tr>${layout.cells(row).map(value => `<td>${escapeHtml(value)}</td>`).join('')}</tr>`).join('');
  return rows.length ? `<div class="table-wrap"><table><thead><tr>${header}</tr></thead><tbody>${body}</tbody></table></div>`
    : emptyState('Записів немає', 'Для цього звіту поки немає даних.');
}

async function loadReportRows(type) {
  const table = document.querySelector('#report-table');
  table.setAttribute('aria-busy', 'true');
  try {
    const rows = await api(`/api/reports?type=${encodeURIComponent(type)}`);
    table.innerHTML = reportTable(type, rows);
  } catch (error) {
    table.innerHTML = `<div class="empty-state"><strong>Не вдалося сформувати звіт</strong><span>${escapeHtml(error.message)}</span></div>`;
  } finally {
    table.setAttribute('aria-busy', 'false');
  }
}

async function renderView() {
  updateShell();
  if (state.view === 'loans' && !state.user) state.view = 'catalog';
  if (state.view === 'readers' && !roleIs('LIBRARIAN')) state.view = 'catalog';
  if (state.view === 'reports' && !roleIs('ADMIN')) state.view = 'catalog';
  updateShell();
  if (state.view === 'loans') return renderLoans();
  if (state.view === 'readers') return renderReaders();
  if (state.view === 'reports') return renderReports();
  return renderCatalog();
}

function setAuthMode(mode) {
  state.authMode = mode;
  document.querySelector('#auth-title').textContent = mode === 'login' ? 'Вхід у систему' : 'Реєстрація читача';
  document.querySelectorAll('[data-auth-mode]').forEach(button => button.classList.toggle('active', button.dataset.authMode === mode));
  const form = document.querySelector('#auth-form');
  form.innerHTML = mode === 'login' ? `
    <label>Логін<input name="login" required maxlength="80" autocomplete="username"></label>
    <label>Пароль<input name="password" type="password" required maxlength="100" autocomplete="current-password"></label>
    <button class="button button-primary" type="submit">Увійти</button>` : `
    <label>Ім’я та прізвище<input name="fullName" required maxlength="160" autocomplete="name"></label>
    <label>Номер читацького квитка<input name="cardNumber" required maxlength="40"></label>
    <div class="form-grid"><label>Email<input name="email" type="email" maxlength="160" autocomplete="email"></label><label>Телефон<input name="phone" maxlength="40" autocomplete="tel"></label></div>
    <label>Логін<input name="login" required maxlength="80" autocomplete="username"></label>
    <label>Пароль<input name="password" type="password" required minlength="8" maxlength="100" autocomplete="new-password"></label>
    <button class="button button-primary" type="submit">Створити читацький запис</button>`;
}

document.querySelector('#main-nav').addEventListener('click', event => {
  const button = event.target.closest('[data-view]');
  if (!button) return;
  state.view = button.dataset.view;
  renderView();
});

document.querySelector('#auth-open').addEventListener('click', () => {
  setAuthMode('login');
  document.querySelector('#auth-dialog').showModal();
});
document.querySelectorAll('[data-auth-mode]').forEach(button => button.addEventListener('click', () => setAuthMode(button.dataset.authMode)));
document.querySelector('#auth-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.currentTarget));
  if (state.authMode === 'register') {
    data.email = data.email || null;
    data.phone = data.phone || null;
  }
  const path = state.authMode === 'login' ? '/api/auth/login' : '/api/auth/register';
  try {
    state.user = await api(path, { method: 'POST', body: JSON.stringify(data) });
    document.querySelector('#auth-dialog').close();
    state.view = 'catalog';
    notify(state.authMode === 'login' ? 'Вхід виконано' : 'Читацький запис створено');
    renderView();
  } catch (error) { notify(error.message, true); }
});

document.querySelector('#logout-button').addEventListener('click', async () => {
  try {
    await api('/api/auth/logout', { method: 'POST' });
    state.user = null;
    state.view = 'catalog';
    notify('Ви вийшли із системи');
    renderView();
  } catch (error) { notify(error.message, true); }
});

document.querySelector('#password-open').addEventListener('click', () => {
  document.querySelector('#password-form').reset();
  document.querySelector('#password-dialog').showModal();
});
document.querySelector('#password-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.currentTarget));
  if (data.newPassword !== data.confirmPassword) {
    notify('Нові паролі не збігаються', true);
    return;
  }
  delete data.confirmPassword;
  try {
    await api('/api/auth/password', { method: 'PUT', body: JSON.stringify(data) });
    event.currentTarget.reset();
    document.querySelector('#password-dialog').close();
    notify('Пароль змінено');
  } catch (error) { notify(error.message, true); }
});

document.querySelector('#staff-password-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.currentTarget));
  if (data.newPassword !== data.confirmPassword) {
    notify('Паролі не збігаються', true);
    return;
  }
  delete data.confirmPassword;
  try {
    await api(`/api/users/${resettingStaffId}/password`, { method: 'PUT', body: JSON.stringify(data) });
    resettingStaffId = null;
    event.currentTarget.reset();
    document.querySelector('#staff-password-dialog').close();
    notify('Пароль працівника скинуто');
  } catch (error) { notify(error.message, true); }
});

function openBookDialog(book = null) {
  editingBookId = book ? Number(book.id) : null;
  const form = document.querySelector('#book-form');
  form.reset();
  form.elements.title.value = book?.title || '';
  form.elements.author.value = book?.author || '';
  form.elements.genre.value = book?.genre || '';
  form.elements.isbn.value = book?.isbn || '';
  form.elements.publicationYear.value = book?.publication_year || '';
  form.elements.publisher.value = book?.publisher || '';
  form.elements.inventoryNumbers.required = !book;
  document.querySelector('#inventory-field').classList.toggle('hidden', Boolean(book));
  document.querySelector('#book-dialog-title').textContent = book ? 'Редагувати видання' : 'Нове видання';
  document.querySelector('#book-dialog').showModal();
}

document.querySelector('#add-book-open').addEventListener('click', () => openBookDialog());
document.querySelector('#book-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.currentTarget));
  data.publicationYear = data.publicationYear ? Number(data.publicationYear) : null;
  if (editingBookId === null) {
    data.inventoryNumbers = data.inventoryNumbers.split(/\r?\n/).map(value => value.trim()).filter(Boolean);
  } else {
    delete data.inventoryNumbers;
  }
  try {
    await api(editingBookId === null ? '/api/books' : `/api/books/${editingBookId}`, {
      method: editingBookId === null ? 'POST' : 'PUT', body: JSON.stringify(data)
    });
    editingBookId = null;
    event.currentTarget.reset();
    document.querySelector('#book-dialog').close();
    notify('Зміни видання збережено');
    state.view = 'catalog';
    renderView();
  } catch (error) { notify(error.message, true); }
});

document.querySelector('#issue-open').addEventListener('click', async () => {
  try {
    const [readers, copies] = await Promise.all([api('/api/readers'), api('/api/copies/available')]);
    const readerSelect = document.querySelector('#loan-form [name="readerId"]');
    const copySelect = document.querySelector('#loan-form [name="copyId"]');
    const activeReaders = readers.filter(reader => reader.active && reader.account_status === 'ACTIVE');
    readerSelect.innerHTML = activeReaders.map(reader => `<option value="${Number(reader.id)}">${escapeHtml(reader.full_name)} · ${escapeHtml(reader.card_number)}</option>`).join('');
    copySelect.innerHTML = copies.map(copy => `<option value="${Number(copy.id)}">${escapeHtml(copy.title)} · ${escapeHtml(copy.inventory_number)}</option>`).join('');
    if (!activeReaders.length || !copies.length) {
      notify(!activeReaders.length ? 'Немає активних читацьких формулярів' : 'Немає доступних примірників', true);
      return;
    }
    document.querySelector('#loan-dialog').showModal();
  } catch (error) { notify(error.message, true); }
});

function openReaderDialog(reader = null) {
  editingReaderId = reader ? Number(reader.id) : null;
  document.querySelector('#reader-dialog-title').textContent = reader ? 'Редагувати читача' : 'Новий читач';
  const form = document.querySelector('#reader-form');
  form.innerHTML = `
    <label>Ім’я та прізвище<input name="fullName" required maxlength="160" autocomplete="name"></label>
    ${reader ? '' : '<label>Номер читацького квитка<input name="cardNumber" required maxlength="40"></label>'}
    <div class="form-grid"><label>Email<input name="email" type="email" maxlength="160" autocomplete="email"></label><label>Телефон<input name="phone" maxlength="40" autocomplete="tel"></label></div>
    ${reader ? '' : '<label>Логін<input name="login" required maxlength="80" autocomplete="username"></label><label>Пароль<input name="password" type="password" required minlength="8" maxlength="100" autocomplete="new-password"></label>'}
    <div class="dialog-actions"><button type="button" class="button button-outline" data-close="reader-dialog">Скасувати</button><button class="button button-primary" type="submit">${reader ? 'Зберегти зміни' : 'Зареєструвати читача'}</button></div>`;
  form.elements.fullName.value = reader?.full_name || '';
  form.elements.email.value = reader?.email || '';
  form.elements.phone.value = reader?.phone || '';
  document.querySelector('#reader-dialog').showModal();
}

async function openReaderHistory(reader) {
  document.querySelector('#history-title').textContent = `Історія: ${reader.full_name}`;
  const content = document.querySelector('#history-content');
  content.innerHTML = '<p class="field-hint">Завантаження історії…</p>';
  document.querySelector('#history-dialog').showModal();
  try {
    const loans = await api(`/api/readers/${Number(reader.id)}/loans`);
    const rows = loans.map(loan => `<tr><td>${escapeHtml(loan.title)}</td><td>${escapeHtml(loan.inventory_number)}</td>
      <td>${dateText(loan.issued_at)}</td><td>${dateText(loan.due_date)}</td><td>${dateText(loan.returned_at)}</td>
      <td>${loan.returned_at ? 'Повернено' : loan.overdue ? 'Прострочено' : 'На руках'}</td></tr>`).join('');
    content.innerHTML = loans.length ? `<div class="table-wrap"><table><thead><tr><th>Книга</th><th>Примірник</th><th>Видано</th><th>Повернути до</th><th>Повернено</th><th>Стан</th></tr></thead><tbody>${rows}</tbody></table></div>`
      : emptyState('Історії ще немає', 'Для цього читача поки не оформлювали видач.');
  } catch (error) {
    content.innerHTML = `<div class="empty-state"><strong>Не вдалося завантажити історію</strong><span>${escapeHtml(error.message)}</span></div>`;
  }
}

document.querySelector('#add-reader-open').addEventListener('click', () => openReaderDialog());
document.querySelector('#reader-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.currentTarget));
  data.email = data.email || null;
  data.phone = data.phone || null;
  const editing = editingReaderId !== null;
  try {
    await api(editing ? `/api/readers/${editingReaderId}` : '/api/readers', {
      method: editing ? 'PUT' : 'POST', body: JSON.stringify(data)
    });
    editingReaderId = null;
    document.querySelector('#reader-dialog').close();
    notify(editing ? 'Дані читача оновлено' : 'Читача зареєстровано');
    renderReaders();
  } catch (error) { notify(error.message, true); }
});
document.querySelector('#loan-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.currentTarget));
  data.readerId = Number(data.readerId);
  data.copyId = Number(data.copyId);
  try {
    await api('/api/loans', { method: 'POST', body: JSON.stringify(data) });
    document.querySelector('#loan-dialog').close();
    notify('Книгу видано на 14 днів');
    state.view = 'loans';
    renderView();
  } catch (error) { notify(error.message, true); }
});

document.querySelector('#staff-form').addEventListener('submit', async event => {
  event.preventDefault();
  const data = Object.fromEntries(new FormData(event.currentTarget));
  try {
    await api('/api/staff', { method: 'POST', body: JSON.stringify(data) });
    event.currentTarget.reset();
    document.querySelector('#staff-dialog').close();
    notify('Працівника додано');
    renderReports();
  } catch (error) { notify(error.message, true); }
});

document.addEventListener('click', event => {
  const button = event.target.closest('[data-close]');
  if (button) document.getElementById(button.dataset.close)?.close();
});
dialogs.forEach(id => {
  const dialog = document.getElementById(id);
  dialog.addEventListener('click', event => {
    if (event.target === dialog) dialog.close();
  });
});

async function start() {
  try { state.user = await api('/api/auth/me'); }
  catch { state.user = null; }
  await renderView();
}

start();