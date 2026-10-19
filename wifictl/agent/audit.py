"""Аудит-лог: каждая выполненная/отклонённая команда пишется в JSON-lines."""

from __future__ import annotations

import datetime as _dt
import json
from pathlib import Path


class AuditLog:
    """Простой append-only аудит-лог в формате JSON-lines."""

    def __init__(self, path: str | Path):
        self._path = Path(path)
        self._path.parent.mkdir(parents=True, exist_ok=True)

    def record(
        self,
        *,
        peer: str,
        kind: str,
        value: str,
        command: str = "",
        exit_code: int | None = None,
        status: str = "executed",
        error: str = "",
    ) -> None:
        """Записать одно событие.

        :param peer: адрес клиента (ip:port).
        :param kind: "macro" | "command".
        :param value: имя макроса или исходная строка запроса.
        :param command: конкретная выполненная команда.
        :param status: "executed" | "denied" | "error".
        """
        entry = {
            "ts": _dt.datetime.now(_dt.timezone.utc).isoformat(),
            "peer": peer,
            "kind": kind,
            "value": value,
            "command": command,
            "exit_code": exit_code,
            "status": status,
            "error": error,
        }
        with self._path.open("a", encoding="utf-8") as fh:
            fh.write(json.dumps(entry, ensure_ascii=False) + "\n")
