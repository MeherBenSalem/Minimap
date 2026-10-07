#!/usr/bin/env bash
set -euo pipefail
ROOT=$(cd "$(dirname "$0")/.." && pwd)
VERSION=1.21.1
RELEASE=
BASELINE=false
while (($#)); do
    case "$1" in
        --version) VERSION=${2:?missing version}; shift 2 ;;
        --release) RELEASE=${2:?missing Java release}; shift 2 ;;
        --baseline) BASELINE=true; shift ;;
        *) echo "Unknown argument: $1" >&2; exit 2 ;;
    esac
done
case "$VERSION" in
    1.20.1) RELEASE=${RELEASE:-17} ;;
    1.21.1) RELEASE=${RELEASE:-21} ;;
    26.1.2|26.2) RELEASE=${RELEASE:-25} ;;
    *) echo "Unsupported version root: $VERSION" >&2; exit 2 ;;
esac
case "$RELEASE" in 17|21|25) ;; *) echo "Unsupported Java release: $RELEASE" >&2; exit 2 ;; esac
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAVA="${JAVA_HOME:+$JAVA_HOME/bin/}java"
BUILD=$(mktemp -d)
trap 'rm -rf "$BUILD"' EXIT
SOURCE="$ROOT/$VERSION/common/src/main/java/dev/nightbeam/odysseymap"
find "$ROOT/tests/persistence/stubs" -name '*.java' > "$BUILD/sources"
if $BASELINE; then
    mkdir -p "$BUILD/baseline"
    for file in Tile TileCache; do
        git -C "$ROOT" show d4bf73e:"$VERSION/common/src/main/java/dev/nightbeam/odysseymap/world/$file.java" > "$BUILD/baseline/$file.java"
        echo "$BUILD/baseline/$file.java" >> "$BUILD/sources"
    done
    echo "$ROOT/tests/persistence/src/dev/nightbeam/odysseymap/world/BaselineReproduction.java" >> "$BUILD/sources"
    "$JAVAC" --release "$RELEASE" -d "$BUILD/classes" @"$BUILD/sources"
    "$JAVA" -ea -cp "$BUILD/classes" dev.nightbeam.odysseymap.world.BaselineReproduction
else
    sed -i '\@/dev/nightbeam/odysseymap/render/MinimapTexture.java$@d; \@/dev/nightbeam/odysseymap/render/FullscreenMapRenderer.java$@d' "$BUILD/sources"
    echo "$SOURCE/render/MinimapTexture.java" >> "$BUILD/sources"
    echo "$SOURCE/render/FullscreenMapRenderer.java" >> "$BUILD/sources"
    for file in Tile TileCache TileStorage WorldScanner OdysseyMapClient; do
        echo "$SOURCE/world/$file.java" >> "$BUILD/sources"
    done
    echo "$SOURCE/client/ClientEvents.java" >> "$BUILD/sources"
    echo "$ROOT/tests/persistence/src/dev/nightbeam/odysseymap/world/MapPersistenceTest.java" >> "$BUILD/sources"
    "$JAVAC" --release "$RELEASE" -Xlint:all -Werror -d "$BUILD/classes" @"$BUILD/sources"
    "$JAVA" -ea -Xmx256m -cp "$BUILD/classes" dev.nightbeam.odysseymap.world.MapPersistenceTest
    echo "Version root $VERSION; Java release $RELEASE"
fi
