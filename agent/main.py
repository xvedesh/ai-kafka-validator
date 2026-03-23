import os
from pathlib import Path

from dotenv import load_dotenv

from tools.env import detect_environment
from agents.setup_agent import SetupAgent
from tools.filesystem import read_file, list_files
from tools.shell import run_allowed_command


def build_repo_context() -> str:
    agent_dir = Path(__file__).resolve().parent
    repo_root = agent_dir.parent

    readme_path = repo_root / "README.md"

    readme_content = read_file(str(readme_path))
    root_files = list_files(str(repo_root))

    return f"""
README.md:
{readme_content}

Repository root structure:
{root_files}
""".strip()


def parse_mode_response(text: str) -> dict:
    lines = [line.rstrip() for line in text.splitlines() if line.strip()]

    mode = None
    command = None
    reason = None
    message = None

    i = 0
    while i < len(lines):
        line = lines[i].strip()

        if line.startswith("MODE:"):
            mode = line.replace("MODE:", "", 1).strip()

        elif line.startswith("COMMAND:"):
            value = line.replace("COMMAND:", "", 1).strip()
            if value:
                command = value
            elif i + 1 < len(lines):
                next_line = lines[i + 1].strip()
                if not next_line.startswith(
                    (
                        "MODE:",
                        "REASON:",
                        "MESSAGE:",
                        "Recommendation:",
                        "Reason:",
                        "Command:",
                        "What to Expect:",
                        "Fallback:",
                        "Next Question:",
                    )
                ):
                    command = next_line

        elif line.startswith("REASON:"):
            value = line.replace("REASON:", "", 1).strip()
            if value:
                reason = value
            elif i + 1 < len(lines):
                next_line = lines[i + 1].strip()
                if not next_line.startswith(
                    (
                        "MODE:",
                        "COMMAND:",
                        "MESSAGE:",
                        "Recommendation:",
                        "Reason:",
                        "Command:",
                        "What to Expect:",
                        "Fallback:",
                        "Next Question:",
                    )
                ):
                    reason = next_line

        elif line.startswith("MESSAGE:"):
            value = line.replace("MESSAGE:", "", 1).strip()
            if value:
                message = value
            elif i + 1 < len(lines):
                next_line = lines[i + 1].strip()
                if not next_line.startswith(
                    (
                        "MODE:",
                        "COMMAND:",
                        "REASON:",
                        "Recommendation:",
                        "Reason:",
                        "Command:",
                        "What to Expect:",
                        "Fallback:",
                        "Next Question:",
                    )
                ):
                    message = next_line

        i += 1

    return {
        "mode": mode,
        "command": command,
        "reason": reason,
        "message": message,
        "raw": text,
    }


def print_agent_response(parsed: dict) -> None:
    mode = parsed["mode"]

    if mode == "CHAT" and parsed["message"]:
        print(f"\nAssistant:\n{parsed['message']}\n")
    else:
        print(f"\nAssistant:\n{parsed['raw']}\n")


def extract_respond_command(text: str) -> str | None:
    lines = [line.rstrip() for line in text.splitlines()]

    for i, line in enumerate(lines):
        stripped = line.strip()

        if stripped.startswith("Command:"):
            value = stripped.split("Command:", 1)[1].strip()
            if value and value != "N/A":
                return value

            if i + 1 < len(lines):
                next_line = lines[i + 1].strip()
                if next_line and not next_line.startswith(
                    (
                        "What to Expect:",
                        "Fallback:",
                        "Next Question:",
                        "Reason:",
                        "Recommendation:",
                        "MODE:",
                    )
                ):
                    return next_line

    return None


def main() -> None:
    root_env = Path(__file__).resolve().parent.parent / ".env"
    load_dotenv(dotenv_path=root_env)

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        print("ERROR: OPENAI_API_KEY is not set")
        return

    repo_context = build_repo_context()
    env_info = detect_environment()
    agent = SetupAgent(api_key=api_key)
    repo_root = Path(__file__).resolve().parent.parent

    pending_command = None
    pending_reason = None
    awaiting_confirmation = False

    print("AI Kafka Validator Setup Assistant")
    print("Type 'exit' to quit.\n")

    while True:
        user_input = input("You: ").strip()

        if user_input.lower() in {"exit", "quit"}:
            print("Goodbye!")
            break

        if not user_input:
            continue

        try:
            # --- Confirmation flow handled by LLM intent classification ---
            if awaiting_confirmation and pending_command:
                confirmation_decision = agent.classify_confirmation(user_input, pending_command)

                if confirmation_decision == "CONFIRM":
                    print(f"\nExecuting approved command: {pending_command}")
                    print(f"Reason: {pending_reason}\n")

                    command_result = run_allowed_command(pending_command, cwd=str(repo_root))

                    print("Command Result:")
                    print(command_result)
                    print()

                    follow_up_text = agent.continue_after_command(
                        original_user_message=f"User approved command execution: {pending_command}",
                        repo_context=repo_context,
                        env_info=env_info,
                        command=pending_command,
                        command_result=command_result,
                    )

                    follow_up = parse_mode_response(follow_up_text)

                    if follow_up["mode"] == "RUN_COMMAND":
                        print("\nAssistant requested another command.")
                        print("Multi-step command chaining is not enabled yet.\n")
                        print_agent_response(follow_up)
                    else:
                        print_agent_response(follow_up)

                    pending_command = None
                    pending_reason = None
                    awaiting_confirmation = False
                    continue

                if confirmation_decision == "REJECT":
                    print("\nAssistant:\nOkay, I will not run that command.\n")
                    pending_command = None
                    pending_reason = None
                    awaiting_confirmation = False
                    continue

                print("\nAssistant:\nPlease confirm whether you want me to run the proposed command.\n")
                continue

            # --- Normal decision flow ---
            decision_text = agent.decide(user_input, repo_context, env_info)
            decision = parse_mode_response(decision_text)

            mode = decision["mode"]

            if mode == "RUN_COMMAND":
                command = decision["command"]
                reason = decision["reason"]

                if not command:
                    print("\nAssistant returned RUN_COMMAND without a valid command.\n")
                    print(f"Raw response:\n{decision['raw']}\n")
                    continue

                print(f"\nAssistant wants to run command: {command}")
                print(f"Reason: {reason}\n")

                command_result = run_allowed_command(command, cwd=str(repo_root))

                print("Command Result:")
                print(command_result)
                print()

                follow_up_text = agent.continue_after_command(
                    original_user_message=user_input,
                    repo_context=repo_context,
                    env_info=env_info,
                    command=command,
                    command_result=command_result,
                )

                follow_up = parse_mode_response(follow_up_text)

                if follow_up["mode"] == "RUN_COMMAND":
                    print("\nAssistant requested another command.")
                    print("Multi-step command chaining is not enabled yet.\n")
                    print_agent_response(follow_up)
                else:
                    print_agent_response(follow_up)

            elif mode == "RESPOND":
                print_agent_response(decision)

                suggested_command = extract_respond_command(decision["raw"])
                if suggested_command:
                    pending_command = suggested_command
                    pending_reason = "User-facing recommended command awaiting confirmation"
                    awaiting_confirmation = True

            elif mode == "CHAT":
                print_agent_response(decision)

            else:
                print(f"\nAssistant returned unrecognized format:\n{decision['raw']}\n")

        except Exception as e:
            print(f"\nERROR: {e}\n")


if __name__ == "__main__":
    main()