/* Infinite Studio Laboratory: project recovery, reversible futures, and safe API plumbing.
 * Requires the original studio script above this module in the document.
 * No credentials or paid API calls are embedded in this client.
 */
(() => {
  'use strict';
  const $ = id => document.getElementById(id);
  const MAX_PROJECT_BYTES = 160 * 1024 * 1024;
  const MAX_CANVAS_SIDE = 8000; // Legacy raster engine limit, deliberately disclosed.
  const BRANCHES = [
    {id:'salt',label:'Salt Print',type:'Local study',suffix:' graphic ink and warm grain'},
    {id:'night',label:'Blue Hour',type:'Local study',suffix:' nocturnal blue and silver'},
    {id:'ember',label:'Copper Light',type:'Local study',suffix:' warm copper and amber'}
  ];
  const H = {stack:[],index:-1,max:9,restoring:false};
  let lastFocus=null;
  let pendingAutosave=null;
  let backendReady=false;
  let selectedBranch=0;
  let currentFutures=null;
  let futuresBusy=false;
  let bootComplete=false;
  let recoverAvailable=false;

  function status(msg){const el=$('lab-status');if(el)el.textContent=msg;}
  function feedback(msg){const el=$('lab-progress');if(el)el.textContent=msg;}
  function storeStatus(msg){const el=$('lab-project-status');if(el)el.textContent=msg;}
  function safeToast(msg){if(typeof showToast==='function')showToast(msg);}
  function showDialog(id){lastFocus=document.activeElement;$(id).classList.remove('lab-hidden');const b=$(id).querySelector('button:not([disabled])');if(b)b.focus();}
  function hideDialog(id){$(id).classList.add('lab-hidden');if(lastFocus&&typeof lastFocus.focus==='function')lastFocus.focus();}

  function validBounds(b){return b&&[b.minX,b.minY,b.width,b.height].every(Number.isFinite)&&b.width>=1&&b.height>=1&&b.width<=MAX_CANVAS_SIDE&&b.height<=MAX_CANVAS_SIDE;}
  function captureDocument(){
    if(!validBounds(state.canvasBounds))throw new Error('Canvas dimensions exceed the supported limit.');
    return {
      format:'infinite-studio-project',version:2,createdAt:new Date().toISOString(),
      bounds:{...state.canvasBounds},paperColor:state.paperColor,
      activeLayerId:state.activeLayerId,
      view:{scale:state.view.scale,x:state.view.x,y:state.view.y},
      layers:state.layers.map(l=>({id:l.id,name:l.name,visible:l.visible,opacity:l.opacity,
        blendMode:l.blendMode,alphaLock:l.alphaLock,png:l.canvas.toDataURL('image/png')}))
    };
  }
  function estimate(doc){return doc.layers.reduce((n,l)=>n+l.png.length,0);}
  // A complete document snapshot rather than the previous active-layer-only undo.
  // Compressed PNG history is capped by count and total encoded bytes.
  function pushDocumentHistory(){
    if(H.restoring)return;
    try{
      const doc=captureDocument();const sig=JSON.stringify(doc.layers.map(l=>[l.id,l.name,l.visible,l.opacity,l.blendMode,l.alphaLock,l.png])).concat(JSON.stringify(doc.bounds),doc.paperColor);
      if(H.index>=0&&H.stack[H.index]?.sig===sig)return;
      H.stack.splice(H.index+1);
      H.stack.push({doc,sig});
      if(H.stack.length>H.max)H.stack.shift();
      let bytes=H.stack.reduce((n,x)=>n+estimate(x.doc),0);
      while(bytes>42*1024*1024&&H.stack.length>2){H.stack.shift();bytes=H.stack.reduce((n,x)=>n+estimate(x.doc),0);}
      H.index=H.stack.length-1;
      updateUndoRedoButtons();
      if(bootComplete)queueAutosave(doc);
    }catch(e){console.error(e);status('History limited · save project');safeToast('History capture failed; save your project now');}
  }
  async function decodePNG(data){
    if(typeof data!=='string'||!data.startsWith('data:image/png;base64,'))throw new Error('Invalid layer image.');
    const img=new Image();img.src=data;
    if(typeof img.decode==='function')await img.decode();
    else await new Promise((ok,no)=>{img.onload=ok;img.onerror=no;});
    return img;
  }
  function validateProject(doc){
    if(!doc||doc.format!=='infinite-studio-project'||doc.version!==2||!validBounds(doc.bounds))throw new Error('Unsupported or invalid project format.');
    if(!Array.isArray(doc.layers)||doc.layers.length<1||doc.layers.length>40)throw new Error('Layer count is invalid.');
    if(estimate(doc)>MAX_PROJECT_BYTES)throw new Error('Project exceeds the safe size limit.');
    const ids=new Set();
    for(const l of doc.layers){
      if(!l||typeof l.id!=='string'||l.id.length>100||ids.has(l.id)||typeof l.png!=='string'||!l.png.startsWith('data:image/png;base64,'))throw new Error('Invalid layer data.');
      if(typeof l.name!=='string'||l.name.length>200||typeof l.visible!=='boolean'||typeof l.opacity!=='number'||l.opacity<0||l.opacity>1)throw new Error('Invalid layer metadata.');
      ids.add(l.id);
    }
  }
  async function restoreDocument(doc){
    validateProject(doc);
    const imgs=await Promise.all(doc.layers.map(l=>decodePNG(l.png)));
    imgs.forEach(img=>{if(img.width!==doc.bounds.width||img.height!==doc.bounds.height)throw new Error('Layer dimensions do not match the document.');});
    H.restoring=true;
    try{
      const newLayers=doc.layers.map((l,i)=>{
        const canvas=document.createElement('canvas');canvas.width=doc.bounds.width;canvas.height=doc.bounds.height;
        canvas.className='absolute pointer-events-none';canvas.dataset.layerId=l.id;
        const ctx=canvas.getContext('2d',{willReadFrequently:true});ctx.lineJoin='round';ctx.lineCap='round';ctx.drawImage(imgs[i],0,0);
        return{id:l.id,name:l.name,visible:l.visible,opacity:l.opacity,blendMode:l.blendMode||'source-over',alphaLock:!!l.alphaLock,canvas,ctx};
      });
      state.layers=newLayers;state.canvasBounds={...doc.bounds};state.activeLayerId=newLayers.some(l=>l.id===doc.activeLayerId)?doc.activeLayerId:newLayers[0].id;
      state.paperColor=typeof doc.paperColor==='string'?doc.paperColor:'#f4f1ea';
      const paper=$('paper-layer');if(state.paperColor==='transparent'){paper.style.backgroundColor='transparent';paper.classList.add('canvas-checkerboard');}
      else{paper.style.backgroundColor=state.paperColor;paper.classList.remove('canvas-checkerboard');}
      const v=doc.view||{};
      state.view.scale=typeof v.scale==='number'&&v.scale>=.15&&v.scale<=4?v.scale:1;
      state.view.x=Number.isFinite(v.x)?v.x:0;state.view.y=Number.isFinite(v.y)?v.y:0;
      $('overlay-canvas').width=doc.bounds.width;$('overlay-canvas').height=doc.bounds.height;
      syncCanvasDOM();renderLayerUI();applyViewportTransform();clearSelection();
    }finally{H.restoring=false;}
  }
  async function stepHistory(direction){
    if(H.restoring||futuresBusy)return;
    const target=H.index+direction;
    if(target<0||target>=H.stack.length){safeToast('No further history');return;}
    try{await restoreDocument(H.stack[target].doc);H.index=target;updateUndoRedoButtons();safeToast(direction<0?'Undo':'Redo');if(bootComplete)queueAutosave(H.stack[target].doc);}
    catch(e){console.error(e);safeToast('History restore failed; canvas unchanged');}
  }
  function refreshHistoryButtons(){$('btn-undo').disabled=H.index<=0;$('btn-redo').disabled=H.index>=H.stack.length-1;}

  // Dynamic names referenced by the original application's event handlers.
  // Install before DOMContentLoaded initializes the legacy studio.
  pushHistory=pushDocumentHistory;
  undo=()=>{void stepHistory(-1);};
  redo=()=>{void stepHistory(1);};
  updateUndoRedoButtons=refreshHistoryButtons;
  runAIGeneration=()=>{openFutures($('input-ai-prompt').value.trim());};

  function newHistoryRoot(){H.stack.length=0;H.index=-1;pushDocumentHistory();}
  function queueAutosave(doc){
    clearTimeout(pendingAutosave);
    pendingAutosave=setTimeout(()=>{void writeAutosave(doc);},4000);
  }
  function openDB(){return new Promise((resolve,reject)=>{
    if(!('indexedDB' in window))return reject(new Error('Local storage unavailable'));
    const req=indexedDB.open('infinite-studio-drafts',1);
    req.onupgradeneeded=()=>{if(!req.result.objectStoreNames.contains('projects'))req.result.createObjectStore('projects');};
    req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error||new Error('Storage error'));
  });}
  async function withDB(mode,cb){const db=await openDB();return new Promise((resolve,reject)=>{
    const tx=db.transaction('projects',mode);const store=tx.objectStore('projects');
    let req;try{req=cb(store);}catch(e){db.close();reject(e);return;}
    tx.oncomplete=()=>{const value=req?.result;db.close();resolve(value);};
    tx.onerror=()=>{const e=tx.error;db.close();reject(e||new Error('Local storage transaction failed'));};
    tx.onabort=()=>{db.close();reject(tx.error||new Error('Local storage aborted'));};
  });}
  async function writeAutosave(doc){try{await withDB('readwrite',store=>store.put(doc,'active'));recoverAvailable=true;$('lab-recover-project').disabled=false;status('Draft saved locally');}
    catch(e){console.warn(e);status('Auto recovery unavailable · save file');}}
  async function checkRecovery(){try{const d=await withDB('readonly',s=>s.get('active'));if(d&&d.layers&&d.layers.length){recoverAvailable=true;$('lab-recover-project').disabled=false;storeStatus('A local draft is available for recovery.');}}
    catch(e){console.warn('Draft recovery unavailable',e);}}
  function downloadProject(){try{
    const doc=captureDocument();const blob=new Blob([JSON.stringify(doc)],{type:'application/json'});
    const url=URL.createObjectURL(blob);const a=document.createElement('a');a.href=url;a.download='InfiniteStudio_'+new Date().toISOString().replace(/[:.]/g,'-')+'.iastudio';a.click();setTimeout(()=>URL.revokeObjectURL(url),15000);storeStatus('Editable project file prepared. Keep it somewhere safe.');
  }catch(e){console.error(e);storeStatus('Project export failed: '+e.message);}}
  async function importProject(file){
    if(!file)return;if(file.size>MAX_PROJECT_BYTES){storeStatus('This project file exceeds the import limit.');return;}
    try{
      const doc=JSON.parse(await file.text());validateProject(doc);
      if(!confirm('Open this project? The current canvas will be replaced. Save it first if needed.'))return;
      // Validation and decoding occur before any state change.
      await restoreDocument(doc);newHistoryRoot();queueAutosave(captureDocument());storeStatus('Editable project loaded successfully.');
    }catch(e){console.error(e);storeStatus('Could not open project: '+e.message);}
  }
  async function recoverDraft(){
    if(!recoverAvailable)return;
    try{const doc=await withDB('readonly',s=>s.get('active'));if(!doc)return;
      if(!confirm('Replace the current canvas with this browser’s recovered draft?'))return;
      await restoreDocument(doc);newHistoryRoot();storeStatus('Local draft recovered. Save a portable backup.');
    }catch(e){console.error(e);storeStatus('Recovery failed: '+e.message);}
  }
  function createBlank(){
    if(!confirm('Start a blank canvas? Export the current project before replacing it.'))return;
    state.layers=[];state.activeLayerId=null;state.canvasBounds={minX:-1200,minY:-800,width:2400,height:1600};
    $('overlay-canvas').width=2400;$('overlay-canvas').height=1600;
    createLayer('Ink & Detail',false);createLayer('Sketch Base',false);state.activeLayerId=state.layers[0].id;
    syncCanvasDOM();renderLayerUI();fitCanvasToScreen();clearSelection();newHistoryRoot();queueAutosave(captureDocument());storeStatus('Blank project created.');
  }

  function selectedRegion(){
    const selection=state.selection.active&&state.selection.type!=='full'&&state.selection.points.length>=3;
    const base=selection?computeSelectionBounds():(computeArtworkBoundingBox(55)||{x:-500,y:-350,w:1000,h:700});
    const margin=selection?Math.min(220,Math.max(60,Math.ceil(Math.max(base.w,base.h)*.18))):0;
    const bounds={x:Math.floor(base.x-margin),y:Math.floor(base.y-margin),w:Math.ceil(base.w+2*margin),h:Math.ceil(base.h+2*margin)};
    return{bounds,points:selection?state.selection.points.map(p=>({...p})):null,type:selection?state.selection.type:'full'};
  }
  function captureRegion(selection){
    const {bounds}=selection;const maxSide=1536;const scale=Math.min(1,maxSide/Math.max(bounds.w,bounds.h));
    const raw=renderFlattenedRegion(bounds,true);
    const out=document.createElement('canvas');out.width=Math.max(1,Math.round(bounds.w*scale));out.height=Math.max(1,Math.round(bounds.h*scale));
    out.getContext('2d').drawImage(raw,0,0,out.width,out.height);
    const mask=document.createElement('canvas');mask.width=out.width;mask.height=out.height;
    const ctx=mask.getContext('2d');ctx.fillStyle='rgba(0,0,0,1)';ctx.fillRect(0,0,mask.width,mask.height);
    if(selection.points){ctx.globalCompositeOperation='destination-out';pathInRegion(ctx,selection,out.width/bounds.w);ctx.fill();ctx.globalCompositeOperation='source-over';}
    else{ctx.clearRect(0,0,mask.width,mask.height);}
    return{canvas:out,mask,scale};
  }
  function pathInRegion(ctx,selection,scale){
    ctx.beginPath();
    if(!selection.points){ctx.rect(0,0,selection.bounds.w*scale,selection.bounds.h*scale);return;}
    selection.points.forEach((p,i)=>{const x=(p.x-selection.bounds.x)*scale,y=(p.y-selection.bounds.y)*scale;if(i)ctx.lineTo(x,y);else ctx.moveTo(x,y);});ctx.closePath();
  }
  function localStudy(base,kind){
    const c=document.createElement('canvas');c.width=base.width;c.height=base.height;
    const ctx=c.getContext('2d');ctx.drawImage(base,0,0);
    const pix=ctx.getImageData(0,0,c.width,c.height);const d=pix.data;
    for(let i=0;i<d.length;i+=4){
      const r=d[i],g=d[i+1],b=d[i+2],l=.29*r+.59*g+.12*b;
      if(kind==='salt'){
        const x=Math.max(0,Math.min(255,(l-118)*1.55+128));d[i]=Math.min(255,x*1.01+6);d[i+1]=Math.min(255,x*.94+10);d[i+2]=Math.min(255,x*.82+16);
      }else if(kind==='night'){
        const sh=Math.max(0,l-55);d[i]=Math.min(255,17+sh*.38);d[i+1]=Math.min(255,27+sh*.64);d[i+2]=Math.min(255,45+sh*.9);
      }else{
        d[i]=Math.min(255,38+l*.91);d[i+1]=Math.min(255,14+l*.77);d[i+2]=Math.min(255,10+l*.49);
      }
    }
    ctx.putImageData(pix,0,0);return c;
  }
  function composedPreview(region,proposal){
    const out=document.createElement('canvas');out.width=region.canvas.width;out.height=region.canvas.height;
    const ctx=out.getContext('2d');ctx.drawImage(region.canvas,0,0);
    ctx.save();pathInRegion(ctx,region.selection,region.scale);ctx.clip();ctx.drawImage(proposal,0,0,out.width,out.height);ctx.restore();return out;
  }
  function updateSplit(){const v=+$('lab-split').value;$('lab-preview-after').style.clipPath=`inset(0 0 0 ${v}%)`;$('lab-compare-line').style.left=`${v}%`;$('lab-split-value').textContent=(100-v)+'%';}
  function selectFuture(n){
    if(!currentFutures||futuresBusy)return;
    selectedBranch=n;
    [...$('lab-branches').querySelectorAll('button')].forEach((b,i)=>b.setAttribute('aria-pressed',String(i===n)));
    $('lab-preview-after').src=currentFutures.branches[n].preview;
    const b=currentFutures.branches[n];feedback((b.source==='api'?'Model result':'Local study')+' selected. The source layers are unchanged.');
  }
  function renderFutureCards(){
    const container=$('lab-branches');container.textContent='';
    currentFutures.branches.forEach((branch,i)=>{
      const b=document.createElement('button');b.className='lab-branch';b.type='button';b.setAttribute('aria-pressed',String(selectedBranch===i));
      const img=document.createElement('img');img.src=branch.preview;img.alt=BRANCHES[i].label+' artwork study';
      const title=document.createElement('strong');title.textContent=BRANCHES[i].label;
      const sub=document.createElement('span');sub.textContent=branch.source==='api'?'API candidate':'Local study';
      b.append(img,title,sub);b.addEventListener('click',()=>selectFuture(i));container.append(b);
    });
  }
  function openFutures(prompt=''){
    if(futuresBusy)return;
    try{
      const selection=selectedRegion();const rendered=captureRegion(selection);const branches=BRANCHES.map(b=>{
        const canvas=localStudy(rendered.canvas,b.id);
        return{source:'local',canvas,preview:composedPreview({...rendered,selection},canvas).toDataURL('image/png')};
      });
      currentFutures={...rendered,selection,branches,original:rendered.canvas.toDataURL('image/png')};selectedBranch=0;
      if(prompt)$('lab-instruction').value=prompt;
      $('lab-preview-before').src=currentFutures.original;
      renderFutureCards();selectFuture(0);$('lab-split').value='50';updateSplit();showDialog('lab-modal');
      feedback('Three local studies ready. Nothing has changed on your canvas.');
      if(rendered.scale<1)feedback('Preview downsampled to protect tablet memory. Accepted layer will fit the original selection.');
    }catch(e){console.error(e);safeToast('Could not prepare futures: '+e.message);}
  }
  function acceptFuture(){
    if(!currentFutures||futuresBusy)return;
    const future=currentFutures.branches[selectedBranch];
    try{
      const s=currentFutures.selection,b=s.bounds;
      const layer=createLayer((future.source==='api'?'Image future: ':'Study: ')+BRANCHES[selectedBranch].label,true);
      layer.ctx.save();
      // Source clip is in world coordinates. The layer pixels outside this region remain transparent.
      if(s.points){layer.ctx.beginPath();s.points.forEach((p,i)=>{const q=worldToLocal(p.x,p.y);if(i)layer.ctx.lineTo(q.x,q.y);else layer.ctx.moveTo(q.x,q.y);});layer.ctx.closePath();layer.ctx.clip();}
      const pos=worldToLocal(b.x,b.y);
      layer.ctx.drawImage(future.canvas,pos.x,pos.y,b.w,b.h);
      layer.ctx.restore();pushHistory();renderLayerUI();clearSelection();hideDialog('lab-modal');currentFutures=null;
      safeToast('Accepted as an editable layer. Original layers preserved.');
    }catch(e){console.error(e);feedback('Could not accept candidate: '+e.message);}
  }
  async function refreshBackend(){try{
    const res=await fetch('/api/health',{cache:'no-store'});const data=await res.json();
    backendReady=!!(res.ok&&data.provider==='openai'&&data.can_generate===true);
    $('lab-generate').disabled=!backendReady;$('lab-connection').textContent=backendReady?'OpenAI ready · paid calls opt-in':'Local studies only · API spending disabled';
  }catch(_){backendReady=false;$('lab-generate').disabled=true;$('lab-connection').textContent='Local studies only · no API server';}}
  async function generateFuture(){
    if(!backendReady||!currentFutures||futuresBusy)return;
    const instruction=$('lab-instruction').value.trim();
    if(!instruction){feedback('Describe what should change before making a model call.');return;}
    const model=$('lab-model').value;
    if(!confirm(`Send this cropped artwork and mask to OpenAI using ${model}? This request can incur API charges. No call will occur unless you confirm.`))return;
    futuresBusy=true;$('lab-modal').setAttribute('aria-busy','true');$('lab-generate').disabled=true;$('lab-accept').disabled=true;
    feedback('Requesting one image candidate. The original is untouched.');
    try{
      const c=currentFutures,req={model,prompt:instruction+'; visual direction: '+BRANCHES[selectedBranch].suffix,image:c.canvas.toDataURL('image/png'),mask:c.selection.points?c.mask.toDataURL('image/png'):null};
      const resp=await fetch('/api/edits',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(req)});
      const payload=await resp.json();if(!resp.ok||typeof payload.image!=='string')throw new Error(payload.error||'Image edit request failed.');
      const edited=await decodePNG(payload.image);
      const candidate=document.createElement('canvas');candidate.width=c.canvas.width;candidate.height=c.canvas.height;candidate.getContext('2d').drawImage(edited,0,0,candidate.width,candidate.height);
      c.branches[selectedBranch]={source:'api',canvas:candidate,preview:composedPreview({...c,selection:c.selection},candidate).toDataURL('image/png')};
      renderFutureCards();selectFuture(selectedBranch);
      feedback('Model candidate received. Compare and accept, or discard.');
    }catch(e){console.error(e);feedback('Generation did not complete: '+e.message);}finally{futuresBusy=false;$('lab-modal').setAttribute('aria-busy','false');$('lab-accept').disabled=false;await refreshBackend();}
  }
  function interceptUndoButton(id,fn){const b=$(id);b.addEventListener('click',e=>{e.stopImmediatePropagation();e.preventDefault();fn();},true);}
  function bindLab(){
    bootComplete=true;
    interceptUndoButton('btn-undo',undo);interceptUndoButton('btn-redo',redo);
    $('lab-project-open').addEventListener('click',()=>showDialog('lab-project-modal'));
    $('lab-project-close').addEventListener('click',()=>hideDialog('lab-project-modal'));
    $('lab-save-project').addEventListener('click',downloadProject);
    $('lab-load-project').addEventListener('click',()=>$('lab-project-file').click());
    $('lab-project-file').addEventListener('change',e=>{void importProject(e.target.files?.[0]);e.target.value='';});
    $('lab-recover-project').addEventListener('click',()=>{void recoverDraft();});
    $('lab-new-project').addEventListener('click',createBlank);
    $('lab-futures-open').addEventListener('click',()=>openFutures($('input-ai-prompt').value.trim()));
    $('lab-close').addEventListener('click',()=>hideDialog('lab-modal'));
    $('lab-discard').addEventListener('click',()=>{hideDialog('lab-modal');currentFutures=null;feedback('All candidate changes discarded.');});
    $('lab-accept').addEventListener('click',acceptFuture);
    $('lab-split').addEventListener('input',updateSplit);
    $('lab-generate').addEventListener('click',()=>{void generateFuture();});
    document.addEventListener('keydown',e=>{
      const activeDialog=!$('lab-modal').classList.contains('lab-hidden')?$('lab-modal'):(!$('lab-project-modal').classList.contains('lab-hidden')?$('lab-project-modal'):null);
      if(e.key==='Tab'&&activeDialog){
        const focusables=[...activeDialog.querySelectorAll('button:not([disabled]),input:not([disabled]),select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])')].filter(el=>el.offsetParent!==null);
        if(focusables.length){const first=focusables[0],last=focusables[focusables.length-1];
          if(e.shiftKey&&document.activeElement===first){e.preventDefault();last.focus();}
          else if(!e.shiftKey&&document.activeElement===last){e.preventDefault();first.focus();}
        }
      }
      if(e.key==='Escape'){if(!$('lab-modal').classList.contains('lab-hidden'))hideDialog('lab-modal');else if(!$('lab-project-modal').classList.contains('lab-hidden'))hideDialog('lab-project-modal');}
      if((e.ctrlKey||e.metaKey)&&e.shiftKey&&e.key.toLowerCase()==='s'){e.preventDefault();downloadProject();}
    });
    // Original layer UI handlers run first. Record full-document changes after their mutation.
    $('layer-list').addEventListener('click',e=>{if(e.target.closest('[data-move-up],[data-move-down],[data-toggle-vis],[data-del-layer]'))queueMicrotask(pushDocumentHistory);});
    $('select-blend-mode').addEventListener('change',()=>queueMicrotask(pushDocumentHistory));
    $('slider-layer-opacity').addEventListener('change',()=>queueMicrotask(pushDocumentHistory));
    $('btn-alpha-lock').addEventListener('click',()=>queueMicrotask(pushDocumentHistory));
    document.querySelectorAll('.paper-btn').forEach(b=>b.addEventListener('click',()=>queueMicrotask(pushDocumentHistory)));
    // The legacy keyboard handler overrides native text undo. Reassert browser text editing with capture.
    window.addEventListener('keydown',e=>{
      if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='z'&&e.target.closest?.('input,textarea,[contenteditable=true]'))e.stopImmediatePropagation();
    },true);
    void checkRecovery();void refreshBackend();
    window.addEventListener('pagehide',()=>{clearTimeout(pendingAutosave);});
  }
  // The source initializes on DOMContentLoaded. Bind afterwards in registration order.
  if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',bindLab);
  else bindLab();
  // Explicitly expose a narrow test seam without changing the normal user experience.
  window.__studioLabTest={captureDocument,validateProject,restoreDocument,selectedRegion,captureRegion,get history(){return{index:H.index,length:H.stack.length};}};
})();
