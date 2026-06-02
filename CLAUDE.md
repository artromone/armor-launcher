# Armor — disguise launcher for Duoqin Qin F22

Заметки по железу, ROM-у, тулчейну и dev-workflow. **Прочитай это до любых действий с устройством**, иначе можно повторно угодить в kiosk-ловушку.

---

## Устройство

- **Модель**: Duoqin Qin F22 (ADB serial `QF22MQC...`, пользователь изначально называл F21 — это не так).
- **OS**: Android 11 (API 30), 32-битная сборка `armeabi-v7a`, чип MediaTek MT6739 (`k39tv1_bsp_1g`).
- **Экран**: 480×640 портретный, физическая T9-клавиатура + D-pad.
- **ROM identifier**: `alps/full_k39tv1_bsp_1g`, fingerprint `ro.duoqin.ota.tag=f22_agn_gms` (Global with GMS).

## ROM-блокировки (важно)

OEM Qin запатчил часть `package` подсистемы. Что **не работает** через ADB:

- `adb shell pm <anything>` → возвращает литеральное `Failure` (одно слово, без причины)
- `adb shell cmd package list packages` / `install` / `uninstall` → `Failure`
- `adb install` / `adb install -r` → `Failure`

Что **работает**:

- `adb shell am ...` (Activity Manager — запуск активити, force-stop, broadcasts)
- `adb shell cmd activity ...` (но **не** `cmd package`)
- `adb shell dumpsys ...` (read-only диагностика)
- `adb shell dpm set-device-owner ...` (идёт через `device_policy` сервис, который НЕ запатчен)
- `adb push` / `adb pull` (FS-операции)

## Установка APK

Прямой ADB install заблокирован. Рабочий путь — через **on-device File Manager** (ZArchiver):

```bash
adb push <apk> /sdcard/Download/armor.apk
adb shell am start -n ru.zdevs.zarchiver/.ZArchiver
# На телефоне: Download → armor.apk → Install
```

Для **первой** установки на чистый телефон может потребоваться разрешить ZArchiver устанавливать пакеты: Settings → Apps → ZArchiver → Install unknown apps → toggle on.

## Совместимость APK

На этом ROM капризничает PackageManagerService. Минимальная рабочая конфигурация APK:

- `compileSdk = 30` (не 34/33 — установка падает на `installd: Couldn't opendir vmdl*.tmp`)
- `targetSdk = 30`
- `minSdk = 30`
- AppCompat **1.3.1** (не 1.7.0), Activity-ktx 1.3.1, Core-ktx 1.6.0
- Подпись debug-keystore нормально работает, v2+ scheme

Симптом установки с неправильной версией: «App not installed», в логах `installd: Couldn't opendir /data/app/vmdl*.tmp`. Это **не** наша вина, это OEM-баг.

## Сборка

```bash
cd /home/art/programming/pet/armor
nix-shell -p jdk17 --run "./gradlew assembleDebug"
# или Android Studio → Build → Make Project
```

Финальный APK здесь:
```
/home/art/programming/pet/armor/app/build/outputs/apk/debug/app-debug.apk
```

Промежуточный (тот же контент):
```
/home/art/programming/pet/armor/app/build/intermediates/apk/debug/app-debug.apk
```

## Device Owner / Lock Task

Device Owner — обязательное условие для **настоящей** блокировки статус-бара (без него immersive sticky обходится свайпом).

### Установка
```bash
# На устройстве не должно быть user accounts (удалить Google и любые другие)
adb shell dpm set-device-owner com.armor.launcher/.DeviceAdmin
```

Успех:
```
Success: Device owner set to package ComponentInfo{com.armor.launcher/...DeviceAdmin}
```

### Снятие (важно — `dpm clear-device-owner` НЕ существует на этом dpm-билде, `dpm remove-active-admin` требует `testOnly=true`)

Единственный надёжный способ — **из кода приложения** через `DevicePolicyManager.clearDeviceOwnerApp()` и `removeActiveAdmin()`. Имплементировано в:
- `RescueReceiver` (срабатывает на `com.armor.launcher.EXIT_KIOSK` broadcast)
- Физическая комбинация: **5 быстрых нажатий `*` за 3 секунды** (в DisguiseActivity)

