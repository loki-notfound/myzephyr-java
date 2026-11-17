"""Checks a built site (site/build_site.py, bin/make_site.sh): every guide file has its page and
every page under guide/ its guide file; every page of the site (every .html file outside javadoc/
and maven/) is well-formed (no unclosed or mismatched tag), starts with the doctype and
<html lang="en">, has the strict policy in its head, one level-1 heading, headings without skipped
levels, unique ids, a skip link to #main, no lateo family bar, no script, no style element or
attribute, no event handler attribute, no element the site does not use, no javascript: URL, no
absolute or protocol-relative path, no http:// at all (code excepted) and https:// only as the
target of a link (<a href>); every callout is a div of class "lt-callout" plus exactly one of
"lt-callout-info", "lt-callout-warning" or "lt-callout-danger", with role="note", whose first
element is its title, a p of class "lt-callout-title", and a guide page has at most two; every
relative link and every anchor resolves; the stylesheets load nothing from elsewhere. A link into
javadoc/ or maven/ is checked when that directory exists in the site and accepted when it does
not (the guide-only build).

Usage: python3 -I site/check_site.py --site <dir>
Python 3 standard library only; exit status 0 when every check passes, 1 otherwise.
"""

import html.parser
import os
import posixpath
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
GUIDE = os.path.join(ROOT, "docs", "guide")
POLICY = "default-src 'none'; style-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'"
VOID = {"area", "base", "br", "col", "embed", "hr", "img", "input", "link", "meta", "source", "track", "wbr"}
UNUSED = {"iframe", "object", "embed", "img", "video", "audio", "source", "form", "input", "base", "frame", "frameset",
          "applet", "picture", "canvas", "svg", "math", "template", "dialog", "button", "select", "textarea"}
URL_ATTRS = {"href", "src", "srcset", "action", "poster", "data", "formaction", "xlink:href", "cite", "background",
             "manifest", "ping"}
# directories of the release site that this checker does not check page by page
FOREIGN = ("javadoc", "maven")
CALLOUT_KINDS = {"lt-callout-info", "lt-callout-warning", "lt-callout-danger"}
MAX_CALLOUTS = 2


