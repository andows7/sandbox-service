import http from 'node:http';
import https from 'node:https';
import {createReadStream} from 'node:fs';
import {stat} from 'node:fs/promises';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const publicDir=fileURLToPath(new URL('./public/',import.meta.url));
const target=new URL(process.env.BACKEND_TARGET||'http://127.0.0.1:8084');
if(!['http:','https:'].includes(target.protocol))throw new Error('BACKEND_TARGET must be an HTTP backend URL');
const host=process.env.HOST||'127.0.0.1',port=Number(process.env.PORT||5173);
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8','.svg':'image/svg+xml','.png':'image/png','.ico':'image/x-icon'};
const server=http.createServer(async(req,res)=>{
 let url;
 try {url=new URL(req.url,'http://localhost');}
 catch {res.writeHead(400);res.end('Invalid URL');return;}
 if(/^\/(api|v1|actuator|swagger-ui|v3)(\/|$)/.test(url.pathname)||url.pathname==='/swagger-ui.html') {
   const headers={...req.headers,host:target.host};
   // Direct dev calls do not need or fabricate gateway identity headers.
   for(const name of ['x-user-id','x-user-role','x-user-roles'])delete headers[name];
   const destination=new URL(target.origin);destination.pathname=url.pathname;destination.search=url.search;
   const upstream=(target.protocol==='https:'?https:http).request(destination,{method:req.method,headers},r=>{
     res.writeHead(r.statusCode,r.headers);r.on('error',()=>res.destroy());r.pipe(res);
   });
   upstream.setTimeout(600000,()=>upstream.destroy(new Error('Gateway timeout')));
   upstream.on('error',()=>{if(!res.headersSent){res.writeHead(502,{'Content-Type':'application/json; charset=utf-8'});res.end(JSON.stringify({code:502,msg:'无法连接沙箱后端，请检查 BACKEND_TARGET 和后端运行状态',data:null}));}else res.destroy();});
   req.on('aborted',()=>upstream.destroy());res.on('close',()=>{if(!res.writableEnded)upstream.destroy();});req.pipe(upstream);return;
 }
 if(!['GET','HEAD'].includes(req.method)){res.writeHead(405);res.end();return;}
 try {
   const relative=decodeURIComponent(url.pathname)==='/'?'index.html':decodeURIComponent(url.pathname).slice(1);
   const file=path.resolve(publicDir,relative);
   if(!file.startsWith(publicDir)||relative.includes('\\')||relative.split('/').some(p=>p.startsWith('.'))){res.writeHead(403);res.end();return;}
   if(!(await stat(file)).isFile()){res.writeHead(404);res.end();return;}
   res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream','Cache-Control':'no-store','X-Content-Type-Options':'nosniff'});
   if(req.method==='HEAD')res.end();else createReadStream(file).on('error',()=>res.destroy()).pipe(res);
 }catch {res.writeHead(404);res.end('Not found');}
});
server.listen(port,host,()=>console.log(`Frontend: http://${host}:${port}\nBackend proxy: ${target.origin}`));
