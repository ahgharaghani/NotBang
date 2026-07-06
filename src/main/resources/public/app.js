/* ================= NotBang client ================= */

// ------- identity & connection -------

const myPid = (() => {
  let pid = localStorage.getItem('notbang-pid');
  if (!pid) {
    pid = crypto.randomUUID();
    localStorage.setItem('notbang-pid', pid);
  }
  return pid;
})();

let ws = null;
let state = null;          // latest game state from server
let joinInfo = null;       // {action:'join', room, name} for reconnects
let selected = [];         // selected card ids
let selectMode = null;     // 'play' | 'respond' | 'discard' | 'dying' | 'ability'
let pendingPlayCard = null; // card object selected for playing (waiting for target/confirm)

const CARD_ICONS = {
  BANG: '💥', MISSED: '💨', BEER: '🍺', PANIC: '😱', CAT_BALOU: '🐱',
  DUEL: '⚔️', GATLING: '🔫', INDIANS: '🏹', STAGECOACH: '🐴', WELLS_FARGO: '💰',
  SALOON: '🥃', GENERAL_STORE: '🏪', BARREL: '🛢️', SCOPE: '🔭', MUSTANG: '🐎',
  JAIL: '⛓️', DYNAMITE: '🧨', VOLCANIC: '🔫', SCHOFIELD: '🔫', REMINGTON: '🔫',
  REV_CARABINE: '🔫', WINCHESTER: '🔫'
};
const NEEDS_TARGET = new Set(['BANG', 'MISSED', 'PANIC', 'CAT_BALOU', 'DUEL', 'JAIL']);

function connect(onOpen) {
  const proto = location.protocol === 'https:' ? 'wss' : 'ws';
  ws = new WebSocket(`${proto}://${location.host}/ws`);
  ws.onopen = () => { if (onOpen) onOpen(); };
  ws.onmessage = (ev) => handleMessage(JSON.parse(ev.data));
  ws.onclose = () => {
    if (joinInfo) {
      setTimeout(() => connect(() => send(joinInfo)), 1200);
    }
  };
}

