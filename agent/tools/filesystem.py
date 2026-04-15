from __future__ import annotations

import re
from pathlib import Path


IMPORTANT_PATHS = [
    "README.md",
    "config.properties",
    "pom.xml",
    "docker-compose.yml",
    "docker-compose.kafka.yml",
    "docs",
    "server/index.js",
    "server/kafkaPublisher.js",
    "src/test/resources/features",
    "src/test/java/com/step_defs",
    "src/test/java/com/kafka",
    "src/test/java/com/api",
]

STOP_WORDS = {
    "the", "and", "for", "with", "that", "this", "from", "into", "how", "what", "why",
    "does", "have", "has", "your", "you", "are", "about", "only", "please", "give",
    "tell", "me", "can", "could", "would", "should", "framework",
}


def read_file(path: str) -> str:
    p = Path(path)
    if not p.exists():
        return f"[ERROR] File not found: {path}"
    if p.is_dir():
        return f"[ERROR] Path is a directory, not a file: {path}"

    try:
        return p.read_text(encoding="utf-8")
    except Exception as e:
        return f"[ERROR] Failed to read file {path}: {e}"


def list_files(path: str = ".") -> str:
    p = Path(path)
    if not p.exists():
        return f"[ERROR] Path not found: {path}"
    if not p.is_dir():
        return f"[ERROR] Path is not a directory: {path}"

    try:
        items = []
        for item in sorted(p.iterdir(), key=lambda x: (x.is_file(), x.name.lower())):
            kind = "DIR" if item.is_dir() else "FILE"
            items.append(f"{kind}: {item.name}")
        return "\n".join(items)
    except Exception as e:
        return f"[ERROR] Failed to list files in {path}: {e}"


def build_repo_overview(repo_root: str) -> str:
    repo = Path(repo_root)
    features_dir = repo / "src" / "test" / "resources" / "features"
    feature_names = []
    if features_dir.exists():
        feature_names = sorted(path.name for path in features_dir.glob("*.feature"))

    docs_dir = repo / "docs"
    doc_names = []
    if docs_dir.exists():
        doc_names = sorted(path.name for path in docs_dir.glob("*.md"))

    return (
        "Repository overview:\n"
        f"- Root files: {list_files(str(repo))}\n"
        f"- Docs: {', '.join(doc_names) if doc_names else 'None'}\n"
        f"- Feature files: {', '.join(feature_names) if feature_names else 'None'}"
    )


def build_knowledge_base(repo_root: str) -> list[dict]:
    repo = Path(repo_root)
    files: list[Path] = []

    for relative in IMPORTANT_PATHS:
        path = repo / relative
        if not path.exists():
            continue
        if path.is_file():
            files.append(path)
        else:
            for child in path.rglob("*"):
                if child.is_file() and _is_supported_file(child):
                    files.append(child)

    chunks: list[dict] = []
    for file_path in sorted(set(files)):
        try:
            content = file_path.read_text(encoding="utf-8")
        except Exception:
            continue

        relative_path = file_path.relative_to(repo).as_posix()
        chunks.extend(_chunk_file(relative_path, content))

    return chunks


def retrieve_relevant_chunks(chunks: list[dict], query: str, limit: int = 4) -> list[dict]:
    query_tokens = _tokenize(query)
    if not query_tokens:
        return []

    scored: list[tuple[int, dict]] = []
    lowered_query = query.lower()

    for chunk in chunks:
        searchable = f"{chunk['path']} {chunk['title']} {chunk['content']}".lower()
        score = 0
        for token in query_tokens:
            if token in searchable:
                score += 3
            if token in chunk["path"].lower():
                score += 2
            if token in chunk["title"].lower():
                score += 2

        for boosted in ("kafka", "docker", "negative", "report", "failure", "transaction", "account", "portfolio", "client"):
            if boosted in lowered_query and boosted in searchable:
                score += 2

        if score > 0:
            scored.append((score, chunk))

    scored.sort(key=lambda item: (-item[0], item[1]["path"], item[1]["start_line"]))
    return [chunk for _, chunk in scored[:limit]]


def format_retrieved_context(chunks: list[dict]) -> str:
    if not chunks:
        return "No matching repository chunks were retrieved."

    sections = []
    for index, chunk in enumerate(chunks, start=1):
        content = chunk["content"].strip()
        if len(content) > 1200:
            content = content[:1200] + "..."
        sections.append(
            f"[Source {index}] {chunk['path']}:{chunk['start_line']} - {chunk['title']}\n{content}"
        )
    return "\n\n".join(sections)


def _is_supported_file(path: Path) -> bool:
    return path.suffix.lower() in {".md", ".feature", ".java", ".js", ".properties", ".xml", ".yml", ".yaml"}


def _chunk_file(relative_path: str, content: str) -> list[dict]:
    if relative_path.endswith(".md"):
        return _chunk_markdown(relative_path, content)
    if relative_path.endswith(".feature"):
        return _chunk_feature(relative_path, content)
    return _chunk_generic(relative_path, content)


def _chunk_markdown(path: str, content: str) -> list[dict]:
    lines = content.splitlines()
    if not lines:
        return []

    chunks = []
    start = 0
    title = lines[0].lstrip("# ").strip() or path
    for index, line in enumerate(lines):
        if index > start and line.startswith("## "):
            chunks.append(_build_chunk(path, title, lines, start, index - 1))
            start = index
            title = line[3:].strip() or path
    chunks.append(_build_chunk(path, title, lines, start, len(lines) - 1))
    return chunks


def _chunk_feature(path: str, content: str) -> list[dict]:
    lines = content.splitlines()
    if not lines:
        return []

    chunks = []
    start = 0
    title = path
    for index, line in enumerate(lines):
        stripped = line.strip()
        if stripped.startswith("Feature:"):
            start = index
            title = stripped
        elif index > start and (stripped.startswith("Scenario:") or stripped.startswith("Scenario Outline:")):
            chunks.append(_build_chunk(path, title, lines, start, index - 1))
            start = max(0, _find_tag_block_start(lines, index))
            title = stripped
    chunks.append(_build_chunk(path, title, lines, start, len(lines) - 1))
    return chunks


def _chunk_generic(path: str, content: str, size: int = 40, overlap: int = 8) -> list[dict]:
    lines = content.splitlines()
    if not lines:
        return []

    chunks = []
    step = max(1, size - overlap)
    for start in range(0, len(lines), step):
        end = min(len(lines) - 1, start + size - 1)
        title = _first_meaningful_line(lines, start, end) or path
        chunks.append(_build_chunk(path, title, lines, start, end))
        if end == len(lines) - 1:
            break
    return chunks


def _find_tag_block_start(lines: list[str], scenario_index: int) -> int:
    cursor = scenario_index - 1
    while cursor >= 0 and lines[cursor].strip().startswith("@"):
        cursor -= 1
    return cursor + 1


def _first_meaningful_line(lines: list[str], start: int, end: int) -> str:
    for index in range(start, end + 1):
        stripped = lines[index].strip()
        if stripped:
            return stripped[:100]
    return ""


def _build_chunk(path: str, title: str, lines: list[str], start: int, end: int) -> dict:
    return {
        "path": path,
        "title": title.strip() if title else path,
        "start_line": start + 1,
        "end_line": end + 1,
        "content": "\n".join(lines[start : end + 1]),
    }


def _tokenize(text: str) -> set[str]:
    tokens = set(re.findall(r"[a-zA-Z0-9@._-]{2,}", text.lower()))
    return {token for token in tokens if token not in STOP_WORDS}
