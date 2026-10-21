"""Drives Puzzle Alarm on an emulator through adb: add an alarm, ring it with the screen off,
snooze once, ring again, solve the puzzle. Screenshots go to ./shots (uploaded as an artifact)
and the visible text of each screen is printed to the log."""

import os
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET

PKG = "com.ishtah.puzzlealarm"
APK = "PuzzleAlarm/app/build/outputs/apk/debug/app-debug.apk"
SHOTS = "shots"
results = []


def adb(*args, check=True, binary=False):
    out = subprocess.run(["adb", *args], capture_output=True, check=False)
    if check and out.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)} failed: {out.stderr.decode(errors='ignore')}")
    return out.stdout if binary else out.stdout.decode(errors="ignore")


def sh(cmd, check=True):
    return adb("shell", cmd, check=check)


def record(name, ok, detail=""):
    results.append((name, ok, detail))
    print(f"{'PASS' if ok else 'FAIL'}: {name} {detail}", flush=True)


def shot(name):
    png = adb("exec-out", "screencap", "-p", binary=True)
    os.makedirs(SHOTS, exist_ok=True)
    with open(f"{SHOTS}/{name}.png", "wb") as f:
        f.write(png)
    describe(name)


def describe(tag):
    """Log what's on screen as text: the resumed activity and every visible label."""
    top = sh("dumpsys activity activities | grep -m1 topResumedActivity", check=False).strip()
    try:
        texts = [t for t in (label(n).split("|")[0] or label(n).split("|")[1] for n in nodes()) if t]
    except RuntimeError as e:
        texts = [str(e)]
    print(f"SCREEN {tag}: {top}\n    " + " / ".join(texts)[:1500], flush=True)


def nodes():
    for _ in range(6):
        out = sh("uiautomator dump /sdcard/ui.xml", check=False)
        if "dumped" in out:
            xml = adb("exec-out", "cat", "/sdcard/ui.xml")
            try:
                return list(ET.fromstring(xml).iter("node"))
            except ET.ParseError:
                pass
        time.sleep(1)
    raise RuntimeError("uiautomator dump failed")


def label(n):
    return "|".join([n.get("text") or "", n.get("content-desc") or "", n.get("class") or ""])


def find(pattern, timeout=10):
    """First node whose text or content-desc matches the regex, waiting up to timeout seconds."""
    end = time.time() + timeout
    while True:
        for n in nodes():
            if re.search(pattern, label(n)):
                return n
        if time.time() > end:
            return None
        time.sleep(1)


def center(n):
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
    return (x1 + x2) // 2, (y1 + y2) // 2


def screen_height():
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    return int(m.group(2))


def tap(pattern, timeout=10):
    n = find(pattern, timeout)
    if n is None:
        raise RuntimeError(f"Nothing on screen matches {pattern!r}")
    x, y = center(n)
    h = screen_height()
    tries = 0
    while y > h - 150 and tries < 4:  # off the bottom: scroll it into view
        sh(f"input swipe 500 {int(h * 0.75)} 500 {int(h * 0.3)} 300")
        time.sleep(1)
        n = find(pattern, 3)
        x, y = center(n)
        tries += 1
    sh(f"input tap {x} {y}")
    time.sleep(1.5)


def type_text(text):
    sh("input text " + text.replace(" ", "%s"))


def clear_system_dialogs():
    """CI emulators sometimes show "System UI isn't responding" and similar; get them out of the way."""
    for _ in range(3):
        n = find(r"isn't responding|keeps stopping", timeout=1)
        if n is None:
            return
        print("Dismissing system dialog: " + label(n), flush=True)
        btn = find(r"^Wait\||^Close app\||^OK\|", timeout=2)
        if btn is not None:
            x, y = center(btn)
            sh(f"input tap {x} {y}")
        else:
            sh("input keyevent KEYCODE_BACK")
        time.sleep(2)


def ringing_visible():
    return find(r"I'm awake", timeout=3) is not None


