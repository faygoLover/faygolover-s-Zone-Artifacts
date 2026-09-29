from PIL import Image
import math
exec(open(__import__('os').path.join(__import__('os').path.dirname(__import__('os').path.abspath(__file__)),'icons.py')).read().split("for f in sys.argv")[0])
def blend(a,b,t): return tuple(int(a[i]+(b[i]-a[i])*t) for i in range(3))
def get(im,x,y): return im.getpixel((x,y))[:3]
def tint_inside(im,col,strength,cx=7.5,cy=7.5,r=5.3):
    for y in range(16):
        for x in range(16):
            d=math.hypot(x-cx,y-cy)
            if inside(x,y,5.3):
                t=strength*max(0,1-d/r)
                im.putpixel((x,y),blend(get(im,x,y),col,t)+(255,))
def glowput(im,x,y,col,t):
    if 0<=x<16 and 0<=y<16 and inside(x,y,5.6):
        im.putpixel((x,y),blend(get(im,x,y),col,t)+(255,))
def electra():
    im=medallion()
    tint_inside(im,(40,70,130),0.8)
    W=(240,250,255); C=(150,215,255); B=(70,140,240)
    rows={3:[9,10],4:[8,9],5:[7,8],6:[6,7,8,9,10],7:[8,9],8:[7,8],9:[6,7],10:[5,6],11:[5]}
    bolt=[(x,y) for y,xs in rows.items() for x in xs]
    branch=[(10,8),(10,9),(11,10)]
    spark=[(4,5),(12,6),(4,9)]
    cells=set(bolt)|set(branch)
    for (x,y) in bolt:
        for dx,dy in [(-1,0),(1,0),(0,-1),(0,1)]:
            if (x+dx,y+dy) not in cells: glowput(im,x+dx,y+dy,B,0.5)
    for (x,y) in bolt:
        left=(x-1,y) not in cells
        put(im,x,y,W if left else C)
    for (x,y) in [(7,6),(8,6)]: put(im,x,y,(255,255,255))
    for (x,y) in branch: put(im,x,y,B)
    put(im,10,8,C)
    for (x,y) in spark: glowput(im,x,y,C,0.85)
    im.save(OUT+'electra_placer.png')
def iney():
    im=medallion()
    tint_inside(im,(40,70,95),0.7)
    W=(245,252,255); L=(185,235,255); M=(110,190,235); D=(55,120,180)
    def shard(x0,ytop,ybot,w):
        # left column light, right dark, pointed tip
        for y in range(ytop,ybot+1):
            if y==ytop:
                put(im,x0,y,W); continue
            put(im,x0,y,L if y<ytop+2 else M)
            if w>1: put(im,x0+1,y,M if y<ytop+3 else D)
            if w>2: put(im,x0-1,y,L)
        put(im,x0,ytop+1,W)
    shard(7,3,11,2)
    shard(4,6,11,2)
    shard(11,5,11,1)
    put(im,10,7,M); put(im,10,8,D); put(im,10,9,D); put(im,10,10,D); put(im,10,11,M)
    # frost base
    for x in range(3,13):
        put(im,x,12,(200,230,245) if (x%3) else W)
    for x in range(4,12,2): put(im,x,11,(215,240,250))
    # sparkles
    for (x,y) in [(4,4),(12,8)]:
        glowput(im,x,y,W,0.9)
    for (x,y) in [(3,4),(5,4),(4,3)]:
        glowput(im,x,y,L,0.4)
    im.save(OUT+'iney_placer.png')
import sys
for f in sys.argv[1:]: globals()[f]()
