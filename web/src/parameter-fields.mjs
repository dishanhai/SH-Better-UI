// Author: dishanhai
const labels = {
  transmission: '通透度', refraction: '扭曲强度', lensing: '透镜强度', scattering: '模糊强度',
  edgeHighlight: '边缘高光', thickness: '边缘厚度', blurRadius: '模糊半径', quality: '采样质量', foreground: '前景文字',
};
const choices = {
  ULTRA: '超高 · ULTRA', BALANCED: '均衡 · BALANCED', PERFORMANCE: '性能 · PERFORMANCE',
  FALLBACK: '纯色 · FALLBACK', LIGHT: '亮色 · LIGHT', DARK: '深色 · DARK',
};
const units = { gui: 'GUI 单位', ratio: '无量纲', ms: '毫秒' };

export function parameterLabel(name) { return labels[name] ?? name; }
export function formatValue(value) { return typeof value === 'string' ? value : JSON.stringify(value); }

export function option(value, text = value) {
  const item = document.createElement('option');
  item.value = value;
  item.textContent = text;
  return item;
}

function input(type, value) {
  const field = document.createElement('input');
  field.type = type;
  field.value = value;
  return field;
}

function setBounds(field, spec) {
  if (spec.minimum !== null) field.min = spec.minimum;
  if (spec.maximum !== null) field.max = spec.maximum;
}

function row(entry, name, spec, value, change) {
  const wrapper = document.createElement('div');
  wrapper.className = 'parameter';
  const label = document.createElement('label');
  const fieldId = `parameter-${name}`;
  label.htmlFor = fieldId;
  const nameText = document.createElement('span');
  nameText.textContent = parameterLabel(name);
  if (nameText.textContent !== name) {
    const key = document.createElement('code');
    key.textContent = name;
    nameText.append(key);
  }
  label.append(nameText);

  let field;
  let slider;
  let valueReader;
  if (['integer', 'float', 'duration'].includes(spec.type)) {
    field = input('number', value);
    setBounds(field, spec);
    field.step = spec.type === 'float' ? 'any' : 1;
    valueReader = () => field.valueAsNumber;
    if (Number.isFinite(spec.minimum) && Number.isFinite(spec.maximum) && spec.step > 0) {
      slider = input('range', value);
      setBounds(slider, spec);
      slider.step = spec.step;
      slider.value = value;
      slider.setAttribute('aria-label', `${parameterLabel(name)}滑块`);
      slider.disabled = !spec.editable;
    }
  } else if (spec.type === 'boolean') {
    field = input('checkbox', 'on');
    field.checked = value;
    valueReader = () => field.checked;
  } else if (spec.type === 'enum' || spec.type === 'texture') {
    field = document.createElement('select');
    const values = spec.type === 'enum' ? spec.choices : entry.requiredResources;
    field.append(...values.map(item => option(item, choices[item] ?? item)));
    field.value = value;
    valueReader = () => field.value;
  } else if (spec.type === 'color') {
    field = input('text', value);
    valueReader = () => field.value;
  } else {
    field = document.createElement('textarea');
    field.rows = 3;
    field.value = JSON.stringify(value);
    valueReader = () => JSON.parse(field.value);
  }

  field.id = fieldId;
  field.disabled = !spec.editable;
  const notes = document.createElement('small');
  notes.className = 'parameter-meta';
  notes.id = `${fieldId}-meta`;
  notes.textContent = [
    units[spec.unit] ?? spec.unit,
    `默认 ${formatValue(spec.defaultValue)}`,
    spec.editable ? '可编辑' : '只读',
    spec.requiresResourceRebuild ? '需重建资源' : '',
    spec.runtimeUpdate ? '运行时更新' : '',
    spec.animatable ? '可动画' : '',
    ...(spec.dependsOn?.length ? [`依赖 ${spec.dependsOn.join('、')}`] : []),
    ...(spec.constraints?.length ? [`约束 ${JSON.stringify(spec.constraints)}`] : []),
  ].filter(Boolean).join(' · ');
  field.setAttribute('aria-describedby', notes.id);
  field.addEventListener('change', () => {
    const ok = change(name, valueReader);
    field.setAttribute('aria-invalid', String(!ok));
    if (ok && slider) slider.value = field.value;
  });

  wrapper.append(label);
  if (slider) {
    const numeric = document.createElement('div');
    numeric.className = 'numeric';
    slider.setAttribute('aria-describedby', notes.id);
    slider.addEventListener('input', () => {
      if (change(name, () => slider.valueAsNumber)) {
        field.value = slider.value;
        field.removeAttribute('aria-invalid');
      }
    });
    numeric.append(slider, field);
    wrapper.append(numeric);
  } else {
    wrapper.append(field);
  }
  wrapper.append(notes);
  return wrapper;
}

export function showParameterFields(container, entry, values, change) {
  const rows = Object.entries(entry.parameters).map(([name, spec]) => row(entry, name, spec, values[name], change));
  if (!rows.length) {
    const hint = document.createElement('p');
    hint.className = 'hint';
    hint.textContent = '目录尚未发布可调参数。请参阅公开 API。';
    rows.push(hint);
  }
  container.replaceChildren(...rows);
}
