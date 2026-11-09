"""向 aapt2 生成的 apk 追加 classes.dex（追加模式不改动既有条目）。"""
import sys
import zipfile

apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a') as z:
    z.write(dex, 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
print('added classes.dex -> ' + apk)
