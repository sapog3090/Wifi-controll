"""Выполнение команд на устройстве со стримингом вывода.

Команды выполняются через системную оболочку (``/bin/sh -c`` или аналог),
потому что макросы рассчитаны на shell-синтаксис (``cd x && ...``, пайпы).
Вывод отдаётся построчно через async-колбэк, чтобы клиент видел результат
в реальном времени. На каждую команду действует таймаут.
"""

from __future__ import annotations

import asyncio
import shlex
from dataclasses import dataclass
from typing import Awaitable, Callable

# Колбэк получает (stream, text): stream — "stdout" или "stderr".
OutputCallback = Callable[[str, str], Awaitable[None]]


class CommandDenied(PermissionError):
    """Команда запрещена политикой агента (allow/deny список)."""


@dataclass
class ExecResult:
    exit_code: int
    timed_out: bool = False


def _first_binary(command: str) -> str | None:
    """Имя первого исполняемого файла в команде (для allow/deny-проверки)."""
    try:
        tokens = shlex.split(command)
    except ValueError:
        return None
    for tok in tokens:
        # Пропускаем присваивания переменных окружения (FOO=bar cmd).
        if "=" in tok and not tok.startswith(("-", "/")) and tok.split("=", 1)[0].isidentifier():
            continue
        return tok.rsplit("/", 1)[-1]
    return None


def check_policy(
    command: str,
    allow_binaries: list[str] | None,
    deny_binaries: list[str] | None,
) -> None:
    """Проверить команду по allow/deny-спискам бинарей.

    :raises CommandDenied: если команда запрещена.
    """
    binary = _first_binary(command)
    if binary is None:
        raise CommandDenied(f"не удалось разобрать команду: {command!r}")
    if deny_binaries and binary in deny_binaries:
        raise CommandDenied(f"команда '{binary}' в чёрном списке")
    if allow_binaries and binary not in allow_binaries:
        raise CommandDenied(f"команда '{binary}' не в белом списке")


async def run_command(
    command: str,
    on_output: OutputCallback,
    timeout: float = 30.0,
) -> ExecResult:
    """Выполнить одну shell-команду, стримя вывод в ``on_output``.

    При превышении ``timeout`` процесс убивается, возвращается
    ``ExecResult(exit_code=124, timed_out=True)``.
    """
    proc = await asyncio.create_subprocess_shell(
        command,
        stdout=asyncio.subprocess.PIPE,
        stderr=asyncio.subprocess.PIPE,
    )

    async def pump(reader: asyncio.StreamReader, stream: str) -> None:
        while True:
            line = await reader.readline()
            if not line:
                break
            await on_output(stream, line.decode("utf-8", errors="replace"))

    pumps = asyncio.gather(
        pump(proc.stdout, "stdout"),  # type: ignore[arg-type]
        pump(proc.stderr, "stderr"),  # type: ignore[arg-type]
    )
    try:
        await asyncio.wait_for(proc.wait(), timeout=timeout)
    except asyncio.TimeoutError:
        proc.kill()
        await proc.wait()
        pumps.cancel()
        try:
            await pumps
        except asyncio.CancelledError:
            pass
        await on_output("stderr", f"[wifictl] команда прервана по таймауту ({timeout}s)\n")
        return ExecResult(exit_code=124, timed_out=True)

    await pumps
    return ExecResult(exit_code=proc.returncode or 0)
