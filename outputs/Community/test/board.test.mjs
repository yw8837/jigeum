import { test, beforeEach, afterEach } from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { readFileSync, readdirSync } from 'node:fs';
import { handleBoard } from '../lib/board.mjs';
let sqlite, env;
beforeEach(() => {
  sqlite=new DatabaseSync(':memory:');
  for(const f of readdirSync('drizzle').filter(f=>f.endsWith('.sql')).sort())sqlite.exec(readFileSync('drizzle/'+f,'utf8'));
  const prepare=(sql,args=[])=>({bind:(...v)=>prepare(sql,v),first:async()=>sqlite.prepare(sql).get(...args)||null,
    all:async()=>({results:sqlite.prepare(sql).all(...args)}),run:async()=>({meta:sqlite.prepare(sql).run(...args)})});
  const objects=new Map();
  env={DB:{prepare,batch:async list=>{sqlite.exec('BEGIN');try{const r=await Promise.all(list.map(s=>s.run()));sqlite.exec('COMMIT');return r;}catch(e){sqlite.exec('ROLLBACK');throw e;}}},
    BUCKET:{put:async(k,v,options={})=>objects.set(k,{bytes:new Uint8Array(v),customMetadata:options.customMetadata}),get:async k=>{const o=objects.get(k);return o?{body:o.bytes,size:o.bytes.length,customMetadata:o.customMetadata,arrayBuffer:async()=>o.bytes.slice().buffer}:null;},delete:async k=>objects.delete(k)},RATE_SECRET:'test-secret',ADMIN_EMAIL:'operator@example.test'};
});
afterEach(()=>sqlite.close());
const visitor='a'.repeat(64),second='b'.repeat(64);
async function call(path,data,who=visitor,extra={}) {
  return handleBoard(new Request('https://board.test/api/'+path,{method:data?'POST':'GET',
    headers:{cookie:'agattok_visitor='+who,origin:'https://board.test','x-board-client':'1',...(data && !(data instanceof FormData)?{'Content-Type':'application/json'}:{}),...extra},
    body:data instanceof FormData?data:data?JSON.stringify(data):undefined}),env);
}
function form(id=crypto.randomUUID()) {
  const jpeg=readFileSync(new URL('./fixtures/synthetic.jpg',import.meta.url));
  const f=new FormData();for(const [k,v]of Object.entries({id,nickname:'익명',body:'우리 아기의 오늘',password:'my-only-post-password',consent:'yes'}))f.set(k,v);
  f.set('file',new File([jpeg],'photo.jpg',{type:'image/jpeg'}));return f;
}
async function create(){const r=await call('posts',form());assert.equal(r.status,201,await r.clone().text());return (await r.json()).id;}
test('type filter searches all visible posts before pagination and excludes blocked authors',async()=>{
  const id=await create(),base=sqlite.prepare('SELECT * FROM posts WHERE id=?').get(id);
  for(let i=0;i<45;i++)sqlite.prepare('INSERT INTO posts(id,author,nickname,body,salt,password_hash,media_key,mime,bytes,duration,created,state) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)').run(crypto.randomUUID(),base.author,'익명','사진 '+i,base.salt,base.password_hash,'photo-'+i,'image/jpeg',25,0,50000+i,'active');
  for(let i=0;i<23;i++)sqlite.prepare('INSERT INTO posts(id,author,nickname,body,salt,password_hash,media_key,mime,bytes,duration,created,state) VALUES(?,?,?,?,?,?,?,?,?,?,?,?)').run(crypto.randomUUID(),base.author,'익명','영상 '+i,base.salt,base.password_hash,'video-'+i,'video/mp4',100,2000,10000+i,'active');
  const first=await (await call('feed?type=video')).json(),next=await (await call('feed?type=video&before='+first.next)).json();
  assert.equal(first.posts.length,20);assert.equal(next.posts.length,3);assert.equal(new Set([...first.posts,...next.posts].map(p=>p.id)).size,23);assert.ok([...first.posts,...next.posts].every(p=>p.mime==='video/mp4'));
  assert.ok((await (await call('feed?type=photo')).json()).posts.every(p=>p.mime==='image/jpeg'));
  assert.equal((await call('feed?type=other')).status,400);
  await call('block',{kind:'post',id},second);assert.equal((await (await call('feed?type=video',null,second)).json()).posts.length,0);
});

