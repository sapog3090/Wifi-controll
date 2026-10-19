"""Безопасность: токены, сравнение в constant-time, self-signed TLS, отпечатки.

Модель доступа простая и рассчитана на доверенную локальную сеть:
  * общий pre-shared токен (сравнивается через :func:`hmac.compare_digest`);
  * TLS с self-signed сертификатом, клиент пиннит его SHA-256 отпечаток (TOFU).

Это НЕ замена полноценной PKI; инструмент предназначен для своих/авторизованных
устройств в рамках одной WiFi-сети.
"""

from __future__ import annotations

import datetime as _dt
import hashlib
import hmac
import secrets
import ssl
from pathlib import Path


def generate_token(nbytes: int = 32) -> str:
    """Сгенерировать случайный URL-safe токен доступа."""
    return secrets.token_urlsafe(nbytes)


def tokens_match(expected: str, provided: str) -> bool:
    """Сравнить токены в постоянное время (защита от timing-атак)."""
    return hmac.compare_digest(expected.encode("utf-8"), provided.encode("utf-8"))


def cert_fingerprint(cert_path: str | Path) -> str:
    """SHA-256 отпечаток сертификата в hex (для пиннинга на клиенте)."""
    pem = Path(cert_path).read_bytes()
    der = ssl.PEM_cert_to_DER_cert(pem.decode("ascii"))
    return hashlib.sha256(der).hexdigest()


def fingerprint_of_der(der: bytes) -> str:
    """SHA-256 отпечаток DER-сертификата в hex."""
    return hashlib.sha256(der).hexdigest()


def generate_self_signed_cert(
    cert_path: str | Path,
    key_path: str | Path,
    common_name: str = "wifictl-agent",
    days_valid: int = 3650,
) -> str:
    """Создать self-signed сертификат и приватный ключ.

    Возвращает SHA-256 отпечаток созданного сертификата. Требует пакет
    ``cryptography`` (тянется как зависимость ``websockets``). Файл ключа
    создаётся с правами 0600.
    """
    from cryptography import x509
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import rsa
    from cryptography.x509.oid import NameOID

    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, common_name)])
    now = _dt.datetime.now(_dt.timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - _dt.timedelta(minutes=1))
        .not_valid_after(now + _dt.timedelta(days=days_valid))
        .add_extension(x509.SubjectAlternativeName([x509.DNSName(common_name)]), critical=False)
        .sign(key, hashes.SHA256())
    )

    key_path = Path(key_path)
    cert_path = Path(cert_path)
    key_path.write_bytes(
        key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.TraditionalOpenSSL,
            encryption_algorithm=serialization.NoEncryption(),
        )
    )
    key_path.chmod(0o600)
    cert_path.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
    return cert_fingerprint(cert_path)


def server_ssl_context(cert_path: str | Path, key_path: str | Path) -> ssl.SSLContext:
    """TLS-контекст для агента (серверная сторона)."""
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    ctx.load_cert_chain(certfile=str(cert_path), keyfile=str(key_path))
    return ctx


def client_ssl_context(expected_fingerprint: str | None = None) -> ssl.SSLContext:
    """TLS-контекст для клиента.

    Self-signed сертификат не проходит обычную проверку CA, поэтому проверку
    цепочки и имени отключаем, а подлинность агента подтверждаем пиннингом
    отпечатка (:func:`verify_peer_fingerprint`) после рукопожатия.
    """
    ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_CLIENT)
    ctx.check_hostname = False
    ctx.verify_mode = ssl.CERT_NONE
    # Отпечаток пиннингуется вызывающей стороной; сохраняем для справки.
    ctx._wifictl_expected_fingerprint = expected_fingerprint  # type: ignore[attr-defined]
    return ctx


def verify_peer_fingerprint(der_cert: bytes | None, expected_fingerprint: str | None) -> None:
    """Проверить отпечаток сертификата агента (TOFU-пиннинг).

    :raises ssl.SSLError: если отпечаток не совпал.
    """
    if not expected_fingerprint:
        return  # пиннинг не настроен — доверяем на первом подключении
    if der_cert is None:
        raise ssl.SSLError("агент не предъявил сертификат")
    actual = fingerprint_of_der(der_cert)
    if not hmac.compare_digest(actual, expected_fingerprint.lower()):
        raise ssl.SSLError(
            "отпечаток сертификата агента не совпал: "
            f"ожидался {expected_fingerprint}, получен {actual}"
        )
