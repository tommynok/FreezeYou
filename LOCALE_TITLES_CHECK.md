# Проверка локалей: заголовки экранов

Приложение применяет свой язык независимо от системного. Ожидаемое — то, что **должно**
показываться при выбранном языке. Пройти с языком English, затем Українська: заголовок в шапке
каждого экрана = ожидаемому. Русские остатки = баг, записать класс экрана.

Оговорка: в списке «Недавние» системы имя всегда из системной локали — это не баг.

| Экран (класс) | Ключ | Ожид. EN | Ожид. RU | Ожид. UK |
|---|---|---|---|---|
| AboutActivity | `about` | About | О FreezeYou-Fork | Про FreezeYou-Fork |
| AppLockActivity | — (label не из строк) | | | |
| AskLockScreenActivity | `askIfLockScreen` | Do you want to lock the screen now? | Вы хотите заблокировать экран сейчас? | Ви хочете заблокувати екран зараз? |
| AskRunActivity | — (label не из строк) | | | |
| AutoDiagnosisActivity | `autoDiagnosis` | Diagnosis | Диагностика | Діагностика |
| BackupImportChooserActivity | `impt` | Import | Импортировать | Імпортувати |
| BackupMainActivity | `backupAndRestore` | Backup and restore | Бекап и восстановление | Бекап і відновлення |
| CommandExecutorActivity | `commandExecutionTool` | Command execution tool | Инструмент выполнения команд | Інструмент виконання команд |
| DisableApplications | — (label не из строк) | | | |
| EnableApplications | — (label не из строк) | | | |
| FUFLauncherShortcutCreator | `disableAEnable` | Freeze/Unfreeze/Run | Заморозка/разморозка/запуск | Заморозка/ Розморозка / Запуск |
| FUFNotificationsManageActivity | `manageQuickFUFNoti` | Manage quick freeze notifications | Управление уведомлениями о быстрой заморозке | Керування сповіщеннями при швидкій заморозці |
| FirstIcon | `app_name` | FreezeYou-Fork | FreezeYou-Fork | FreezeYou-Fork |
| FirstTimeSetupActivity | — (label не из строк) | | | |
| ForceStop | `forceStop` | Force Stop | Принудительная остановка | Примусова зупинка |
| Freeze | `disableAEnable` | Freeze/Unfreeze/Run | Заморозка/разморозка/запуск | Заморозка/ Розморозка / Запуск |
| FullScreenImageViewerActivity | — (label не из строк) | | | |
| GetDisabledApplications | — (label не из строк) | | | |
| InstallPackagesActivity | `installAndUninstall` | Install and Uninstall | Установка и удаление | Установка і видалення |
| LauncherShortcutConfirmAndGenerateActivity | `createLauncherShortcut` | Create Launcher Shortcut | Создать ярлык на рабочем столе | Створити ярлик на робочому столі |
| Main | — (label не из строк) | | | |
| ManualModeActivity | `manualMode` | Freeze by package name | Заморозка по имени пакета | Заморозка за ім\'ям пакета |
| OneKeyFreeze | `oneKeyFreeze` | Onekey Freeze | OneKEY заморозка | OneKEY заморозка |
| OneKeyScreenLockImmediatelyActivity | `oneKeyLockScreen` | Lock Screen | Блокировка экрана | Блокування екрану |
| OneKeyUF | `oneKeyUF` | Onekey Unfreeze | OneKEY разморозка | OneKEY розморозка |
| ScheduledTaskCommandsSyntaxActivity | `scheduledTaskCommandsSyntax` | Task Setup | Настройка заданий | Налаштування завдань |
| ScheduledTasksAddActivity | — (label не из строк) | | | |
| ScheduledTasksManageActivity | `scheduledTasks` | Scheduled tasks | Запланированные задания | Заплановані завдання |
| SecondIcon | `app_name` | FreezeYou-Fork | FreezeYou-Fork | FreezeYou-Fork |
| SelectShortcutIconActivity | `createLauncherShortcut` | Create Launcher Shortcut | Создать ярлык на рабочем столе | Створити ярлик на робочому столі |
| SelectTargetActivityActivity | `plsSelect` | Please select | Пожалуйста выберите | Будь ласка, оберіть |
| SettingsActivity | `moreSettings` | Settings | Настройки | Налаштування |
| ShortcutLauncherFolderActivity | `folder` | Folder | Папка | Папка |
| ShowLogcatActivity | `showLogcat` | View Logs | Просмотр журнала | Перегляд журналу |
| ShowSimpleDialogActivity | — (label не из строк) | | | |
| ThirdIcon | `app_name` | FreezeYou-Fork | FreezeYou-Fork | FreezeYou-Fork |
| UninstallActivity | — (label не из строк) | | | |
| UriAutoAllowManageActivity | `manageUriAutoAllow` | Manage URI Request Auto Allow List | Управление запросами белого списка URI | Керування запитами білого списку URI |
| UriFreezeActivity | `disableAEnable` | Freeze/Unfreeze/Run | Заморозка/разморозка/запуск | Заморозка/ Розморозка / Запуск |
| UserDefinedListsManageActivity | `manageMyCustomization` | Manage my customization | Управление списком «Моя подборка» | Керування «Моєю підбіркою» |
