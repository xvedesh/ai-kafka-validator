from __future__ import annotations

import os
from pathlib import Path

from dotenv import load_dotenv

from agents.setup_agent import SetupAgent
from core.framework_knowledge import (
    FrameworkKnowledge,
    build_framework_guidance,
    classify_framework_intent,
)
from core.memory import ConversationMemory
from tools.env import detect_environment
from tools.filesystem import (
    build_knowledge_base,
    build_repo_overview,
    format_retrieved_context,
    retrieve_relevant_chunks,
)
from tools.shell import run_safe_command


def parse_mode_response(text: str) -> dict:
    lines = text.splitlines()
    mode = None
    current_key = None
    sections: dict[str, list[str]] = {
        "recommendation": [],
        "reason": [],
        "command": [],
        "what_to_expect": [],
        "fallback": [],
        "next_question": [],
        "message": [],
        "run_command": [],
        "run_reason": [],
    }

    def heading_key(line: str) -> str | None:
        stripped = line.strip()
        if stripped.startswith("MODE:"):
            return "mode"
        mapping = {
            "Recommendation:": "recommendation",
            "Reason:": "reason",
            "Command:": "command",
            "What to Expect:": "what_to_expect",
            "Fallback:": "fallback",
            "Next Question:": "next_question",
            "MESSAGE:": "message",
            "COMMAND:": "run_command",
            "REASON:": "run_reason",
        }
        for heading, key in mapping.items():
            if stripped.startswith(heading):
                return key
        return None

    for raw_line in lines:
        key = heading_key(raw_line)
        stripped = raw_line.strip()

        if key == "mode":
            mode = stripped.replace("MODE:", "", 1).strip()
            current_key = None
            continue

        if key:
            current_key = key
            value = stripped.split(":", 1)[1].strip()
            if value:
                sections[current_key].append(value)
            continue

        if current_key and raw_line.strip():
            sections[current_key].append(raw_line.rstrip())

    return {
        "mode": mode,
        "recommendation": "\n".join(sections["recommendation"]).strip(),
        "reason": "\n".join(sections["reason"]).strip(),
        "command": "\n".join(sections["command"]).strip(),
        "what_to_expect": "\n".join(sections["what_to_expect"]).strip(),
        "fallback": "\n".join(sections["fallback"]).strip(),
        "next_question": "\n".join(sections["next_question"]).strip(),
        "message": "\n".join(sections["message"]).strip(),
        "run_command": "\n".join(sections["run_command"]).strip(),
        "run_reason": "\n".join(sections["run_reason"]).strip(),
        "raw": text.strip(),
    }


def render_user_response(parsed: dict) -> str:
    mode = parsed.get("mode")
    if mode == "CHAT":
        return parsed.get("message") or parsed.get("raw") or ""

    if mode == "RUN_COMMAND":
        command = parsed.get("run_command") or "N/A"
        reason = parsed.get("run_reason") or "N/A"
        return (
            "I want to inspect one thing before answering.\n\n"
            f"Command:\n```bash\n{command}\n```\n\n"
            f"Reason:\n{reason}\n\n"
            "Tell me if you want me to run it."
        )

    if mode == "RESPOND":
        parts = []
        if parsed.get("recommendation"):
            parts.append(parsed["recommendation"])
        if parsed.get("reason") and parsed["reason"] != "N/A":
            parts.append(f"Why:\n{parsed['reason']}")
        if parsed.get("command") and parsed["command"] != "N/A":
            parts.append(f"Commands:\n```bash\n{parsed['command'].strip()}\n```")
        if parsed.get("what_to_expect") and parsed["what_to_expect"] != "N/A":
            parts.append(f"What to expect:\n{parsed['what_to_expect']}")
        if parsed.get("fallback") and parsed["fallback"] != "N/A":
            parts.append(f"Fallback:\n{parsed['fallback']}")
        if parsed.get("next_question") and parsed["next_question"] != "N/A":
            parts.append(parsed["next_question"])
        return "\n\n".join(part for part in parts if part.strip())

    return parsed.get("raw") or ""