def wait_for_ringing(timeout=20):
    end = time.time() + timeout
    while time.time() < end:
        if "AlarmActivity" in sh("dumpsys activity activities | grep -E 'topResumedActivity|ResumedActivity'", check=False):
            return True
        time.sleep(1)
    return False


def service_running():
    return "AlarmService" in sh(f"dumpsys activity services {PKG}", check=False)


def fire(alarm_id, snooze):
    """Deliver the alarm broadcast exactly as AlarmManager would (needs adb root)."""
    sh(f"am broadcast -n {PKG}/.alarm.AlarmReceiver --ei alarm_id {alarm_id} --ez is_snooze {str(snooze).lower()}")


def solve(question):
    q = question.replace("\n", " ")
    m = re.search(r"What is (\d+) × (\d+) \+ (\d+) − (\d+)", q)
    if m:
        a, b, c, d = map(int, m.groups())
        return str(a * b + c - d)
    m = re.search(r"You have (\d+) boxes with (\d+) apples in each\. You give away (\d+)", q)
    if m:
        b, a, e = map(int, m.groups())
        return str(a * b - e)
    m = re.search(r"comes next\?\s*([\d, ]+), \?", q)
    if m:
        t = [int(x) for x in m.group(1).split(",")]
        if all(t[i + 1] - t[i] == t[1] - t[0] for i in range(len(t) - 1)):
            return str(t[-1] + t[1] - t[0])
        if t[0] and all(t[i + 1] == t[i] * (t[1] // t[0]) for i in range(len(t) - 1)):
            return str(t[-1] * (t[1] // t[0]))
        return str(t[-1] + t[-2])
    return None  # e.g. an unscramble: ask for a different puzzle


def current_question():
    for n in nodes():
        t = n.get("text") or ""
        if re.search(r"What is|comes next|boxes with|Unscramble", t):
            return t
    return None


def main():
    adb("root", check=False)
    time.sleep(3)
    adb("wait-for-device")
    adb("install", "-r", "-g", APK)
    sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS", check=False)
    sh("settings put system screen_off_timeout 600000")
    sh("input keyevent KEYCODE_WAKEUP")
    sh("wm dismiss-keyguard", check=False)
    sh("input keyevent 82")
    time.sleep(2)
    clear_system_dialogs()

    # 1. Empty list
    sh(f"am start -W -n {PKG}/.MainActivity")
    time.sleep(5)
    clear_system_dialogs()
    record("App launches", find(r"No alarms yet") is not None)
    shot("01-empty-list")

    # 2. New alarm editor
    tap(r"Add alarm")
    record("Alarm editor opens", find(r"New alarm") is not None)
    shot("02-new-alarm")
    tap(r"^Save\|")
    time.sleep(1)
    record("Alarm saved and listed", find(r"Snooze 5 min \(once\)") is not None)
    shot("03-alarm-list")

    # 3. Settings
    tap(r"Settings")
    record("Settings opens", find(r"API key") is not None)
    # Any one of these secrets works; the app picks the provider from the key's prefix.
    shot("04-settings")
    sh("input keyevent KEYCODE_BACK")
    time.sleep(1)

    # 4. Ring alarm 1 with the screen off and locked, like a real morning.
    sh("input keyevent KEYCODE_HOME")
    sh("input keyevent KEYCODE_SLEEP")
    time.sleep(2)
    fire(1, snooze=False)
    via_fsi = wait_for_ringing(20)
    record("Alarm screen appears over the lock screen", via_fsi)
    if not via_fsi:
        sh("input keyevent KEYCODE_WAKEUP")
        sh("cmd statusbar expand-notifications", check=False)
        time.sleep(2)
        shot("05b-notification-shade")
        tap(r"Solve the puzzle")
    time.sleep(2)
    record("Alarm is ringing (service running)", service_running())
    record("Snooze offered on first ring", find(r"Snooze 5 min \(only once\)") is not None)
    shot("05-ringing-first")

    # 5. Snooze once
    tap(r"Snooze 5 min")
    time.sleep(2)
    record("Snooze stops the ringing", not service_running())
    alarms = sh("dumpsys alarm | grep -A3 com.ishtah.puzzlealarm", check=False)
    record("Snooze alarm scheduled", "AlarmReceiver" in alarms or "puzzlealarm" in alarms)

    # 6. Snoozed ring: no snooze this time
    sh("input keyevent KEYCODE_SLEEP")
    time.sleep(2)
    fire(1, snooze=True)
    if not wait_for_ringing(20):
        sh("input keyevent KEYCODE_WAKEUP")
        sh("cmd statusbar expand-notifications", check=False)
        time.sleep(2)
        tap(r"Solve the puzzle")
    time.sleep(2)
    record("No snooze after snoozing once", find(r"Snooze already used") is not None and find(r"Snooze \d+ min", 2) is None)
    shot("06-ringing-after-snooze")

    # 7. Back does nothing
    sh("input keyevent KEYCODE_BACK")
    time.sleep(1)
    record("Back button doesn't dismiss", service_running() and ringing_visible())

    # 8. Puzzle
    tap(r"I'm awake")
    time.sleep(3)
    shot("07-puzzle")
    for _ in range(6):
        q = current_question()
        if q and solve(q):
            break
        sh("input keyevent KEYCODE_BACK")  # hide the keyboard so the button is reachable
        time.sleep(1)
        tap(r"different puzzle")
        time.sleep(2)
    q = current_question()
    answer = solve(q) if q else None
    record("Offline puzzle shown", q is not None, repr(q))

    tap(r"EditText")
    type_text("999999")
    sh("input keyevent KEYCODE_ENTER")
    time.sleep(1.5)
    record("Wrong answer rejected", service_running() and find(r"Not quite") is not None)
    shot("08-wrong-answer")

    if answer:
        tap(r"EditText")
        type_text(answer)
        sh("input keyevent KEYCODE_ENTER")
        time.sleep(3)
        record("Correct answer turns the alarm off", not service_running())
        shot("09-after-dismiss")

    # 9. AI puzzles, when a key secret is configured (done last: the script can't solve AI puzzles).
    key = next((os.environ[k] for k in ("ANTHROPIC_API_KEY", "OPENROUTER_API_KEY", "OPENAI_API_KEY") if os.environ.get(k)), "")
    if key:
        sh(f"am start -W -n {PKG}/.MainActivity")
        time.sleep(2)
        tap(r"Settings")
        tap(r"EditText")  # the API key field is the first text field
        type_text(key)
        sh("input keyevent KEYCODE_BACK")  # hide keyboard
        tap(r"Try a puzzle")
        record("AI generates a puzzle in Settings", find(r"^AI puzzle\|", timeout=40) is not None)
        shot("10-settings-ai-puzzle")
        tap(r"Ring a test alarm now")
        time.sleep(3)
        tap(r"I'm awake")
        record("Ringing alarm shows an AI puzzle", find(r"^AI puzzle\|", timeout=40) is not None)
        shot("11-ringing-ai-puzzle")
    else:
        print("No API key secret set: skipping the AI puzzle checks", flush=True)

    crashes = adb("logcat", "-d", "-b", "crash", check=False)
    record("No crashes", PKG not in crashes, crashes[-2000:] if PKG in crashes else "")

    print("\n==== SUMMARY ====")
    for name, ok, detail in results:
        print(f"{'PASS' if ok else 'FAIL'}  {name}")
    return 0 if all(ok for _, ok, _ in results) else 1


if __name__ == "__main__":
    try:
        code = main()
    except Exception as e:  # keep a final screenshot for debugging
        print(f"ERROR: {e}", flush=True)
        try:
            shot("99-error")
        except Exception:
            pass
        log = adb("logcat", "-d", check=False).splitlines()
        print("\n".join(l for l in log if "puzzlealarm" in l or "AndroidRuntime" in l)[-5000:])
        code = 1
    sys.exit(code)
