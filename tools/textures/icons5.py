exec(open(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'icons4.py')).read().split("import sys\nfor f in sys.argv")[0])
# 0.1.34.1: новые иконки старых аномалий (Жарка, Разлом, Плешь, Воронка, Карусель, Подушка, Тесла, Кометы)
def halo(im,cx,cy,r,col,t):
    for y in range(16):
        for x in range(16):
            d=math.hypot(x-cx,y-cy)
            if d<r: glowput(im,x,y,col,t*(1-d/r))
def ramp(stops,v):
    v=max(0.0,min(1.0,v))
    for i in range(len(stops)-1):
        a,ca=stops[i]; b,cb=stops[i+1]
        if v<=b: return blend(ca,cb,(v-a)/(b-a) if b>a else 0)
    return stops[-1][1]
FIRE=[(0,(0x5a,0x10,0x08)),(0.35,(0xc8,0x30,0x10)),(0.6,(0xff,0x80,0x20)),(0.82,(0xff,0xd0,0x60)),(1,(0xff,0xfa,0xe0))]
ICE=[(0,(0x10,0x28,0x5a)),(0.35,(0x28,0x70,0xc8)),(0.6,(0x70,0xc0,0xff)),(0.82,(0xc0,0xe8,0xff)),(1,(0xff,0xff,0xff))]
def zharka():
    im=medallion(); tint_inside(im,(70,25,10),0.8)
    for y in range(16):
        for x in range(16):
            if not inside(x,y,5.3) or y<3 or y>11: continue
            h=(y-2.5)/9.0
            w=3.3*h**0.75
            cx=7.5+0.7*math.sin(y*1.1)*(1-h)
            d=abs(x-cx)/max(w,0.3)
            if d<1:
                v=(1-d)*(0.35+0.75*h)
                put(im,x,y,ramp(FIRE,v))
    for x in range(3,13):
        put(im,x,12,(0x2a,0x16,0x10) if x%3 else (0x3a,0x1c,0x12))
    for x in (4,8,11): glowput(im,x,12,(0xff,0x70,0x20),0.8)
    for (x,y) in [(4,4),(11,5),(10,3)]: glowput(im,x,y,(0xff,0xa0,0x40),0.7)
    im.save(OUT+'zharka_placer.png')
def _comet(name,pal,tint,spark):
    im=medallion(); tint_inside(im,tint,0.8)
    hx,hy=9.6,5.6
    # хвост к левому нижнему краю
    for y in range(16):
        for x in range(16):
            if not inside(x,y,5.3): continue
            vx,vy=x-hx,y-hy
            along=-(vx*0.72-vy*0.69)
            across=abs(vx*0.69+vy*0.72)
            if along>0:
                w=1.9-along*0.22
                if across<w:
                    v=(1-across/w)*max(0,0.75-along*0.1)
                    if v>0.05: glowput(im,x,y,ramp(pal,v),min(1,0.35+v))
    halo(im,hx,hy,3.4,pal[2][1],0.7)
    for y in range(16):
        for x in range(16):
            d=math.hypot(x-hx,y-hy)
            if d<2.1: put(im,x,y,ramp(pal,1.05-d/2.1*0.7)) if inside(x,y,5.3) else None
    put(im,int(hx)-1,int(hy),pal[4][1]); put(im,int(hx),int(hy)-1,pal[4][1])
    for (x,y) in spark: glowput(im,x,y,pal[3][1],0.8)
    im.save(OUT+name+'_placer.png')
def comet(): _comet('comet',FIRE,(60,25,12),[(4,6),(12,10),(6,12)])
def cold_comet(): _comet('cold_comet',ICE,(15,30,60),[(4,5),(12,10),(5,12),(13,6)])
def tesla():
    im=medallion(); tint_inside(im,(20,40,90),0.85)
    rnd=random.Random(7)
    W=(0xf0,0xfa,0xff); C=(0x98,0xd8,0xff); B=(0x40,0x88,0xf0)
    for k in range(5):
        a=k*2*math.pi/5+0.4
        x,y=7.5,7.5; pts=[]
        for s in range(6):
            a2=a+rnd.uniform(-0.55,0.55)
            x+=math.cos(a2)*1.0; y+=math.sin(a2)*1.0
            pts.append((int(round(x)),int(round(y))))
        for i,(px,py) in enumerate(pts[1:]):
            glowput(im,px,py,C if i<3 else B,0.9-i*0.1)
    halo(im,7.5,7.5,4.2,B,0.8)
    halo(im,7.5,7.5,2.6,C,0.9)
    for y in range(16):
        for x in range(16):
            if math.hypot(x-7.5,y-7.5)<1.3: put(im,x,y,W)
    glowput(im,6,6,(255,255,255),1.0)
    im.save(OUT+'tesla_placer.png')
