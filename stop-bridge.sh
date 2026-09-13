#!/system/bin/sh
# Stop only the explicitly identified PageH Java entry point.
if [ "$(id -u)" != 0 ]; then echo 'ADB root required'; exit 1; fi
for bridge_pid in $(pgrep -f 'dev[.]pageh[.]helper[.]RootDaemon'); do
    bridge_cmd=$(tr '\000' ' ' </proc/"$bridge_pid"/cmdline 2>/dev/null)
    case "$bridge_cmd" in
        *" dev.pageh.helper.RootDaemon "*) kill "$bridge_pid" && echo "Stopped PageH bridge PID=$bridge_pid";;
    esac
done
