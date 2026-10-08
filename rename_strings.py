import os

def replace_strings(filepath, replacements):
    if not os.path.exists(filepath): return
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    for old, new in replacements:
        content = content.replace(old, new)
        
    with open(filepath, 'w', encoding='utf-8', newline='\n') as f:
        f.write(content)

ru_replacements = [
    ('>Устанавливать APK через FreezeYou<', '>Устанавливать и удалять приложения через FreezeYou<'),
    ('>Позволяет устанавливать APK-файлы через встроенный установщик<', '>Позволяет устанавливать APK-файлы и удалять приложения, в том числе системные.<')
]

en_replacements = [
    ('>Install APK files through FreezeYou<', '>Install and uninstall apps through FreezeYou<'),
    (r'>Allows installing APK files using the app\'s built-in installer<', '>Allows installing APK files and uninstalling apps, including system apps.<')
]

uk_replacements = [
    ('>Встановлювати APK-файли через FreezeYou<', '>Встановлювати та видаляти програми через FreezeYou<'),
    ('>Дозволяє встановлювати APK-файли через вбудований інсталятор<', '>Дозволяє встановлювати APK-файли та видаляти програми, в тому числі системні.<')
]

replace_strings('app/src/main/res/values-ru-rRU/strings.xml', ru_replacements)
replace_strings('app/src/main/res/values/strings.xml', en_replacements)
replace_strings('app/src/main/res/values-uk-rUA/strings.xml', uk_replacements)
print('Done!')
