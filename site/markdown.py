"""A converter for exactly the Markdown subset the guide (docs/guide/) uses, for the site build
(site/build_site.py). It knows ATX headings, paragraphs, fenced code blocks, bullet and numbered
lists (nested by indentation), pipe tables, inline code, links, bold and emphasis with asterisks,
and backslash escapes. Anything else (raw HTML, character references, block quotes, rules, images,
indented code, reference links, bare URLs, underscore emphasis, setext headings, hard line breaks,
numbered lists that do not start at 1, headings whose anchor GitHub would derive differently) is an
error naming the file and the line, so the build fails instead of emitting something GitHub would
render differently.

Python 3 standard library only. The module is loaded by its path (site/build_site.py runs under
python3 -I, which does not put the script's directory on the module path).
"""

import re


class MarkdownError(Exception):
    def __init__(self, file, line, message):
        super().__init__(f"{file}:{line}: {message}")


def escape_html(s):
    return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace('"', "&quot;")


def slug(text):
    """The anchor GitHub derives from a heading's text: lower case, punctuation removed, spaces to
    hyphens."""
    return re.sub(r"[^a-z0-9 _-]", "", text.lower()).replace(" ", "-")


# ------------------------------------------------------------------ inline

_ESCAPABLE = set("\\`*_{}[]()#+-.!|<>")
_CHAR_REF = re.compile(r"&(#[0-9]+|#[xX][0-9a-fA-F]+|[A-Za-z][A-Za-z0-9]*);")


def parse_inline(src, file, line):
    """Parse inline Markdown into tokens: {"t": "text"|"code"|"strong"|"em"|"link", ...}. Fails on
    raw HTML, character references, images, bare URLs, reference links and underscore emphasis."""

    def fail(m):
        raise MarkdownError(file, line, m)

    out = []
    text = []

    def flush():
        if text:
            out.append({"t": "text", "v": "".join(text)})
            text.clear()

    n_src = len(src)
    i = 0
    while i < n_src:
        c = src[i]
        nxt = src[i + 1] if i + 1 < n_src else ""
        if c == "\\" and nxt in _ESCAPABLE and nxt != "":
            text.append(nxt)
            i += 2
            continue
        if c == "`":
            n = 0
            while i + n < n_src and src[i + n] == "`":
                n += 1
            fence = "`" * n
            end = src.find(fence, i + n)
            if end < 0:
                fail("unclosed code span")
            code = src[i + n:end]
            if n > 1 and code.startswith(" ") and code.endswith(" "):
                code = code[1:-1]
            flush()
            out.append({"t": "code", "v": code.replace("\\|", "|")})
            i = end + n
            continue
        if c == "!" and nxt == "[":
            fail("images are not supported")
        if c == "<" and re.match(r"[A-Za-z/!?]", nxt):
            fail("raw HTML is not supported (put it in a code span)")
        if c == "&" and _CHAR_REF.match(src, i):
            fail("character references are not supported (write the character)")
        if c == "[":
            close = _find_close(src, i, "[", "]")
            if close < 0:
                fail("unclosed [")
            after = src[close + 1] if close + 1 < n_src else ""
            if after == "(":
                end = _find_close(src, close + 1, "(", ")")
                if end < 0:
                    fail("unclosed link target")
                href = src[close + 2:end]
                if re.search(r"\s", href):
                    fail("a link target with a space or a title is not supported")
                flush()
                out.append({"t": "link", "href": href, "children": parse_inline(src[i + 1:close], file, line)})
                i = end + 1
                continue
            if after == "[":
                fail("reference links are not supported")
            text.append(c)
            i += 1
            continue
        if c == "*":
            strong = nxt == "*"
            marker = "**" if strong else "*"
            end = src.find(marker, i + len(marker))
            inner = "" if end < 0 else src[i + len(marker):end]
            after = src[end + 1] if 0 <= end and end + 1 < n_src else ""
            if (end > i + len(marker) and not re.match(r"\s", inner) and not re.search(r"\s$", inner)
                    and (strong or after != "*")):
                flush()
                out.append({"t": "strong" if strong else "em", "children": parse_inline(inner, file, line)})
                i = end + len(marker)
                continue
            text.append(c)
            i += 1
            continue
        if c == "_" and (i == 0 or re.match(r"[\s(]", src[i - 1])) and re.match(r"\S", nxt):
            fail("underscore emphasis is not supported")
        if re.match(r"https?://", src[i:]) and not re.search(r"[(<]$", src[:i]):
            fail("a bare URL is not supported (use a link)")
        text.append(c)
        i += 1
    flush()
    return out


def _find_close(s, start, open_, close):
    depth = 0
    i = start
    while i < len(s):
        ch = s[i]
        if ch == "\\":
            i += 2
            continue
        if ch == "`":
            e = s.find("`", i + 1)
            if e < 0:
                return -1
            i = e + 1
            continue
        if ch == open_:
            depth += 1
        elif ch == close:
            depth -= 1
            if depth == 0:
                return i
        i += 1
    return -1


