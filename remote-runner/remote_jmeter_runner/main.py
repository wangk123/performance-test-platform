import argparse
import json
import sys
import time
from pathlib import Path

import paramiko


JMETER_IMAGE = "justb4/jmeter:latest"


def respond(ok, message="", log="", exit_code=0):
    print(json.dumps({
        "ok": ok,
        "exitCode": exit_code,
        "message": message,
        "log": log,
    }, ensure_ascii=False))
    return 0 if ok else 1


def connect(node):
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    connect_args = {
        "hostname": node["host"],
        "port": int(node.get("sshPort", 22)),
        "username": node["sshUsername"],
        "timeout": 20,
        "banner_timeout": 20,
        "auth_timeout": 20,
    }
    if node.get("sshPassword"):
        connect_args["password"] = node["sshPassword"]
    else:
        connect_args["key_filename"] = node["sshKeyPath"]
    client.connect(**connect_args)
    return client


def run(client, command):
    stdin, stdout, stderr = client.exec_command(command)
    code = stdout.channel.recv_exit_status()
    output = stdout.read().decode("utf-8", errors="replace")
    error = stderr.read().decode("utf-8", errors="replace")
    return code, output + error


def sftp_put(client, local_path, remote_path):
    remote_file = str(remote_path)
    run(client, f"mkdir -p {shell_quote(str(remote_path.parent))}")
    with client.open_sftp() as sftp:
        sftp.put(str(local_path), remote_file)


def sftp_get(client, remote_path, local_path):
    Path(local_path).parent.mkdir(parents=True, exist_ok=True)
    with client.open_sftp() as sftp:
        sftp.get(str(remote_path), str(local_path))


def shell_quote(value):
    return "'" + value.replace("'", "'\"'\"'") + "'"


def check_node(payload):
    client = None
    try:
        client = connect(payload)
        command = " ".join([
            "docker info >/dev/null &&",
            "(docker image inspect", shell_quote(JMETER_IMAGE), ">/dev/null 2>&1 || docker pull", shell_quote(JMETER_IMAGE), ">/dev/null) &&",
            "echo ready",
        ])
        code, output = run(client, command)
        return respond(code == 0, "docker ready" if code == 0 else output.strip(), output, code)
    except Exception as exc:
        return respond(False, str(exc))
    finally:
        if client:
            client.close()


def install_key(payload):
    client = None
    try:
        client = connect(payload)
        public_key = shell_quote(payload["publicKey"])
        remote_dir = shell_quote(payload.get("remoteWorkDir", "/tmp/perftest-platform"))
        command = " ".join([
            "mkdir -p ~/.ssh", remote_dir + ";",
            "touch ~/.ssh/authorized_keys;",
            "grep -qxF", public_key, "~/.ssh/authorized_keys || echo", public_key, ">> ~/.ssh/authorized_keys;",
            "chmod 700 ~/.ssh;",
            "chmod 600 ~/.ssh/authorized_keys;",
            "docker info >/dev/null;",
            "docker pull justb4/jmeter:latest >/dev/null;",
            "echo ready",
        ])
        code, output = run(client, command)
        return respond(code == 0, "node initialized" if code == 0 else output.strip(), output, code)
    except Exception as exc:
        return respond(False, str(exc))
    finally:
        if client:
            client.close()


def node_ref(node):
    return {
        "host": node["host"],
        "sshPort": node.get("sshPort", 22),
        "sshUsername": node["sshUsername"],
        "sshKeyPath": node["sshKeyPath"],
        "remoteWorkDir": node.get("remoteWorkDir", "/tmp/perftest-platform"),
    }


def start_worker(node, run_id):
    client = connect(node)
    remote_dir = Path(node.get("remoteWorkDir", "/tmp/perftest-platform")) / run_id
    container_name = f"jmeter-worker-{run_id}"
    command = " ".join([
        "docker rm -f", shell_quote(container_name), ">/dev/null 2>&1 || true;",
        "mkdir -p", shell_quote(str(remote_dir)) + ";",
        "docker run -d --name", shell_quote(container_name),
        "--network host",
        JMETER_IMAGE,
        shell_quote(f"-Djava.rmi.server.hostname={node['host']}"),
        "-s",
        "-Jserver.rmi.localport=4000",
        "-Jserver_port=1099",
        "-Jserver.rmi.ssl.disable=true",
    ])
    code, output = run(client, command)
    client.close()
    return code, output


