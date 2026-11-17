/* Original local interaction study. No audio, accounts, or platform requests. */
'use strict';
const tracks = [
 ['山间来信','林间工作室','慢下来',241],['薄荷色的风','林间工作室','慢下来',204],
 ['沿着河流','青石','夏日手记',228],['不赶时间','青石','夏日手记',193],
 ['傍晚六点','森声实验室','城市切片',256],['玻璃窗上的雨','森声实验室','城市切片',217],
 ['回家的小路','林间工作室','慢下来',231],['远山与晨光','青石','夏日手记',268]
].map(([title,artist,album,duration],id)=>({id,title,artist,album,duration}));
const state={index:0,playing:false,position:0,filter:'all',query:'',vinyl:false,likes:new Set([0,2,5])};
const $=s=>document.querySelector(s);
const format=s=>`${String(Math.floor(s/60)).padStart(2,'0')}:${String(Math.floor(s%60)).padStart(2,'0')}`;
function announce(s){$('#status').textContent=s;}
function visibleTracks(){const q=state.query.toLocaleLowerCase();return tracks.filter(t=>(state.filter!=='liked'||state.likes.has(t.id))&&`${t.title} ${t.artist} ${t.album}`.toLocaleLowerCase().includes(q));}
function toggleLike(id){if(state.likes.has(id))state.likes.delete(id);else state.likes.add(id);renderList();renderPlayer();announce(state.likes.has(id)?'已喜欢，可再次点击取消':'已取消喜欢');}
function select(id){state.index=id;state.position=0;state.playing=true;renderList();renderPlayer();announce(`演示播放：${tracks[id].title}，不输出音频`);}
function renderList(){const list=$('#tracks');list.replaceChildren();const items=visibleTracks();$('#count').textContent=items.length;$('#empty').hidden=items.length>0;$('#list-title').firstChild.textContent=state.filter==='liked'?'我喜欢 ':'全部歌曲 ';for(const t of items){
 const row=document.createElement('div');row.className='track'+(state.index===t.id?' selected':'');
 const button=document.createElement('button');button.className='track-main';button.setAttribute('aria-label',`演示播放 ${t.title}，${t.artist}`);button.setAttribute('aria-current',String(t.id===state.index));button.addEventListener('click',()=>select(t.id));
 const art=document.createElement('span');art.className='sleeve mini';art.textContent='山';art.setAttribute('aria-hidden','true');
 const copy=document.createElement('span');copy.className='track-copy';const title=document.createElement('span');title.className='track-title';title.textContent=t.title;const artist=document.createElement('span');artist.className='track-artist';artist.textContent=t.artist;copy.append(title,artist);button.append(art,copy);
 const album=document.createElement('span');album.className='track-album';album.textContent=t.album;const duration=document.createElement('span');duration.className='track-end';duration.textContent=format(t.duration);
 const like=document.createElement('button');like.className='like';like.textContent=state.likes.has(t.id)?'♥':'♡';like.setAttribute('aria-label',`${state.likes.has(t.id)?'取消喜欢':'喜欢'} ${t.title}`);like.setAttribute('aria-pressed',String(state.likes.has(t.id)));like.onclick=()=>{toggleLike(t.id);const replacement=[...$('#tracks').querySelectorAll('.like')].find(b=>b.getAttribute('aria-label').endsWith(' '+t.title));(replacement||$('#like-current')).focus();};row.append(button,album,duration,like);list.append(row);
}}
function renderPlayer(){const t=tracks[state.index];$('#now-title').textContent=$('#studio-title').textContent=t.title;$('#now-artist').textContent=$('#studio-artist').textContent=t.artist+' · '+t.album;$('#play').textContent=state.playing?'Ⅱ':'▶';$('#play').setAttribute('aria-label',state.playing?'暂停演示':'播放演示');$('#play').setAttribute('aria-pressed',String(state.playing));document.body.classList.toggle('playing',state.playing);$('#duration').textContent=format(t.duration);$('#seek').max=t.duration;$('#seek').value=state.position;$('#elapsed').textContent=format(state.position);$('#seek').setAttribute('aria-valuetext',`${format(state.position)} / ${format(t.duration)}`);$('#like-current').textContent=state.likes.has(t.id)?'♥':'♡';$('#like-current').setAttribute('aria-pressed',String(state.likes.has(t.id)));$('#like-current').setAttribute('aria-label',state.likes.has(t.id)?'取消喜欢当前歌曲':'喜欢当前歌曲');}
function setMode(v){state.vinyl=v;document.body.classList.toggle('vinyl',v);$('.library').hidden=v;$('.studio').hidden=!v;$('#mode').textContent=v?'☷ 返回列表':'◉ 唱机模式';$('#mode').setAttribute('aria-pressed',String(v));$('.search').hidden=v;announce(v?'已进入唱机模式':'已返回列表');}
$('#search').addEventListener('input',e=>{state.query=e.target.value;renderList();});
for(const b of document.querySelectorAll('[data-filter]'))b.onclick=()=>{state.filter=b.dataset.filter;setMode(false);document.querySelectorAll('.nav').forEach(n=>{const active=n.dataset.filter===state.filter;n.classList.toggle('active',active);n.setAttribute('aria-pressed',String(active));});renderList();};
$('#mode').onclick=()=>setMode(!state.vinyl);$('#now').onclick=()=>setMode(!state.vinyl);
$('#play-all').onclick=()=>{const first=visibleTracks()[0];if(first)select(first.id);else announce('没有可播放的示例');};
$('#play').onclick=()=>{state.playing=!state.playing;renderPlayer();announce(state.playing?'演示计时开始，不输出音频':'演示已暂停');};
$('#previous').onclick=()=>select((state.index+tracks.length-1)%tracks.length);$('#next').onclick=()=>select((state.index+1)%tracks.length);
$('#like-current').onclick=()=>toggleLike(state.index);$('#seek').oninput=e=>{state.position=Number(e.target.value);renderPlayer();};
document.addEventListener('keydown',e=>{if(e.key==='Escape'&&state.vinyl){setMode(false);$('#mode').focus();}});
let last=performance.now();setInterval(()=>{const now=performance.now();const dt=(now-last)/1000;last=now;if(state.playing&&!document.hidden){state.position=Math.min(tracks[state.index].duration,state.position+dt);if(state.position>=tracks[state.index].duration){state.playing=false;announce('示例播放完毕');}renderPlayer();}},250);
renderList();renderPlayer();if(matchMedia('(max-width:560px)').matches)setMode(true);