def _razlom(name,pal,glow,core):
    im=medallion()
    for y in range(16):
        for x in range(16):
            if inside(x,y,5.3): put(im,x,y,(0x2c,0x22,0x1e) if (x*3+y)%5 else (0x36,0x2a,0x24))
    cracks=[[(3,6),(5,7),(7,7),(8,8),(10,8),(12,10)],[(6,3),(7,5),(7,7),(8,8),(8,10),(9,12)],[(4,11),(6,10),(8,8),(10,6),(12,5)]]
    lit=set()
    for cr in cracks:
        for (x0,y0),(x1,y1) in zip(cr,cr[1:]):
            n=max(abs(x1-x0),abs(y1-y0))
            for i in range(n+1):
                lit.add((round(x0+(x1-x0)*i/n),round(y0+(y1-y0)*i/n)))
    for (x,y) in lit:
        for dx,dy in [(-1,0),(1,0),(0,-1),(0,1)]:
            if (x+dx,y+dy) not in lit: glowput(im,x+dx,y+dy,glow,0.3)
    for (x,y) in lit:
        d=math.hypot(x-8,y-8)
        put(im,x,y,ramp(pal,0.85-d*0.09))
    halo(im,8,7,1.8,pal[2][1],0.6)
    put(im,8,7,core[0]); put(im,8,6,core[1]); glowput(im,8,5,pal[2][1],0.7)
    im.save(OUT+name+'_placer.png')
def razlom(): _razlom('razlom',FIRE,(0x90,0x24,0x0c),[(0xff,0xf0,0xc0),(0xff,0xc0,0x50)])
def cold_razlom(): _razlom('cold_razlom',ICE,(0x14,0x40,0x90),[(0xf0,0xfa,0xff),(0xa8,0xe0,0xff)])
GR=[(0,(0x28,0x1a,0x40)),(0.4,(0x58,0x38,0x98)),(0.7,(0x98,0x70,0xe0)),(1,(0xe8,0xd8,0xff))]
def plesh():
    im=medallion(); tint_inside(im,(35,25,55),0.8)
    # вдавленная земля
    for y in range(16):
        for x in range(16):
            if not inside(x,y,5.3) or y<9: continue
            put(im,x,y,(0x3a,0x34,0x2e) if (x+y)%3 else (0x46,0x3e,0x36))
    ring(im,7.5,10.5,2.4,(0x98,0x70,0xe0),0.9,0.5)
    ring(im,7.5,10.5,4.3,(0x68,0x48,0xb0),0.7,0.5)
    for y in range(16):
        for x in range(16):
            if math.hypot(x-7.5,(y-10.5)*1.8)<1.6: glowput(im,x,y,(0x14,0x10,0x1c),0.8)
    # давящий сверху воздух — две стрелки-шеврона вниз
    for i,(x0,x1,y0) in enumerate([(4,11,1),(4,11,5)]):
        n=(x1-x0+1)//2
        for k in range(n):
            c=ramp(GR,0.95-i*0.15-k*0.05)
            put(im,x0+k,y0+k,c); put(im,x1-k,y0+k,c)
    im.save(OUT+'plesh_placer.png')