def is_small_talk(text: str) -> bool:
    normalized = text.strip().lower()
    return normalized in {"hi", "hello", "hey", "thanks", "thank you", "how are you"}


def small_talk_response(text: str) -> str:
    normalized = text.strip().lower()
    if normalized == "how are you":
        return "Doing well, thank you - hope you are too. How can I help you with AI Kafka Validator today?"
    if normalized in {"thanks", "thank you"}:
        return "You're welcome. I can help with setup, local execution, Kafka tests, reports, or troubleshooting."
    return "Hello. How can I help you with AI Kafka Validator today?"


def is_formatting_request(text: str) -> bool:
    lowered = text.lower()
    patterns = [
        "pretty view",
        "line by line",
        "new line",
        "each command",
        "just commands",
        "command only",
        "only commands",
        "format that",
        "reformat",
        "shorter",
    ]
    return any(pattern in lowered for pattern in patterns)


def format_previous_commands(parsed: dict, request: str) -> str | None:
    if not parsed:
        return None

    command_block = parsed.get("command") or parsed.get("run_command")
    if not command_block or command_block == "N/A":
        return None

    commands = [line.rstrip() for line in command_block.splitlines()]
    request_lower = request.lower()

    if "just commands" in request_lower or "only commands" in request_lower or "command only" in request_lower:
        return "```bash\n" + "\n".join(commands).strip() + "\n```"

    return "Here are the commands, one per line:\n\n```bash\n" + "\n".join(commands).strip() + "\n```"


def pending_intent(text: str) -> str:
    lowered = text.strip().lower()
    confirm = {"yes", "y", "go ahead", "run it", "do it", "please do", "execute it", "sure", "ok", "okay"}
    reject = {"no", "n", "cancel", "stop", "don't", "do not", "not now"}

    if lowered in confirm or any(phrase in lowered for phrase in confirm):
        return "CONFIRM"
    if lowered in reject or any(phrase in lowered for phrase in reject):
        return "REJECT"
    return "NEW_REQUEST"


def should_arm_confirmation(parsed: dict) -> bool:
    command = parsed.get("command") or parsed.get("run_command")
    next_question = (parsed.get("next_question") or "").lower()
    if not command or command == "N/A":
        return False
    confirmation_cues = [
        "do you want me to run",
        "would you like me to run",
        "should i run",
        "do you want me to execute",
        "tell me if you want me to run it",
    ]
    return any(cue in next_question for cue in confirmation_cues)


def build_turn_context(
    user_input: str,
    memory: ConversationMemory,
    repo_overview: str,
    knowledge_chunks: list[dict],
    framework_knowledge: FrameworkKnowledge,
) -> str:
    retrieval_query = user_input
    recent_focus = memory.recent_user_focus()
    if recent_focus:
        retrieval_query = f"{user_input} {recent_focus}"

    chunks = retrieve_relevant_chunks(knowledge_chunks, retrieval_query, limit=4)
    return (
        framework_knowledge.summary()
        + "\n\n"
        + repo_overview
        + "\n\nRetrieved repository context:\n"
        + format_retrieved_context(chunks)
    )


def extract_command_for_execution(parsed: dict) -> str | None:
    command = (parsed.get("run_command") or parsed.get("command") or "").strip()
    if not command or command == "N/A":
        return None
    return command


