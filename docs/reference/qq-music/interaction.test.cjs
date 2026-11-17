// Node built-ins only; logic test, not a substitute for browser rendering QA.
const fs = require('node:fs');
const vm = require('node:vm');
const assert = require('node:assert/strict');
class Element {
  constructor(){this.children=[];this.hidden=false;this.dataset={};this.attrs={};this.value='';this.firstChild={textContent:''};this.classList={toggle(){}};}
  replaceChildren(){this.children=[];} append(...nodes){this.children.push(...nodes);}
  setAttribute(k,v){this.attrs[k]=v;} getAttribute(k){return this.attrs[k];}
  addEventListener(k,f){this['on'+k]=f;} focus(){this.focused=true;}
  querySelectorAll(){return this.children.flatMap(row=>row.children.filter(e=>e.className==='like'));}
}
const elements=new Map(); const element=s=>{if(!elements.has(s))elements.set(s,new Element());return elements.get(s);};
const navs=['all','liked'].map(filter=>{const e=new Element();e.dataset.filter=filter;return e;});
const document={body:new Element(),hidden:false,querySelector:element,querySelectorAll:()=>navs,createElement:()=>new Element(),addEventListener(k,f){this['on'+k]=f;}};
let tick;const context=vm.createContext({document,performance:{now:()=>0},setInterval:f=>tick=f,matchMedia:()=>({matches:false}),console});
vm.runInContext(fs.readFileSync(__dirname+'/app.js','utf8'),context);
const run=s=>vm.runInContext(s,context);
assert.equal(element('#tracks').children.length,8);
run("state.query='青石';renderList()");assert.equal(element('#tracks').children.length,3);
run("state.query='不存在';renderList()");assert.equal(element('#empty').hidden,false);
run("state.query='';state.filter='liked';renderList()");assert.equal(element('#tracks').children.length,3);
run('toggleLike(0)');assert.equal(element('#tracks').children.length,2);
run('toggleLike(0)');assert.equal(element('#tracks').children.length,3);
run('select(2)');assert.equal(element('#now-title').textContent,'沿着河流');assert.equal(element('#play').attrs['aria-pressed'],'true');
element('#play').onclick();assert.equal(element('#play').attrs['aria-pressed'],'false');
run('setMode(true)');assert.equal(element('.library').hidden,true);document.onkeydown({key:'Escape'});assert.equal(element('.studio').hidden,true);assert.equal(element('#mode').focused,true);
element('#seek').oninput({target:{value:'100'}});assert.equal(element('#elapsed').textContent,'01:40');
run('select(7)');element('#next').onclick();assert.equal(run('state.index'),0);element('#previous').onclick();assert.equal(run('state.index'),7);
run('state.position=tracks[7].duration;state.playing=true');tick();assert.equal(run('state.playing'),false);
console.log('PASS: 12 interaction/state checks (DOM stub; browser rendering unverified)');
