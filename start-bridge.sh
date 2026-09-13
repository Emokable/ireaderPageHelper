#!/system/bin/sh
# Run as the existing ADB root user. No boot or persistent system changes.
APK_UID="$1"
case "$APK_UID" in ''|*[!0-9]*) echo 'Expected application UID'; exit 1;; esac
if [ "$(id -u)" != 0 ] || [ "$APK_UID" -lt 10000 ]; then
    echo 'ADB root and a valid application UID are required'; exit 1
fi
for bridge_pid in $(pgrep -f 'dev[.]pageh[.]helper[.]RootDaemon'); do
    bridge_cmd=$(tr '\000' ' ' </proc/"$bridge_pid"/cmdline 2>/dev/null)
    case "$bridge_cmd" in
        *" dev.pageh.helper.RootDaemon $APK_UID ") echo "Bridge already running PID=$bridge_pid"; exit 0;;
        *" dev.pageh.helper.RootDaemon "*) echo 'Existing bridge uses a different UID; run stop-bridge.ps1 first'; exit 1;;
    esac
done
if [ "$2" = '--check' ]; then exit 3; fi
nohup env CLASSPATH=/data/local/tmp/pageh-helper.dex app_process /system/bin dev.pageh.helper.RootDaemon "$APK_UID" </dev/null >/data/local/tmp/pageh-bridge.log 2>&1 &
echo "Started bridge PID=$!"
