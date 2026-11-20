// Author: dishanhai. The MusicKit SDK holds all Apple catalog and recommendation credentials.
const storefrontPath = '/v1/catalog/{{storefrontId}}';
const catalogId = { artists: /^[0-9]{1,30}$/, albums: /^[0-9]{1,30}$/, playlists: /^pl\.[A-Za-z0-9._-]{1,160}$/ };
const songId = /^[0-9]{1,30}$/;
const artwork = (value) => {
  if (typeof value !== 'string') return '';
  try {
    const url = new URL(value.replaceAll('{w}', '360').replaceAll('{h}', '360'));
    return url.protocol === 'https:' && !url.username && !url.password && !url.port && /(^|\.)mzstatic\.com$/.test(url.hostname) ? url.href : '';
  } catch { return ''; }
};
const label = (value) => typeof value === 'string' ? value.slice(0, 220) : '';
const card = (item, kind) => item?.type === kind && catalogId[kind].test(item?.id ?? '') ? {
  kind, id: item.id, name: label(item.attributes?.name) || item.id, artwork: artwork(item.attributes?.artwork?.url),
  subtitle: label(item.attributes?.curatorName || item.attributes?.artistName || item.attributes?.genreNames?.[0])
} : null;
function track(item) {
  const id = item?.id ?? '';
  return {
    id, name: label(item?.attributes?.name) || id || '未知曲目', artist: label(item?.attributes?.artistName),
    artwork: artwork(item?.attributes?.artwork?.url), duration: Math.max(0, Number(item?.attributes?.durationInMillis) || 0),
    playable: item?.type === 'songs' && songId.test(id) && Boolean(item?.attributes?.playParams)
  };
}

