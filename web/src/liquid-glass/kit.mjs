// Author: dishanhai. Native semantic controls, no runtime dependency.
/** Mount scoped tabs, preferences and dialog. Dispose before replacing the root. */
export function mountGlassKit(root) {
  const controller = new AbortController();
  const options = { signal: controller.signal };
  const groups = [...root.querySelectorAll('[data-glass-tabs]')];
  const select = (group, tab) => {
    for (const item of group.querySelectorAll('[role=tab]')) {
      const selected = item === tab;
      item.setAttribute('aria-selected', String(selected));
      item.tabIndex = selected ? 0 : -1;
      const panel = root.querySelector(`#${CSS.escape(item.getAttribute('aria-controls'))}`);
      if (panel) panel.hidden = !selected;
    }
  };
  for (const group of groups) {
    group.addEventListener('click', event => {
      const tab = event.target.closest('[role=tab]');
      if (tab && group.contains(tab) && !tab.disabled) select(group, tab);
    }, options);
    group.addEventListener('keydown', event => {
      const tabs = [...group.querySelectorAll('[role=tab]')].filter(tab => !tab.disabled);
      const index = tabs.indexOf(event.target);
      if (index < 0 || !['ArrowLeft','ArrowRight','Home','End'].includes(event.key)) return;
      event.preventDefault();
      const next = event.key === 'Home' ? 0 : event.key === 'End' ? tabs.length - 1
        : (index + (event.key === 'ArrowRight' ? 1 : -1) + tabs.length) % tabs.length;
      select(group, tabs[next]); tabs[next].focus();
    }, options);
  }
  for (const input of root.querySelectorAll('[data-glass-pref]')) {
    const sync = () => { root.dataset[input.dataset.glassPref] = input.type === 'checkbox'
      ? (input.checked ? input.dataset.on : input.dataset.off) : input.value; };
    input.addEventListener('change', sync, options); sync();
  }
  for (const input of root.querySelectorAll('input[type=range][data-output]')) {
    const sync = () => { root.querySelector(`#${CSS.escape(input.dataset.output)}`).value = input.value; };
    input.addEventListener('input', sync, options); sync();
  }
  const dialog = root.querySelector('dialog');
  let opener;
  root.querySelector('[data-open-dialog]')?.addEventListener('click', event => {
    if (dialog.open) return;
    opener = event.currentTarget; dialog.showModal();
  }, options);
  dialog?.addEventListener('close', () => { if (opener?.isConnected) opener.focus(); }, options);
  return { dispose() { if (dialog?.open) dialog.close(); controller.abort(); } };
}
