// Author: dishanhai
import { editNode, exportDocument, importDocument, insertNode, styleFlags } from './rich-text-document.mjs';

const get = name => document.getElementById(`rich-${name}`);
const outline = get('nodes');
const preview = get('content');
const form = get('properties');
const theme = matchMedia('(prefers-color-scheme: dark)');
const applyTheme = () => { document.documentElement.dataset.theme = theme.matches ? 'dark' : 'light'; };
theme.addEventListener('change', applyTheme);
applyTheme();

const labels = new Map([
  ['text', '文字'], ['localized', '翻译'], ['paragraph', '段落分隔'], ['image', '本地图片'], ['extension', '扩展封装'],
]);
const demo = importDocument(JSON.stringify({ version: 1, nodes: [
  { id: 'workbench:title', type: 'text', text: '地质考察手记', style: { bold: true } },
  { id: 'workbench:break', type: 'paragraph', spacing: 12 },
  { id: 'workbench:body', type: 'text', text: '样本箱记录了一枚晶体。标本名称为 ' },
  { id: 'workbench:name', type: 'localized', key: 'item.minecraft.diamond', fallback: '钻石',
    arguments: [], style: { bold: true, underlined: true } },
  { id: 'workbench:icon', type: 'image', texture: 'minecraft:textures/item/diamond.png', width: 16, height: 16, alt: '钻石图标' },
] }));
let state = { entries: demo, index: 0 };
let savedEntries = demo;
let importSequence = 0;
const undo = [];
const redo = [];
const selection = () => state.entries[state.index];
const fieldValues = new WeakMap();

function element(tag, text) {
  const node = document.createElement(tag);
  if (text !== undefined) node.textContent = text;
  return node;
}

function message(target, text, error = false) {
  target.textContent = text;
  target.dataset.error = String(error);
}

function saveState(entries, index, detail) {
  undo.push(state);
  if (undo.length > 40) undo.shift();
  redo.length = 0;
  state = { entries, index };
  render(detail);
}

function updateSelection(index, focus = false) {
  if (index !== state.index) {
    if (!discardDraft()) return;
    state = { ...state, index };
    render();
  }
  if (focus) outline.querySelector(`[data-index="${index}"]`)?.focus();
}

function changeHistory(from, to, detail) {
  if (!from.length || !discardDraft()) return;
  to.push(state);
  state = from.pop();
  render(detail);
  if (document.activeElement === document.body) focusSelection();
}

function focusSelection() {
  const button = outline.querySelector('[aria-current="true"]');
  (button ?? get('add')).focus();
}

function renderNodes() {
  const content = document.createDocumentFragment();
  state.entries.forEach(({ value, requirement }, index) => {
    const button = element('button');
    button.type = 'button';
    button.dataset.index = index;
    if (index === state.index) button.setAttribute('aria-current', 'true');
    button.append(element('strong', `${labels.get(value.type) ?? '未知节点'}${requirement ? ' · 只读' : ''}`), element('code', value.id));
    const item = element('li');
    item.append(button);
    content.append(item);
  });
  outline.replaceChildren(content);
  get('node-count').textContent = String(state.entries.length);
  get('up').disabled = state.index <= 0;
  get('down').disabled = state.index < 0 || state.index === state.entries.length - 1;
  get('delete').disabled = !selection() || Boolean(selection().requirement);
}

const hex = rgb => `#${rgb.toString(16).padStart(6, '0')}`;

function previewNode({ value, requirement }) {
  if (value.type === 'image' || requirement) {
    const type = labels.get(value.type) ?? '未知节点';
    const fallback = typeof value.alt === 'string' && value.alt.length ? value.alt : value.id;
    const box = element('span', `[${type} · ${fallback}]`);
    box.className = 'rich-placeholder';
    if (value.type === 'image' && !requirement) box.title = `${value.texture}（${value.width} × ${value.height}）`;
    if (requirement) box.title = requirement;
    return box;
  }
  if (value.type === 'paragraph') {
    const gap = element('span');
    gap.className = 'rich-break';
    gap.style.paddingBlockEnd = `${value.spacing}px`;
    return gap;
  }
  const run = element('span', value.type === 'text' ? value.text : value.fallback || value.key);
  const style = value.style ?? {};
  if (style.color !== undefined) run.style.color = hex(style.color);
  if (style.bold) run.style.fontWeight = 'bold';
  if (style.italic) run.style.fontStyle = 'italic';
  run.style.textDecorationLine = [style.underlined && 'underline', style.strikethrough && 'line-through'].filter(Boolean).join(' ');
  const hints = [];
  if (value.type === 'localized') hints.push(`翻译键 ${value.key}`);
  if (style.font) hints.push(`原生字体 ${style.font}`);
  if (style.obfuscated) hints.push('原生混淆字符');
  if (hints.length) run.title = hints.join(' · ');
  return run;
}

