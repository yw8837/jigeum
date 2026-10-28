// Keep browser Back inside the current dialog stack; no draft or password enters history.
const marker = '__agattokDialog';
const managers = new WeakMap();

export function bindDialogHistory(browser, close) {
  let manager = managers.get(browser);
  if (!manager) {
    manager = { entries: [] };
    managers.set(browser, manager);
    if (browser.history.state?.[marker]) {
      const state = { ...browser.history.state };
      delete state[marker];
      browser.history.replaceState(state, '', browser.location.href);
    }
    browser.addEventListener('popstate', () => {
      const target = browser.history.state?.[marker];
      const index = manager.entries.findIndex(entry => entry.id === target);
      for (const entry of manager.entries.slice(index + 1).reverse()) {
        if (entry.close() === false) {
          browser.history.pushState({ ...browser.history.state, [marker]: entry.id }, '', browser.location.href);
          return;
        }
        manager.entries = manager.entries.filter(item => item !== entry);
      }
      // A lower dialog may have been removed together with its child after deletion.
      if (target && !manager.entries.some(entry => entry.id === target)) browser.history.back();
    });
  }
  const entry = { id: crypto.randomUUID(), close };
  manager.entries.push(entry);
  browser.history.pushState({ ...browser.history.state, [marker]: entry.id }, '', browser.location.href);
  return () => {
    manager.entries = manager.entries.filter(item => item !== entry);
    if (browser.history.state?.[marker] === entry.id) browser.history.back();
  };
}
