import subprocess
from pathlib import Path


def run_command(cmd: str, cwd: str | None = None, timeout: int = 120) -> dict:
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


ALLOWED_COMMANDS = {
    "pwd",
    "ls",
    "docker --version",
    "docker compose version",
    "docker compose config",
    "docker ps",
    "java -version",
    "mvn -version",
    "node --version",
    "python --version",
    "python3 --version",
}


def run_allowed_command(cmd: str, cwd: str | None = None, timeout: int = 120) -> dict:
    normalized = cmd.strip()

    if normalized not in ALLOWED_COMMANDS:
        return {
            "command": normalized,
            "cwd": cwd,
            "returncode": -1,
            "stdout": "",
            "stderr": f"Command is not allowed: {normalized}",
            "success": False,
        }

    return run_command(normalized, cwd=cwd, timeout=timeout)