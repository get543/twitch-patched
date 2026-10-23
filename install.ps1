
try {
    adb -s emulator-5554 install ".\twitch_patched.apk"
} catch {
    adb -s emulator-5554 install -r ".\twitch_patched.apk"
}
