import pytest

from wifictl import protocol


def test_roundtrip_request():
    req = protocol.Request(id="1", kind="macro", value="root", params={"a": "b"})
    decoded = protocol.decode(protocol.encode(req))
    assert isinstance(decoded, protocol.Request)
    assert decoded == req


def test_roundtrip_all_frames():
    frames = [
        protocol.AuthFrame(token="t"),
        protocol.AuthResult(ok=True, allow_arbitrary=True),
        protocol.OutputFrame(id="1", command="ls", stream="stdout", data="x"),
        protocol.ResultFrame(id="1", exit_code=0),
    ]
    for f in frames:
        assert protocol.decode(protocol.encode(f)) == f


def test_invalid_json():
    with pytest.raises(protocol.ProtocolError):
        protocol.decode("{not json")


def test_unknown_type():
    with pytest.raises(protocol.ProtocolError):
        protocol.decode('{"type": "bogus"}')


def test_extra_keys_ignored():
    decoded = protocol.decode('{"type": "auth", "token": "t", "junk": 1}')
    assert isinstance(decoded, protocol.AuthFrame)
    assert decoded.token == "t"