test('public read never reveals password, salt, author token, or media key',async()=>{
  await create();const r=await call('feed');assert.equal(r.status,200);const data=await r.text();
  for(const secret of ['password_hash','salt','media_key','author',visitor,'my-only-post-password'])assert.ok(!data.includes(secret),secret);
  assert.equal(JSON.parse(data).posts.length,1);
});
test('wrong password cannot edit/delete, correct password works from another browser',async()=>{
  const id=await create();assert.equal((await call('manage',{kind:'post',id,action:'delete',password:'wrong-pass'},second)).status,403);
  assert.equal((await call('manage',{kind:'post',id,action:'edit',password:'my-only-post-password',body:'수정한 설명'},second)).status,200);
  assert.equal((await (await call('feed')).json()).posts[0].body,'수정한 설명');
  assert.equal((await call('manage',{kind:'post',id,action:'delete',password:'my-only-post-password'},second)).status,200);
  assert.equal((await call('media/'+id)).status,404);
});
test('likes are idempotent, concurrent duplicate creates only one',async()=>{
  const id=await create();await Promise.all([call('like',{postId:id,liked:true}),call('like',{postId:id,liked:true})]);
  assert.equal((await (await call('feed')).json()).posts[0].likes,1);
  await call('like',{postId:id,liked:false});assert.equal((await (await call('feed')).json()).posts[0].likes,0);
});
test('comments require their own password; parent deletion removes visibility',async()=>{
  const postId=await create(),id=crypto.randomUUID();
  assert.equal((await call('comments',{postId,id,body:'귀여워요',password:'comment-password',consent:'yes'},second)).status,201);
  assert.equal((await call('manage',{kind:'comment',id,action:'delete',password:'my-only-post-password'})).status,403);
  assert.equal((await call('manage',{kind:'comment',id,action:'edit',password:'comment-password',consent:'yes',body:'정말 귀여워요'})).status,200);
  await call('manage',{kind:'post',id:postId,action:'delete',password:'my-only-post-password'});
  assert.equal((await call('comments/'+postId)).status,404);
});
test('block hides posts and media only for blocking visitor; unblock restores',async()=>{
  const id=await create();await call('block',{kind:'post',id},second);
  assert.equal((await (await call('feed',null,second)).json()).posts.length,0);assert.equal((await call('media/'+id,null,second)).status,404);
  assert.equal((await (await call('feed')).json()).posts.length,1);
  const blocks=await (await call('blocks',null,second)).json();await call('unblock',{author:blocks.blocks[0].author},second);
  assert.equal((await (await call('feed',null,second)).json()).posts.length,1);
});
test('reports are private; only verified configured operator can moderate',async()=>{
  const id=await create();assert.equal((await call('report',{kind:'post',id,reason:'검토 요청'},second)).status,200);
  assert.equal((await call('moderation')).status,403);
  const auth={'oai-authenticated-user-id':'operator-id','oai-authenticated-user-email':'operator@example.test'};
  assert.equal((await call('moderation',null,visitor,auth)).status,200);
  assert.equal((await call('moderate',{kind:'post',id,action:'hide'},visitor,auth)).status,200);
  assert.equal((await call('media/'+id)).status,404);
  assert.equal((await call('moderation-media/'+id)).status,403);
  assert.equal((await call('moderation-media/'+id,null,visitor,auth)).status,200);
  assert.equal((await call('moderate',{kind:'post',id,action:'restore'},visitor,{'oai-authenticated-user-id':'impostor'})).status,403);
});
test('rate limits survive new visitor cookies and password guessing is bounded',async()=>{
  const id=await create();for(let i=0;i<15;i++)assert.equal((await call('manage',{kind:'post',id,action:'delete',password:'incorrect-password'},i.toString(16).padStart(64,'0'))).status,403);
  assert.equal((await call('manage',{kind:'post',id,action:'delete',password:'incorrect-password'},second)).status,429);
});
test('explicit consent, valid media, origin and cookie required before publish',async()=>{
  const f=form();f.set('consent','no');assert.equal((await call('posts',f)).status,400);
  assert.equal((await call('posts',form(),visitor,{origin:'https://evil.test'})).status,403);
  assert.equal((await call('posts',form(),'')).status,401);
  const bad=form();bad.set('file',new File(['<script>alert(1)</script>'],'a.jpg',{type:'image/jpeg'}));assert.equal((await call('posts',bad)).status,400);
});
test('media deletion failure stays invisible and is retried on next session',async()=>{
  const id=await create(),key=sqlite.prepare('SELECT media_key FROM posts WHERE id=?').get(id).media_key;
  const originalDelete=env.BUCKET.delete; env.BUCKET.delete=async()=>{throw new Error('storage unavailable');};
  assert.equal((await call('manage',{kind:'post',id,action:'delete',password:'my-only-post-password'})).status,200);
  assert.equal((await call('media/'+id)).status,404);assert.ok(await env.BUCKET.get(key));
  await call('session'); assert.equal(sqlite.prepare('SELECT media_key FROM posts WHERE id=?').get(id).media_key,key);
  env.BUCKET.delete=originalDelete;await call('session');
  assert.equal(await env.BUCKET.get(key),null);assert.equal(sqlite.prepare('SELECT media_key FROM posts WHERE id=?').get(id).media_key,'');
});
test('unset operator configuration revokes persisted operator immediately',async()=>{
  const auth={'oai-authenticated-user-id':'operator-id','oai-authenticated-user-email':'operator@example.test'};
  assert.equal((await call('moderation',null,visitor,auth)).status,200);env.ADMIN_EMAIL='';
  assert.equal((await call('moderation',null,visitor,auth)).status,403);
});
test('feed and comments cursors do not skip matching timestamps',async()=>{
  const id=await create();
  const base=sqlite.prepare('SELECT * FROM posts WHERE id=?').get(id);
  for(let i=0;i<24;i++)sqlite.prepare('INSERT INTO posts SELECT ?,author,nickname,body,salt,password_hash,media_key,mime,bytes,duration,created,state FROM posts WHERE id=?').run(crypto.randomUUID(),id);
  const first=await (await call('feed')).json(),secondPage=await (await call('feed?before='+first.next)).json();
  assert.equal(first.posts.length,20);assert.equal(secondPage.posts.length,5);assert.equal(new Set([...first.posts,...secondPage.posts].map(p=>p.id)).size,25);
  for(let i=0;i<61;i++)sqlite.prepare("INSERT INTO comments(id,post_id,author,nickname,body,salt,password_hash,created,state) VALUES(?,?,?,?,?,?,?,?,?)").run(crypto.randomUUID(),id,base.author,'익명','댓글 '+i,base.salt,base.password_hash,12345,'active');
  const a=await (await call('comments/'+id)).json(),b=await (await call('comments/'+id+'?after='+a.next)).json();
  assert.equal(a.comments.length,50);assert.equal(b.comments.length,11);assert.equal(new Set([...a.comments,...b.comments].map(c=>c.id)).size,61);
  assert.equal((await call('feed?before=invalid')).status,400);
});
test('operator can inspect, hide, restore and remove a reported comment',async()=>{
  const postId=await create(),id=crypto.randomUUID(),auth={'oai-authenticated-user-id':'operator-id','oai-authenticated-user-email':'operator@example.test'};
  await call('comments',{postId,id,body:'검토할 댓글',password:'comment-password',consent:'yes'});
  await call('report',{kind:'comment',id,reason:'검토 요청'},second);
  const report=(await (await call('moderation',null,visitor,auth)).json()).reports[0];
  assert.equal(report.body,'검토할 댓글');assert.equal(report.postId,postId);assert.equal(report.state,'active');
  for(const [action,state] of [['hide','hidden'],['restore','active'],['delete','deleted']]) {
    assert.equal((await call('moderate',{kind:'comment',id,action},visitor,auth)).status,200);
    assert.equal(sqlite.prepare('SELECT state FROM comments WHERE id=?').get(id).state,state);
  }
});
test('client retries do not duplicate a post or comment',async()=>{
  const f=form(),id=f.get('id');assert.equal((await call('posts',f)).status,201);assert.equal((await call('posts',f)).status,200);
  assert.equal((await call('posts',f,second)).status,409);assert.equal((await (await call('feed')).json()).posts.length,1);
  const data={postId:id,id:crypto.randomUUID(),body:'댓글',password:'comment-password',consent:'yes'};
  await call('comments',data);await call('comments',data);assert.equal((await (await call('comments/'+id)).json()).comments.length,1);
});