function renderPreview() {
  const content = document.createDocumentFragment();
  state.entries.forEach((entry, index) => {
    const item = previewNode(entry);
    item.dataset.index = index;
    if (index === state.index) item.setAttribute('aria-current', 'true');
    content.append(item);
  });
  if (!state.entries.length) content.append(element('p', '空文档'));
  preview.replaceChildren(content);
  const protectedCount = state.entries.filter(entry => entry.requirement).length;
  get('compatibility').textContent = protectedCount
    ? `有 ${protectedCount} 个受保护节点。请在节点属性中查看原生解码要求。`
    : '此文档可通过原生 RichDocument v1 Codec 读取。';
}

function field(parent, name, title, value, options = {}) {
  const label = element('label', title);
  const input = element(options.multiline ? 'textarea' : 'input');
  input.name = name;
  input.autocomplete = 'off';
  if (options.multiline) input.rows = 3;
  if (options.number) input.inputMode = 'numeric';
  if (options.checkbox) {
    input.type = 'checkbox';
    input.defaultChecked = Boolean(value);
    label.className = 'check';
    label.prepend(input);
  } else {
    input.defaultValue = value;
    fieldValues.set(input, input.value);
    label.append(input);
  }
  parent.append(label);
}

function renderInspector() {
  form.replaceChildren();
  const entry = selection();
  if (!entry) {
    form.append(element('p', '插入节点后可编辑属性。'));
    return;
  }
  const { value, requirement, original } = entry;
  if (requirement) {
    const details = element('details');
    const source = element('textarea');
    source.rows = 5;
    source.readOnly = true;
    source.setAttribute('aria-label', '节点原始 JSON');
    source.value = original;
    details.append(element('summary', '原始 JSON'), source);
    form.append(element('code', value.id), element('p', requirement), details);
    return;
  }
  field(form, 'id', '节点 ID', value.id);
  if (value.type === 'text') field(form, 'text', '文字', value.text, { multiline: true });
  if (value.type === 'localized') {
    field(form, 'key', '翻译键', value.key);
    field(form, 'fallback', '回退文本', value.fallback, { multiline: true });
    field(form, 'arguments', '参数（JSON 字符串数组）', JSON.stringify(value.arguments), { multiline: true });
  }
  if (value.type === 'paragraph') field(form, 'spacing', '间距（像素）', value.spacing, { number: true });
  if (value.type === 'image') {
    field(form, 'texture', '图片资源 ID', value.texture);
    field(form, 'alt', '替代文本', value.alt, { multiline: true });
    field(form, 'width', '宽度（像素）', value.width, { number: true });
    field(form, 'height', '高度（像素）', value.height, { number: true });
  }
  if (value.type === 'text' || value.type === 'localized') {
    const style = value.style ?? {};
    const styles = element('fieldset');
    styles.append(element('legend', '样式'));
    field(styles, 'color', '颜色（#RRGGBB，留空继承）', style.color === undefined ? '' : hex(style.color));
    field(styles, 'font', '原生字体资源 ID（留空继承）', style.font ?? '');
    for (const [name, title] of styleFlags) field(styles, name, title, style[name], { checkbox: true });
    form.append(styles);
  }
  const actions = element('div');
  actions.className = 'actions';
  const apply = element('button', '应用修改');
  apply.type = 'submit';
  apply.id = 'rich-apply';
  apply.className = 'rich-primary';
  apply.setAttribute('aria-keyshortcuts', 'Control+Enter Meta+Enter');
  const cancel = element('button', '取消');
  cancel.type = 'reset';
  cancel.id = 'rich-cancel';
  cancel.setAttribute('aria-keyshortcuts', 'Escape');
  const status = element('p');
  status.id = 'rich-inspector-status';
  status.className = 'rich-message';
  status.setAttribute('role', 'status');
  actions.append(apply, cancel);
  form.append(actions, status);
}

function render(detail = '') {
  renderNodes();
  renderPreview();
  renderInspector();
  get('undo').disabled = !undo.length;
  get('redo').disabled = !redo.length;
  message(get('status'), detail || (state.entries === savedEntries ? '本机编辑 · RichDocument v1' : '有未导出的修改'));
}

const changedFields = () => [...form.querySelectorAll('input, textarea[name]')].filter(input => input.type === 'checkbox'
  ? input.checked !== input.defaultChecked
  : input.value !== fieldValues.get(input));

function discardDraft() {
  return !changedFields().length || confirm('当前节点有未应用的修改。要舍弃这些修改吗？');
}

