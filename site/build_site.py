"""Builds the project's site from docs/guide/ into an output directory: the landing page
(index.html), one page per guide file (guide/index.html from docs/guide/README.md, then
guide/<NN-name>.html for every docs/guide/NN-*.md in sorted order), the house stylesheet and the
site's assets (assets/), and .nojekyll. No JavaScript, no inline style, no external resource, and
relative links only, so the site works under any sub-path (GitHub Pages serves it under
/myzephyr-java/). A Markdown file in docs/guide/ without a place in that order fails the build.

Links from the guide to other guide files become links to their pages (the anchor must exist);
links to other files of the repository become links to them on GitHub (the file must exist, and
an anchor into a Markdown file must name one of its headings); a link to the Javadoc on the
project's Pages (https://loki-notfound.github.io/myzephyr-java/javadoc/...) becomes a relative
link into javadoc/. The converter (site/markdown.py) fails on any Markdown it does not know.

A code block takes its language and nothing else, with one exception: a Java block whose info
string is "java examples/<Class>.java" quotes that compiled example (the file must exist in
src/test/java/examples/; test/TestExamples.java checks that the block is verbatim in it) and is
labelled "Java, compiled". A callout (GitHub's alert syntax) becomes the house style's lt-callout
box, info for NOTE, warning for WARNING, danger for CAUTION, with its bold title as
lt-callout-title; a page has at most two.

The footer names the version of pom.xml and the commit the site was built from, with the
commit's date in UTC, and "with local changes" when docs/guide/ or site/ differ from it. Without
git, or in a tree that is not the top of its own repository (an unpacked git archive), it says
"built from a source tree without a git commit" instead. Nothing else varies: the same commit
gives the same bytes on any machine and at any time.

Usage: python3 -I site/build_site.py --out <dir> [--maven <dir>] [--index-only]
  --maven <dir>   a site's Maven repository: the index lists every version found there other
                  than this one as an older release (the release build passes the merged
                  repository, bin/merge_previous_release.sh; the guide-only build that of the
                  published site)
  --index-only    write only <dir>/index.html (into an existing site)
Without --index-only the outputs of an earlier build in <dir> (index.html, .nojekyll, guide/,
assets/) are replaced; anything else in <dir> (javadoc/, maven/) is left alone.
"""

import importlib.util
import os
import posixpath
import re
import shutil
import subprocess
import sys
import xml.etree.ElementTree as ET

sys.dont_write_bytecode = True  # no __pycache__ in site/: it would count as a local change

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
GUIDE = os.path.join(ROOT, "docs", "guide")


