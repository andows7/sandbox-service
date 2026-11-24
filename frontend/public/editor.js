'use strict';
// Small offline editor enhancement: lexical highlighting and local word/snippet completion.
(() => {
 const code=document.getElementById('code'), area=code.parentElement;
 const painted=document.createElement('pre');painted.id='highlight';painted.setAttribute('aria-hidden','true');area.append(painted);
 const menu=document.createElement('div');menu.id='completions';menu.hidden=true;menu.setAttribute('role','listbox');menu.setAttribute('aria-label','代码补全');area.append(menu);
 code.setAttribute('aria-controls','completions');code.setAttribute('aria-autocomplete','list');
 const words={C:['printf','scanf','puts','getchar','putchar','malloc','free','sizeof','include','stdio.h','stdlib.h','int','long','char','double','void','const','return','if','else','for','while','struct','typedef'],CPP:['cout','cin','endl','vector','string','iostream','algorithm','push_back','size','int','long','bool','return','if','else','for','while','class','public','using','namespace','std'],JAVA:['System.out.println','Scanner','String','Integer','Math','public','static','void','class','main','return','if','else','for','while','new','boolean','int','long','nextInt','nextLong'],PYTHON:['print','input','range','len','int','str','list','dict','enumerate','append','split','map','def','return','if','else','elif','for','while','True','False','None','import']};
 const snippets={C:{printf:'printf("%d\\n", value);',scanf:'scanf("%d", &value);'},CPP:{cout:'cout << value << "\\n";',cin:'cin >> value;'},JAVA:{'System.out.println':'System.out.println(value);'},PYTHON:{print:'print(value)',input:'input()'}};
 let options=[],selected=0,start=0;
 function hide(){menu.hidden=true;code.setAttribute('aria-expanded','false');}
 function render(){
   const source=code.value+'\n';painted.replaceChildren();
   const regex=/(\/\*[\s\S]*?(?:\*\/|$)|\/\/[^\n]*|#[^\n]*|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'|\b\d+(?:\.\d+)?\b|\b[A-Za-z_]\w*\b)/g;
   const lang=document.getElementById('language').value, known=new Set(words[lang]);let last=0;
   for(const m of source.matchAll(regex)){
     painted.append(document.createTextNode(source.slice(last,m.index)));
     const span=document.createElement('span'),v=m[0];span.textContent=v;
     span.className=/^\/\*|^\/\/|^#/.test(v)?'syntax-comment':/^['"]/.test(v)?'syntax-string':/^\d/.test(v)?'syntax-number':known.has(v)?'syntax-keyword':'';
     painted.append(span);last=m.index+v.length;
   }
   painted.append(document.createTextNode(source.slice(last)));painted.scrollTop=code.scrollTop;painted.scrollLeft=code.scrollLeft;
 }
 function accept(index){if(menu.hidden)return;const word=options[index],lang=document.getElementById('language').value;code.setRangeText(snippets[lang]?.[word]||word,start,code.selectionStart,'end');hide();code.dispatchEvent(new Event('input'));hide();code.focus();}
 function complete(force=false){
   const match=code.value.slice(0,code.selectionStart).match(/[A-Za-z_][\w.]*$/),prefix=match?.[0]||'';
   if(!force && prefix.length<2){hide();return;}
   start=code.selectionStart-prefix.length;
   const lang=document.getElementById('language').value;
   options=words[lang].filter(w=>w.toLowerCase().startsWith(prefix.toLowerCase()) && w!==prefix).slice(0,7);selected=0;
   if(!options.length){hide();return;}
   menu.replaceChildren();options.forEach((w,i)=>{const b=document.createElement('button');b.type='button';b.textContent=w;b.setAttribute('role','option');b.onmousedown=e=>e.preventDefault();b.onclick=()=>accept(i);menu.append(b);});
   menu.hidden=false;code.setAttribute('aria-expanded','true');select();
 }
 function select(){[...menu.children].forEach((b,i)=>{b.classList.toggle('selected',i===selected);b.setAttribute('aria-selected',i===selected);});}
 code.addEventListener('input',()=>{render();complete();});
 code.addEventListener('scroll',()=>{painted.scrollTop=code.scrollTop;painted.scrollLeft=code.scrollLeft;hide();});
 code.addEventListener('keydown',e=>{
   if(e.ctrlKey&&e.code==='Space'){e.preventDefault();complete(true);return;}
   if(menu.hidden||e.ctrlKey||e.metaKey||e.isComposing)return;
   if(['ArrowDown','ArrowUp','Enter','Tab','Escape'].includes(e.key)){
     e.preventDefault();e.stopImmediatePropagation();
     if(e.key==='Escape')hide();else if(e.key==='Enter'||e.key==='Tab')accept(selected);
     else {selected=(selected+(e.key==='ArrowDown'?1:-1)+options.length)%options.length;select();}
   }
 },true);
 code.addEventListener('blur',hide);code.addEventListener('click',hide);
 document.getElementById('language').addEventListener('change',()=>{hide();render();});
 document.getElementById('problem-select').addEventListener('change',()=>{hide();render();});
 document.getElementById('reset-code').addEventListener('click',()=>{hide();render();});
 document.getElementById('restore-draft').addEventListener('click',()=>{hide();render();});
 document.getElementById('code-help').textContent='Tab 缩进 · Ctrl + Space 补全 · Ctrl / ⌘ + Enter 提交';
 render();
})();
