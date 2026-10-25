#!/system/bin/sh
# Sourced by Magisk, KernelSU, KernelSU Next and APatch native installers.
[ "$API" -ge 29 ] || abort "Call Recorder requires Android 10 or newer."
[ -f "$MODPATH/system/priv-app/zoro.benojir.callrecorder/app-release.apk" ] || abort "APK is missing from this ZIP."
[ -f "$MODPATH/system/etc/permissions/privapp-permissions-zoro.benojir.callrecorder.xml" ] || abort "Privileged permission allowlist is missing."

if [ "${APATCH:-false}" = true ]; then
  ui_print "- Installing with APatch"
elif [ "${KSU:-false}" = true ]; then
  ui_print "- Installing with KernelSU / KernelSU Next"
else
  ui_print "- Installing with Magisk"
fi
set_perm_recursive "$MODPATH/system" 0 0 0755 0644
set_perm "$MODPATH/system/addon.d/51-zoro.benojir.callrecorder.sh" 0 0 0755
if [ "${KSU:-false}" = true ] || [ "${APATCH:-false}" = true ]; then
  ui_print "- System files require your root manager's system mounting backend."
  ui_print "- On metamodule-based versions, install/enable a compatible mount metamodule first."
fi
ui_print "- Reboot, open Call Recorder and grant microphone / phone state permissions."
ui_print "- Audio support depends on device firmware; choose a source in Settings."
