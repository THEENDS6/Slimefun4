from __future__ import annotations

import argparse
import os
import pathlib
import shutil
import subprocess
import sys
import tempfile
from typing import Iterable


def find_git() -> str:
    candidates = [
        os.environ.get("GIT_EXECUTABLE"),
        r"D:\Tools\apps\git\cmd\git.exe",
        shutil.which("git.exe"),
        shutil.which("git"),
    ]
    for candidate in candidates:
        if candidate and pathlib.Path(candidate).exists():
            return candidate
    return "git"


GIT = find_git()
ROOT = pathlib.Path(
    subprocess.check_output([GIT, "rev-parse", "--show-toplevel"], cwd=pathlib.Path(__file__).resolve().parent.parent)
    .decode("utf-8")
    .strip()
)

CLIKE_EXTENSIONS = {
    ".c",
    ".cc",
    ".cpp",
    ".cs",
    ".gradle",
    ".groovy",
    ".h",
    ".hpp",
    ".java",
    ".js",
    ".jsx",
    ".kt",
    ".kts",
    ".ts",
    ".tsx",
}
HASH_EXTENSIONS = {".py", ".rb", ".sh", ".bash", ".zsh"}
YAML_EXTENSIONS = {".yml", ".yaml"}
POWERSHELL_EXTENSIONS = {".ps1", ".psm1", ".psd1"}
BATCH_EXTENSIONS = {".bat", ".cmd"}
LINE_COMMENT_FILES = {".editorconfig", ".gitattributes", ".gitignore"}
SPECIAL_HASH_FILES = {"gradlew"}
TEXT_EXTENSIONS = (
    CLIKE_EXTENSIONS
    | HASH_EXTENSIONS
    | YAML_EXTENSIONS
    | POWERSHELL_EXTENSIONS
    | BATCH_EXTENSIONS
)


def run_git(arguments: list[str], input_data: bytes | None = None, env: dict[str, str] | None = None) -> bytes:
    merged_env = os.environ.copy()
    if env:
        merged_env.update(env)
    completed = subprocess.run(
        [GIT, *arguments],
        cwd=ROOT,
        input=input_data,
        stdout=subprocess.PIPE,
        stderr=subprocess.PIPE,
        env=merged_env,
    )
    if completed.returncode != 0:
        message = completed.stderr.decode("utf-8", errors="replace").strip()
        raise RuntimeError(f"git {' '.join(arguments)} failed: {message}")
    return completed.stdout


def git_text(arguments: list[str]) -> str:
    return run_git(arguments).decode("utf-8").strip()


def resolve_revision(revision: str) -> str:
    return git_text(["rev-parse", "--verify", f"{revision}^{{commit}}"])


def file_kind(path: str) -> str | None:
    name = pathlib.PurePosixPath(path).name.lower()
    suffix = pathlib.PurePosixPath(path).suffix.lower()
    if suffix in CLIKE_EXTENSIONS:
        return "clike"
    if suffix in HASH_EXTENSIONS or name in SPECIAL_HASH_FILES:
        return "hash"
    if suffix in YAML_EXTENSIONS:
        return "yaml"
    if suffix in POWERSHELL_EXTENSIONS:
        return "powershell"
    if suffix in BATCH_EXTENSIONS:
        return "batch"
    if name in LINE_COMMENT_FILES:
        return "line"
    return None


def decode_text(data: bytes) -> str | None:
    if b"\x00" in data:
        return None
    try:
        return data.decode("utf-8")
    except UnicodeDecodeError:
        return None


