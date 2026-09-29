import tempfile
import numpy as np, scipy.signal as sg, scipy.io.wavfile as wf, subprocess, os
SR=44100
rng=np.random.default_rng(7)
OUT=os.path.join(os.path.join(os.path.dirname(os.path.abspath(__file__)),'..','..','src','main','resources','assets','fl_zone_arts'),'sounds')+os.sep
def t(d): return np.arange(int(SR*d))/SR
def lp(x,f,o=4): b,a=sg.butter(o,f/(SR/2),'low'); return sg.lfilter(b,a,x)
def hp(x,f,o=4): b,a=sg.butter(o,f/(SR/2),'high'); return sg.lfilter(b,a,x)
def bp(x,f1,f2,o=3): b,a=sg.butter(o,[f1/(SR/2),f2/(SR/2)],'band'); return sg.lfilter(b,a,x)
def env(n,a,d):
    e=np.ones(n); na=max(1,int(a*SR)); e[:na]=np.linspace(0,1,na)
    tt=np.arange(n)/SR; e*=np.exp(-np.maximum(0,tt-a)/max(d,1e-3)); return e
def norm(x,peak=0.9): return x/(np.max(np.abs(x))+1e-9)*peak
def reverb(x,dur=1.6,mix=0.5,dark=2500):
    n=int(SR*dur); ir=rng.standard_normal(n)*np.exp(-np.arange(n)/SR*4.0/dur*1.5); ir=lp(ir,dark)
    ir/=np.sqrt(np.sum(ir**2)); wet=sg.fftconvolve(x,ir)
    dry=np.concatenate([x,np.zeros(len(wet)-len(x))]); return dry*(1-mix)+wet*mix*0.6
def fade(x,fi=0.01,fo=0.05):
    n=len(x); a=int(fi*SR); b=int(fo*SR)
    if a>0: x[:a]*=np.linspace(0,1,a)
    if b>0: x[-b:]*=np.linspace(1,0,b)
    return x
def loopify(x,xf=0.5):
    # crossfade tail into head for a seamless loop
    n=int(xf*SR); head=x[:n].copy(); tail=x[-n:].copy(); w=np.linspace(0,1,n)
    y=x[:-n].copy(); y[:n]=tail*(1-w)+head*w; return y
def save(name,x,peak=0.9):
    x=norm(np.asarray(x,dtype=np.float64),peak); wav=os.path.join(tempfile.gettempdir(),name+'.wav')
    wf.write(wav,SR,(x*32767).astype(np.int16))
    subprocess.run(['ffmpeg','-y','-loglevel','error','-i',wav,'-ac','1','-c:a','libvorbis','-q:a','5',OUT+name+'.ogg'],check=True)
    os.remove(wav)


# bubble pop x2: soft pop + low whump + airy tail
for v in range(2):
    d=1.0; tt=t(d); n=len(tt)
    thump=np.sin(2*np.pi*np.cumsum(np.linspace(140+v*30,50,n))/SR)*env(n,0.002,0.12)
    pop=hp(rng.standard_normal(n),1500)*env(n,0.001,0.02)*0.8
    wob=np.sin(2*np.pi*np.cumsum(np.linspace(900,300,n))/SR)*env(n,0.002,0.05)*0.4
    air=bp(rng.standard_normal(n),300,2000)*env(n,0.01,0.25)*0.35
    save(f'bubble_pop{v+1}',fade(reverb(thump+pop+wob+air,0.6,0.25,4000),0.001,0.1),0.85)
# khlopushka charge: rising whine + crackle, 2.2 s
d=2.2; tt=t(d); n=len(tt)
f=np.linspace(300,2400,n)**1.0
wh=np.sin(2*np.pi*np.cumsum(f)/SR)*np.linspace(0.1,1,n)**1.5
wh+=0.3*np.sin(2*np.pi*np.cumsum(f*1.5)/SR)*np.linspace(0,1,n)**2
cr=np.zeros(n)
for k in range(300):
    st=int((rng.random()**0.5)*(n-400)); L=rng.integers(20,150)
    cr[st:st+L]+=rng.standard_normal(L)*np.exp(-np.arange(L)/30)
x=wh*0.6+hp(cr,2000)*0.5
save('khlopushka_charge',fade(x,0.05,0.02),0.7)
# khlopushka bang: sharp crack + boom + tail
d=2.0; tt=t(d); n=len(tt)
crack=rng.standard_normal(n)*env(n,0.0005,0.015)*1.5
boom=np.sin(2*np.pi*np.cumsum(np.linspace(110,40,n))/SR)*env(n,0.002,0.3)
rum=lp(rng.standard_normal(n),250)*env(n,0.005,0.6)*0.8
x=crack+boom*1.2+rum
save('khlopushka_bang',fade(reverb(x,1.4,0.3,4000),0.0005,0.3),0.95)
# khlopushka ring (tinnitus loop): high tone + faint hiss
d=6.0; tt=t(d+0.5); n=len(tt)
x=np.sin(2*np.pi*4200*tt)*0.5+np.sin(2*np.pi*4213*tt)*0.3+hp(rng.standard_normal(n),6000)*0.05
save('khlopushka_ring',loopify(x,0.5),0.4)
# kamerton ring loop: glassy partials with beating
d=8.0; tt=t(d+0.5); n=len(tt)
x=np.zeros(n)
for fr,a in [(1760,0.5),(1763.5,0.4),(2637,0.25),(3520,0.15),(4430,0.1)]:
    x+=a*np.sin(2*np.pi*fr*tt+rng.random()*6)
x*=0.75+0.25*np.sin(2*np.pi*0.3*tt)
save('kamerton_ring',loopify(x,0.5),0.35)
# kamerton cut x2: glassy slice
for v in range(2):
    d=0.8; tt=t(d); n=len(tt)
    sl=bp(rng.standard_normal(n),3000,9000)*env(n,0.003,0.06)
    ting=sum(a*np.sin(2*np.pi*fr*tt)*np.exp(-tt*dd) for fr,a,dd in [(2200+v*300,0.5,6),(3310+v*200,0.35,8),(5100,0.2,12)])
    save(f'kamerton_cut{v+1}',fade(sl+ting,0.001,0.1),0.75)
# kamerton shatter: many tinkles
d=1.5; n=int(SR*d); x=np.zeros(n)
x+=hp(rng.standard_normal(n),2500)*env(n,0.001,0.05)
for k in range(40):
    st=int(rng.random()**1.5*SR*0.9); fr=2000+rng.random()*6000; L=int(SR*0.3)
    tt=np.arange(L)/SR; x[st:st+L]+=np.sin(2*np.pi*fr*tt)*np.exp(-tt*(15+rng.random()*20))*(0.3+rng.random()*0.5)
save('kamerton_shatter',fade(x,0.001,0.1),0.8)
# web snap x2: thin twang + tick
for v in range(2):
    d=0.6; tt=t(d); n=len(tt)
    tick=hp(rng.standard_normal(n),4000)*env(n,0.0005,0.004)
    tw=np.sin(2*np.pi*np.cumsum(np.linspace(1800+v*400,900,n))/SR)*env(n,0.001,0.08)*0.5
    save(f'web_snap{v+1}',fade(tick+tw,0.0005,0.05),0.6)
print('ok')
