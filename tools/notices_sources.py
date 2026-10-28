"""Kairo98's third-party notices: the sources shared/tools/notices.py writes
kairo98/src/main/assets/THIRD_PARTY_NOTICES.txt from. Run it again after an import."""
from notices import text

OUT = 'kairo98/src/main/assets/THIRD_PARTY_NOTICES.txt'

HEADER = '''Kairo98 third-party notices
These notices apply to the emulator code, generated font, and statically linked C++ runtime in this package.'''

# (title, file under third_party/, its encoding): Neko Project's licenses are Shift-JIS, Shinonome's EUC-JP.
FILES = [
    ('Neko Project 21/W rev104, base license', 'np21w/LICENSES/LICENSE.TXT', 'cp932'),
    ('Neko Project 21/W IA-32 core', 'np21w/LICENSES/LICENSE-I386C.TXT', 'cp932'),
    ('Neko Project 21/W IA-32 FPU stubs', 'np21w/LICENSES/LICENSE-I386C-FPU.TXT', 'cp932'),
    ('Neko Project 21/W SIMD stubs', 'np21w/LICENSES/LICENSE-I386C-SIMD.TXT', 'cp932'),
    ('Neko Project 21/W LIO', 'np21w/LICENSES/LICENSE-LIO.TXT', 'cp932'),
    ('ymfm', 'ymfm/LICENSE', 'utf-8'),
    ('Spleen 8x16 bitmap font', 'spleen/LICENSE', 'utf-8'),
    ('Shinonome 16-dot bitmap fonts (public domain)', 'shinonome/LICENSE', 'euc_jp'),
    ('Android NDK 28.2.13676358 LLVM toolchain and statically linked C++ runtime',
     'android-ndk-llvm-28.2.13676358/NOTICE', 'utf-8'),
]

# (title, source, [text]) in the order shown.
SECTIONS = [(title, f'third_party/{path}', [text(path, encoding)]) for title, path, encoding in FILES]
