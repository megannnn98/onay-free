#!/usr/bin/env python3
"""Rebase *install identity* of an apktool-decoded, already-merged APK.

Keep the original Java/Dex package and fully qualify relative manifest classes.
Do not rename arbitrary kz.onay strings in resources or bytecode: those need an
explicit runtime audit, not a global replacement.
"""
from __future__ import annotations

import argparse
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "http://schemas.android.com/apk/res/android"
A = lambda name: "{" + ANDROID + "}" + name
OLD = "kz.onay"
NEW = "kz.onay.free"
CLASS_TAGS = {"activity", "activity-alias", "application", "provider", "receiver", "service", "instrumentation"}
APP_CLASSES = {"name", "backupAgent", "appComponentFactory", "manageSpaceActivity", "zygotePreloadName"}
COMP_CLASSES = {"name", "targetActivity"}
IDENTITY_ATTRS = {"taskAffinity", "process"}
PERMISSION_ATTRS = {"permission", "readPermission", "writePermission"}
PERMISSION_TAGS = {"permission", "permission-group", "permission-tree"}


def package_prefixed(value: str, old: str, new: str) -> str:
    if value == old or value.startswith(old + "."):
        return new + value[len(old):]
    return value


def normalize_class(value: str, old: str) -> str:
    """An APK's relative names resolve against the *old* manifest package."""
    if value.startswith("."):
        return old + value
    if "." not in value:
        return old + "." + value
    return value


def change_manifest(path: Path, old: str = OLD, new: str = NEW) -> dict[str, int]:
    if old == new or not re.fullmatch(r"[A-Za-z_]\w*(?:\.[A-Za-z_]\w*)+", new):
        raise ValueError("unsafe or invalid destination package")
    ET.register_namespace("android", ANDROID)
    tree = ET.parse(path)
    root = tree.getroot()
    if root.tag != "manifest":
        raise ValueError("not an Android manifest")
    if root.get("package") != old:
        raise ValueError(f"expected source package {old!r}, got {root.get('package')!r}")
    if root.get(A("sharedUserId")):
        raise ValueError("sharedUserId present: cannot safely clone app identity")
    application = root.find("application")
    if application is None:
        raise ValueError("missing <application>")

    declared = {}
    for el in root:
        if el.tag in PERMISSION_TAGS:
            p = el.get(A("name"))
            if p and (p == old or p.startswith(old + ".")):
                declared[p] = package_prefixed(p, old, new)

    stats = {"classes_preserved": 0, "authorities_rebased": 0, "permissions_rebased": 0}
    root.set("package", new)
    application.set(A("label"), "ONAY Free")

    authorities_seen = {}
    for el in root.iter():
        tag = el.tag.rsplit("}", 1)[-1]
        if tag in CLASS_TAGS:
            attrs = APP_CLASSES if tag == "application" else COMP_CLASSES
            if tag == "instrumentation":
                attrs = {"name"}
            for attr in attrs:
                v = el.get(A(attr))
                if v:
                    explicit = normalize_class(v, old)
                    if explicit != v:
                        el.set(A(attr), explicit)
                        stats["classes_preserved"] += 1
        if tag == "instrumentation":
            if el.get(A("targetPackage")) == old:
                el.set(A("targetPackage"), new)
        if tag in CLASS_TAGS:
            for attr in IDENTITY_ATTRS:
                v = el.get(A(attr))
                if v and not v.startswith(":"):
                    el.set(A(attr), package_prefixed(v, old, new))

        authorities = el.get(A("authorities"))
        if tag == "provider":
            if not authorities:
                raise ValueError("provider without authorities")
            rewritten = []
            for authority in authorities.split(";"):
                authority = authority.strip()
                if not authority:
                    raise ValueError("empty provider authority")
                updated = package_prefixed(authority, old, new)
                stats["authorities_rebased"] += updated != authority
                # Existing ONAY manifest can repeat one authority on multiple
                # providers. Preserve such pre-existing duplicates. Reject only
                # *new* collisions introduced by rebasing two different names.
                if updated in authorities_seen and authorities_seen[updated] != authority:
                    raise ValueError("new provider authority collision: " + updated)
                authorities_seen[updated] = authority
                rewritten.append(updated)
            el.set(A("authorities"), ";".join(rewritten))

        for attr in PERMISSION_ATTRS:
            value = el.get(A(attr))
            if value in declared:
                el.set(A(attr), declared[value])
                stats["permissions_rebased"] += 1
        if tag in PERMISSION_TAGS:
            value = el.get(A("name"))
            if value in declared:
                el.set(A("name"), declared[value])
                stats["permissions_rebased"] += 1
        elif tag in {"uses-permission", "uses-permission-sdk-23", "uses-permission-sdk-m"}:
            value = el.get(A("name"))
            if value in declared:
                el.set(A("name"), declared[value])
                stats["permissions_rebased"] += 1

    # The launcher can override <application android:label>. Keep its label distinct.
    for activity in application:
        if activity.tag not in {"activity", "activity-alias"}:
            continue
        for filter_ in activity.findall("intent-filter"):
            actions = {a.get(A("name")) for a in filter_.findall("action")}
            cats = {c.get(A("name")) for c in filter_.findall("category")}
            if "android.intent.action.MAIN" in actions and "android.intent.category.LAUNCHER" in cats:
                activity.set(A("label"), "ONAY Free")

    tree.write(path, encoding="utf-8", xml_declaration=True)
    return stats


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--to-package", default=NEW)
    args = parser.parse_args()
    try:
        stats = change_manifest(args.manifest, new=args.to_package)
    except (ValueError, ET.ParseError, OSError) as exc:
        print(f"package clone rejected: {exc}", file=sys.stderr)
        return 1
    print(f"manifest install identity: {OLD} -> {args.to_package}; changes: {stats}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
