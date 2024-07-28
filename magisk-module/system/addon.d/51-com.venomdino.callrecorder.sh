#!/sbin/sh
# ADDOND_VERSION=2

. /tmp/backuptool.functions

files="priv-app/com.venomdino.callrecorder/app-release.apk etc/permissions/privapp-permissions-com.venomdino.callrecorder.xml"

case "${1}" in
backup|restore)
    for f in ${files}; do
        "${1}_file" "${S}/${f}"
    done
    ;;
esac