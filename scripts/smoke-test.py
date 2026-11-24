"""Real HTTP / Docker tests against dev. Supply a gateway token or explicit internal test user."""
import os
import concurrent.futures
import json
from pathlib import Path
import time
import urllib.request
import urllib.error
import uuid
import sys

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://127.0.0.1:8084'
RESULTS = []

def http(path, data=None):
    headers={'Content-Type':'application/json','X-Trace-Id':'smoke-'+uuid.uuid4().hex}
    if os.environ.get('SANDBOX_TEST_TOKEN'):
        headers['Authorization']='Bearer '+os.environ['SANDBOX_TEST_TOKEN'].removeprefix('Bearer ')
    if os.environ.get('SANDBOX_TEST_USER_ID'):
        headers['X-User-Id']=os.environ['SANDBOX_TEST_USER_ID']
    req = urllib.request.Request(BASE+path, data=json.dumps(data).encode() if data is not None else None,
        headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=120) as response:
            return response.status, json.load(response)
    except urllib.error.HTTPError as error:
        return error.code, json.load(error)

def run(name, language, code, expected, stdin='1 2', stdout='3', memory=128, time_limit=1000):
    start=time.monotonic()
    status, body = http('/v1/api/sandbox/execute', {'submitId':'smoke-'+uuid.uuid4().hex,
        'language':language, 'code':code, 'testCases':[{'input':stdin,'expectedOutput':stdout}],
        'timeLimitMs':time_limit,'memoryLimitMb':memory})
    actual=body.get('data',{}).get('status') if body.get('data') else str(body)
    record={'name':name,'language':language,'expected':expected,'actual':actual,
        'wallMs':round((time.monotonic()-start)*1000),'response':body}
    RESULTS.append(record)
    print(name, actual, flush=True)
    assert status==200 and actual==expected, json.dumps(record,ensure_ascii=False)

try:
    codes={
      'C':'#include <stdio.h>\nint main(void){long a,b;if(scanf("%ld%ld",&a,&b)!=2)return 1;printf("%ld\\n",a+b);return 0;}',
      'CPP':'#include <iostream>\nint main(){long a,b;std::cin>>a>>b;std::cout<<a+b;}',
      'JAVA':'import java.util.Scanner; public class Main{public static void main(String[]x){Scanner s=new Scanner(System.in);System.out.println(s.nextLong()+s.nextLong());}}',
      'PYTHON':'a,b=map(int,input().split());print(a+b)',
    }
    for lang, code in codes.items(): run(lang+' AC',lang,code,'AC',time_limit=3000)
    run('Python CE','PYTHON','if True\n    print(1)\n','CE',stdin='',stdout='',time_limit=3000)
    run('Python RE','PYTHON','raise RuntimeError("runtime failure")\n','RE',stdin='',stdout='',time_limit=3000)
    run('C WA','C',codes['C'],'WA',stdout='4')
    run('C CE','C','int main( {','CE')
    run('C TLE','C','int main(void){for(;;){} }','TLE',time_limit=200)
    run('C RE','C','#include <signal.h>\nint main(void){raise(SIGSEGV);}','RE')
    run('C MLE','C','#include <stdlib.h>\nint main(void){for(;;){volatile char *p=malloc(1048576);if(!p)return 1;for(int i=0;i<1048576;i+=4096)p[i]=1;}}','MLE',memory=32,time_limit=3000)
    run('C UTF-8','C','#include <stdio.h>\nint main(void){puts("慧学 C 语言");}','AC',stdin='',stdout='慧学 C 语言')
    run('C excessive output','C','#include <stdio.h>\nint main(void){for(int i=0;i<100000;i++)putchar(65);}','RE',stdin='',stdout='')
    _, body=http('/api/sandbox/execute',{'submitId':'bad','language':'C','code':'int main(void){}','testCases':[]})
    assert body['code']==400,body
    RESULTS.append({'name':'DTO empty cases','actual':400,'expected':400})
    # Concurrent submissions demonstrate bounded admission and resource return.
    req={'submitId':'concurrent','language':'C','code':'int main(void){for(;;){}}',
         'testCases':[{'input':'','expectedOutput':''}],'timeLimitMs':500,'memoryLimitMb':64}
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
        responses=list(executor.map(lambda _:http('/api/sandbox/execute',dict(req,submitId=uuid.uuid4().hex)),range(8)))
    assert all(b['code'] in (200,503) for _,b in responses),responses
    assert any(b['code']==503 for _,b in responses),responses
    _, info=http('/api/sandbox/status')
    assert info['data']['containers']==0,info
    RESULTS.append({'name':'8 concurrent requests and cleanup','actual':'bounded; zero containers after completion','expected':'bounded; zero containers after completion','service':info['data']})
finally:
    out=Path('outputs');out.mkdir(exist_ok=True)
    (out/'smoke-results.json').write_text(json.dumps(RESULTS,ensure_ascii=False,indent=2),encoding='utf-8')
print('All real execution smoke tests passed.')
