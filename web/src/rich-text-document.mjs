// Author: dishanhai
const MAX_BYTES = 256 * 1024;
const MAX_NODES = 4096;
const MAX_DEPTH = 16;
const nonNativeNumber = Symbol('non-native JSON number');
const encoder = new TextEncoder();

export const styleFlags = new Map([
  ['bold', '粗体'], ['italic', '斜体'], ['underlined', '下划线'],
  ['strikethrough', '删除线'], ['obfuscated', '原生混淆字符'],
]);

const fields = {
  text: ['id', 'type', 'text', 'style'],
  localized: ['id', 'type', 'key', 'fallback', 'arguments', 'style'],
  paragraph: ['id', 'type', 'spacing'],
  image: ['id', 'type', 'texture', 'width', 'height', 'alt'],
  extension: ['id', 'type', 'extension', 'version', 'width', 'height', 'logicalHeight', 'alt', 'payload'],
};
const styleFields = ['color', 'font', ...styleFlags.keys()];
const isObject = value => value !== null && typeof value === 'object' && !Array.isArray(value);
const extraFields = (value, allowed) => Object.keys(value).some(key => !allowed.includes(key));

function requireValue(condition, message) {
  if (!condition) throw new Error(message);
}

function checkSize(value, limit = MAX_BYTES) {
  requireValue(typeof value === 'string' && value.isWellFormed(), '需要有效的 Unicode 文本。');
  requireValue(value.length <= limit && encoder.encode(value).length <= limit,
    limit === MAX_BYTES ? 'RichDocument JSON 必须在 256 KiB 以内。' : `文本必须在 ${limit} 字节以内。`);
}

