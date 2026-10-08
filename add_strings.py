def add_strings(filepath, new_strings):
    with open(filepath, 'r', encoding='utf-8') as f:
        content = f.read()
    
    insert_index = content.find('</resources>')
    if insert_index == -1:
        print('Error')
        return
        
    new_content = content[:insert_index] + new_strings + '\n' + content[insert_index:]
    with open(filepath, 'w', encoding='utf-8', newline='\n') as f:
        f.write(new_content)

ru_strings = '''    <string name="system_app_warning">Это системное приложение, будьте осторожны!</string>
    <string name="uninstall_updates_only">Удалить обновления</string>
    <string name="uninstall_completely">Удалить полностью</string>'''

en_strings = '''    <string name="system_app_warning">This is a system app, please be careful!</string>
    <string name="uninstall_updates_only">Uninstall updates</string>
    <string name="uninstall_completely">Uninstall completely</string>'''

add_strings('app/src/main/res/values-ru-rRU/strings.xml', ru_strings)
add_strings('app/src/main/res/values/strings.xml', en_strings)
