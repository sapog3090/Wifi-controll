import ssl

import pytest

from wifictl import security


def test_token_generation_unique():
    assert security.generate_token() != security.generate_token()


def test_tokens_match():
    tok = security.generate_token()
    assert security.tokens_match(tok, tok)
    assert not security.tokens_match(tok, tok + "x")
    assert not security.tokens_match("", "x")


def test_self_signed_cert_and_fingerprint(tmp_path):
    cert = tmp_path / "c.pem"
    key = tmp_path / "k.pem"
    fp = security.generate_self_signed_cert(cert, key)
    assert len(fp) == 64  # sha256 hex
    assert security.cert_fingerprint(cert) == fp
    # ключ создан с приватными правами
    assert (key.stat().st_mode & 0o077) == 0


def test_verify_peer_fingerprint_mismatch():
    with pytest.raises(ssl.SSLError):
        security.verify_peer_fingerprint(b"\x00\x01", "a" * 64)


def test_verify_peer_fingerprint_none_expected_ok():
    # отпечаток не настроен => проверка пропускается
    security.verify_peer_fingerprint(None, None)
