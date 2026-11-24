'use strict';
const $ = id => document.getElementById(id);
const problems = {
  sum: {title:'两数之和',tags:['基础运算','标准输入'],description:'给定两个整数 a 和 b，请计算并输出它们的和。',input:'一行包含两个以空格分隔的整数 a 和 b。',output:'输出一个整数，表示 a + b 的结果。',constraints:'−10⁹ ≤ a, b ≤ 10⁹',cases:[{input:'1 2',expectedOutput:'3'},{input:'-7 12',expectedOutput:'5'},{input:'0 0',expectedOutput:'0'}],solution:'读取两个整数，直接计算它们的和。C / C++ 可使用 long long，Java 使用 long，避免扩展数据范围后出现整数溢出。\n\n时间复杂度 O(1)，空间复杂度 O(1)。'},
  root: {title:'根结点与子结点之和',tags:['二叉树','条件判断'],description:'一棵二叉树由 3 个结点组成：根结点、左子结点和右子结点。如果根结点的值等于两个子结点值之和，输出 true，否则输出 false。',input:'一行包含 root、left、right 三个整数。演示采用标准输入，无需实现 TreeNode。',output:'输出 true 或 false（小写）。',constraints:'−10⁶ ≤ root, left, right ≤ 10⁶',cases:[{input:'10 4 6',expectedOutput:'true'},{input:'5 3 1',expectedOutput:'false'}],solution:'依次读取根结点、左子结点、右子结点的值，比较 root == left + right。\n\n参考图中的树结构在本演示中转换为三个整数的标准输入。时间复杂度 O(1)，空间复杂度 O(1)。'},
  fib: {title:'斐波那契数列',tags:['迭代','空间优化'],description:'定义 F(0) = 0、F(1) = 1，且 F(n) = F(n−1) + F(n−2)。给定 n，计算 F(n)。',input:'一行包含一个整数 n。',output:'输出 F(n) 的值。',constraints:'0 ≤ n ≤ 40',cases:[{input:'0',expectedOutput:'0'},{input:'10',expectedOutput:'55'},{input:'40',expectedOutput:'102334155'}],solution:'用两个变量保存相邻两项，每次迭代更新它们。无需递归，也无需保存完整数组。\n\n时间复杂度 O(n)，空间复杂度 O(1)。'}
};
const templates = {
  sum: {
    C:'#include <stdio.h>\n\nint main(void) {\n    long long a, b;\n    if (scanf("%lld %lld", &a, &b) != 2) return 1;\n    printf("%lld\\n", a + b);\n    return 0;\n}\n',
    CPP:'#include <iostream>\nusing namespace std;\n\nint main() {\n    long long a, b;\n    cin >> a >> b;\n    cout << a + b << "\\n";\n    return 0;\n}\n',
    JAVA:'import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        long a = in.nextLong();\n        long b = in.nextLong();\n        System.out.println(a + b);\n    }\n}\n',
    PYTHON:'a, b = map(int, input().split())\nprint(a + b)\n'
  },
  root: {
    C:'#include <stdio.h>\n\nint main(void) {\n    int root, left, right;\n    if (scanf("%d %d %d", &root, &left, &right) != 3) return 1;\n    puts(root == left + right ? "true" : "false");\n    return 0;\n}\n',
    CPP:'#include <iostream>\nusing namespace std;\n\nint main() {\n    int root, left, right;\n    cin >> root >> left >> right;\n    cout << (root == left + right ? "true" : "false") << "\\n";\n}\n',
    JAVA:'import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        Scanner in = new Scanner(System.in);\n        int root = in.nextInt();\n        int left = in.nextInt();\n        int right = in.nextInt();\n        System.out.println(root == left + right);\n    }\n}\n',
    PYTHON:'root, left, right = map(int, input().split())\nprint("true" if root == left + right else "false")\n'
  },
  fib: {
    C:'#include <stdio.h>\n\nint main(void) {\n    int n;\n    if (scanf("%d", &n) != 1) return 1;\n    long long a = 0, b = 1;\n    for (int i = 0; i < n; i++) {\n        long long next = a + b;\n        a = b;\n        b = next;\n    }\n    printf("%lld\\n", a);\n    return 0;\n}\n',
    CPP:'#include <iostream>\nusing namespace std;\n\nint main() {\n    int n; cin >> n;\n    long long a = 0, b = 1;\n    for (int i = 0; i < n; ++i) {\n        long long next = a + b;\n        a = b; b = next;\n    }\n    cout << a << "\\n";\n}\n',
    JAVA:'import java.util.Scanner;\n\npublic class Main {\n    public static void main(String[] args) {\n        int n = new Scanner(System.in).nextInt();\n        long a = 0, b = 1;\n        for (int i = 0; i < n; i++) {\n            long next = a + b;\n            a = b; b = next;\n        }\n        System.out.println(a);\n    }\n}\n',
    PYTHON:'n = int(input())\na, b = 0, 1\nfor _ in range(n):\n    a, b = b, a + b\nprint(a)\n'
  }
};
let problem = 'sum', activeCase = 0, cases = [], busy = false, saveTimer, activeDraftKey, currentSubmission = null, codeDirty = false;
const names = {C:'main.c',CPP:'main.cpp',JAVA:'Main.java',PYTHON:'main.py'};
const labels = {AC:'答案正确',WA:'答案错误',CE:'编译错误',TLE:'运行超时',MLE:'内存超限',RE:'运行异常'};
function readLocal(key, fallback) {try {const value=localStorage.getItem(key);return value===null?fallback:JSON.parse(value);}catch{return fallback;}}
function writeLocal(key,value) {try {localStorage.setItem(key,JSON.stringify(value));return true;}catch{return false;}}
let history = readLocal('huixue.history', []);
if (!Array.isArray(history)) history=[];
history=history.filter(h=>h && typeof h.title==='string' && h.result && typeof h.result==='object' && Array.isArray(h.items)).slice(0,20);
const drafts = {};
function draftKey() { return `huixue.draft.${problem}.${$('language').value}`; }
function refreshDraftButton() {const saved=readLocal(activeDraftKey,null);$('restore-draft').hidden=typeof saved!=='string'||saved===$('code').value;}
function saveCode() {clearTimeout(saveTimer);if(!codeDirty)return;const key=activeDraftKey||draftKey();drafts[key]=$('code').value;$('save-state').textContent=writeLocal(key,$('code').value)?'已保存到本地':'草稿保留于本次会话';codeDirty=false;refreshDraftButton();}
function updateLines() {$('line-numbers').textContent=Array.from({length:$('code').value.split('\n').length},(_,i)=>i+1).join('\n');}
function loadCode() {activeDraftKey=draftKey();const v=drafts[activeDraftKey];$('code').value=typeof v==='string'?v:templates[problem][$('language').value];codeDirty=false;$('filename').textContent=names[$('language').value];$('save-state').textContent=typeof v==='string'?'本次草稿':'正确示例';updateLines();refreshDraftButton();}
function node(tag, cls, value) {const n=document.createElement(tag);if(cls)n.className=cls;if(value!==undefined)n.textContent=value;return n;}
function showView(view) {document.querySelectorAll('[data-view]').forEach(b=>b.setAttribute('aria-selected',b.dataset.view===view));['description','solution','history'].forEach(id=>$(id).hidden=id!==view);}
function showTest(view) {document.querySelectorAll('[data-testview]').forEach(b=>b.setAttribute('aria-selected',b.dataset.testview===view));['cases','results','submission'].forEach(id=>$(id).hidden=id!==view);}
function renderProblem() {
  const p=problems[problem], d=$('description');d.replaceChildren();
  const meta=node('div','problem-meta');meta.append(node('span','difficulty','简单'),...p.tags.map(t=>node('span','tag',t)));d.append(meta,node('h2','',p.title),node('p','',p.description),node('h3','','输入格式'),node('p','',p.input),node('h3','','输出格式'),node('p','',p.output));
  if(problem==='root') {const wrap=node('div');wrap.innerHTML='<svg class="tree" viewBox="0 0 220 135" role="img" aria-label="根结点10，左子结点4，右子结点6"><line x1="110" y1="30" x2="55" y2="103"/><line x1="110" y1="30" x2="165" y2="103"/><circle cx="110" cy="30" r="23"/><circle cx="55" cy="103" r="23"/><circle cx="165" cy="103" r="23"/><text x="110" y="36" text-anchor="middle">10</text><text x="55" y="109" text-anchor="middle">4</text><text x="165" y="109" text-anchor="middle">6</text></svg>';d.append(wrap);}
  p.cases.slice(0,2).forEach((tc,i)=>{d.append(node('h3','',`示例 ${i+1}`));const sample=node('div','sample');sample.append(node('div','sample-label','输入'),node('pre','',tc.input),node('div','sample-label','输出'),node('pre','',tc.expectedOutput));d.append(sample);});d.append(node('h3','','数据范围'),node('p','constraint',p.constraints));
  $('solution').replaceChildren(node('h2','','解题思路'),...p.solution.split('\n\n').map(t=>node('p','',t)));
}
function renderCases() {const tabs=$('case-tabs');tabs.replaceChildren();cases.forEach((_,i)=>{const b=node('button','case-tab'+(i===activeCase?' active':''),`Case ${i+1}`);b.type='button';b.setAttribute('aria-pressed',i===activeCase);b.disabled=busy;b.onclick=()=>{persistCase();activeCase=i;renderCases();};tabs.append(b);});$('case-input').value=cases[activeCase].input;$('case-expected').value=cases[activeCase].expectedOutput;$('remove-case').disabled=busy||cases.length===1;$('add-case').disabled=busy||cases.length>=50;}
function persistCase() {if(cases[activeCase]) {cases[activeCase].input=$('case-input').value;cases[activeCase].expectedOutput=$('case-expected').value;}}
function clearResults(message='准备就绪，等待你的代码') {$('results').replaceChildren();const n=node('div','empty-result');n.append(node('span','','⌁'),node('strong','',message),node('p','','运行一个用例，或提交全部用例开始评测。'));$('results').append(n);$('result-dot').textContent='';}
function setBusy(value) {busy=value;['run','submit','language','problem-select','reset-code','restore-draft','case-input','case-expected','time-limit','memory-limit'].forEach(id=>$(id).disabled=value);$('code').readOnly=value;renderCases();renderHistory();}
function renderSubmission(submission) {
  const area=$('submission');area.replaceChildren();
  if(!submission){area.append(node('p','result-meta','评测完成后，点击提交记录可查看当时提交的代码。'));return;}
  area.append(node('h3','submission-title',`${submission.title} · ${submission.language}`),node('p','result-meta',`${submission.at} · ${submission.runOnly?'运行当前用例':'提交全部用例'}`));
  const limits=[];
  if(Number.isFinite(submission.timeLimitMs))limits.push(`${submission.timeLimitMs} ms`);
  if(Number.isFinite(submission.memoryLimitMb))limits.push(`${submission.memoryLimitMb} MiB`);
  if(limits.length)area.append(node('p','result-meta',`当时限制：${limits.join(' / ')}`));
  area.append(node('p','submission-notice','以下为当时实际提交的源码，查看记录不会修改当前编辑器。'));
  const source=node('section','submission-source');source.append(node('h4','',`当时提交的代码 · ${names[submission.language]||submission.language}`));
  source.append(typeof submission.code==='string'?node('pre','submission-value',submission.code):node('p','result-meta','这条旧记录未保存源码，无法还原当时的代码。'));
  area.append(source);
  const testCases=node('details','submission-cases');testCases.append(node('summary','',`当时测试用例（${submission.items.length} 个）`));
  submission.items.forEach((tc,i)=>{
    const section=node('section','submission-case');section.append(node('h4','',`Case ${Number.isInteger(tc.index)?tc.index+1:i+1}`));
    section.append(node('div','sample-label','标准输入'),node('pre','submission-value',tc.input||'（空输入）'),node('div','sample-label','预期输出'),node('pre','submission-value',tc.expectedOutput||'（空输出）'));testCases.append(section);
  });
  area.append(testCases);
}
function renderResult(result,items,runOnly,submission) {
  const area=$('results');area.replaceChildren();const summary=node('div','result-summary');summary.append(node('strong','result-status'+(result.status==='AC'?'':' failed'),`${result.status} · ${labels[result.status]||'未知状态'}`));const measured=result.memoryUsedMb>0?`${result.memoryUsedMb} MiB`:'未采集';summary.append(node('span','result-meta',`${result.executionTimeMs||0} ms / 峰值 RSS ${measured}${runOnly?' / 当前用例':' / 全部用例'}`));area.append(summary);
  if(submission){const context=node('div','submission-context'),view=node('button','snapshot-button','查看提交代码');view.type='button';view.onclick=()=>showTest('submission');context.append(node('span','result-meta',`${submission.title} · ${submission.language} · ${submission.at}`),view);area.prepend(context);}
  if(result.compileError)area.append(node('pre','error-detail',result.compileError));
  const results=Array.isArray(result.testCaseResults)?result.testCaseResults:[];
  items.forEach((tc,i)=>{const cr=results[i],row=node('div','result-case');row.append(node('span',cr?(cr.passed?'pass':'fail'):'',`Case ${tc.index+1} · ${cr?(cr.passed?'通过':'未通过'):'未执行'}`));if(cr){row.append(node('pre','',`实际输出：${cr.actualOutput??'—'}\n预期输出：${tc.expectedOutput}`));if(cr.errorLine)row.append(node('span','',`第 ${cr.errorLine} 行`));}area.append(row);});
  if(results.length<items.length)area.append(node('p','result-meta','编译失败或遇到首个失败用例后，后续用例不再执行。'));
  $('result-dot').textContent=' •';showTest('results');
}
function renderHistory() {$('history-count').textContent=history.length;const list=$('history-list');list.replaceChildren();if(!history.length) {list.append(node('p','','还没有提交记录。完成一次评测后，结果会保存在本浏览器。'));return;}history.forEach(h=>{const b=node('button','history-item'),info=node('span');b.type='button';b.disabled=busy;b.setAttribute('aria-pressed',currentSubmission===h);info.append(node('strong','',`${h.title} · ${h.language}`),node('small','',h.at),node('small','history-action','查看提交代码与结果'));b.append(info,node('strong','',h.result.status));b.onclick=()=>{if(busy)return;currentSubmission=h;renderSubmission(h);renderResult(h.result,h.items,h.runOnly,h);showTest('submission');renderHistory();};list.append(b);});}
async function execute(runOnly) {
  if(busy)return;persistCase();saveCode();if(!$('code').value.trim()){$('results').replaceChildren(node('pre','error-detail','请先输入完整程序。'));showTest('results');return;}
  const items=(runOnly?[activeCase]:cases.map((_,i)=>i)).map(index=>({...cases[index],index}));const language=$('language').value;
  const submission={title:problems[problem].title,language,at:new Date().toLocaleString('zh-CN'),code:$('code').value,timeLimitMs:Number($('time-limit').value),memoryLimitMb:Number($('memory-limit').value),items,runOnly};
  setBusy(true);showTest('results');const waiting=node('div','empty-result');const text=node('strong','','正在准备容器并运行代码…');text.prepend(node('span','spinner'));waiting.append(text,node('p','','首次运行需要创建容器，耗时取决于本机 Docker 环境。'));$('results').replaceChildren(waiting);
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),600000);
  try {
    const res=await apiFetch('/api/sandbox/execute',{method:'POST',headers:{'Content-Type':'application/json','X-Trace-Id':`demo-${crypto.randomUUID()}`},body:JSON.stringify({submitId:`demo-${crypto.randomUUID()}`,language,code:submission.code,testCases:items.map(({input,expectedOutput})=>({input,expectedOutput})),timeLimitMs:submission.timeLimitMs,memoryLimitMb:submission.memoryLimitMb}),signal:controller.signal});
    let body;try{body=await res.json();}catch{throw new Error(`服务返回了无法解析的响应（HTTP ${res.status}）。`);}
    if(!res.ok||body.code!==200||!body.data)throw new Error(body.msg||`HTTP ${res.status}`);
    submission.result=body.data;currentSubmission=submission;renderSubmission(submission);renderResult(body.data,items,runOnly,submission);
    history.unshift(submission);history=history.slice(0,20);writeLocal('huixue.history',history);renderHistory();
  } catch(e) {$('results').replaceChildren(node('pre','error-detail',e.name==='AbortError'?'等待响应超时。请检查服务日志与 Docker 状态。':`评测未完成：${e.message}\n请确认 Docker 已启动、语言镜像已构建，服务端口为 8084。`));}
  finally{clearTimeout(timer);setBusy(false);refreshStatus();}
}
async function refreshStatus(){try {const r=await apiFetch('/api/sandbox/status');if(r.status===401){$('connection').textContent='后端拒绝直连，请确认使用最新 dev 配置';$('connection').classList.remove('online');return;}if(!r.ok)throw new Error();const b=await r.json();if(b.code!==200)throw new Error();$('pool-count').textContent=`${b.data.containers} / ${b.data.maxContainers}`;$('heap-used').textContent=b.data.heapUsedMb;$('connection').textContent='评测服务已连接';$('connection').classList.add('online');}catch{$('connection').textContent='评测服务未连接';$('connection').classList.remove('online');}}
document.querySelectorAll('[data-view]').forEach(b=>b.onclick=()=>showView(b.dataset.view));document.querySelectorAll('[data-testview]').forEach(b=>b.onclick=()=>showTest(b.dataset.testview));
$('language').onchange=()=>{saveCode();loadCode();clearResults();};
$('problem-select').onchange=()=>{saveCode();problem=$('problem-select').value;activeCase=0;cases=problems[problem].cases.map(x=>({...x}));renderProblem();renderCases();loadCode();clearResults();showView('description');};
$('code').addEventListener('input',()=>{codeDirty=true;updateLines();clearResults('代码已修改，请重新运行');drafts[draftKey()]=$('code').value;$('save-state').textContent='正在保存…';clearTimeout(saveTimer);saveTimer=setTimeout(saveCode,300);});
$('code').addEventListener('scroll',()=>{$('line-numbers').scrollTop=$('code').scrollTop;});
$('code').addEventListener('keydown',e=>{if(e.key==='Tab'&&!busy){e.preventDefault();const el=e.target;el.setRangeText('    ',el.selectionStart,el.selectionEnd,'end');el.dispatchEvent(new Event('input'));}});
$('reset-code').onclick=()=>{saveCode();delete drafts[activeDraftKey];loadCode();clearResults('已恢复示例，请重新运行');};
$('restore-draft').onclick=()=>{const saved=readLocal(activeDraftKey,null);if(typeof saved!=='string')return;clearTimeout(saveTimer);$('code').value=saved;drafts[activeDraftKey]=saved;codeDirty=false;$('save-state').textContent='已恢复本地草稿';updateLines();refreshDraftButton();clearResults('已恢复草稿，请重新运行');};
$('case-input').oninput=()=>{persistCase();clearResults();};$('case-expected').oninput=()=>{persistCase();clearResults();};
$('add-case').onclick=()=>{persistCase();cases.push({input:'',expectedOutput:''});activeCase=cases.length-1;renderCases();};$('remove-case').onclick=()=>{if(cases.length>1){cases.splice(activeCase,1);activeCase=Math.min(activeCase,cases.length-1);renderCases();}};
$('run').onclick=()=>execute(true);$('submit').onclick=()=>execute(false);document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key==='Enter'){e.preventDefault();execute(false);}});
window.addEventListener('beforeunload',()=>{saveCode();});cases=problems[problem].cases.map(x=>({...x}));renderProblem();renderCases();loadCode();renderHistory();renderSubmission(null);refreshStatus();setInterval(refreshStatus,15000);
