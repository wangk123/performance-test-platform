import argparse
import base64
import json
import sys
import time
from pathlib import Path

import paramiko


JMETER_IMAGE = "justb4/jmeter:latest"


def respond(ok, message="", log="", exit_code=0, start_results=None, tail_data=None, new_offset=None, eof=None, snapshot_data=None, snapshot_mtime=None, snapshots=None):
    body = {
        "ok": ok,
        "exitCode": exit_code,
        "message": message,
        "log": log,
    }
    if start_results is not None:
        body["startResults"] = start_results
    if tail_data is not None:
        body["tailData"] = tail_data
    if new_offset is not None:
        body["newOffset"] = new_offset
    if eof is not None:
        body["eof"] = eof
    if snapshot_data is not None:
        body["snapshotData"] = snapshot_data
    if snapshot_mtime is not None:
        body["snapshotMtime"] = snapshot_mtime
    if snapshots is not None:
        body["snapshots"] = snapshots
    print(json.dumps(body, ensure_ascii=False))
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


def sftp_get(client, remote_path, local_path, append=False):
    Path(local_path).parent.mkdir(parents=True, exist_ok=True)
    with client.open_sftp() as sftp:
        if append:
            with sftp.open(str(remote_path), "rb") as remote_file:
                data = remote_file.read()
            if data:
                with open(local_path, "ab") as local_file:
                    local_file.write(data)
        else:
            sftp.get(str(remote_path), str(local_path))


def shell_quote(value):
    return "'" + value.replace("'", "'\"'\"'") + "'"


def start_exporter(client, directory, port, extra_arg=None):
    args = [str(port)]
    if extra_arg is not None:
        args.append(extra_arg)
    args_text = " ".join(shell_quote(arg) for arg in args)
    command = " ".join([
        f"cd {shell_quote(directory)} &&",
        f"./start.sh {args_text}",
    ])
    return run(client, command)


def deploy_monitoring(payload):
    client = None
    try:
        plugin_dir = payload["pluginDir"].rstrip("/")
        remote_root = f"{plugin_dir}/prometheus"
        client = connect(payload)
        run(client, f"mkdir -p {shell_quote(remote_root + '/file_sd')}")
        uploaded = []
        for item in payload.get("files", []):
            local_path = item["localPath"]
            relative_path = item["relativePath"]
            remote_path = Path(remote_root) / relative_path
            sftp_put(client, local_path, remote_path)
            uploaded.append(str(remote_path))
        shell_files = [path for path in uploaded if path.endswith(".sh")]
        if shell_files:
            quoted = " ".join(shell_quote(path) for path in shell_files)
            run(client, f"chmod +x {quoted}")
        binary_names = (
            "node_exporter",
            "mysqld_exporter",
            "redis_exporter",
            "nginx-prometheus-exporter",
            "kafka_exporter",
            "jmx_prometheus_javaagent.jar",
        )
        binaries = [path for path in uploaded if path.rsplit("/", 1)[-1] in binary_names]
        if binaries:
            quoted = " ".join(shell_quote(path) for path in binaries)
            run(client, f"chmod +x {quoted}")
        start_results = []
        node_port = payload.get("nodeExporterPort", 9100)
        code, output = start_exporter(client, f"{remote_root}/node-exporter", node_port)
        start_results.append({
            "title": "Node Exporter",
            "success": code == 0,
            "output": output.strip() or ("started on :" + str(node_port) if code == 0 else "start failed"),
        })
        for item in payload.get("items", []):
            item_type = item.get("type")
            name = item.get("name") or item_type
            port = item.get("port")
            if item_type == "JAVA_JMX_AGENT":
                continue
            if item_type == "MYSQL_EXPORTER":
                code, output = start_exporter(
                    client,
                    f"{remote_root}/mysql-exporter",
                    port,
                    "user:pass@(127.0.0.1:3306)/",
                )
            elif item_type == "REDIS_EXPORTER":
                code, output = start_exporter(
                    client,
                    f"{remote_root}/redis-exporter",
                    port,
                    "redis://127.0.0.1:6379",
                )
            elif item_type == "NGINX_EXPORTER":
                code, output = start_exporter(
                    client,
                    f"{remote_root}/nginx-exporter",
                    port,
                    "http://127.0.0.1/stub_status",
                )
            elif item_type == "KAFKA_EXPORTER":
                code, output = start_exporter(
                    client,
                    f"{remote_root}/kafka-exporter",
                    port,
                    "127.0.0.1:9092",
                )
            else:
                continue
            start_results.append({
                "title": name,
                "success": code == 0,
                "output": output.strip() or ("started on :" + str(port) if code == 0 else "start failed"),
            })
        return respond(True, "uploaded", "\n".join(uploaded), start_results=start_results)
    except Exception as exc:
        return respond(False, str(exc))
    finally:
        if client:
            client.close()


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