test('changed retry payload or password is rejected, including old objects without a fingerprint',async()=>{
  const f=form(),id=f.get('id');assert.equal((await call('posts',f)).status,201);
  for(const [key,value] of [['body','바뀐 설명'],['password','a-new-password'],['nickname','새 닉네임']]){
    const retry=form(id);retry.set(key,value);assert.equal((await call('posts',retry)).status,409);
  }
  const changed=form(id),jpeg=readFileSync(new URL('./fixtures/synthetic.jpg',import.meta.url));
  // A valid extra JPEG COM marker changes the content without corrupting the image.
  changed.set('file',new File([jpeg.subarray(0,2),new Uint8Array([255,254,0,4,65,66]),jpeg.subarray(2)],'photo.jpg',{type:'image/jpeg'}));
  assert.equal((await call('posts',changed)).status,409);
  const get=env.BUCKET.get;env.BUCKET.get=async(...args)=>({...await get(...args),customMetadata:undefined});
  assert.equal((await call('posts',form(id))).status,200);
  assert.equal((await (await call('post/'+id)).json()).post.body,'우리 아기의 오늘');
});
test('comment retries bind consent, parent, nickname, text and password',async()=>{
  const postId=await create(),other=await create(),data={postId,id:crypto.randomUUID(),body:'댓글',password:'comment-password',consent:'yes'};
  assert.equal((await call('comments',{...data,consent:'no'})).status,400);
  assert.equal((await call('comments',data)).status,201);
  for(const update of [{postId:other},{body:'바뀐 댓글'},{password:'new-password'},{nickname:'새 이름'}])
    assert.equal((await call('comments',{...data,...update})).status,409);
  assert.equal((await call('comments',data)).status,200);
});
test('detail fetch works outside first feed page and respects block, hidden and deleted states',async()=>{
  const id=await create();
  for(let i=0;i<21;i++)sqlite.prepare('INSERT INTO posts SELECT ?,author,nickname,body,salt,password_hash,media_key,mime,bytes,duration,created+?,state FROM posts WHERE id=?').run(crypto.randomUUID(),i+1,id);
  assert.ok(!(await (await call('feed')).json()).posts.some(p=>p.id===id));
  const detail=await (await call('post/'+id)).json();assert.equal(detail.post.id,id);
  assert.ok(!JSON.stringify(detail).includes('password_hash'));
  await call('block',{kind:'post',id},second);assert.equal((await call('post/'+id,null,second)).status,404);
  sqlite.prepare("UPDATE posts SET state='hidden' WHERE id=?").run(id);assert.equal((await call('post/'+id)).status,404);
  sqlite.prepare("UPDATE posts SET state='deleted' WHERE id=?").run(id);assert.equal((await call('post/'+id)).status,404);
});
for(const kind of ['post','comment'])test('deletion during password verification cannot repopulate '+kind,async()=>{
  const postId=await create(),id=kind==='post'?postId:crypto.randomUUID();
  if(kind==='comment')await call('comments',{postId,id,body:'원래 댓글',password:'comment-password',consent:'yes'});
  const original=env.DB.prepare;let captured=false,release,signal;
  const gate=new Promise(r=>release=r),selected=new Promise(r=>signal=r);
  function wrapped(sql,args=[]){
    const real=original(sql).bind(...args);
    return {...real,bind:(...v)=>wrapped(sql,v),first:async()=>{
      const row=await real.first();
      if(!captured&&sql===`SELECT * FROM ${kind==='post'?'posts':'comments'} WHERE id=? AND state!='deleted'`){captured=true;signal();await gate;}
      return row;
    }};
  }
  env.DB.prepare=sql=>wrapped(sql);
  const pending=call('manage',{kind,id,action:'edit',body:'늦게 도착한 수정',password:kind==='post'?'my-only-post-password':'comment-password'});
  await selected;
  assert.equal((await call('manage',{kind:'post',id:postId,action:'delete',password:'my-only-post-password'})).status,200);
  release();assert.equal((await pending).status,404);
  const row=sqlite.prepare(`SELECT body,state FROM ${kind==='post'?'posts':'comments'} WHERE id=?`).get(id);
  assert.equal(row.body,'');assert.equal(row.state,'deleted');
});
