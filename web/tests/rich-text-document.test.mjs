// Author: dishanhai
import assert from 'node:assert/strict';
import { test } from 'node:test';
import { editNode, exportDocument, importDocument, insertNode } from '../src/rich-text-document.mjs';

const documentJson = (...nodes) => `{"version":1,"nodes":[${nodes.join(',')}]}`;
const text = String.raw`{"id":"demo:text","type":"text","text":"甲\r\nالعربية 👩‍🔬","style":{"color":1193046,"bold":true}}`;
const localized = '{"id":"demo:key","type":"localized","key":"demo.translation","fallback":"回退","arguments":["%s","3"]}';
const paragraph = '{"id":"demo:gap","type":"paragraph","spacing":12}';
const image = '{"id":"demo:media","type":"image","texture":"demo:textures/icon.apng","width":32,"height":16,"alt":"标本"}';
const extension = String.raw`{"id":"demo:extension","type":"extension","extension":"demo:status","version":1,"width":14,"height":8,"logicalHeight":8,"alt":"测试","payload":"{\"value\":42}"}`;

test('v1 passive fields round-trip and a style edit keeps the original Unicode text', () => {
  const original = documentJson(text, localized, paragraph, image);
  const entries = importDocument(original);
  assert.equal(exportDocument(entries), original);
  const { value } = entries[0];
  const changed = editNode(entries, 0, { ...value, style: { ...value.style, italic: true } });
  const reloaded = importDocument(exportDocument(changed));
  assert.equal(reloaded[0].value.text, '甲\r\nالعربية 👩‍🔬');
  assert.equal(reloaded[0].value.style.italic, true);
  assert.equal(entries[0].value.style.italic, undefined);
  const inserted = insertNode(insertNode(reloaded, 0, 'image'), 1, 'paragraph');
  assert.equal(inserted[0].value.id, 'workbench:node_1');
  assert.equal(inserted[1].value.id, 'workbench:node_2');
  assert.equal(importDocument(exportDocument(inserted)).length, 6);
});

test('unknown types, extended fields and passive extension envelopes preserve their source text', () => {
  const unknown = '{ "type": "future", "id": "demo:future", "version": 18446744073709551617, "nested": {"x":null,"__proto__":{"a":1.6e4}} }';
  const addedField = '{"id":"demo:style","type":"text","text":"样本","style":{"newField":null}}';
  const entries = importDocument(documentJson(unknown, addedField, extension, text));
  assert.match(entries[0].requirement, /未知节点/);
  assert.match(entries[1].requirement, /额外字段/);
  assert.match(entries[2].requirement, /显式输入策略/);
  assert.throws(() => editNode(entries, 2, entries[2].value), /受保护/);
  const changed = editNode(entries, 3, { ...entries[3].value, text: '新文档' });
  assert.ok(exportDocument(changed).includes(documentJson(unknown, addedField, extension).slice(0, -2)));
});

test('ambiguous JSON, invalid native fields and import limits fail atomically', () => {
  assert.throws(() => importDocument(documentJson(text, text)), /ID 重复/);
  assert.throws(() => importDocument(documentJson(text.replace('"text":', '"id":"demo:old","text":'))), /字段重复/);
  assert.throws(() => importDocument('{"version":1e0,"nodes":[]}'), /version/);
  assert.throws(() => importDocument('{"version":1,"nodes":[],}'), /逗号/);
  assert.throws(() => importDocument(documentJson(text.replace('1193046', '1193046.0'))), /RGB/);
  assert.throws(() => importDocument(documentJson(text.replace('甲', String.raw`\ud800`))), /Unicode/);
  assert.throws(() => importDocument(documentJson(image.replace('demo:textures/icon.apng', 'https://example.com/icon.png'))), /本地 PNG/);
  assert.throws(() => importDocument(documentJson(image.replace('"width":32,"height":16', '"width":4096,"height":4096'))), /总面积/);
  assert.throws(() => importDocument(documentJson(paragraph.replace('12', '257'))), /段落间距/);
  assert.throws(() => importDocument(documentJson('{"id":"demo:deep","type":"future","payload":'
    + '['.repeat(17) + '0' + ']'.repeat(17) + '}')), /嵌套/);
  assert.throws(() => importDocument(documentJson(...Array.from({ length: 4097 }, (_, i) => `{"id":"a:${i}","type":"text","text":""}`))), /4096/);
  assert.throws(() => importDocument(documentJson(text.replace('甲', '界'.repeat(90_000)))), /256 KiB/);
  assert.equal(importDocument(documentJson(text.replace('甲', 'a'.repeat(200_000)))).length, 1);
});
