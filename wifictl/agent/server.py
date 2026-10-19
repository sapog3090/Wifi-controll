"""WebSocket-сервер агента: аутентификация, диспетчеризация, стриминг вывода."""

from __future__ import annotations

import asyncio
import logging

import websockets
from websockets.server import WebSocketServerProtocol

from wifictl import protocol, security
from wifictl.agent.audit import AuditLog
from wifictl.agent.executor import CommandDenied, check_policy, run_command
from wifictl.config import AgentConfig
from wifictl.macros import MacroEngine, MacroError

logger = logging.getLogger("wifictl.agent")


class Agent:
    """Агент на целевом устройстве."""

    def __init__(self, config: AgentConfig, engine: MacroEngine, audit: AuditLog):
        self._config = config
        self._engine = engine
        self._audit = audit

    async def serve(self) -> None:
        """Запустить сервер и работать до отмены задачи."""
        ssl_ctx = security.server_ssl_context(self._config.cert_path, self._config.key_path)
        async with websockets.serve(
            self._handle,
            self._config.host,
            self._config.port,
            ssl=ssl_ctx,
            ping_interval=20,
        ):
            logger.info(
                "агент слушает wss://%s:%s (allow_arbitrary=%s)",
                self._config.host,
                self._config.port,
                self._config.allow_arbitrary,
            )
            await asyncio.Future()  # работаем до отмены

    # --- обработка соединения --------------------------------------------

    async def _handle(self, ws: WebSocketServerProtocol) -> None:
        peer = self._peer(ws)
        logger.info("подключение от %s", peer)
        try:
            if not await self._authenticate(ws):
                return
            async for raw in ws:
                await self._dispatch(ws, raw, peer)
        except websockets.ConnectionClosed:
            logger.info("соединение с %s закрыто", peer)
        except protocol.ProtocolError as exc:
            logger.warning("протокольная ошибка от %s: %s", peer, exc)

    async def _authenticate(self, ws: WebSocketServerProtocol) -> bool:
        raw = await ws.recv()
        frame = protocol.decode(raw)
        if not isinstance(frame, protocol.AuthFrame):
            await self._send(ws, protocol.AuthResult(ok=False, message="ожидался auth-кадр"))
            return False
        if not security.tokens_match(self._config.token, frame.token):
            await self._send(ws, protocol.AuthResult(ok=False, message="неверный токен"))
            logger.warning("отклонена аутентификация от %s", self._peer(ws))
            return False
        await self._send(
            ws,
            protocol.AuthResult(ok=True, allow_arbitrary=self._config.allow_arbitrary),
        )
        return True

    async def _dispatch(self, ws: WebSocketServerProtocol, raw, peer: str) -> None:
        frame = protocol.decode(raw)
        if not isinstance(frame, protocol.Request):
            raise protocol.ProtocolError("ожидался request-кадр")

        if frame.kind == "macro":
            await self._run_macro(ws, frame, peer)
        elif frame.kind == "command":
            await self._run_arbitrary(ws, frame, peer)
        elif frame.kind == "list":
            await self._list_macros(ws, frame)
        else:
            await self._send(
                ws, protocol.ResultFrame(id=frame.id, exit_code=2, error=f"неизвестный kind: {frame.kind}")
            )

    async def _list_macros(self, ws: WebSocketServerProtocol, req: protocol.Request) -> None:
        for name, desc in self._engine.describe():
            line = f"{name:<16} {desc}\n" if desc else f"{name}\n"
            await self._send(ws, protocol.OutputFrame(id=req.id, command="", stream="stdout", data=line))
        await self._send(ws, protocol.ResultFrame(id=req.id, exit_code=0))

    # --- выполнение -------------------------------------------------------

    async def _run_macro(self, ws: WebSocketServerProtocol, req: protocol.Request, peer: str) -> None:
        try:
            commands = self._engine.expand(req.value, req.params)
        except MacroError as exc:
            self._audit.record(peer=peer, kind="macro", value=req.value, status="error", error=str(exc))
            await self._send(ws, protocol.ResultFrame(id=req.id, exit_code=2, error=str(exc)))
            return
        await self._run_sequence(ws, req, peer, commands, kind="macro")

    async def _run_arbitrary(self, ws: WebSocketServerProtocol, req: protocol.Request, peer: str) -> None:
        if not self._config.allow_arbitrary:
            msg = "произвольные команды запрещены (allow_arbitrary=false)"
            self._audit.record(peer=peer, kind="command", value=req.value, status="denied", error=msg)
            await self._send(ws, protocol.ResultFrame(id=req.id, exit_code=126, error=msg))
            return
        await self._run_sequence(ws, req, peer, [req.value], kind="command")

    async def _run_sequence(
        self,
        ws: WebSocketServerProtocol,
        req: protocol.Request,
        peer: str,
        commands: list[str],
        kind: str,
    ) -> None:
        last_exit = 0
        for command in commands:
            try:
                check_policy(command, self._config.allow_binaries, self._config.deny_binaries)
            except CommandDenied as exc:
                self._audit.record(
                    peer=peer, kind=kind, value=req.value, command=command, status="denied", error=str(exc)
                )
                await self._send(ws, protocol.ResultFrame(id=req.id, exit_code=126, error=str(exc)))
                return

            async def on_output(stream: str, data: str, _cmd=command) -> None:
                await self._send(
                    ws, protocol.OutputFrame(id=req.id, command=_cmd, stream=stream, data=data)
                )

            result = await run_command(command, on_output, timeout=self._config.command_timeout)
            last_exit = result.exit_code
            self._audit.record(
                peer=peer, kind=kind, value=req.value, command=command,
                exit_code=result.exit_code, status="executed",
            )
            if result.exit_code != 0:
                # Прерываем последовательность при первой неуспешной команде.
                break

        await self._send(ws, protocol.ResultFrame(id=req.id, exit_code=last_exit))

    # --- утилиты ----------------------------------------------------------

    @staticmethod
    async def _send(ws: WebSocketServerProtocol, frame) -> None:
        await ws.send(protocol.encode(frame))

    @staticmethod
    def _peer(ws: WebSocketServerProtocol) -> str:
        addr = ws.remote_address
        if addr:
            return f"{addr[0]}:{addr[1]}"
        return "unknown"


async def run_agent(config: AgentConfig) -> None:
    """Собрать и запустить агента по конфигу."""
    engine = MacroEngine.from_file(config.macros_path)
    audit = AuditLog(config.audit_log)
    agent = Agent(config, engine, audit)
    await agent.serve()
