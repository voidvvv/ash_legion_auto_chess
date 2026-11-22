#!/usr/bin/env python3
"""Deterministic pixel-art pilot generator. Python 3 stdlib only; fixed seed 20261122."""
import csv, hashlib, json, math, random, struct, zlib
from pathlib import Path

ROOT = Path(__file__).resolve().parent
SEED = 20261122
UNITS = [
    ("unit_warrior_01", "兽人", "战士", "TRIANGLE"),
    ("unit_wolf_pup", "野兽", "刺客", "DIAMOND"),
    ("unit_skeleton_soldier", "亡灵", "战士", "PENTAGON"),
    ("unit_frost_giant", "巨人", "战士", "HEXAGON"),
]
ANIMS = [("idle",2),("walk",2),("attack",3),("cast",2),("death",3)]
PALETTE = {
 "unit_warrior_01": ((218,218,210),(94,98,101),(48,53,59),(142,105,75)),
 "unit_wolf_pup": ((208,207,198),(105,109,109),(47,53,59),(156,139,115)),
 "unit_skeleton_soldier": ((226,221,203),(119,115,104),(48,51,55),(153,126,83)),
 "unit_frost_giant": ((215,216,210),(105,112,114),(47,52,56),(144,151,149)),
}
class Canvas:
 def __init__(self,w,h,bg=(0,0,0,0)): self.w=w; self.h=h; self.p=[bg]*(w*h)
 def put(self,x,y,c):
  if 0<=x<self.w and 0<=y<self.h: self.p[y*self.w+x]=c if len(c)==4 else (*c,255)
 def rect(self,x0,y0,x1,y1,c):
  for y in range(y0,y1+1):
   for x in range(x0,x1+1): self.put(x,y,c)
 def line(self,pts,c):
  for (x0,y0),(x1,y1) in zip(pts,pts[1:]):
   n=max(abs(x1-x0),abs(y1-y0),1)
   for i in range(n+1): self.put(round(x0+(x1-x0)*i/n),round(y0+(y1-y0)*i/n),c)
 def polygon(self,pts,c):
  ys=[p[1] for p in pts]
  for y in range(max(0,math.floor(min(ys))),min(self.h,math.ceil(max(ys))+1)):
   xs=[]
   for (x1,y1),(x2,y2) in zip(pts,pts[1:]+pts[:1]):
    if y1==y2: continue
    if min(y1,y2)<=y<max(y1,y2): xs.append(x1+(y-y1)*(x2-x1)/(y2-y1))
   xs.sort()
   for a,b in zip(xs[::2],xs[1::2]):
    for x in range(math.ceil(a),math.floor(b)+1): self.put(x,y,c)
 def png(self,path):
  raw=b''.join(b'\0'+bytes(v for px in self.p[y*self.w:(y+1)*self.w] for v in px) for y in range(self.h))
  def chunk(t,d): return struct.pack('>I',len(d))+t+d+struct.pack('>I',zlib.crc32(t+d)&0xffffffff)
  Path(path).parent.mkdir(parents=True,exist_ok=True)
  Path(path).write_bytes(b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('>2I5B',self.w,self.h,8,6,0,0,0))+chunk(b'IDAT',zlib.compress(raw,9))+chunk(b'IEND',b''))
 def blit(self,src,x,y,scale=1,flip=False):
  for sy in range(src.h):
   for sx in range(src.w):
    px=src.p[sy*src.w+sx]
    if px[3]:
     dx=src.w-1-sx if flip else sx
     for yy in range(scale):
      for xx in range(scale): self.put(x+dx*scale+xx,y+sy*scale+yy,px)

def shape_points(fam):
 return {"TRIANGLE":[(15,5),(7,25),(24,25)],"DIAMOND":[(15,4),(25,15),(15,26),(5,15)],"PENTAGON":[(15,4),(25,11),(22,24),(8,24),(5,11)],"HEXAGON":[(9,4),(21,4),(26,11),(21,26),(9,26),(4,11)]}[fam]
