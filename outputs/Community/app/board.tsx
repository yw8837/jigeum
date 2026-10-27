'use client';
import { useCallback, useEffect, useRef, useState, type ReactNode } from 'react';
import { stripMovieMetadata } from '../lib/security.mjs';
import { bindDialogHistory } from '../lib/dialog-history.mjs';
import { Heart, MessageCircle, Plus, ArrowLeft, X, ImagePlus, Play, ShieldCheck, MoreHorizontal, Sparkles, LockKeyhole } from 'lucide-react';
type Post={id:string;nickname:string;body:string;created:number;mime:string;duration:number;media:string;liked:boolean;likes:number;comments:number;state:string};
type Comment={id:string;nickname:string;body:string;created:number};
type Target={kind:'post'|'comment';id:string;body:string};
type Block={author:string;created:number};
type Report={id:string;kind:'post'|'comment';target:string;reason:string;created:number;body:string;state:string;postId:string;mime?:string};
async function api(path:string,data?:object|FormData) {
  const controller=new AbortController(),timer=setTimeout(()=>controller.abort(),90000);
  try {
    const r=await fetch('/api/'+path,{signal:controller.signal,method:data?'POST':'GET',credentials:'same-origin',cache:'no-store',headers:data?{'x-board-client':'1',...(data instanceof FormData?{}:{'Content-Type':'application/json'})}:{},body:data instanceof FormData?data:data?JSON.stringify(data):undefined});
    const value=await r.json().catch(()=>{throw new Error('서버 응답을 확인하지 못했어요. 잠시 후 다시 시도해 주세요.');}) as {error?:string;admin:boolean;post:Post;posts:Post[];comments:Comment[];blocks:Block[];reports:Report[];hiddenComments:Comment[];pendingMediaDeletes:number;next:string|null;liked:boolean;count:number};
    if(!r.ok)throw new Error(value.error||'잠시 후 다시 시도해 주세요.');return value;
  } catch(error) {
    if(controller.signal.aborted)throw new Error('연결이 오래 걸려 응답을 확인하지 못했어요. 입력한 내용은 유지돼요. 연결을 확인하고 다시 시도해 주세요.');
    if(error instanceof TypeError)throw new Error('인터넷 연결을 확인해 주세요. 입력한 내용은 유지돼요. 연결되면 다시 시도할 수 있어요.');
    throw error;
  } finally {clearTimeout(timer);}
}
const date=(time:number)=>new Intl.DateTimeFormat('ko-KR',{month:'long',day:'numeric',hour:'2-digit',minute:'2-digit'}).format(time);
function useDraftGuard(active:boolean) {
  useEffect(()=>{if(!active)return;const warn=(event:BeforeUnloadEvent)=>{event.preventDefault();event.returnValue='';};window.addEventListener('beforeunload',warn);return()=>window.removeEventListener('beforeunload',warn);},[active]);
}
function Modal({title,onClose,children}:{title:string;onClose:()=>void|boolean;children:ReactNode}) {
  const ref=useRef<HTMLDialogElement>(null),closeRef=useRef(onClose);closeRef.current=onClose;
  useEffect(()=>{const d=ref.current;let active=true,unbind:(()=>void)|undefined;d?.showModal();queueMicrotask(()=>{if(active)unbind=bindDialogHistory(window,()=>closeRef.current());});return()=>{active=false;unbind?.();d?.close();};},[]);
  return <dialog ref={ref} onCancel={e=>{e.preventDefault();onClose();}} className="modal"><header><h2>{title}</h2><button aria-label="닫기" className="icon" onClick={onClose}><X size={22}/></button></header>{children}</dialog>;
}
function Media({src,video}:{src:string;video:boolean}) {
  const [failed,setFailed]=useState(false);
  useEffect(()=>setFailed(false),[src]);
  return <>{video?<video src={src} controls playsInline preload="metadata" onPlay={e=>{document.querySelectorAll('video').forEach(v=>{if(v!==e.currentTarget)v.pause();});}} onError={()=>setFailed(true)}/>:<img src={src} alt="보호자가 공유한 우리 아이의 순간" loading="lazy" onError={()=>setFailed(true)}/>}
    {failed&&<p role="alert" className="error">파일을 표시하지 못했어요. 연결을 확인하고 새로고침해 주세요.</p>}</>;
}
async function prepareFile(file:File) {
  if(file.size>25*1024*1024)throw new Error('사진·영상은 25MB 이하로 골라 주세요.');
  if(file.type.startsWith('image/')) {
    const bitmap=await createImageBitmap(file);
    try {const scale=Math.min(1,1600/Math.max(bitmap.width,bitmap.height)),canvas=document.createElement('canvas');
      canvas.width=Math.round(bitmap.width*scale);canvas.height=Math.round(bitmap.height*scale);const ctx=canvas.getContext('2d');if(!ctx)throw new Error('사진을 준비하지 못했어요.');
      ctx.fillStyle='#fff';ctx.fillRect(0,0,canvas.width,canvas.height);ctx.drawImage(bitmap,0,0,canvas.width,canvas.height);
      const blob=await new Promise<Blob>((resolve,reject)=>canvas.toBlob(b=>b?resolve(b):reject(new Error('사진을 준비하지 못했어요.')),'image/jpeg',0.9));
      return new File([blob],'agattok-photo.jpg',{type:'image/jpeg'});
    } finally {bitmap.close();}
  }
  if(file.type!=='video/mp4')throw new Error('사진 또는 MP4 영상카드를 골라 주세요.');
  const src=URL.createObjectURL(file);
  try {await new Promise<void>((resolve,reject)=>{const video=document.createElement('video'),timer=setTimeout(()=>finish(new Error('영상을 읽지 못했어요.')),10000);
    function finish(error?:Error){clearTimeout(timer);video.removeAttribute('src');video.load();error?reject(error):resolve();}
    video.preload='metadata';video.onloadedmetadata=()=>finish(!Number.isFinite(video.duration)||video.duration>30.1?new Error('영상은 30초 이내로 골라 주세요.'):undefined);
    video.onerror=()=>finish(new Error('재생할 수 있는 MP4 영상을 골라 주세요.'));video.src=src;
  });const clean=stripMovieMetadata(new Uint8Array(await file.arrayBuffer()));return new File([clean],'agattok-video.mp4',{type:'video/mp4'});}finally{URL.revokeObjectURL(src);}
}
function Composer({onClose,onPosted}:{onClose:()=>void;onPosted:()=>void}) {
  const [file,setFile]=useState<File|null>(null),[preview,setPreview]=useState(''),[busy,setBusy]=useState(false),[uploading,setUploading]=useState(false),[error,setError]=useState('');
  const [body,setBody]=useState(''),[nickname,setNickname]=useState(''),[password,setPassword]=useState(''),[repeat,setRepeat]=useState(''),[consent,setConsent]=useState(false);
  const id=useRef(crypto.randomUUID()),selection=useRef(0);
  useEffect(()=>{if(!file){setPreview('');return;}const u=URL.createObjectURL(file);setPreview(u);return()=>URL.revokeObjectURL(u);},[file]);
  useDraftGuard(!!(body||file||password)||busy);
  const close=()=>{if(busy)return false;if((body||file||password)&&!confirm('아직 게시하지 않은 내용을 닫을까요?'))return false;onClose();};
  async function select(f?:File){if(!f)return;const seq=++selection.current;setBusy(true);setError('');try{const prepared=await prepareFile(f);if(seq===selection.current){setFile(prepared);}}catch(e){setError((e as Error).message);}finally{if(seq===selection.current)setBusy(false);}}
  async function publish(){setError('');if(password!==repeat){setError('관리 비밀번호를 두 번 똑같이 입력해 주세요.');return;}if(!file||!consent)return;
    setBusy(true);setUploading(true);try{const f=new FormData();for(const [k,v]of Object.entries({id:id.current,nickname:nickname||'익명',body,password,consent:'yes'}))f.set(k,v);f.set('file',file);await api('posts',f);onPosted();}
    catch(e){setError((e as Error).message);}finally{setBusy(false);setUploading(false);}}
  return <Modal title="우리 아이 자랑하기" onClose={close}><form onSubmit={e=>{e.preventDefault();void publish();}}>
    <p className="muted">사진 한 장, 또는 아가말 영상카드로 오늘의 순간을 나눠요.</p>
    {preview&&<div className="preview"><Media src={preview} video={file?.type==='video/mp4'}/><span className="preview-label">공개 전 미리보기</span></div>}
    <label className="file-pick"><ImagePlus size={24}/><span>{file?'다른 사진·영상 고르기':'사진·영상카드 고르기'}<small>사진 또는 MP4 · 최대 30초 / 25MB</small></span><input type="file" accept="image/*,video/mp4" disabled={busy} onChange={e=>void select(e.target.files?.[0])}/></label>
    <label>함께 나눌 이야기<textarea required maxLength={1000} rows={4} placeholder="오늘 어떤 귀여운 순간이 있었나요?" value={body} onChange={e=>setBody(e.target.value)} disabled={busy}/></label>
    <label>닉네임 <span className="optional">선택 · 비워 두면 익명</span><input maxLength={20} autoComplete="off" value={nickname} onChange={e=>setNickname(e.target.value)} disabled={busy}/></label>
    <div className="passwords"><label>관리 비밀번호<input required type="password" minLength={8} maxLength={64} autoComplete="new-password" value={password} onChange={e=>setPassword(e.target.value)} disabled={busy}/></label><label>비밀번호 확인<input required type="password" minLength={8} maxLength={64} autoComplete="new-password" value={repeat} onChange={e=>setRepeat(e.target.value)} disabled={busy}/></label></div>
    <p className="hint"><LockKeyhole size={15}/> 8~64자. 다른 서비스 비밀번호는 피해주세요. 수정·삭제할 때 필요하며 분실 시 자동 복구할 수 없어요.</p>
    <label className="check"><input required type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)} disabled={busy}/><span>아래 게시판 이용규칙과 개인정보 안내에 동의하며 공유할 권리가 있는 자료입니다. 영상 속 소리·얼굴과 설명이 다른 사람에게 공개되는 데 동의해요. 아동의 사생활을 해치는 장면은 올리지 않아요.</span></label>
    <details className="policy"><summary>게시판 이용규칙과 개인정보 안내</summary><Policy/></details>
    <p className="hint">위 파일과 설명만 게시돼요. 아가톡의 개인 기록은 전송하지 않아요. 게시물은 누구나 열람할 수 있어요.</p>
    {error&&<p role="alert" className="error">{error}</p>}<button className="primary full" disabled={busy||!file||!consent}>{busy?(uploading?'올리는 중이에요…':'미리보기를 준비하고 있어요…'):'확인한 내용 공개하기'}</button>
  </form></Modal>;
}
function Manage({target,onClose,onChanged}:{target:Target;onClose:()=>void;onChanged:(action:string)=>void}) {
  const [body,setBody]=useState(target.body),[password,setPassword]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  async function save(action:string){if(action==='delete'&&!confirm('삭제하면 되돌릴 수 없어요. 삭제할까요?'))return;setBusy(true);setError('');try{await api('manage',{...target,body,password,action});onChanged(action);}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
  useDraftGuard(body!==target.body||busy);
  return <Modal title={target.kind==='post'?'게시글 수정·삭제':'댓글 수정·삭제'} onClose={()=>{if(busy)return false;if(body!==target.body&&!confirm('수정한 내용을 저장하지 않고 닫을까요?'))return false;onClose();}}><form onSubmit={e=>{e.preventDefault();void save('edit');}}>
    <label>내용<textarea required rows={4} maxLength={target.kind==='post'?1000:500} value={body} onChange={e=>setBody(e.target.value)} disabled={busy}/></label>
    <label>작성할 때 설정한 비밀번호<input required type="password" minLength={8} maxLength={64} value={password} onChange={e=>setPassword(e.target.value)} autoComplete="current-password" disabled={busy}/></label>
    <p className="hint">비밀번호를 잊었다면 게시글 신고 또는 운영자 문의를 이용해 주세요.</p>{error&&<p role="alert" className="error">{error}</p>}
    <div className="actions"><button type="button" className="danger" disabled={busy||password.length<8} onClick={()=>void save('delete')}>삭제하기</button><button className="primary" disabled={busy}>수정 저장</button></div>
  </form></Modal>;
}
function CommentPanel({post,onManage,onReport,onBlock,onChanged,onDraftChange,revision}:{post:Post;onManage:(t:Target)=>void;onReport:(t:Target)=>void;onBlock:(t:Target)=>void;onChanged:()=>void;onDraftChange:(dirty:boolean)=>void;revision:number}) {
  const [comments,setComments]=useState<Comment[]>([]),[nextComment,setNextComment]=useState<string|null>(null),[body,setBody]=useState(''),[nickname,setNickname]=useState(''),[password,setPassword]=useState(''),[busy,setBusy]=useState(false),[error,setError]=useState('');
  const [loadingComments,setLoadingComments]=useState(false),[loadError,setLoadError]=useState(''),[consent,setConsent]=useState(false);
  const id=useRef(crypto.randomUUID()),loadSequence=useRef(0),loadingRef=useRef(false);
  const load=useCallback(async(after?:string)=>{if(after&&loadingRef.current)return;const seq=++loadSequence.current;loadingRef.current=true;setLoadingComments(true);setLoadError('');try{const data=await api('comments/'+post.id+(after?'?after='+encodeURIComponent(after):''));if(seq!==loadSequence.current)return;setComments(old=>after?[...old,...data.comments.filter(c=>!old.some(existing=>existing.id===c.id))]:data.comments);setNextComment(data.next);}catch(e){if(seq===loadSequence.current)setLoadError((e as Error).message);}finally{if(seq===loadSequence.current){loadingRef.current=false;setLoadingComments(false);}}},[post.id]);
  useEffect(()=>{void load();return()=>{loadSequence.current++;loadingRef.current=false;};},[load,revision]);
  useDraftGuard(!!body||busy);
  useEffect(()=>{onDraftChange(!!body||busy);},[body,busy,onDraftChange]);
  useEffect(()=>()=>onDraftChange(false),[onDraftChange]);
  async function submit(){if(!consent)return;setBusy(true);setError('');try{await api('comments',{id:id.current,postId:post.id,body,nickname:nickname||'익명',password,consent:'yes'});setBody('');setPassword('');id.current=crypto.randomUUID();await load();onChanged();}catch(e){setError((e as Error).message);}finally{setBusy(false);}}
  return <section className="comments" aria-label="댓글"><h3>따뜻한 한마디 <small>최신순</small></h3>{loadingComments&&comments.length===0&&<p role="status" className="muted">댓글을 불러오고 있어요…</p>}{comments.length===0&&!loadingComments&&!loadError&&<p className="muted">첫 응원을 남겨 주세요.</p>}{loadError&&<p role="alert" className="error">{loadError} <button type="button" disabled={loadingComments} onClick={()=>void load()}>댓글 다시 불러오기</button></p>}
    {comments.map(c=><div key={c.id} className="comment"><div className="comment-head"><strong>{c.nickname}</strong><time>{date(c.created)}</time></div><p>{c.body}</p><div className="sub-actions">
      <button onClick={()=>onManage({kind:'comment',id:c.id,body:c.body})}>수정·삭제</button><button onClick={()=>onReport({kind:'comment',id:c.id,body:c.body})}>신고</button><button onClick={()=>onBlock({kind:'comment',id:c.id,body:c.body})}>작성자 차단</button></div></div>)}
    {nextComment&&<button type="button" disabled={loadingComments} onClick={()=>void load(nextComment)}>{loadingComments?'불러오는 중…':'댓글 더 보기'}</button>}
    <form onSubmit={e=>{e.preventDefault();void submit();}}><label>댓글<textarea required maxLength={500} rows={2} value={body} onChange={e=>setBody(e.target.value)} placeholder="서로에게 다정한 말로 응원해요." disabled={busy}/></label>
      <div className="passwords"><label>닉네임 · 선택<input maxLength={20} placeholder="익명" value={nickname} onChange={e=>setNickname(e.target.value)} disabled={busy}/></label><label>댓글 관리 비밀번호<input required type="password" minLength={8} maxLength={64} placeholder="8~64자" autoComplete="new-password" value={password} onChange={e=>setPassword(e.target.value)} disabled={busy}/></label></div>
      <details className="policy"><summary>게시판 이용규칙과 개인정보 안내</summary><Policy/></details><label className="check"><input required type="checkbox" checked={consent} onChange={e=>setConsent(e.target.checked)} disabled={busy}/><span>이용규칙과 개인정보 안내에 동의하며 댓글을 공개합니다.</span></label>
      <p className="hint">등록하면 공개돼요. 비밀번호는 댓글 수정·삭제에 필요하며 자동 복구할 수 없어요.</p>{error&&<p role="alert" className="error">{error} <button type="button" onClick={()=>void load()}>다시 불러오기</button></p>}<button className="primary" disabled={busy||!consent}>댓글 등록</button>
    </form></section>;
}
function Policy(){return <div className="policy"><p>이곳은 보호자가 직접 고른 사진·영상과 이야기를 공개하는 공간이에요. 아가말은 소리·장면을 참고한 재미있는 표현이며 실제 마음·욕구를 알아낸 번역이 아니에요.</p><h3>서로의 아이를 지켜 주세요</h3><ul><li>공유할 권리가 있고 등장하는 사람의 동의를 받은 자료만 올려 주세요.</li><li>노출·목욕·배변 장면, 주소·연락처·어린이집 등 민감한 정보, 괴롭힘·혐오·성적·폭력적 내용, 불법·저작권 침해·스팸 등 타인을 해치는 내용은 올릴 수 없어요.</li><li>신고된 게시물과 댓글은 운영자가 검토해 숨기거나 삭제할 수 있어요.</li></ul><h3>저장하는 정보</h3><p>선택한 사진·영상, 닉네임, 설명·댓글, 관리 비밀번호의 검증용 해시, 좋아요·차단·신고와 브라우저별 무작위 식별값을 저장해요. 로그인 계정과 개인 분석 기록은 수집하지 않아요. 스팸 제한을 위해 IP를 서버 비밀값과 날짜로 해시하며 원문 IP는 앱 DB에 저장하지 않아요. 호스팅 제공자의 보안 로그는 해당 제공자의 정책을 따라요.</p><p>게시물은 삭제 전까지 공개되며, 삭제하면 즉시 공개 접근을 차단하고 미디어 삭제를 시도해요. 저장소 오류로 지연되면 다음 방문 또는 운영자 확인 때 다시 삭제해요. 타인이 이미 저장한 사본까지 회수할 수는 없어요. 보안상 삭제 상태와 신고 기록이 남을 수 있어요. 차단과 좋아요는 브라우저 기준이라 기기 변경·쿠키 삭제 뒤에는 달라질 수 있어요.</p><p>운영: 최용우 · 문의/삭제 요청 <a href="mailto:bsfire1237@gmail.com">bsfire1237@gmail.com</a><br/>호스팅: OpenAI Sites / Cloudflare · 공개 게시 자료는 해외 서버에서 처리될 수 있어요.</p></div>;}
export default function Board() {
  const [posts,setPosts]=useState<Post[]>([]),[next,setNext]=useState<string|null>(null),[loading,setLoading]=useState(true),[ready,setReady]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('');
  const [composer,setComposer]=useState(false),[opened,setOpened]=useState<string|null>(null),[manage,setManage]=useState<Target|null>(null),[policy,setPolicy]=useState(false),[filter,setFilter]=useState('all'),[admin,setAdmin]=useState(false),[pending,setPending]=useState<string|null>(null);
  const [commentDraft,setCommentDraft]=useState(false),[commentRevision,setCommentRevision]=useState(0);
  const [pendingDeletes,setPendingDeletes]=useState(0),[hiddenComments,setHiddenComments]=useState<Comment[]>([]),[moderatorMedia,setModeratorMedia]=useState('');
  const loadSequence=useRef(0),openedRef=useRef(opened);openedRef.current=opened;
  const [openedSnapshot,setOpenedSnapshot]=useState<Post|null>(null);
  const [blocks,setBlocks]=useState<Block[]|null>(null),[reports,setReports]=useState<Report[]|null>(null),[moderationPosts,setModerationPosts]=useState<Post[]>([]),[report,setReport]=useState<Target|null>(null),[reason,setReason]=useState('');
  const load=useCallback(async(before?:string)=>{const seq=++loadSequence.current;setLoading(true);setError('');try{const v=await api('feed?type='+filter+(before?'&before='+encodeURIComponent(before):''));if(seq!==loadSequence.current)return;setPosts(p=>before?[...p,...v.posts.filter(n=>!p.some(old=>old.id===n.id))]:v.posts);setNext(v.next);const id=openedRef.current;if(id){const detail=await api('post/'+id);if(seq===loadSequence.current&&openedRef.current===id)setOpenedSnapshot(detail.post);}}catch(e){if(seq===loadSequence.current)setError((e as Error).message);}finally{if(seq===loadSequence.current)setLoading(false);}},[filter]);
  useEffect(()=>{let active=true;api('session').then(v=>{if(active){setReady(true);setAdmin(v.admin);if(location.hash==='#compose')setComposer(true);}}).catch(e=>{if(active){setError(e.message);setLoading(false);}});return()=>{active=false;};},[]);
  useEffect(()=>{if(ready)void load();return()=>{loadSequence.current++;};},[ready,load]);
  async function action(key:string,fn:()=>Promise<void>){if(pending)return;setPending(key);setNotice('');try{await fn();}catch(e){setNotice((e as Error).message);}finally{setPending(null);}}
  const selectedPost=openedSnapshot?.id===opened?openedSnapshot:posts.find(p=>p.id===opened);
  const filtered=posts.filter(p=>filter==='all'||(filter==='video'?p.mime==='video/mp4':p.mime!=='video/mp4'));
  async function block(t:Target){if(!confirm(commentDraft?'작성 중인 댓글을 지우고 이 작성자의 글과 댓글을 숨길까요?':'이 브라우저에서 작성자의 글과 댓글을 숨길까요?'))return;await action('block',async()=>{await api('block',t);setOpened(null);await load();setNotice('작성자를 차단했어요. 아래 차단 관리에서 해제할 수 있어요.');});}
  async function loadModeration(){const data=await api('moderation');setReports(data.reports);setModerationPosts(data.posts);setPendingDeletes(data.pendingMediaDeletes);setHiddenComments(data.hiddenComments);}
  return <><header className="site-header"><a className="brand" href="/" aria-label="우리 아이 자랑 처음으로"><span className="brand-mark">아</span><span>아가톡<small>우리 아이 자랑</small></span></a><button className="primary compact" disabled={!ready} onClick={()=>setComposer(true)}><Plus size={18}/> 자랑하기</button></header>
    <main><section className="community-banner" aria-labelledby="board-title"><h1 id="board-title" className="visually-hidden">우리 아이 자랑 · 아가톡</h1><img src="/agattok-community-banner.webp" width={1440} height={703} fetchPriority="high" alt="아가톡. 소중한 한마디, 함께 간직해요. 아가말 영상카드 · 우리 아이 자랑. 웃는 아기가 영상카드와 하트 카드를 들고 있어요."/></section>
    <div className="feed-heading"><div className="filters" aria-label="게시물 종류">{[['all','전체'],['video','영상카드'],['photo','사진·이미지']].map(([key,label])=><button key={key} aria-pressed={filter===key} onClick={()=>{if(key!==filter){loadSequence.current++;setPosts([]);setNext(null);setLoading(true);setFilter(key);}}}>{label}</button>)}</div><span className="sort">최신순</span></div>
    {notice&&<p role="status" className="notice">{notice}<button aria-label="알림 닫기" onClick={()=>setNotice('')}><X size={16}/></button></p>}
    {error&&<div role="alert" className="error">{error}<button onClick={()=>ready?void load():location.reload()}>다시 시도</button></div>}
    {loading&&posts.length===0&&<div className="empty" role="status">우리 아이들의 순간을 불러오고 있어요…</div>}
    {!loading&&!error&&filtered.length===0&&<section className="empty"><div className="empty-art" aria-hidden="true"><ImagePlus size={42}/><Heart size={22}/></div><h2>{filter==='all'?'첫 번째 자랑을 기다려요':filter==='video'?'아직 영상카드가 없어요':'아직 사진·이미지가 없어요'}</h2><p>오늘의 웃음 한 장을 올려 주세요.<br/>아가말 영상카드도 함께 나눌 수 있어요.</p><button className="primary" disabled={!ready} onClick={()=>setComposer(true)}><Plus size={18}/> 첫 순간 나누기</button></section>}
    <div className="moment-grid">{filtered.map(p=><button className="moment-tile" key={p.id} onClick={()=>{setNotice('');setOpenedSnapshot(p);setOpened(p.id);}} aria-label={p.nickname+'의 '+p.body+' · 자세히 보기'}>
      <span className="tile-media">{p.mime==='video/mp4'?<video src={p.media+'#t=0.1'} muted playsInline preload="metadata" tabIndex={-1}/>:<img src={p.media} alt="" loading="lazy"/>}
        {p.mime==='video/mp4'&&<span className="tile-badge"><Play size={13} fill="currentColor"/>{Math.floor(p.duration/60000)}:{String(Math.round(p.duration/1000)%60).padStart(2,'0')}</span>}
      </span><span className="tile-copy"><strong>{p.nickname}</strong><span className="tile-description">{p.body}</span><span className="tile-meta"><span><Heart size={14}/>{p.likes}</span><span><MessageCircle size={14}/>{p.comments}</span></span></span>
    </button>)}</div>
    {selectedPost&&<Modal title="우리 아이의 순간" onClose={()=>{if(commentDraft&&!confirm('작성 중인 댓글을 지우고 닫을까요?'))return false;setOpened(null);}}><article className="post detail-post">
      <header><span className="avatar" aria-hidden="true">{selectedPost.nickname.slice(0,1)}</span><div><strong>{selectedPost.nickname}</strong><time>{date(selectedPost.created)}</time></div></header>
      <div className="post-media"><Media src={selectedPost.media} video={selectedPost.mime==='video/mp4'}/></div>
      <div className="post-body"><p>{selectedPost.body}</p><div className="reactions">
        <button aria-pressed={selectedPost.liked} disabled={!!pending} onClick={()=>void action('like',async()=>{const v=await api('like',{postId:selectedPost.id,liked:!selectedPost.liked});setPosts(old=>old.map(p=>p.id===selectedPost.id?{...p,liked:v.liked,likes:v.count}:p));setOpenedSnapshot(old=>old?.id===selectedPost.id?{...old,liked:v.liked,likes:v.count}:old);})}><Heart size={20} fill={selectedPost.liked?'currentColor':'none'}/> 좋아요 {selectedPost.likes}</button>
        <span><MessageCircle size={20}/> 댓글 {selectedPost.comments}</span></div>
        {notice&&<p role="status" className="notice">{notice}</p>}
        <div className="sub-actions"><button onClick={()=>{setManage({kind:'post',id:selectedPost.id,body:selectedPost.body});}}>수정·삭제</button><button onClick={()=>{setReport({kind:'post',id:selectedPost.id,body:selectedPost.body});setReason('');setNotice('');}}>신고하기</button><button onClick={()=>void block({kind:'post',id:selectedPost.id,body:selectedPost.body})}>작성자 차단</button></div>
      </div><CommentPanel key={selectedPost.id} revision={commentRevision} onDraftChange={setCommentDraft} post={selectedPost} onManage={t=>{setManage(t);}} onReport={t=>{setReport(t);setReason('');setNotice('');}} onBlock={t=>void block(t)} onChanged={()=>void load()}/>
    </article></Modal>}
    {next&&<button className="more" disabled={loading} onClick={()=>void load(next)}>{loading?'불러오는 중…':'더 보기'}</button>}
    <aside className="care-note"><ShieldCheck size={25}/><p><strong>아이들의 순간을 소중히 대해 주세요.</strong><br/>실명·주소 등 개인정보와 민감한 장면은 빼고,<br className="mobile-break"/> 따뜻한 말로 서로를 응원해요.</p></aside></main>
    <footer><button onClick={()=>setPolicy(true)}>게시판 이용·개인정보 안내</button><a href="/privacy.html">앱 개인정보처리방침</a><button onClick={()=>void action('blocks',async()=>setBlocks((await api('blocks')).blocks))}>차단 관리</button><a href="mailto:bsfire1237@gmail.com">운영자 문의</a>{admin&&<button onClick={()=>void action('moderation',loadModeration)}>신고 관리</button>}<p>아가톡 · 지금 내 기분이 그래</p></footer>
    {composer&&<Composer onClose={()=>setComposer(false)} onPosted={()=>{setComposer(false);setNotice('우리 아이의 순간을 게시했어요.');void load();}}/>}
    {manage&&<Manage target={manage} onClose={()=>setManage(null)} onChanged={action=>{if(manage.kind==='post'&&action==='delete')setOpened(null);if(manage.kind==='comment')setCommentRevision(v=>v+1);setManage(null);void load();setNotice('반영했어요.');}}/>}
    {report&&<Modal title="운영자에게 신고하기" onClose={()=>!pending&&setReport(null)}><form onSubmit={e=>{e.preventDefault();void action('report',async()=>{await api('report',{...report,reason});setReport(null);setNotice('신고를 접수했어요. 운영자가 검토할게요.');});}}><label>신고 사유<textarea required maxLength={300} rows={4} value={reason} onChange={e=>setReason(e.target.value)} placeholder="개인정보 노출, 부적절한 내용 등을 알려 주세요."/></label><p className="hint">신고 내용은 다른 방문자에게 공개되지 않아요.</p>{notice&&<p role="alert" className="error">{notice}</p>}<button className="primary" disabled={!!pending}>신고 접수</button></form></Modal>}
    {policy&&<Modal title="안심하고 함께 나눠요" onClose={()=>setPolicy(false)}><Policy/></Modal>}
    {blocks&&<Modal title="차단한 작성자" onClose={()=>setBlocks(null)}>{notice&&<p role="alert" className="error">{notice}</p>}<p className="hint">이 브라우저에서 차단한 작성자예요. 닉네임이 바뀌어도 같은 브라우저에서 올린 글은 숨겨져요.</p>{blocks.length===0?<p>차단한 작성자가 없어요.</p>:blocks.map((b,i)=><div className="block-row" key={b.author}><span>작성자 {i+1}<small>{date(b.created)} 차단</small></span><button disabled={!!pending} onClick={()=>void action('unblock',async()=>{await api('unblock',{author:b.author});setBlocks((await api('blocks')).blocks);await load();})}>해제</button></div>)}</Modal>}
    {reports&&<Modal title="신고·게시물 관리" onClose={()=>setReports(null)}>{notice&&<p role="alert" className="error">{notice}</p>}<p>미디어 삭제 대기: {pendingDeletes}개 <button disabled={!!pending} onClick={()=>void action('cleanup',loadModeration)}>새로고침·삭제 재시도</button></p>{reports.length===0&&<p>처리할 신고가 없어요.</p>}{reports.map(r=><div className="comment" key={r.id}><p>{r.kind==='post'?'게시글':'댓글'} 신고: {r.reason}</p><blockquote>{r.body||'삭제된 내용'}</blockquote><small>상태: {r.state||'삭제됨'}</small>{r.kind==='post'&&r.state!=='deleted'&&<><button onClick={()=>setModeratorMedia(moderatorMedia===r.target?'':r.target)}>첨부 확인</button>{moderatorMedia===r.target&&<div className="preview"><Media src={'/api/moderation-media/'+r.target} video={r.mime==='video/mp4'}/></div>}</>}<div className="actions"><button disabled={!!pending} onClick={()=>void action('moderate',async()=>{await api('moderate',{kind:r.kind,id:r.target,action:'hide'});await loadModeration();await load();})}>대상 숨기기</button><button disabled={!!pending} onClick={()=>void action('moderate',async()=>{await api('moderate',{kind:r.kind,id:r.target,action:'restore'});await loadModeration();await load();})}>대상 복원</button><button disabled={!!pending} onClick={()=>{if(!confirm('신고된 내용을 삭제할까요?'))return;void action('moderate',async()=>{await api('moderate',{kind:r.kind,id:r.target,action:'delete'});await loadModeration();await load();});}}>대상 삭제</button><button disabled={!!pending} onClick={()=>void action('moderate',async()=>{await api('moderate',{id:r.id,action:'resolve'});await loadModeration();})}>검토 완료</button></div></div>)}<h3>숨긴 댓글</h3>{hiddenComments.map(c=><div className="comment" key={c.id}><p>{c.nickname}: {c.body}</p><div className="actions">{['restore','delete'].map(a=><button key={a} disabled={!!pending} onClick={()=>{if(a==='delete'&&!confirm('댓글을 삭제할까요?'))return;void action('moderate',async()=>{await api('moderate',{kind:'comment',id:c.id,action:a});await loadModeration();await load();});}}>{a==='restore'?'복원':'삭제'}</button>)}</div></div>)}<h3>최근 게시물</h3>{moderationPosts.map(p=><div className="comment" key={p.id}><p>{p.body}</p><small>{p.state}</small><button onClick={()=>setModeratorMedia(moderatorMedia===p.id?'':p.id)}>첨부 확인</button>{moderatorMedia===p.id&&<div className="preview"><Media src={'/api/moderation-media/'+p.id} video={p.mime==='video/mp4'}/></div>}<div className="actions">{['hide','restore','delete'].map(a=><button key={a} disabled={!!pending} onClick={()=>{if(a==='delete'&&!confirm('미디어까지 삭제할까요?'))return;void action('moderate',async()=>{await api('moderate',{kind:'post',id:p.id,action:a});await loadModeration();await load();});}}>{a==='hide'?'숨김':a==='restore'?'복원':'삭제'}</button>)}</div></div>)}</Modal>}
  </>;
}
