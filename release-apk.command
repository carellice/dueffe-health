#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
close_terminal_on_success() {
  if [[ "${TERM_PROGRAM:-}" == "Apple_Terminal" && -n "${TERM_SESSION_ID:-}" ]]; then
    osascript -e 'tell application "Terminal" to close (front window)' >/dev/null 2>&1 || true
  fi
}
REPO="${RELEASE_REPO:-carellice/dueffe-health}"
[[ "$REPO" =~ ^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$ ]] || { echo "RELEASE_REPO deve essere owner/repo" >&2; exit 1; }
command -v gh >/dev/null || { echo "Installa GitHub CLI: gh" >&2; exit 1; }
command -v keytool >/dev/null || { echo "keytool non disponibile: installa un JDK" >&2; exit 1; }
command -v openssl >/dev/null || { echo "openssl non disponibile" >&2; exit 1; }
gh auth status >/dev/null || { echo "Autenticazione GitHub non valida. Esegui gh auth login." >&2; exit 1; }
gh repo view "$REPO" --json nameWithOwner >/dev/null
CODE="$(sed -n 's/^VERSION_CODE=//p' version.properties)"
NAME="$(sed -n 's/^VERSION_NAME=//p' version.properties)"
[[ "$CODE" =~ ^[0-9]+$ && "$NAME" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "version.properties non valido" >&2; exit 1; }
IFS=. read -r MAJOR MINOR PATCH <<< "$NAME"
NEXT_CODE=$((CODE + 1))
NEXT_NAME="$MAJOR.$MINOR.$((PATCH + 1))"
TAG="health-v$NEXT_NAME"
if gh release view "$TAG" --repo "$REPO" >/dev/null 2>&1; then echo "Release già esistente: $TAG" >&2; exit 1; fi
mkdir -p .secrets dist
chmod 700 .secrets
if [[ ! -f .secrets/signing.properties ]]; then
  PASS="$(openssl rand -hex 24)"
  keytool -genkeypair -v -keystore .secrets/health-release.jks -alias health -keyalg RSA -keysize 3072 -validity 10000 -storepass "$PASS" -keypass "$PASS" -dname "CN=Health" >/dev/null 2>&1
  printf 'storeFile=.secrets/health-release.jks\nstorePassword=%s\nkeyAlias=health\nkeyPassword=%s\n' "$PASS" "$PASS" > .secrets/signing.properties
  chmod 600 .secrets/signing.properties .secrets/health-release.jks
  echo "Creata chiave .secrets/health-release.jks: conservala per i prossimi aggiornamenti."
fi
ORIGINAL="$(cat version.properties)"
VERSION_UPDATED=0
restore_version() { if [[ "$VERSION_UPDATED" == 0 ]]; then printf '%s\n' "$ORIGINAL" > version.properties; fi; }
trap restore_version EXIT
printf 'VERSION_CODE=%s\nVERSION_NAME=%s\n' "$NEXT_CODE" "$NEXT_NAME" > version.properties
./gradlew :app:assembleRelease "-PupdateRepository=$REPO"
APK="app/build/outputs/apk/release/app-release.apk"
[[ -s "$APK" ]] || { echo "APK release non creato" >&2; exit 1; }
cp "$APK" "dist/health-$NEXT_NAME.apk"
python3 write-update-metadata.py "dist/health-$NEXT_NAME.apk" "$NEXT_CODE" "$NEXT_NAME" it.dueffe.health
gh release create "$TAG" "dist/health-$NEXT_NAME.apk" "dist/latest.json" --repo "$REPO" --title "Health $NEXT_NAME" --notes "APK Android Health $NEXT_NAME"
echo "Release $TAG pubblicata. Pulizia delle vecchie release Health..."
RELEASE_TAGS="$(gh api --paginate "repos/$REPO/releases" --jq '.[].tag_name')" || { echo "Pubblicazione riuscita, impossibile elencare le vecchie release: controllale manualmente." >&2; exit 1; }
while IFS= read -r old_tag; do
  [[ "$old_tag" =~ ^health-v[0-9]+\.[0-9]+\.[0-9]+$ ]] || continue
  [[ "$old_tag" == "$TAG" ]] && continue
  gh release delete "$old_tag" --repo "$REPO" --cleanup-tag --yes || { echo "Pubblicazione riuscita, pulizia interrotta su $old_tag." >&2; exit 1; }
done <<< "$RELEASE_TAGS"
VERSION_UPDATED=1
trap - EXIT
echo "Completato: $REPO / $TAG"
close_terminal_on_success
