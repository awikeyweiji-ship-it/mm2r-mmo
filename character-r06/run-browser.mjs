import {chromium} from 'playwright';
import {spawn} from 'node:child_process';
import fs from 'node:fs';
fs.mkdirSync('out',{recursive:true});
const server=spawn('python3',['-m','http.server','4173','--bind','127.0.0.1'],{stdio:['ignore','pipe','pipe']});
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
await sleep(1200);
let browser;
try{
 browser=await chromium.launch({headless:true});
 const page=await browser.newPage({viewport:{width:1600,height:1100},deviceScaleFactor:1});
 const errors=[]; page.on('console',m=>{if(m.type()==='error')errors.push('console:'+m.text())}); page.on('pageerror',e=>errors.push('page:'+e.message));
 await page.goto('http://127.0.0.1:4173/character_r06_hair_body_candidate.html',{waitUntil:'domcontentloaded',timeout:120000});
 await page.waitForFunction(()=>{const t=document.querySelector('#r06-status')?.textContent||'';return /候选GLB已生成并回读|构建失败/.test(t)},null,{timeout:180000});
 const status=await page.locator('#r06-status').textContent();
 if(/失败/.test(status||'')) throw new Error('R06 build failed: '+status);
 async function shot(name,view,focus,mode='material'){
   await page.click('#focus-'+focus);
   await page.click('#view-'+view);
   await page.click('#mode-'+mode);
   await page.waitForTimeout(350);
   await page.locator('#viewport').screenshot({path:'out/'+name+'.png'});
 }
 await shot('head-front','front','head');
 await shot('head-quarter','quarter','head');
 await shot('head-profile','profile','head');
 await shot('full-front','front','whole');
 await shot('full-quarter','quarter','whole');
 await shot('full-profile','profile','whole');
 await shot('head-front-clay','front','head','clay');
 await shot('full-front-clay','front','whole','clay');
 const downloadPromise=page.waitForEvent('download');
 await page.click('#download-model');
 const download=await downloadPromise;
 await download.saveAs('out/character-r06-candidate.glb');
 const glb=fs.readFileSync('out/character-r06-candidate.glb');
 const jsonLength=glb.readUInt32LE(12);
 const sceneJSON=JSON.parse(glb.toString('utf8',20,20+jsonLength));
 const nodeSummary=(sceneJSON.nodes||[]).filter(n=>n.mesh!==undefined).map(n=>({name:n.name||'',meshName:sceneJSON.meshes[n.mesh]?.name||'',materials:(sceneJSON.meshes[n.mesh]?.primitives||[]).map(p=>sceneJSON.materials?.[p.material]?.name||null)}));
 fs.writeFileSync('out/mesh-manifest.json',JSON.stringify({meshNodes:nodeSummary,bones:(sceneJSON.skins||[]).map(s=>s.joints?.length||0),meshCount:sceneJSON.meshes?.length||0},null,2));
 const state=await page.evaluate(()=>window.characterViewer?.getState?.());
 fs.writeFileSync('out/browser-state.json',JSON.stringify({status,state,errors},null,2));
 if(errors.length) throw new Error('Browser errors: '+errors.join(' | '));
 console.log(JSON.stringify({status,state,glbBytes:fs.statSync('out/character-r06-candidate.glb').size},null,2));
} finally {
 if(browser) await browser.close();
 server.kill('SIGTERM');
}