def scan_clike(text: str) -> tuple[str, list[tuple[int, str]]]:
    output: list[str] = []
    hits: list[tuple[int, str]] = []
    state = "normal"
    quote = ""
    line = 1
    index = 0
    pending_space = False

    def append(value: str) -> None:
        nonlocal line
        output.append(value)
        line += value.count("\n")

    def escaped(position: int) -> bool:
        backslashes = 0
        cursor = position - 1
        while cursor >= 0 and text[cursor] == "\\":
            backslashes += 1
            cursor -= 1
        return backslashes % 2 == 1

    while index < len(text):
        if state == "normal":
            if pending_space:
                if output and not output[-1][-1:].isspace() and not text[index].isspace():
                    output.append(" ")
                pending_space = False
            if text.startswith("//", index):
                hits.append((line, "line"))
                state = "line"
                index += 2
                continue
            if text.startswith("/*", index):
                hits.append((line, "block"))
                pending_space = bool(output and not output[-1][-1:].isspace())
                state = "block"
                index += 2
                continue
            if text.startswith('"""', index):
                append('"""')
                state = "triple-double"
                index += 3
                continue
            if text.startswith("'''", index):
                append("'''")
                state = "triple-single"
                index += 3
                continue
            if text[index] in ('"', "'"):
                quote = text[index]
                append(quote)
                state = "quote"
                index += 1
                continue
            append(text[index])
            index += 1
            continue

        if state == "line":
            if text[index] in "\r\n":
                append(text[index])
                state = "normal"
            index += 1
            continue

        if state == "block":
            if text.startswith("*/", index):
                state = "normal"
                index += 2
                continue
            append(text[index]) if text[index] in "\r\n" else None
            index += 1
            continue

        if state == "quote":
            character = text[index]
            append(character)
            index += 1
            if character == "\\" and index < len(text):
                append(text[index])
                index += 1
            elif character == quote:
                state = "normal"
            continue

        if state == "triple-double":
            if text.startswith('"""', index) and not escaped(index):
                append('"""')
                state = "normal"
                index += 3
            else:
                append(text[index])
                index += 1
            continue

        if state == "triple-single":
            if text.startswith("'''", index) and not escaped(index):
                append("'''")
                state = "normal"
                index += 3
            else:
                append(text[index])
                index += 1
            continue

    return "".join(output), hits


def scan_hash(text: str, yaml_mode: bool = False) -> tuple[str, list[tuple[int, str]]]:
    output: list[str] = []
    hits: list[tuple[int, str]] = []
    state = "normal"
    quote = ""
    line = 1
    index = 0

    def append(value: str) -> None:
        nonlocal line
        output.append(value)
        line += value.count("\n")

    while index < len(text):
        character = text[index]
        if state == "normal":
            if index == 0 and text.startswith("#!", index):
                append("#!")
                index += 2
                continue
            if character in ('"', "'"):
                quote = character
                state = "quote"
                append(character)
                index += 1
                continue
            if character == "#" and (not yaml_mode or index == 0 or text[index - 1].isspace()):
                hits.append((line, "line"))
                state = "line"
                index += 1
                continue
            append(character)
            index += 1
            continue
        if state == "line":
            if character in "\r\n":
                append(character)
                state = "normal"
            index += 1
            continue
        append(character)
        index += 1
        if character == "\\" and quote == '"' and index < len(text):
            append(text[index])
            index += 1
        elif character == quote:
            state = "normal"

    return "".join(output), hits


def scan_powershell(text: str) -> tuple[str, list[tuple[int, str]]]:
    output: list[str] = []
    hits: list[tuple[int, str]] = []
    state = "normal"
    quote = ""
    line = 1
    index = 0

    def append(value: str) -> None:
        nonlocal line
        output.append(value)
        line += value.count("\n")

    while index < len(text):
        character = text[index]
        if state == "normal":
            if text.startswith("<#", index):
                hits.append((line, "block"))
                state = "block"
                index += 2
                continue
            if character in ('"', "'"):
                quote = character
                state = "quote"
                append(character)
                index += 1
                continue
            if character == "#":
                hits.append((line, "line"))
                state = "line"
                index += 1
                continue
            append(character)
            index += 1
            continue
        if state == "line":
            if character in "\r\n":
                append(character)
                state = "normal"
            index += 1
            continue
        if state == "block":
            if text.startswith("#>", index):
                state = "normal"
                index += 2
                continue
            append(character) if character in "\r\n" else None
            index += 1
            continue
        append(character)
        index += 1
        if character == quote:
            if quote == "'" and index < len(text) and text[index] == "'":
                append(text[index])
                index += 1
            else:
                state = "normal"
        elif character == "`" and index < len(text):
            append(text[index])
            index += 1

    return "".join(output), hits


