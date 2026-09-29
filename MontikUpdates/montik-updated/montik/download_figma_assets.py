#!/usr/bin/env python3
"""
Скрипт для скачивания всех нужных ассетов из Figma для игры Монтик.
Требует: pip install requests

Использование:
    python3 download_figma_assets.py
"""

import os
import json
import requests
from pathlib import Path

# Данные Figma файла
FIGMA_FILE_KEY = "fRE5bxFESOizTbv7now5aL"
FIGMA_API_URL = "https://www.figma.com/api/mcp/asset"

# Список фреймов/компонентов для экспорта
# Формат: (node_id, file_name, description)
ASSETS_TO_DOWNLOAD = [
    # Frame 33-34: Раскраска персонажа (8 цветных вариантов)
    ("438:136", "figma_assets/skins/frame_33.png", "Frame 33 - раскраска с 8 вариантами Монтика"),
    ("443:155", "figma_assets/skins/frame_34.png", "Frame 34 - тот же экран"),

    # Android Compact - 39: Рынок/бартер (если есть)
    ("407:55", "figma_assets/barter/android_compact_39.png", "Android Compact 39 - возможно рынок"),

    # Дополнительные экраны
    ("114:227", "figma_assets/ui/android_compact_1.png", "Android Compact 1"),
    ("114:228", "figma_assets/ui/android_compact_2.png", "Android Compact 2"),

    # Главный экран
    ("103:82", "figma_assets/ui/main_screen.png", "Главный экран комнаты"),
]

def ensure_dir(path):
    """Создаёт директорию если её нет."""
    os.makedirs(path, exist_ok=True)

def download_file(url, output_path):
    """Скачивает файл с URL."""
    try:
        print(f"  Скачиваю: {output_path}...", end=" ", flush=True)
        response = requests.get(url, timeout=30)
        response.raise_for_status()

        ensure_dir(os.path.dirname(output_path))
        with open(output_path, 'wb') as f:
            f.write(response.content)

        size_mb = len(response.content) / (1024 * 1024)
        print(f"✅ ({size_mb:.2f} MB)")
        return True
    except Exception as e:
        print(f"❌ Ошибка: {e}")
        return False

def download_figma_assets():
    """Скачивает все ассеты из Figma."""
    print("=" * 60)
    print("📥 Скачивание ассетов из Figma для Монтика")
    print("=" * 60)
    print()

    # Создаём папки
    ensure_dir("figma_assets/skins")
    ensure_dir("figma_assets/barter")
    ensure_dir("figma_assets/raccoon")
    ensure_dir("figma_assets/credits")
    ensure_dir("figma_assets/ui")

    successful = 0
    failed = 0

    for node_id, file_path, description in ASSETS_TO_DOWNLOAD:
        print(f"📦 {description}")

        # Формируем URL для скачивания через Figma API
        # На самом деле, нам нужно использовать правильный endpoint
        # Но поскольку download_assets уже даёт нам URLs, используем их напрямую

        # Временно используем https://www.figma.com/api/mcp/asset/{id}
        # В реальности нужен Figma API с токеном, но для MVP может быть...

        # На самом деле, давайте используем другой подход:
        # Figma CDN позволяет скачивать без токена если есть публичная ссылка

        # Попробуем скачать используя стандартный экспорт
        export_url = f"https://figma.com/api/file/{FIGMA_FILE_KEY}/export"

        # Лучше: используем node ID для экспорта
        # Figma позволяет экспортировать конкретные ноды через публичный API
        figma_export_url = f"https://www.figma.com/api/v1/nodes/{FIGMA_FILE_KEY}"

        # На самом деле, для публичных файлов можно использовать:
        # https://www.figma.com/file/{file_key}/image?ids={node_id}

        image_url = f"https://www.figma.com/file/{FIGMA_FILE_KEY}/image?ids={node_id.replace(':', '%3A')}"

        if download_file(image_url, file_path):
            successful += 1
        else:
            failed += 1

        print()

    print("=" * 60)
    print(f"✅ Успешно: {successful}")
    print(f"❌ Ошибок: {failed}")
    print("=" * 60)
    print()

    if successful > 0:
        print("📁 Файлы сохранены в папку: figma_assets/")
        print()
        print("Следующие шаги:")
        print("1. Проверьте качество скачанных изображений")
        print("2. При необходимости обрежьте или оптимизируйте")
        print("3. Конвертируйте PNG в WebP (для меньшего размера):")
        print("   cwebp image.png -o image.webp")
        print("4. Создайте архив: zip -r figma_assets.zip figma_assets/")
        print("5. Закиньте figma_assets/ папку в GameMontik/app/src/main/res/drawable-nodpi/")

    return successful > 0

def main():
    print()

    # Проверяем наличие requests
    try:
        import requests
    except ImportError:
        print("❌ Ошибка: требуется библиотека 'requests'")
        print()
        print("Установите её командой:")
        print("  pip install requests")
        print()
        return False

    # Скачиваем
    return download_figma_assets()

if __name__ == "__main__":
    success = main()
    exit(0 if success else 1)
