import platform
import shutil
import subprocess


def detect_os() -> dict:
    system = platform.system()
    release = platform.release()
    version = platform.version()

    return {
        "os": system,
        "release": release,
        "version": version,
        "is_windows": system == "Windows",
        "is_macos": system == "Darwin",
        "is_linux": system == "Linux",
    }


def check_prerequisite(name: str) -> dict:
    path = shutil.which(name)

    return {
        "name": name,
        "installed": path is not None,
        "path": path,
    }


def get_command_version(command: str, version_arg: str = "--version") -> dict:
    try:
        result = subprocess.run(
            [command, version_arg],
            capture_output=True,
            text=True,
            timeout=15,
        )

        output = (result.stdout or result.stderr).strip()

        return {
            "command": command,
            "success": result.returncode == 0,
            "output": output,
            "returncode": result.returncode,
        }
    except Exception as e:
        return {
            "command": command,
            "success": False,
            "output": str(e),
            "returncode": -1,
        }
    

def detect_environment() -> dict:
    os_info = detect_os()

    checks = {}
    versions = {}

    prerequisites = ["docker", "java", "mvn", "node", "python3", "python"]

    for name in prerequisites:
        check = check_prerequisite(name)
        checks[name] = check

        if check["installed"]:
            version_arg = "--version"
            if name == "java":
                version_arg = "-version"

            versions[name] = get_command_version(name, version_arg=version_arg)
        else:
            versions[name] = {
                "command": name,
                "success": False,
                "output": "Not installed",
                "returncode": -1,
            }

    recommended_setup = None

    if checks["docker"]["installed"]:
        recommended_setup = "docker"
    elif checks["java"]["installed"] and checks["mvn"]["installed"] and checks["node"]["installed"]:
        recommended_setup = "local"
    else:
        recommended_setup = "incomplete"

    preferred_python = None
    if checks["python"]["installed"]:
        preferred_python = "python"
    elif checks["python3"]["installed"]:
        preferred_python = "python3"

    return {
        "os": os_info,
        "checks": checks,
        "versions": versions,
        "preferred_python": preferred_python,
        "recommended_setup": recommended_setup,
    }