def scan_batch(text: str) -> tuple[str, list[tuple[int, str]]]:
    output: list[str] = []
    hits: list[tuple[int, str]] = []
    lines = text.splitlines(keepends=True)
    for line_number, line in enumerate(lines, 1):
        body = line.rstrip("\r\n")
        ending = line[len(body):]
        stripped = body.lstrip(" \t").lower()
        if (
            stripped.startswith("::")
            or stripped == "rem"
            or stripped.startswith("rem ")
            or stripped.startswith("rem\t")
            or stripped == "@rem"
            or stripped.startswith("@rem ")
            or stripped.startswith("@rem\t")
        ):
            hits.append((line_number, "line"))
            output.append(ending)
        else:
            output.append(line)
    return "".join(output), hits


def scan_line_file(text: str) -> tuple[str, list[tuple[int, str]]]:
    output: list[str] = []
    hits: list[tuple[int, str]] = []
    for line_number, line in enumerate(text.splitlines(keepends=True), 1):
        body = line.rstrip("\r\n")
        ending = line[len(body):]
        if body.lstrip(" \t").startswith("#"):
            hits.append((line_number, "line"))
            output.append(ending)
        else:
            output.append(line)
    return "".join(output), hits


def sanitize_bytes(path: str, data: bytes) -> tuple[bytes, list[tuple[int, str]]]:
    kind = file_kind(path)
    if kind is None:
        return data, []
    text = decode_text(data)
    if text is None:
        return data, []
    if kind == "clike":
        sanitized, hits = scan_clike(text)
    elif kind == "hash":
        sanitized, hits = scan_hash(text)
    elif kind == "yaml":
        sanitized, hits = scan_hash(text, yaml_mode=True)
    elif kind == "powershell":
        sanitized, hits = scan_powershell(text)
    elif kind == "batch":
        sanitized, hits = scan_batch(text)
    else:
        sanitized, hits = scan_line_file(text)
    return sanitized.encode("utf-8"), hits


def parse_tree_entries(raw: bytes) -> Iterable[tuple[str, str, str, str]]:
    for entry in raw.split(b"\0"):
        if not entry:
            continue
        metadata, path = entry.split(b"\t", 1)
        mode, object_type, object_id = metadata.split()
        yield mode.decode(), object_type.decode(), object_id.decode(), path.decode("utf-8")


def tree_entries(revision: str) -> list[tuple[str, str, str, str]]:
    return list(parse_tree_entries(run_git(["ls-tree", "-r", "-z", revision])))


def scan_tree(revision: str, limit: int = 50) -> list[str]:
    hits: list[str] = []
    for _, object_type, object_id, path in tree_entries(revision):
        if object_type != "blob":
            continue
        data = run_git(["cat-file", "blob", object_id])
        _, file_hits = sanitize_bytes(path, data)
        for line, kind in file_hits:
            hits.append(f"{path}:{line}:{kind}")
            if len(hits) >= limit:
                return hits
    return hits


def history_commits(revision: str) -> list[str]:
    output = git_text(["rev-list", "--topo-order", "--reverse", revision])
    return [line for line in output.splitlines() if line]


