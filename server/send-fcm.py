#!/usr/bin/env python3
"""
Send a single FCM push to the registered watch.

Usage:  send-fcm.py "Title" "Body text"

Reads:
  - Service account JSON from $GOOGLE_APPLICATION_CREDENTIALS
    (default ~/.claude-watch/service-account.json)
  - Device token from $WATCH_TOKEN_FILE
    (default ~/.claude-watch/watch-token, written by the watch over SSH)

Requires:  pip install google-auth requests
"""
import json
import os
import pathlib
import sys

import google.auth.transport.requests
import requests
from google.oauth2 import service_account

SCOPES = ["https://www.googleapis.com/auth/firebase.messaging"]
HOME = pathlib.Path.home()
CRED = os.environ.get("GOOGLE_APPLICATION_CREDENTIALS", str(HOME / ".claude-watch" / "service-account.json"))
TOKEN_FILE = os.environ.get("WATCH_TOKEN_FILE", str(HOME / ".claude-watch" / "watch-token"))


def main() -> int:
    title = sys.argv[1] if len(sys.argv) > 1 else "Claude is done"
    body = sys.argv[2] if len(sys.argv) > 2 else "Your prompt finished."

    if not os.path.exists(CRED):
        print(f"send-fcm: missing service account at {CRED}", file=sys.stderr)
        return 1
    if not os.path.exists(TOKEN_FILE):
        print("send-fcm: no watch token registered yet", file=sys.stderr)
        return 1

    device_token = pathlib.Path(TOKEN_FILE).read_text().strip()
    if not device_token:
        print("send-fcm: empty watch token", file=sys.stderr)
        return 1

    with open(CRED) as f:
        project_id = json.load(f)["project_id"]

    creds = service_account.Credentials.from_service_account_file(CRED, scopes=SCOPES)
    creds.refresh(google.auth.transport.requests.Request())

    url = f"https://fcm.googleapis.com/v1/projects/{project_id}/messages:send"
    message = {
        "message": {
            "token": device_token,
            "notification": {"title": title, "body": body},
            "android": {"priority": "HIGH"},
            # data mirror so the app can render even if it ignores `notification`
            "data": {"title": title, "body": body},
        }
    }
    resp = requests.post(
        url,
        headers={
            "Authorization": f"Bearer {creds.token}",
            "Content-Type": "application/json",
        },
        data=json.dumps(message),
        timeout=10,
    )
    if resp.status_code >= 300:
        print(f"send-fcm: FCM error {resp.status_code}: {resp.text}", file=sys.stderr)
        return 1
    print("send-fcm: sent")
    return 0


if __name__ == "__main__":
    sys.exit(main())
