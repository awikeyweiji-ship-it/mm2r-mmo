import hashlib, json, os, sqlite3, time, uuid
from contextlib import contextmanager
from pathlib import Path

ROOT=Path(os.getenv("AGENT_DATA_DIR","./data")).resolve()
DB=ROOT/"agent.sqlite3"
OBJECTS=ROOT/"objects"
TEXTS=ROOT/"texts"
RESULTS=ROOT/"results"
for p in (ROOT,OBJECTS,TEXTS,RESULTS): p.mkdir(parents=True,exist_ok=True)

SCHEMA="""
PRAGMA journal_mode=WAL;
CREATE TABLE IF NOT EXISTS projects(
 id TEXT PRIMARY KEY,title TEXT NOT NULL,revision INTEGER NOT NULL DEFAULT 0,
 created_at REAL NOT NULL,updated_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS sources(
 id TEXT PRIMARY KEY,project_id TEXT NOT NULL,filename TEXT NOT NULL,mime TEXT NOT NULL,
 sha256 TEXT NOT NULL UNIQUE,size_bytes INTEGER NOT NULL,object_path TEXT NOT NULL,text_path TEXT NOT NULL,
 created_at REAL NOT NULL);
CREATE TABLE IF NOT EXISTS tasks(
 id TEXT PRIMARY KEY,project_id TEXT NOT NULL,goal TEXT NOT NULL,rules TEXT NOT NULL,
 rule_version TEXT NOT NULL,requested_model TEXT NOT NULL,model_policy TEXT NOT NULL,
 status TEXT NOT NULL,context_state TEXT NOT NULL,data_state TEXT NOT NULL,rule_state TEXT NOT NULL,
 reasoning_state TEXT NOT NULL,quality_state TEXT NOT NULL,context_epoch INTEGER NOT NULL DEFAULT 0,
 current_unit INTEGER NOT NULL DEFAULT 0,total_units INTEGER NOT NULL DEFAULT 0,last_event_seq INTEGER NOT NULL DEFAULT 0,
 checkpoint_id TEXT,run_id TEXT,lease_owner TEXT,lease_until REAL,error_codes TEXT NOT NULL DEFAULT '[]',
 draft_path TEXT,verified_report_path TEXT,created_at REAL NOT NULL,updated_at REAL NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS idx_task_project ON tasks(project_id,id);
CREATE TABLE IF NOT EXISTS task_sources(task_id TEXT NOT NULL,source_id TEXT NOT NULL,PRIMARY KEY(task_id,source_id));
CREATE TABLE IF NOT EXISTS memories(
 id TEXT PRIMARY KEY,task_id TEXT NOT NULL,kind TEXT NOT NULL,trust TEXT NOT NULL,
 source_ref TEXT NOT NULL,content_hash TEXT NOT NULL,value_json TEXT NOT NULL,version INTEGER NOT NULL,updated_at REAL NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS idx_memory_unique ON memories(task_id,kind,content_hash);
CREATE TABLE IF NOT EXISTS evidence(
 id TEXT PRIMARY KEY,task_id TEXT NOT NULL,source_sha256 TEXT NOT NULL,locator TEXT NOT NULL,
 quote_hash TEXT NOT NULL,quote TEXT NOT NULL,verification_state TEXT NOT NULL,
 evidence_group_id TEXT NOT NULL,independence_state TEXT NOT NULL,updated_at REAL NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS idx_evidence_unique ON evidence(task_id,source_sha256,locator,quote_hash);
CREATE TABLE IF NOT EXISTS events(
 id TEXT PRIMARY KEY,task_id TEXT NOT NULL,seq INTEGER NOT NULL,type TEXT NOT NULL,payload TEXT NOT NULL,created_at REAL NOT NULL);
CREATE UNIQUE INDEX IF NOT EXISTS idx_event_seq ON events(task_id,seq);
CREATE TABLE IF NOT EXISTS model_calls(
 id TEXT PRIMARY KEY,task_id TEXT NOT NULL,unit INTEGER NOT NULL,epoch INTEGER NOT NULL,
 idempotency_key TEXT NOT NULL UNIQUE,requested_model TEXT NOT NULL,actual_model TEXT,
 status TEXT NOT NULL,request_hash TEXT NOT NULL,response_path TEXT,error_code TEXT,
started_at REAL NOT NULL,completed_at REAL);
CREATE INDEX IF NOT EXISTS idx_model_calls_task ON model_calls(task_id);
"""

