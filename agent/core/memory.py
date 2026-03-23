from __future__ import annotations

from dataclasses import dataclass
from typing import Any


@dataclass
class Turn:
    role: str
    message: str
    structured: dict[str, Any] | None = None


class ConversationMemory:
    def __init__(self, max_turns: int = 8):
        self.max_turns = max_turns
        self.turns: list[Turn] = []

    def add_user(self, message: str) -> None:
        self.turns.append(Turn(role="user", message=message))
        self._trim()

    def add_assistant(self, message: str, structured: dict[str, Any] | None = None) -> None:
        self.turns.append(Turn(role="assistant", message=message, structured=structured))
        self._trim()

    def transcript(self, limit: int = 6) -> str:
        recent = self.turns[-limit:]
        if not recent:
            return "No prior conversation."
        lines = []
        for turn in recent:
            speaker = "User" if turn.role == "user" else "Assistant"
            lines.append(f"{speaker}: {turn.message}")
        return "\n".join(lines)

    def recent_user_focus(self, limit: int = 3) -> str:
        recent_users = [turn.message for turn in self.turns if turn.role == "user"][-limit:]
        return " | ".join(recent_users)

    def last_assistant_structured(self) -> dict[str, Any] | None:
        for turn in reversed(self.turns):
            if turn.role == "assistant" and turn.structured:
                return turn.structured
        return None

    def _trim(self) -> None:
        overflow = len(self.turns) - (self.max_turns * 2)
        if overflow > 0:
            self.turns = self.turns[overflow:]