def controller_container_name(run_id):
    return f"jmeter-controller-{run_id}"


def controller_status(client, run_id):
    container_name = controller_container_name(run_id)
    command = " ".join([
        "docker inspect -f",
        shell_quote("{{.State.Running}} {{.State.ExitCode}}"),
        shell_quote(container_name),
        "2>/dev/null || echo missing",
    ])
    _, output = run(client, command)
    parts = output.strip().split()
    if not parts or parts[0] == "missing":
        return "missing", -1
    running = parts[0].lower() == "true"
    exit_code = int(parts[1]) if len(parts) > 1 else -1
    if running:
        return "running", 0
    return "finished", exit_code


def start_worker(node, run_id, hdr_source=None):
    client = connect(node)
    remote_dir = Path(node.get("remoteWorkDir", "/tmp/perftest-platform")) / run_id
    container_name = f"jmeter-worker-{run_id}"
    role_host = f"worker-{node['host']}"
    code, output = run(client, " ".join(["mkdir -p", shell_quote(str(remote_dir))]))
    if code != 0:
        client.close()
        return code, output
    if hdr_source:
        sftp_put(client, Path(hdr_source), remote_dir / Path(hdr_source).name)
    jmeter_args = " ".join([
        shell_quote(f"-Djava.rmi.server.hostname={node['host']}"),
        "-s",
        "-Jserver.rmi.localport=4000",
        "-Jserver_port=1099",
        "-Jserver.rmi.ssl.disable=true",
        shell_quote(f"-JjmeterRoleHost={role_host}"),
    ])
    shell_cmd = " ".join([
        "JMETER_HOME=${JMETER_HOME:-$(find /opt -maxdepth 1 -name 'apache-jmeter-*' -type d 2>/dev/null | head -1)}",
        "&& (ls /test/HdrHistogram*.jar >/dev/null 2>&1 && cp /test/HdrHistogram*.jar \"$JMETER_HOME/lib/ext/\" || true)",
        f"&& exec /entrypoint.sh {jmeter_args}",
    ])
    command = " ".join([
        "docker ps -a --filter name=jmeter-worker- -q | xargs -r docker rm -f >/dev/null 2>&1 || true;",
        "docker run -d --name", shell_quote(container_name),
        "--network host",
        "-v", shell_quote(f"{remote_dir}:/test"),
        "--entrypoint", shell_quote("/bin/sh"),
        JMETER_IMAGE,
        "-c", shell_quote(shell_cmd),
    ])
    code, output = run(client, command)
    client.close()
    return code, output