def scan_history(revision: str, limit: int = 50) -> list[str]:
    hits: list[str] = []
    seen: set[tuple[str, str]] = set()
    for commit in reversed(history_commits(revision)):
        for _, object_type, object_id, path in tree_entries(commit):
            if object_type != "blob":
                continue
            kind = file_kind(path)
            if kind is None or (object_id, kind) in seen:
                continue
            seen.add((object_id, kind))
            data = run_git(["cat-file", "blob", object_id])
            _, file_hits = sanitize_bytes(path, data)
            for line, comment_kind in file_hits:
                hits.append(f"{commit}:{path}:{line}:{comment_kind}")
                if len(hits) >= limit:
                    return hits
    return hits


def index_entries(environment: dict[str, str]) -> list[tuple[str, str, str, str]]:
    raw = run_git(["ls-files", "-s", "-z"], env=environment)
    entries: list[tuple[str, str, str, str]] = []
    for entry in raw.split(b"\0"):
        if not entry:
            continue
        metadata, path = entry.split(b"\t", 1)
        mode, object_id, stage = metadata.split()
        object_type = b"commit" if mode == b"160000" else b"blob"
        entries.append((mode.decode(), object_type.decode(), object_id.decode(), path.decode("utf-8")))
    return entries


def sanitized_tree(commit: str, cache: dict[tuple[str, str], str], index_path: pathlib.Path) -> str:
    environment = {"GIT_INDEX_FILE": str(index_path)}
    run_git(["read-tree", commit], env=environment)
    for mode, object_type, object_id, path in index_entries(environment):
        if object_type != "blob":
            continue
        kind = file_kind(path)
        if kind is None:
            continue
        cache_key = (object_id, kind)
        new_object_id = cache.get(cache_key)
        if new_object_id is None:
            data = run_git(["cat-file", "blob", object_id])
            sanitized, _ = sanitize_bytes(path, data)
            _, residual_hits = sanitize_bytes(path, sanitized)
            if residual_hits:
                raise RuntimeError(f"comment stripper left comments in {path}: {residual_hits[0]}")
            if sanitized == data:
                new_object_id = object_id
            else:
                new_object_id = git_text_with_input(["hash-object", "-w", "--stdin"], sanitized)
            cache[cache_key] = new_object_id
        if new_object_id != object_id:
            run_git(["update-index", "--add", "--cacheinfo", mode, new_object_id, path], env=environment)
    return git_text_with_env(["write-tree"], environment)


def git_text_with_env(arguments: list[str], environment: dict[str, str]) -> str:
    return run_git(arguments, env=environment).decode("utf-8").strip()


def commit_entries(raw: bytes) -> tuple[list[list[bytes]], bytes]:
    header, message = raw.split(b"\n\n", 1)
    entries: list[list[bytes]] = []
    current: list[bytes] = []
    for line in header.split(b"\n"):
        if line.startswith(b" ") and current:
            current.append(line)
        else:
            if current:
                entries.append(current)
            current = [line]
    if current:
        entries.append(current)
    return entries, message


def entry_name(entry: list[bytes]) -> bytes:
    return entry[0].split(b" ", 1)[0]


def rewrite_commit(original: str, mapping: dict[str, str], new_tree: str) -> str:
    entries, message = commit_entries(run_git(["cat-file", "commit", original]))
    new_header: list[bytes] = []
    for entry in entries:
        name = entry_name(entry)
        if name == b"tree":
            new_header.append(b"tree " + new_tree.encode())
        elif name == b"parent":
            old_parent = entry[0].split(b" ", 1)[1].decode()
            if old_parent not in mapping:
                raise RuntimeError(f"parent {old_parent} was not rewritten before {original}")
            new_header.append(b"parent " + mapping[old_parent].encode())
        elif name in {b"gpgsig", b"mergetag"}:
            continue
        else:
            new_header.extend(entry)
    raw = b"\n".join(new_header) + b"\n\n" + message
    return git_text_with_input(["hash-object", "-t", "commit", "-w", "--stdin"], raw)