// Track object spans to round-trip opaque nodes, including numbers outside JavaScript's precise range.
// Detect duplicate fields before their values could be discarded by JSON.parse.
function readJson(source) {
  const token = /[ \r\n\t]*("(?:[^"\\\u0000-\u001f]|\\(?:["\\/bfnrt]|u[\da-fA-F]{4}))*"|true|false|null|-?(?:0|[1-9]\d*)(?:\.\d+)?(?:[eE][+-]?\d+)?|[{}\[\]:,])/y;
  const ranges = new WeakMap();
  let position = 0;
  let current = nextToken();

  function nextToken() {
    token.lastIndex = position;
    const match = token.exec(source);
    if (!match) {
      requireValue(/^[ \r\n\t]*$/.test(source.slice(position)), `JSON 格式错误（位置 ${position + 1}）。`);
      return '';
    }
    position = token.lastIndex;
    return match[1];
  }

  function take(expected) {
    requireValue(current === expected, `JSON 格式错误（位置 ${position}）。`);
    current = nextToken();
  }

  function readValue(depth) {
    requireValue(depth <= MAX_DEPTH, 'JSON 嵌套最多 16 层。');
    const start = position - current.length;
    if (current === '{') {
      const result = Object.create(null);
      take('{');
      while (current !== '}') {
        requireValue(current.startsWith('"'), `JSON 需要字段名（位置 ${position}）。`);
        const key = JSON.parse(current);
        requireValue(!Object.hasOwn(result, key), `JSON 字段重复（位置 ${position}）。`);
        current = nextToken();
        take(':');
        result[key] = readValue(depth + 1);
        if (current === '}') break;
        take(',');
        requireValue(current !== '}', 'JSON 对象末尾不能有逗号。');
      }
      ranges.set(result, [start, position]);
      take('}');
      return result;
    }
    if (current === '[') {
      const result = [];
      take('[');
      while (current !== ']') {
        requireValue(result.length < MAX_NODES, 'JSON 数组最多包含 4096 个元素。');
        result.push(readValue(depth + 1));
        if (current === ']') break;
        take(',');
        requireValue(current !== ']', 'JSON 数组末尾不能有逗号。');
      }
      take(']');
      return result;
    }
    const literal = current;
    requireValue(/^(?:"|-|\d|true$|false$|null$)/.test(literal), `JSON 缺少值（位置 ${position}）。`);
    current = nextToken();
    if (/^[-\d]/.test(literal)) {
      const number = Number(literal);
      return /^-?\d+$/.test(literal) && number >= -2147483648 && number <= 2147483647
        ? number : nonNativeNumber;
    }
    return JSON.parse(literal);
  }

  const value = readValue(0);
  requireValue(current === '', 'JSON 末尾存在多余内容。');
  return { value, original: object => source.slice(...ranges.get(object)) };
}

const isId = value => typeof value === 'string' && /^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(value);
const isResource = value => isId(value) && !value.split(/[:/]/).some(part => ['', '.', '..'].includes(part));
const isInt = (value, low, high) => Number.isInteger(value) && value >= low && value <= high;

function dimensions(value) {
  requireValue(isInt(value.width, 1, 4096) && isInt(value.height, 1, 4096)
    && value.width * value.height <= 4 * 1024 * 1024, '宽高必须为 1–4096 的整数，总面积不得超过 4 Mi 像素。');
}

function validateStyle(style) {
  requireValue(isObject(style), '样式必须是对象。');
  for (const flag of styleFlags.keys()) {
    if (Object.hasOwn(style, flag)) requireValue(typeof style[flag] === 'boolean', `${styleFlags.get(flag)}需要布尔值。`);
  }
  if (Object.hasOwn(style, 'color')) requireValue(isInt(style.color, 0, 0xffffff), '颜色必须是 24 位 RGB 整数。');
  if (Object.hasOwn(style, 'font')) requireValue(isResource(style.font), '字体必须是命名空间内的本地资源 ID。');
}

function validateFields(value) {
  switch (value.type) {
    case 'text':
      checkSize(value.text);
      break;
    case 'localized':
      checkSize(value.key);
      requireValue(!/^[\u0009-\u000d\u001c-\u0020\u1680\u2000-\u2006\u2008-\u200a\u2028\u2029\u205f\u3000]*$/.test(value.key),
        '翻译键不能为空。');
      checkSize(value.fallback);
      requireValue(Array.isArray(value.arguments) && value.arguments.length <= 32, '翻译参数最多 32 个，须为字符串数组。');
      for (const argument of value.arguments) checkSize(argument);
      break;
    case 'paragraph':
      requireValue(isInt(value.spacing, 0, 256), '段落间距必须是 0–256 的整数。');
      break;
    case 'image':
      requireValue(isResource(value.texture) && /\.(?:png|gif|apng)$/.test(value.texture),
        '图片必须是命名空间内的本地 PNG、GIF 或 APNG 资源 ID。');
      dimensions(value);
      checkSize(value.alt);
      break;
    case 'extension':
      requireValue(isResource(value.extension), '扩展类型必须是命名空间内的本地资源 ID。');
      checkSize(value.extension, 128);
      requireValue(isInt(value.version, 1, 65535), '扩展版本必须是 1–65535 的整数。');
      dimensions(value);
      requireValue(isInt(value.logicalHeight, 1, 256), '扩展行高必须是 1–256 的整数。');
      checkSize(value.alt, 1024);
      checkSize(value.payload, 4096);
      break;
  }
  if (Object.hasOwn(value, 'style')) validateStyle(value.style);
}

function makeEntry(value, original) {
  requireValue(isObject(value) && isId(value.id), '节点 ID 必须是小写命名空间 ID。');
  requireValue(typeof value.type === 'string' && value.type.length > 0, '节点缺少类型。');
  let requirement = '';
  if (!Object.hasOwn(fields, value.type)) {
    requirement = '未知节点类型。当前 v1 原生 Codec 无法解码。';
  } else if (extraFields(value, fields[value.type]) || (isObject(value.style) && extraFields(value.style, styleFields))) {
    requirement = '节点包含额外字段。当前 v1 原生 Codec 无法解码。';
  } else {
    validateFields(value);
    if (value.type === 'extension') requirement = '扩展封装需要原生宿主提供显式输入策略。';
  }
  requireValue(!requirement || original, '受保护节点只允许保留原始数据。');
  return { value, requirement, original: requirement ? original(value) : null };
}

export function importDocument(source) {
  checkSize(source);
  const { value, original } = readJson(source);
  requireValue(isObject(value) && !extraFields(value, ['version', 'nodes']) && value.version === 1 && Array.isArray(value.nodes),
    '需要由 version: 1 和 nodes 组成的 RichDocument JSON。');
  const entries = value.nodes.map((node, index) => {
    try {
      return makeEntry(node, original);
    } catch (error) {
      throw new Error(`节点 ${index + 1}：${error.message}`);
    }
  });
  exportDocument(entries);
  return entries;
}

export function exportDocument(entries) {
  requireValue(entries.length <= MAX_NODES, '文档最多包含 4096 个节点。');
  const ids = new Set();
  const nodes = entries.map(entry => {
    requireValue(!ids.has(entry.value.id), `节点 ID 重复：${entry.value.id}`);
    ids.add(entry.value.id);
    return entry.original ?? JSON.stringify(entry.value);
  });
  const source = `{"version":1,"nodes":[${nodes.join(',')}]}`;
  checkSize(source);
  return source;
}

export function editNode(entries, index, value) {
  requireValue(entries[index] && !entries[index].requirement, '受保护节点无法编辑。');
  const result = entries.with(index, makeEntry(value));
  exportDocument(result);
  return result;
}

const defaults = {
  text: { text: '新文字' },
  localized: { key: 'example.translation', fallback: '翻译回退文本', arguments: [] },
  paragraph: { spacing: 4 },
  image: { texture: 'minecraft:textures/item/diamond.png', width: 16, height: 16, alt: '钻石图标' },
};

export function insertNode(entries, index, type) {
  requireValue(Object.hasOwn(defaults, type), '无法插入此类型。');
  const used = new Set(entries.map(entry => entry.value.id));
  let suffix = 1;
  while (used.has(`workbench:node_${suffix}`)) suffix += 1;
  const entry = makeEntry({ id: `workbench:node_${suffix}`, type, ...defaults[type] });
  const result = entries.toSpliced(index, 0, entry);
  exportDocument(result);
  return result;
}