def voronka():
    im=medallion()
    for y in range(16):
        for x in range(16):
            d=math.hypot(x-7.5,y-7.5)
            if d>5.3: continue
            a=math.atan2(y-7.5,x-7.5)
            v=0.5+0.5*math.sin(2*a+math.log(d+0.3)*4.2)
            k=d/5.3
            put(im,x,y,ramp(GR,(0.12+0.8*v)*(0.35+0.65*k)))
    for y in range(16):
        for x in range(16):
            d=math.hypot(x-7.5,y-7.5)
            if d<1.8: put(im,x,y,(0x06,0x04,0x0c))
            elif d<2.3: glowput(im,x,y,(0xe8,0xd8,0xff),0.7)
    for (x,y) in [(3,5),(12,11)]: glowput(im,x,y,(0x90,0x78,0x60),0.9)
    im.save(OUT+'voronka_placer.png')
def karusel():
    im=medallion(); tint_inside(im,(30,28,50),0.8)
    for x in range(3,13): put(im,x,12,(0x3a,0x34,0x2e))
    for i,(cy,r) in enumerate([(4.5,4.2),(7.2,3.4),(9.8,2.6)]):
        for y in range(16):
            for x in range(16):
                e=math.hypot(x-7.5,(y-cy)*2.6)
                if abs(e-r)<0.55:
                    front=y>=cy
                    glowput(im,x,y,ramp(GR,0.85 if front else 0.5),0.9 if front else 0.55)
    for (x,y,c) in [(3,5,(0x90,0x78,0x60)),(12,7,(0x70,0x90,0x50)),(5,10,(0x90,0x78,0x60)),(10,3,(0xc0,0xc0,0xb0))]:
        put(im,x,y,c)
    im.save(OUT+'karusel_placer.png')
def podushka():
    im=medallion(); tint_inside(im,(25,35,55),0.8)
    for x in range(3,13): put(im,x,12,(0x3a,0x3e,0x46))
    # мягкий купол
    for y in range(16):
        for x in range(16):
            e=math.hypot((x-7.5)/4.6,(y-12)/3.4)
            if e<1 and y<=12:
                glowput(im,x,y,(0x70,0xb0,0xe8),0.25+0.35*e)
            if abs(e-1)<0.13 and y<=12: glowput(im,x,y,(0xc0,0xe4,0xff),0.8)
    # подброшенный предмет и дуги отскока
    put(im,7,3,(0xe8,0xe0,0xd0)); put(im,8,3,(0xc8,0xc0,0xb0)); put(im,7,4,(0xb0,0xa8,0x98)); put(im,8,4,(0x98,0x90,0x80))
    for (x,y) in [(5,6),(10,6),(4,7),(11,7)]: glowput(im,x,y,(0xc0,0xe4,0xff),0.6)
    glowput(im,6,9,(255,255,255),0.8)
    im.save(OUT+'podushka_placer.png')
def pda():
    # 0.1.35.0: the anomaly KPK — a rugged handheld: dark body, lit screen with a detector trace, antenna, buttons.
    im=Image.new('RGBA',(16,16),(0,0,0,0))
    def px(x,y,c): im.putpixel((x,y),c+(255,))
    body=(0x2e,0x33,0x2c); edge=(0x16,0x19,0x15); hi=(0x4a,0x52,0x46)
    for y in range(3,15):
        for x in range(3,13):
            px(x,y,body)
    for x in range(3,13): px(x,3,edge); px(x,14,edge)
    for y in range(3,15): px(3,y,edge); px(12,y,edge)
    for y in range(4,14): px(4,y,hi)
    # antenna
    for y in range(0,3): px(10,y,(0x60,0x66,0x5e))
    px(10,0,(0xd0,0x40,0x30))
    # screen
    scr=(0x12,0x2a,0x1a)
    for y in range(5,10):
        for x in range(5,11): px(x,y,scr)
    trace=[(5,8),(6,8),(7,7),(7,6),(8,8),(9,7),(10,7)]
    for (x,y) in trace: px(x,y,(0x8c,0xf0,0x6a))
    px(9,5,(0xd8,0xff,0xc0))
    # buttons
    px(5,11,(0xc8,0x3c,0x2c)); px(7,11,(0xd8,0xb0,0x30)); px(9,11,(0x5a,0xb0,0x50))
    for x in (5,7,9): px(x,12,(0x1c,0x20,0x1b))
    im.save(OUT+'pda.png')
import sys
for f in sys.argv[1:]: globals()[f]()
