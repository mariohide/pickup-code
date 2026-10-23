#!/usr/bin/env python3
"""推送前的隐私闸门：扫描这次要推送的提交，发现个人信息就拦下推送。

由 .githooks/pre-push 调用，参数与标准输入同 git 的 pre-push 钩子。扫描：
- 提交的作者、提交者邮箱：只允许 GitHub 的 noreply 地址；
- 提交信息与新增的每一行：本机路径、邮箱、手机号、身份证号、私钥、写死的密码；
- 改动的文件名：签名配置、本机环境文件等不该进仓库的文件；
- 以上内容再对照本机的 .privacy-patterns（不提交，每行一个正则，不区分大小写），
  放邮箱、姓名、设备序列号、主机名等具体标识，免得把它们本身写进仓库。

确认是误报时，可以 `git push --no-verify` 跳过，但要先人工看过命中的内容。
"""

import re
import subprocess
import sys
from pathlib import Path

ZERO_SHA = "0" * 40

ALLOWED_EMAIL = re.compile(
    r"@users\.noreply\.github\.com$|^noreply@anthropic\.com$|@example\.(com|org)$",
    re.IGNORECASE,
)

EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")

# Kotlin 的带标签写法（this@drawWithContent.drawContent、return@forEach 等）形似邮箱，不算
KOTLIN_LABEL = re.compile(r"^(this|super|return|break|continue)@")

RULES = [
    ("本机路径", re.compile(r"/Users/[^/\s\"']+/|/home/[^/\s\"']+/|[A-Za-z]:\\\\?Users\\\\?[^\\\s]+")),
    ("手机号", re.compile(r"(?<![\d])1[3-9]\d{9}(?![\d])")),
    ("身份证号", re.compile(r"(?<![\d])\d{17}[\dXx](?![\dA-Za-z])")),
    ("私钥", re.compile(r"-----BEGIN [A-Z ]*PRIVATE KEY-----")),
    ("写死的密码", re.compile(r"(?i)(password|passwd|secret|token)\s*[=:]\s*[\"'][^\"']+[\"']")),
]

FORBIDDEN_FILES = re.compile(
    r"(^|/)(CLAUDE\.local\.md|keystore\.properties|local\.properties|\.privacy-patterns)$"
    r"|\.(jks|keystore)$"
)


def git(*args: str) -> str:
    return subprocess.run(["git", *args], check=True, capture_output=True, text=True).stdout


def local_patterns() -> list[re.Pattern]:
    path = Path(git("rev-parse", "--show-toplevel").strip()) / ".privacy-patterns"
    if not path.exists():
        return []
    lines = path.read_text(encoding="utf-8").splitlines()
    return [re.compile(line, re.IGNORECASE) for line in lines if line.strip() and not line.startswith("#")]


def commits_to_push(remote: str) -> list[str]:
    commits: list[str] = []
    for line in sys.stdin:
        parts = line.split()
        if len(parts) != 4:
            continue
        _, local_sha, _, _ = parts
        if local_sha == ZERO_SHA:  # 删除远端分支，没有新内容
            continue
        # 远端还没有的提交，按从旧到新
        out = git("rev-list", "--reverse", local_sha, "--not", f"--remotes={remote}")
        commits += [c for c in out.split() if c not in commits]
    return commits


def scan_text(text: str, patterns: list[re.Pattern]) -> list[str]:
    hits = []
    for name, rule in RULES:
        hits += [f"{name}：{m.group(0)}" for m in rule.finditer(text)]
    hits += [
        f"邮箱：{m.group(0)}"
        for m in EMAIL.finditer(text)
        if not ALLOWED_EMAIL.search(m.group(0)) and not KOTLIN_LABEL.match(m.group(0))
    ]
    hits += [f"本机标识：{m.group(0)}" for p in patterns for m in p.finditer(text)]
    return hits


def scan_commit(commit: str, patterns: list[re.Pattern]) -> list[str]:
    problems = []
    short = commit[:7]

    for label, fmt in (("作者", "%an%n%ae"), ("提交者", "%cn%n%ce")):
        name, email = git("show", "-s", f"--format={fmt}", commit).strip().split("\n")
        if not ALLOWED_EMAIL.search(email):
            problems.append(f"{short} {label}邮箱不是 noreply 地址：{email}")
        problems += [f"{short} {label}名字 {hit}" for hit in scan_text(name, patterns)]

    message = git("show", "-s", "--format=%B", commit)
    problems += [f"{short} 提交信息 {hit}" for hit in scan_text(message, patterns)]

    files = git("show", "--format=", "--name-only", commit).split("\n")
    for file in filter(None, files):
        if FORBIDDEN_FILES.search(file):
            problems.append(f"{short} 不该提交的文件：{file}")
        problems += [f"{short} 文件名 {file} {hit}" for hit in scan_text(file, patterns)]

    # 只看新增的行；二进制文件 git 不输出内容，另行提示
    diff = git("show", "--format=", "-U0", "--no-color", "--no-ext-diff", commit)
    current = ""
    for line in diff.split("\n"):
        if line.startswith("+++ "):
            current = line[6:] if line.startswith("+++ b/") else line[4:]
        elif line.startswith("Binary files"):
            print(f"隐私闸门：{short} 含二进制文件，未扫描内容：{line}", file=sys.stderr)
        elif line.startswith("+") and not line.startswith("+++"):
            # 闸门脚本自身包含规则正则表达式（如路径正则），跳过内置规则扫描，但仍检查本机标识
            if current == "scripts/privacy_gate.py":
                problems += [
                    f"{short} {current}：本机标识：{m.group(0)}"
                    for p in patterns
                    for m in p.finditer(line[1:])
                ]
            else:
                problems += [f"{short} {current}：{hit}" for hit in scan_text(line[1:], patterns)]
    return problems


def main() -> int:
    remote = sys.argv[1] if len(sys.argv) > 1 else "origin"
    patterns = local_patterns()
    if not patterns:
        print("隐私闸门：没有找到 .privacy-patterns，只按通用规则扫描", file=sys.stderr)
    commits = commits_to_push(remote)
    problems = [p for c in commits for p in scan_commit(c, patterns)]
    if problems:
        print(f"隐私闸门：拦下推送，{len(commits)} 个提交中发现 {len(problems)} 处疑似个人信息：", file=sys.stderr)
        for p in problems:
            print(f"  - {p}", file=sys.stderr)
        print("改掉后重新提交；确认是误报，人工看过后可用 git push --no-verify 跳过。", file=sys.stderr)
        return 1
    print(f"隐私闸门：已扫描 {len(commits)} 个提交，未发现个人信息", file=sys.stderr)
    return 0


if __name__ == "__main__":
    sys.exit(main())