def _load_markdown():
    spec = importlib.util.spec_from_file_location("site_markdown", os.path.join(HERE, "markdown.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


md = _load_markdown()
esc = md.escape_html

REPO_URL = "https://github.com/loki-notfound/myzephyr-java"
PAGES_URL = "https://loki-notfound.github.io/myzephyr-java"
POLICY = "default-src 'none'; style-src 'self'; img-src 'self'; base-uri 'none'; form-action 'none'"
ASSETS = ["content.css", "print.css", "site.css", "favicon.svg", "favicon-32.png", "apple-touch-icon-180.png",
          "STYLE-VERSION", "NOTICE.txt", "LICENSE-style.txt", "TRADEMARKS.txt"]
CODE_LABELS = {"java": "Java", "kotlin": "Kotlin", "groovy": "Groovy", "sh": "Shell", "toml": "TOML", "xml": "XML",
               "text": "Text"}
# the one info string allowed beyond a language: a Java block that quotes a compiled example
NAMED_EXAMPLE = re.compile(r"^java examples/([A-Z][A-Za-z0-9]*)\.java$")
EXAMPLES = os.path.join(ROOT, "src", "test", "java", "examples")
MAX_CALLOUTS = 2
GROUP_PATH = "io/github/loki-notfound"
GROUP_ID = GROUP_PATH.replace("/", ".")
SCHEME = re.compile(r"^(\d+)\.(\d+)\.(\d+)-sec\.(\d+)-java\.(\d+)$")
FINGERPRINT = "31DA 39AB 43E0 CA67 F1F3  A661 BA2A FF22 DF8A 4A13"
DONATION_ADDRESS = ("ZEPHs8MmrN556ieVvv9YUZf3J5pW2uN1z5S88mb9DksG9bcEcqVGg9XbuQc6Q9e9Ka6bjMHYgaSidWfL1r1DGLyngj8"
                    "NHT3Q6gp")


class BuildError(Exception):
    pass


# ------------------------------------------------------------------ inputs

def pom_version():
    ns = {"m": "http://maven.apache.org/POM/4.0.0"}
    return ET.parse(os.path.join(ROOT, "pom.xml")).getroot().find("m:version", ns).text.strip()


def aar_artifact_id():
    with open(os.path.join(ROOT, "android", "gradle.properties"), encoding="utf-8") as f:
        for line in f:
            if line.startswith("ARTIFACT_ID="):
                return line[len("ARTIFACT_ID="):].strip()
    raise BuildError("android/gradle.properties has no ARTIFACT_ID")


def git(*args):
    env = dict(os.environ, TZ="UTC", LC_ALL="C")
    return subprocess.run(["git", *args], cwd=ROOT, env=env, check=True, capture_output=True,
                          text=True).stdout.strip()


def source_state():
    """(commit, date, dirty) of the checkout, or (None, None, False) without git or a repository."""
    try:
        # a tree without its own repository (an unpacked git archive) inside another one: no commit
        if os.path.realpath(git("rev-parse", "--show-toplevel")) != os.path.realpath(ROOT):
            return None, None, False
        commit = git("rev-parse", "--short=8", "HEAD")
        date = git("log", "-1", "--format=%cd", "--date=format-local:%Y-%m-%d", "HEAD")
        dirty = git("status", "--porcelain", "--untracked-files=all", "--", "docs/guide", "site") != ""
    except (OSError, subprocess.CalledProcessError):
        return None, None, False
    return commit, date, dirty


def version_order(v):
    m = SCHEME.match(v)
    if not m:
        raise BuildError(f"version {v} does not follow the release scheme <x>.<y>.<z>-sec.<N>-java.<M>")
    return tuple(int(x) for x in m.groups())


def older_releases(maven, version):
    """{version: [artifactId, ...]} of every version in a site's Maven repository but this one."""
    base = os.path.join(maven, GROUP_PATH)
    if not os.path.isdir(base):
        raise BuildError(f"{maven} has no {GROUP_PATH}/")
    ids = sorted(a for a in os.listdir(base) if os.path.isfile(os.path.join(base, a, "maven-metadata.xml")))
    if not ids:
        raise BuildError(f"{maven} has no artifact under {GROUP_PATH}/")
    older = {}
    for aid in ids:
        adir = os.path.join(base, aid)
        for v in sorted(d for d in os.listdir(adir) if os.path.isdir(os.path.join(adir, d))):
            version_order(v)
            if v != version:
                older.setdefault(v, []).append(aid)
    return older


def guide_pages():
    """The guide in page order, parsed: README.md, then every NN-*.md in sorted order."""
    found = []
    for dirpath, dirnames, filenames in os.walk(GUIDE):
        dirnames.sort()
        for name in sorted(filenames):
            found.append(posixpath.relpath(os.path.join(dirpath, name), GUIDE).replace(os.sep, "/"))
    order = ["README.md"] + sorted(f for f in found if re.match(r"^\d\d-[^/]*\.md$", f))
    stray = [f for f in found if f.endswith(".md") and f not in order]
    if stray:
        raise BuildError("guide files without a place in the page order: " + ", ".join(stray))
    if "README.md" not in found:
        raise BuildError("docs/guide/README.md is missing")
    pages = []
    for rel in order:
        where = "docs/guide/" + rel
        with open(os.path.join(GUIDE, rel), encoding="utf-8") as f:
            blocks = md.parse(f.read(), where)
        h1 = [b for b in blocks if b["type"] == "heading" and b["level"] == 1]
        if len(h1) != 1 or not blocks or blocks[0] is not h1[0]:
            raise BuildError(f"{where}: a page starts with its one level-1 heading")
        level = 1
        for b in blocks:
            if b["type"] == "heading":
                if b["level"] > level + 1:
                    raise BuildError(f"{where}:{b['line']}: heading level {b['level']} after {level}")
                level = b["level"]
        callouts = [b for b in blocks if b["type"] == "callout"]
        if len(callouts) > MAX_CALLOUTS:
            raise BuildError(f"{where}:{callouts[MAX_CALLOUTS]['line']}: more than {MAX_CALLOUTS} callouts on one page")
        out = "guide/index.html" if rel == "README.md" else "guide/" + rel[:-3] + ".html"
        pages.append({"rel": rel, "out": out, "blocks": blocks, "title": h1[0]["text"],
                      "ids": {b["id"] for b in blocks if b["type"] == "heading"}})
    return pages


_repo_anchor_cache = {}


def repo_markdown_anchors(rel):
    """The heading anchors GitHub gives a Markdown file of the repository (headings outside fences)."""
    if rel not in _repo_anchor_cache:
        ids, counts, fenced = set(), {}, False
        with open(os.path.join(ROOT, rel), encoding="utf-8") as f:
            for line in f.read().split("\n"):
                if line.startswith("```"):
                    fenced = not fenced
                    continue
                m = None if fenced else re.match(r"^(#{1,6}) +(.+?) *#* *$", line)
                if m:
                    text = md.inline_text(md.parse_inline(m.group(2), rel, 0))
                    base = md.slug(text)
                    n = counts.get(base, 0)
                    counts[base] = n + 1
                    ids.add(base if n == 0 else f"{base}-{n}")
        _repo_anchor_cache[rel] = ids
    return _repo_anchor_cache[rel]


# ------------------------------------------------------------------ rendering

def rel_link(from_out, to_out):
    r = posixpath.relpath(to_out, posixpath.dirname(from_out) or ".")
    return posixpath.basename(to_out) if r == "." else r


def heading_html(level, ident, inner):
    if level == 1:
        return f'<h1 id="{ident}">{inner}</h1>'
    return (f'<h{level} id="{ident}">{inner}<a class="heading-anchor" href="#{ident}" '
            f'aria-label="Link to this section">#</a></h{level}>')


def code_block_html(label, text):
    return (f'<div class="lt-code-block">\n<div class="lt-code-head"><span>{esc(label)}</span></div>\n'
            f'<pre><code>{esc(text)}</code></pre>\n</div>')


def guide_body(page, by_rel):
    where = "docs/guide/" + page["rel"]

    def resolve_link(href):
        if href.startswith(PAGES_URL + "/javadoc/"):
            return rel_link(page["out"], href[len(PAGES_URL) + 1:])
        if href.startswith("https://"):
            return href
        if re.match(r"^[a-z][a-z0-9+.-]*:", href, re.I) or href.startswith("/"):
            raise BuildError(f'{where}: link "{href}" must be https:// or relative')
        target, hashmark, frag = href.partition("#")
        if target == "":
            if frag not in page["ids"]:
                raise BuildError(f"{where}: no heading #{frag} on this page")
            return "#" + frag
        guide_rel = posixpath.normpath(posixpath.join(posixpath.dirname(page["rel"]), target))
        if guide_rel in by_rel:
            to = by_rel[guide_rel]
            if hashmark and frag not in to["ids"]:
                raise BuildError(f"{where}: no heading #{frag} in docs/guide/{guide_rel}")
            return rel_link(page["out"], to["out"]) + (hashmark + frag)
        repo_rel = posixpath.normpath(posixpath.join("docs/guide", posixpath.dirname(page["rel"]), target))
        if repo_rel.startswith("..") or not os.path.exists(os.path.join(ROOT, repo_rel)):
            raise BuildError(f"{where}: link target {repo_rel} does not exist")
        if repo_rel == "docs/guide" or repo_rel.startswith("docs/guide/"):
            raise BuildError(f"{where}: {repo_rel} is in the guide but has no page")
        if hashmark and repo_rel.endswith(".md") and frag not in repo_markdown_anchors(repo_rel):
            raise BuildError(f"{where}: no heading #{frag} in {repo_rel}")
        kind = "tree" if os.path.isdir(os.path.join(ROOT, repo_rel)) else "blob"
        return f"{REPO_URL}/{kind}/main/{repo_rel}" + (hashmark + frag)

    last_heading = [""]

    def block_html(b):
        t = b["type"]
        if t == "heading":
            last_heading[0] = b["id"]
            return heading_html(b["level"], b["id"], md.inline_html(b["inline"], resolve_link))
        if t == "paragraph":
            return f"<p>{md.inline_html(b['inline'], resolve_link)}</p>"
        if t == "list":
            tag = "ol" if b["ordered"] else "ul"
            items = []
            for it in b["items"]:
                inner = "".join(md.inline_html(x["inline"], resolve_link) if x["type"] == "paragraph" else block_html(x)
                                for x in it["blocks"])
                items.append(f"<li>{inner}</li>")
            return f"<{tag}>{''.join(items)}</{tag}>"
        if t == "table":
            head = "".join(f'<th scope="col">{md.inline_html(c, resolve_link)}</th>' for c in b["head"])
            rows = "\n".join("<tr>" + "".join(f"<td>{md.inline_html(c, resolve_link)}</td>" for c in r) + "</tr>"
                             for r in b["rows"])
            return (f'<div class="lt-table-scroll" tabindex="0" role="region" aria-labelledby="{last_heading[0]}">\n'
                    f"<table>\n<thead><tr>{head}</tr></thead>\n<tbody>\n{rows}\n</tbody>\n</table>\n</div>")
        if t == "code":
            label = CODE_LABELS.get(b["lang"])
            if label is None:
                raise BuildError(f"{where}:{b['line']}: unknown code language {b['lang']}")
            if b["info"] != b["lang"]:
                named = NAMED_EXAMPLE.match(b["info"])
                if not named:
                    raise BuildError(f"{where}:{b['line']}: a code block takes only its language, or "
                                     f"\"java examples/<Class>.java\", not \"{b['info']}\"")
                if not os.path.isfile(os.path.join(EXAMPLES, named.group(1) + ".java")):
                    raise BuildError(f"{where}:{b['line']}: no src/test/java/examples/{named.group(1)}.java")
                label = "Java, compiled"
            return code_block_html(label, b["text"])
        if t == "callout":
            inner = "".join(block_html(x) + "\n" for x in b["blocks"])
            return (f'<div class="lt-callout lt-callout-{b["kind"]}" role="note">\n'
                    f'<p class="lt-callout-title">{md.inline_html(b["title"], resolve_link)}</p>\n{inner}</div>')
        raise BuildError("unknown block")

    return "\n".join(block_html(b) for b in page["blocks"])


def index_body(version, aar_id, older):
    def h2(ident, text):
        return heading_html(2, ident, esc(text))

    gradle = f"""repositories {{
    maven {{ url = uri("{PAGES_URL}/maven") }}
}}

android {{
    packaging {{ resources.excludes += "/META-INF/DEPENDENCIES" }}
}}

dependencies {{
    implementation("{GROUP_ID}:{aar_id}:{version}")
}}"""
    maven = f"""<repositories>
  <repository>
    <id>zephyr-java</id>
    <url>{PAGES_URL}/maven</url>
  </repository>
</repositories>

<dependency>
  <groupId>{GROUP_ID}</groupId>
  <artifactId>zephyr-java</artifactId>
  <version>{version}</version>
</dependency>"""
    parts = [
        heading_html(1, "zephyr-java", "zephyr-java"),
        "<p class=\"lead\">Java bindings for the Zephyr Protocol wallet core, for Android (AAR) and for desktop "
        "and server JVMs (JAR), built from the security-hardened forks of zephyr-backports. "
        f"Version {esc(version)}.</p>",
        '<div class="lt-callout lt-callout-info" role="note">\n'
        '<p class="lt-callout-title">Not affiliated with ZephyrProtocol.</p>\n'
        "<p>An independent project. Report Zephyr vulnerabilities to the ZephyrProtocol team, not here.</p>\n"
        "</div>",
        h2("android-gradle-kotlin-dsl", "Android (Gradle, Kotlin DSL)"),
        code_block_html("Kotlin", gradle),
        h2("desktop-and-server-maven", "Desktop and server (Maven)"),
        code_block_html("XML", maven),
    ]
    if older:
        items = []
        for v in sorted(older, key=version_order, reverse=True):
            # the Android artifact first, as in the sections above
            coords = ", ".join(f"<code>{GROUP_ID}:{aid}:{v}</code>"
                               for aid in sorted(older[v], key=lambda a: (not a.endswith("-android"), a)))
            items.append(f'<li>{v} (<a href="{REPO_URL}/releases/tag/v{v}">release</a>): {coords}</li>')
        parts += [
            h2("older-releases", "Older releases"),
            "<p>The Maven repository keeps every release, so a build can stay on an older version with the same "
            'repository line. The <a href="javadoc/index.html">Javadoc</a> on this site is that of version '
            f"{esc(version)} only; an older version's Javadoc is its "
            "<code>zephyr-java-&lt;version&gt;-javadoc.jar</code>, in the repository and among the files of its "
            "release.</p>",
            "<ul>\n" + "\n".join(items) + "\n</ul>",
        ]
    parts += [
        h2("documentation", "Documentation"),
        "<ul>\n"
        '<li><a href="guide/index.html">User guide</a>, also as Markdown in the repository '
        f'(<a href="{REPO_URL}/blob/main/docs/guide/README.md">docs/guide</a> on GitHub)</li>\n'
        f'<li><a href="javadoc/index.html">Javadoc</a> of version {esc(version)}</li>\n'
        f'<li><a href="{REPO_URL}/releases">Releases</a> with the build manifest and the signed SHA256SUMS</li>\n'
        f'<li><a href="{REPO_URL}">Source</a></li>\n'
        "</ul>",
        h2("verifying", "Verifying"),
        "<p>Release tags and SHA256SUMS are signed with the maintainer key</p>",
        f'<p class="lt-fingerprint"><code>{FINGERPRINT}</code></p>',
        f'<p>whose public key is <a href="{REPO_URL}/blob/main/KEYS.asc">KEYS.asc</a> in the repository.</p>',
        h2("donations", "Donations"),
        "<p>If zephyr-java is useful to you, donations are welcome at this Zephyr address:</p>",
        f'<pre class="addr"><code>{DONATION_ADDRESS}</code></pre>',
    ]
    return "\n".join(parts)


def render(out, title, description, body, toc_ids, pages, current, meta, crumbs="", pager=""):
    """One page of the site: the shell around an article. current is the guide page shown, or None."""
    root = "../" if "/" in out else ""
    in_guide = current is not None
    current_attr = ' aria-current="page"'
    guide_nav = "\n".join(f'<li><a href="{rel_link(out, p["out"])}"{current_attr if p is current else ""}>'
                          f'{esc(p["title"])}</a></li>' for p in pages)
    toc = "\n".join(f'<li><a href="#{ident}">{esc(text)}</a></li>' for ident, text in toc_ids)
    toc_html = f'<h2 id="site-nav-page">On this page</h2>\n<nav aria-labelledby="site-nav-page">\n<ol>\n{toc}\n</ol>\n</nav>\n' if toc else ""
    if meta["commit"]:
        built = (f"Guide for zephyr-java {esc(meta['version'])}, built from commit <code>{esc(meta['commit'])}</code>"
                 f"{' with local changes' if meta['dirty'] else ''} ({esc(meta['date'])} UTC).")
    else:
        built = f"Guide for zephyr-java {esc(meta['version'])}, built from a source tree without a git commit."
    return f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta http-equiv="Content-Security-Policy" content="{esc(POLICY)}">
<meta name="referrer" content="no-referrer">
<title>{esc(title)}</title>
<meta name="description" content="{esc(description)}">
<link rel="icon" href="{root}assets/favicon.svg" type="image/svg+xml">
<link rel="icon" href="{root}assets/favicon-32.png" sizes="32x32" type="image/png">
<link rel="apple-touch-icon" href="{root}assets/apple-touch-icon-180.png">
<link rel="stylesheet" href="{root}assets/content.css">
<link rel="stylesheet" href="{root}assets/print.css">
<link rel="stylesheet" href="{root}assets/site.css">
</head>
<body>
<a class="lt-skip-link" href="#main">Skip to main content</a>
<header class="lt-header">
  <div class="lt-wrap lt-wrap-wide lt-header-inner">
    <div class="site-title">
      <a class="lt-brand" href="{rel_link(out, "index.html")}">zephyr-java</a>
      <p class="site-tagline">Java wallet library for Zephyr, a lateo project</p>
    </div>
    <nav class="lt-nav" aria-label="Site navigation">
      <a href="{rel_link(out, "guide/index.html")}"{current_attr if in_guide else ""}>Guide</a>
      <a href="{rel_link(out, "javadoc/index.html")}">Javadoc</a>
      <a href="{REPO_URL}/releases">Releases</a>
      <a href="{REPO_URL}">Repository</a>
    </nav>
  </div>
</header>
<main id="main" tabindex="-1">
<div class="lt-wrap lt-wrap-wide lt-docs-layout">
<aside class="lt-toc" aria-label="Guide navigation">
<h2 id="site-nav-guide">Guide</h2>
<nav aria-labelledby="site-nav-guide">
<ol>
{guide_nav}
</ol>
</nav>
{toc_html}</aside>
<article class="lt-docs-main lt-stack">
{crumbs}{body}
{pager}</article>
</div>
</main>
<footer class="lt-footer">
<div class="lt-wrap lt-wrap-wide">
<p>{built}</p>
<p>zephyr-java and this site are a <a href="https://lateo.dev/">lateo</a> project by an independent operator, not affiliated with or endorsed by ZephyrProtocol. Source: <a href="{REPO_URL}">github.com/loki-notfound/myzephyr-java</a>.</p>
<p>The library and the guide are under the MIT licence of the repository; the stylesheet is BSD 3-Clause; the lateo name, mark and favicons are not covered by either and all rights in them are reserved by their owner (<a href="{root}assets/NOTICE.txt">notice</a>).</p>
</div>
</footer>
</body>
</html>
"""


def render_guide_page(n, pages, by_rel, meta):
    page = pages[n]
    out = page["out"]
    body = guide_body(page, by_rel)
    toc_ids = [(b["id"], b["text"]) for b in page["blocks"] if b["type"] == "heading" and b["level"] == 2]
    prev = pages[n - 1] if n > 0 else None
    nxt = pages[n + 1] if n < len(pages) - 1 else None
    links = []
    if prev:
        links.append(f'<a class="site-prev" rel="prev" href="{rel_link(out, prev["out"])}">Previous: {esc(prev["title"])}</a>')
    if nxt:
        links.append(f'<a class="site-next" rel="next" href="{rel_link(out, nxt["out"])}">Next: {esc(nxt["title"])}</a>')
    pager = '<nav class="site-pager" aria-label="Previous and next page">\n' + "\n".join(links) + "\n</nav>\n"
    trail = [f'<li><a href="{rel_link(out, "index.html")}">zephyr-java</a></li>']
    if n == 0:
        trail.append(f'<li><a href="{rel_link(out, out)}" aria-current="page">Guide</a></li>')
    else:
        trail.append(f'<li><a href="{rel_link(out, "guide/index.html")}">Guide</a></li>')
        trail.append(f'<li><a href="{rel_link(out, out)}" aria-current="page">{esc(page["title"])}</a></li>')
    crumbs = f'<nav class="lt-breadcrumbs" aria-label="Breadcrumb"><ol>{"".join(trail)}</ol></nav>\n'
    title = "zephyr-java guide" if n == 0 else f"{page['title']} - zephyr-java guide"
    first = next((b for b in page["blocks"] if b["type"] == "paragraph"), None)
    description = md.inline_text(first["inline"])[:200] if first else page["title"]
    return render(out, title, description, body, toc_ids, pages, page, meta, crumbs, pager)


def render_index(pages, meta, aar_id, older):
    body = index_body(meta["version"], aar_id, older)
    toc_ids = re.findall(r'<h2 id="([^"]*)">([^<]*)<a class="heading-anchor"', body)
    toc_ids = [(i, t.replace("&amp;", "&")) for i, t in toc_ids]
    description = ("Java bindings for the Zephyr Protocol wallet core, for Android (AAR) and for desktop and server "
                   "JVMs (JAR): the Maven coordinates, the user guide, the Javadoc and the releases.")
    return render("index.html", "zephyr-java", description, body, toc_ids, pages, None, meta)


def write(path, text):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        f.write(text)


def main(argv):
    out = None
    maven = None
    index_only = False
    args = list(argv)
    while args:
        a = args.pop(0)
        if a in ("--out", "--maven") and args:
            if a == "--out":
                out = args.pop(0)
            else:
                maven = args.pop(0)
        elif a == "--index-only":
            index_only = True
        else:
            raise BuildError(f"unknown argument {a}; usage: build_site.py --out <dir> [--maven <dir>] [--index-only]")
    if not out:
        raise BuildError("no --out <dir>")
    out_arg = out
    out = os.path.abspath(out)

    version = pom_version()
    version_order(version)
    commit, date, dirty = source_state()
    meta = {"version": version, "commit": commit, "date": date, "dirty": dirty}
    aar_id = aar_artifact_id()
    older = {}
    if maven:
        if not os.path.isdir(maven):
            raise BuildError(f"--maven {maven} is not a directory")
        older = older_releases(maven, version)

    pages = guide_pages()
    by_rel = {p["rel"]: p for p in pages}
    present = sorted(os.listdir(os.path.join(HERE, "assets")))
    if present != sorted(ASSETS):
        raise BuildError(f"site/assets/ holds {present}, expected {sorted(ASSETS)}")

    # render everything before writing anything
    files = {"index.html": render_index(pages, meta, aar_id, older)}
    if not index_only:
        for n in range(len(pages)):
            files[pages[n]["out"]] = render_guide_page(n, pages, by_rel, meta)

    if index_only:
        if not os.path.isdir(out):
            raise BuildError(f"--index-only: {out} is not a directory")
    else:
        os.makedirs(out, exist_ok=True)
        for name in ("guide", "assets"):
            shutil.rmtree(os.path.join(out, name), ignore_errors=True)
        for name in ("index.html", ".nojekyll"):
            if os.path.lexists(os.path.join(out, name)):
                os.remove(os.path.join(out, name))
        os.makedirs(os.path.join(out, "assets"))
        for name in ASSETS:
            shutil.copyfile(os.path.join(HERE, "assets", name), os.path.join(out, "assets", name))
        write(os.path.join(out, ".nojekyll"), "")
    for name in sorted(files):
        write(os.path.join(out, name), files[name])

    source = (f"commit {commit}{' with local changes' if dirty else ''}, {date}" if commit
              else "no git commit")
    what = "index.html" if index_only else f"{len(files)} pages and {len(ASSETS)} assets"
    print(f"site: {what} in {out_arg} (version {version}, {source}, {len(older)} older release(s) listed)")


if __name__ == "__main__":
    try:
        main(sys.argv[1:])
    except (BuildError, md.MarkdownError) as e:
        print(f"site build failed: {e}", file=sys.stderr)
        sys.exit(1)