def main() -> None:
    root_env = Path(__file__).resolve().parent.parent / ".env"
    load_dotenv(dotenv_path=root_env)

    api_key = os.getenv("OPENAI_API_KEY")
    if not api_key:
        print("ERROR: OPENAI_API_KEY is not set")
        return

    repo_root = Path(__file__).resolve().parent.parent
    repo_overview = build_repo_overview(str(repo_root))
    knowledge_chunks = build_knowledge_base(str(repo_root))
    framework_knowledge = FrameworkKnowledge()
    env_info = detect_environment()
    agent = SetupAgent(api_key=api_key)
    memory = ConversationMemory(max_turns=8)

    pending_command = None
    pending_user_request = None
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
            memory.add_user(user_input)

            if is_small_talk(user_input):
                reply = small_talk_response(user_input)
                print(f"\nAssistant:\n{reply}\n")
                memory.add_assistant(reply, {"mode": "CHAT", "message": reply})
                continue

            if awaiting_confirmation and pending_command:
                intent = pending_intent(user_input)
                if intent == "CONFIRM":
                    turn_context = build_turn_context(user_input, memory, repo_overview, knowledge_chunks, framework_knowledge)
                    adaptation = agent.adapt_command_for_environment(
                        proposed_command=pending_command,
                        repo_context=turn_context,
                        env_info=env_info,
                    )

                    if not adaptation["related_to_readme"]:
                        reply = (
                            "I will not run that command because it is not clearly aligned with the repository setup flow.\n\n"
                            f"Reason:\n{adaptation['reason']}"
                        )
                        print(f"\nAssistant:\n{reply}\n")
                        memory.add_assistant(reply, {"mode": "RESPOND", "recommendation": reply})
                        pending_command = None
                        pending_user_request = None
                        awaiting_confirmation = False
                        continue

                    adapted_command = adaptation["adapted_command"]
                    print(f"\nRunning command:\n{adapted_command}\n")
                    command_result = run_safe_command(
                        original_command=pending_command,
                        adapted_command=adapted_command,
                        cwd=str(repo_root),
                    )

                    print("Command Result:")
                    print(command_result)
                    print()

                    follow_up_text = agent.continue_after_command(
                        original_user_message=pending_user_request or user_input,
                        repo_context=turn_context,
                        env_info=env_info,
                        conversation_history=memory.transcript(),
                        command=adapted_command,
                        command_result=command_result,
                    )
                    follow_up = parse_mode_response(follow_up_text)
                    rendered = render_user_response(follow_up)
                    print(f"Assistant:\n{rendered}\n")
                    memory.add_assistant(rendered, follow_up)
                    pending_command = None
                    pending_user_request = None
                    awaiting_confirmation = False
                    continue

                if intent == "REJECT":
                    reply = "Okay, I will not run that command."
                    print(f"\nAssistant:\n{reply}\n")
                    memory.add_assistant(reply, {"mode": "CHAT", "message": reply})
                    pending_command = None
                    pending_user_request = None
                    awaiting_confirmation = False
                    continue

                pending_command = None
                pending_user_request = None
                awaiting_confirmation = False

            if is_formatting_request(user_input):
                previous = memory.last_assistant_structured()
                formatted = format_previous_commands(previous, user_input)
                if formatted:
                    print(f"\nAssistant:\n{formatted}\n")
                    memory.add_assistant(formatted, {"mode": "CHAT", "message": formatted})
                    continue

            framework_intent = classify_framework_intent(user_input)
            if framework_intent:
                decision = build_framework_guidance(framework_intent)
                rendered = render_user_response(decision)
                print(f"\nAssistant:\n{rendered}\n")
                memory.add_assistant(rendered, decision)

                if should_arm_confirmation(decision):
                    pending_command = extract_command_for_execution(decision)
                    pending_user_request = user_input
                    awaiting_confirmation = pending_command is not None
                else:
                    pending_command = None
                    pending_user_request = None
                    awaiting_confirmation = False
                continue

            turn_context = build_turn_context(user_input, memory, repo_overview, knowledge_chunks, framework_knowledge)
            decision_text = agent.decide(
                user_message=user_input,
                repo_context=turn_context,
                env_info=env_info,
                conversation_history=memory.transcript(),
            )
            decision = parse_mode_response(decision_text)
            rendered = render_user_response(decision)

            print(f"\nAssistant:\n{rendered}\n")
            memory.add_assistant(rendered, decision)

            if decision.get("mode") == "RUN_COMMAND":
                pending_command = extract_command_for_execution(decision)
                pending_user_request = user_input
                awaiting_confirmation = pending_command is not None
            elif decision.get("mode") == "RESPOND" and should_arm_confirmation(decision):
                pending_command = extract_command_for_execution(decision)
                pending_user_request = user_input
                awaiting_confirmation = pending_command is not None
            else:
                pending_command = None
                pending_user_request = None
                awaiting_confirmation = False

        except Exception as e:
            print(f"\nERROR: {e}\n")


if __name__ == "__main__":
    main()
