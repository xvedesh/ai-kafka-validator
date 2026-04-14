from __future__ import annotations

import json
import os
from pathlib import Path

from openai import OpenAI


class SetupAgent:
    def __init__(self, api_key: str):
        self.client = OpenAI(api_key=api_key)
        self.system_prompt = self._load_prompt()
        self.model = os.getenv("AGENT_OPENAI_MODEL") or os.getenv("OPENAI_MODEL") or "gpt-4.1-mini"

    def _load_prompt(self) -> str:
        prompt_path = Path(__file__).resolve().parent.parent / "prompts" / "setup_prompt.txt"
        return prompt_path.read_text(encoding="utf-8")

    def _run(self, user_content: str) -> str:
        response = self.client.responses.create(
            model=self.model,
            input=[
                {"role": "system", "content": self.system_prompt},
                {"role": "user", "content": user_content},
            ],
        )
        return response.output_text

    def _build_input(self, user_message: str, repo_context: str, env_info: dict, conversation_history: str) -> str:
        return f"""
Current user request:
{user_message}

Conversation history:
{conversation_history}

Environment info:
{json.dumps(env_info, indent=2)}

Retrieved repository context:
{repo_context}
""".strip()

    def decide(self, user_message: str, repo_context: str, env_info: dict, conversation_history: str) -> str:
        return self._run(self._build_input(user_message, repo_context, env_info, conversation_history))

    def continue_after_command(
        self,
        original_user_message: str,
        repo_context: str,
        env_info: dict,
        conversation_history: str,
        command: str,
        command_result: dict,
    ) -> str:
        return self._run(
            f"""
Original user request:
{original_user_message}

Conversation history:
{conversation_history}

Environment info:
{json.dumps(env_info, indent=2)}

Retrieved repository context:
{repo_context}

Executed command:
{command}

Command result:
{json.dumps(command_result, indent=2)}

A command has already been executed.
Analyze the result and decide the next best response.

Rules:
- Return RESPOND if enough information is available
- Return RUN_COMMAND only if exactly one more safe inspection command is needed
- Do not ignore the command result
- Base conclusions only on the command result, environment info, repository context, and conversation history
""".strip()
        )

    def adapt_command_for_environment(
        self,
        proposed_command: str,
        repo_context: str,
        env_info: dict,
    ) -> dict:
        raw = self._run(
            f"""
You evaluate whether a shell command is aligned with the repository README/setup flow,
and if yes, adapt it to the current environment.

Return ONLY valid JSON with this exact schema:
{{
  "related_to_readme": true,
  "adapted_command": "string",
  "reason": "string"
}}

Rules:
- related_to_readme = true only if the command clearly belongs to setup, verification,
  readiness checks, supported test execution, or supported framework agent entrypoints
  described by the repository context
- If the command is not clearly related to the repository flow, return:
  {{
    "related_to_readme": false,
    "adapted_command": "",
    "reason": "..."
  }}
- Preserve the intent of the command
- Adapt only what is necessary for the environment
- You may adapt hostnames, localhost/IP, ports, python/python3, docker-compose/docker compose,
  mvn/mvnw usage, and similar environment-dependent details
- Do NOT introduce destructive commands
- Do NOT introduce file modification commands
- Do NOT add multiple new commands
- Return JSON only

Proposed command:
{proposed_command}

Environment info:
{json.dumps(env_info, indent=2)}

Repository context:
{repo_context}
""".strip()
        )

        try:
            parsed = json.loads(raw.strip())
            return {
                "related_to_readme": bool(parsed.get("related_to_readme", False)),
                "adapted_command": str(parsed.get("adapted_command", "")).strip(),
                "reason": str(parsed.get("reason", "")).strip(),
                "raw": raw,
            }
        except Exception:
            return {
                "related_to_readme": False,
                "adapted_command": "",
                "reason": "Failed to parse command adaptation response",
                "raw": raw,
            }
