from pathlib import Path
from openai import OpenAI


class SetupAgent:
    def __init__(self, api_key: str):
        self.client = OpenAI(api_key=api_key)
        self.system_prompt = self._load_prompt()

    def _load_prompt(self) -> str:
        prompt_path = Path(__file__).resolve().parent.parent / "prompts" / "setup_prompt.txt"
        return prompt_path.read_text(encoding="utf-8")

    def _build_input(self, user_message: str, repo_context: str, env_info: dict) -> str:
        return f"""
User request:
{user_message}

Environment info:
{env_info}

Repository context:
{repo_context}
""".strip()

    def ask(self, user_message: str, repo_context: str, env_info: dict) -> str:
        response = self.client.responses.create(
            model="gpt-5",
            input=[
                {"role": "system", "content": self.system_prompt},
                {
                    "role": "user",
                    "content": self._build_input(user_message, repo_context, env_info),
                },
            ],
        )
        return response.output_text

    def decide(self, user_message: str, repo_context: str, env_info: dict) -> str:
        response = self.client.responses.create(
            model="gpt-5",
            input=[
                {"role": "system", "content": self.system_prompt},
                {
                    "role": "user",
                    "content": self._build_input(user_message, repo_context, env_info),
                },
            ],
        )
        return response.output_text

    def continue_after_command(
        self,
        original_user_message: str,
        repo_context: str,
        env_info: dict,
        command: str,
        command_result: dict,
    ) -> str:
        response = self.client.responses.create(
            model="gpt-5",
            input=[
                {"role": "system", "content": self.system_prompt},
                {
                    "role": "user",
                    "content": f"""
Original user request:
{original_user_message}

Environment info:
{env_info}

Repository context:
{repo_context}

Executed command:
{command}

Command result:
{command_result}

A command has already been executed.
Analyze the result and decide the next best response.

Rules:
- Return RESPOND if enough information is available
- Return RUN_COMMAND only if exactly one more allowed inspection command is needed
- Do not ignore the command result
- Base conclusions only on the command result, environment info, and repository context
""".strip(),
                },
            ],
        )
        return response.output_text

    def classify_confirmation(self, user_message: str, pending_command: str) -> str:
        response = self.client.responses.create(
            model="gpt-5",
            input=[
                {
                    "role": "system",
                    "content": """
You classify whether the user confirmed execution of a previously proposed command.

Return exactly one label:
- CONFIRM
- REJECT
- UNCLEAR

Rules:
- CONFIRM = user agrees to proceed or run the command
- REJECT = user declines, cancels, or says not to run it
- UNCLEAR = anything ambiguous, unrelated, or not enough to decide

Return only the label.
""".strip(),
                },
                {
                    "role": "user",
                    "content": f"""
Pending command:
{pending_command}

User response:
{user_message}
""".strip(),
                },
            ],
        )
        return response.output_text.strip()