import re
import subprocess
from pathlib import Path


RESTRICTED_PATTERNS = [
    r"\brm\b",
    r"\brmdir\b",
    r"\bmv\b",
    r"\bchmod\b",
    r"\bchown\b",
    r"\bsudo\b",
    r"\bkill\b",
    r"\bpkill\b",
    r"\bdd\b",
    r"\bfind\b.*\b-delete\b",
    r"\bgit\s+reset\s+--hard\b",
    r"\bgit\s+clean\b",
    r"\bdocker\s+compose\s+down\s+-v\b",
    r"\bdocker\s+system\s+prune\b",
    r"\bdocker\s+volume\s+rm\b",
    r"\bdocker\s+container\s+prune\b",
    r"\bdocker\s+image\s+prune\b",
    r"\bsed\b.*\s-i\b",
    r"\btee\b",
    r"\bcp\b",
    r"\btouch\b",
    r"\bmkdir\b",
    r"\brename\b",
    r"\btruncate\b",
]


RESTRICTED_TOKENS = {
    ">",
    ">>",
}


def normalize_command(cmd: str) -> str:
    return re.sub(r"\s+", " ", cmd.strip())


def is_restricted_command(cmd: str) -> tuple[bool, str | None]:
    normalized = normalize_command(cmd)

    for token in RESTRICTED_TOKENS:
        if token in normalized:
            return True, f"Restricted redirection token detected: {token}"

    for pattern in RESTRICTED_PATTERNS:
        if re.search(pattern, normalized, flags=re.IGNORECASE):
            return True, f"Restricted command pattern detected: {pattern}"

    return False, None


def run_command(cmd: str, cwd: str | None = None, timeout: int = 1200) -> dict:
    try:
        working_dir = Path(cwd).resolve() if cwd else None

        result = subprocess.run(
            cmd,
            shell=True,
            cwd=str(working_dir) if working_dir else None,
            capture_output=True,
            text=True,
            timeout=timeout,
        )

        return {
            "command": cmd,
            "cwd": str(working_dir) if working_dir else None,
            "returncode": result.returncode,
            "stdout": result.stdout.strip(),
            "stderr": result.stderr.strip(),
            "success": result.returncode == 0,
        }

    except subprocess.TimeoutExpired:
        return {
            "command": cmd,
            "cwd": cwd,
            "returncode": -1,
            "stdout": "",
            "stderr": f"Command timed out after {timeout} seconds",
            "success": False,
        }

    except Exception as e:
        return {
            "command": cmd,
            "cwd": cwd,
            "returncode": -1,
            "stdout": "",
            "stderr": str(e),
            "success": False,
        }


def run_safe_command(
    original_command: str,
    adapted_command: str,
    cwd: str | None = None,
    timeout: int = 1200,
) -> dict:
    original_restricted, original_reason = is_restricted_command(original_command)
    if original_restricted:
        return {
            "command": original_command,
            "adapted_command": adapted_command,
            "cwd": cwd,
            "returncode": -1,
            "stdout": "",
            "stderr": f"Original command is restricted: {original_reason}",
            "success": False,
        }

    adapted_restricted, adapted_reason = is_restricted_command(adapted_command)
    if adapted_restricted:
        return {
            "command": original_command,
            "adapted_command": adapted_command,
            "cwd": cwd,
            "returncode": -1,
            "stdout": "",
            "stderr": f"Adapted command is restricted: {adapted_reason}",
            "success": False,
        }

    return run_command(adapted_command, cwd=cwd, timeout=timeout)