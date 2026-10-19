"""WebSocket-клиент: подключение к агенту, TLS-пиннинг, отправка запросов."""

from __future__ import annotations

import uuid
from typing import Awaitable, Callable

import websockets
from websockets.client import WebSocketClientProtocol

from wifictl import protocol, security

OutputHandler = Callable[[protocol.OutputFrame], Awaitable[None]]


class AuthFailed(RuntimeError):
    """Агент отклонил аутентификацию."""


class WifictlClient:
    """Подключение к одному агенту."""

    def __init__(self, ws: WebSocketClientProtocol, allow_arbitrary: bool):
        self._ws = ws
        self.allow_arbitrary = allow_arbitrary

    @classmethod
    async def connect(
        cls,
        host: str,
        port: int,
        token: str,
        fingerprint: str | None = None,
    ) -> "WifictlClient":
        """Подключиться, проверить отпечаток сертификата и пройти аутентификацию."""
        ssl_ctx = security.client_ssl_context(fingerprint)
        uri = f"wss://{host}:{port}"
        ws = await websockets.connect(uri, ssl=ssl_ctx, ping_interval=20)

        # TOFU-пиннинг: сверяем отпечаток реально предъявленного сертификата.
        ssl_obj = ws.transport.get_extra_info("ssl_object")
        der = ssl_obj.getpeercert(binary_form=True) if ssl_obj else None
        try:
            security.verify_peer_fingerprint(der, fingerprint)
        except Exception:
            await ws.close()
            raise

        await ws.send(protocol.encode(protocol.AuthFrame(token=token)))
        result = protocol.decode(await ws.recv())
        if not isinstance(result, protocol.AuthResult) or not result.ok:
            msg = getattr(result, "message", "отказано")
            await ws.close()
            raise AuthFailed(f"аутентификация не удалась: {msg}")
        return cls(ws, allow_arbitrary=result.allow_arbitrary)

    async def run(
        self,
        kind: str,
        value: str,
        params: dict[str, str] | None = None,
        on_output: OutputHandler | None = None,
    ) -> protocol.ResultFrame:
        """Отправить запрос, прокинуть вывод в ``on_output``, вернуть результат."""
        req_id = uuid.uuid4().hex
        await self._ws.send(
            protocol.encode(
                protocol.Request(id=req_id, kind=kind, value=value, params=params or {})
            )
        )
        async for raw in self._ws:
            frame = protocol.decode(raw)
            if isinstance(frame, protocol.OutputFrame) and frame.id == req_id:
                if on_output is not None:
                    await on_output(frame)
            elif isinstance(frame, protocol.ResultFrame) and frame.id == req_id:
                return frame
        raise ConnectionError("соединение закрыто до получения результата")

    async def close(self) -> None:
        await self._ws.close()