def launch_controller(controller, payload):
    client = connect(controller)
    run_id = payload["runId"]
    remote_dir = Path(controller.get("remoteWorkDir", "/tmp/perftest-platform")) / run_id
    remote_script = remote_dir / Path(payload["scriptPath"]).name
    container_name = controller_container_name(run_id)
    sftp_put(client, Path(payload["scriptPath"]), remote_script)
    for dependency in payload.get("dependencies", []):
        source = Path(dependency["sourcePath"])
        target = remote_dir / dependency["targetPath"]
        sftp_put(client, source, target)
    worker_hosts = ",".join([node["host"] for node in payload["workers"]])
    per_label_limit = payload.get("perLabelLimit", 50)
    global_limit = payload.get("globalLimit", 1000)
    role_host = f"controller-{controller['host']}"
    jmeter_args = " ".join([
        shell_quote(f"-Djava.rmi.server.hostname={controller['host']}"),
        "-n",
        "-t", shell_quote(f"/test/{remote_script.name}"),
        "-l", "/test/discard.jtl",
        "-j", "/test/jmeter.log",
        "-R", shell_quote(worker_hosts),
        "-Jclient.rmi.localport=4001",
        "-Jserver.rmi.ssl.disable=true",
        "-Jmode=Standard",
        "-GfailureSamplesPath=/test/failure-samples.jsonl",
        f"-GfailureSamplePerLabelLimit={per_label_limit}",
        f"-GfailureSampleGlobalLimit={global_limit}",
        "-JfailureSamplesPath=/test/failure-samples.jsonl",
        f"-JfailureSamplePerLabelLimit={per_label_limit}",
        f"-JfailureSampleGlobalLimit={global_limit}",
        "-JaggregateSnapshotPath=/test/aggregate-snapshot.bin",
        "-GaggregateSnapshotPath=/test/aggregate-snapshot.bin",
        shell_quote(f"-JjmeterRoleHost={role_host}"),
    ])
    shell_cmd = " ".join([
        "JMETER_HOME=${JMETER_HOME:-$(find /opt -maxdepth 1 -name 'apache-jmeter-*' -type d 2>/dev/null | head -1)}",
        "&& cp /test/HdrHistogram*.jar \"$JMETER_HOME/lib/ext/\"",
        f"&& exec /entrypoint.sh {jmeter_args}",
    ])
    command = " ".join([
        "docker rm -f", shell_quote(container_name), ">/dev/null 2>&1 || true;",
        "docker run -d --name", shell_quote(container_name),
        "--network host",
        "-v", shell_quote(f"{remote_dir}:/test"),
        "--entrypoint", shell_quote("/bin/sh"),
        JMETER_IMAGE,
        "-c", shell_quote(shell_cmd),
    ])
    code, output = run(client, command)
    client.close()
    return code, output


def collect_artifacts(controller, payload):
    client = connect(controller)
    run_id = payload["runId"]
    remote_log = Path(controller.get("remoteWorkDir", "/tmp/perftest-platform")) / run_id / "jmeter.log"
    logs = []
    try:
        sftp_get(client, remote_log, payload["logPath"])
    except Exception as exc:
        logs.append(str(exc))
    local_samples = Path(payload["failureSamplesPath"])
    local_samples.parent.mkdir(parents=True, exist_ok=True)
    local_samples.write_bytes(b"")
    sources = [controller] + [node_ref(node) for node in payload.get("workers", [])]
    for source in sources:
        remote_samples = Path(source.get("remoteWorkDir", "/tmp/perftest-platform")) / run_id / "failure-samples.jsonl"
        source_client = connect(source)
        try:
            sftp_get(source_client, remote_samples, local_samples, append=True)
        except FileNotFoundError:
            pass
        except Exception as exc:
            logs.append(f"{source['host']} failure-samples: {exc}")
        finally:
            source_client.close()
    if logs and not Path(payload["logPath"]).exists():
        Path(payload["logPath"]).write_text("\n".join(logs), encoding="utf-8")
    client.close()
    return "\n".join(logs)


def tail_failure_samples(payload):
    client = None
    try:
        node = node_ref(payload.get("tailNode", payload["controller"]))
        client = connect(node)
        run_id = payload["runId"]
        remote_dir = Path(node.get("remoteWorkDir", "/tmp/perftest-platform")) / run_id
        remote_path = remote_dir / "failure-samples.jsonl"
        offset = int(payload.get("offset", 0))
        chunk_size = int(payload.get("chunkSize", 65536))
        with client.open_sftp() as sftp:
            try:
                file_size = sftp.stat(str(remote_path)).st_size
            except FileNotFoundError:
                return respond(True, "missing", "", 0, tail_data="", new_offset=offset, eof=True)
            if offset >= file_size:
                return respond(True, "noop", "", 0, tail_data="", new_offset=offset, eof=False)
            with sftp.open(str(remote_path), "rb") as remote_file:
                remote_file.seek(offset)
                data = remote_file.read(chunk_size)
                new_offset = offset + len(data)
                eof = new_offset >= file_size
                encoded = base64.b64encode(data).decode("ascii") if data else ""
                return respond(True, "tailed", "", 0, tail_data=encoded, new_offset=new_offset, eof=eof)
    except Exception as exc:
        return respond(False, str(exc))
    finally:
        if client:
            client.close()


