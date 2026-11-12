// Local interaction model for the design prototype. No network or persistent storage.
'use strict';
const app = document.getElementById('app');
const page = document.getElementById('page');
const nav = document.getElementById('nav');
const dialog = document.getElementById('dialog');
const state = {
  enabled:true, selected:true, mic:true, active:'base', installed:new Set(['base']),
  transfers:{}, speech:'Engels', mode:'Standaard', side:'Rechts', wide:false, height:'Normaal',
  keyboardFirst:true, autoStart:true, remember:true, continuous:false, live:true, autoStop:'Uit',
  ruleTab:false, rulesOn:false, matchCase:true, query:'', testDraft:'', nextId:10,
  terms:[{id:1,text:'OpStudio'},{id:2,text:'Lowkey'}],
  rules:[{id:3,spoken:'project delta',written:'Project Delta',enabled:true}],
};
const models = [
  {id:'base',name:'Whisper base',size:160,multi:true,recommended:true},
  {id:'small',name:'Whisper small',size:375,multi:true},
  {id:'tinyEn',name:'Whisper tiny.en',size:103,multi:false},
];
const parent = {keyboard:'settings',position:'keyboard',typing:'keyboard',customize:'keyboard',physical:'keyboard',languages:'settings',layout:'languages',clipboard:'settings',appearance:'settings',updates:'settings',about:'settings',speechLanguage:'voice',dictation:'voice',storage:'voice',setupEnable:'setup',setupChoose:'setupEnable',setupReady:'home',voiceSetup:'home'};
const owner = {home:'home',setup:'home',setupEnable:'home',setupChoose:'home',setupReady:'home',voiceSetup:'home',voice:'voice',speechLanguage:'voice',dictation:'voice',storage:'voice',words:'words'};
const titles = {home:'Home',settings:'Instellingen',keyboard:'Toetsenbord',voice:'Stem',words:'Woordenboek',languages:'Talen en indelingen',layout:'Nederlandse indeling',position:'Formaat en positie',typing:'Typen',customize:'Aanpassen',physical:'Fysiek toetsenbord',speechLanguage:'Dicteertaal',dictation:'Dicteergedrag',storage:'Opslag en prestaties',appearance:'Weergave',updates:'Updates',about:'Over Lowkey',clipboard:'Klembord en afbeeldingen',setup:'Welkom',setupEnable:'Activeer Lowkey',setupChoose:'Kies Lowkey',setupReady:'Klaar om te typen',voiceSetup:'Voeg spraak toe'};
let route='home', opener=null, dialogSave=null, dialogRestore=null;
const scrollPositions = new Map();
const timers = new Map();
const esc = value => String(value).replace(/[&<>"']/g, c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const icon = name => `<span class="icon">${ICONS[name]||''}</span>`;
const button = (label,action,style='secondary',extra='') => `<button type="button" class="${style}" data-action="${action}" ${extra}>${label}</button>`;
const row = (label,description,to,extra='') => `<button type="button" class="row" data-go="${to}" ${extra}><span class="row-text"><strong>${label}</strong><small>${description}</small></span>${icon('ChevronRight')}</button>`;
const section = (title,body) => `<section class="section"><h2>${title}</h2>${body}</section>`;
const check = (key,title,detail='',disabled=false) => `<label class="preference ${disabled?'disabled':''}"><input type="checkbox" data-setting="${key}" ${state[key]?'checked':''} ${disabled?'disabled':''}><span>${title}${detail?`<small>${detail}</small>`:''}</span></label>`;
const radios = (name,values,current) => values.map(([value,label,detail])=>`<label class="preference"><input type="radio" name="${name}" value="${esc(value)}" ${current===value?'checked':''}><span>${label}${detail?`<small>${detail}</small>`:''}</span></label>`).join('');
const testAction = (label='Toetsenbord proberen') => button(label,'test','secondary wide');
function showNotice(text,undo,focusSelector){
 const node=document.getElementById('notice');node.replaceChildren();
 const message=document.createElement('span');message.textContent=text;node.append(message);
 if(undo){const b=document.createElement('button');b.textContent='Ongedaan maken';b.onclick=()=>{undo();node.hidden=true;render(false);page.querySelector(focusSelector||'[data-action=add]')?.focus();};node.append(document.createElement('br'),b);}
 const close=document.createElement('button');close.textContent='Sluiten';close.onclick=()=>{node.hidden=true;};node.append(close);node.hidden=false;
}
function navigate(to){
 window.LowkeyStartup?.finish();
 scrollPositions.set(route,page.scrollTop);route=to;
 document.getElementById('notice').hidden=true;
 render(true);page.scrollTop=scrollPositions.get(to)||0;
}
function render(focus=false){
 const scrolling=page.scrollTop;
 const isSetup=route.startsWith('setup');
 document.querySelector('.brand').hidden=!(route==='home'||isSetup);
 app.classList.toggle('no-brand',document.querySelector('.brand').hidden);
 nav.hidden=isSetup;
 nav.innerHTML=[['home','Home','Home'],['voice','Stem','Mic'],['words','Woor<wbr>den<wbr>boek','Book'],['settings','Instel<wbr>lin<wbr>gen','Settings']].map(([to,label,symbol])=>`<button type="button" data-go="${to}" aria-label="${titles[to]}" ${to===(owner[route]||'settings')?'aria-current="page"':''}>${icon(symbol)}<span>${label}</span></button>`).join('');
 const backTarget=route==='setupChoose'&&state.enabled?'setup':parent[route];
 page.innerHTML=(backTarget&&route!=='setupReady'?`<button type="button" class="back" data-go="${backTarget}">${icon('Back')}${titles[backTarget]}</button>`:'')+view();
 if(focus)page.focus();else page.scrollTop=scrolling;
}
function view(){
 switch(route){
 case 'home': return homeView();
 case 'setup': return `<div class="eyebrow">Welkom bij Lowkey</div><h1>Jouw woorden.<br>Jouw toetsenbord.</h1><p>Begin met typen. Voeg spraak toe wanneer je wilt.</p><ol class="steps"><li>Activeer Lowkey in Android</li><li>Kies Lowkey als toetsenbord</li></ol><div class="action-stack">${button('Stel toetsenbord in','setupStart','primary wide')}${button('Later instellen','skipSetup','textbtn')}</div><p class="hint">Android toont een waarschuwing voor toetsenbordapps. Lowkey verwerkt tekst op je toestel.</p>`;
 case 'setupEnable': return `<div class="eyebrow">Toetsenbord · stap 1 van 2</div><h1>Activeer Lowkey</h1><p>Zet Lowkey aan als beschikbaar toetsenbord in de Android-instellingen.</p><div class="action-stack">${button('Open instellingen','enable','primary wide')}${button('Later instellen','skipSetup','textbtn')}</div>`;
 case 'setupChoose': return `<div class="eyebrow">Toetsenbord · stap 2 van 2</div><h1>Kies Lowkey</h1><p>Lowkey is beschikbaar. Kies het nu als je huidige toetsenbord.</p><div class="action-stack">${button('Kies toetsenbord','select','primary wide')}${button('Later kiezen','skipSetup','textbtn')}</div>`;
 case 'setupReady': return `<div class="status">Klaar om te typen</div><h1>Probeer het even</h1><p>Spraak is optioneel. Je kunt nu al typen.</p><div class="action-stack">${testAction()}</div>${section('Ook met je stem','<p>Voeg een lokaal spraakmodel toe om te dicteren.</p><div class="action-stack">'+button('Voeg spraak toe','voiceSetup','secondary wide')+button('Verder zonder spraak','skipSetup','textbtn')+'</div>')}`;
 case 'voiceSetup': return `<h1>Voeg spraak toe</h1><p>Je stem wordt op je toestel verwerkt.</p>${section('1. Microfoon',state.mic?'<div class="status">Microfoon toegestaan in dit voorbeeld</div>':`<p>Je toestemming is nodig om te dicteren.</p><div class="actions">${button('Sta microfoon toe','grantMic')}</div>`)}${section('2. Taal en model',`<p>Whisper base · meertalig · ongeveer 160 MB</p><label class="field-label">Dicteertaal<select class="field" data-setting="speech">${['Engels','Nederlands','Volgt toetsenbord'].map(x=>`<option ${state.speech===x?'selected':''}>${x}</option>`).join('')}</select></label><p class="hint">Download bij voorkeur via wifi. Je toetsenbordtaal verandert hierdoor niet.</p><div class="action-stack">${button('Download Whisper base','setupDownload','primary wide')}${button('Ander model kiezen','chooseModel','textbtn')}${button('Later instellen','skipSetup','textbtn')}</div>`)}`;
 case 'settings': return `<h1>Instellingen</h1><p>Maak Lowkey van jou.</p>${section('Toetsenbord en invoer',row('Toetsenbord','Formaat, positie en typen','keyboard')+row('Talen en indelingen','Nederlands · QWERTY','languages')+row('Spraak en dicteren','Openen in Stem','voice')+row('Klembord en afbeeldingen','Screenshots en beeldbewerking','clipboard'))}${section('De app',row('Weergave','Systeem, licht en donker','appearance')+row('Updates','Controleren op een nieuwe versie','updates')+row('Over Lowkey','Privacy, licenties en hulp','about'))}`;
 case 'keyboard': return `<h1>Toetsenbord</h1>${row('Formaat en positie',`${state.mode} · ${state.height.toLowerCase()}`,'position')}${row('Typen','Autocorrect, suggesties en hoofdletters','typing')}${row('Aanpassen','Trilling, cijferrij en snelmenu','customize')}${row('Fysiek toetsenbord','Toetsenbord op het scherm tonen','physical')}`;
 case 'position': return `<h1>Formaat en positie</h1><p>Kies hoe het toetsenbord op je scherm staat.</p>${section('Hoogte',radios('height',[['Compact','Compact'],['Normaal','Normaal'],['Hoog','Hoog']],state.height))}${section('Op telefoonbreedte',radios('mode',[['Standaard','Standaard','Over de schermbreedte'],['Eenhandig','Eenhandig','Links of rechts'],['Zwevend','Zwevend','Vrij op het scherm']],state.mode))}${state.mode==='Eenhandig'?section('Zijde',radios('side',[['Links','Links'],['Rechts','Rechts']],state.side)):''}${section('Op een breed scherm',radios('wide',[['same','Zelfde als telefoon'],['split','Splitsen','Alleen als je hiervoor kiest']],state.wide?'split':'same'))}<p class="hint">De breed-schermkeuze is een voorstel. Handmatige split en automatisch splitsen worden afgestemd met de keyboard-track.</p><div class="action-stack">${testAction('Aanpassen op het toetsenbord')}${button('Herstel standaardpositie','resetPosition','textbtn')}</div>`;
 case 'languages': return `<h1>Talen en indelingen</h1><p>Toetsenbordtalen bepalen letters, suggesties en autocorrect.</p>${section('Op dit toestel',row('Nederlands','QWERTY · huidige indeling','layout'))}<div class="actions">${button('Taal toevoegen','languageInfo','textbtn')}</div>${section('Spraak',row('Dicteertaal',`${state.speech} · openen in Stem`,'speechLanguage'))}<div class="action-stack">${testAction()}</div>`;
 case 'layout': return `<h1>Nederlandse indeling</h1><p>QWERTY is actief. Bewaar minstens één indeling.</p>${section('Indelingen',radios('layout',[['QWERTY','QWERTY'],['AZERTY','AZERTY'],['QWERTZ','QWERTZ']],'QWERTY'))}<div class="action-stack">${testAction()}</div>`;
 case 'voice': return voiceView();
 case 'speechLanguage': return `<h1>Dicteertaal</h1>${state.active&&models.find(m=>m.id===state.active)?.multi===false?'<p>Engels. Dit model ondersteunt alleen Engels.</p>':`<p>Voor het actieve of volgende meertalige model. Engels is de standaard bij eerste gebruik.</p>${section('Taal',radios('speech',[['Engels','Engels'],['Nederlands','Nederlands'],['Volgt toetsenbord','Volgt toetsenbord','Nu: Nederlands']],state.speech))}`}<div class="action-stack">${testAction('Probeer dicteren')}</div>`;
 case 'dictation': return dictationView();
 case 'words': return wordsView();
 case 'storage': return `<h1>Opslag en prestaties</h1><p>Een gedownload bestand is niet hetzelfde als een geladen spraakmodel.</p>${section('Op dit toestel',`<p>${models.filter(m=>state.installed.has(m.id)).reduce((n,m)=>n+m.size,0)} MB aan geïnstalleerde modellen in dit voorbeeld.</p>`)}${section('Werkgeheugen','<p>De echte app toont hier geladen modelgeheugen en hoelang het model geladen blijft.</p>')}`;
 case 'appearance': return `<h1>Weergave</h1><p>Systeem, licht en donker blijven beschikbaar.</p><p class="hint">Bekijk beide varianten met Donker boven het voorbeeld. Donker is een Lowkey-vertaling van dezelfde opbouw.</p>`;
 default: return otherView();
 }
}
function homeView(){
 const ready=state.enabled&&state.selected;
 const title=!state.enabled?'Stel Lowkey in':!state.selected?'Kies Lowkey':'Klaar om te typen';
 const summary=!state.enabled?'Begin met je toetsenbord. Spraak kun je later toevoegen.':!state.selected?'Lowkey is beschikbaar. Kies het om ermee te typen.':'Typ op jouw manier. Je stem blijft op je toestel.';
 const status=!state.enabled?'Nog niet geactiveerd':!state.selected?'Niet het huidige toetsenbord':'Lowkey is actief';
 const m=models.find(m=>m.id===state.active);
 return `<div class="eyebrow">Jouw toetsenbord</div><h1>${title}</h1><p>${summary}</p><div class="status ${ready?'':'neutral'}">${status}</div>${ready?button('Toetsenbord proberen','test','primary wide'):button(state.enabled?'Kies toetsenbord':'Stel toetsenbord in',state.enabled?'selectFromHome':'setupStart','primary wide')}${section('Op jouw manier',row('Taal en indeling','Nederlands · QWERTY · in Instellingen','languages')+row('Spraak',m?`${m.name} · ${m.multi?state.speech:'Engels'} · in Stem`:'Nog niet ingesteld · optioneel','voice'))}<p class="hint">Spraak wordt lokaal verwerkt. Voor modellen, talen en updates is internet nodig.</p>`;
}
function modelRow(model){
 const active=state.active===model.id, installed=state.installed.has(model.id), transfer=state.transfers[model.id];
 let control;
 if(transfer?.status==='downloading')control=`<div class="progress"><progress max="100" value="${transfer.progress}" aria-label="Downloadvoortgang ${model.name}"></progress><span>${transfer.progress}%</span></div><div class="actions">${button('Annuleer','cancelDownload','textbtn',`data-id="${model.id}"`)}</div>`;
 else if(transfer?.status==='error')control=`<p class="error-text" role="status">Download mislukt. Probeer het opnieuw wanneer je verbinding hebt.</p><div class="actions">${button('Opnieuw proberen','download','secondary',`data-id="${model.id}"`)}</div>`;
 else if(active)control='<div class="status">Actief voor dicteren</div>';
 else if(installed)control=`<div class="actions">${button('Gebruik','activate','secondary',`data-id="${model.id}"`)}</div>`;
 else control=`<div class="actions">${button(`Download · ${model.size} MB`,'download','secondary',`data-id="${model.id}"`)}</div>`;
 return `<article class="model" data-model="${model.id}">${model.recommended?'<div class="tag">Aanbevolen startmodel</div>':''}<h3>${model.name}</h3><p class="meta">${model.multi?'Meertalig':'Alleen Engels'} · ${model.size} MB${installed?' · op dit toestel':''}</p>${control}<button type="button" class="textbtn" data-action="modelDetails" data-id="${model.id}">Details en beheer ${icon('ChevronRight')}</button></article>`;
}
function voiceView(){
 const active=models.find(m=>m.id===state.active);
 const intro=active?`<div class="status">${state.mic?'Klaar om te dicteren':'Microfoontoestemming nodig'}</div>${section('Actief spraakmodel',`${active.recommended?'<div class="tag">Aanbevolen startmodel</div>':''}<h3>${active.name}</h3><p class="meta">${active.multi?state.speech:'Engels'} · tekst verschijnt na Stop</p><div class="action-stack">${state.mic?testAction('Probeer dicteren'):button('Sta microfoon toe','grantMic','primary wide')}</div><button type="button" class="textbtn" data-action="modelDetails" data-id="${active.id}">Modeldetails en beheer ${icon('ChevronRight')}</button>`)}`:`<div class="status neutral">Spraak nog niet ingesteld</div><p>Typen werkt zonder spraakmodel. Kies hieronder een model als je ook wilt dicteren.</p>${!state.mic?`<div class="actions">${button('Sta microfoon toe','grantMic')}</div>`:''}`;
 const modelSection=(label,predicate)=>{const list=models.filter(m=>predicate(m)&&m.id!==state.active);return list.length?section(label,list.map(modelRow).join('')):'';};
 return `<h1>Stem</h1><p>Dicteren, helemaal op je toestel.</p>${intro}${row('Dicteertaal',active&&!active.multi?'Engels · vast voor dit model':state.speech,'speechLanguage')}${row('Dicteergedrag',state.keyboardFirst?'Begin vanuit de toetsen':'Aparte spraakmodus','dictation')}${modelSection(active?'Andere meertalige modellen':'Meertalige modellen',m=>m.multi)}${modelSection('Alleen Engels',m=>!m.multi)}${row('Opslag en prestaties','Opslag en geladen geheugen','storage')}`;
}
function dictationView(){
 const dependent=!state.keyboardFirst;
 return `<h1>Dicteergedrag</h1>${section('Hoe begin je?',radios('entry',[['keys','Vanuit de toetsen','Tik op de microfoon; daarna terug naar toetsen.'],['voice','Aparte spraakmodus','Meer controle over starten en doorgaan.']],state.keyboardFirst?'keys':'voice'))}${section('Aparte spraakmodus',(!dependent?'<p class="hint">Deze voorkeuren gelden alleen bij Aparte spraakmodus. Je keuzes blijven bewaard.</p>':'')+check('autoStart','Automatisch starten','',!dependent)+check('remember','Laatste modus onthouden','',!dependent)+check('continuous','Continu dicteren','',!dependent))}${section('Tekst en stoppen',check('live','Live tekst tonen')+`<label class="field-label">Stoppen na stilte<select class="field" data-setting="autoStop">${['Uit','Kort','Lang'].map(x=>`<option ${state.autoStop===x?'selected':''}>${x}</option>`).join('')}</select></label>`)}${row('Spraakregels','Openen in Woordenboek → Spraakregels','words','data-dictionary-tab="rules"')}<div class="action-stack">${testAction('Probeer dicteren')}</div>`;
}
function filteredEntries(){
 const list=state.ruleTab?state.rules:state.terms;
 const q=state.query.trim().toLocaleLowerCase();
 return list.filter(item=>(state.ruleTab?item.spoken+' '+item.written:item.text).toLocaleLowerCase().includes(q));
}
function entryCount(){
 const count=filteredEntries().length;
 return `${count} ${state.ruleTab?(count===1?'regel':'regels'):(count===1?'woord':'woorden')}`;
}
function entriesHtml(){
 const items=filteredEntries();
 if(!items.length)return `<div class="empty"><h3>${state.query?'Geen resultaten':state.ruleTab?'Nog geen spraakregels':'Nog geen woorden'}</h3><p>${state.query?'Probeer een andere zoekterm.':state.ruleTab?'Voeg toe wat je anders wilt laten schrijven.':'Bewaar woorden met hun exacte spelling.'}</p></div>`;
 return items.map(item=>`<div class="term ${state.ruleTab&&(!state.rulesOn||!item.enabled)?'inactive':''}"><div class="term-body"><strong>${esc(state.ruleTab?item.written:item.text)}</strong><small>${state.ruleTab?`${esc(item.spoken)} → ${esc(item.written)} · ${state.rulesOn&&item.enabled?'Aan':'Uit'}`:'Exacte spelling'}</small></div>${button(icon('Edit'),'edit','iconbtn',`data-id="${item.id}" aria-label="Bewerk ${esc(state.ruleTab?item.written:item.text)}"`)}</div>`).join('');
}
function wordsView(){
 return `<h1>Woordenboek</h1><div class="tabs" role="tablist" aria-label="Type woordenlijst"><button type="button" role="tab" id="termsTab" data-action="termsTab" aria-selected="${!state.ruleTab}">Mijn woorden</button><button type="button" role="tab" id="rulesTab" data-action="rulesTab" aria-selected="${state.ruleTab}">Spraak<wbr>regels</button></div><p class="hint">${state.ruleTab?'Vervang een herkende uitdrukking na Stop. Dit traint het model niet.':'Bewaar spelling zoals jij ze schrijft.'}</p><label class="field-label">${state.ruleTab?'Zoek spraakregels':'Zoek woorden'}<input class="field" id="search" type="search" value="${esc(state.query)}" placeholder="${state.ruleTab?'Zoek een uitdrukking':'Zoek een woord'}"></label><div class="search-actions"><span class="result-count" id="count" role="status">${entryCount()}</span>${button(state.ruleTab?'Regel toevoegen':'Woord toevoegen','add','primary')}</div><div id="entries">${entriesHtml()}</div>${section(state.ruleTab?'Regels toepassen':'Spelling bij dicteren',state.ruleTab?check('rulesOn','Spraakregels toepassen',state.rulesOn?'Aan in dit voorbeeld':'Uit. Bewaarde regels worden niet toegepast.'):check('matchCase','Spelling herstellen bij dicteren','Gebruik de exacte spelling van je bewaarde woorden.'))}`;
}
function otherView(){
 const text={typing:'Autocorrect, suggesties en hoofdletters houden hier één plek.',customize:'Trilling, cijferrij en snelmenu. Nieuwe keyboardopties volgen de aparte keyboard-track.',physical:'Laat het toetsenbord op het scherm staan bij gebruik van een fysiek toetsenbord.',clipboard:'Recente screenshots met toestemming; loephoeken onder Afbeeldingseditor.',updates:'Automatisch controleren en handmatig controleren blijven bereikbaar. Installeren vraagt altijd je bevestiging.',about:'Privacy, licenties en problemen oplossen blijven hier bereikbaar.'};
 return `<h1>${titles[route]}</h1><p>${text[route]||'Dit onderdeel wordt later verder uitgewerkt.'}</p>${['typing','customize','physical'].includes(route)?`<div class="action-stack">${testAction()}</div>`:''}${route==='about'?row('Setup controleren','Bekijk de actuele toetsenbordstatus','home'):''}`;
}
function openDialog(title,html,buttons,save){
 window.LowkeyStartup?.finish();opener=document.activeElement;
 dialog.classList.toggle('dark',app.classList.contains('dark'));
 dialog.classList.toggle('large-type',app.classList.contains('large-type'));
 document.getElementById('dialogTitle').textContent=title;
 document.getElementById('dialogBody').innerHTML=html;
 document.getElementById('dialogActions').innerHTML=buttons;
 dialogSave=save||null;dialogRestore=null;dialog.showModal();
}
const closeButton=()=>button('Sluiten','closeDialog','secondary');
function openEditor(id){
 const rules=state.ruleTab, list=rules?state.rules:state.terms, item=list.find(x=>x.id===id);
 const fields=rules?`<label class="field-label">Gesproken uitdrukking<input class="field" name="spoken" required maxlength="120" value="${esc(item?.spoken||'')}"></label><label class="field-label">Schrijf als<input class="field" name="written" required maxlength="120" value="${esc(item?.written||'')}"></label>${item?`<label class="preference"><input type="checkbox" name="enabled" ${item.enabled?'checked':''}><span>Deze regel inschakelen</span></label>`:''}`:`<label class="field-label">Exacte spelling<input class="field" name="text" required maxlength="48" value="${esc(item?.text||'')}"></label>`;
 openDialog(item?'Bewerken':rules?'Spraakregel toevoegen':'Woord toevoegen',fields+'<p class="error-text" id="formError" role="status"></p>',`<button class="primary" type="submit">Bewaren</button>${item?button('Verwijderen','delete','danger',`data-id="${id}"`):''}${button('Annuleren','closeDialog','secondary')}`,form=>{
  const value=name=>form.elements.namedItem(name).value.trim();
  if((rules&&(!value('spoken')||!value('written')))||(!rules&&!value('text'))){document.getElementById('formError').textContent='Vul de tekst in.';return;}
  if(!rules&&list.some(x=>x.id!==id&&x.text.toLocaleLowerCase()===value('text').toLocaleLowerCase())){document.getElementById('formError').textContent='Dit woord is al bewaard.';return;}
  const entry=rules?{id:id||state.nextId++,spoken:value('spoken'),written:value('written'),enabled:item?form.elements.namedItem('enabled').checked:true}:{id:id||state.nextId++,text:value('text')};
  if(item)list.splice(list.indexOf(item),1,entry);else list.push(entry);
  dialogRestore=`[data-action=edit][data-id="${entry.id}"]`;
  dialog.close();render(false);showNotice(rules&&!state.rulesOn?'Regel bewaard. Regels toepassen staat nog uit.':'Bewaard in dit voorbeeld.');
 });
}
function clearTimer(id){if(timers.has(id)){clearInterval(timers.get(id));timers.delete(id);}}
function updateModelRow(id){
 const old=page.querySelector(`[data-model="${id}"]`);
 if(!old)return;
 const focused=old.contains(document.activeElement);
 const action=document.activeElement?.dataset?.action;
 const holder=document.createElement('div');holder.innerHTML=modelRow(models.find(m=>m.id===id));
 const replacement=holder.firstElementChild;old.replaceWith(replacement);
 if(focused)(replacement.querySelector(`[data-action="${action}"]`)||replacement.querySelector('button'))?.focus();
}
function advanceDownload(id){
 const transfer=state.transfers[id];
 if(!transfer||transfer.status!=='downloading'){clearTimer(id);return;}
 transfer.progress=Math.min(100,transfer.progress+20);
 if(transfer.progress===100){
  clearTimer(id);delete state.transfers[id];state.installed.add(id);updateModelRow(id);
  showNotice(`${models.find(m=>m.id===id).name} staat op je toestel. Kies Gebruik om het te activeren.`);
  return;
 }
 const model=page.querySelector(`[data-model="${id}"]`);
 const progress=model?.querySelector('progress');
 if(progress){progress.value=transfer.progress;progress.nextElementSibling.textContent=`${transfer.progress}%`;}
}
function startDownload(id,progress=20){
 clearTimer(id);state.transfers[id]={status:'downloading',progress};updateModelRow(id);
 timers.set(id,setInterval(()=>advanceDownload(id),800));
}
function applyScenario(name){
 for(const id of timers.keys())clearTimer(id);
 state.enabled=!['fresh','setup'].includes(name);state.selected=!['fresh','setup','enabled','recovery'].includes(name);
 state.active=['fresh','setup','enabled','noVoice'].includes(name)?null:'base';
 state.installed=new Set(state.active?['base']:[]);state.mic=!!state.active;state.transfers={};
 if(['downloading','downloadError','downloaded'].includes(name)){
  if(name==='downloaded')state.installed.add('small');else state.transfers.small={status:name==='downloading'?'downloading':'error',progress:40};
  navigate('voice');
  if(name==='downloading')startDownload('small',40);
  const model=page.querySelector('[data-model=small]');
  if(model)page.scrollTop+=model.getBoundingClientRect().top-page.getBoundingClientRect().top-80;
 }else navigate(name==='setup'?'setup':'home');
}
document.addEventListener('click',event=>{
 const target=event.target.closest('button');if(!target)return;
 if(target.dataset.go){if(target.dataset.dictionaryTab==='rules'){state.ruleTab=true;state.query='';}navigate(target.dataset.go);return;}
 const id=target.dataset.id, action=target.dataset.action;
 switch(action){
 case 'setupStart': state.enabled? navigate('setupChoose'):navigate('setupEnable');break;
 case 'enable':state.enabled=true;navigate('setupChoose');break;
 case 'select':state.enabled=true;state.selected=true;navigate('setupReady');break;
 case 'selectFromHome':state.selected=true;navigate('home');break;
 case 'skipSetup':navigate('home');break;
 case 'voiceSetup':navigate('voiceSetup');break;
 case 'grantMic':state.mic=true;render(false);showNotice('Microfoon toegestaan in dit voorbeeld.');break;
 case 'setupDownload':navigate('voice');startDownload('base');break;
 case 'chooseModel':navigate('voice');break;
 case 'download':startDownload(id);break;
 case 'cancelDownload':clearTimer(id);delete state.transfers[id];updateModelRow(id);showNotice('Download geannuleerd.');break;
 case 'activate':state.active=id;render(false);page.querySelector('[data-action=test]')?.focus();showNotice(`${models.find(m=>m.id===id).name} is nu actief.`);break;
 case 'modelDetails':{
  const m=models.find(x=>x.id===id);openDialog(m.name,`<p>${m.multi?'Meertalig':'Alleen Engels'} · ${m.size} MB · MIT</p><p>Lokale herkenning. In de app vind je hier de bron en bestandscontrole.</p>${state.active===id?'<p>Het actieve model kan niet worden verwijderd. Kies eerst een ander model.</p>':''}`,(state.installed.has(id)&&state.active!==id?button('Model verwijderen','deleteModel','danger',`data-id="${id}"`):'')+closeButton());break;
 }
 case 'deleteModel':state.installed.delete(id);dialog.close();render(false);showNotice('Model verwijderd uit dit voorbeeld.');break;
 case 'termsTab':case 'rulesTab':state.ruleTab=action==='rulesTab';state.query='';render(false);document.getElementById(action).focus();break;
 case 'add':case 'edit':target.focus();openEditor(action==='edit'?Number(id):null);break;
 case 'delete':{
  const list=state.ruleTab?state.rules:state.terms, index=list.findIndex(x=>x.id===Number(id));const item=list[index];
  if(!item)break;list.splice(index,1);dialogRestore='[data-action=add]';dialog.close();render(false);showNotice('Verwijderd uit dit voorbeeld.',()=>list.splice(Math.min(index,list.length),0,item),`[data-action=edit][data-id="${item.id}"]`);break;
 }
 case 'test':target.focus();openDialog('Toetsenbord proberen',`<p>In de app opent hier je echte toetsenbord.</p><label class="field-label">Testtekst<textarea class="field" id="testText">${esc(state.testDraft)}</textarea></label><p class="hint">Dit voorbeeld gebruikt alleen tekstinvoer.</p>`,closeButton());break;
 case 'closeDialog':dialog.close();break;
 case 'resetPosition':state.mode='Standaard';state.side='Rechts';state.wide=false;state.height='Normaal';render(false);showNotice('Standaardpositie hersteld in dit voorbeeld.');break;
 case 'languageInfo':openDialog('Taal toevoegen','<p>In de app kies en download je hier een toetsenbordtaal. Een taaldownload en een spraakmodel zijn verschillende bestanden.</p>',closeButton());break;
 }
});
document.addEventListener('change',event=>{
 const el=event.target;
 if(el.id==='scenario'){applyScenario(el.value);return;}
 if(el.id==='dark'){app.classList.toggle('dark',el.checked);return;}
 if(el.id==='largeText'){app.classList.toggle('large-type',el.checked);return;}
 if(el.id==='reduceMotion'){if(el.checked)window.LowkeyStartup?.finish();return;}
 if(el.dataset.setting){state[el.dataset.setting]=el.type==='checkbox'?el.checked:el.value;const key=el.dataset.setting;render(false);page.querySelector(`[data-setting="${key}"]`)?.focus();return;}
 if(el.type==='radio'){
  if(['mode','height','side','speech'].includes(el.name))state[el.name]=el.value;
  if(el.name==='wide')state.wide=el.value==='split';
  if(el.name==='entry')state.keyboardFirst=el.value==='keys';
  const selector=`input[name="${el.name}"][value="${el.value}"]`;render(false);page.querySelector(selector)?.focus();
 }
});
document.addEventListener('input',event=>{
 if(event.target.id==='testText')state.testDraft=event.target.value;
 if(event.target.id==='search'){
  state.query=event.target.value;document.getElementById('entries').innerHTML=entriesHtml();
  document.getElementById('count').textContent=entryCount();
 }
});
document.getElementById('dialogForm').addEventListener('submit',event=>{event.preventDefault();dialogSave?.(event.target);});
dialog.addEventListener('close',()=>{dialogSave=null;const target=dialogRestore&&page.querySelector(dialogRestore);if(target)target.focus();else if(opener?.isConnected)opener.focus();else (page.querySelector('[data-action=add]')||page).focus();dialogRestore=null;});
document.addEventListener('keydown',event=>{
 if(event.target.getAttribute('role')==='tab'&&['ArrowLeft','ArrowRight','Home','End'].includes(event.key)){
  event.preventDefault();state.ruleTab=event.key==='End'?true:event.key==='Home'?false:!state.ruleTab;state.query='';render(false);document.getElementById(state.ruleTab?'rulesTab':'termsTab').focus();
 }
});
document.getElementById('replay').addEventListener('click',()=>window.LowkeyStartup?.play(app,{force:true,reducedMotion:document.getElementById('reduceMotion').checked}));
// Synchronous first render and motion setup prevent a content flash before the reveal.
render();
window.LowkeyStartup?.play(app);