def git_text_with_input(arguments: list[str], input_data: bytes) -> str:
    return run_git(arguments, input_data=input_data).decode("utf-8").strip()


def rewrite_history(source: str) -> tuple[str, int]:
    commits = history_commits(source)
    mapping: dict[str, str] = {}
    cache: dict[tuple[str, str], str] = {}
    with tempfile.TemporaryDirectory(prefix="sfx-sanitized-index-") as temporary:
        index_path = pathlib.Path(temporary) / "index"
        for number, original in enumerate(commits, 1):
            new_tree = sanitized_tree(original, cache, index_path)
            mapping[original] = rewrite_commit(original, mapping, new_tree)
            if number == 1 or number % 25 == 0 or number == len(commits):
                print(f"rewrote {number}/{len(commits)} commits", flush=True)
    return mapping[commits[-1]], len(commits)


def print_hits(title: str, hits: list[str]) -> None:
    if not hits:
        print(f"{title}: clean")
        return
    print(f"{title}: found {len(hits)} or more comment locations")
    for hit in hits:
        print(f"  {hit}")


def check_revision(revision: str, history: bool) -> int:
    resolved = resolve_revision(revision)
    hits = scan_history(resolved) if history else scan_tree(resolved)
    print_hits(f"check {resolved}", hits)
    return 1 if hits else 0


def expected_remote(remote: str, branch: str) -> str:
    tracking = f"refs/remotes/{remote}/{branch}"
    return git_text(["rev-parse", "--verify", tracking])


def publish(source: str, remote: str, branch: str) -> int:
    source_sha = resolve_revision(source)
    expected = expected_remote(remote, branch)
    print(f"source={source_sha}")
    print(f"remote={remote} branch={branch} expected={expected}")
    sanitized, count = rewrite_history(source_sha)
    tree_hits = scan_tree(sanitized)
    print_hits(f"sanitized tip {sanitized} ({count} commits rebuilt)", tree_hits)
    if tree_hits:
        return 1
    refspec = f"{sanitized}:refs/heads/{branch}"
    lease = f"refs/heads/{branch}:{expected}"
    run_git(["push", "--no-verify", f"--force-with-lease={lease}", remote, refspec])
    print(f"published {sanitized} to {remote}/{branch}")
    return 0


def pre_push() -> int:
    failed = False
    for raw_line in sys.stdin:
        fields = raw_line.strip().split()
        if len(fields) != 4:
            continue
        local_ref, local_sha, remote_ref, _ = fields
        if local_sha == "0" * 40:
            continue
        hits = scan_tree(local_sha)
        if hits:
            failed = True
            print(f"direct push blocked for {remote_ref}: local tree contains comments", file=sys.stderr)
            for hit in hits:
                print(f"  {hit}", file=sys.stderr)
    if failed:
        print("use scripts/publish-sanitized-history.ps1 -Publish for the sanitized public history", file=sys.stderr)
        return 1
    return 0


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true")
    parser.add_argument("--check-history", action="store_true")
    parser.add_argument("--pre-push", action="store_true")
    parser.add_argument("--prepare", action="store_true")
    parser.add_argument("--publish", action="store_true")
    parser.add_argument("--source", default="master")
    parser.add_argument("--remote", default="origin")
    parser.add_argument("--branch", default="master")
    args = parser.parse_args()
    try:
        if args.pre_push:
            return pre_push()
        if args.prepare:
            sanitized, count = rewrite_history(args.source)
            hits = scan_tree(sanitized)
            print_hits(f"prepared tip {sanitized} ({count} commits rebuilt)", hits)
            if not hits:
                print(sanitized)
            return 1 if hits else 0
        if args.publish:
            return publish(args.source, args.remote, args.branch)
        if args.check or args.check_history:
            return check_revision(args.source, args.check_history)
        parser.error("choose --check, --check-history, --pre-push, --prepare or --publish")
    except RuntimeError as error:
        print(str(error), file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