// Only follow Apple-relative continuations for the exact original endpoint.
function nextPage(next, expected) {
  if (next == null) return null;
  if (typeof next !== 'string' || !next.startsWith(expected + '?')) throw new Error('Invalid continuation');
  const result = new URL(next, 'https://api.music.apple.com');
  if (result.origin !== 'https://api.music.apple.com' || result.hash ||
      result.pathname !== expected || !result.search) throw new Error('Invalid continuation');
  return result.pathname + result.search;
}
export function createCatalog(music) {
  function root() {
    const region = music.storefrontCountryCode;
    if (!/^[a-z]{2}$/.test(region ?? '')) throw new Error('Storefront unavailable');
    return { base: storefrontPath.replace('{{storefrontId}}', region), region };
  }
  async function request(path, query) {
    const { data } = await music.api.music(path, query);
    if (!data || data.errors) throw new Error('Catalog unavailable');
    return data;
  }
  // Fetch the relationship to completion before enabling ordered playback. Never silently truncate a queue.
  async function relationship(path, region) {
    const items = [], seen = new Set();
    let next = path;
    do {
      if (seen.has(next) || seen.size >= 30 || music.storefrontCountryCode !== region) throw new Error('Catalog pagination unavailable');
      seen.add(next);
      const data = await request(next);
      if (!Array.isArray(data.data)) throw new Error('Invalid catalog relationship');
      items.push(...data.data);
      if (items.length > 1000) throw new Error('Catalog collection exceeds safe queue limit');
      next = nextPage(data.next, path);
    } while (next);
    if (music.storefrontCountryCode !== region) throw new Error('Storefront changed');
    return items;
  }
  return {
    async search(term) {
      const q = term.trim();
      if (!q || q.length > 100) throw new Error('Enter a catalog search term');
      const { base } = root();
      const { results } = await request(base + '/search', { types: 'artists,playlists', term: q, limit: 15 });
      if (!results) throw new Error('Invalid catalog search');
      return ['artists', 'playlists'].flatMap(kind => (results[kind]?.data ?? []).map(item => card(item, kind)).filter(Boolean));
    },
    async chartGenres() {
      const { base, region } = root();
      const { data } = await request(base + '/genres');
      if (!Array.isArray(data) || music.storefrontCountryCode !== region) throw new Error('Chart genres unavailable');
      const seen = new Set(), genres = [];
      for (const item of data) {
        const name = label(item?.attributes?.name), id = item?.id;
        if (item?.type !== 'genres' || typeof id !== 'string' || !songId.test(id) || !name || seen.has(id)) continue;
        seen.add(id); genres.push({ id, name });
      }
      return genres;
    },
    async charts(genre = '') {
      if (typeof genre !== 'string' || (genre && !songId.test(genre))) throw new Error('Invalid chart genre');
      const { base, region } = root();
      const { results } = await request(base + '/charts', { types: 'playlists', limit: 16, ...(genre && { genre }) });
      if (!results || music.storefrontCountryCode !== region) throw new Error('Invalid chart response');
      return (results.playlists ?? []).flatMap(chart => chart.data ?? []).map(item => card(item, 'playlists')).filter(Boolean);
    },
    async recommendations(isCurrent = () => true) {
      if (music.isAuthorized !== true) throw new Error('Authorization required');
      const { base, region } = root();
      const checkCurrent = () => {
        if (!isCurrent() || music.isAuthorized !== true || music.storefrontCountryCode !== region) {
          throw new Error('Apple Music session changed');
        }
      };
      // MusicKit Web adds its own Music User Token; never read it or send this through Java.
      const requestPersonal = async path => {
        checkCurrent();
        const response = await request(path);
        checkCurrent();
        return response;
      };
      const path = '/v1/me/recommendations';
      const { data } = await requestPersonal(path);
      if (!Array.isArray(data)) throw new Error('Invalid recommendations response');
      const seenPlaylists = new Set(), seenPages = new Set(), result = [];
      let itemCount = data.length;
      if (itemCount > 1000) throw new Error('Recommendation collection exceeds limit');
      for (const [index, recommendation] of data.entries()) {
        let contents = recommendation?.relationships?.contents;
        if (recommendation?.type !== 'personal-recommendation' || contents == null) continue;
        const section = {
          index, title: label(recommendation.attributes?.title?.stringForDisplay),
          reason: label(recommendation.attributes?.reason?.stringForDisplay)
        };
        do {
          if (!Array.isArray(contents.data)) throw new Error('Invalid recommendation contents');
          itemCount += contents.data.length;
          if (itemCount > 1000) throw new Error('Recommendation collection exceeds limit');
          for (const item of contents.data) {
            const entry = card(item, 'playlists');
            if (!entry || item.href !== `${base}/playlists/${entry.id}` || seenPlaylists.has(entry.id)) continue;
            seenPlaylists.add(entry.id); result.push({ ...entry, section });
          }
          if (contents.next == null) break;
          if (typeof recommendation.id !== 'string' || !recommendation.id) throw new Error('Invalid recommendation ID');
          const relationshipPath = path + '/' + encodeURIComponent(recommendation.id) + '/contents';
          const next = nextPage(contents.next, relationshipPath);
          if (seenPages.has(next) || seenPages.size >= 30) throw new Error('Recommendation pagination unavailable');
          seenPages.add(next);
          contents = await requestPersonal(next);
        } while (true);
      }
      return result;
    },
    async detail(kind, id) {
      if (!Object.hasOwn(catalogId, kind) || typeof id !== 'string' || !catalogId[kind].test(id)) throw new Error('Invalid catalog reference');
      const { base, region } = root(), path = `${base}/${kind}/${id}`;
      const metadata = request(path).then(response => {
        const item = card(response.data?.find(item => item.id === id), kind);
        if (!item) throw new Error('Catalog item unavailable');
        return item;
      });
      if (kind === 'artists') {
        const [item, tracks, albums, playlists] = await Promise.all([
          metadata,
          relationship(path + '/view/top-songs', region),
          request(path + '/view/featured-albums').catch(() => null),
          request(path + '/view/featured-playlists').catch(() => null)
        ]);
        return { item, region, tracks: tracks.map(track),
          albums: albums === null ? null : (albums.data ?? []).map(item => card(item, 'albums')).filter(Boolean),
          playlists: playlists === null ? null : (playlists.data ?? []).map(item => card(item, 'playlists')).filter(Boolean) };
      }
      const [item, tracks] = await Promise.all([metadata, relationship(path + '/tracks', region)]);
      return { item, region, tracks: tracks.map(track) };
    }
  };
}

// Keep duplicates and translate the selected display position across visibly unavailable rows.
export function orderedSelection(tracks, row) {
  if (!Number.isInteger(row) || !tracks[row]?.playable) return null;
  return { songs: tracks.filter(track => track.playable).map(track => track.id),
    index: tracks.slice(0, row).filter(track => track.playable).length };
}
