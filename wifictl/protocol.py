"""Сообщения протокола wifictl (JSON поверх WebSocket).

Поток общения:
  1. Клиент подключается по ``wss://`` и шлёт :class:`AuthFrame`.
  2. Агент отвечает :class:`AuthResult`.
  3. Клиент шлёт :class:`Request` (макрос или произвольная команда).
  4. Агент стримит :class:`OutputFrame` (stdout/stderr) и завершает
     :class:`ResultFrame` с кодом возврата.

Все кадры сериализуются в одну JSON-строку. Поле ``type`` различает их.
"""

from __future__ import annotations

import json
from dataclasses import asdict, dataclass, field
from typing import Any


class ProtocolError(ValueError):
    """Некорректный или неожиданный кадр протокола."""


@dataclass
class AuthFrame:
    """Клиент → агент: предъявление токена доступа."""

    token: str
    type: str = "auth"


@dataclass
class AuthResult:
    """Агент → клиент: результат аутентификации."""

    ok: bool
    message: str = ""
    # Разрешены ли на этом агенте произвольные команды (не из макросов).
    allow_arbitrary: bool = False
    type: str = "auth_result"


@dataclass
class Request:
    """Клиент → агент: выполнить макрос (``kind='macro'``) или команду."""

    id: str
    kind: str  # "macro" | "command"
    value: str  # имя макроса или строка команды
    params: dict[str, str] = field(default_factory=dict)
    type: str = "request"


@dataclass
class OutputFrame:
    """Агент → клиент: кусок вывода одной из выполняемых команд."""

    id: str
    command: str  # какая именно команда породила вывод
    stream: str  # "stdout" | "stderr"
    data: str
    type: str = "output"


@dataclass
class ResultFrame:
    """Агент → клиент: завершение запроса.

    ``exit_code`` — код последней выполненной команды. ``error`` заполняется,
    если запрос отклонён (неизвестный макрос, запрещённая команда и т.п.).
    """

    id: str
    exit_code: int = 0
    error: str = ""
    done: bool = True
    type: str = "result"


_FRAME_TYPES: dict[str, type] = {
    "auth": AuthFrame,
    "auth_result": AuthResult,
    "request": Request,
    "output": OutputFrame,
    "result": ResultFrame,
}


def encode(frame: Any) -> str:
    """Сериализовать dataclass-кадр в JSON-строку."""
    return json.dumps(asdict(frame), ensure_ascii=False)


def decode(raw: str | bytes) -> Any:
    """Разобрать JSON-строку в соответствующий dataclass-кадр.

    :raises ProtocolError: если JSON некорректен или тип кадра неизвестен.
    """
    try:
        payload = json.loads(raw)
    except (json.JSONDecodeError, TypeError) as exc:
        raise ProtocolError(f"невалидный JSON: {exc}") from exc
    if not isinstance(payload, dict):
        raise ProtocolError("ожидался JSON-объект")
    frame_type = payload.get("type")
    cls = _FRAME_TYPES.get(frame_type)
    if cls is None:
        raise ProtocolError(f"неизвестный тип кадра: {frame_type!r}")
    field_names = {f for f in cls.__dataclass_fields__}  # type: ignore[attr-defined]
    kwargs = {k: v for k, v in payload.items() if k in field_names}
    try:
        return cls(**kwargs)
    except TypeError as exc:
        raise ProtocolError(f"не хватает полей для {frame_type}: {exc}") from exc
