from PIL import Image
import random, math
W,H=32,128
rnd=random.Random(11)
im=Image.new('RGBA',(W,H),(0,0,0,0)); px=im.load()
def plot(x,y,a=235):
    x=int(round(x))%W; y=int(round(y))
    if 0<=y<H:
        t=y/H
        base=int(150+90*t)
        c=(min(255,base+rnd.randint(-10,8)), min(255,base+rnd.randint(-12,6)), min(255,int(base*0.93)+rnd.randint(-10,6)))
        oa=px[x,y][3]
        if a>oa: px[x,y]=c+(a,)
def line(x0,y0,x1,y1,a=230):
    n=int(max(abs(x1-x0),abs(y1-y0))*1.5)+1
    for k in range(n+1):
        t=k/n; plot(x0+(x1-x0)*t,y0+(y1-y0)*t,a)
def sag(x0,y0,x1,y1,s,a=220):
    n=int(abs(x1-x0)*2)+4
    for k in range(n+1):
        t=k/n; plot(x0+(x1-x0)*t, y0+(y1-y0)*t+s*4*t*(1-t), a)
# anchor strands (sparse), each ends at its own hem; some run long as loose threads
xs=[]; x=rnd.uniform(0,2)
while x<W: xs.append(x); x+=rnd.uniform(3.0,5.5)
ends={}
for sx in xs:
    e=rnd.uniform(0.55,0.85)*H
    if rnd.random()<0.35: e=min(H-2,e+rnd.uniform(10,30))
    ends[sx]=e
    ph=rnd.uniform(0,6)
    for y in range(0,int(e)):
        plot(sx+0.7*math.sin(y*0.12+ph), y, 235 if y<e-4 else 140)
# lace: rows of drooping arcs between neighbouring strands, cells getting bigger lower down
y=5.0
while y<H*0.8:
    for i in range(len(xs)):
        a=xs[i]; b=xs[(i+1)%len(xs)]+(W if i==len(xs)-1 else 0)
        yy=y+rnd.uniform(-2,2)
        if yy < min(ends[xs[i]], ends[xs[(i+1)%len(xs)]])-2 and rnd.random()<0.85:
            sag(a,yy,b,yy+rnd.uniform(-1.5,1.5),rnd.uniform(1.5,3.5+y/H*6))
    y+=rnd.uniform(4,7)+y/H*6
# a few cross threads
for _ in range(25):
    x0=rnd.uniform(0,W); y0=rnd.uniform(3,H*0.6)
    line(x0,y0,x0+rnd.uniform(-4,4),y0+rnd.uniform(2,6),200)
# dense grubby mat at the top
for yy in range(0,6):
    for xx in range(W):
        if rnd.random()<0.8-yy*0.12:
            px[xx,yy]=(105+rnd.randint(0,30),95+rnd.randint(0,25),62+rnd.randint(0,18),255)
import os
im.save(os.path.join(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','src','main','resources','assets','fl_zone_arts'),'textures','block','pukh_lace.png'))
prev=Image.new('RGBA',(W*4*3,H*4),(40,50,70,255))
L=im.resize((W*4,H*4),Image.NEAREST)
for i in range(3): prev.paste(L,(i*W*4,0),L)
prev.save(os.path.join(os.path.dirname(os.path.abspath(__file__)),'lace_prev.png'))
