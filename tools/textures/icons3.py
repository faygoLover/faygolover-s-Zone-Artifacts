exec(open(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'icons2.py')).read().split("import sys\nfor f in sys.argv")[0])
import math, random
def disc(im,cx,cy,r,col,t=1.0):
    for y in range(16):
        for x in range(16):
            if math.hypot(x-cx,y-cy)<=r: glowput(im,x,y,col,t)
def ring(im,cx,cy,r,col,t=1.0,w=0.5):
    for y in range(16):
        for x in range(16):
            if abs(math.hypot(x-cx,(y-cy)*1.8)-r)<w: glowput(im,x,y,col,t)
def tryasina():
    im=medallion()
    for y in range(16):
        for x in range(16):
            if y>=7 and inside(x,y,5.3):
                put(im,x,y,(0x4a,0x40,0x30) if (x+y)%4 else (0x55,0x4a,0x38))
    ring(im,7.5,9.5,2.2,(0xb8,0xae,0x96),0.8,0.45)
    ring(im,7.5,9.5,4.0,(0x9a,0x90,0x7a),0.6,0.45)
    for (x,y) in [(6,7),(7,7),(8,7),(9,7)]: put(im,x,y,(0x6a,0x5e,0x48))
    put(im,7,5,(0xd8,0xd0,0xc0)); put(im,8,6,(0xc0,0xb8,0xa8))
    im.save(OUT+'tryasina_placer.png')
def dymka():
    im=medallion(); tint_inside(im,(70,74,78),0.8)
    for y,(x0,x1,c) in {5:(4,11,(0xb0,0xb4,0xb8)),6:(3,9,(0xc8,0xcc,0xd0)),8:(5,12,(0xd8,0xdc,0xe0)),9:(4,10,(0xb8,0xbc,0xc0)),11:(3,11,(0xa8,0xac,0xb0)),12:(6,10,(0xc0,0xc4,0xc8))}.items():
        for x in range(x0,x1+1): glowput(im,x,y,c,0.85 if (x+y)%3 else 0.6)
    im.save(OUT+'dymka_placer.png')
def sumrak():
    im=medallion()
    for y in range(16):
        for x in range(16):
            d=math.hypot(x-7.5,y-7.5)
            if d<=5.3:
                a=math.atan2(y-7.5,x-7.5)+d*0.7
                v=0.5+0.5*math.sin(a*3)
                c=(int(4+14*v*(d/5.3)),int(4+10*v*(d/5.3)),int(8+26*v*(d/5.3)))
                put(im,x,y,c)
    for (x,y) in [(4,5),(11,9),(9,4)]: glowput(im,x,y,(0x50,0x40,0x70),0.5)
    im.save(OUT+'sumrak_placer.png')
def psi_zone():
    im=medallion(); tint_inside(im,(40,20,60),0.8)
    for r,t in [(1.2,1.0),(2.6,0.75),(4.0,0.5)]:
        for y in range(16):
            for x in range(16):
                if abs(math.hypot(x-7.5,y-7.5)-r)<0.45: glowput(im,x,y,(0xc0,0x70,0xff),t)
    glowput(im,7,7,(0xff,0xe0,0xff),1.0); glowput(im,8,8,(0xf0,0xc0,0xff),1.0)
    glowput(im,4,8,(0x60,0xe0,0xff),0.6); glowput(im,11,7,(0xff,0x60,0x80),0.6)
    im.save(OUT+'psi_zone_placer.png')
def poppy_field():
    im=medallion(); tint_inside(im,(30,50,25),0.7)
    for y in range(8,13): put(im,7,y,(0x3a,0x7a,0x2a))
    put(im,8,10,(0x4a,0x8a,0x30)); put(im,9,9,(0x4a,0x8a,0x30))
    for (x,y,c) in [(6,5,(0xd0,0x1c,0x10)),(7,4,(0xe8,0x2a,0x18)),(8,5,(0xd0,0x1c,0x10)),(9,6,(0xb8,0x14,0x0c)),(5,6,(0xb8,0x14,0x0c)),(6,7,(0xc8,0x18,0x0e)),(8,7,(0xc8,0x18,0x0e)),(7,6,(0x20,0x14,0x10)),(6,6,(0xe8,0x30,0x20)),(8,6,(0xe0,0x26,0x18)),(7,7,(0xa0,0x10,0x08)),(7,5,(0xf0,0x40,0x28))]:
        put(im,x,y,c)
    for (x,y) in [(4,10),(11,11),(10,4)]: glowput(im,x,y,(0xe0,0x30,0x20),0.8)
    im.save(OUT+'poppy_field_placer.png')
def rust():
    im=medallion(); rnd=random.Random(4)
    for y in range(16):
        for x in range(16):
            if inside(x,y,5.3):
                v=rnd.random()
                c=[(0x5a,0x2e,0x17),(0x7a,0x40,0x1e),(0x9a,0x55,0x28),(0xb0,0x68,0x33)][int(v*4)]
                if rnd.random()<0.12: c=(0x24,0x22,0x2e)
                put(im,x,y,c)
    for y in range(16):
        for x in range(16):
            d=math.hypot(x-8.5,y-9)
            if d<2.4: glowput(im,x,y,(0xc0,0x20,0x10),0.9*(1-d/2.4)+0.1)
    glowput(im,8,9,(0xff,0x70,0x30),0.9)
    im.save(OUT+'rust_placer.png')
def ezhik():
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    G=[(0x5a,0x5a,0x60),(0x7a,0x7a,0x80),(0x98,0x98,0x9e),(0x40,0x40,0x46)]
    for x in range(1,15): im.putpixel((x,14),G[0]+(255,)); im.putpixel((x,15),G[3]+(255,))
    spikes=[(7,3,2),(4,6,1),(10,5,1),(2,9,1),(12,9,1),(6,8,1),(9,8,1),(5,11,1),(10,11,1)]
    for (x,top,w) in spikes:
        for y in range(top,14):
            ww=max(0,int((y-top)/3))+ (1 if w>1 else 0)
            for dx in range(-ww,ww+1):
                xx=x+dx
                if 0<=xx<16:
                    c=G[2] if dx<0 else (G[1] if dx==0 else G[0])
                    im.putpixel((xx,y),c+(255,))
        im.putpixel((x,top),(0xc0,0xc0,0xc6,255))
    im.save(OUT+'ezhik.png')
import sys
for f in sys.argv[1:]: globals()[f]()
