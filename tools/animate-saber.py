#!/usr/bin/env python3
"""Pixel-preserving draft animation transfer. Requires Pillow, never edits inputs."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw

W, H = 12, 15
SEQUENCES = {
    'idle': ([0, 0, 0, 1, 0, 0, 1, 1], 1000),
    'walk': ([2, 3, 4, 5, 6, 7], 50),
    'attack': ([13, 14, 15, 0], 70),
    'death': ([8, 9, 10, 11, 12, 11], 50),
    'operate': ([16, 17, 16, 17], 130),
    'fly': ([18], 1000),
    'read': ([19, 20, 20, 20, 20, 20, 20, 20, 20, 19], 50),
}
# Non-overlapping ownership. Optional JSON can override these for the actual design.
BOXES = {
    'head': [0, 0, 12, 7],
    'torso': [3, 7, 9, 10],
    'left_arm': [0, 7, 3, 12],
    'right_arm': [9, 7, 12, 12],
    'skirt': [3, 10, 9, 12],
    'left_foot': [0, 12, 6, 15],
    'right_foot': [6, 12, 12, 15],
}


def partition(boxes):
    owners = {}
    for name, (x0, y0, x1, y1) in boxes.items():
        if not (0 <= x0 < x1 <= W and 0 <= y0 < y1 <= H):
            raise ValueError(f'Invalid region {name}')
        for y in range(y0, y1):
            for x in range(x0, x1):
                if (x, y) in owners:
                    raise ValueError(f'Overlapping region at {(x, y)}')
                owners[x, y] = name
    if len(owners) != W * H:
        raise ValueError('Regions must cover all 180 pixels exactly once')
    return owners


def motion(base, target, box, radius):
    """Match original warrior RGB patches, including silhouette mismatches."""
    x0, y0, x1, y1 = box
    points = [(x, y) for y in range(y0, y1) for x in range(x0, x1)
              if base.getpixel((x, y))[3]]
    if not points:
        return (0, 0)
    def cost(dx, dy):
        total = 0.0
        for x, y in points:
            if not (0 <= x + dx < W and 0 <= y + dy < H):
                total += 2.0
                continue
            a, b = base.getpixel((x, y)), target.getpixel((x + dx, y + dy))
            total += (sum(abs(a[c] - b[c]) for c in range(3)) / 765
                      if b[3] else 1.5)
        return total / len(points) + .025 * (abs(dx) + abs(dy))
    candidates = [(dx, dy) for dy in range(-radius, radius + 1)
                  for dx in range(-radius, radius + 1)]
    return min(candidates, key=lambda v: (cost(*v), abs(v[0])+abs(v[1]), v))


def transfer(source, reference, boxes, owners, frame):
    if frame == 0:
        return source.copy(), {'motion': {}, 'clipped_pixels': 0}
    # Preserve identity in death frames instead of copying the warrior's disappearing skull.
    if 8 <= frame <= 12:
        angles = [15, 35, 65, 90, 100]
        body = source.crop(source.getbbox())
        body = body.rotate(-angles[frame-8], Image.Resampling.NEAREST, expand=True)
        scale = min(1, W/body.width, H/body.height)
        body = body.resize((max(1, round(body.width*scale)), max(1, round(body.height*scale))),
                           Image.Resampling.NEAREST)
        out = Image.new('RGBA', (W, H))
        out.paste(body, ((W-body.width)//2, H-body.height))
        return out, {'death_rotation': angles[frame-8], 'clipped_pixels': 0,
                     'note': 'Authored fall approximation; not warrior skull copying'}
    offsets = {name: motion(reference[0], reference[frame], box,
                           1 if name in ('head', 'torso') else 3)
               for name, box in boxes.items()}
    # Constrain noisy patch matches to preserve joints and avoid feet hiding in the torso.
    for name, (dx, dy) in list(offsets.items()):
        if name.endswith('_foot'):
            offsets[name] = (max(-1,min(1,dx)), max(-1,min(1,dy)))
        elif name.endswith('_arm'):
            offsets[name] = (max(-2,min(2,dx)), max(-2,min(2,dy)))
    # Translate the entire head rigidly, never recolor facial features or ahoge.
    for name, (dx, dy) in list(offsets.items()):
        points = [(x,y) for (x,y), owner in owners.items()
                  if owner == name and source.getpixel((x,y))[3]]
        if points:
            dx = max(-min(x for x,y in points), min(dx, W-1-max(x for x,y in points)))
            dy = max(-min(y for x,y in points), min(dy, H-1-max(y for x,y in points)))
        offsets[name] = (dx,dy)
    # Keep the trunk and skirt attached to the rigid head: independent patch
    # matches can otherwise split a small face or open holes through the chest.
    if 'head' in offsets:
        for part in ('torso', 'skirt'):
            if part in offsets:
                offsets[part] = offsets['head']
    out = Image.new('RGBA', (W,H))
    clipped = 0
    # Back-to-front layering; head last protects face/hair from arm overlap.
    order = ['torso','skirt','left_foot','right_foot','left_arm','right_arm','head']
    order = [n for n in order if n in boxes] + [n for n in boxes if n not in order]
    for name in order:
        dx,dy = offsets[name]
        for (x,y), owner in owners.items():
            if owner != name or not source.getpixel((x,y))[3]:
                continue
            # The hem follows the difference between the two foot displacements.
            sway = 0
            if name == 'skirt' and frame in range(2,8) and y == boxes[name][3]-1:
                sway = max(-1,min(1,offsets.get('right_foot',(0,0))[0]
                                      - offsets.get('left_foot',(0,0))[0]))
            xx,yy = x+dx+sway,y+dy
            if 0 <= xx < W and 0 <= yy < H:
                out.putpixel((xx,yy), source.getpixel((x,y)))
            else:
                clipped += 1
    return out, {'motion': offsets, 'clipped_pixels': clipped}


def background(frame, scale=16):
    tile = Image.new('RGBA',(W,H),(34,39,47,255))
    tile.alpha_composite(frame)
    return tile.convert('RGB').resize((W*scale,H*scale),Image.Resampling.NEAREST)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('source',type=Path,help='Original transparent 12x15 Saber PNG')
    p.add_argument('--reference',type=Path,default=Path(__file__).resolve().parents[1]/'core/src/main/assets/sprites/warrior.png')
    p.add_argument('--output',type=Path,required=True,help='New output directory (must not exist)')
    p.add_argument('--regions',type=Path,help='Optional JSON region boxes; coordinates have exclusive right/bottom')
    a=p.parse_args()
    if not a.source.is_file(): p.error('Source PNG is missing; the actual original file is required')
    source=Image.open(a.source)
    if source.format!='PNG' or source.size!=(W,H): p.error('Source must be an original 12x15 PNG; no automatic resizing')
    source=source.convert('RGBA')
    if not source.getbbox(): p.error('Source is entirely transparent')
    if source.getchannel('A').getextrema()[0]!=0: p.error('Source needs a transparent background')
    ref=Image.open(a.reference).convert('RGBA')
    if ref.size!=(256,128): p.error('Expected the original 256x128 warrior sheet')
    reference=[ref.crop((n*W,H,n*W+W,2*H)) for n in range(21)]
    boxes=json.loads(a.regions.read_text()) if a.regions else BOXES
    owners=partition(boxes)
    a.output.mkdir(parents=True,exist_ok=False)
    frames=[]; reports=[]
    for n in range(21):
        im,report=transfer(source,reference,boxes,owners,n)
        im.save(a.output/f'frame-{n:02}.png'); frames.append(im); reports.append(report)
    # Seven identical armor rows intentionally keep the same Saber outfit.
    sheet=Image.new('RGBA',(256,128))
    for tier in range(7):
        for n,im in enumerate(frames): sheet.paste(im,(n*W,tier*H))
    sheet.save(a.output/'saber-sheet.png')
    board=Image.new('RGB',(7*208,3*520),(22,26,33)); draw=ImageDraw.Draw(board)
    for n in range(21):
        x,y=(n%7)*208,(n//7)*520
        draw.text((x+8,y+4),f'{n:02} WARRIOR / SABER',fill='white')
        board.paste(background(reference[n]),(x+8,y+24))
        board.paste(background(frames[n]),(x+8,y+272))
    board.save(a.output/'comparison-16x.png')
    for name,(indices,duration) in SEQUENCES.items():
        images=[background(frames[n]) for n in indices]
        # GIF time is quantized to 10 ms; attack 66.7ms -> 70ms, operate 125ms -> 130ms.
        images[0].save(a.output/f'{name}.gif',save_all=True,append_images=images[1:],
                       duration=duration,loop=0,disposal=2,optimize=False)
    report={'regions':boxes,'frames':reports,'sequences':SEQUENCES,
            'notes':['Draft pose transfer: regional matching is heuristic, not anatomical tracking.',
                     'Death is a nearest-neighbor rigid rotation approximation.',
                     'GIFs repeat for inspection, including one-shot game animations.',
                     'All seven armor rows share one outfit.']}
    (a.output/'motion-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    # Invariants: source frame is exact and no new opaque colors were introduced.
    assert frames[0].tobytes()==source.tobytes()
    palette={source.getpixel((x,y)) for y in range(H) for x in range(W) if source.getpixel((x,y))[3]}
    assert all(im.getpixel((x,y)) in palette for im in frames for y in range(H) for x in range(W) if im.getpixel((x,y))[3])
    print(f'Created 21 frames, 256x128 sheet, comparison and 7 GIFs: {a.output}')
    if any(r['clipped_pixels'] for r in reports):
        print('Inspect motion-report.json: some skirt edge pixels reached the cell boundary.')

if __name__=='__main__':
    main()