@contextmanager
def db():
    ROOT.mkdir(parents=True,exist_ok=True)
    con=sqlite3.connect(DB,timeout=30,isolation_level=None)
    con.row_factory=sqlite3.Row
    con.execute("PRAGMA foreign_keys=ON")
    con.execute("PRAGMA busy_timeout=30000")
    try: yield con
    finally: con.close()

def init():
    with db() as c:
        c.executescript(SCHEMA)

def now(): return time.time()

def event(c,task_id,typ,payload):
    row=c.execute("SELECT last_event_seq FROM tasks WHERE id=?",(task_id,)).fetchone()
    seq=int(row["last_event_seq"])+1
    c.execute("UPDATE tasks SET last_event_seq=?,updated_at=? WHERE id=?",(seq,now(),task_id))
    c.execute("INSERT INTO events(id,task_id,seq,type,payload,created_at) VALUES(?,?,?,?,?,?)",
              (str(uuid.uuid4()),task_id,seq,typ,json.dumps(payload,ensure_ascii=False),now()))
    return seq

def create_project(title,project_id=None):
    pid=project_id or str(uuid.uuid4()); t=now()
    with db() as c:
        c.execute("INSERT OR IGNORE INTO projects(id,title,revision,created_at,updated_at) VALUES(?,?,?,?,?)",(pid,title,1,t,t))
    return project(pid)

def project(pid):
    with db() as c:
        r=c.execute("SELECT * FROM projects WHERE id=?",(pid,)).fetchone()
        return dict(r) if r else None

def sync_project(pid,title,base_revision):
    t=now()
    with db() as c:
        c.execute("BEGIN IMMEDIATE")
        r=c.execute("SELECT * FROM projects WHERE id=?",(pid,)).fetchone()
        if not r:
            if int(base_revision or 0)!=0: c.rollback(); raise ValueError("REVISION_CONFLICT")
            c.execute("INSERT INTO projects(id,title,revision,created_at,updated_at) VALUES(?,?,?,?,?)",(pid,title,1,t,t))
            return project(pid)
        if int(r["revision"])!=int(base_revision):
            c.rollback(); raise ValueError("REVISION_CONFLICT")
        c.execute("UPDATE projects SET title=?,revision=revision+1,updated_at=? WHERE id=?",(title,t,pid))
        r=c.execute("SELECT * FROM projects WHERE id=?",(pid,)).fetchone()
        return dict(r)

def add_source(pid,filename,mime,sha,size,obj,text_path,source_id=None):
    sid=source_id or str(uuid.uuid4()); t=now()
    with db() as c:
        r=c.execute("SELECT * FROM projects WHERE id=?",(pid,)).fetchone()
        if not r: raise ValueError("PROJECT_NOT_FOUND")
        existing=c.execute("SELECT * FROM sources WHERE sha256=?",(sha,)).fetchone()
        if existing:
            if existing["project_id"]!=pid: raise ValueError("SOURCE_ALREADY_BOUND_TO_OTHER_PROJECT")
            if int(existing["size_bytes"])!=int(size): raise ValueError("SOURCE_HASH_SIZE_MISMATCH")
            return dict(existing)
        c.execute("INSERT INTO sources(id,project_id,filename,mime,sha256,size_bytes,object_path,text_path,created_at) VALUES(?,?,?,?,?,?,?,?,?)",
                  (sid,pid,filename,mime,sha,int(size),str(obj),str(text_path),t))
        return source(sid)

