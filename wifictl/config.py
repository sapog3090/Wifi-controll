"""Загрузка и валидация YAML-конфигурации агента."""

from __future__ import annotations

from dataclasses import dataclass, field
from pathlib import Path

import yaml


class ConfigError(ValueError):
    """Ошибка в конфигурации агента."""


@dataclass
class AgentConfig:
    """Конфигурация агента (устройства-цели).

    :param host: интерфейс для прослушивания (``0.0.0.0`` — все интерфейсы LAN).
    :param port: TCP-порт WebSocket-сервера.
    :param token: pre-shared токен доступа.
    :param cert_path: путь к TLS-сертификату (PEM).
    :param key_path: путь к приватному ключу (PEM).
    :param macros_path: путь к файлу с макросами.
    :param audit_log: путь к аудит-логу (JSON-lines).
    :param allow_arbitrary: разрешить произвольные команды (не из макросов).
    :param allow_binaries: белый список исполняемых файлов (пусто — без ограничений).
    :param deny_binaries: чёрный список исполняемых файлов.
    :param command_timeout: таймаут выполнения одной команды, сек.
    """

    host: str = "0.0.0.0"
    port: int = 8765
    token: str = ""
    cert_path: str = "config/agent-cert.pem"
    key_path: str = "config/agent-key.pem"
    macros_path: str = "config/macros.yaml"
    audit_log: str = "audit.log"
    allow_arbitrary: bool = False
    allow_binaries: list[str] = field(default_factory=list)
    deny_binaries: list[str] = field(default_factory=list)
    command_timeout: float = 30.0

    def validate(self) -> None:
        if not self.token:
            raise ConfigError("в конфиге не задан 'token' — запустите 'wifictl init'")
        if not (0 < self.port < 65536):
            raise ConfigError(f"некорректный порт: {self.port}")
        if self.command_timeout <= 0:
            raise ConfigError("command_timeout должен быть положительным")


_KNOWN_KEYS = set(AgentConfig.__dataclass_fields__)


def load_agent_config(path: str | Path) -> AgentConfig:
    """Прочитать и провалидировать конфиг агента из YAML-файла."""
    path = Path(path)
    if not path.exists():
        raise ConfigError(f"файл конфигурации не найден: {path}")
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    if not isinstance(data, dict):
        raise ConfigError("конфиг должен быть YAML-объектом (отображением)")

    unknown = set(data) - _KNOWN_KEYS
    if unknown:
        raise ConfigError(f"неизвестные ключи в конфиге: {', '.join(sorted(unknown))}")

    cfg = AgentConfig(**data)
    cfg.validate()

    # Относительные пути трактуем относительно каталога конфига, чтобы агент
    # можно было запускать из любого рабочего каталога.
    base = path.resolve().parent
    for attr in ("cert_path", "key_path", "macros_path", "audit_log"):
        value = Path(getattr(cfg, attr))
        if not value.is_absolute():
            setattr(cfg, attr, str((base / value).resolve()))
    return cfg
