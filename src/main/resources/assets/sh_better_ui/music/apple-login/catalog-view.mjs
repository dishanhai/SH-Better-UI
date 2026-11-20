// Author: dishanhai. Direct catalog navigation in the existing Apple companion.
import { orderedSelection } from './catalog-flow.mjs';
export function createCatalogView(root, catalog, music, play) {
  const browse = root.querySelector('#catalog-browse'), detail = root.querySelector('#catalog-detail');
  const notice = root.querySelector('#catalog-status'), search = root.querySelector('form');
  const results = browse.querySelector('#catalog-results');
  const recommendations = root.querySelector('#catalog-recommendations');
  let revision = 0, closed = false, busy = false, activeDetail = null, personal = false;
  let playbackBlocked = true, displayedRegion = music.storefrontCountryCode;
  const element = (tag, text = '', css = '') => {
    const node = document.createElement(tag);
    node.textContent = text;
    if (css) node.className = css;
    return node;
  };
  function art(src, css = '') {
    if (!src) return element('span', '♫', 'catalog-art ' + css);
    const image = element('img', '', 'catalog-art ' + css);
    image.src = src; image.alt = ''; image.loading = 'lazy'; image.referrerPolicy = 'no-referrer';
    return image;
  }
  function button(label, action, css = '') {
    const node = element('button', label, css);
    node.type = 'button'; node.addEventListener('click', action);
    return node;
  }
  const time = duration => {
    if (!Number.isFinite(duration) || duration <= 0) return '';
    const seconds = Math.floor(duration / 1000);
    return Math.floor(seconds / 60) + ':' + String(seconds % 60).padStart(2, '0');
  };
  const kinds = { albums: '专辑', artists: '艺人', playlists: '歌单' };
  function cards(host, entries, title, back) {
    host.append(element('h3', title));
    if (entries === null) { host.append(element('p', '暂时无法读取此分组。')); return; }
    if (!entries?.length) { host.append(element('p', '没有目录内容。')); return; }
    const grid = element('ul', '', 'catalog-cards');
    for (const entry of entries) {
      const key = entry.kind + '/' + entry.id;
      const row = element('li'), open = button('', () => loadDetail(entry, () => {
        back();
        [...host.querySelectorAll('[data-catalog-entry]')].find(node => node.dataset.catalogEntry === key)?.focus();
      }), 'secondary catalog-card');
      open.dataset.catalogEntry = key;
      open.append(art(entry.artwork), element('strong', entry.name), element('small', entry.subtitle || kinds[entry.kind]));
      open.setAttribute('aria-label', `查看${kinds[entry.kind]}：${entry.name}`);
      row.append(open); grid.append(row);
    }
    host.append(grid);
  }
  function playButtons() {
    const available = activeDetail?.region === music.storefrontCountryCode;
    for (const node of detail.querySelectorAll('[data-play-row]')) {
      node.disabled = busy || playbackBlocked || !available;
    }
  }
  function showDetail(data, back) {
    activeDetail = data;
    const goBack = button('‹ 返回', back, 'secondary');
    const hero = element('div', '', 'catalog-hero');
    const copy = element('div');
    const heading = element('h3', data.item.name);
    heading.tabIndex = -1;
    copy.append(element('small', kinds[data.item.kind]), heading);
    if (data.item.subtitle) copy.append(element('p', data.item.subtitle));
    const start = (row) => {
      const selected = orderedSelection(data.tracks, row);
      if (selected && !closed && activeDetail === data && !busy && !playbackBlocked &&
          data.region === music.storefrontCountryCode) play(selected.songs, selected.index);
    };
    const first = data.tracks.findIndex(track => track.playable);
    if (first >= 0) {
      const startAll = button(data.item.kind === 'artists' ? '▶ 播放热门歌曲' : '▶ 从头播放', () => start(first));
      startAll.dataset.playRow = first; copy.append(startAll);
    }
    hero.append(art(data.item.artwork, 'catalog-cover'), copy);
    detail.replaceChildren(goBack, hero, element('h4', data.item.kind === 'artists' ? '热门歌曲' : '曲目'));
    if (!data.tracks.length) detail.append(element('p', '此目录没有曲目。'));
    else {
      const rows = element('ol', '', 'catalog-tracks');
      data.tracks.forEach((track, i) => {
        const row = element('li'), summary = element('span', '', 'catalog-track');
        summary.append(art(track.artwork), element('strong', track.name));
        if (track.artist) summary.append(element('small', track.artist));
        if (track.playable) {
          const play = button('', () => start(i), 'secondary catalog-row-play');
          const tail = element('span', '', 'catalog-track-end');
          if (time(track.duration)) tail.append(element('small', time(track.duration)));
          const arrow = element('span', '▶'); arrow.setAttribute('aria-hidden', 'true');
          tail.append(arrow); play.append(summary, tail);
          play.setAttribute('aria-label', `从第 ${i + 1} 首播放：${track.name}`);
          play.dataset.playRow = i; row.append(play);
        } else {
          summary.append(element('small', '不可播放')); row.append(summary);
        }
        rows.append(row);
      });
      detail.append(rows, element('p', '标为不可播放的曲目不会加入队列。其他曲目按这一页的顺序播放。', 'note'));
    }
    const reopen = () => { ++revision; busy = false; notice.textContent = ''; showDetail(data, back); };
    if (data.item.kind === 'artists') {
      cards(detail, data.albums, '代表专辑', reopen);
      cards(detail, data.playlists, '艺人精选歌单', reopen);
    }
    browse.hidden = true; detail.hidden = false;
    playButtons(); heading.focus();
  }
  function showBrowse(focusSearch = true) {
    ++revision; busy = false; activeDetail = null;
    browse.hidden = false; detail.hidden = true; notice.textContent = '';
    if (focusSearch) search.querySelector('input').focus();
  }
  async function loadDetail(entry, back) {
    const current = ++revision;
    busy = true; playButtons();
    notice.textContent = `正在加载${kinds[entry.kind]}…`;
    try {
      const data = await catalog.detail(entry.kind, entry.id);
      if (closed || current !== revision) return;
      busy = false;
      notice.textContent = '';
      showDetail(data, back);
    } catch {
      if (closed || current !== revision) return;
      busy = false; playButtons();
      notice.textContent = '无法载入目录详情或完整曲目列表。请检查地区与连接后重试。';
    }
  }
  async function loadBrowse(load, title, forYou = false) {
    if (closed || (forYou && music.isAuthorized !== true)) return;
    const current = ++revision;
    busy = false; personal = forYou;
    // Drop any previous personal content before switching to a different browse source.
    results.replaceChildren(); detail.replaceChildren();
    notice.textContent = forYou ? '正在向 Apple Music 请求个人推荐…' : '正在加载目录…';
    try {
      const entries = await load();
      if (closed || current !== revision) return;
      results.replaceChildren();
      if (forYou && !entries.length) results.append(element('p', 'Apple Music 暂无可显示的个人推荐歌单。'));
      else cards(results, entries, title, showBrowse);
      notice.textContent = `共 ${entries.length} 项`;
    } catch {
      if (closed || current !== revision) return;
      notice.textContent = forYou
        ? '无法读取个人推荐。请检查 Apple Music 授权和订阅后重试。'
        : '无法读取 Apple Music 目录。请检查地区与连接后重试。';
    }
  }
  search.addEventListener('submit', event => {
    event.preventDefault();
    void loadBrowse(() => catalog.search(search.querySelector('input').value), '艺人与歌单搜索');
  });
  browse.querySelector('#catalog-charts').addEventListener('click', () => {
    void loadBrowse(() => catalog.charts(), 'Apple Music 地区热门歌单');
  });
  recommendations.addEventListener('click', () => {
    void loadBrowse(() => catalog.recommendations(), '为你推荐的歌单', true);
  });
  for (const input of browse.querySelectorAll('button, input')) input.disabled = input === recommendations && music.isAuthorized !== true;
  return {
    update(blocked, authorized) {
      recommendations.disabled = !authorized;
      if (personal && (!authorized || displayedRegion !== music.storefrontCountryCode)) {
        personal = false;
        showBrowse(false); results.replaceChildren(); detail.replaceChildren();
      }
      if (playbackBlocked !== blocked || displayedRegion !== music.storefrontCountryCode) {
        playbackBlocked = blocked; displayedRegion = music.storefrontCountryCode; playButtons();
      }
    },
    close() { closed = true; ++revision; }
  };
}
