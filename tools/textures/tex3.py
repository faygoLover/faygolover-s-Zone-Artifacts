import numpy as np
from PIL import Image
import os
R=os.path.join(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','src','main','resources','assets','fl_zone_arts'),'textures')+os.sep
rng=np.random.default_rng(11)
def field(n,scale):
    f=rng.standard_normal((n,n)); F=np.fft.fft2(f)
    ky=np.fft.fftfreq(n)[:,None]; kx=np.fft.fftfreq(n)[None,:]; k=np.sqrt(kx**2+ky**2)
    F*=np.exp(-(k*scale)**2); x=np.real(np.fft.ifft2(F)); x=(x-x.min())/(x.max()-x.min()); return x
import os; os.makedirs(R+'misc',exist_ok=True)
N=64
low=field(N,10); mid=field(N,4); hi=rng.random((N,N))
pal=np.array([[0x4E,0x27,0x12],[0x6B,0x3A,0x1E],[0x8A,0x4B,0x22],[0xA3,0x5E,0x2A],[0xB8,0x70,0x3A]])
img=np.zeros((N,N,4),np.uint8)
tone=np.clip(0.55*mid+0.3*hi+0.15*low,0,0.999)
idx=(tone*len(pal)).astype(int)
img[...,:3]=pal[idx]
hole=low<0.28  # bald patches
edge=(low<0.36)&~hole
alpha=np.where(hole,0,255); alpha=np.where(edge&(hi<0.5),0,alpha)
img[...,3]=alpha
Image.fromarray(img,'RGBA').save(R+'misc/rust_moss.png')
for name,density,cols in [('rust_fuzz_a',0.16,[[0x9A,0x55,0x28],[0xB0,0x68,0x33],[0x7A,0x40,0x1E]]),('rust_fuzz_b',0.07,[[0xC9,0x82,0x46],[0xB8,0x70,0x3A],[0xD8,0x96,0x5A]])]:
    im=np.zeros((N,N,4),np.uint8)
    m=field(N,6)
    for y in range(N):
        for x in range(N):
            if rng.random()<density*(0.4+1.2*m[y,x]):
                c=cols[rng.integers(len(cols))]; im[y,x,:3]=c; im[y,x,3]=255
                if rng.random()<0.35:
                    yy=(y+1)%N; im[yy,x,:3]=c; im[yy,x,3]=255
    Image.fromarray(im,'RGBA').save(R+'misc/'+name+'.png')
# poppy petals 8x8
shapes=[["..XXX...",".XXXXX..","XXXXXXX.","XXXXXXX.",".XXXXXX.","..XXXX..","...XX...","........"],
        ["...XX...","..XXXX..",".XXXXXX.",".XXXXXX.","..XXXXX.","...XXX..","....X...","........"],
        [".XX.....","XXXX....","XXXXX...",".XXXXX..","..XXXX..","...XX...","........","........"]]
for i,s in enumerate(shapes):
    im=Image.new('RGBA',(8,8),(0,0,0,0))
    for y,row in enumerate(s):
        for x,ch in enumerate(row):
            if ch=='X':
                v=235 if (x+y)%3 else 205
                if y>=5: v=180
                im.putpixel((x,y),(v,v,v,255))
    im.save(R+f'particle/poppy_petal_{i}.png')
print('ok')