class Page(html.parser.HTMLParser):
    """One page, parsed: its problems, ids, headings, URL attributes and the text outside code."""

    def __init__(self):
        super().__init__(convert_charrefs=True)
        self.problems = []
        self.stack = []
        self.ids = []
        self.headings = []
        self.urls = []  # (tag, attribute, value, line)
        self.code_depth = 0
        self.text_outside_code = []
        self.skip_link = False
        self.policy = False
        self.callouts = 0
        self.callout_title_due = None  # the line of a callout whose title has not come yet

    def where(self):
        return f"line {self.getpos()[0]}"

    def handle_starttag(self, tag, attrs):
        self._tag(tag, attrs)
        if tag not in VOID:
            self.stack.append((tag, self.getpos()[0]))
        if tag == "code":
            self.code_depth += 1

    def handle_startendtag(self, tag, attrs):
        self._tag(tag, attrs)

    def _tag(self, tag, attrs):
        classes = next(((v or "").split() for n, v in attrs if n == "class"), [])
        if self.callout_title_due is not None:
            if not (tag == "p" and classes == ["lt-callout-title"]):
                self.problems.append(f"line {self.callout_title_due}: a callout whose first element is not its "
                                     "title (p class=\"lt-callout-title\")")
            self.callout_title_due = None
        elif "lt-callout-title" in classes:
            self.problems.append(f"{self.where()}: a callout title outside a callout")
        if any(c == "lt-callout" or c.startswith("lt-callout-") for c in classes) and "lt-callout-title" not in classes:
            role = next((v for n, v in attrs if n == "role"), None)
            if (tag != "div" or len(classes) != 2 or classes[0] != "lt-callout" or classes[1] not in CALLOUT_KINDS
                    or role != "note"):
                self.problems.append(f"{self.where()}: a callout that is not <div class=\"lt-callout lt-callout-info|"
                                     f"warning|danger\" role=\"note\">")
            self.callouts += 1
            self.callout_title_due = self.getpos()[0]
        a = {}
        for name, value in attrs:
            value = "" if value is None else value
            if name in a:
                self.problems.append(f"{self.where()}: attribute {name} twice on <{tag}>")
            a[name] = value
            if name == "style":
                self.problems.append(f"{self.where()}: a style attribute on <{tag}>")
            if name.startswith("on"):
                self.problems.append(f"{self.where()}: an event handler attribute {name} on <{tag}>")
            if name == "id":
                self.ids.append(value)
            if name in URL_ATTRS:
                self.urls.append((tag, name, value, self.getpos()[0]))
            elif "https://" in value.lower() or "http://" in value.lower():
                self.problems.append(f"{self.where()}: a URL in <{tag} {name}>: a resource from elsewhere")
            if "javascript:" in value.lower():
                self.problems.append(f"{self.where()}: a javascript: URL in <{tag} {name}>")
            if name == "class" and "lt-family-bar" in value.split():
                self.problems.append(f"{self.where()}: the lateo family bar")
        if tag == "script":
            self.problems.append(f"{self.where()}: a script element")
        if tag == "style":
            self.problems.append(f"{self.where()}: a style element")
        if tag in UNUSED:
            self.problems.append(f"{self.where()}: an element the site does not use: <{tag}>")
        if re.fullmatch(r"h[1-6]", tag):
            self.headings.append((int(tag[1]), self.getpos()[0]))
        if tag == "a" and a.get("class") == "lt-skip-link" and a.get("href") == "#main":
            self.skip_link = True
        if tag == "meta" and a.get("http-equiv") == "Content-Security-Policy" and a.get("content") == POLICY:
            self.policy = True

    def handle_endtag(self, tag):
        if tag in VOID:
            self.problems.append(f"{self.where()}: an end tag for the void element <{tag}>")
            return
        if not self.stack:
            self.problems.append(f"{self.where()}: </{tag}> without an open element")
            return
        open_tag, line = self.stack.pop()
        if open_tag != tag:
            self.problems.append(f"{self.where()}: </{tag}> closes <{open_tag}> of line {line}")
        if tag == "code":
            self.code_depth = max(0, self.code_depth - 1)

    def handle_data(self, data):
        if not self.code_depth:
            self.text_outside_code.append((data, self.getpos()[0]))

    def handle_comment(self, data):
        self.problems.append(f"{self.where()}: a comment")

    def handle_pi(self, data):
        self.problems.append(f"{self.where()}: a processing instruction")

    def close_page(self):
        self.close()
        if self.callout_title_due is not None:
            self.problems.append(f"line {self.callout_title_due}: a callout without a title")
        for tag, line in self.stack:
            self.problems.append(f"<{tag}> of line {line} is never closed")


def walk(base):
    """Every file under base as a sorted list of relative POSIX paths."""
    out = []
    for dirpath, dirnames, filenames in os.walk(base):
        dirnames.sort()
        for name in sorted(filenames):
            out.append(os.path.relpath(os.path.join(dirpath, name), base).replace(os.sep, "/"))
    return sorted(out)


