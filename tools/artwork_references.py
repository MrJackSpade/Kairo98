"""Lossless identifiers for catalog artwork, with URL templates owned by code."""
import copy
import hashlib
import re

BASE = "https://images.launchbox-app.com/"
UUID = re.compile(r"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")
URL = re.compile(re.escape(BASE) + r"(r2_)?(" + UUID.pattern + r")\.(jpg|png)")
PATH = re.compile(r"art/catalog/pc(88|98)/([1-9][0-9]*)/(box|screenshot)-([0-9a-f]{12})\.webp")
KINDS = {"boxArt": "box", "preview": "screenshot"}


def expand(art):
    if not isinstance(art, dict):
        raise ValueError("invalid artwork")
    if not (art.keys() & {"platform", "game"}) and not any(isinstance(v, dict) for v in art.values()):
        return copy.deepcopy(art)
    if (not {"platform", "game"} < art.keys() or art.keys() - {"platform", "game", *KINDS}
            or type(art["platform"]) is not int or art["platform"] not in (88, 98)
            or type(art["game"]) is not int or not 1 <= art["game"] <= 2147483647):
        raise ValueError("invalid artwork group")
    result = {}
    for kind, label in KINDS.items():
        if kind not in art:
            continue
        ref = art[kind]
        if (not isinstance(ref, dict) or set(ref) != {"id", "format", "revision"}
                or not isinstance(ref["id"], str) or not UUID.fullmatch(ref["id"])
                or type(ref["format"]) is not int or ref["format"] not in (0, 1)
                or type(ref["revision"]) is not int or ref["revision"] not in (0, 2)):
            raise ValueError("invalid artwork reference")
        url = BASE + ("r2_" if ref["revision"] == 2 else "") + ref["id"] + (".png" if ref["format"] else ".jpg")
        token = hashlib.sha256(url.encode("utf-8")).hexdigest()[:12]
        result[kind] = f'art/catalog/pc{art["platform"]}/{art["game"]}/{label}-{token}.webp'
        result[kind + "Url"] = url
    return result


def compact(art):
    original = expand(art)
    if not original:
        return {}
    result, groups = {}, set()
    if original.keys() - {"boxArt", "preview", "boxArtUrl", "previewUrl"}:
        raise ValueError("unknown artwork fields")
    for kind, label in KINDS.items():
        if kind not in original and kind + "Url" not in original:
            continue
        path = PATH.fullmatch(original.get(kind, ""))
        url = URL.fullmatch(original.get(kind + "Url", ""))
        if not path or not url or path[3] != label:
            raise ValueError("artwork cannot be represented by catalog templates")
        groups.add((int(path[1]), int(path[2])))
        result[kind] = {"id": url[2], "format": int(url[3] == "png"), "revision": 2 if url[1] else 0}
    if len(groups) != 1:
        raise ValueError("artwork pair uses different asset groups")
    platform, game = groups.pop()
    result = {"platform": platform, "game": game, **result}
    if expand(result) != original:
        raise ValueError("artwork cache paths or URLs failed round-trip")
    return result
