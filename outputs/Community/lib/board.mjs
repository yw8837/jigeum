import { BoardError, need, textField, passwordField, randomToken, digest, hashPassword, checkPassword, cookieToken, sameOrigin, mediaInfo, stripMovieMetadata, MAX_MEDIA } from './security.mjs';
const json = (value, status = 200, extra = {}) => Response.json(value, {status, headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff','Referrer-Policy':'no-referrer',...extra}});
const bytesDigest = async bytes => Array.from(new Uint8Array(await crypto.subtle.digest('SHA-256', bytes)), b => b.toString(16).padStart(2,'0')).join('');
const retryConflict = '이 요청의 이전 내용이 이미 게시됐어요. 목록에서 해당 글·댓글을 확인한 뒤 수정·삭제해 주세요.';
const pageCursor = value => { if(!value)return null; const [time,id]=value.split(':'); need(/^\d{1,16}$/.test(time)&&Number.isSafeInteger(Number(time)), '페이지 정보가 올바르지 않아요.'); return {time:Number(time),id:uuid(id)}; };
const uuid = value => { need(/^[a-f0-9]{8}-[a-f0-9]{4}-4[a-f0-9]{3}-[89ab][a-f0-9]{3}-[a-f0-9]{12}$/.test(value || ''), '요청 번호가 올바르지 않아요.'); return value; };
async function boundedBody(request, maximum) {
  need(Number(request.headers.get('content-length') || 0) <= maximum, '파일 용량이 너무 커요.', 413);
  const reader = request.body?.getReader(); need(reader, '내용이 비어 있어요.');
  const chunks = []; let size = 0;
  try { while (true) { const {done,value}=await reader.read(); if(done)break; size+=value.length; need(size<=maximum,'파일 용량이 너무 커요.',413); chunks.push(value); } }
  finally { await reader.cancel(); }
  const bytes=new Uint8Array(size); let at=0; for(const c of chunks){bytes.set(c,at);at+=c.length;} return bytes;
}
const stmt=(db,sql,...args)=>db.prepare(sql).bind(...args);
const one=(db,sql,...args)=>stmt(db,sql,...args).first();
const run=(db,sql,...args)=>stmt(db,sql,...args).run();
const all=async(db,sql,...args)=>(await stmt(db,sql,...args).all()).results;
async function limit(db,key,max,seconds,now) {
  const bucket=Math.floor(now/(seconds*1000));
  const result=await one(db,'INSERT INTO rate_limits (key,count,expires) VALUES (?,1,?) ON CONFLICT(key) DO UPDATE SET count=count+1 RETURNING count',key+':'+bucket,(bucket+1)*seconds*1000);
  need(result.count<=max,'요청이 많아요. 잠시 후 다시 시도해 주세요.',429);
}
async function admin(request,env,db) {
  const id=request.headers.get('oai-authenticated-user-id'),email=request.headers.get('oai-authenticated-user-email');
  if(!id || !env.ADMIN_EMAIL?.trim())return false;
  // Bootstrap only the server-configured operator's platform-verified identity; thereafter bind the stable ID.
  if(env.ADMIN_EMAIL && email?.toLowerCase()===env.ADMIN_EMAIL.toLowerCase())
    await run(db,"INSERT OR IGNORE INTO settings (key,value) VALUES ('operator',?)",id);
  return (await one(db,"SELECT value FROM settings WHERE key='operator'"))?.value===id;
}
async function visiblePost(db,id,visitor) {
  const row=await one(db,"SELECT * FROM posts WHERE id=? AND state='active' AND NOT EXISTS(SELECT 1 FROM blocks WHERE visitor=? AND author=posts.author)",id,visitor);
  need(row,'이 게시글을 볼 수 없어요.',404); return row;
}
function publicPost(p) {
  return {id:p.id,nickname:p.nickname,body:p.body,created:p.created,mime:p.mime,duration:p.duration,
    media:'/api/media/'+p.id,liked:!!p.liked,likes:p.likes||0,comments:p.comments||0,state:p.state};
}
async function removePost(db,bucket,id) {
  const row=await one(db,'SELECT media_key FROM posts WHERE id=?',id);
  await db.batch([
    stmt(db,"UPDATE posts SET state='deleted',body='',nickname='삭제됨',password_hash='',salt='' WHERE id=?",id),
    stmt(db,"UPDATE comments SET state='deleted',body='',nickname='삭제됨',password_hash='',salt='' WHERE post_id=?",id),
    stmt(db,'DELETE FROM likes WHERE post_id=?',id)
  ]);
  // Tombstone commits first: a storage failure cannot leave deleted content publicly readable.
  if(row?.media_key) {
    try { await bucket.delete(row.media_key); await run(db,"UPDATE posts SET media_key='',bytes=0 WHERE id=? AND state='deleted'",id); }
    catch { /* Keep the key on the inaccessible tombstone for the next cleanup attempt. */ }
  }
}
async function retryMediaDeletion(db,bucket) {
  const pending=await all(db,"SELECT id,media_key FROM posts WHERE state='deleted' AND media_key!='' LIMIT 5");
  for(const row of pending) {
    try { await bucket.delete(row.media_key); await run(db,"UPDATE posts SET media_key='',bytes=0 WHERE id=? AND state='deleted'",row.id); }
    catch { /* Retain pending key; never restore public visibility after a storage error. */ }
  }
}
export async function handleBoard(request,env) {
  try {
    need(env.DB && env.BUCKET,'게시판 연결을 준비하고 있어요.',503);
    const db=env.DB,url=new URL(request.url),parts=url.pathname.split('/').filter(Boolean).slice(1);
    const token=cookieToken(request),visitor=token?await digest(token):'',now=Date.now();
    if(request.method==='GET' && ['session','moderation'].includes(parts[0])) await retryMediaDeletion(db,env.BUCKET);
    if(request.method==='GET' && parts[0]==='session') {
      const newToken=token||randomToken(),isAdmin=await admin(request,env,db);
      return json({admin:isAdmin,ready:true},200,token?{}:{'Set-Cookie':`agattok_visitor=${newToken}; Path=/; HttpOnly; SameSite=Strict; Max-Age=31536000${url.protocol==='https:'?'; Secure':''}`});
    }
    if(request.method==='GET' && parts[0]==='feed') {
      const filter=url.searchParams.get('type')||'all';
      need(['all','video','photo'].includes(filter),'게시물 종류를 확인해 주세요.');
      const cursor=pageCursor(url.searchParams.get('before')),before=cursor?.time??Number.MAX_SAFE_INTEGER,beforeId=cursor?.id||'~';
      const rows=await all(db,`SELECT p.*,(SELECT COUNT(*) FROM likes WHERE post_id=p.id) likes,
        EXISTS(SELECT 1 FROM likes WHERE post_id=p.id AND visitor=?) liked,
        (SELECT COUNT(*) FROM comments c WHERE c.post_id=p.id AND c.state='active' AND NOT EXISTS(SELECT 1 FROM blocks WHERE visitor=? AND author=c.author)) comments
        FROM posts p WHERE p.state='active' AND (p.created<? OR (p.created=? AND p.id<?)) AND NOT EXISTS(SELECT 1 FROM blocks WHERE visitor=? AND author=p.author)
        AND (?='all' OR (?='video' AND p.mime='video/mp4') OR (?='photo' AND p.mime='image/jpeg'))
        ORDER BY p.created DESC,p.id DESC LIMIT 20`,visitor,visitor,before,before,beforeId,visitor,filter,filter,filter);
      return json({posts:rows.map(publicPost),next:rows.length===20?rows.at(-1).created+':'+rows.at(-1).id:null});
    }
    if(request.method==='GET' && ['media','moderation-media'].includes(parts[0])) {
      let row;
      if(parts[0]==='moderation-media') {
        need(await admin(request,env,db),'운영자 로그인이 필요해요.',403);
        row=await one(db,"SELECT * FROM posts WHERE id=? AND state!='deleted'",uuid(parts[1]));
        need(row,'이 게시글을 볼 수 없어요.',404);
      } else row=await visiblePost(db,uuid(parts[1]),visitor);
      const object=await env.BUCKET.get(row.media_key,{range:request.headers});
      need(object,'파일을 찾을 수 없어요.',404);
      const headers={'Content-Type':row.mime,'Cache-Control':'private, no-store','X-Content-Type-Options':'nosniff','Content-Disposition':'inline','Accept-Ranges':'bytes','Referrer-Policy':'no-referrer'};
      if(object.range && 'offset' in object.range){const r=object.range;headers['Content-Range']=`bytes ${r.offset}-${r.offset+r.length-1}/${object.size}`;headers['Content-Length']=String(r.length);return new Response(object.body,{status:206,headers});}
      headers['Content-Length']=String(object.size);return new Response(object.body,{headers});
    }
    if(request.method==='GET' && parts[0]==='post') {
      const p=await visiblePost(db,uuid(parts[1]),visitor);
      const counts=await one(db,"SELECT (SELECT COUNT(*) FROM likes WHERE post_id=?) likes, EXISTS(SELECT 1 FROM likes WHERE post_id=? AND visitor=?) liked, (SELECT COUNT(*) FROM comments c WHERE c.post_id=? AND c.state='active' AND NOT EXISTS(SELECT 1 FROM blocks WHERE visitor=? AND author=c.author)) comments",p.id,p.id,visitor,p.id,visitor);
      return json({post:publicPost({...p,...counts})});
    }
    if(request.method==='GET' && parts[0]==='comments') {
      const post=await visiblePost(db,uuid(parts[1]),visitor);
      const cursor=pageCursor(url.searchParams.get('after')),after=cursor?.time||Number.MAX_SAFE_INTEGER,afterId=cursor?.id||'~';
      const rows=await all(db,"SELECT id,nickname,body,created FROM comments c WHERE post_id=? AND state='active' AND (created<? OR (created=? AND id<?)) AND NOT EXISTS(SELECT 1 FROM blocks WHERE visitor=? AND author=c.author) ORDER BY created DESC,id DESC LIMIT 50",post.id,after,after,afterId,visitor);
      return json({comments:rows,next:rows.length===50?rows.at(-1).created+':'+rows.at(-1).id:null});
    }
    if(request.method==='GET' && parts[0]==='blocks') {
      return json({blocks:await all(db,'SELECT author,created FROM blocks WHERE visitor=? ORDER BY created DESC',visitor)});
    }
    if(request.method==='GET' && parts[0]==='moderation') {
      need(await admin(request,env,db),'운영자 로그인이 필요해요.',403);
      return json({hiddenComments:await all(db,"SELECT c.id,c.post_id postId,c.body,c.nickname,c.created FROM comments c JOIN posts p ON p.id=c.post_id WHERE c.state='hidden' AND p.state!='deleted' ORDER BY c.created LIMIT 100"),pendingMediaDeletes:(await one(db,"SELECT COUNT(*) total FROM posts WHERE state='deleted' AND media_key!=''")).total,reports:await all(db,"SELECT r.id,r.kind,r.target,r.reason,r.created,CASE WHEN r.kind='post' THEN p.body ELSE c.body END body,CASE WHEN r.kind='post' THEN p.state ELSE c.state END state,CASE WHEN r.kind='post' THEN p.id ELSE c.post_id END postId,p.mime FROM reports r LEFT JOIN posts p ON r.kind='post' AND p.id=r.target LEFT JOIN comments c ON r.kind='comment' AND c.id=r.target WHERE r.resolved=0 ORDER BY r.created LIMIT 100"),
        posts:(await all(db,"SELECT * FROM posts WHERE state!='deleted' ORDER BY created DESC LIMIT 100")).map(publicPost)});
    }
    need(request.method==='POST','지원하지 않는 요청이에요.',405);
    sameOrigin(request); need(token,'화면을 새로고침한 뒤 다시 시도해 주세요.',401);
    const loopback=['localhost','127.0.0.1'].includes(url.hostname);
    need(env.RATE_SECRET || loopback,'게시판 연결을 준비하고 있어요.',503);
    const originHash=await digest((env.RATE_SECRET||'local-only')+':'+(request.headers.get('cf-connecting-ip')||'unknown')+':'+Math.floor(now/86400000));
    await limit(db,'origin:'+originHash,120,3600,now); await limit(db,'visitor:'+visitor,60,60,now);
    await run(db,'DELETE FROM rate_limits WHERE expires<?',now-3600000);
    if(parts[0]==='posts' && parts.length===1) {
      await limit(db,'post:'+visitor,6,3600,now); await limit(db,'upload:'+originHash,20,3600,now);
      const bytes=await boundedBody(request,MAX_MEDIA+16384);
      const form=await new Response(bytes,{headers:{'Content-Type':request.headers.get('content-type')||''}}).formData();
      need(form.get('consent')==='yes','공개 공유 동의를 확인해 주세요.');
      const id=uuid(form.get('id')),existing=await one(db,'SELECT * FROM posts WHERE id=?',id);
      const nickname=textField(form.get('nickname')||'익명',20),body=textField(form.get('body'),1000),pw=passwordField(form.get('password'));
      const file=form.get('file');need(file instanceof File,'사진이나 영상카드를 골라 주세요.');
      const media=new Uint8Array(await file.arrayBuffer()),info=mediaInfo(media);
      const clean=info.mime==='video/mp4'?stripMovieMetadata(media):media, fingerprint=await bytesDigest(clean);
      if(existing){
        need(existing.author===visitor&&existing.state==='active','같은 요청을 다시 사용할 수 없어요.',409);
        need(existing.nickname===nickname&&existing.body===body,retryConflict,409);
        try{await checkPassword(pw,existing);}catch{throw new BoardError(409,retryConflict);}
        const stored=await env.BUCKET.get(existing.media_key);need(stored,'파일을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.',503);
        const previous=stored.customMetadata?.sha256||await bytesDigest(await stored.arrayBuffer());
        need(previous===fingerprint,retryConflict,409);return json({id});
      }
      const hashed=await hashPassword(pw),key=crypto.randomUUID()+'.'+info.ext;
      await env.BUCKET.put(key,clean,{httpMetadata:{contentType:info.mime},customMetadata:{sha256:fingerprint}});
      try {
        await run(db,'INSERT INTO posts (id,author,nickname,body,salt,password_hash,media_key,mime,bytes,duration,created,state) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)',id,visitor,nickname,body,hashed.salt,hashed.hash,key,info.mime,media.length,Math.round(info.duration*1000),now,'active');
      } catch(error) { await env.BUCKET.delete(key); throw error; }
      return json({id},201);
    }
    const raw=await boundedBody(request,16384);let data;
    try{data=JSON.parse(new TextDecoder().decode(raw));}catch{throw new BoardError(400,'입력 내용을 확인해 주세요.');}
    need(data&&typeof data==='object'&&!Array.isArray(data),'입력 내용을 확인해 주세요.');
    if(parts[0]==='comments' && parts.length===1) {
      await limit(db,'comment:'+visitor,30,3600,now);
      need(data.consent==='yes','게시판 이용규칙과 공개 댓글 동의를 확인해 주세요.');
      const p=await visiblePost(db,uuid(data.postId),visitor),id=uuid(data.id);
      const nickname=textField(data.nickname||'익명',20),body=textField(data.body,500),pw=passwordField(data.password);
      const existing=await one(db,'SELECT * FROM comments WHERE id=?',id);
      if(existing){
        need(existing.author===visitor&&existing.state==='active','같은 요청을 다시 사용할 수 없어요.',409);
        need(existing.post_id===p.id&&existing.nickname===nickname&&existing.body===body,retryConflict,409);
        try{await checkPassword(pw,existing);}catch{throw new BoardError(409,retryConflict);}
        return json({id});
      }
      const hashed=await hashPassword(pw);
      await run(db,"INSERT INTO comments(id,post_id,author,nickname,body,salt,password_hash,created,state) SELECT ?,?,?,?,?,?,?,?,'active' WHERE EXISTS(SELECT 1 FROM posts WHERE id=? AND state='active')",id,p.id,visitor,nickname,body,hashed.salt,hashed.hash,now,p.id);
      return json({id},201);
    }
    if(parts[0]==='like') {
      const p=await visiblePost(db,uuid(data.postId),visitor); need(typeof data.liked==='boolean','좋아요 상태를 확인해 주세요.');
      if(data.liked)await run(db,"INSERT OR IGNORE INTO likes(post_id,visitor) SELECT ?,? WHERE EXISTS(SELECT 1 FROM posts WHERE id=? AND state='active')",p.id,visitor,p.id);
      else await run(db,'DELETE FROM likes WHERE post_id=? AND visitor=?',p.id,visitor);
      return json({liked:data.liked,count:(await one(db,'SELECT COUNT(*) total FROM likes WHERE post_id=?',p.id)).total});
    }
    if(parts[0]==='manage') {
      need(['post','comment'].includes(data.kind),'대상을 확인해 주세요.'); const table=data.kind==='post'?'posts':'comments',id=uuid(data.id);
      await limit(db,'password:'+originHash,15,3600,now);
      const row=await one(db,`SELECT * FROM ${table} WHERE id=? AND state!='deleted'`,id);need(row,'이미 삭제된 글이에요.',404);
      await checkPassword(data.password,row);
      if(data.action==='delete') {
        if(data.kind==='post')await removePost(db,env.BUCKET,id);
        else await run(db,"UPDATE comments SET state='deleted',body='',nickname='삭제됨',salt='',password_hash='' WHERE id=?",id);
      } else {need(data.action==='edit','작업을 확인해 주세요.');const changed=await run(db,`UPDATE ${table} SET body=? WHERE id=? AND state!='deleted'${data.kind==='comment'?" AND EXISTS(SELECT 1 FROM posts WHERE posts.id=comments.post_id AND posts.state!='deleted')":''}`,textField(data.body,data.kind==='post'?1000:500),id);need(changed.meta.changes>0,'이미 삭제된 글이에요.',404);}
      return json({ok:true});
    }
    if(parts[0]==='report'||parts[0]==='block') {
      need(['post','comment'].includes(data.kind),'대상을 확인해 주세요.');const table=data.kind==='post'?'posts':'comments',id=uuid(data.id);
      const row=await one(db,`SELECT author FROM ${table} WHERE id=? AND state='active'`,id);need(row,'이 글을 찾을 수 없어요.',404);
      if(parts[0]==='block')await run(db,'INSERT OR IGNORE INTO blocks(visitor,author,created) VALUES (?,?,?)',visitor,row.author,now);
      else {await limit(db,'report:'+visitor,20,3600,now);await run(db,'INSERT INTO reports(id,kind,target,visitor,reason,created) VALUES (?,?,?,?,?,?)',crypto.randomUUID(),data.kind,id,visitor,textField(data.reason,300),now);}
      return json({ok:true});
    }
    if(parts[0]==='unblock') {await run(db,'DELETE FROM blocks WHERE visitor=? AND author=?',visitor,textField(data.author,64));return json({ok:true});}
    if(parts[0]==='moderate') {
      need(await admin(request,env,db),'운영자 로그인이 필요해요.',403);
      if(data.action==='resolve')await run(db,'UPDATE reports SET resolved=1 WHERE id=?',uuid(data.id));
      else {
        need(['post','comment'].includes(data.kind),'대상을 확인해 주세요.');const table=data.kind==='post'?'posts':'comments',id=uuid(data.id);
        if(data.action==='delete' && data.kind==='post')await removePost(db,env.BUCKET,id);
        else {need(['hide','restore','delete'].includes(data.action),'작업을 확인해 주세요.');
          if(data.action==='delete')await run(db,"UPDATE comments SET state='deleted',body='',nickname='삭제됨',salt='',password_hash='' WHERE id=?",id);
          else await run(db,`UPDATE ${table} SET state=? WHERE id=? AND state!='deleted'`,data.action==='hide'?'hidden':'active',id);
        }
      }
      return json({ok:true});
    }
    throw new BoardError(404,'요청을 찾을 수 없어요.');
  } catch(error) {
    // Never log request bodies, cookies, password hashes, private media or IP addresses.
    return json({error:error instanceof BoardError?error.message:'처리하지 못했어요. 내용은 그대로 두고 다시 시도해 주세요.'},error instanceof BoardError?error.status:500);
  }
}
