# Armor — Duoqin Qin F22 disguise launcher
#
# Common dev tasks. The Qin F22's PackageManagerService is patched so direct
# `adb install` is blocked — we push the APK and open ZArchiver on the device
# to install manually. See CLAUDE.md for the full story.

PACKAGE      := com.armor.launcher
ADMIN        := $(PACKAGE)/.DeviceAdmin
HOME_ACT     := $(PACKAGE)/.DisguiseActivity
APK          := app/build/outputs/apk/debug/app-debug.apk
APK_REMOTE   := /sdcard/Download/armor.apk
ZARCHIVER    := ru.zdevs.zarchiver/.ZArchiver

.PHONY: help build push open-installer install update relaunch end start \
        status logs boot-trace home-resolver dpm-status clean

help:
	@echo 'Armor dev targets:'
	@echo '  make build         — gradle assembleDebug (in nix-shell jdk17)'
	@echo '  make push          — adb push APK to /sdcard/Download/armor.apk'
	@echo '  make open-installer— launch ZArchiver on the device'
	@echo '  make install       — build + push + open ZArchiver (tap APK on phone)'
	@echo '  make update        — force-stop + relaunch DisguiseActivity'
	@echo '  make relaunch      — alias for update'
	@echo '  make end           — broadcast EXIT_KIOSK (disarm)'
	@echo '  make start         — set device-owner'
	@echo '  make status        — show DO + Lock Task + HOME resolver state'
	@echo '  make logs          — adb logcat filtered to Armor tags'
	@echo '  make boot-trace    — clear log, reboot, dump boot-relevant lines'
	@echo '  make home-resolver — show what the system thinks HOME is'
	@echo '  make dpm-status    — dump device_policy state'

build:
	nix-shell -p jdk17 --run './gradlew assembleDebug'

push:
	adb push $(APK) $(APK_REMOTE)

open-installer:
	adb shell am start -n $(ZARCHIVER)

install: build push open-installer
	@echo '>>> Now tap armor.apk in ZArchiver to install.'

update:
	adb shell am force-stop $(PACKAGE)
	adb shell am start -n $(HOME_ACT)

relaunch: update

end:
	adb shell am broadcast -a $(PACKAGE).EXIT_KIOSK -p $(PACKAGE)

start:
	adb shell dpm set-device-owner $(ADMIN)

status:
	@echo '--- Device Owner ---'
	@adb shell dumpsys device_policy | grep -iE 'owner|armor' | head -20
	@echo '--- Lock Task ---'
	@adb shell dumpsys activity activities | grep -iE 'mLockTaskModeState|mLockTaskPackages' | head -5
	@echo '--- HOME resolver ---'
	@$(MAKE) -s home-resolver

home-resolver:
	@adb shell dumpsys package preferred-xml 2>/dev/null | head -40 || true
	@adb shell dumpsys package r preferred-activities 2>/dev/null | grep -A2 -iE 'home|main' | head -40

dpm-status:
	adb shell dumpsys device_policy

logs:
	adb logcat -v time ArmorBoot:V ArmorDPC:V ArmorDisguise:V ArmorHidden:V ArmorRescue:V '*:S'

boot-trace:
	@echo '>>> clearing logcat, rebooting...'
	adb logcat -c
	adb reboot
	@echo '>>> waiting 25s for boot...'
	@sleep 25
	@echo '--- ActivityTaskManager / Boot / Armor / Launcher ---'
	adb logcat -d | grep -iE 'ActivityTaskManager.*START|Boot complete|ArmorBoot|launcher|HOME' | head -80

clean:
	./gradlew clean
