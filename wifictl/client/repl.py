"""Интерактивный REPL клиента.

Ввод:
  * ``имя_макроса`` — выполнить макрос (``имя key=value`` — с параметрами);
  * ``!команда`` — выполнить произвольную команду (если агент разрешил);
  * ``:macros`` — список макросов; ``:help`` — помощь; ``:quit`` / ``:q`` — выход.
"""

from __future__ import annotations

import asyncio

from rich.console import Console

from wifictl import protocol
from wifictl.client.connection import WifictlClient

console = Console()

_HELP = """\
[bold]Команды REPL:[/bold]
  <макрос> [k=v ...]   выполнить макрос (напр.: root, deploy dir=/srv/app)
  ![/]команда           выполнить произвольную команду (если агент разрешил)
  :macros              показать доступные макросы
  :help                эта справка
  :quit, :q            выход
"""


def _parse_params(tokens: list[str]) -> dict[str, str]:
    params: dict[str, str] = {}
    for tok in tokens:
        if "=" in tok:
            key, val = tok.split("=", 1)
            params[key] = val
    return params


async def _print_output(frame: protocol.OutputFrame) -> None:
    text = frame.data.rstrip("\n")
    if frame.stream == "stderr":
        console.print(text, style="yellow")
    else:
        console.print(text)


async def run_repl(client: WifictlClient, target: str) -> None:
    """Запустить цикл чтения команд до выхода пользователя."""
    console.print(f"[green]Подключено к[/green] {target}")
    if client.allow_arbitrary:
        console.print("[dim]Произвольные команды разрешены (префикс !).[/dim]")
    else:
        console.print("[dim]Разрешены только макросы (произвольные команды отключены).[/dim]")
    console.print("[dim]:help — справка, :quit — выход[/dim]")

    try:
        while True:
            try:
                line = (await asyncio.to_thread(input, "wifictl> ")).strip()
            except EOFError:
                break
            if not line:
                continue

            if line in (":quit", ":q"):
                break
            if line == ":help":
                console.print(_HELP)
                continue
            if line == ":macros":
                await _dispatch(client, "list", "", {})
                continue

            if line.startswith("!"):
                command = line[1:].strip()
                if not command:
                    continue
                await _dispatch(client, "command", command, {})
                continue

            tokens = line.split()
            name, param_tokens = tokens[0], tokens[1:]
            await _dispatch(client, "macro", name, _parse_params(param_tokens))
    finally:
        await client.close()
        console.print("[green]Отключено.[/green]")


async def _dispatch(client: WifictlClient, kind: str, value: str, params: dict[str, str]) -> None:
    try:
        result = await client.run(kind, value, params, on_output=_print_output)
    except Exception as exc:  # noqa: BLE001 — показать пользователю и продолжить
        console.print(f"[red]Ошибка связи: {exc}[/red]")
        return
    if result.error:
        console.print(f"[red]{result.error}[/red]")
    elif result.exit_code != 0:
        console.print(f"[yellow]Код возврата: {result.exit_code}[/yellow]")
