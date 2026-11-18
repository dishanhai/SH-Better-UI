// Author: dishanhai
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { chromium, eventually } from './chromium.mjs';
import { serve } from '../scripts/serve.mjs';
const server = await serve();
const browser = await chromium();
try {
  await browser.command('Page.navigate', { url: server.url + 'liquid-glass.html' });
  await eventually(() => browser.evaluate('Boolean(window.glassKit)'), 'glass kit mount');
  const read = await browser.evaluate(`(async()=>{
    const root=document.querySelector('#glass-kit');
    const tabs=[...root.querySelectorAll('[role=tab]')];
    tabs[0].focus();tabs[0].dispatchEvent(new KeyboardEvent('keydown',{key:'ArrowRight',bubbles:true,cancelable:true}));
    const switched=tabs[1].getAttribute('aria-selected')==='true'&&!document.querySelector('#library').hidden&&document.activeElement===tabs[1];
    tabs[1].dispatchEvent(new KeyboardEvent('keydown',{key:'End',bubbles:true,cancelable:true}));
    const end=tabs[2].getAttribute('aria-selected')==='true';
    const open=root.querySelector('[data-open-dialog]');open.click();open.click();
    const dialog=root.querySelector('dialog');const modal=dialog.open;
    dialog.close();
    const deadline=Date.now()+2000;
    while(document.activeElement!==open&&Date.now()<deadline) await new Promise(r=>setTimeout(r,25));
    const returned=document.activeElement===open;
    const contrast=root.querySelector('[data-glass-pref=contrast]');contrast.checked=true;contrast.dispatchEvent(new Event('change'));
    const high=root.dataset.contrast==='high';
    const slider=root.querySelector('#volume');slider.value='100';slider.dispatchEvent(new Event('input'));const volume=root.querySelector('#volume-value').value==='100';
    open.click();window.glassKit.dispose();await new Promise(r=>setTimeout(r,50));open.click();const disposed=!dialog.open;
    return {switched,end,modal,returned,high,volume,disposed};
  })()`);
  for(const [name,value] of Object.entries(read)) assert.equal(value,true,name);
  await browser.evaluate('window.glassKit = null');
  await browser.command('Page.reload');
  await eventually(() => browser.evaluate('Boolean(window.glassKit)'), 'reload');
  await browser.command('Emulation.setDeviceMetricsOverride',{width:390,height:844,deviceScaleFactor:1,mobile:true});
  const overflow=await browser.evaluate('document.documentElement.scrollWidth>innerWidth');
  assert.equal(overflow,false,'mobile horizontal overflow');
  await browser.command('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
  assert.equal(await browser.evaluate("getComputedStyle(document.querySelector('.lg-button')).transitionDuration"),'0s');
  const output=new URL('../../build/liquid-glass-web/',import.meta.url);await mkdir(output,{recursive:true});
  let shot=await browser.command('Page.captureScreenshot',{format:'png'});await writeFile(new URL('mobile.png',output),Buffer.from(shot.data,'base64'));
  await browser.command('Emulation.setDeviceMetricsOverride',{width:1280,height:1000,deviceScaleFactor:1,mobile:false});
  shot=await browser.command('Page.captureScreenshot',{format:'png'});await writeFile(new URL('desktop.png',output),Buffer.from(shot.data,'base64'));
  console.log('Liquid Glass Web interactions, focus restore, repeat open, disposal, preferences, mobile layout passed.');
}finally{await browser.close();await server.close();}
