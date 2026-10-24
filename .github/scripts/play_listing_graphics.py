"""Replace the Play store listing's icon, feature graphic and phone screenshots."""
import argparse
import json
import os

from google.oauth2 import service_account
from googleapiclient.discovery import build
from googleapiclient.errors import HttpError
from googleapiclient.http import MediaFileUpload

p = argparse.ArgumentParser()
p.add_argument("package")
p.add_argument("--icon")
p.add_argument("--feature")
p.add_argument("--screenshots", nargs="*", default=[])
a = p.parse_args()

creds = service_account.Credentials.from_service_account_info(
    json.loads(os.environ["PLAY_SERVICE_ACCOUNT_JSON"].lstrip("\ufeff")),  # Windows tools may add a BOM
    scopes=["https://www.googleapis.com/auth/androidpublisher"],
)
edits = build("androidpublisher", "v3", credentials=creds, cache_discovery=False).edits()
pkg = a.package
edit = edits.insert(packageName=pkg, body={}).execute()["id"]
lang = edits.details().get(packageName=pkg, editId=edit).execute().get("defaultLanguage", "en-US")
print("Listing language:", lang)


def replace(kind, files):
    edits.images().deleteall(packageName=pkg, editId=edit, language=lang, imageType=kind).execute()
    for f in files:
        edits.images().upload(packageName=pkg, editId=edit, language=lang, imageType=kind,
                              media_body=MediaFileUpload(f, mimetype="image/png")).execute()
        print(f"{kind}: uploaded {f}")


if a.icon:
    replace("icon", [a.icon])
if a.feature:
    replace("featureGraphic", [a.feature])
if a.screenshots:
    replace("phoneScreenshots", sorted(a.screenshots))

try:
    edits.commit(packageName=pkg, editId=edit).execute()
except HttpError as e:
    if "changesNotSentForReview" not in str(e):
        raise
    edits.commit(packageName=pkg, editId=edit, changesNotSentForReview=True).execute()
print("Committed listing graphics.")
