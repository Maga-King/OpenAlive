"""Extract the three original Flyme 9 video wallpapers without transcoding.

Usage: python tools/import_flyme9_video.py build/flyme9-common-1.8.15.apk
All output is confined to app/assets/flyme-video. The loop endpoints and
activity policy come from sdkstage 1.8.15's video/v17/{a,b,c} and video/h.
"""
import argparse
import hashlib
import json
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEST = ROOT / 'app/assets/flyme-video'
VARIANTS = (
    (401, 'langliu', '浪流', 'The wave', 9_983_333, 600, 10_000_000),
    (402, 'shenqian', '深潜', 'Deep diving', 8_000_000, 481, 8_016_667),
    (403, 'lianyi', '涟漪', 'The ripple', 9_950_000, 598, 9_966_667),
)


def digest(data):
    return hashlib.sha256(data).hexdigest()


def json_bytes(value):
    return (json.dumps(value, ensure_ascii=False, indent=2) + '\n').encode('utf-8')


def extract(apk_path, check=False):
    # Stage every byte in memory first: an incomplete/wrong input cannot leave
    # half an updated resource set behind.
    output = {}
    provenance = {
        'source': 'Flyme 9 sdkstage 1.8.15 (com.meizu.flyme.sdkstage)',
        'apk_sha256': digest(apk_path.read_bytes()),
        'files': {},
        'behavior_sources': [
            'com.meizu.flyme.sdkstage.wallpaper.video.h',
            'com.meizu.flyme.sdkstage.wallpaper.video.h$3',
            'com.meizu.flyme.sdkstage.wallpaper.video.h$3$1',
            'com.meizu.flyme.sdkstage.wallpaper.video.c',
            'com.meizu.flyme.sdkstage.wallpaper.video.d',
            'com.meizu.flyme.sdkstage.wallpaper.video.g',
            'com.meizu.flyme.sdkstage.wallpaper.video.l',
            'com.meizu.flyme.sdkstage.wallpaper.video.m',
            'com.meizu.flyme.sdkstage.wallpaper.video.v17.a',
            'com.meizu.flyme.sdkstage.wallpaper.video.v17.b',
            'com.meizu.flyme.sdkstage.wallpaper.video.v17.c',
        ],
    }
    catalogue = []
    with zipfile.ZipFile(apk_path) as apk:
        def copy(original, target):
            data = apk.read(original)
            output[target] = data
            provenance['files'][target] = {
                'original': original, 'bytes': len(data), 'sha256': digest(data),
            }

        for index, (variant, slug, name, english, endpoint, frames, duration) in enumerate(VARIANTS, 1):
            movie = f'mz_video_wallpaper_v{index}_1080.mp4'
            thumbnail = f'{slug}.png'
            copy(f'assets/video/{movie}', movie)
            copy(f'res/drawable-nodpi-v4/mz_wallpaper_video_v{index}.png', thumbnail)
            catalogue.append({
                'id': variant, 'slug': slug, 'name': name, 'english_name': english,
                'video': movie, 'thumbnail': thumbnail,
                'loop_start_us': 0, 'loop_end_us': endpoint,
                'width': 1080, 'height': 2340, 'frame_rate': 60,
                'frame_count': frames, 'duration_us': duration,
                'video_codec': 'h264', 'all_frames_sync': True, 'audio': False,
            })
        for shader in ('video_vertex.glsl', 'video_frag.glsl'):
            copy(f'assets/video/{shader}', shader)
        # The shared helper has a different dark transform. Retain the exact
        # APK shaders, then redirect only this derived fragment's include.
        copy('assets/shader/base/dark_utlis.glsl', 'dark_utlis.glsl')
    original_include = b'#include <shader/base/dark_utlis.glsl>'
    fragment = output['video_frag.glsl']
    if fragment.count(original_include) != 1:
        raise ValueError('Unexpected original video fragment include')
    output['video_runtime_frag.glsl'] = fragment.replace(
        original_include, b'#include <flyme-video/dark_utlis.glsl>')
    output['thumbnail_runtime_frag.glsl'] = output['video_runtime_frag.glsl'].replace(
        b'#extension GL_OES_EGL_image_external_essl3 : require', b'').replace(
        b'samplerExternalOES', b'sampler2D')
    provenance['derived_files'] = {'video_runtime_frag.glsl': {
        'original': 'assets/video/video_frag.glsl',
        'change': 'Redirect dark helper include to the byte-identical private original',
        'sha256': digest(output['video_runtime_frag.glsl']),
    }, 'thumbnail_runtime_frag.glsl': {
        'original': 'assets/video/video_frag.glsl',
        'change': 'Use private original dark helper and sampler2D for the official loading thumbnail',
        'sha256': digest(output['thumbnail_runtime_frag.glsl']),
    }}
    output['catalog.json'] = json_bytes(catalogue)
    output['playback.json'] = json_bytes({
        'normal_speed': 1.0, 'slowdown_delay_ms': 7000,
        'slowdown_duration_ms': 1000, 'slow_speed': 0.3333,
        'slowdown_interpolator': 'linear', 'stop_after_slowdown': True,
        'render_rate': 'int(speed * 30 + 30)',
        'resume': 'retain source time, restart activity window at normal speed',
        'scene_geometry': 'same video in AOD, lock and home',
        'portrait': 'stretch original quad to viewport',
        'landscape': 'quad height is integer(viewportWidth squared / viewportHeight)',
        'normal_theme_dark_strength': [0, 0, 0],
        'dark_theme_dark_strength': [1, 1, 1],
        'dark_transition_ms': 100,
    })
    output['source.json'] = json_bytes(provenance)
    if check:
        invalid = [name for name, data in sorted(output.items())
                   if not (DEST / name).is_file() or (DEST / name).read_bytes() != data]
        if invalid:
            raise ValueError('Extracted video resources differ: ' + ', '.join(invalid))
        print(f'Verified {len(output)} deterministic files against original APK')
        return
    DEST.mkdir(parents=True, exist_ok=True)
    for name, data in sorted(output.items()):
        path = DEST / name
        if not path.exists() or path.read_bytes() != data:
            path.write_bytes(data)
    print(f'Extracted {len(catalogue)} wallpapers and {len(output)} files to {DEST}')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk', type=Path, help='Original Flyme 9 sdkstage 1.8.15 APK')
    parser.add_argument('--check', action='store_true', help='Check all resources against the APK without writing')
    args = parser.parse_args()
    extract(args.apk.resolve(), args.check)


if __name__ == '__main__':
    main()
