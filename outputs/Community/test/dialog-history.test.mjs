import { test } from 'node:test';
import assert from 'node:assert/strict';
import { bindDialogHistory } from '../lib/dialog-history.mjs';

function browser() {
  const entries = [{ route: 'feed' }]; let index = 0; const listeners = [];
  return { location: { href: 'https://board.test/' },
    addEventListener: (type, fn) => { if (type === 'popstate') listeners.push(fn); },
    history: {
      get state() { return entries[index]; },
      get depth() { return index; },
      pushState(state) { entries.splice(++index); entries.push(state); },
      replaceState(state) { entries[index] = state; },
      back() { if (index > 0) { index--; listeners.forEach(fn => fn()); } }
    }
  };
}
test('Back closes the top dialog, then its parent, while preserving the feed', () => {
  const win = browser(), closed = [];
  const parent = bindDialogHistory(win, () => closed.push('detail'));
  const child = bindDialogHistory(win, () => closed.push('manage'));
  win.history.back(); assert.deepEqual(closed, ['manage']); child();
  win.history.back(); assert.deepEqual(closed, ['manage', 'detail']); parent();
  assert.deepEqual(win.history.state, { route: 'feed' });
});
test('Declining to discard a draft restores its entry and keeps the parent open', () => {
  const win = browser(); let allowed = false, parentClosed = false;
  bindDialogHistory(win, () => { parentClosed = true; });
  bindDialogHistory(win, () => allowed);
  win.history.back(); assert.equal(win.history.depth, 2); assert.equal(parentClosed, false);
  allowed = true; win.history.back(); assert.equal(win.history.depth, 1); assert.equal(parentClosed, false);
});
test('Closing with X removes its history entry without closing the parent', () => {
  const win = browser(); let closed = false;
  const parent = bindDialogHistory(win, () => { closed = true; });
  const child = bindDialogHistory(win, () => {});
  child(); assert.equal(closed, false); assert.equal(win.history.depth, 1);
  parent(); assert.equal(win.history.depth, 0);
});
test('Removing parent and child together does not leave stale Back entries', () => {
  const win = browser();
  const parent = bindDialogHistory(win, () => {}), child = bindDialogHistory(win, () => {});
  parent(); child(); assert.equal(win.history.depth, 0);
});
