from pathlib import Path


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