function send(obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

// ------- helpers -------

const $ = (id) => document.getElementById(id);

function show(screenId) {
  for (const s of ['home-screen', 'lobby-screen', 'game-screen']) {
    $(s).classList.toggle('hidden', s !== screenId);
  }
}

function toast(msg) {
  const t = $('toast');
  t.textContent = msg;
  t.classList.remove('hidden');
  clearTimeout(toast._timer);
  toast._timer = setTimeout(() => t.classList.add('hidden'), 3200);
}

function clearSelection() {
  selected = [];
  selectMode = null;
  pendingPlayCard = null;
}

function hearts(p) {
  return '❤'.repeat(p.health) + '🖤'.repeat(Math.max(0, p.maxHealth - p.health));
}

// ------- message handling -------

function handleMessage(msg) {
  switch (msg.type) {
    case 'lobby':
      joinInfo = { action: 'join', room: msg.room, name: currentName(), pid: myPid };
      sessionStorage.setItem('notbang-room', msg.room);
      renderLobby(msg);
      break;
    case 'state':
      joinInfo = joinInfo || { action: 'join', room: msg.room, name: currentName(), pid: myPid };
      state = msg;
      clearSelection();
      renderGame();
      break;
    case 'error':
      if ($('home-screen').classList.contains('hidden')) {
        toast(msg.message);
      } else {
        $('home-error').textContent = msg.message;
      }
      break;
  }
}

function currentName() {
  return $('name-input').value.trim() || localStorage.getItem('notbang-name') || 'Cowboy';
}

// ------- home screen -------

$('name-input').value = localStorage.getItem('notbang-name') || '';

$('create-btn').onclick = () => {
  const name = $('name-input').value.trim();
  if (!name) { $('home-error').textContent = 'Enter your name first, cowboy.'; return; }
  localStorage.setItem('notbang-name', name);
  send({ action: 'create', name, pid: myPid });
};

$('join-btn').onclick = joinRoom;
$('room-input').addEventListener('keydown', (e) => { if (e.key === 'Enter') joinRoom(); });

function joinRoom() {
  const name = $('name-input').value.trim();
  const room = $('room-input').value.trim().toUpperCase();
  if (!name) { $('home-error').textContent = 'Enter your name first, cowboy.'; return; }
  if (room.length !== 4) { $('home-error').textContent = 'Room codes have 4 characters.'; return; }
  localStorage.setItem('notbang-name', name);
  send({ action: 'join', room, name, pid: myPid });
}

// ------- lobby -------

function renderLobby(msg) {
  show('lobby-screen');
  $('lobby-code').textContent = msg.room;
  const ul = $('lobby-players');
  ul.innerHTML = '';
  for (const p of msg.players) {
    const li = document.createElement('li');
    li.textContent = p.name + (p.id === myPid ? ' (you)' : '');
    if (p.host) {
      const tag = document.createElement('span');
      tag.className = 'host-tag';
      tag.textContent = ' ★ host';
      li.appendChild(tag);
    }
    ul.appendChild(li);
  }
  const canStart = msg.host && msg.players.length >= 3;
  $('start-btn').classList.toggle('hidden', !msg.host);
  $('start-btn').disabled = !canStart;
  $('lobby-hint').textContent = msg.players.length < 3
    ? `Waiting for players… (${msg.players.length}/3 minimum, 7 max)`
    : msg.host ? 'Ready when you are!' : 'Waiting for the host to start the game…';
}

$('start-btn').onclick = () => send({ action: 'start' });

// ------- game rendering -------

function renderGame() {
  show('game-screen');
  const me = state.players.find(p => p.id === myPid) || null;
  const turnPlayer = state.players.find(p => p.id === state.turn);

  $('room-label').textContent = 'Room ' + state.room;
  $('deck-info').textContent = `Deck: ${state.deckCount} cards`;
  $('turn-label').textContent = state.result ? '' : `${turnPlayer.name}'s turn`;

  const banner = $('result-banner');
  banner.classList.toggle('hidden', !state.result);
  if (state.result) banner.textContent = state.result;

  renderOpponents(me);
  renderPiles();
  renderLog();
  renderMe(me);
  renderPrompt(me);
}

function renderOpponents(me) {
  const container = $('opponents');
  container.innerHTML = '';
  const myIndex = state.players.findIndex(p => p.id === myPid);
  const n = state.players.length;
  const ordered = [];
  for (let i = 1; i < n; i++) {
    ordered.push(state.players[(Math.max(myIndex, 0) + i) % n]);
  }
  if (myIndex === -1) ordered.unshift(state.players[0]); // spectator sees everyone

  for (const p of ordered) {
    const div = document.createElement('div');
    div.className = 'opponent';
    if (p.isTurn) div.classList.add('current-turn');
    if (!p.alive) div.classList.add('dead');
    if (state.pending && state.pending.awaiting === p.id) div.classList.add('awaited');

    const role = p.role ? `${p.role}${p.alive ? '' : ' (dead)'}` : '❓ Hidden role';
    div.innerHTML = `
      <div class="p-name">${escapeHtml(p.name)}</div>
      <div class="p-role">${role}</div>
      <div class="p-char" title="${escapeHtml(p.ability)}">${escapeHtml(p.character)} ℹ</div>
      <div class="p-stats">
        <span class="hearts">${hearts(p)}</span> &nbsp; 🂠 ${p.handCount} &nbsp; 🔫 ${p.weaponRange}
      </div>`;

    if (p.distance !== undefined) {
      const d = document.createElement('div');
      d.className = 'p-distance';
      d.textContent = '📏 ' + p.distance;
      div.appendChild(d);
    }

    const tc = document.createElement('div');
    tc.className = 'table-cards';
    for (const c of p.table) {
      tc.appendChild(miniCard(c));
    }
    div.appendChild(tc);

    if (isTargetable(me, p)) {
      div.classList.add('targetable');
      div.onclick = () => targetChosen(p);
    }
    container.appendChild(div);
  }
}

function miniCard(c) {
  const el = document.createElement('span');
  el.className = 'mini-card' + (c.blue ? ' blue' : '');
  el.textContent = `${CARD_ICONS[c.type] || ''} ${c.name}`;
  el.title = `${c.name} (${c.rank}${c.suitSymbol}) — ${c.description}`;
  return el;
}

function renderPiles() {
  $('deck-pile').innerHTML = `<div>🂠</div><div>${state.deckCount}</div>`;
  const dp = $('discard-pile');
  dp.innerHTML = '';
  if (state.discardTop) {
    dp.appendChild(cardEl(state.discardTop, { static: true, small: true }));
  } else {
    dp.textContent = 'discard';
  }
}

function renderLog() {
  const el = $('log');
  el.innerHTML = '';
  state.log.forEach((line, i) => {
    const d = document.createElement('div');
    d.textContent = line;
    if (i === state.log.length - 1) d.className = 'latest';
    el.appendChild(d);
  });
  el.scrollTop = el.scrollHeight;
}

function renderMe(me) {
  const area = $('me-area');
  const info = $('me-info');
  const handEl = $('hand');
  const tableEl = $('me-table');
  const actions = $('me-actions');
  info.innerHTML = '';
  handEl.innerHTML = '';
  tableEl.innerHTML = '';
  actions.innerHTML = '';
  area.classList.toggle('current-turn', !!me && me.isTurn);

  if (!me || !state.you) {
    info.innerHTML = '<em>You are spectating this game.</em>';
    return;
  }

  info.innerHTML = `
    <span class="p-name">${escapeHtml(me.name)} (you)</span>
    <span class="p-role" title="${escapeHtml(state.you.goal)}">${state.you.role}</span>
    <span class="p-char" title="${escapeHtml(me.ability)}">${escapeHtml(me.character)} ℹ</span>
    <span class="hearts">${hearts(me)}</span>
    <span>🔫 range ${me.weaponRange}</span>
    ${me.alive ? '' : '<span style="color:#ff9d99">☠ You are eliminated — you can keep watching.</span>'}`;

  for (const c of me.table) tableEl.appendChild(miniCard(c));

  const pend = state.pending;
  const awaitingMe = pend && pend.awaiting === myPid;
  const options = awaitingMe && pend.options ? new Set(pend.options) : null;

  for (const c of state.you.hand) {
    const el = cardEl(c);
    if (selected.includes(c.id)) el.classList.add('selected');
    if (options && options.has(c.id)) el.classList.add('option');
    if (pendingPlayCard && pendingPlayCard.id === c.id) el.classList.add('selected');
    el.onclick = () => handCardClicked(c, me);
    handEl.appendChild(el);
  }

  // Action buttons
  if (me.alive && !state.result) {
    if (me.isTurn && state.stage === 3 && !pend) {
      const end = button('End turn', () => send({ action: 'endTurn' }), 'primary');
      actions.appendChild(end);
    }
    if (me.character === 'Sid Ketchum' && me.health < me.maxHealth && state.you.hand.length >= 2 && !pend) {
      if (selectMode === 'ability') {
        actions.appendChild(button(`Heal with selected (${selected.length}/2)`, () => {
          if (selected.length === 2) send({ action: 'ability', cardIds: selected });
        }, selected.length === 2 ? 'primary' : ''));
        actions.appendChild(button('Cancel', () => { clearSelection(); renderGame(); }));
      } else {
        actions.appendChild(button('🍀 Sid: discard 2 cards → +1 life', () => {
          clearSelection();
          selectMode = 'ability';
          renderGame();
        }));
      }
    }
  }
}

function cardEl(c, opts = {}) {
  const el = document.createElement('div');
  el.className = 'card' + (c.blue ? ' blue' : '') + (opts.static ? ' static' : '');
  const red = c.suit === 'HEARTS' || c.suit === 'DIAMONDS';
  el.innerHTML = `
    <div class="c-name">${escapeHtml(c.name)}</div>
    <div class="c-icon">${CARD_ICONS[c.type] || '🃏'}</div>
    <div class="c-rank ${red ? 'red' : ''}">${c.rank}${c.suitSymbol}</div>
    ${opts.static ? '' : `<div class="c-tip">${escapeHtml(c.description)}</div>`}`;
  if (opts.small) {
    el.style.width = '78px';
    el.style.height = '108px';
  }
  return el;
}

function button(label, onClick, cls = '') {
  const b = document.createElement('button');
  b.className = 'btn ' + cls;
  b.textContent = label;
  b.onclick = onClick;
  return b;
}

// ------- interactions -------

function handCardClicked(card, me) {
  if (!me.alive || state.result) return;
  const pend = state.pending;

  // 1. Responding to a pending action
  if (pend && pend.awaiting === myPid) {
    if (pend.type === 'DISCARD_EXCESS') {
      toggleSelect(card.id, pend.discardCount);
      renderGame();
      return;
    }
    if (pend.type === 'GENERAL_STORE') return; // picks happen in the modal
    const opts = new Set(pend.options || []);
    if (!opts.has(card.id)) { toast('That card cannot be used right now.'); return; }
    const max = pend.type === 'BANG' ? pend.needed
              : pend.type === 'DYING' ? (pend.beersNeeded || 1) : 1;
    toggleSelect(card.id, max);
    renderGame();
    return;
  }

  // 2. Sid Ketchum ability selection
  if (selectMode === 'ability') {
    toggleSelect(card.id, 2);
    renderGame();
    return;
  }

  // 3. Playing a card on your turn
  if (!me.isTurn || state.stage !== 3 || pend) {
    toast('You can play cards on your turn, after resolving pending actions.');
    return;
  }
  if (pendingPlayCard && pendingPlayCard.id === card.id) {
    pendingPlayCard = null; // deselect
    renderGame();
    return;
  }
  pendingPlayCard = card;
  selected = [];
  selectMode = null;
  renderGame();
}

function toggleSelect(cardId, max) {
  const i = selected.indexOf(cardId);
  if (i >= 0) selected.splice(i, 1);
  else {
    if (selected.length >= max) selected.shift();
    selected.push(cardId);
  }
}

function isTargetable(me, p) {
  if (!me || !me.alive || state.result || !p.alive) return false;
  if (!pendingPlayCard || !me.isTurn || state.stage !== 3 || state.pending) return false;
  const t = pendingPlayCard.type;
  if (!NEEDS_TARGET.has(t)) return false;
  if ((t === 'BANG' || t === 'MISSED') && p.distance > me.weaponRange) return false;
  if (t === 'PANIC' && p.distance > 1) return false;
  if (t === 'JAIL' && (p.role === 'Sheriff' || p.table.some(c => c.type === 'JAIL'))) return false;
  if (t === 'CAT_BALOU' && p.handCount === 0 && p.table.length === 0) return false;
  if (t === 'PANIC' && p.handCount === 0 && p.table.length === 0) return false;
  return true;
}

function targetChosen(target) {
  const card = pendingPlayCard;
  if (!card) return;
  const t = card.type;
  if ((t === 'PANIC' || t === 'CAT_BALOU') && target.table.length > 0) {
    openStealModal(card, target);
    return;
  }
  send({ action: 'play', cardId: card.id, targetId: target.id });
}

function openStealModal(card, target) {
  const verb = card.type === 'PANIC' ? 'take' : 'make them discard';
  openModal(`${card.name}: which card do you want to ${verb}?`, (body) => {
    if (target.handCount > 0) {
      const tile = document.createElement('div');
      tile.className = 'choice-tile';
      tile.textContent = `🂠 Random card from ${target.name}'s hand`;
      tile.onclick = () => {
        closeModal();
        send({ action: 'play', cardId: card.id, targetId: target.id });
      };
      body.appendChild(tile);
    }
    for (const c of target.table) {
      const el = cardEl(c, { small: true });
      el.onclick = () => {
        closeModal();
        send({ action: 'play', cardId: card.id, targetId: target.id, targetCardId: c.id });
      };
      body.appendChild(el);
    }
  });
}

// ------- prompt area -------

function renderPrompt(me) {
  const area = $('prompt-area');
  const text = $('prompt-text');
  const buttons = $('prompt-buttons');
  buttons.innerHTML = '';
  area.classList.add('hidden');
  area.classList.remove('info');
  closeModal();

  if (state.result) {
    area.classList.remove('hidden');
    area.classList.add('info');
    text.textContent = state.result + ' Refresh or create a new room to play again.';
    return;
  }

  const pend = state.pending;
  const namesById = Object.fromEntries(state.players.map(p => [p.id, p.name]));

  if (pend) {
    area.classList.remove('hidden');
    if (pend.awaiting === myPid && me && me.alive) {
      switch (pend.type) {
        case 'BANG': {
          const from = namesById[pend.source] || 'someone';
          text.textContent = `💥 ${from} shot at you! Play ${pend.needed} Missed! or take the hit.`;
          buttons.appendChild(button(`Play Missed! (${selected.length}/${pend.needed})`, () => {
            if (selected.length === pend.needed) send({ action: 'respond', cardIds: selected });
            else toast(`Select ${pend.needed} card(s) first.`);
          }, selected.length === pend.needed ? 'primary' : ''));
          buttons.appendChild(button('Take the hit 💔', () => send({ action: 'respond', cardIds: [] }), 'danger'));
          break;
        }
        case 'INDIANS':
          text.textContent = '🏹 Indians! Discard a Bang! or lose 1 life.';
          buttons.appendChild(button('Discard Bang!', () => {
            if (selected.length === 1) send({ action: 'respond', cardIds: selected });
            else toast('Select a Bang! card first.');
          }, selected.length === 1 ? 'primary' : ''));
          buttons.appendChild(button('Lose 1 life 💔', () => send({ action: 'respond', cardIds: [] }), 'danger'));
          break;
        case 'DUEL': {
          const vs = namesById[pend.source] || 'your rival';
          text.textContent = `⚔️ Duel against ${vs}! Discard a Bang! or lose 1 life.`;
          buttons.appendChild(button('Fire! (discard Bang!)', () => {
            if (selected.length === 1) send({ action: 'respond', cardIds: selected });
            else toast('Select a Bang! card first.');
          }, selected.length === 1 ? 'primary' : ''));
          buttons.appendChild(button('Back down 💔', () => send({ action: 'respond', cardIds: [] }), 'danger'));
          break;
        }
        case 'DYING': {
          const need = pend.beersNeeded || 1;
          text.textContent = `☠ You are dying! Drink ${need} Beer(s) to survive.`;
          buttons.appendChild(button(`Drink Beer (${selected.length}/${need})`, () => {
            if (selected.length > 0) send({ action: 'respond', cardIds: selected });
            else toast('Select your Beer card(s) first.');
          }, selected.length >= need ? 'primary' : ''));
          buttons.appendChild(button('Accept your fate ☠', () => send({ action: 'respond', cardIds: [] }), 'danger'));
          break;
        }
        case 'DISCARD_EXCESS':
          text.textContent = `🗑 Too many cards! Select ${pend.discardCount} card(s) to discard.`;
          buttons.appendChild(button(`Discard selected (${selected.length}/${pend.discardCount})`, () => {
            if (selected.length === pend.discardCount) send({ action: 'respond', cardIds: selected });
            else toast(`Select ${pend.discardCount} card(s) first.`);
          }, selected.length === pend.discardCount ? 'primary' : ''));
          break;
        case 'GENERAL_STORE':
          text.textContent = '🏪 Pick a card from the General Store.';
          openModal('🏪 General Store — pick a card', (body) => {
            for (const c of pend.storeCards) {
              const el = cardEl(c, { small: true });
              el.onclick = () => { closeModal(); send({ action: 'pick', cardId: c.id }); };
              body.appendChild(el);
            }
          }, /*dismissable=*/false);
          break;
      }
    } else {
      area.classList.add('info');
      const who = namesById[pend.awaiting] || 'someone';
      const what = {
        BANG: 'dodge a shot', INDIANS: 'answer the Indians', DUEL: 'fight a duel',
        DYING: 'fight for their life', DISCARD_EXCESS: 'discard cards',
        GENERAL_STORE: 'pick from the General Store'
      }[pend.type] || 'act';
      text.textContent = `⏳ Waiting for ${who} to ${what}…`;
      if (pend.type === 'GENERAL_STORE' && pend.storeCards) {
        text.textContent += ' Store: ' + pend.storeCards.map(c => c.name).join(', ');
      }
    }
    return;
  }

  if (me && me.isTurn && state.stage === 3) {
    area.classList.remove('hidden');
    area.classList.add('info');
    if (pendingPlayCard) {
      if (NEEDS_TARGET.has(pendingPlayCard.type)) {
        text.textContent = `🎯 ${pendingPlayCard.name}: choose a target (highlighted in red).`;
        buttons.appendChild(button('Cancel', () => { clearSelection(); renderGame(); }));
      } else {
        text.textContent = `Play ${pendingPlayCard.name}?`;
        buttons.appendChild(button(`Play ${pendingPlayCard.name}`, () => {
          send({ action: 'play', cardId: pendingPlayCard.id });
        }, 'primary'));
        buttons.appendChild(button('Cancel', () => { clearSelection(); renderGame(); }));
      }
    } else {
      text.textContent = '🤠 Your turn! Click a card to play it, or end your turn.';
    }
  }
}

// ------- modal -------

function openModal(title, buildBody, dismissable = true) {
  $('modal-title').textContent = title;
  const body = $('modal-body');
  const btns = $('modal-buttons');
  body.innerHTML = '';
  btns.innerHTML = '';
  buildBody(body);
  if (dismissable) {
    btns.appendChild(button('Cancel', closeModal));
  }
  $('modal-backdrop').classList.remove('hidden');
}

function closeModal() {
  $('modal-backdrop').classList.add('hidden');
}

function escapeHtml(s) {
  return String(s ?? '').replace(/[&<>"']/g, (ch) => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  }[ch]));
}

// ------- boot -------

connect(() => {
  // Auto-rejoin after a refresh mid-game
  const room = sessionStorage.getItem('notbang-room');
  const name = localStorage.getItem('notbang-name');
  if (room && name) {
    send({ action: 'join', room, name, pid: myPid });
  }
});