def source(sid):
    with db() as c:
        r=c.execute("SELECT * FROM sources WHERE id=?",(sid,)).fetchone()
        return dict(r) if r else None

def sources_for_project(pid):
    with db() as c:return [dict(x) for x in c.execute("SELECT * FROM sources WHERE project_id=? ORDER BY created_at",(pid,))]

def sources_for_task(tid):
    with db() as c:return [dict(x) for x in c.execute("SELECT s.* FROM sources s JOIN task_sources ts ON ts.source_id=s.id WHERE ts.task_id=? ORDER BY s.created_at",(tid,))]

def create_task(pid,goal,source_ids,rules,rule_version,requested_model,policy,idempotency):
    t=now();tid=idempotency or str(uuid.uuid4())
    with db() as c:
        existing=c.execute("SELECT * FROM tasks WHERE id=?",(tid,)).fetchone()
        if existing:return dict(existing)
        if not project(pid):raise ValueError("PROJECT_NOT_FOUND")
        if not requested_model:raise ValueError("MODEL_REQUIRED")
        for sid in source_ids:
            s=c.execute("SELECT project_id FROM sources WHERE id=?",(sid,)).fetchone()
            if not s or s["project_id"]!=pid:raise ValueError("SOURCE_PROJECT_MISMATCH")
        c.execute("""INSERT INTO tasks(id,project_id,goal,rules,rule_version,requested_model,model_policy,status,context_state,data_state,rule_state,reasoning_state,quality_state,created_at,updated_at)
                     VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)""",
                  (tid,pid,goal,rules or "",rule_version or "",requested_model,json.dumps(policy or {},ensure_ascii=False),
                   "INGESTED","SAFE","PASS","UNKNOWN","NOT_EVALUATED","NOT_EVALUATED",t,t))
        for sid in source_ids:c.execute("INSERT INTO task_sources(task_id,source_id) VALUES(?,?)",(tid,sid))
        event(c,tid,"TASK_CREATED",{"source_ids":source_ids,"rule_version":rule_version or "","requested_model":requested_model})
        return task(tid)

def task(tid):
    with db() as c:
        r=c.execute("SELECT * FROM tasks WHERE id=?",(tid,)).fetchone()
        return dict(r) if r else None

def claim_task(worker_id,lease_seconds=45):
    with db() as c:
        c.execute("BEGIN IMMEDIATE"); t=time.time()
        r=c.execute("""SELECT id FROM tasks
                       WHERE status IN ('INGESTED','RUNNING','CHECKPOINTED','INTERRUPTED')
                       AND (lease_until IS NULL OR lease_until<?)
                       ORDER BY updated_at LIMIT 1""",(t,)).fetchone()
        if not r:c.commit();return None
        until=t+lease_seconds
        c.execute("UPDATE tasks SET lease_owner=?,lease_until=?,status=CASE WHEN status='INGESTED' THEN 'RUNNING' ELSE status END,updated_at=? WHERE id=?",
                  (worker_id,until,t,r["id"]))
        c.commit()
        return task(r["id"])

def renew(task_id,worker_id,lease_seconds=45):
    with db() as c:
        c.execute("UPDATE tasks SET lease_until=?,updated_at=? WHERE id=? AND lease_owner=?",(now()+lease_seconds,now(),task_id,worker_id))
        return c.rowcount==1

def release(task_id,worker_id):
    with db() as c:c.execute("UPDATE tasks SET lease_owner=NULL,lease_until=NULL WHERE id=? AND lease_owner=?",(task_id,worker_id))

def set_state(tid,**fields):
    fields["updated_at"]=now()
    with db() as c:
        cols=[];vals=[]
        for k,v in fields.items():
            cols.append(k+"=?");vals.append(v if not isinstance(v,(list,dict)) else json.dumps(v,ensure_ascii=False))
        vals.append(tid);c.execute("UPDATE tasks SET "+",".join(cols)+" WHERE id=?",vals)

