#!/usr/bin/env bash
# ============================================================
#  ClawDesktop Linux 打包脚本（统信 UOS / 麒麟 OS）
#  生成 .deb（UOS）和 .rpm（麒麟）安装包
#
#  前置条件：
#    1. JDK 21+（含 jpackage）
#    2. dpkg-deb（生成 deb，UOS/Debian 系自带）
#    3. rpm-build（生成 rpm，麒麟/CentOS 系自带）
#    4. Maven（构建 fat jar）
#
#  用法：
#    ./scripts/package-linux.sh           # 同时生成 deb + rpm
#    ./scripts/package-linux.sh deb       # 仅生成 deb
#    ./scripts/package-linux.sh rpm       # 仅生成 rpm
# ============================================================
set -e

APP_NAME="ClawDesktop"
APP_VERSION="0.1.0"
APP_VENDOR="OpenClaw"
MAIN_CLASS="com.openclaw.desktop.DesktopApplication"
TARGET_DIR="target"
PACKAGING_DIR="packaging"
ICON_DIR="${PACKAGING_DIR}/icons"
ICON_PATH="${ICON_DIR}/icon-256.png"
OUTPUT_DIR="${PACKAGING_DIR}/dist/linux"
JAR_FILE="claw-java-0.1.0-SNAPSHOT.jar"

PKG_TYPE="${1:-all}"

echo "=== ClawDesktop Linux Packaging (UOS + Kylin) ==="

# 1. 检查 jpackage
if ! command -v jpackage &>/dev/null; then
    echo "[ERROR] jpackage not found. Ensure JDK 21+ is installed."
    exit 1
fi

# 2. 检查 maven
if ! command -v mvn &>/dev/null; then
    echo "[ERROR] mvn not found."
    exit 1
fi

# 3. 构建 fat jar
echo "[1/5] Building fat jar with Maven..."
mvn clean package -DskipTests

# 4. 生成图标
echo "[2/5] Generating icons..."
mkdir -p "${ICON_DIR}"
if [ ! -f "${ICON_PATH}" ]; then
    java -cp "${TARGET_DIR}/${JAR_FILE}" com.openclaw.desktop.util.IconGenerator "${ICON_DIR}" || {
        echo "[WARN] Icon generation failed."
        ICON_PATH=""
    }
fi

# 5. 创建输出目录
mkdir -p "${OUTPUT_DIR}"

# 通用 jpackage 参数
JPACKAGE_COMMON_ARGS=(
    --name "${APP_NAME}"
    --app-version "${APP_VERSION}"
    --vendor "${APP_VENDOR}"
    --input "${TARGET_DIR}"
    --main-jar "${JAR_FILE}"
    --main-class "${MAIN_CLASS}"
    --dest "${OUTPUT_DIR}"
    --java-options "--enable-native-access=ALL-UNNAMED"
    --java-options "-Xms256m -Xmx1024m"
    --linux-menu-group "Utility;Office"
    --linux-shortcut
)
if [ -n "${ICON_PATH}" ] && [ -f "${ICON_PATH}" ]; then
    JPACKAGE_COMMON_ARGS+=(--icon "${ICON_PATH}")
fi

# 6. 生成 deb（统信 UOS）
build_deb() {
    echo "[3/5] Building .deb for UOS..."
    if ! command -v dpkg-deb &>/dev/null; then
        echo "[WARN] dpkg-deb not found, skipping deb."
        return 0
    fi
    jpackage --type deb "${JPACKAGE_COMMON_ARGS[@]}" \
        --linux-deb-maintainer "openclaw@example.com" \
        --linux-package-name "clawdesktop"
    echo "[INFO] .deb created in ${OUTPUT_DIR}/"
}

# 7. 生成 rpm（麒麟 OS）
build_rpm() {
    echo "[4/5] Building .rpm for Kylin..."
    if ! command -v rpmbuild &>/dev/null; then
        echo "[WARN] rpmbuild not found, skipping rpm."
        return 0
    fi
    jpackage --type rpm "${JPACKAGE_COMMON_ARGS[@]}" \
        --linux-package-name "clawdesktop" \
        --linux-rpm-license-type "MIT"
    echo "[INFO] .rpm created in ${OUTPUT_DIR}/"
}

case "${PKG_TYPE}" in
    deb) build_deb ;;
    rpm) build_rpm ;;
    all) build_deb; build_rpm ;;
    *)
        echo "Usage: $0 [deb|rpm|all]"
        exit 1
        ;;
esac

echo "[5/5] Done."
echo "=== Packaging Complete ==="
echo "Output: ${OUTPUT_DIR}/"
ls -lh "${OUTPUT_DIR}/" 2>/dev/null || true
