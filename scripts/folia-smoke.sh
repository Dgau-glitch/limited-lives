#!/usr/bin/env bash
set -euo pipefail

ROOT=$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)
WORK_DIR=${FOLIA_SMOKE_DIR:-"$ROOT/build/folia-smoke"}
FOLIA_URL=${FOLIA_URL:-"https://fill-data.papermc.io/v1/objects/f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39/folia-1.21.11-14.jar"}
FOLIA_SHA256=${FOLIA_SHA256:-"f52c408490a0225611e67907a3ca19f7e6da2c6bc899e715d5f46844e7103c39"}
MOJANG_URL=${MOJANG_URL:-"https://piston-data.mojang.com/v1/objects/64bb6d763bed0a9f1d632ec347938594144943ed/server.jar"}
MOJANG_SHA1=${MOJANG_SHA1:-"64bb6d763bed0a9f1d632ec347938594144943ed"}

if [[ ${FOLIA_SMOKE_REUSE:-0} != 1 ]]; then
"$ROOT/gradlew" clean shadowJar
rm -rf "$WORK_DIR"
mkdir -p "$WORK_DIR/plugins"
curl --fail --location --silent --show-error "$FOLIA_URL" --output "$WORK_DIR/folia.jar"
echo "$FOLIA_SHA256  $WORK_DIR/folia.jar" | sha256sum --check --status
mkdir -p "$WORK_DIR/cache"
curl --fail --location --silent --show-error "$MOJANG_URL" --output "$WORK_DIR/cache/mojang_1.21.11.jar"
echo "$MOJANG_SHA1  $WORK_DIR/cache/mojang_1.21.11.jar" | sha1sum --check --status
cp "$(find "$ROOT/build/libs" -maxdepth 1 -name 'LimitedLives-*.jar' | sort | tail -1)" "$WORK_DIR/plugins/LimitedLives.jar"
if jar tf "$WORK_DIR/plugins/LimitedLives.jar" | rg -qi '(^|/)(bstats|metrics|telemetry|analytics)(/|\.|$)|AnnoyingStats'; then
    echo 'Metrics implementation detected in runtime JAR' >&2
    exit 1
fi
mkdir -p "$WORK_DIR/plugins/LimitedLives/libs/com/h2database/h2/2.2.224"
mkdir -p "$WORK_DIR/plugins/LimitedLives/libs/org/javassist/javassist/3.28.0-GA"
mkdir -p "$WORK_DIR/plugins/LimitedLives/libs/org/reflections/reflections/0.10.2"
mkdir -p "$WORK_DIR/plugins/LimitedLives/libs/org/ow2/asm/asm/9.7"
mkdir -p "$WORK_DIR/plugins/LimitedLives/libs/org/ow2/asm/asm-commons/9.7"
mkdir -p "$WORK_DIR/plugins/LimitedLives/libs/org/ow2/asm/asm-tree/9.7"
mkdir -p "$WORK_DIR/plugins/LimitedLives/libs/me/lucko/jar-relocator/1.7"
curl --fail --location --silent --show-error https://repo.maven.apache.org/maven2/com/h2database/h2/2.2.224/h2-2.2.224.jar \
    --output "$WORK_DIR/plugins/LimitedLives/libs/com/h2database/h2/2.2.224/h2-2.2.224.jar"
curl --fail --location --silent --show-error https://repo.maven.apache.org/maven2/org/javassist/javassist/3.28.0-GA/javassist-3.28.0-GA.jar \
    --output "$WORK_DIR/plugins/LimitedLives/libs/org/javassist/javassist/3.28.0-GA/javassist-3.28.0-GA.jar"
curl --fail --location --silent --show-error https://repo.maven.apache.org/maven2/org/reflections/reflections/0.10.2/reflections-0.10.2.jar \
    --output "$WORK_DIR/plugins/LimitedLives/libs/org/reflections/reflections/0.10.2/reflections-0.10.2.jar"
for artifact in asm asm-commons asm-tree; do
    curl --fail --location --silent --show-error "https://repo.maven.apache.org/maven2/org/ow2/asm/$artifact/9.7/$artifact-9.7.jar" \
        --output "$WORK_DIR/plugins/LimitedLives/libs/org/ow2/asm/$artifact/9.7/$artifact-9.7.jar"
done
curl --fail --location --silent --show-error https://repo.maven.apache.org/maven2/me/lucko/jar-relocator/1.7/jar-relocator-1.7.jar \
    --output "$WORK_DIR/plugins/LimitedLives/libs/me/lucko/jar-relocator/1.7/jar-relocator-1.7.jar"
printf 'eula=true\n' > "$WORK_DIR/eula.txt"
printf 'online-mode=false\nmax-players=4\nview-distance=2\nsimulation-distance=2\n' > "$WORK_DIR/server.properties"

run_server() {
    local delay=$1
    local console=$2
    (
        cd "$WORK_DIR"
        timeout 180s bash -c "{ sleep $delay; echo lifereload; sleep 2; echo stop; } | java -Xms512M -Xmx1G -jar folia.jar --nogui" | tee "$console"
    )
}

run_server 70 console-first.log
cp "$WORK_DIR/logs/latest.log" "$WORK_DIR/first.log"
sed -i '/^threaded-regions:/,/^[^ ]/ s/^  threads: .*/  threads: 4/' "$WORK_DIR/config/paper-global.yml"
run_server 40 console-second.log
fi

LOG="$WORK_DIR/logs/latest.log"
test -f "$LOG"
rg -q 'LimitedLives.*Enabled|Enabling LimitedLives' "$LOG"
rg -q 'Done \(' "$LOG"
rg -q 'Closing Server|Stopping server' "$LOG"
rg -q 'Successfully reloaded the plugin' "$WORK_DIR/first.log" "$LOG"
rg -q 'initial 4 target thread\(s\)' "$LOG"
if rg -i 'ownership|async.?catcher|schedule.*(disabled|shutdown)|retired entity|thread.*violation|failed to close|(ERROR|SEVERE).*LimitedLives|Error occurred while enabling LimitedLives' "$WORK_DIR/first.log" "$LOG"; then
    echo 'Folia ownership/lifecycle error detected' >&2
    exit 1
fi

echo 'Folia 1.21.11 startup/shutdown smoke test passed.'
