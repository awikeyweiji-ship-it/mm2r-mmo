import hashlib
import json
import operator
import re
from . import store

def norm(value):
    return re.sub(r"\s+", " ", str(value or "")).strip()

def quote_hash(quote):
    return hashlib.sha256(norm(quote).encode("utf-8")).hexdigest()

def validate_output(task, source_rows, payload):
    errors = []
    evidence = []
    memories = []
    claims = payload.get("claims")
    if not isinstance(claims, list):
        errors.append("OUTPUT_CLAIMS_INVALID")
        claims = []

    by_id = {s["id"]: s for s in source_rows}
    for index, claim in enumerate(claims):
        if not isinstance(claim, dict):
            errors.append("CLAIM_" + str(index) + "_INVALID")
            continue
        refs = claim.get("source_refs")
        if not isinstance(refs, list) or not refs:
            errors.append("CLAIM_" + str(index) + "_NO_EVIDENCE")
            continue
        claim_error_before = len(errors)
        for ref_index, ref in enumerate(refs):
            if not isinstance(ref, dict):
                errors.append("CLAIM_" + str(index) + "_REF_" + str(ref_index) + "_INVALID")
                continue
            sid = str(ref.get("source_id", ""))
            locator = str(ref.get("locator", ""))
            quote = str(ref.get("quote", ""))
            src = by_id.get(sid)
            if not src:
                errors.append("CLAIM_" + str(index) + "_SOURCE_UNKNOWN")
                continue
            text = store.read_text(src)
            if not quote or norm(quote) not in norm(text):
                errors.append("CLAIM_" + str(index) + "_QUOTE_NOT_FOUND")
                continue
            qh = quote_hash(quote)
            group = hashlib.sha256((src["sha256"] + "|" + locator).encode()).hexdigest()[:24]
            evidence.append({
                "task_id": task["id"], "source_sha256": src["sha256"], "locator": locator,
                "quote_hash": qh, "quote": quote, "verification_state": "PASS",
                "evidence_group_id": group, "independence_state": "UNKNOWN"
            })
        if len(errors) == claim_error_before:
            value = claim.get("value", claim.get("text", ""))
            memories.append({
                "kind": claim.get("kind", "FACT"), "trust": "VERIFIED",
                "source_ref": str(refs[0].get("source_id", "")) + ":" + str(refs[0].get("locator", "")),
                "value": value
            })

    rule_state = "UNKNOWN"
    rules = (task.get("rules") or "").strip()
    if rules:
        try:
            rule = json.loads(rules)
        except Exception:
            rule = {"required_evidence": True, "rule_text": rules}
        if rule.get("required_evidence", True) and not evidence:
            errors.append("RULE_REQUIRED_EVIDENCE")
        for item in rule.get("numeric_constraints", []):
            if not numeric_constraint(payload, item):
                errors.append("RULE_NUMERIC_CONSTRAINT")
        for item in rule.get("forbidden_values", []):
            if forbidden(payload, item):
                errors.append("RULE_FORBIDDEN_VALUE")
        rule_state = "PASS" if not errors else "FAIL"

    reasoning = "UNKNOWN" if not claims else ("FAIL" if conflict(claims) else "PASS")
    return errors, evidence, memories, rule_state, reasoning

def resolve(obj, path):
    current = [obj]
    for part in path.split("."):
        nxt = []
        for item in current:
            if isinstance(item, list):
                for value in item:
                    if part == "*":
                        nxt.append(value)
                    elif isinstance(value, dict) and part in value:
                        nxt.append(value[part])
            elif isinstance(item, dict) and part in item:
                nxt.append(item[part])
        current = nxt
    return current

def numeric_constraint(payload, item):
    values = resolve(payload, str(item.get("path", "")))
    if not values:
        return False
    try:
        target = float(item.get("value"))
    except Exception:
        return False
    fn = {">=": operator.ge, "<=": operator.le, ">": operator.gt, "<": operator.lt, "==": operator.eq}.get(item.get("op"))
    if not fn:
        return False
    try:
        return all(fn(float(value), target) for value in values)
    except (TypeError, ValueError):
        return False

def forbidden(payload, item):
    return any(str(value) == str(item.get("equals")) for value in resolve(payload, str(item.get("path", ""))))

def conflict(claims):
    seen = {}
    for claim in claims:
        if not isinstance(claim, dict):
            continue
        key = claim.get("claim_key")
        if key is None:
            continue
        value = json.dumps(claim.get("value", claim.get("text")), ensure_ascii=False, sort_keys=True)
        if key in seen and seen[key] != value:
            return True
        seen[key] = value
    return False

def final_gate(task):
    ev = store.evidence(task["id"])
    mem = store.trusted_memories(task["id"])
    if task["data_state"] != "PASS":
        return False, "DATA_NOT_PASS"
    if task["rule_state"] != "PASS":
        return False, "RULE_NOT_PASS"
    if task["reasoning_state"] == "FAIL":
        return False, "REASONING_CONFLICT"
    if not ev or not mem:
        return False, "NO_VERIFIED_EVIDENCE"
    return True, "PASS"