def fetch_aggregate_snapshot(payload):
    last_mtime = int(payload.get("lastMtime", 0))
    sources = [node_ref(payload["controller"])] + [node_ref(node) for node in payload.get("workers", [])]
    run_id = payload["runId"]
    snapshots = []
    overall_mtime = last_mtime
    for source in sources:
        client = None
        try:
            client = connect(source)
            remote_path = Path(source.get("remoteWorkDir", "/tmp/perftest-platform")) / run_id / "aggregate-snapshot.bin"
            with client.open_sftp() as sftp:
                try:
                    stat = sftp.stat(str(remote_path))
                except FileNotFoundError:
                    continue
                mtime = int(stat.st_mtime * 1000)
                if mtime > overall_mtime:
                    overall_mtime = mtime
                with sftp.open(str(remote_path), "rb") as remote_file:
                    data = remote_file.read()
                if not data:
                    continue
                snapshots.append({
                    "host": source["host"],
                    "mtime": mtime,
                    "data": base64.b64encode(data).decode("ascii"),
                })
        except Exception:
            continue
        finally:
            if client:
                client.close()
    if not snapshots:
        return respond(True, "missing", "", 0, snapshots=[], snapshot_mtime=last_mtime)
    if overall_mtime <= last_mtime:
        return respond(True, "noop", "", 0, snapshots=[], snapshot_mtime=overall_mtime)
    return respond(True, "changed", "", 0, snapshots=snapshots, snapshot_mtime=overall_mtime)


def start_run(payload):
    logs = []
    workers = [node_ref(node) for node in payload["workers"]]
    controller = node_ref(payload["controller"])
    hdr_jar = None
    for dependency in payload.get("dependencies", []):
        target = str(dependency.get("targetPath", ""))
        if target.startswith("HdrHistogram") and target.endswith(".jar"):
            hdr_jar = dependency.get("sourcePath")
            break
    try:
        for worker in workers:
            code, output = start_worker(worker, payload["runId"], hdr_source=hdr_jar)
            logs.append(f"worker {worker['host']}: {output.strip()}")
            if code != 0:
                return respond(False, output.strip() or f"worker {worker['host']} failed to start", "\n".join(logs), code)
        time.sleep(5)
        code, output = launch_controller(controller, payload)
        logs.append(f"controller {controller['host']}: {output.strip()}")
        if code != 0:
            return respond(False, output.strip() or f"controller {controller['host']} failed to start", "\n".join(logs), code)
        return respond(True, "launched", "\n".join(logs), 0)
    except Exception as exc:
        return respond(False, str(exc), "\n".join(logs))


def poll_run(payload):
    client = None
    try:
        controller = node_ref(payload["controller"])
        client = connect(controller)
        status, exit_code = controller_status(client, payload["runId"])
        if status == "running":
            return respond(True, "running", "", 0)
        if status == "missing":
            return respond(True, "finished", "controller container not found", -1)
        log = "" if exit_code == 0 else f"JMeter controller exited with code {exit_code}"
        return respond(True, "finished", log, exit_code)
    except Exception as exc:
        return respond(False, str(exc))
    finally:
        if client:
            client.close()


def collect_run(payload):
    try:
        controller = node_ref(payload["controller"])
        log = collect_artifacts(controller, payload)
        return respond(True, "collected", log, 0)
    except Exception as exc:
        return respond(False, str(exc))


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
    parser.add_argument(
        "command",
        choices=[
            "check-node",
            "install-key",
            "deploy-monitoring",
            "start-run",
            "poll-run",
            "collect-run",
            "tail-failure-samples",
            "fetch-aggregate-snapshot",
            "stop-run",
        ],
    )
    parser.add_argument("payload")
    args = parser.parse_args()
    payload = json.loads(args.payload)
    if args.command == "check-node":
        return check_node(payload)
    if args.command == "install-key":
        return install_key(payload)
    if args.command == "deploy-monitoring":
        return deploy_monitoring(payload)
    if args.command == "start-run":
        return start_run(payload)
    if args.command == "poll-run":
        return poll_run(payload)
    if args.command == "collect-run":
        return collect_run(payload)
    if args.command == "tail-failure-samples":
        return tail_failure_samples(payload)
    if args.command == "fetch-aggregate-snapshot":
        return fetch_aggregate_snapshot(payload)
    return stop_run(payload)


if __name__ == "__main__":
    sys.exit(main())