def make(unit,fam,variant,anim,frame):
 rng=random.Random(SEED ^ sum(map(ord,unit+anim+str(frame))))
 base,shade,ink,accent=PALETTE[unit]; c=Canvas(32,32); pts=shape_points(fam)
 # Stable silhouette and foot line at y=26 for every frame; animation uses limbs and values only.
 c.polygon(pts,ink); inner=[(15+(x-15)*.78,5+(y-5)*.82) for x,y in pts]; c.polygon(inner,base)
 # recognizable lower-body detail, deliberately below UI-covered upper region
 c.rect(10,19,20,23,shade); c.rect(12,18,18,22,base); c.rect(10,24,13,26,ink); c.rect(18,24,21,26,ink)
 # face/eyes placed in lower-middle region
 c.put(17,15,ink); c.put(20,15,accent); c.rect(15,17,19,17,shade)
 if variant=="WARRIOR": c.rect(7,6,23,7,accent); c.rect(8,8,22,8,ink)
 elif variant=="MAGE": c.put(15,15,accent); c.put(16,15,accent)
 elif variant=="ASSASSIN": c.line([(8,10),(14,14),(20,20)],ink)
 elif variant=="RANGER": c.rect(14,13,18,18,(0,0,0,0)); c.rect(14,13,18,13,ink); c.rect(14,18,18,18,ink)
 # procedural limbs change posture without moving body/baseline
 delta=frame%2
 if anim=="walk":
  c.rect(9,21,11+delta,24,shade); c.rect(19-delta,21,22,24,shade)
 elif anim=="attack":
  c.rect(21,14,26,15,accent); c.rect(24,13,27,13,ink)
 elif anim=="cast":
  c.put(24,13,accent); c.put(25,12,accent)
 elif anim=="death":
  if frame==1: c.rect(6,25,25,26,shade)
  elif frame==2: c.polygon([(5,24),(25,24),(23,27),(7,27)],shade)
 # subtle one-pixel frame shading, no global translations/scaling
 if anim=="idle" and frame==1: c.put(23,10,accent)
 return c
def tint(src,t):
 c=Canvas(src.w,src.h)
 for i,p in enumerate(src.p): c.p[i]=(int(p[0]*t[0]/255),int(p[1]*t[1]/255),int(p[2]*t[2]/255),p[3])
 return c
def main():
 random.seed(SEED); out=ROOT/'units'; rows=[]; allkeys=[]
 for unit,race,cls,fam in UNITS:
  variant={"战士":"WARRIOR","法师":"MAGE","刺客":"ASSASSIN","游侠":"RANGER"}.get(cls,"PLAIN")
  frames=[]
  for anim,n in ANIMS:
   for i in range(n):
    key=f"{unit}_{anim}_{i}"; c=make(unit,fam,variant,anim,i); p=out/(key+'.png'); c.png(p); frames.append(c); allkeys.append(key)
    rows.append(dict(key=key,file=p.relative_to(ROOT).as_posix(),w=32,h=32,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),race=race,**{"class":cls},variant=variant,batch="pilot"))
  sheet=Canvas(32*12,32)
  for i,c in enumerate(frames): sheet.blit(c,i*32,0)
  sheet.png(ROOT/'previews'/f'{unit}_contact.png')
  thumb=Canvas(24*12,24)
  for i,c in enumerate(frames):
   # nearest-neighbor 32->24 by integer source sampling
   for y in range(24):
    for x in range(24): thumb.put(i*24+x,y,c.p[(y*32//24)*32+(x*32//24)])
  thumb.png(ROOT/'previews'/f'{unit}_24px.png')
  tri=Canvas(32*3,32)
  for j,t in enumerate(((209,209,199),(250,199,56),(184,102,245))): tri.blit(tint(frames[0],t),j*32,0)
  tri.png(ROOT/'previews'/f'{unit}_star_tints.png')
  sim=Canvas(32*2+8,40,(38,40,46,255)); sim.blit(frames[0],4,4,flip=False); sim.blit(frames[0],40,4,flip=True)
  sim.rect(3,3,36,3,(255,77,65)); sim.rect(3,3,3,36,(255,77,65)); sim.rect(3,36,36,36,(255,77,65)); sim.rect(36,3,36,36,(255,77,65))
  sim.png(ROOT/'previews'/f'{unit}_redframe_flipx.png')
 with (ROOT/'manifest.csv').open('w',newline='',encoding='utf-8') as f:
  w=csv.DictWriter(f,fieldnames=['key','file','w','h','sha256','race','class','variant','batch']); w.writeheader(); w.writerows(rows)
 (ROOT/'pilot_keys.json').write_text(json.dumps(allkeys,indent=2),encoding='utf-8')
 (ROOT/'AI_GENERATION.md').write_text('# AI 生成声明\n\n本批素材由 Codex（OpenAI，GPT-6）按本地确定性 Python 像素绘制脚本生成；没有调用图像生成模型或外部素材。工具：Python 3 标准库。模型本身不提供独立素材授权；产物为原创程序化几何绘制，未引入第三方素材，适合项目商用使用。\n',encoding='utf-8')
 print(f'Generated {len(rows)} PNGs; seed={SEED}; manifest={ROOT/"manifest.csv"}')
if __name__=='__main__': main()