def append_event(tid,typ,payload):
    with db() as c:
        c.execute("BEGIN IMMEDIATE");seq=event(c,tid,typ,payload);c.commit();return seq

def events(tid,after=0):
    with db() as c:return [dict(x) for x in c.execute("SELECT * FROM events WHERE task_id=? AND seq>? ORDER BY seq",(tid,after))]

def save_memory(tid,m):
    value=json.dumps(m.get("value",""),ensure_ascii=False)
    h=m.get("content_hash") or hashlib.sha256(value.encode()).hexdigest()
    with db() as c:
        c.execute("""INSERT OR REPLACE INTO memories(id,task_id,kind,trust,source_ref,content_hash,value_json,version,updated_at)
                     VALUES(?,?,?,?,?,?,?,?,?)""",
                  (m.get("id") or str(uuid.uuid4()),tid,m.get("kind","FACT"),m.get("trust","UNVERIFIED"),m.get("source_ref",""),h,value,int(m.get("version",0)),now()))

def trusted_memories(tid):
    with db() as c:
        rows=c.execute("SELECT * FROM memories WHERE task_id=? AND trust='VERIFIED' ORDER BY updated_at DESC",(tid,)).fetchall()
        return [dict(x) for x in rows]

def save_evidence(tid,e):
    with db() as c:
        c.execute("""INSERT OR REPLACE INTO evidence(id,task_id,source_sha256,locator,quote_hash,quote,verification_state,evidence_group_id,independence_state,updated_at)
                     VALUES(?,?,?,?,?,?,?,?,?,?)""",
                  (e.get("id") or str(uuid.uuid4()),tid,e.get("source_sha256",""),e.get("locator",""),e.get("quote_hash",""),
                   e.get("quote",""),e.get("verification_state","UNKNOWN"),e.get("evidence_group_id",""),e.get("independence_state","UNKNOWN"),now()))

def evidence(tid):
    with db() as c:return [dict(x) for x in c.execute("SELECT * FROM evidence WHERE task_id=? ORDER BY updated_at DESC",(tid,))]

def model_call(tid,unit,epoch,model,request_hash):
    key=f"{tid}:{unit}:{epoch}"
    with db() as c:
        r=c.execute("SELECT * FROM model_calls WHERE idempotency_key=?",(key,)).fetchone()
        if r:return dict(r)
        c.execute("""INSERT INTO model_calls(id,task_id,unit,epoch,idempotency_key,requested_model,status,request_hash,started_at)
                     VALUES(?,?,?,?,?,?,?,?,?)""",
                  (str(uuid.uuid4()),tid,unit,epoch,key,model,"IN_FLIGHT",request_hash,now()))
        return dict(c.execute("SELECT * FROM model_calls WHERE idempotency_key=?",(key,)).fetchone())

def finish_model_call(call_id,status,actual_model=None,response_path=None,error_code=None):
    with db() as c:c.execute("UPDATE model_calls SET status=?,actual_model=?,response_path=?,error_code=?,completed_at=? WHERE id=?",
                             (status,actual_model,response_path,error_code,now(),call_id))

def mark_checkpoint(tid,epoch,unit,checkpoint_id,context_state="ROLLED"):
    set_state(tid,context_epoch=epoch,current_unit=unit,checkpoint_id=checkpoint_id,context_state=context_state,status="CHECKPOINTED")
    append_event(tid,"CHECKPOINT_COMMITTED",{"checkpoint_id":checkpoint_id,"context_epoch":epoch,"current_unit":unit})

def read_text(source_row):
    return Path(source_row["text_path"]).read_text(encoding="utf-8",errors="ignore")

def file_sha(path):
    h=hashlib.sha256();n=0
    with open(path,"rb") as f:
        for b in iter(lambda:f.read(1024*1024),b""):h.update(b);n+=len(b)
    return h.hexdigest(),n

init()
