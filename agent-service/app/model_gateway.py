import os
import json
import httpx

class ModelError(Exception):
    def __init__(self, code, message, uncertain=False):
        super().__init__(message)
        self.code = code
        self.uncertain = uncertain

def call_model(model, messages):
    base = os.getenv("MODEL_API_BASE", "").rstrip("/")
    key = os.getenv("MODEL_API_KEY", "")
    if not base or not key:
        raise ModelError("MODEL_UNAVAILABLE", "没有配置 MODEL_API_BASE / MODEL_API_KEY")
    url = base + "/v1/chat/completions"
    payload = {"model": model, "messages": messages, "temperature": 0.1}
    try:
        with httpx.Client(timeout=httpx.Timeout(180.0, connect=15.0)) as client:
            response = client.post(url, headers={
                "Authorization": "Bearer " + key,
                "Content-Type": "application/json"
            }, json=payload)
    except httpx.RequestError as exc:
        raise ModelError("MODEL_NETWORK_UNCERTAIN", "模型请求网络状态不确定；为避免重复计费，任务暂停。", True) from exc
    if response.status_code < 200 or response.status_code >= 300:
        code = "MODEL_CONTEXT_LIMIT" if response.status_code == 400 and "context" in response.text.lower() else "MODEL_HTTP_" + str(response.status_code)
        raise ModelError(code, "模型服务返回 HTTP " + str(response.status_code))
    try:
        data = response.json()
    except Exception as exc:
        raise ModelError("MODEL_INVALID_RESPONSE", "模型响应不是 JSON") from exc
    choices = data.get("choices") or []
    if not choices:
        raise ModelError("MODEL_EMPTY_RESPONSE", "模型没有返回 choices")
    text = choices[0].get("message", {}).get("content")
    if not isinstance(text, str) or not text.strip():
        raise ModelError("MODEL_EMPTY_RESPONSE", "模型没有返回文本")
    actual = data.get("model")
    if not actual:
        raise ModelError("MODEL_ID_UNKNOWN", "服务商没有返回实际模型 ID，STRICT 模式拒绝")
    if actual != model:
        raise ModelError("MODEL_ID_MISMATCH", "实际模型 " + str(actual) + " 与请求模型 " + model + " 不一致")
    return text, actual, data.get("usage") or {}

def parse_json(text):
    s = text.strip()
    if s.startswith("json:"):
        s = s[5:].strip()
    return json.loads(s)
