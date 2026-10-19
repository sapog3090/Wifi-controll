import pytest

from wifictl.agent.executor import CommandDenied, check_policy, run_command


async def _collect(command, timeout=10.0):
    chunks = []

    async def on_output(stream, data):
        chunks.append((stream, data))

    result = await run_command(command, on_output, timeout=timeout)
    return result, chunks


async def test_run_echo_stdout():
    result, chunks = await _collect("echo hello")
    assert result.exit_code == 0
    assert any("hello" in data for stream, data in chunks if stream == "stdout")


async def test_nonzero_exit_code():
    result, _ = await _collect("exit 3")
    assert result.exit_code == 3


async def test_stderr_captured():
    result, chunks = await _collect("echo oops 1>&2")
    assert any(stream == "stderr" and "oops" in data for stream, data in chunks)


async def test_timeout_kills_process():
    result, _ = await _collect("sleep 5", timeout=0.3)
    assert result.timed_out
    assert result.exit_code == 124


def test_policy_deny_list():
    with pytest.raises(CommandDenied):
        check_policy("rm -rf /tmp/x", allow_binaries=[], deny_binaries=["rm"])


def test_policy_allow_list_blocks_others():
    with pytest.raises(CommandDenied):
        check_policy("curl http://x", allow_binaries=["echo"], deny_binaries=[])
    # разрешённый бинарь проходит
    check_policy("echo hi", allow_binaries=["echo"], deny_binaries=[])


def test_policy_skips_env_assignment():
    # FOO=bar echo -> проверяется echo, не присваивание
    check_policy("FOO=bar echo hi", allow_binaries=["echo"], deny_binaries=[])
