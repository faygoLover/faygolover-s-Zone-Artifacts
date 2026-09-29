from PIL import Image
import math, random, sys
import os
OUT=os.path.join(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','src','main','resources','assets','fl_zone_arts'),'textures','item')+os.sep
def medallion():
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    for y in range(16):
        for x in range(16):
            d=math.hypot(x-7.5,y-7.5)
            if d<=7.0: im.putpixel((x,y),(0x14,0x14,0x16,255))
            if d<=6.2: im.putpixel((x,y),(0x40,0x40,0x50,255))
            if d<=5.3: im.putpixel((x,y),(0x20,0x22,0x2e,255))
    return im
def inside(x,y,r=5.3): return math.hypot(x-7.5,y-7.5)<=r
def put(im,x,y,c):
    if 0<=x<16 and 0<=y<16 and inside(x,y,5.6): im.putpixel((x,y),c+(255,) if len(c)==3 else c)

def chem():
    im=medallion(); rnd=random.Random(3)
    blobs=[(7.5,7.5,3.4,(0x7a,0x8a,0x2c)),(6,6.5,2.2,(0xaf,0xc4,0x3a)),(9,8.5,2.0,(0xaf,0xc4,0x3a)),(7,9,1.6,(0x4c,0x56,0x1c)),(8.5,6,1.3,(0xd9,0xe2,0x50)),(6.2,6,0.9,(0xe8,0xf0,0x80))]
    for bx,by,br,c in blobs:
        for y in range(16):
            for x in range(16):
                if math.hypot(x-bx,y-by)<=br: put(im,x,y,c)
    put(im,7,12,(0xaf,0xc4,0x3a)); put(im,7,13,(0x7a,0x8a,0x2c))
    im.save(OUT+'chem_comet_placer.png')
def gravi():
    im=medallion()
    pts=[(4,11,1.6),(7,8,1.9),(10.5,5,2.3)]
    for i,(cx,cy,r) in enumerate(pts):
        for y in range(16):
            for x in range(16):
                d=math.hypot(x-cx,y-cy)
                if abs(d-r)<0.55: put(im,x,y,(0x90+i*0x20,0x60+i*0x10,0xe0))
        put(im,int(cx),int(cy),(0xd8,0xc0,0xff))
    im.save(OUT+'gravi_placer.png')
def lift():
    im=medallion()
    for x in range(3,13): put(im,x,11,(0x55,0x5a,0x66))
    for (x,y,c) in [(5,9,(0xc8,0xd0,0xe0)),(8,7,(0xe0,0xe6,0xf0)),(10,9,(0xb0,0xb8,0xc8)),(6,5,(0x98,0xa0,0xb0)),(9,4,(0xc8,0xd0,0xe0)),(7,10,(0x90,0x98,0xa8))]:
        put(im,x,y,c)
    for y in (6,7,8):
        put(im,11,y,(0x70,0x78,0x88))
    put(im,11,5,(0xa0,0xa8,0xb8)); put(im,10,6,(0x70,0x78,0x88)); put(im,12,6,(0x70,0x78,0x88))
    im.save(OUT+'lift_placer.png')
def amoeba():
    im=medallion()
    for y in range(16):
        for x in range(16):
            d=math.hypot((x-7.5)*0.8,(y-9)*1.4)
            if d<=3.8: put(im,x,y,(0x6f,0x8a,0x3a))
            if d<=2.4: put(im,x,y,(0x9a,0xb0,0x5a))
    for (x,y) in [(4,6),(11,6),(3,8),(12,9),(7,4)]: put(im,x,y,(0x8f,0xa8,0x4a))
    put(im,6,8,(0xd8,0xa0,0xa0)); put(im,9,9,(0xc8,0x90,0x90)); put(im,7,9,(0xe0,0xe8,0xb0))
    im.save(OUT+'amoeba_placer.png')
def pukh():
    im=medallion()
    for x in range(3,13): put(im,x,3,(0x6a,0x5a,0x30)); put(im,x,4,(0x8a,0x78,0x40))
    rnd=random.Random(5)
    for x in range(3,13):
        L=rnd.randint(3,8)
        for y in range(5,5+L):
            c=(0xc8,0xc8,0xc0) if (x+y)%2 else (0x98,0x98,0x90)
            if rnd.random()<0.8: put(im,x,y,c)
    im.save(OUT+'pukh_placer.png')
def pukh_flesh():
    im=Image.new('RGBA',(16,16),(0,0,0,0)); rnd=random.Random(9)
    for y in range(16):
        for x in range(16):
            d=math.hypot((x-7.5)*0.9,(y-8)*1.2)
            if d<=5.5+0.8*math.sin(x*1.3+y):
                base=(0x6a,0x5c,0x2c) if rnd.random()<0.6 else (0x7e,0x6e,0x34)
                if abs(math.sin(x*0.9+y*0.7))<0.18: base=(0xa8,0x90,0x40)
                if d>4.6: base=(0x4a,0x3e,0x1e)
                im.putpixel((x,y),base+(255,))
    im.save(OUT+'pukh_flesh.png')

def kisel():
    im=medallion()
    for y in range(16):
        for x in range(16):
            d=math.hypot((x-7.5)*0.75,(y-9.5)*1.5)
            if d<=4.2: put(im,x,y,(0x1f,0x5c,0x0e))
            if d<=3.2: put(im,x,y,(0x2f,0x8c,0x16))
            if d<=1.8: put(im,x,y,(0x6c,0xff,0x2e))
    for (x,y,c) in [(5,6,(0x8c,0xff,0x50)),(9,5,(0xb0,0xff,0x80)),(7,4,(0x6c,0xe0,0x2e)),(10,8,(0xd0,0xff,0xa0)),(5,9,(0xb0,0xff,0x80))]:
        put(im,x,y,c)
    im.save(OUT+'kisel_placer.png')
def acid_fog():
    im=medallion(); rnd=random.Random(11)
    for y in range(16):
        for x in range(16):
            if y>=8:
                w=0.5+0.5*math.sin(x*0.9+y*1.7)
                c=(int(0x5c+0x30*w),int(0x7a+0x36*w),int(0x2e+0x22*w))
                if rnd.random()<0.9: put(im,x,y,c)
    for y in range(3,9):
        put(im,6,y,(0xa8,0xd0,0x60) if y%2 else (0xc8,0xec,0x8a))
        if y>4: put(im,5+(y%2),y,(0x86,0xae,0x44))
    for y in range(5,9): put(im,10,y,(0x86,0xae,0x44))
    put(im,10,4,(0xc8,0xec,0x8a)); put(im,4,4,(0xd0,0xe8,0x60)); put(im,8,3,(0xd0,0xe8,0x60))
    im.save(OUT+'acid_fog_placer.png')
for f in sys.argv[1:]: globals()[f]()