def inline_text(tokens):
    """Plain text of inline tokens (code spans keep their content, links their text)."""
    return "".join(k["v"] if k["t"] in ("text", "code") else inline_text(k["children"]) for k in tokens)


def inline_html(tokens, resolve_link):
    """HTML of inline tokens; resolve_link(href) returns the href to emit."""
    parts = []
    for k in tokens:
        t = k["t"]
        if t == "text":
            parts.append(escape_html(k["v"]))
        elif t == "code":
            parts.append(f"<code>{escape_html(k['v'])}</code>")
        elif t == "strong":
            parts.append(f"<strong>{inline_html(k['children'], resolve_link)}</strong>")
        elif t == "em":
            parts.append(f"<em>{inline_html(k['children'], resolve_link)}</em>")
        elif t == "link":
            parts.append(f'<a href="{escape_html(resolve_link(k["href"]))}">{inline_html(k["children"], resolve_link)}</a>')
        else:
            raise ValueError("unknown inline token")
    return "".join(parts)


# ------------------------------------------------------------------ blocks

LIST_ITEM = re.compile(r"^( *)([-*]|\d+\.) +(.*)$")


def parse(src, file):
    """Parse a document into blocks: heading {level, inline, text, id, line}, paragraph {inline},
    code {lang, info, text, line}, list {ordered, items: [{blocks}]}, table {head, rows, line}."""
    lines = src.replace("\r\n", "\n").split("\n")
    blocks = []
    ids = {}

    def fail(n, m):
        raise MarkdownError(file, n + 1, m)

    def unique_id(text):
        base = slug(text)
        n = ids.get(base, 0)
        ids[base] = n + 1
        return base if n == 0 else f"{base}-{n}"

    def check_block_start(line, n):
        if re.match(r"^ {0,3}>", line):
            fail(n, "block quotes are not supported")
        if re.match(r"^ {0,3}([-*_])( *\1){2,} *$", line):
            fail(n, "horizontal rules are not supported")
        if re.match(r"^ {0,3}<[A-Za-z!/]", line):
            fail(n, "raw HTML is not supported")
        if line.startswith("\t"):
            fail(n, "tabs are not supported")

    # A list ends at the first blank line (loose lists are not supported). Nested lists are items
    # indented to the parent item's text; a nested list must be the last part of its item.
    def parse_list(start):
        group = []
        n = start
        while n < len(lines) and lines[n].strip() != "":
            if re.match(r"^ *```", lines[n]):
                fail(n, "code blocks inside list items are not supported")
            group.append((lines[n], n))
            n += 1
        if n + 1 < len(lines) and LIST_ITEM.match(lines[n + 1]) and len(LIST_ITEM.match(lines[n + 1]).group(1)) == 0:
            fail(n, "loose lists (blank lines between items) are not supported")
        return build_list(group), n

    def build_list(group):
        first = LIST_ITEM.match(group[0][0])
        indent = len(first.group(1))
        ordered = first.group(2)[0].isdigit()
        if ordered and first.group(2) != "1.":
            fail(group[0][1], "a numbered list must start at 1 (the converter does not carry other start numbers)")
        items = []
        k = 0
        while k < len(group):
            m = LIST_ITEM.match(group[k][0])
            if not m or len(m.group(1)) != indent:
                fail(group[k][1], "unexpected indentation in a list")
            if m.group(2)[0].isdigit() != ordered:
                fail(group[k][1], "a list mixes bullets and numbers")
            content_indent = indent + len(m.group(2)) + 1
            para = [(m.group(3), group[k][1])]
            nested = []
            k += 1
            while k < len(group):
                text, n = group[k]
                s = LIST_ITEM.match(text)
                lead = len(text) - len(text.lstrip())
                if s and len(s.group(1)) == indent:
                    break
                if s and len(s.group(1)) < indent:
                    fail(n, "unexpected indentation in a list")
                if s or nested:
                    if lead < content_indent:
                        fail(n, "a nested line must be indented to the item's text")
                    nested.append(group[k])
                else:
                    if lead < content_indent:
                        fail(n, "a continuation line must be indented to the item's text")
                    para.append(group[k])
                k += 1
            item_blocks = [{"type": "paragraph",
                            "inline": parse_inline(" ".join(p[0].strip() for p in para), file, para[0][1] + 1),
                            "line": para[0][1] + 1}]
            if nested:
                if not LIST_ITEM.match(nested[0][0]):
                    fail(nested[0][1], "text after a nested list is not supported")
                item_blocks.append(build_list(nested))
            items.append({"blocks": item_blocks})
        return {"type": "list", "ordered": ordered, "items": items}

    def split_row(line, n):
        s = line.strip()
        if not s.startswith("|") or not s.endswith("|") or len(s) < 2:
            fail(n, "a table row must start and end with |")
        s = s[1:-1]
        cells = []
        cur = []
        k = 0
        while k < len(s):
            if s[k] == "\\" and k + 1 < len(s) and s[k + 1] == "|":
                cur.append("\\|")
                k += 2
                continue
            if s[k] == "|":
                cells.append("".join(cur).strip())
                cur = []
                k += 1
                continue
            cur.append(s[k])
            k += 1
        cells.append("".join(cur).strip())
        return cells

    # a line ending in two spaces or a backslash is a hard line break on GitHub; the converter has none
    fenced = False
    for n, line in enumerate(lines):
        if fenced:
            fenced = line != "```"
        elif line.startswith("```"):
            fenced = True
        elif line.strip() and (line.endswith("  ") or line.endswith("\\")):
            fail(n, "a hard line break (two trailing spaces or a trailing backslash) is not supported")

    i = 0
    while i < len(lines):
        line = lines[i]
        if line.strip() == "":
            i += 1
            continue
        check_block_start(line, i)
        if re.match(r"^ {4,}\S", line):
            fail(i, "indented code blocks are not supported (use a fenced block)")
        fence = re.match(r"^```(.*)$", line)
        if fence:
            info = fence.group(1).strip()
            start = i
            body = []
            i += 1
            while i < len(lines) and lines[i] != "```":
                body.append(lines[i])
                i += 1
            if i >= len(lines):
                fail(start, "unclosed code block")
            i += 1
            lang = info.split()[0] if info else ""
            if not lang:
                fail(start, "a code block needs a language")
            blocks.append({"type": "code", "lang": lang, "info": info, "text": "\n".join(body), "line": start + 1})
            continue
        if re.match(r"^ {0,3}~{3}", line):
            fail(i, "tilde fences are not supported")
        heading = re.match(r"^(#{1,6}) +(.+?) *#* *$", line)
        if heading:
            inline = parse_inline(heading.group(2), file, i + 1)
            text = inline_text(inline)
            if any(ch.isalnum() and not ch.isascii() for ch in text):
                fail(i, "a heading with a letter or digit outside ASCII (its anchor would differ from GitHub's)")
            blocks.append({"type": "heading", "level": len(heading.group(1)), "inline": inline, "text": text,
                           "id": unique_id(text), "line": i + 1})
            i += 1
            continue
        if line.startswith("#"):
            fail(i, "a heading needs a space after #")
        if LIST_ITEM.match(line):
            if len(LIST_ITEM.match(line).group(1)) > 0:
                fail(i, "a list must start at the left margin")
            block, i = parse_list(i)
            blocks.append(block)
            continue
        if line.startswith("|"):
            start = i
            head = split_row(line, i)
            if i + 1 >= len(lines) or not re.match(r"^\|( *:?-+:? *\|)+ *$", lines[i + 1].strip()):
                fail(i, "a table needs a separator row")
            sep = split_row(lines[i + 1], i + 1)
            if len(sep) != len(head):
                fail(i + 1, "the separator row has another number of cells")
            i += 2
            rows = []
            while i < len(lines) and lines[i].startswith("|"):
                cells = split_row(lines[i], i)
                if len(cells) != len(head):
                    fail(i, f"a row with {len(cells)} cells in a table of {len(head)}")
                rows.append([parse_inline(c, file, i + 1) for c in cells])
                i += 1
            blocks.append({"type": "table", "head": [parse_inline(c, file, start + 1) for c in head], "rows": rows,
                           "line": start + 1})
            continue
        # paragraph: until a blank line or another block
        start = i
        para = []
        while (i < len(lines) and lines[i].strip() != "" and not re.match(r"^(```|#{1,6} |\|)", lines[i])
               and not (LIST_ITEM.match(lines[i]) and not para)):
            if i > start and LIST_ITEM.match(lines[i]):
                fail(i, "a list must be separated from a paragraph by a blank line")
            if i > start and re.match(r"^(=+|-+) *$", lines[i]):
                fail(i, "setext headings are not supported")
            check_block_start(lines[i], i)
            para.append(lines[i].strip())
            i += 1
        blocks.append({"type": "paragraph", "inline": parse_inline(" ".join(para), file, start + 1), "line": start + 1})
    return blocks


def links(blocks):
    """Every link target of a block list, with its line."""
    out = []

    def walk_inline(tokens, line):
        for k in tokens:
            if k["t"] == "link":
                out.append({"href": k["href"], "line": line})
            if "children" in k:
                walk_inline(k["children"], line)

    def walk(bs):
        for b in bs:
            if b["type"] in ("heading", "paragraph"):
                walk_inline(b["inline"], b["line"])
            elif b["type"] == "list":
                for it in b["items"]:
                    walk(it["blocks"])
            elif b["type"] == "table":
                for c in b["head"]:
                    walk_inline(c, b["line"])
                for r in b["rows"]:
                    for c in r:
                        walk_inline(c, b["line"])

    walk(blocks)
    return out