def main(argv):
    if len(argv) != 2 or argv[0] != "--site":
        print("usage: check_site.py --site <dir>", file=sys.stderr)
        return 2
    site = argv[1]
    if not os.path.isdir(site):
        print(f"site check failed: {site} is not a directory", file=sys.stderr)
        return 1
    failures = []

    def fail(f, m):
        failures.append(f"{f}: {m}")

    files = walk(site)
    file_set = set(files)
    present_foreign = {d for d in FOREIGN if os.path.isdir(os.path.join(site, d))}
    pages = [f for f in files if f.endswith(".html") and f.split("/")[0] not in FOREIGN]

    # the guide files and their pages, both ways
    guide_files = [f for f in walk(GUIDE) if f.endswith(".md")]
    expected = {}
    for g in guide_files:
        page = "guide/" + re.sub(r"\.md$", ".html", re.sub(r"(^|/)README\.md$", r"\1index.html", g))
        expected[page] = g
        if page not in file_set:
            fail(page, f"no page for docs/guide/{g}")
    for f in pages:
        if f.startswith("guide/") and f not in expected:
            fail(f, "a page under guide/ without a guide file")
    for f in ("index.html", ".nojekyll", "assets/content.css", "assets/print.css", "assets/site.css",
              "assets/NOTICE.txt"):
        if f not in file_set:
            fail(f, "missing")

    parsed = {}
    raw = {}
    for f in pages:
        with open(os.path.join(site, f), encoding="utf-8") as fh:
            s = fh.read()
        raw[f] = s
        p = Page()
        try:
            p.feed(s)
            p.close_page()
        except Exception as e:  # html.parser raises on very broken input only
            fail(f, f"not parsable: {e}")
            continue
        parsed[f] = p

    ids_cache = {}

    def ids_of(f):
        if f in parsed:
            return set(parsed[f].ids)
        if f not in ids_cache:
            with open(os.path.join(site, f), encoding="utf-8", errors="replace") as fh:
                ids_cache[f] = set(re.findall(r'\s(?:id|name)="([^"]*)"', fh.read()))
        return ids_cache[f]

    links = 0
    accepted = 0
    for f in pages:
        if f not in parsed:
            continue
        s = raw[f]
        p = parsed[f]
        for m in p.problems:
            fail(f, m)
        if not s.startswith('<!doctype html>\n<html lang="en">'):
            fail(f, "no doctype or no lang")
        if not p.policy:
            fail(f, "the policy meta element is missing or different")
        if re.search(r"<script", s, re.I):
            fail(f, "<script in the page")
        if re.search(r"<style", s, re.I):
            fail(f, "<style in the page")
        if "lt-family-bar" in s:
            fail(f, "lt-family-bar in the page")
        dup = sorted({x for x in p.ids if p.ids.count(x) > 1})
        if dup:
            fail(f, "duplicate ids: " + ", ".join(dup))
        if not p.skip_link or "main" not in p.ids:
            fail(f, "no skip link to #main")
        if f.startswith("guide/") and p.callouts > MAX_CALLOUTS:
            fail(f, f"{p.callouts} callouts (at most {MAX_CALLOUTS} on a guide page)")
        h1 = sum(1 for level, _ in p.headings if level == 1)
        if h1 != 1:
            fail(f, f"{h1} level-1 headings")
        level = 0
        for lv, line in p.headings:
            if level and lv > level + 1:
                fail(f, f"line {line}: heading level {lv} after {level}")
            level = lv
        for data, line in p.text_outside_code:
            if "http://" in data.lower():
                fail(f, f"line {line}: http:// outside code")
        for tag, name, v, line in p.urls:
            if re.match(r"^https://", v, re.I):
                if not (tag == "a" and name == "href"):
                    fail(f, f"line {line}: https:// in <{tag} {name}>: a resource from elsewhere")
                continue
            if re.match(r"^http://", v, re.I):
                fail(f, f'line {line}: http:// in <{tag} {name}="{v}">')
                continue
            if re.match(r"^[a-z][a-z0-9+.-]*:", v, re.I):
                fail(f, f'line {line}: URL scheme in <{tag} {name}="{v}">')
                continue
            if v.startswith("/") or v.startswith("\\"):
                fail(f, f'line {line}: absolute or protocol-relative path in <{tag} {name}="{v}">')
                continue
            if name == "srcset":
                fail(f, f"line {line}: srcset is not used")
                continue
            target, hashmark, frag = v.partition("#")
            resolved = f if target == "" else posixpath.normpath(posixpath.join(posixpath.dirname(f), target))
            if resolved.startswith(".."):
                fail(f, f"line {line}: link out of the site: {v}")
                continue
            top = resolved.split("/")[0]
            if top in FOREIGN and top not in present_foreign:
                accepted += 1
                continue
            links += 1
            if resolved not in file_set:
                fail(f, f"line {line}: broken link {v}")
                continue
            if hashmark and frag != "":
                if not resolved.endswith(".html"):
                    fail(f, f"line {line}: an anchor on a file that is not a page: {v}")
                elif frag not in ids_of(resolved):
                    fail(f, f"line {line}: broken anchor {v}")

    for f in files:
        if f.endswith(".css") and f.split("/")[0] not in FOREIGN:
            with open(os.path.join(site, f), encoding="utf-8") as fh:
                s = fh.read()
            if re.search(r"@import|https?:|url\(\s*[\"']?(/|data:)", s, re.I):
                fail(f, "a stylesheet that loads something from elsewhere or uses an absolute path")

    foreign_note = ", ".join(f"{d}/ {'checked as link target' if d in present_foreign else 'absent'}" for d in FOREIGN)
    print(f"site check: {len(pages)} pages, {len(files) - len(pages)} other files, {links} relative links and "
          f"anchors checked, {accepted} into absent javadoc/ or maven/ accepted ({foreign_note})")
    for f in pages:
        print(f"  {f} {os.path.getsize(os.path.join(site, f))} bytes")
    if failures:
        print(f"{len(failures)} failure(s):")
        for x in failures:
            print("- " + x)
        return 1
    print("site check passed")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
