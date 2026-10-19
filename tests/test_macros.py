import pytest

from wifictl.macros import MacroEngine, MacroError

DATA = {
    "macros": {
        "root": {"description": "snapshot", "commands": ["whoami", "uptime"]},
        "full": {"commands": ["@root", "free -h"]},
        "deploy": {"params": ["dir"], "commands": ["cd {dir} && git pull"]},
        "loop_a": {"commands": ["@loop_b"]},
        "loop_b": {"commands": ["@loop_a"]},
    }
}


def engine():
    return MacroEngine.from_dict(DATA)


def test_expand_simple():
    assert engine().expand("root") == ["whoami", "uptime"]


def test_include_expands_nested():
    assert engine().expand("full") == ["whoami", "uptime", "free -h"]


def test_params_substitution():
    assert engine().expand("deploy", {"dir": "/srv/app"}) == ["cd /srv/app && git pull"]


def test_missing_param_raises():
    with pytest.raises(MacroError, match="dir"):
        engine().expand("deploy")


def test_unknown_macro_raises():
    with pytest.raises(MacroError, match="неизвестный"):
        engine().expand("nope")


def test_recursion_detected():
    with pytest.raises(MacroError, match="рекурси"):
        engine().expand("loop_a")


def test_empty_commands_rejected():
    with pytest.raises(MacroError):
        MacroEngine.from_dict({"macros": {"x": {"commands": []}}})


def test_describe_and_names():
    eng = engine()
    assert "root" in eng.names()
    assert ("root", "snapshot") in eng.describe()
