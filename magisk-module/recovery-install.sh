#!/sbin/sh
# Sourced by META-INF/com/google/android/update-binary in a custom recovery.
MODID=zoro.benojir.callrecorder
APK="system/priv-app/$MODID/app-release.apk"
ALLOWLIST="system/etc/permissions/privapp-permissions-$MODID.xml"
ADDON="system/addon.d/51-$MODID.sh"
cleanup() {
  [ -z "${STAGE:-}" ] || rm -rf "$STAGE"
  [ -z "${NEW:-}" ] || rm -rf "$NEW"
  [ -z "${PERM:-}" ] || rm -f "$PERM.new"
  rm -rf "$WORK"
}
trap cleanup EXIT
unzip -o "$ZIPFILE" module.prop customize.sh "$APK" "$ALLOWLIST" "$ADDON" -d "$WORK" >/dev/null || abort "Cannot extract module payload."
[ -s "$WORK/$APK" ] && [ -s "$WORK/$ALLOWLIST" ] || abort "Incomplete module payload."

# If a root manager is already installed, stage a systemless module. Never write
# through the encrypted /data placeholder when the recovery cannot decrypt it.
if [ -d /data/adb/magisk ] || [ -f /data/adb/ksud ] || [ -d /data/adb/ksu ] || [ -f /data/adb/apd ] || [ -d /data/adb/ap ]; then
  [ -d /data/adb/modules ] || mkdir -p /data/adb/modules || abort "Mount and decrypt /data first."
  TARGET="/data/adb/modules/$MODID"
  STAGE="/data/adb/.$MODID-install-$$"
  mkdir -p "$STAGE" || abort "Cannot write modules directory. Decrypt /data first."
  cp -R "$WORK/system" "$STAGE/system" || abort "Cannot stage system files."
  cp "$WORK/module.prop" "$WORK/customize.sh" "$STAGE/" || abort "Cannot stage module metadata."
  chown -R 0:0 "$STAGE" || abort "Cannot set module ownership."
  find "$STAGE" -type d -exec chmod 0755 {} \; || abort "Cannot set module directory permissions."
  find "$STAGE" -type f -exec chmod 0644 {} \; || abort "Cannot set module file permissions."
  chmod 0755 "$STAGE/$ADDON" || abort "Cannot set addon permissions."
  command -v chcon >/dev/null 2>&1 || abort "Recovery must provide chcon to label systemless app files."
  chcon -R u:object_r:system_file:s0 "$STAGE/system" || abort "Cannot label systemless app files."
  # Only fixed, app-owned paths are ever replaced.
  BACKUP="/data/adb/.$MODID-backup-$$"
  if [ -d "$TARGET" ]; then mv "$TARGET" "$BACKUP" || abort "Cannot back up old module."; fi
  if ! mv "$STAGE" "$TARGET"; then
    [ ! -d "$BACKUP" ] || mv "$BACKUP" "$TARGET"
    abort "Cannot activate module. Previous version restored."
  fi
  rm -rf "$BACKUP"
  ui_print "- Systemless module installed. Reboot with your root manager enabled."
  ui_print "- KernelSU/Next/APatch need a working system mounting backend/metamodule."
  exit 0
fi

# Direct installation is only possible on a mounted, writable system partition.
# EROFS, verified read-only dynamic partitions and locked AVB are not bypassed.
mount /system_root 2>/dev/null || :
mount /system 2>/dev/null || :
SYSTEM=""
for candidate in /system_root/system /system/system /system; do
  if [ -d "$candidate/priv-app" ] && [ -d "$candidate/etc/permissions" ]; then SYSTEM="$candidate"; break; fi
done
[ -n "$SYSTEM" ] || abort "Mount the real system partition in recovery first."
SYSTEM_API=$(sed -n 's/^ro.build.version.sdk=//p' "$SYSTEM/build.prop" 2>/dev/null | head -n 1)
case "$SYSTEM_API" in
  ''|*[!0-9]*) ui_print "- Could not read the installed Android API level; Android 10+ is required." ;;
  *) [ "$SYSTEM_API" -ge 29 ] || abort "Call Recorder requires Android 10 or newer." ;;
esac
mount -o remount,rw /system_root 2>/dev/null || :
mount -o remount,rw /system 2>/dev/null || :
PROBE="$SYSTEM/priv-app/.call-recorder-write-$$"
(umask 077; : > "$PROBE") 2>/dev/null || abort "System is read-only (for example EROFS/AVB). Use a root manager and its system mounting backend."
rm -f "$PROBE"

APPDIR="$SYSTEM/priv-app/$MODID"
NEW="$SYSTEM/.$MODID-new-$$"
OLD="$SYSTEM/.$MODID-old-$$"
PERM="$SYSTEM/etc/permissions/privapp-permissions-$MODID.xml"
mkdir -p "$NEW" || abort "Cannot create application directory."
cp "$WORK/$APK" "$NEW/app-release.apk" || abort "Cannot copy APK (check free space)."
cp "$WORK/$ALLOWLIST" "$PERM.new" || abort "Cannot copy permission allowlist."
chown 0:0 "$NEW" "$NEW/app-release.apk" "$PERM.new" || abort "Cannot set system file ownership."
chmod 0755 "$NEW" || abort "Cannot set app directory permissions."
chmod 0644 "$NEW/app-release.apk" "$PERM.new" || abort "Cannot set system file permissions."
command -v chcon >/dev/null 2>&1 || abort "Recovery must provide chcon for a safe privileged app installation."
chcon u:object_r:system_file:s0 "$NEW" "$NEW/app-release.apk" "$PERM.new" || abort "Cannot set SELinux file labels."
# Keep rollback copies until APK AND permission allowlist are installed together.
[ ! -f "$PERM" ] || cp -p "$PERM" "$WORK/previous-permissions.xml" || abort "Cannot back up allowlist."
[ ! -d "$APPDIR" ] || mv "$APPDIR" "$OLD" || abort "Cannot back up installed app."
if ! mv "$NEW" "$APPDIR" || ! mv "$PERM.new" "$PERM"; then
  rm -rf "$APPDIR"
  [ ! -d "$OLD" ] || mv "$OLD" "$APPDIR"
  [ ! -f "$WORK/previous-permissions.xml" ] || cp -p "$WORK/previous-permissions.xml" "$PERM"
  abort "Installation failed; previous files restored."
fi
rm -rf "$OLD"
if [ -d "$SYSTEM/addon.d" ]; then
  cp "$WORK/$ADDON" "$SYSTEM/addon.d/51-$MODID.sh"
  chown 0:0 "$SYSTEM/addon.d/51-$MODID.sh"
  chmod 0755 "$SYSTEM/addon.d/51-$MODID.sh"
  chcon u:object_r:system_file:s0 "$SYSTEM/addon.d/51-$MODID.sh"
fi
ui_print "- Installed privileged app and permission allowlist. Reboot Android."
