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
    BUCKET:{put:async(k,v)=>objects.set(k,new Uint8Array(v)),get:async k=>objects.has(k)?{body:objects.get(k),size:objects.get(k).length}:null,delete:async k=>objects.delete(k)},RATE_SECRET:'test-secret',ADMIN_EMAIL:'operator@example.test'};
});
afterEach(()=>sqlite.close());
const visitor='a'.repeat(64),second='b'.repeat(64);
async function call(path,data,who=visitor,extra={}) {
  return handleBoard(new Request('https://board.test/api/'+path,{method:data?'POST':'GET',
    headers:{cookie:'agattok_visitor='+who,origin:'https://board.test','x-board-client':'1',...(data && !(data instanceof FormData)?{'Content-Type':'application/json'}:{}),...extra},
    body:data instanceof FormData?data:data?JSON.stringify(data):undefined}),env);
}
function form(id=crypto.randomUUID()) {
  const jpeg=new Uint8Array(25);jpeg.set([255,216,255,218]);jpeg.set([255,217],23);
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
  assert.equal((await call('comments',{postId,id,body:'귀여워요',password:'comment-password'},second)).status,201);
  assert.equal((await call('manage',{kind:'comment',id,action:'delete',password:'my-only-post-password'})).status,403);
  assert.equal((await call('manage',{kind:'comment',id,action:'edit',password:'comment-password',body:'정말 귀여워요'})).status,200);
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
  await call('comments',{postId,id,body:'검토할 댓글',password:'comment-password'});
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
  const data={postId:id,id:crypto.randomUUID(),body:'댓글',password:'comment-password'};
  await call('comments',data);await call('comments',data);assert.equal((await (await call('comments/'+id)).json()).comments.length,1);
});
