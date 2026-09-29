exec(open(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'icons3.py')).read().split("import sys\nfor f in sys.argv")[0])
def soap_bubbles():
    im=medallion(); tint_inside(im,(30,34,50),0.6)
    for cx,cy,r,h in [(6,8,3.0,0.0),(10.5,5.5,1.6,0.4),(10,10.5,1.2,0.7)]:
        for y in range(16):
            for x in range(16):
                d=math.hypot(x-cx,y-cy)
                if abs(d-r)<0.55:
                    a=(math.atan2(y-cy,x-cx)/6.283+h)%1
                    import colorsys
                    rr,gg,bb=colorsys.hsv_to_rgb(a,0.5,1.0)
                    glowput(im,x,y,(int(rr*255),int(gg*255),int(bb*255)),0.85)
        glowput(im,int(cx-r*0.4),int(cy-r*0.45),(255,255,255),0.9)
    im.save(OUT+'soap_bubbles_placer.png')
def khlopushka():
    im=medallion(); tint_inside(im,(40,40,70),0.7)
    for y in range(16):
        for x in range(16):
            d=math.hypot(x-7.5,y-7.5)
            if d<4.5: glowput(im,x,y,(200,220,255),max(0,1-d/4.5)*0.9)
            if d<1.8: glowput(im,x,y,(255,255,240),1.0)
    for (x,y) in [(3,4),(12,5),(4,12),(11,11),(7,2),(13,8)]: glowput(im,x,y,(180,220,255),0.8)
    im.save(OUT+'khlopushka_placer.png')
def svetlyachok():
    im=medallion(); tint_inside(im,(15,25,20),0.8)
    for x in range(3,13): put(im,x,12,(0x30,0x50,0x28))
    for cx,cy in [(5,6),(10,5),(8,9)]:
        for y in range(16):
            for x in range(16):
                d=math.hypot(x-cx,y-cy)
                if d<2.2: glowput(im,x,y,(170,255,90),max(0,1-d/2.2)*0.7)
        put(im,cx,cy,(245,255,190))
    im.save(OUT+'svetlyachok_placer.png')
def kamerton():
    im=medallion(); tint_inside(im,(30,40,50),0.7)
    for k in range(12):
        a=k*math.pi/6
        for s in [2.2,3.0,3.8,4.6]:
            x=int(round(7.5+math.cos(a)*s)); y=int(round(7.5+math.sin(a)*s))
            glowput(im,x,y,(200,235,250),0.35+0.15*(4.6-s))
    glowput(im,7,7,(255,255,255),0.6); glowput(im,8,8,(230,245,255),0.5)
    for (x,y) in [(4,4),(11,11)]: glowput(im,x,y,(255,255,255),0.9)
    im.save(OUT+'kamerton_placer.png')
def pautina():
    im=medallion(); tint_inside(im,(25,25,30),0.6)
    c=(7.5,7.5)
    for k in range(6):
        a=k*math.pi/3+0.3
        for s in [i*0.25 for i in range(0,22)]:
            glowput(im,int(round(c[0]+math.cos(a)*s)),int(round(c[1]+math.sin(a)*s)),(220,225,235),0.75)
    for r in [2.0,3.6]:
        for k in range(60):
            a=k*math.pi/30
            glowput(im,int(round(c[0]+math.cos(a)*r)),int(round(c[1]+math.sin(a)*r)),(200,205,215),0.55)
    glowput(im,5,5,(255,255,255),1.0)
    im.save(OUT+'pautina_placer.png')
def fantom_light():
    im=medallion(); tint_inside(im,(15,20,40),0.7)
    for i,x in enumerate([4,6,8,10,12]):
        y0,y1=(6,11) if i in (0,4) else (4,12)
        for y in range(y0,y1+1,1):
            glowput(im,x-1 if x>8 else x,y,(90,160,255),0.4)
            if (y+i)%2==0: glowput(im,x-1 if x>8 else x,y,(200,235,255),0.95)
    im.save(OUT+'fantom_light_placer.png')
import sys
for f in sys.argv[1:]: globals()[f]()