def start_controller(controller, payload):
    client = connect(controller)
    run_id = payload["runId"]
    remote_dir = Path(controller.get("remoteWorkDir", "/tmp/perftest-platform")) / run_id
    remote_script = remote_dir / Path(payload["scriptPath"]).name
    remote_result = remote_dir / "result.jtl"
    remote_log = remote_dir / "jmeter.log"
    container_name = f"jmeter-controller-{run_id}"
    sftp_put(client, Path(payload["scriptPath"]), remote_script)
    for dependency in payload.get("dependencies", []):
        source = Path(dependency["sourcePath"])
        target = remote_dir / dependency["targetPath"]
        sftp_put(client, source, target)
    worker_hosts = ",".join([node["host"] for node in payload["workers"]])
    command = " ".join([
        "docker rm -f", shell_quote(container_name), ">/dev/null 2>&1 || true;",
        "docker run --rm --name", shell_quote(container_name),
        "--network host",
        "-v", shell_quote(f"{remote_dir}:/test"),
        JMETER_IMAGE,
        shell_quote(f"-Djava.rmi.server.hostname={controller['host']}"),
        "-n",
        "-t", shell_quote(f"/test/{remote_script.name}"),
        "-l", "/test/result.jtl",
        "-j", "/test/jmeter.log",
        "-R", shell_quote(worker_hosts),
        "-Jclient.rmi.localport=4001",
        "-Jserver.rmi.ssl.disable=true",
        "-Jmode=Standard",
        "-Jbackend_influxdb.send_interval=1",
        "-Gbackend_influxdb.send_interval=1",
        "-Jjmeter.save.saveservice.output_format=csv",
        "-Jjmeter.save.saveservice.print_field_names=true",
        "-Jjmeter.save.saveservice.url=true",
        "-Jjmeter.save.saveservice.samplerData=true",
        "-Jjmeter.save.saveservice.requestHeaders=true",
        "-Jjmeter.save.saveservice.response_data=true",
        "-Jjmeter.save.saveservice.responseHeaders=true",
        "-Jjmeter.save.saveservice.assertion_results_failure_message=true",
    ])
    code, output = run(client, command)
    try:
        sftp_get(client, remote_result, payload["resultPath"])
    except Exception:
        pass
    try:
        sftp_get(client, remote_log, payload["logPath"])
    except Exception:
        Path(payload["logPath"]).write_text(output, encoding="utf-8")
    client.close()
    return code, output


def start_run(payload):
    logs = []
    workers = [node_ref(node) for node in payload["workers"]]
    controller = node_ref(payload["controller"])
    try:
        for worker in workers:
            code, output = start_worker(worker, payload["runId"])
            logs.append(output)
            if code != 0:
                return respond(False, output.strip(), "\n".join(logs), code)
        time.sleep(5)
        code, output = start_controller(controller, payload)
        logs.append(output)
        return respond(code == 0, "distributed run finished" if code == 0 else output.strip(), "\n".join(logs), code)
    except Exception as exc:
        return respond(False, str(exc), "\n".join(logs))


def stop_run(payload):
    logs = []
    nodes = [payload["controller"], *payload.get("workers", [])]
    ok = True
    for node in nodes:
        client = None
        try:
            client = connect(node_ref(node))
            run_id = payload["runId"]
            code, output = run(client, f"docker rm -f jmeter-controller-{run_id} jmeter-worker-{run_id} >/dev/null 2>&1 || true")
            logs.append(output)
            ok = ok and code == 0
        except Exception as exc:
            logs.append(str(exc))
            ok = False
        finally:
            if client:
                client.close()
    return respond(ok, "stopped" if ok else "stop failed", "\n".join(logs))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("command", choices=["check-node", "install-key", "start-run", "stop-run"])
    parser.add_argument("payload")
    args = parser.parse_args()
    payload = json.loads(args.payload)
    if args.command == "check-node":
        return check_node(payload)
    if args.command == "install-key":
        return install_key(payload)
    if args.command == "start-run":
        return start_run(payload)
    return stop_run(payload)


if __name__ == "__main__":
    sys.exit(main())
