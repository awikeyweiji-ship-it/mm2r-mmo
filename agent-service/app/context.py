import hashlib
import re
from dataclasses import dataclass
from . import store

@dataclass
class Unit:
    index: int
    source_id: str
    sha256: str
    locator: str
    text: str

def split_text(text: str, max_chars: int = 6000, overlap: int = 400):
    text = text.replace("\r\n", "\n").replace("\r", "\n")
    paragraphs = [p.strip() for p in re.split(r"\n{2,}", text) if p.strip()]
    units = []
    buf = ""
    for p in paragraphs:
        if len(p) > max_chars:
            if buf:
                units.append(buf)
                buf = ""
            i = 0
            while i < len(p):
                j = min(len(p), i + max_chars)
                units.append(p[i:j])
                i = max(j - overlap, i + 1)
            continue
        candidate = p if not buf else buf + "\n\n" + p
        if len(candidate) > max_chars and buf:
            units.append(buf)
            buf = p
        else:
            buf = candidate
    if buf:
        units.append(buf)
    return units

def build_units(source_rows):
    out = []
    idx = 0
    for s in source_rows:
        text = store.read_text(s)
        for part_no, part in enumerate(split_text(text)):
            out.append(Unit(idx, s["id"], s["sha256"], "part:" + str(part_no), part))
            idx += 1
    return out

def tokens(text):
    return set(re.findall(r"[\w\u4e00-\u9fff]{2,}", str(text or "").lower()))

def select_memories(memories, query, max_items=12):
    q = tokens(query)
    def score(m):
        value = str(m.get("value_json", ""))
        return (len(q & tokens(value)), 1 if m.get("kind") == "DEFINITION" else 0, float(m.get("updated_at", 0)))
    return sorted(memories, key=score, reverse=True)[:max_items]

def compile_context(task, unit, memories, context_limit):
    header = (
        "TASK GOAL:\n" + task["goal"] +
        "\n\nRULES:\n" + (task["rules"] or "NONE") +
        "\n\nCURRENT SOURCE:\n" + unit.text +
        "\n\nVERIFIED MEMORY:\n"
    )
    selected = select_memories(memories, task["goal"] + " " + unit.text)
    memory_text = "\n".join(
        "- [" + m["kind"] + "] " + m["value_json"] + " (source=" + m["source_ref"] + ")"
        for m in selected
    )
    prompt = header + memory_text + "\n\nReturn JSON only."
    estimate = max(1, len(prompt) // 4)
    if estimate > context_limit:
        selected = select_memories(memories, unit.text, max_items=4)
        memory_text = "\n".join(
            "- [" + m["kind"] + "] " + m["value_json"] + " (source=" + m["source_ref"] + ")"
            for m in selected
        )
        prompt = header.split("\n\nVERIFIED MEMORY:")[0] + "\n\nVERIFIED MEMORY:\n" + memory_text + "\n\nReturn JSON only."
        estimate = max(1, len(prompt) // 4)
    return prompt, estimate, selected

def request_hash(prompt, model):
    return hashlib.sha256((model + "\n" + prompt).encode("utf-8")).hexdigest()
