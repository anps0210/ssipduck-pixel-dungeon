#!/usr/bin/env python3
"""Generate Roland suit animation from a 12x15 PNG using the accepted reference. Requires Pillow."""
import argparse
import json
from pathlib import Path
from PIL import Image, ImageDraw

W, H = 12, 15
APPROVED_POSES = None
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
    'jacket_hem': [3, 10, 9, 12],
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
    """Measure reference motion using RGB patches and silhouette mismatches."""
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
    """Use the accepted reference timing with a suit-specific connected pixel rig."""
    if frame == 0:
        return source.copy(), {'pose': 'unaltered original', 'clipped_pixels': 0}
    if 8 <= frame <= 12:
        # A shared tombstone effect, copied from the supplied approved Saber sheet.
        # Do not map stone highlights to a black suit or a pure-white shirt.
        return reference[frame].copy(), {'pose':'approved Saber tombstone','clipped_pixels':0}
    pose = APPROVED_POSES[frame]
    bob = pose['head_bob']
    measured = pose['measured_head_offset']
    feet = pose['feet']
    offsets = pose['arms']
    if frame == 1:
        return source.copy(), {'head_bob':0,'feet':feet,'arms':offsets,
                               'clipped_pixels':0,'pose':'accepted idle; no invented blink'}
    out=Image.new('RGBA',(W,H))
    # Fixed hips and connected trousers. Shift only the distal row, never the
    # whole leg as a floating block. Lift by folding the lower leg at the knee.
    for x in range(W): out.putpixel((x,12),source.getpixel((x,12)))
    for start,(dx,lift) in zip((2,7),feet):
        for y in range(13,15-lift):
            shift=dx if y==14 else 0
            sy=y+lift
            for x in range(start,start+3):
                out.putpixel((x+shift,y),source.getpixel((x,sy)))
    # Translate the upper jacket rigidly: collar and tie pixels are not resampled.
    out.alpha_composite(source.crop((3,8,9,12)),(3,8+bob))
    for start,end,offset in [(0,3,offsets[0]),(9,12,offsets[1])]:
        for sy in range(8,12):
            points=[x for x in range(start,end) if source.getpixel((x,sy))[3]]
            if not points: continue
            t=(sy-8)/3
            dx=round(offset[0]*t);dy=round(offset[1]*t)
            # Keep each source row intact at the edge instead of clipping fingers.
            dx=max(-min(points),min(W-1-max(points),dx))
            yy=sy+bob+dy
            for x in points:
                c=source.getpixel((x,sy))
                out.putpixel((x+dx,yy),c)
                if sy>8 and dy<0 and yy+1<H and not out.getpixel((x+dx,yy+1))[3]:
                    out.putpixel((x+dx,yy+1),c)
    out.alpha_composite(source.crop((0,0,W,8)),(0,bob))
    return out, {'head_bob':bob,'measured_head_offset':measured,'feet':feet,'arms':offsets,
                 'clipped_pixels':0,'note':'Accepted Saber schedule; original suit pixels, anchored knees, rigid tie'}


def background(frame, scale=16):
    tile = Image.new('RGBA',(W,H),(34,39,47,255))
    tile.alpha_composite(frame)
    return tile.convert('RGB').resize((W*scale,H*scale),Image.Resampling.NEAREST)


def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('source',type=Path,help='Original transparent 12x15 Roland PNG')
    p.add_argument('--reference',type=Path,required=True,help='Approved Saber 256x128 sheet from the user ZIP')
    p.add_argument('--output',type=Path,required=True,help='New output directory (must not exist)')
    p.add_argument('--regions',type=Path,help='Optional JSON region boxes; coordinates have exclusive right/bottom')
    p.add_argument('--poses',type=Path,default=Path(__file__).resolve().parent/'sprite-inputs/saber-motion-reference.json',help='motion-report.json from the approved Saber ZIP')
    a=p.parse_args()
    global APPROVED_POSES
    APPROVED_POSES=json.loads(a.poses.read_text())['frames']
    if len(APPROVED_POSES)!=21: p.error('Expected 21 approved poses')
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
    # Seven identical armor rows intentionally keep the same Roland outfit.
    sheet=Image.new('RGBA',(256,128))
    for tier in range(7):
        for n,im in enumerate(frames): sheet.paste(im,(n*W,tier*H))
    sheet.save(a.output/'roland-sheet.png')
    board=Image.new('RGB',(7*208,3*520),(22,26,33)); draw=ImageDraw.Draw(board)
    for n in range(21):
        x,y=(n%7)*208,(n//7)*520
        draw.text((x+8,y+4),f'{n:02} REFERENCE / ROLAND',fill='white')
        board.paste(background(reference[n]),(x+8,y+24))
        board.paste(background(frames[n]),(x+8,y+272))
    board.save(a.output/'comparison-16x.png')
    for name,(indices,duration) in SEQUENCES.items():
        images=[background(frames[n]) for n in indices]
        # GIF time is quantized to 10 ms; attack 66.7ms -> 70ms, operate 125ms -> 130ms.
        images[0].save(a.output/f'{name}.gif',save_all=True,append_images=images[1:],
                       duration=duration,loop=0,disposal=2,optimize=False)
    report={'regions':boxes,'frames':reports,'sequences':SEQUENCES,
            'notes':['Reference pose schedule, adapted to Roland jacket and trousers; head and tie stay pixel-exact; idle matches the supplied original.',
                     'Death uses the approved Saber tombstone pixels without palette remapping.',
                     'GIFs repeat for inspection, including one-shot game animations.',
                     'All seven armor rows share one outfit.']}
    (a.output/'motion-report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
    # Invariants: source frame is exact and no new opaque colors were introduced.
    assert frames[0].tobytes()==source.tobytes()
    palette={source.getpixel((x,y)) for y in range(H) for x in range(W) if source.getpixel((x,y))[3]}
    assert all(im.getpixel((x,y)) in palette for n,im in enumerate(frames) if not 8<=n<=12 for y in range(H) for x in range(W) if im.getpixel((x,y))[3])
    print(f'Created 21 frames, 256x128 sheet, comparison and 7 GIFs: {a.output}')
    if any(r['clipped_pixels'] for r in reports):
        print('Inspect motion-report.json: some pixels reached the cell boundary.')

if __name__=='__main__':
    main()
