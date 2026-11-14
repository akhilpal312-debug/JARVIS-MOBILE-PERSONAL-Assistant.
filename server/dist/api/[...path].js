// Jarvis backend — bundled from server/ (api/, lib/). Built for Vercel file deployment.
var d=class extends Error{constructor(t,r,o,n){super(o),this.status=t,this.code=r,this.details=n}};function m(e,t=200,r={}){
return new Response(JSON.stringify(e),{status:t,headers:{"content-type":"application/json; charset=utf-8","cache-control":"\
no-store, max-age=0","x-content-type-options":"nosniff",...r}})}function Y(e,t={}){return new Response(e,{status:200,headers:{
"content-type":"text/event-stream; charset=utf-8","cache-control":"no-store, no-transform",connection:"keep-alive",...t}})}
async function v(e,{maxBytes:t=64*1024}={}){let r=await e.text();if(r.length>t)throw new d(413,"payload_too_large","Requ\
est body is too large.");if(!r.trim())return{};try{let o=JSON.parse(r);if(o===null||typeof o!="object"||Array.isArray(o))
throw new Error("not an object");return o}catch{throw new d(400,"invalid_json","Request body must be a JSON object.")}}function k(e,{
field:t,max:r=4e3,required:o=!1,pattern:n}={}){if(e==null){if(o)throw new d(400,"missing_field",`"${t}" is required.`);return}
if(typeof e!="string")throw new d(400,"invalid_field",`"${t}" must be a string.`);let i=e.trim();if(o&&!i)throw new d(400,
"missing_field",`"${t}" must not be empty.`);if(i.length>r)throw new d(400,"invalid_field",`"${t}" exceeds ${r} characte\
rs.`);if(n&&i&&!n.test(i))throw new d(400,"invalid_field",`"${t}" has an unsupported format.`);return i}function g(e,t={}){let r={};for(let[o,n]of Object.entries(t))n!==void 0&&(/token|key|secret|authorization|prompt|message|content/i.
test(o)||(r[o]=typeof n=="string"?n.slice(0,200):n));console.log(JSON.stringify({ts:new Date().toISOString(),event:e,...r}))}
function w(e){return e instanceof d?m({error:{code:e.code,message:e.message,details:e.details}},e.status):(g("unhandled_\
error",{error:e?.name,message:e?.message}),m({error:{code:"internal_error",message:"Unexpected server error."}},500))}function _(e){
return m({error:{code:"method_not_allowed",message:`Use ${e.join(", ")}.`}},405,{allow:e.join(", ")})}var W=[{slug:"groq",name:"Groq",country:"US",baseUrl:"https://api.groq.com/openai/v1",keyEnv:"GROQ_API_KEY",keyOptional:!1,
docsUrl:"https://console.groq.com/docs",models:["openai/gpt-oss-120b","openai/gpt-oss-20b","qwen/qwen3.8-27b"],caps:{chat:!0,
json:!0,tools:!0,reasoning:!0},freeTier:"Free tier, no credit card required. Free-plan prompts are governed by Groq data\
 policies.",rateLimit:"30 RPM, 1,000 RPD per listed chat model (directory, 2026-11).",priority:10,contextWindow:131072},
{slug:"gemini",name:"Google Gemini (OpenAI-compatible)",country:"US",baseUrl:"https://generativelanguage.googleapis.com/\
v1beta/openai",keyEnv:"GEMINI_API_KEY",keyOptional:!1,docsUrl:"https://ai.google.dev/gemini-api/docs/openai",models:["ge\
mini-2.5-flash","gemini-2.5-flash-lite"],caps:{chat:!0,json:!0,tools:!0,reasoning:!0},freeTier:"Free tier without a cred\
it card; free-tier prompts may be used to improve Google products.",rateLimit:"Google no longer publishes per-model free\
-tier limits; check AI Studio quotas.",priority:20,contextWindow:1e6},{slug:"mistral",name:"Mistral AI",country:"FR",baseUrl:"\
https://api.mistral.ai/v1",keyEnv:"MISTRAL_API_KEY",keyOptional:!1,docsUrl:"https://docs.mistral.ai",models:["mistral-sm\
all-latest","ministral-8b-latest"],caps:{chat:!0,json:!0,tools:!0,reasoning:!1},freeTier:"Free mode enabled by default, \
no credit card. Free-mode prompts may train Mistral models unless opted out.",rateLimit:"Historically ~1 RPS / 500K TPM;\
 the provider now points at the admin Limits page.",priority:30,contextWindow:262144},{slug:"nvidia",name:"NVIDIA NIM",country:"\
US",baseUrl:"https://integrate.api.nvidia.com/v1",keyEnv:"NVIDIA_API_KEY",keyOptional:!1,docsUrl:"https://build.nvidia.c\
om",models:["openai/gpt-oss-20b","nvidia/nemotron-3-ultra-550b-a55b"],caps:{chat:!0,json:!0,tools:!0,reasoning:!0},freeTier:"\
Free with NVIDIA Developer Program membership.",rateLimit:"40 RPM, 10,000 RPD (directory, 2026-11).",priority:40,contextWindow:131072},
{slug:"zai",name:"Z AI (Zhipu AI)",country:"CN",baseUrl:"https://api.z.ai/api/paas/v4",keyEnv:"ZAI_API_KEY",keyOptional:!1,
docsUrl:"https://docs.z.ai",models:["glm-4.7-flash","glm-4.6v-flash"],caps:{chat:!0,json:!0,tools:!1,reasoning:!0},freeTier:"\
Permanent free Flash models, no credit card required.",rateLimit:"1 concurrent request on Flash models.",priority:50,contextWindow:204800},
{slug:"cloudflare",name:"Cloudflare Workers AI",country:"US",baseUrlTemplate:"https://api.cloudflare.com/client/v4/accou\
nts/{account_id}/ai/v1",keyEnv:"CLOUDFLARE_API_TOKEN",extraEnv:["CLOUDFLARE_ACCOUNT_ID"],keyOptional:!1,docsUrl:"https:/\
/developers.cloudflare.com/workers-ai",models:["@cf/meta/llama-3.3-70b-instruct-fp8-fast","@cf/openai/gpt-oss-120b"],caps:{
chat:!0,json:!1,tools:!1,reasoning:!1},freeTier:"10,000 Neurons/day free, no credit card required.",rateLimit:"Shared 10\
K neurons/day across models.",priority:60,contextWindow:24576},{slug:"openrouter",name:"OpenRouter (free pool)",country:"\
US",baseUrl:"https://openrouter.ai/api/v1",keyEnv:"OPENROUTER_API_KEY",keyOptional:!1,docsUrl:"https://openrouter.ai/doc\
s",models:["google/gemma-4-26b-a4b-it:free","liquid/lfm-2.5-2.6b:free","nvidia/nemotron-3.5-lightning:free"],caps:{chat:!0,
json:!0,tools:!0,reasoning:!1},freeTier:"Models with the :free suffix; free providers may log prompts for training.",rateLimit:"\
20 RPM, 50 RPD across all free models.",priority:70,contextWindow:262144},{slug:"llm7",name:"LLM7.io",country:"UK",baseUrl:"\
https://api.llm7.io/v1",keyEnv:"LLM7_API_KEY",keyOptional:!0,docsUrl:"https://docs.llm7.io",models:["gpt-oss:20b","nemot\
ron-3-nano:30b"],caps:{chat:!0,json:!1,tools:!1,reasoning:!1},freeTier:"Anonymous access to turbo models with no key; a \
free token raises limits.",rateLimit:"Free token: 1 RPS, 60 RPM, 250 requests/hour, 100K tokens/24h.",priority:80,contextWindow:128e3},
{slug:"ovh",name:"OVHcloud AI Endpoints (anonymous)",country:"FR",baseUrl:"https://oai.endpoints.kepler.ai.cloud.ovh.net\
/v1",keyEnv:"OVH_AI_TOKEN",keyOptional:!0,docsUrl:"https://www.ovhcloud.com/en/public-cloud/ai-endpoints/catalog/",models:[
"gpt-oss-20b","Mistral-Nemo-Instruct-2407"],caps:{chat:!0,json:!1,tools:!1,reasoning:!1},freeTier:"Permanent free anonym\
ous tier: no account and no key required.",rateLimit:"2 RPM per IP per model (anonymous).",priority:90,contextWindow:131072}];function Z(e,t=process.env){let r=String(t.JARVIS_PROVIDER_ALLOWLIST||"").split(",").map(s=>s.trim()).filter(Boolean);if(r.
length>0&&!r.includes(e.slug))return null;let o=e.keyEnv?String(t[e.keyEnv]||"").trim():"",n=/^(paste-|changeme|your-|<)/i.
test(o)?"":o;if(!n&&!e.keyOptional)return null;let i=`JARVIS_PROVIDER_BASE_URL_${e.slug.toUpperCase().replace(/-/g,"_")}`,
a=String(t[i]||"").trim(),c=a||e.baseUrl;if(e.baseUrlTemplate&&!a){let s={};for(let f of e.extraEnv||[]){let u=String(t[f]||
"").trim();if(!u)return null;s[f]=u}if(c=e.baseUrlTemplate.replace(/\{(\w+)\}/g,(f,u)=>{let l=Object.keys(s).find(h=>h.toLowerCase().
includes(u.toLowerCase()));return l?s[l]:""}),!c||c.includes("{"))return null}return{...e,baseUrl:c,apiKey:n||void 0,configured:!0,
keyOptional:e.keyOptional}}function R(e=process.env){return W.map(t=>Z(t,e)).filter(Boolean)}function X(e=process.env){return W.
map(t=>{let r=Z(t,e);return{slug:t.slug,name:t.name,country:t.country,models:t.models,capabilities:t.caps,freeTier:t.freeTier,
rateLimit:t.rateLimit,docsUrl:t.docsUrl,keyEnv:t.keyEnv,keyOptional:t.keyOptional,configured:!!r,status:r?"available":t.
keyOptional?"unavailable":"not_configured"}})}var M="jarvis_tasks",L="jarvis_memories",E={tasks:new Map,memories:new Map},te=["pending","in_progress","scheduled","blo\
cked","completed"],re=["low","medium","high","critical"];function p(e=process.env){let t=String(e.SUPABASE_URL||"").trim(),
r=String(e.SUPABASE_SERVICE_ROLE_KEY||"").trim();return t&&r?"supabase":"memory"}function me(e){let t=String(e.SUPABASE_URL||
"").trim().replace(/\/$/,""),r=String(e.SUPABASE_SERVICE_ROLE_KEY||"").trim();return!t||!r?null:{url:t,key:r}}async function b(e,{
method:t="GET",body:r,env:o,fetchImpl:n=fetch,prefer:i}={}){let a=me(o);if(!a)throw new d(503,"store_not_configured","Cl\
oud sync is not configured.");let c={apikey:a.key,authorization:`Bearer ${a.key}`,"content-type":"application/json",accept:"\
application/json"};i&&(c.prefer=i);let s=await n(`${a.url}/rest/v1/${e}`,{method:t,headers:c,body:r===void 0?void 0:JSON.
stringify(r),signal:AbortSignal.timeout?AbortSignal.timeout(8e3):void 0});if(!s.ok){let u=await s.text().catch(()=>"");throw new d(
502,"store_error",`Storage request failed (${s.status}).`,{detail:u.slice(0,300)})}if(s.status===204)return null;let f=await s.
text();return f?JSON.parse(f):null}function Q(e,t,r=null){let o=Date.now(),n=String(e.title||r?.title||"").trim();if(!n)
throw new d(400,"invalid_task","Task title is required.");if(n.length>300)throw new d(400,"invalid_task","Task title is \
too long.");let i=te.includes(e.status)?e.status:r?.status||"pending",a=re.includes(e.priority)?e.priority:r?.priority||
"medium",c=Number.isFinite(Number(e.dueAt))&&Number(e.dueAt)>0?Number(e.dueAt):null;return{id:String(e.id||r?.id||`t_${o.
toString(36)}_${Math.random().toString(36).slice(2,8)}`),device_id:t,title:n.slice(0,300),notes:String(e.notes||r?.notes||
"").slice(0,2e3),status:i,priority:a,due_at:c,reminder_at:Number.isFinite(Number(e.reminderAt))&&Number(e.reminderAt)>0?
Number(e.reminderAt):r?.reminder_at||null,completed_at:i==="completed"?r?.completed_at||o:null,evidence:String(e.evidence||
r?.evidence||"").slice(0,500),created_at:r?.created_at||o,updated_at:o}}function ee(e,t,r=null){let o=String(e.content||
"").trim();if(!o)throw new d(400,"invalid_memory","Memory content is required.");let n=Date.now();return{id:String(e.id||
r?.id||`m_${n.toString(36)}_${Math.random().toString(36).slice(2,8)}`),device_id:t,kind:["preference","routine","fact","\
goal"].includes(e.kind)?e.kind:"fact",content:o.slice(0,1e3),created_at:r?.created_at||n,updated_at:n}}var U=te,oe=re;async function P(e,{
env:t=process.env,fetchImpl:r=fetch,status:o}={}){if(p(t)==="supabase"){let n=o?`&status=eq.${encodeURIComponent(o)}`:"";
return await b(`${M}?device_id=eq.${encodeURIComponent(e)}${n}&order=updated_at.desc&limit=500`,{env:t,fetchImpl:r})||[]}
return[...E.tasks.values()].filter(n=>n.device_id===e)}async function $(e,t,{env:r=process.env,fetchImpl:o=fetch}={}){if(p(
r)==="supabase"){let a=t.id?await b(`${M}?id=eq.${encodeURIComponent(t.id)}&device_id=eq.${encodeURIComponent(e)}&limit=\
1`,{env:r,fetchImpl:o}):null,c=Q(t,e,a?.[0]||null);return(await b(`${M}?on_conflict=id`,{method:"POST",body:[c],env:r,fetchImpl:o,
prefer:"resolution=merge-duplicates,return=representation"}))?.[0]||c}let n=t.id?E.tasks.get(String(t.id)):null,i=Q(t,e,
n||null);return E.tasks.set(i.id,i),i}async function ne(e,t,{env:r=process.env,fetchImpl:o=fetch}={}){return p(r)==="sup\
abase"?(await b(`${M}?id=eq.${encodeURIComponent(t)}&device_id=eq.${encodeURIComponent(e)}`,{method:"DELETE",env:r,fetchImpl:o}),
{deleted:!0}):(E.tasks.delete(String(t)),{deleted:!0})}async function O(e,{env:t=process.env,fetchImpl:r=fetch}={}){return p(
t)==="supabase"?await b(`${L}?device_id=eq.${encodeURIComponent(e)}&order=updated_at.desc&limit=500`,{env:t,fetchImpl:r})||
[]:[...E.memories.values()].filter(o=>o.device_id===e)}async function N(e,t,{env:r=process.env,fetchImpl:o=fetch}={}){if(p(
r)==="supabase"){let a=t.id?await b(`${L}?id=eq.${encodeURIComponent(t.id)}&device_id=eq.${encodeURIComponent(e)}&limit=\
1`,{env:r,fetchImpl:o}):null,c=ee(t,e,a?.[0]||null);return(await b(`${L}?on_conflict=id`,{method:"POST",body:[c],env:r,fetchImpl:o,
prefer:"resolution=merge-duplicates,return=representation"}))?.[0]||c}let n=t.id?E.memories.get(String(t.id)):null,i=ee(
t,e,n||null);return E.memories.set(i.id,i),i}async function C(e,t,{env:r=process.env,fetchImpl:o=fetch}={}){return p(r)===
"supabase"?(await b(`${L}?id=eq.${encodeURIComponent(t)}&device_id=eq.${encodeURIComponent(e)}`,{method:"DELETE",env:r,fetchImpl:o}),
{deleted:!0}):(E.memories.delete(String(t)),{deleted:!0})}async function pe(){return m({ok:!0,service:"jarvis-backend",version:"1.0.0",time:new Date().toISOString(),providers:{configured:R().
length},storage:p()})}async function K(e){try{return e.method==="GET"?await pe():e.method==="HEAD"?m({ok:!0}):_(["GET"])}catch(t){return w(t)}}import{timingSafeEqual as fe}from"node:crypto";var ge=/^[A-Za-z0-9_-]{8,64}$/;function he(e=process.env){return String(e.JARVIS_API_TOKEN||"").split(",").map(t=>t.trim()).
filter(Boolean)}function ye(e,t){let r=Buffer.from(e),o=Buffer.from(t);return r.length!==o.length?!1:fe(r,o)}function y(e,t=process.
env){let r=he(t);if(r.length===0)throw new d(503,"server_not_configured","JARVIS_API_TOKEN is not configured on the serv\
er.");let o=e.headers.get("authorization")||"",n=/^Bearer\s+(.+)$/i.exec(o.trim());if(!n)throw new d(401,"unauthorized",
"Missing bearer token.");let i=n[1].trim(),a=!1;for(let s of r)ye(i,s)&&(a=!0);if(!a)throw new d(401,"unauthorized","Inv\
alid bearer token.");let c=(e.headers.get("x-device-id")||"").trim();if(!ge.test(c))throw new d(400,"invalid_device_id",
"X-Device-Id must be 8-64 characters of [A-Za-z0-9_-].");return{deviceId:c,userId:c}}var j=new Map,we=3e4,_e=2;function se(){let e=Date.now();return[...j.entries()].map(([t,r])=>({slug:t,failures:r.failures,
open:r.openedUntil>e,retryInMs:Math.max(0,r.openedUntil-e)}))}function Se(e){let t=j.get(e)||{failures:0,openedUntil:0};t.failures+=1,t.failures>=_e&&(t.openedUntil=Date.now()+we,t.failures=
0),j.set(e,t)}function ve(e){j.delete(e)}function ke({needJson:e=!1,needTools:t=!1,env:r=process.env}={}){return R(r).filter(
o=>o.caps.chat).filter(o=>e?o.caps.json:!0).sort((o,n)=>e&&o.caps.json!==n.caps.json?o.caps.json?-1:1:t&&o.caps.tools!==
n.caps.tools?o.caps.tools?-1:1:o.priority-n.priority).map(o=>({...o,model:o.models[0],fallbackModels:o.models.slice(1)}))}
function Ee(e){let r=e?.choices?.[0]?.message||{},o=typeof r.content=="string"?r.content:"";return o.trim()?o:(typeof r.
reasoning_content=="string"?r.reasoning_content:"").trim()}async function be(e,t,{timeoutMs:r,fetchImpl:o,env:n}){let i=`${e.
baseUrl.replace(/\/$/,"")}/chat/completions`,a={"content-type":"application/json",accept:"application/json","user-agent":"\
Jarvis-Android-Backend/1.0"};e.apiKey&&(a.authorization=`Bearer ${e.apiKey}`),e.slug==="openrouter"&&(a["http-referer"]=
String(n.APP_ORIGIN||"https://jarvis-assistant.vercel.app"),a["x-title"]="Jarvis Personal Assistant");let c=AbortSignal.
timeout?AbortSignal.timeout(r):void 0,s=await o(i,{method:"POST",headers:a,body:JSON.stringify(t),signal:c});if(!s.ok){let l="";
try{l=(await s.text()).slice(0,400)}catch{}let h=new Error(`provider ${e.slug} returned ${s.status}`);throw h.status=s.status,
h.retryable=s.status===429||s.status>=500,h.detail=l,h}let f=await s.json(),u=Ee(f);if(!u){let l=new Error(`provider ${e.
slug} returned an empty completion`);throw l.retryable=!0,l}return{text:u,usage:f.usage||null,raw:f}}async function D({messages:e,
needJson:t=!1,needTools:r=!1,maxTokens:o=800,temperature:n=.5,timeoutMs:i=2e4,env:a=process.env,fetchImpl:c=fetch}={}){if(!Array.
isArray(e)||e.length===0)throw new d(400,"invalid_messages","messages must be a non-empty array.");let s=ke({needJson:t,
needTools:r,env:a}),f=[];for(let l of s){let h=j.get(l.slug);if(h&&h.openedUntil>Date.now()){f.push({provider:l.slug,model:l.
model,skipped:"circuit_open"});continue}let I=[l.model,...l.fallbackModels];for(let A of I){let x={model:A,messages:e,temperature:n,
max_tokens:o,stream:!1};t&&l.caps.json&&(x.response_format={type:"json_object"});let V=Date.now();try{let T=await be(l,x,
{timeoutMs:i,fetchImpl:c,env:a});return ve(l.slug),f.push({provider:l.slug,model:A,ok:!0,latencyMs:Date.now()-V}),{text:T.
text,provider:l.slug,providerName:l.name,model:A,usage:T.usage,attempts:f}}catch(T){if(f.push({provider:l.slug,model:A,ok:!1,
status:T.status||null,reason:String(T.message||"error").slice(0,160),latencyMs:Date.now()-V}),Se(l.slug),T.status===401||
T.status===403)break}}}throw new d(503,s.length===0?"no_provider_configured":"all_providers_failed",s.length===0?"No LLM\
 provider is configured on this deployment. Set a provider key (see /api/assistant/status).":"Every configured LLM provi\
der failed or was rate limited. Try again shortly.",{attempts:f})}async function Ae(e){let t=Date.now();try{let r={accept:"application/json","user-agent":"Jarvis-Android-Backend/1.0"};e.
apiKey&&(r.authorization=`Bearer ${e.apiKey}`);let o=await fetch(`${e.baseUrl.replace(/\/$/,"")}/models`,{headers:r,signal:AbortSignal.
timeout?AbortSignal.timeout(4500):void 0});return{slug:e.slug,reachable:o.ok,httpStatus:o.status,latencyMs:Date.now()-t}}catch(r){
return{slug:e.slug,reachable:!1,httpStatus:null,latencyMs:Date.now()-t,reason:String(r?.name||"error")}}}async function xe(e){
y(e);let r=new URL(e.url).searchParams.get("probe")==="1",o=X(),n=[];return r&&(n=await Promise.all(R().map(i=>Ae(i))),g(
"provider_probe",{configured:o.filter(i=>i.configured).length})),m({ok:!0,version:"1.0.0",time:new Date().toISOString(),
storage:p(),providers:o,probes:n,circuitBreakers:se(),hints:{keys:"Provider keys are server-side environment variables o\
nly. The app never receives them.",freeTier:"Free-tier terms are descriptive; re-check each provider pricing page before\
 production use.",storage:p()==="memory"?"Cloud sync uses per-instance memory: set SUPABASE_URL and SUPABASE_SERVICE_ROL\
E_KEY for durable sync. The app keeps working locally without it.":"Cloud sync is durable (Supabase REST)."}})}async function J(e){
try{return e.method==="GET"?await xe(e):_(["GET"])}catch(t){return w(t)}}var ie=new Map;function Te(e,{limit:t=30,windowMs:r=6e4}={}){let o=Date.now(),n=ie.get(e);return!n||o-n.start>=r?(ie.set(
e,{start:o,count:1}),{allowed:!0,remaining:t-1,resetMs:r}):(n.count+=1,n.count>t?{allowed:!1,remaining:0,resetMs:r-(o-n.
start)}:{allowed:!0,remaining:t-n.count,resetMs:r-(o-n.start)})}function S(e,t){let r=Te(e,t);if(!r.allowed)throw new d(
429,"rate_limited","Too many requests. Please retry shortly.",{retryAfterMs:r.resetMs});return r}var Oe=`Reply with ONE JSON object and nothing else:
{
  "reply": string,                 // short spoken answer in the user's language
  "language": "hi" | "en" | "hinglish",
  "intent": "create_tasks" | "complete_task" | "delete_task" | "reschedule_task" | "list_tasks" | "create_memory" | "dev\
ice_action" | "chat",
  "tasks": [{"title": string, "notes": string, "priority": "low"|"medium"|"high"|"critical",
             "dueAtISO": string|null, "reminderAtISO": string|null}],
  "taskRefs": [{"match": string, "status": "pending"|"in_progress"|"scheduled"|"blocked"|"completed"}],
  "memories": [{"kind": "preference"|"routine"|"fact"|"goal", "content": string}],
  "deviceAction": null | {"type": "open_app"|"open_settings"|"calendar_event"|"dial"|"media",
                          "target": string, "title": string|null, "startAtISO": string|null},
  "needsConfirmation": boolean     // true for irreversible or consequential actions
}`;function G({tasks:e=[],memories:t=[],language:r="auto",now:o=new Date}={}){let n=e.slice(0,25).map(a=>`- [${a.status}\
] ${a.title}${a.due_at?` (due ${new Date(a.due_at).toISOString()})`:""}`).join(`
`),i=t.slice(0,25).map(a=>`- (${a.kind}) ${a.content}`).join(`
`);return["You are Jarvis, the user's personal assistant on their Android phone.","You are calm, concise and practical. \
You never invent facts, and you say plainly when something is not possible, offline, or needs a permission.","You unders\
tand and answer in Hindi (Devanagari), English and Hinglish. Always mirror the language and script the user used.","You \
may create, complete, reschedule or remove tasks only through the JSON contract you are given.","Never claim a device ac\
tion happened; the phone reports the real result back.","Keep spoken replies under 60 words unless the user asks for det\
ail.",`Current time: ${o.toISOString()}. User timezone offset: ${-o.getTimezoneOffset()/60}h.`,r!=="auto"?`Preferred lan\
guage: ${r}.`:"",n?`Open tasks:
${n}`:"The user has no open tasks.",i?`User-approved memories:
${i}`:"No saved memories."].filter(Boolean).join(`

`)}function ce({text:e,tasks:t=[],memories:r=[],now:o=new Date}){return[G({tasks:t,memories:r,now:o}),"Convert the user'\
s instruction into the JSON contract. Break multi-part requests into separate tasks.","Do not duplicate a task that alre\
ady exists with the same meaning. Use deviceAction only for actions the phone can perform with a standard Android intent\
.",Oe,`User said: ${e}`].join(`

`)}var Ie=/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2}(\.\d{1,3})?)?(Z|[+-]\d{2}:\d{2})?$/;function B(e){if(!e||typeof e!="str\
ing"||!Ie.test(e))return null;let t=Date.parse(e);return Number.isFinite(t)?t:null}function le(e){if(typeof e!="string")
return null;let t=e.trim().replace(/^```(?:json)?/i,"").replace(/```$/,"").trim(),r=ae(t);if(r)return r;let o=t.indexOf(
"{"),n=t.lastIndexOf("}");return o===-1||n<=o?null:ae(t.slice(o,n+1))}function ae(e){try{let t=JSON.parse(e);return t&&typeof t==
"object"&&!Array.isArray(t)?t:null}catch{return null}}function de(e){if(!e||typeof e!="object")return null;let t=typeof e.
reply=="string"?e.reply.trim().slice(0,1200):"",r=Array.isArray(e.tasks)?e.tasks.filter(s=>s&&typeof s.title=="string"&&
s.title.trim()).slice(0,12).map(s=>({title:s.title.trim().slice(0,300),notes:typeof s.notes=="string"?s.notes.trim().slice(
0,1e3):"",priority:oe.includes(s.priority)?s.priority:"medium",dueAt:B(s.dueAtISO),reminderAt:B(s.reminderAtISO)})):[],o=Array.
isArray(e.taskRefs)?e.taskRefs.filter(s=>s&&typeof s.match=="string"&&s.match.trim()).slice(0,12).map(s=>({match:s.match.
trim().slice(0,300),status:U.includes(s.status)?s.status:"completed"})):[],n=Array.isArray(e.memories)?e.memories.filter(
s=>s&&typeof s.content=="string"&&s.content.trim()).slice(0,8).map(s=>({kind:["preference","routine","fact","goal"].includes(
s.kind)?s.kind:"fact",content:s.content.trim().slice(0,500)})):[],i=null;if(e.deviceAction&&typeof e.deviceAction=="obje\
ct"){let s=e.deviceAction.type;["open_app","open_settings","calendar_event","dial","media"].includes(s)&&(i={type:s,target:String(
e.deviceAction.target||"").slice(0,200),title:e.deviceAction.title?String(e.deviceAction.title).slice(0,200):null,startAt:B(
e.deviceAction.startAtISO)})}let c=["create_tasks","complete_task","delete_task","reschedule_task","list_tasks","create_\
memory","device_action","chat"].includes(e.intent)?e.intent:"chat";return!t&&r.length===0&&o.length===0&&n.length===0&&!i?
null:{reply:t,language:["hi","en","hinglish"].includes(e.language)?e.language:"auto",intent:c,tasks:r,taskRefs:o,memories:n,
deviceAction:i,needsConfirmation:!!e.needsConfirmation}}var Re=["user","assistant","system"];function Pe(e){if(!Array.isArray(e)||e.length===0)throw new d(400,"invalid_messages",
'"messages" must be a non-empty array.');return e.filter(t=>t&&Re.includes(t.role)&&typeof t.content=="string").slice(-14).
map(t=>({role:t.role==="system"?"user":t.role,content:t.content.trim().slice(0,4e3)})).filter(t=>t.content.length>0)}async function Ue(e){
let{userId:t}=y(e);S(`chat:${t}`,{limit:30,windowMs:6e4});let r=await v(e),o=Pe(r.messages),n=k(r.language,{field:"langu\
age",max:20})||"auto",i=r.stream===!0,a=[],c=[];if(r.includeContext!==!1)try{[a,c]=await Promise.all([P(t),O(t)])}catch(u){
g("context_unavailable",{store:p(),code:u?.code})}let f=[{role:"system",content:G({tasks:a,memories:c,language:n})},...o];
try{let u=await D({messages:f,maxTokens:900,temperature:.6});if(g("chat_ok",{provider:u.provider,model:u.model,store:p()}),
i){let l=new TextEncoder,h=new ReadableStream({start(I){let A=u.text.split(/(\s+)/);for(let x=0;x<A.length;x+=6)I.enqueue(
l.encode(`data: ${JSON.stringify({delta:A.slice(x,x+6).join("")})}

`));I.enqueue(l.encode(`data: ${JSON.stringify({done:!0,provider:u.provider,model:u.model})}

`)),I.close()}});return Y(h)}return m({reply:u.text,provider:u.provider,providerName:u.providerName,model:u.model,usage:u.
usage,contextTaskCount:a.length,contextMemoryCount:c.length})}catch(u){if(u instanceof d&&(u.code==="no_provider_configu\
red"||u.code==="all_providers_failed"))return g("chat_degraded",{code:u.code}),m({degraded:!0,reply:null,reason:u.message,
attempts:u.details?.attempts||[]},200);throw u}}async function z(e){try{return e.method==="POST"?await Ue(e):_(["POST"])}catch(t){
return w(t)}}async function $e(e){let{userId:t}=y(e);S(`command:${t}`,{limit:20,windowMs:6e4});let r=await v(e),o=k(r.text,{field:"te\
xt",required:!0,max:2e3}),n=r.persist!==!1,i=[],a=[];try{[i,a]=await Promise.all([P(t),O(t)])}catch(l){g("context_unavai\
lable",{code:l?.code})}let c;try{c=await D({messages:[{role:"user",content:ce({text:o,tasks:i,memories:a})}],needJson:!0,
maxTokens:1e3,temperature:.2})}catch(l){if(l instanceof d&&(l.code==="no_provider_configured"||l.code==="all_providers_f\
ailed"))return g("command_degraded",{code:l.code}),m({degraded:!0,reason:l.message,command:null,attempts:l.details?.attempts||
[],note:"The app falls back to its on-device bilingual command parser."});throw l}let s=de(le(c.text));if(!s)return g("c\
ommand_unparseable",{provider:c.provider}),m({degraded:!0,reason:"The model reply was not a valid command object.",command:null,
provider:c.provider,model:c.model});let f=[],u=[];if(n&&p()==="supabase"){for(let l of s.tasks){let h=await $(t,l);f.push(
h.id)}for(let l of s.memories){let h=await N(t,l);u.push(h.id)}}return g("command_ok",{provider:c.provider,model:c.model,
intent:s.intent}),m({command:s,provider:c.provider,providerName:c.providerName,model:c.model,persisted:n&&p()==="supabas\
e",createdTaskIds:f,createdMemoryIds:u})}async function q(e){try{return e.method==="POST"?await $e(e):_(["POST"])}catch(t){
return w(t)}}async function je(e){let{userId:t}=y(e),o=new URL(e.url).searchParams.get("status")||void 0;if(o&&!U.includes(o))throw new d(
400,"invalid_status",`status must be one of: ${U.join(", ")}.`);let n=await P(t,{status:o});return m({tasks:n,storage:p(),
count:n.length})}async function Me(e){let{userId:t}=y(e);S(`tasks:${t}`,{limit:120,windowMs:6e4});let r=await v(e);k(r.title,
{field:"title",required:!0,max:300});let o=await $(t,r);return g("task_saved",{storage:p(),status:o.status}),m({task:o},
201)}async function Le(e){let{userId:t}=y(e);S(`tasks:${t}`,{limit:120,windowMs:6e4});let r=await v(e),o=k(r.id,{field:"\
id",required:!0,max:80}),n=await $(t,{...r,id:o});return g("task_updated",{storage:p(),status:n.status}),m({task:n})}async function Ne(e){
let{userId:t}=y(e);S(`tasks:${t}`,{limit:120,windowMs:6e4});let o=new URL(e.url).searchParams.get("id")||(await v(e,{maxBytes:4096})).
id;if(!o)throw new d(400,"missing_field",'"id" is required.');return await ne(t,String(o)),g("task_deleted",{storage:p()}),
m({deleted:!0,id:String(o)})}async function F(e){try{switch(e.method){case"GET":return await je(e);case"POST":return await Me(
e);case"PATCH":return await Le(e);case"DELETE":return await Ne(e);default:return _(["GET","POST","PATCH","DELETE"])}}catch(t){
return w(t)}}async function De(e){let{userId:t}=y(e),r=await O(t);return m({memories:r,storage:p(),count:r.length})}async function Ce(e){
let{userId:t}=y(e);S(`memory:${t}`,{limit:60,windowMs:6e4});let r=await v(e);k(r.content,{field:"content",required:!0,max:1e3});
let o=await N(t,r);return g("memory_saved",{storage:p()}),m({memory:o},201)}async function Ke(e){let{userId:t}=y(e);S(`m\
emory:${t}`,{limit:60,windowMs:6e4});let r=new URL(e.url),o=r.searchParams.get("id");if(!o){if(r.searchParams.get("all")===
"1"){let n=await O(t);for(let i of n)await C(t,i.id);return g("memory_cleared",{count:n.length}),m({deleted:!0,cleared:n.
length})}throw new d(400,"missing_field",'"id" is required (or pass all=1 to clear).')}return await C(t,String(o)),g("me\
mory_deleted"),m({deleted:!0,id:String(o)})}async function H(e){try{switch(e.method){case"GET":return await De(e);case"P\
OST":return await Ce(e);case"DELETE":return await Ke(e);default:return _(["GET","POST","DELETE"])}}catch(t){return w(t)}}var ue={"/api/health":K,"/api/assistant/status":J,"/api/assistant/chat":z,"/api/assistant/command":q,"/api/assistant/tas\
ks":F,"/api/assistant/memory":H};function Je(e){try{return new URL(e,"https://jarvis.local").pathname.replace(/\/+$/,"")}catch{
return e}}async function Be(e){let t=Je(e.url),r=ue[t];return r?r(e):new Response(JSON.stringify({error:{code:"not_found",
message:`No Jarvis endpoint at ${t}.`},endpoints:Object.keys(ue)}),{status:404,headers:{"content-type":"application/json\
; charset=utf-8","cache-control":"no-store"}})}export{Be as default};
