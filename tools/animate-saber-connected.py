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
    """Connected, integer-pixel pose rig tuned to the supplied blonde reference.

    Head bob and leg phases follow that reference. Arm arcs are conservative
    authored approximations: colored attack overlays are not skeletal data.
    """
    if frame == 0:
        return source.copy(), {'pose': 'original', 'clipped_pixels': 0}
    palette = sorted({source.getpixel((x,y)) for y in range(H) for x in range(W)
                      if source.getpixel((x,y))[3]})
    if 8 <= frame <= 12:
        # Use the supplied grave silhouette instead of shrinking/rotating the face.
        out = Image.new('RGBA',(W,H))
        silver = [c for c in palette if max(c[:3])-min(c[:3]) < 65 and c[2]>=c[0]]
        silver.sort(key=lambda c: sum(c[:3]))
        colors = sorted({reference[frame].getpixel((x,y)) for y in range(H)
                         for x in range(W) if reference[frame].getpixel((x,y))[3]},
                        key=lambda c: sum(c[:3]))
        mapping = {c: silver[round(i*(len(silver)-1)/max(1,len(colors)-1))]
                   for i,c in enumerate(colors)}
        for y in range(H):
            for x in range(W):
                c=reference[frame].getpixel((x,y))
                if c[3]: out.putpixel((x,y),mapping[c])
        return out, {'pose':'reference grave silhouette / original armor palette', 'clipped_pixels':0}
    measured = motion(reference[0], reference[frame], (0,0,12,7), 1)
    bob=max(0,min(1,measured[1]))
    # (x, lift): two feet alternate support, all offsets within one source pixel.
    walk={2:((0,0),(0,1)), 3:((-1,0),(0,1)), 4:((0,1),(1,0)),
          5:((0,1),(0,0)), 6:((0,1),(-1,0)), 7:((1,0),(0,1))}
    feet=walk.get(frame,((0,0),(0,0)))
    if frame==18: feet=((0,1),(0,1))
    # Distal displacement, blended from fixed shoulder at row 8.
    arms={13:((-1,0),(0,0)),14:((-2,-2),(0,0)),15:((2,-1),(0,0)),
          16:((1,-1),(-1,-1)),17:((1,0),(-1,0)),
          19:((0,0),(-1,-1)),20:((0,0),(-2,-2))}
    left,right=arms.get(frame,((0,0),(0,0)))
    out=Image.new('RGBA',(W,H))
    # Render trunk + skirt in inverse coordinates: no holes from moving blocks.
    for y in range(8+bob,13):
        sy=8 + round((y-(8+bob))*4/max(1,4-bob))
        for x in range(4,8):
            out.putpixel((x,y),source.getpixel((x,min(12,sy))))
    # Feet remain anchored at their upper edge; shift increases toward the boot.
    for side,(dx,lift) in enumerate(feet):
        x0,x1=(0,6) if side==0 else (6,12)
        for y in range(12,15-lift):
            sy=min(14,y+lift)
            shift=dx if y>=13 else 0
            for x in range(x0,x1):
                c=source.getpixel((x,sy))
                if c[3] and 0<=x+shift<W: out.putpixel((x+shift,y),c)
    # Skirt outer folds follow the foot phase by one pixel only at the hem.
    for x0,x1,arm in [(0,4,left),(8,12,right)]:
        for sy in range(8,13):
            t=max(0,min(1,(sy-8)/3))
            dx=round(arm[0]*t);dy=round(arm[1]*t)
            for x in range(x0,x1):
                c=source.getpixel((x,sy))
                if not c[3]: continue
                xx=max(0,min(11,x+dx)); yy=max(0,min(14,sy+bob+dy))
                out.putpixel((xx,yy),c)
                # Fill vertical gaps caused by an upward forearm arc with its own pixel.
                if sy>8 and dy<0 and yy+1<15 and not out.getpixel((xx,yy+1))[3]:
                    out.putpixel((xx,yy+1),c)
    # Rigid head/face/ahoge drawn last. No flipping facial features during idle.
    for y in range(8):
        for x in range(W):
            c=source.getpixel((x,y))
            if c[3]: out.putpixel((x,y+bob),c)
    return out, {'head_bob':bob,'measured_head_offset':measured,
                 'feet':feet,'arms':[left,right],'clipped_pixels':0,
                 'note':'Reference-guided constrained pose, not exact motion capture'}


def background(frame, scale=16):
    tile = Image.new('RGBA',(W,H),(34,39,47,255))
    tile.alpha_composite(frame)
    return tile.convert('RGB').resize((W*scale,H*scale),Image.Resampling.NEAREST)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('source',type=Path,help='Original transparent 12x15 Saber PNG')
    p.add_argument('--reference',type=Path,required=True,help='User supplied 256x128 sheet; top row is used')
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
    if ref.size!=(256,128): p.error('Expected a 256x128 reference sheet')
    reference=[ref.crop((n*W,0,n*W+W,H)) for n in range(21)]
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
        draw.text((x+8,y+4),f'{n:02} REFERENCE / SABER',fill='white')
        board.paste(background(reference[n]),(x+8,y+24))
        board.paste(background(frames[n]),(x+8,y+272))
    board.save(a.output/'comparison-16x.png')
    for name,(indices,duration) in SEQUENCES.items():
        images=[background(frames[n]) for n in indices]
        # GIF time is quantized to 10 ms; attack 66.7ms -> 70ms, operate 125ms -> 130ms.
        images[0].save(a.output/f'{name}.gif',save_all=True,append_images=images[1:],
                       duration=duration,loop=0,disposal=2,optimize=False)
    report={'regions':boxes,'frames':reports,'sequences':SEQUENCES,
            'notes':['Constrained pose rig: head motion is measured; limb arcs are reference-guided approximations.',
                     'Death uses the supplied grave silhouette with source armor colors.',
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
