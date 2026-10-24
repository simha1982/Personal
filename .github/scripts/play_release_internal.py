"""Roll out the draft release on the Play internal testing track to its testers."""
import json
import os
import sys

from google.oauth2 import service_account
from googleapiclient.discovery import build

pkg = sys.argv[1]
creds = service_account.Credentials.from_service_account_info(
    json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"].lstrip("﻿")),  # Windows tools may add a BOM
    scopes=["https://www.googleapis.com/auth/androidpublisher"],
)
edits = build("androidpublisher", "v3", credentials=creds, cache_discovery=False).edits()
edit = edits.insert(packageName=pkg, body={}).execute()["id"]
track = edits.tracks().get(packageName=pkg, editId=edit, track="internal").execute()
drafts = [r for r in track.get("releases", []) if r.get("status") == "draft"]
if not drafts:
    sys.exit(f"No draft release on the internal track: {track}")
release = max(drafts, key=lambda r: max(int(v) for v in r["versionCodes"]))
release["status"] = "completed"
print("Rolling out", release.get("name"), release["versionCodes"])
edits.tracks().update(packageName=pkg, editId=edit, track="internal",
                      body={"track": "internal", "releases": [release]}).execute()
edits.commit(packageName=pkg, editId=edit).execute()
print("Internal testing release is live for its testers.")
