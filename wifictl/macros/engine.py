"""Загрузка макросов и разворачивание ключевого слова в список команд.

Формат файла макросов (YAML)::

    macros:
      root:
        description: "Снимок состояния системы"
        commands: ["whoami", "uptime", "df -h"]
      full:
        commands:
          - "@root"          # включение другого макроса
          - "free -h"
      deploy:
        params: [dir]
        commands: ["cd {dir} && git pull --ff-only"]

Команда вида ``@name`` включает другой макрос (с защитой от рекурсии).
Плейсхолдеры ``{param}`` подставляются из переданных параметров.
"""

from __future__ import annotations

import re
import string
from dataclasses import dataclass, field
from pathlib import Path

import yaml

_INCLUDE_PREFIX = "@"
_PLACEHOLDER_RE = re.compile(r"\{([a-zA-Z_][a-zA-Z0-9_]*)\}")


class MacroError(ValueError):
    """Ошибка определения или разворачивания макроса."""


@dataclass
class Macro:
    """Определение одного макроса."""

    name: str
    commands: list[str]
    description: str = ""
    params: list[str] = field(default_factory=list)


class MacroEngine:
    """Хранит макросы и разворачивает ключевое слово в список команд."""

    def __init__(self, macros: dict[str, Macro]):
        self._macros = macros

    # --- загрузка ---------------------------------------------------------

    @classmethod
    def from_file(cls, path: str | Path) -> "MacroEngine":
        path = Path(path)
        if not path.exists():
            raise MacroError(f"файл макросов не найден: {path}")
        data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
        return cls.from_dict(data)

    @classmethod
    def from_dict(cls, data: dict) -> "MacroEngine":
        if not isinstance(data, dict):
            raise MacroError("определения макросов должны быть YAML-объектом")
        raw = data.get("macros", data)
        if not isinstance(raw, dict):
            raise MacroError("ключ 'macros' должен быть отображением")

        macros: dict[str, Macro] = {}
        for name, body in raw.items():
            if not isinstance(body, dict):
                raise MacroError(f"макрос {name!r}: тело должно быть отображением")
            commands = body.get("commands")
            if not isinstance(commands, list) or not commands:
                raise MacroError(f"макрос {name!r}: нужен непустой список 'commands'")
            if not all(isinstance(c, str) for c in commands):
                raise MacroError(f"макрос {name!r}: все команды должны быть строками")
            params = body.get("params", [])
            if not isinstance(params, list) or not all(isinstance(p, str) for p in params):
                raise MacroError(f"макрос {name!r}: 'params' должен быть списком строк")
            macros[name] = Macro(
                name=name,
                commands=list(commands),
                description=str(body.get("description", "")),
                params=list(params),
            )
        return cls(macros)

    # --- доступ -----------------------------------------------------------

    def names(self) -> list[str]:
        return sorted(self._macros)

    def get(self, name: str) -> Macro | None:
        return self._macros.get(name)

    def describe(self) -> list[tuple[str, str]]:
        """Список ``(имя, описание)`` для вывода пользователю."""
        return [(m.name, m.description) for m in self._macros.values()]

    # --- разворачивание ---------------------------------------------------

    def expand(self, name: str, params: dict[str, str] | None = None) -> list[str]:
        """Развернуть макрос в плоский список готовых команд.

        :raises MacroError: неизвестный макрос, рекурсия, нехватка параметров.
        """
        params = params or {}
        commands = self._expand_recursive(name, _seen=())
        return [self._substitute(cmd, name, params) for cmd in commands]

    def _expand_recursive(self, name: str, _seen: tuple[str, ...]) -> list[str]:
        if name in _seen:
            cycle = " -> ".join([*_seen, name])
            raise MacroError(f"обнаружена рекурсия макросов: {cycle}")
        macro = self._macros.get(name)
        if macro is None:
            raise MacroError(f"неизвестный макрос: {name!r}")

        result: list[str] = []
        for cmd in macro.commands:
            if cmd.startswith(_INCLUDE_PREFIX):
                included = cmd[len(_INCLUDE_PREFIX):].strip()
                result.extend(self._expand_recursive(included, (*_seen, name)))
            else:
                result.append(cmd)
        return result

    @staticmethod
    def _substitute(command: str, macro_name: str, params: dict[str, str]) -> str:
        """Подставить плейсхолдеры ``{param}``; экранировать ``{{``/``}}``."""
        needed = set(_PLACEHOLDER_RE.findall(command))
        missing = needed - set(params)
        if missing:
            raise MacroError(
                f"макрос {macro_name!r}: не заданы параметры: {', '.join(sorted(missing))}"
            )
        try:
            return string.Formatter().vformat(command, (), _SafeParams(params))
        except (KeyError, ValueError) as exc:
            raise MacroError(f"макрос {macro_name!r}: ошибка подстановки: {exc}") from exc


class _SafeParams(dict):
    """Отображение для ``vformat``: KeyError с понятным текстом."""

    def __missing__(self, key):  # pragma: no cover - проверяется заранее в expand
        raise KeyError(key)
