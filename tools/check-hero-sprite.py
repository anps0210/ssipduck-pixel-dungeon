#!/usr/bin/env python3
"""Read-only validation of a replacement for the current 12x15 hero sheet."""
import sys
from pathlib import Path
from PIL import Image

path = Path(sys.argv[1]) if len(sys.argv) > 1 else (
    Path(__file__).resolve().parents[1] / 'core/src/main/assets/sprites/warrior.png'
)
errors = []
with Image.open(path) as source:
    if source.format != 'PNG':
        errors.append('Expected PNG format')
    if source.size != (256, 128):
        errors.append(f'Expected 256x128, got {source.size}')
    sheet = source.convert('RGBA')
    if sheet.getchannel('A').getextrema()[0] != 0:
        errors.append('Missing fully transparent background pixels')
    if source.size == (256, 128):
        for tier in range(7):
            for frame in range(21):
                x, y = frame * 12, tier * 15
                if sheet.crop((x, y, x + 12, y + 15)).getchannel('A').getbbox() is None:
                    errors.append(f'Empty frame: tier {tier}, frame {frame}')
        for box in [(252, 0, 256, 128), (0, 105, 256, 128)]:
            if sheet.crop(box).getchannel('A').getbbox() is not None:
                errors.append(f'Nontransparent padding: {box}')
if errors:
    print('\n'.join(errors), file=sys.stderr)
    sys.exit(1)
print(f'PASS: {path.name}: 256x128 PNG, 147 populated 12x15 cells, transparent padding')
