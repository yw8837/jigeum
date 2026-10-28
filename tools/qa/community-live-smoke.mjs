// Synthetic-only API smoke test. Credentials arrive through hidden stdin, never argv/files.
import fs from 'node:fs';
import assert from 'node:assert/strict';
process.stdin.setRawMode?.(true); process.stdin.resume();
process.stdout.write('Ready for synthetic QA JSON (input hidden).\n');
const config=await new Promise(resolve=>{let input='';const read=chunk=>{input+=chunk.toString();if(/[\r\n]/.test(input)){process.stdin.off('data',read);process.stdin.setRawMode?.(false);process.stdin.pause();resolve(JSON.parse(input.trim()));}};process.stdin.on('data',read);});
const base=config.base, common=config.bypass?{'OAI-Sites-Authorization':'Bearer '+config.bypass}:{};
let cookie='';const results=[];const created=[];
async function request(path,data,extra={}){
  return fetch(base+'/api/'+path,{method:data?'POST':'GET',headers:{...common,cookie,origin:base,'x-board-client':'1',...(data&&!(data instanceof FormData)?{'Content-Type':'application/json'}:{}),...extra},body:data instanceof FormData?data:data?JSON.stringify(data):undefined});
}
try {
  const session=await request('session');assert.equal(session.status,200);cookie=(session.headers.get('set-cookie')||'').split(';')[0];assert.ok(cookie);assert.equal((await session.json()).admin,false);results.push('anonymous session');
  const spoof={'oai-authenticated-user-id':'qa-forged-operator','oai-authenticated-user-email':'bsfire1237@gmail.com'};
  assert.equal((await (await request('session',null,spoof)).json()).admin,false);
  assert.equal((await request('moderation',null,spoof)).status,403);results.push('host strips forged operator headers');
  for(const [path,mime] of [[config.jpeg,'image/jpeg'],[config.mp4,'video/mp4']]) {
    const id=crypto.randomUUID(),form=new FormData();for(const [k,v] of Object.entries({id,nickname:'합성 QA',body:'합성 테스트 · 실제 아기 자료 없음',password:'synthetic-only-qa-password',consent:'yes'}))form.set(k,v);
    form.set('file',new File([fs.readFileSync(path)],mime==='video/mp4'?'synthetic.mp4':'synthetic.jpg',{type:mime}));
    const posted=await request('posts',form);assert.equal(posted.status,201,await posted.clone().text());created.push(id);
    assert.equal((await request('like',{postId:id,liked:true})).status,200);
    assert.equal((await (await request('like',{postId:id,liked:true})).json()).count,1);
    const commentId=crypto.randomUUID();assert.equal((await request('comments',{postId:id,id:commentId,body:'합성 댓글',password:'comment-only-qa-password',consent:'yes'})).status,201);
    assert.equal((await request('manage',{kind:'post',id,action:'edit',body:'수정',password:'wrong-password'})).status,403);
    assert.equal((await request('manage',{kind:'post',id,action:'edit',body:'합성 QA 수정 완료',password:'synthetic-only-qa-password'})).status,200);
    const range=await request('media/'+id,null,{Range:'bytes=0-99'});assert.equal(range.status,206);assert.equal((await range.arrayBuffer()).byteLength,100);
    const feed=await (await request('feed')).json();const p=feed.posts.find(p=>p.id===id);assert.equal(p.body,'합성 QA 수정 완료');assert.equal(p.comments,1);assert.equal(p.likes,1);
    results.push(mime+' publish, persist, range, comment, like and password');
  }
} finally {
  for(const id of created){const deleted=await request('manage',{kind:'post',id,action:'delete',password:'synthetic-only-qa-password'});assert.equal(deleted.status,200);assert.equal((await request('media/'+id)).status,404);}
  if(created.length)results.push('all owned synthetic posts deleted; media denied');
}
const output={time:new Date().toISOString(),base,passed:results,syntheticPosts:created.length};
if(config.evidence)fs.writeFileSync(config.evidence,JSON.stringify(output,null,2)+'\n');
console.log(JSON.stringify(output));