function changedValue(value, changes) {
  const result = { ...value };
  const style = { ...value.style };
  for (const input of changes) {
    const { name, value: text } = input;
    if (styleFlags.has(name)) style[name] = input.checked;
    else if (name === 'font' || name === 'color') {
      if (!text) delete style[name];
      else if (name === 'font') style.font = text;
      else {
        if (!/^#[0-9a-fA-F]{6}$/.test(text)) throw new Error('颜色格式必须是 #RRGGBB。');
        style.color = Number.parseInt(text.slice(1), 16);
      }
    } else if (name === 'arguments') {
      try { result.arguments = JSON.parse(text); } catch { throw new Error('参数必须是有效的 JSON 字符串数组。'); }
    } else if (['spacing', 'width', 'height'].includes(name)) {
      result[name] = /^\d+$/.test(text) ? Number(text) : NaN;
    } else result[name] = text;
  }
  if (Object.keys(style).length) result.style = style;
  else delete result.style;
  return result;
}

form.addEventListener('submit', event => {
  event.preventDefault();
  const changes = changedFields();
  if (!changes.length) return;
  const { entries, index } = state;
  const active = document.activeElement;
  try {
    const value = changedValue(selection().value, changes);
    saveState(editNode(entries, index, value), index, '已应用节点修改。');
    (form.elements.namedItem(active.name) ?? get('apply')).focus();
  } catch (error) {
    message(get('inspector-status'), error.message, true);
  }
});
form.addEventListener('reset', () => {
  message(get('inspector-status'), '已取消未应用的修改。');
});
form.addEventListener('input', () => message(get('inspector-status'), ''));

outline.addEventListener('click', event => {
  const button = event.target.closest('button[data-index]');
  if (button) updateSelection(Number(button.dataset.index), true);
});
outline.addEventListener('keydown', event => {
  const button = event.target.closest('button[data-index]');
  const offset = event.key === 'ArrowUp' ? -1 : event.key === 'ArrowDown' ? 1 : 0;
  if (!offset || !button) return;
  event.preventDefault();
  const index = Number(button.dataset.index) + offset;
  if (state.entries[index]) updateSelection(index, true);
});
preview.addEventListener('click', event => {
  const item = event.target.closest('[data-index]');
  if (item && window.getSelection().isCollapsed) updateSelection(Number(item.dataset.index));
});

function move(offset) {
  if (!discardDraft()) return;
  const { index, entries } = state;
  const next = index + offset;
  if (!entries[next]) return;
  const moved = entries.toSpliced(index, 1).toSpliced(next, 0, entries[index]);
  saveState(moved, next, offset > 0 ? '已下移节点。' : '已上移节点。');
  focusSelection();
}

get('up').addEventListener('click', () => move(-1));
get('down').addEventListener('click', () => move(1));
get('delete').addEventListener('click', () => {
  const { index, entries } = state;
  if (!selection() || selection().requirement || !discardDraft()) return;
  saveState(entries.toSpliced(index, 1), Math.min(index, entries.length - 2), '已移除节点，可撤销。');
  focusSelection();
});
get('add').addEventListener('click', () => {
  if (!discardDraft()) return;
  const index = state.index + 1;
  try {
    saveState(insertNode(state.entries, index, get('add-type').value), index, '已插入节点。');
    form.querySelector('textarea, input')?.focus();
  } catch (error) {
    message(get('status'), error.message, true);
  }
});
get('undo').addEventListener('click', () => changeHistory(undo, redo, '已撤销。'));
get('redo').addEventListener('click', () => changeHistory(redo, undo, '已重做。'));

function loadDocument(text) {
  const entries = importDocument(text);
  if (!discardDraft()) {
    message(get('import-status'), '已取消导入。');
    return;
  }
  saveState(entries, entries.length ? 0 : -1, '已导入文档。');
  message(get('import-status'), `已导入 ${entries.length} 个节点。`);
}

get('import').addEventListener('click', () => {
  importSequence += 1;
  try {
    loadDocument(get('json').value);
  } catch (error) {
    message(get('import-status'), error.message, true);
  }
});
get('file').addEventListener('change', async event => {
  const sequence = ++importSequence;
  const input = event.target;
  const [file] = input.files;
  input.value = '';
  if (!file) return;
  try {
    if (file.size > 256 * 1024) throw new Error('RichDocument JSON 必须在 256 KiB 以内。');
    const bytes = await file.arrayBuffer();
    if (sequence === importSequence) loadDocument(new TextDecoder('utf-8', { fatal: true }).decode(bytes));
  } catch (error) {
    if (sequence === importSequence) message(get('import-status'), error.message, true);
  }
});
get('download').addEventListener('click', () => {
  if (changedFields().length) {
    message(get('status'), '请先应用或取消当前节点的修改。', true);
    get('apply').focus();
    return;
  }
  const url = URL.createObjectURL(new Blob([exportDocument(state.entries)], { type: 'application/json' }));
  const link = element('a');
  link.href = url;
  link.download = 'rich-document.json';
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 1000);
  savedEntries = state.entries;
  message(get('status'), '已发起 JSON 下载。');
});

document.addEventListener('keydown', event => {
  if (event.isComposing) return;
  if (form.contains(event.target) && get('apply')) {
    if (event.key === 'Escape') {
      event.preventDefault();
      form.reset();
    }
    if ((event.ctrlKey || event.metaKey) && event.key === 'Enter') {
      event.preventDefault();
      form.requestSubmit();
    }
  }
  if (!(event.ctrlKey || event.metaKey) || event.altKey
    || event.target.matches('textarea, input:not([type="checkbox"])')) return;
  const key = event.key.toLowerCase();
  if (key === 'z' || key === 'y') {
    event.preventDefault();
    if (key === 'y' || event.shiftKey) changeHistory(redo, undo, '已重做。');
    else changeHistory(undo, redo, '已撤销。');
  }
});

window.addEventListener('beforeunload', event => {
  if (state.entries === savedEntries && !changedFields().length) return;
  event.preventDefault();
  event.returnValue = '';
});

render();
