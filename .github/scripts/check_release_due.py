#!/usr/bin/env python3
"""
Check if a release should be triggered:
Condition 1: Triggered by a tag.
Condition 2: Not a tag, but > 24 hours since the previous release.
Outputs 'should_release=true/false' to GITHUB_OUTPUT.
"""
import datetime
import json
import os
import subprocess
import sys

def main():
    ref_type = os.getenv("GITHUB_REF_TYPE", "")
    ref = os.getenv("GITHUB_REF", "")
    event_name = os.getenv("GITHUB_EVENT_NAME", "")
    repo = os.getenv("GITHUB_REPOSITORY", "")
    output_file = os.getenv("GITHUB_OUTPUT")

    # 1. 打了 tag
    if ref_type == "tag" or ref.startswith("refs/tags/"):
        tag_name = os.getenv("GITHUB_REF_NAME", ref)
        print(f"Condition met: triggered by tag '{tag_name}'.")
        set_output(output_file, "should_release", "true")
        return

    # 手动触发 workflow_dispatch
    if event_name == "workflow_dispatch":
        print("Condition met: triggered manually via workflow_dispatch.")
        set_output(output_file, "should_release", "true")
        return

    # 2. 没打 tag，检查距离上次 release 是否超过 24 小时
    try:
        cmd = ["gh", "release", "list", "--limit", "1", "--json", "tagName,publishedAt,createdAt"]
        if repo:
            cmd.extend(["--repo", repo])
        res = subprocess.check_output(cmd, text=True).strip()
        releases = json.loads(res) if res else []
    except Exception as e:
        print(f"Warning: Failed to fetch releases via gh cli ({e}). Proceeding with release.")
        set_output(output_file, "should_release", "true")
        return

    if not releases:
        print("No previous releases found. Release will proceed.")
        set_output(output_file, "should_release", "true")
        return

    latest_release = releases[0]
    tag_name = latest_release.get("tagName", "unknown")
    pub_time_str = latest_release.get("publishedAt") or latest_release.get("createdAt")
    if not pub_time_str:
        print("Previous release has no timestamp. Release will proceed.")
        set_output(output_file, "should_release", "true")
        return

    last_time = datetime.datetime.fromisoformat(pub_time_str.replace("Z", "+00:00"))
    now = datetime.datetime.now(datetime.timezone.utc)
    diff_hours = (now - last_time).total_seconds() / 3600.0

    print(f"Previous release '{tag_name}' published at: {pub_time_str}")
    print(f"Hours elapsed since previous release: {diff_hours:.2f}h")

    if diff_hours >= 24.0:
        print(f"Condition met: {diff_hours:.2f}h >= 24h since previous release. Release will proceed.")
        set_output(output_file, "should_release", "true")
    else:
        print(f"Condition not met: only {diff_hours:.2f}h (< 24h) since previous release and no tag was pushed. Skipping release.")
        set_output(output_file, "should_release", "false")

def set_output(output_file, key, value):
    if output_file:
        with open(output_file, "a", encoding="utf-8") as f:
            f.write(f"{key}={value}\n")
    print(f"[GITHUB_OUTPUT] {key}={value}")

if __name__ == "__main__":
    main()
