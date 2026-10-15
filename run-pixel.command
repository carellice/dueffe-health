#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
close_terminal_on_success() {
  if [[ "${TERM_PROGRAM:-}" == "Apple_Terminal" && -n "${TERM_SESSION_ID:-}" ]]; then
    osascript -e 'tell application "Terminal" to close (front window)' >/dev/null 2>&1 || true
  fi
}
SDK="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB="$SDK/platform-tools/adb"
EMULATOR="$SDK/emulator/emulator"
[[ -x "$ADB" && -x "$EMULATOR" ]] || { echo "Android SDK non trovato in $SDK" >&2; exit 1; }
AVD="${1:-}"
if [[ -z "$AVD" ]]; then
  AVD="$($EMULATOR -list-avds | grep -i '^Pixel' | head -n 1 || true)"
fi
[[ -n "$AVD" ]] || { echo "Nessun emulatore Pixel disponibile. Creane uno in Android Studio." >&2; exit 1; }
"$EMULATOR" -list-avds | grep -Fxq "$AVD" || { echo "AVD inesistente: $AVD" >&2; exit 1; }
SERIAL=""
while read -r candidate; do
  [[ "$candidate" == emulator-* ]] || continue
  name="$($ADB -s "$candidate" emu avd name 2>/dev/null | head -n 1 | tr -d '\r' || true)"
  if [[ "$name" == "$AVD" ]]; then SERIAL="$candidate"; break; fi
done < <("$ADB" devices | awk '$2 == "device" {print $1}')
if [[ -z "$SERIAL" ]]; then
  echo "Avvio $AVD..."
  nohup "$EMULATOR" -avd "$AVD" </dev/null >/tmp/dueffe-health-emulator.log 2>&1 &
  disown || true
  for _ in {1..90}; do
    sleep 2
    while read -r candidate; do
      [[ "$candidate" == emulator-* ]] || continue
      name="$($ADB -s "$candidate" emu avd name 2>/dev/null | head -n 1 | tr -d '\r' || true)"
      if [[ "$name" == "$AVD" ]]; then SERIAL="$candidate"; break; fi
    done < <("$ADB" devices | awk '$2 == "device" {print $1}')
    [[ -n "$SERIAL" ]] && break
  done
fi
[[ -n "$SERIAL" ]] || { echo "L'emulatore non si è avviato. Vedi /tmp/dueffe-health-emulator.log" >&2; exit 1; }
"$ADB" -s "$SERIAL" wait-for-device
for _ in {1..90}; do
  [[ "$($ADB -s "$SERIAL" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]] && break
  sleep 2
done
[[ "$($ADB -s "$SERIAL" shell getprop sys.boot_completed | tr -d '\r')" == "1" ]] || { echo "Timeout boot emulatore" >&2; exit 1; }
./gradlew :app:assembleDebug
"$ADB" -s "$SERIAL" install -r app/build/outputs/apk/debug/app-debug.apk
"$ADB" -s "$SERIAL" shell am start -n it.dueffe.health/.MainActivity
sleep 2
"$ADB" -s "$SERIAL" shell pidof it.dueffe.health >/dev/null || { echo "L’app si è arrestata subito dopo l’avvio" >&2; exit 1; }
echo "Health avviata su $AVD ($SERIAL)."
close_terminal_on_success
