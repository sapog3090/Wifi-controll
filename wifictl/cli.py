"""CLI wifictl: ``init`` | ``agent`` | ``connect``."""

from __future__ import annotations

import asyncio
import socket
from pathlib import Path

import click

from wifictl import __version__, security
from wifictl.config import ConfigError, load_agent_config


def _lan_ip() -> str:
    """Определить локальный IP устройства в WiFi-сети (без отправки трафика)."""
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("10.255.255.255", 1))
        return s.getsockname()[0]
    except OSError:
        return "127.0.0.1"
    finally:
        s.close()


_AGENT_TEMPLATE = """\
# Конфигурация агента wifictl. НЕ коммитьте этот файл — в нём токен.
host: 0.0.0.0            # слушать на всех интерфейсах LAN
port: {port}
token: "{token}"
cert_path: "{cert_path}"
key_path: "{key_path}"
macros_path: "{macros_path}"
audit_log: "audit.log"

# Произвольные команды (не из макросов). Включайте осознанно — только на
# устройствах, которыми владеете или управляете с разрешения владельца.
allow_arbitrary: false

# Необязательные списки бинарей. allow_binaries пуст => без белого списка.
allow_binaries: []
deny_binaries: ["rm", "shutdown", "reboot", "mkfs", "dd"]

command_timeout: 30
"""

_MACROS_TEMPLATE = """\
# Макросы: ключевое слово -> набор команд.
# '@имя' включает другой макрос; '{{param}}' подставляется из параметров.
macros:
  root:
    description: "Снимок состояния системы"
    commands:
      - "whoami"
      - "uptime"
      - "df -h"
  net:
    description: "Сетевые интерфейсы и подключения"
    commands:
      - "ip -brief address || ifconfig"
  full:
    description: "root + память + сеть"
    commands:
      - "@root"
      - "free -h"
      - "@net"
  deploy:
    description: "Обновить проект (пример с параметром)"
    params: [dir]
    commands:
      - "cd {{dir}} && git pull --ff-only"
"""


@click.group(help="Управление своими/авторизованными устройствами по WiFi (LAN).")
@click.version_option(__version__, prog_name="wifictl")
def main() -> None:  # pragma: no cover - точка входа
    pass


@main.command(help="Сгенерировать сертификат, токен и примеры конфигов.")
@click.option("--dir", "config_dir", default="config", show_default=True, help="Каталог конфигов.")
@click.option("--port", default=8765, show_default=True, help="Порт агента.")
@click.option("--force", is_flag=True, help="Перезаписать существующие файлы.")
def init(config_dir: str, port: int, force: bool) -> None:
    cfg_dir = Path(config_dir)
    cfg_dir.mkdir(parents=True, exist_ok=True)
    cert_path = cfg_dir / "agent-cert.pem"
    key_path = cfg_dir / "agent-key.pem"
    agent_cfg = cfg_dir / "agent.yaml"
    macros_cfg = cfg_dir / "macros.yaml"

    existing = [p for p in (cert_path, key_path, agent_cfg, macros_cfg) if p.exists()]
    if existing and not force:
        raise click.ClickException(
            "уже существуют: " + ", ".join(str(p) for p in existing) + " (используйте --force)"
        )

    fingerprint = security.generate_self_signed_cert(cert_path, key_path)
    token = security.generate_token()

    # Пути пишем относительно каталога конфига (все файлы лежат рядом) —
    # так конфиг самодостаточен и агент запускается из любого рабочего каталога.
    agent_cfg.write_text(
        _AGENT_TEMPLATE.format(
            port=port, token=token,
            cert_path=cert_path.name, key_path=key_path.name,
            macros_path=macros_cfg.name,
        ),
        encoding="utf-8",
    )
    if not macros_cfg.exists() or force:
        macros_cfg.write_text(_MACROS_TEMPLATE, encoding="utf-8")

    ip = _lan_ip()
    click.echo("Готово. Данные для подключения клиента:\n")
    click.echo(f"  host        : {ip}")
    click.echo(f"  port        : {port}")
    click.echo(f"  token       : {token}")
    click.echo(f"  fingerprint : {fingerprint}\n")
    click.echo(f"Запуск агента : wifictl agent --config {agent_cfg.as_posix()}")
    click.echo(
        f"Подключение   : wifictl connect {ip}:{port} --token {token} --fingerprint {fingerprint}"
    )


@main.command(help="Запустить агента на этом устройстве.")
@click.option("--config", "config_path", default="config/agent.yaml", show_default=True)
def agent(config_path: str) -> None:
    from wifictl.agent.server import run_agent

    try:
        cfg = load_agent_config(config_path)
    except ConfigError as exc:
        raise click.ClickException(str(exc))
    click.echo(f"Агент: wss://{cfg.host}:{cfg.port}  (Ctrl+C — остановить)")
    try:
        asyncio.run(run_agent(cfg))
    except KeyboardInterrupt:
        click.echo("\nОстановлено.")


@main.command(help="Подключиться к агенту и открыть REPL.")
@click.argument("address")
@click.option("--token", required=True, help="Токен доступа агента.")
@click.option("--fingerprint", default=None, help="Ожидаемый SHA-256 отпечаток сертификата.")
def connect(address: str, token: str, fingerprint: str | None) -> None:
    from wifictl.client.connection import WifictlClient
    from wifictl.client.repl import run_repl

    host, _, port_s = address.partition(":")
    if not port_s:
        raise click.ClickException("адрес должен быть в формате host:port")
    try:
        port = int(port_s)
    except ValueError:
        raise click.ClickException(f"некорректный порт: {port_s}")

    async def _go() -> None:
        try:
            client = await WifictlClient.connect(host, port, token, fingerprint)
        except Exception as exc:  # noqa: BLE001
            raise click.ClickException(f"не удалось подключиться: {exc}")
        await run_repl(client, address)

    try:
        asyncio.run(_go())
    except KeyboardInterrupt:
        click.echo("\nОтключено.")


if __name__ == "__main__":  # pragma: no cover
    main()