```bash
# disarm через ADB — ОБЯЗАТЕЛЬНО с явной таргет-привязкой
# (Android 11 не доставляет implicit broadcasts manifest-receiver'ам):
adb shell am broadcast -a com.armor.launcher.EXIT_KIOSK -p com.armor.launcher

# альтернативно — указать компонент напрямую:
adb shell am broadcast -a com.armor.launcher.EXIT_KIOSK -n com.armor.launcher/.RescueReceiver
```

**Без `-p` или `-n` broadcast уйдёт в никуда** — `result=0` обманчиво показывает успех, но receiver не вызывается. Это особенность Android 8+ broadcast restrictions. Проверить эффект:

```bash
adb shell dumpsys device_policy | grep -i armor    # должно быть пусто
adb shell dumpsys activity activities | grep mLockTaskModeState   # должно быть NONE
```

### OEM-капкан с phantom admin

После снятия Device Owner Settings UI Qin'а **может продолжать** показывать «Cannot uninstall active device admin app», даже если `dumpsys device_policy` пуст. Состояние не консистентно между Settings и DPM service. Решается тем что DisguiseActivity при запуске вызывает `dpm.removeActiveAdmin()` для своего же компонента — это очищает оба источника. См. `disarm()` в `DisguiseActivity.kt`.

## Dev workflow (с активным Device Owner)

```bash
# 1. изменили код, пересобрали:
nix-shell -p jdk17 --run "./gradlew assembleDebug" 

# 2. вышли из kiosk (если он активен):
adb shell am broadcast -a com.armor.launcher.EXIT_KIOSK -p com.armor.launcher
# или 5 раз * на телефоне

# 3. push + install через File Manager как описано выше

# 4. вернуть Device Owner:
adb shell dpm set-device-owner com.armor.launcher/.DeviceAdmin

# 5. перезапустить Armor:
adb shell am force-stop com.armor.launcher
adb shell am start -n com.armor.launcher/.DisguiseActivity

# 6. проверить что Lock Task активен:
adb shell dumpsys activity activities | grep -i lockTask
# должно быть: mLockTaskModeState=LOCKED
```

## Аварийное восстановление (если попали в kiosk без disarm)

Если новой APK без RescueReceiver нет, а вы в Lock Task:

**Trick: kill-loop**, который держит Armor мёртвым пока вы делаете дела:

```bash
while true; do adb shell am force-stop com.armor.launcher 2>/dev/null; sleep 0.3; done
```

В параллельном терминале открыть File Manager и установить APK с rescue:

```bash
adb shell am start -n ru.zdevs.zarchiver/.ZArchiver
```

Когда установили — Ctrl+C на kill-loop, и сделайте clear + relaunch.

**Крайний случай**: recovery → Wipe data / factory reset. Сбросит всё, но точно вытащит.

## Текущее состояние (по итогу первой сессии)

- ✅ Установлен минимальный Armor (без HOME, без DeviceAdmin в манифесте, без kiosk)
- ✅ Device Owner снят
- ✅ Active admin снят
- ⏸ HOME-launcher функционал **не активирован** — пользователь хотел развивать UI без риска снова застрять. Включим обратно когда iteration 2+ будет готова и протестирована.

## План итераций

1. ✅ Skeleton + кiosk machinery + dev workflow
2. ⬜ Disguise UI — Nokia-style: пиксельный шрифт, статус-бар (часы/оператор/батарея), главное меню, T9/D-pad навигация
3. ⬜ Hardening — re-arm Lock Task на BOOT_COMPLETED, защита от перезагрузки
4. ⬜ Secret unlock — long-press 5 + tap-code → переход в Real mode
5. ⬜ Real launcher + DPM hide/unhide реальных приложений (browser, Telegram, VPN)
6. ⬜ Fake apps (Contacts, SMS, Calculator, Snake) для аутентичности
