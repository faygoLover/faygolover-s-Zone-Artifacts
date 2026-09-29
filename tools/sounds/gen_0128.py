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

# 1 swamp squelch x2
for v in range(2):
    d=1.0; tt=t(d); n=len(tt)
    mud=lp(rng.standard_normal(n),400+v*150)*env(n,0.03,0.18)
    f=np.linspace(110+v*20,55,n); blob=np.sin(2*np.pi*np.cumsum(f)/SR)*env(n,0.02,0.25)
    x=mud*1.2+blob*0.8
    for k in range(3+v):
        st=int(SR*(0.15+rng.random()*0.55)); L=int(SR*0.07)
        ff=np.linspace(380+rng.random()*200,160,L)
        pop=np.sin(2*np.pi*np.cumsum(ff)/SR)*np.exp(-np.arange(L)/SR*45)
        x[st:st+L]+=pop*0.5
    save(f'swamp_squelch{v+1}',fade(lp(x,1800)),0.8)

# 2 dymka distant x4
def groan():
    d=3.5; tt=t(d); n=len(tt)
    x=np.zeros(n)
    for f0 in (73,97,131,149):
        bend=f0*(1+0.06*np.sin(2*np.pi*0.3*tt+rng.random()*6)+0.04*tt/d)
        x+=np.sin(2*np.pi*np.cumsum(bend)/SR)*(0.5+0.5*rng.random())
    x+=bp(rng.standard_normal(n),200,900)*0.3
    x*=np.sin(np.pi*np.clip(tt/d,0,1))**1.5
    return reverb(lp(x,1200),2.2,0.7,1800)
def knocks():
    d=2.8; n=int(SR*d); x=np.zeros(n)
    for k,tk in enumerate(sorted(rng.random(3)*1.6+0.2)):
        st=int(tk*SR); L=int(0.25*SR); tt=np.arange(L)/SR
        th=np.sin(2*np.pi*(95+rng.random()*30)*tt)*np.exp(-tt*22)+lp(rng.standard_normal(L),600)*np.exp(-tt*40)*0.6
        x[st:st+L]+=th*(0.7+0.3*rng.random())
    return reverb(lp(x,900),2.0,0.75,1500)
def voice():
    d=3.2; tt=t(d); n=len(tt)
    f0=160+30*np.sin(2*np.pi*0.25*tt)-25*tt/d
    saw=sg.sawtooth(2*np.pi*np.cumsum(f0)/SR)
    x=bp(saw,280,420,2)*1.0+bp(saw,700,900,2)*0.5+bp(saw,2300,2700,2)*0.15
    x*=np.sin(np.pi*np.clip(tt/d,0,1))**2
    return reverb(x,2.4,0.8,2200)
def whistle():
    d=3.0; tt=t(d); n=len(tt)
    x=np.zeros(n); c=np.linspace(700,1100,n)+120*np.sin(2*np.pi*0.4*tt)
    noise=rng.standard_normal(n)
    # moving bandpass via short blocks
    blk=2048
    for i in range(0,n,blk):
        cc=c[min(i,n-1)]; x[i:i+blk]=bp(noise[i:i+blk+0],cc*0.9,cc*1.1,2)[:len(x[i:i+blk])]
    x=lp(x,3000)*np.sin(np.pi*np.clip(tt/d,0,1))**2
    return reverb(x,2.0,0.7,2600)
for i,fn in enumerate([groan,knocks,voice,whistle]):
    save(f'dymka_distant{i+1}',fade(fn(),0.02,0.3),0.85)

# 3 sumrak drone loop
d=9.0; tt=t(d+0.5); n=len(tt)
x=np.sin(2*np.pi*41*tt)*0.8+np.sin(2*np.pi*43.3*tt)*0.5+np.sin(2*np.pi*55*tt)*0.3
brown=np.cumsum(rng.standard_normal(n)); brown=hp(brown,20,2); brown=lp(brown,180)
x+=norm(brown,0.6)*(0.8+0.2*np.sin(2*np.pi*0.13*tt))
x*=0.85+0.15*np.sin(2*np.pi*0.21*tt)
save('sumrak_drone',loopify(lp(x,400),0.5),0.7)

# 4 psi hum loop
d=8.0; tt=t(d+0.5); n=len(tt)
x=np.sin(2*np.pi*110*tt)*0.7+np.sin(2*np.pi*111.6*tt)*0.5+np.sin(2*np.pi*220.8*tt)*0.18+np.sin(2*np.pi*331*tt)*0.08
x+=np.sin(2*np.pi*(3100+40*np.sin(2*np.pi*0.3*tt))*tt)*0.006
x+=bp(rng.standard_normal(n),80,300)*0.25
x*=0.8+0.2*np.sin(2*np.pi*0.5*tt)
save('psi_hum',loopify(x,0.5),0.65)

# 5 psi whisper x3
for v in range(3):
    d=1.6+0.4*rng.random(); n=int(SR*d); tt=np.arange(n)/SR
    e=np.zeros(n); pos=0.05
    while pos<d-0.15:
        L=0.08+rng.random()*0.16; a=int(pos*SR); b=min(n,int((pos+L)*SR))
        seg=np.sin(np.linspace(0,np.pi,b-a))**1.5*(0.5+0.5*rng.random()); e[a:b]=np.maximum(e[a:b],seg)
        pos+=L+rng.random()*0.08
    noise=rng.standard_normal(n)
    f1=bp(noise,900+rng.random()*300,1500,2); f2=bp(noise,2200,3400,2); f3=hp(noise,5000,2)*0.3
    mixw=0.5+0.5*np.sin(2*np.pi*(2+rng.random()*3)*tt)
    x=(f1*mixw+f2*(1-mixw)+f3)*e
    save(f'psi_whisper{v+1}',fade(reverb(x,0.8,0.35,4000),0.01,0.2),0.7)

# 6 poppy hum loop
d=8.0; tt=t(d+0.5); n=len(tt)
x=np.sin(2*np.pi*196*tt)*0.5+np.sin(2*np.pi*197.2*tt)*0.4+np.sin(2*np.pi*98*tt)*0.35
x+=np.sin(2*np.pi*6200*tt)*0.03*(0.7+0.3*np.sin(2*np.pi*0.2*tt))
pink=lp(rng.standard_normal(n),700,2)*0.3
x+=pink
x*=0.85+0.15*np.sin(2*np.pi*0.25*tt)
save('poppy_hum',loopify(lp(x,7000),0.5),0.6)

# 7 rust dust x2
for v in range(2):
    d=0.7; n=int(SR*d)
    x=lp(rng.standard_normal(n),1800+v*600)*env(n,0.02,0.16)
    x+=hp(rng.standard_normal(n),4000)*env(n,0.01,0.06)*0.2
    save(f'rust_dust{v+1}',fade(x),0.6)

# 8 rust blast
d=1.8; tt=t(d); n=len(tt)
boom=np.sin(2*np.pi*np.cumsum(np.linspace(80,38,n))/SR)*env(n,0.005,0.35)
burst=lp(rng.standard_normal(n),3000)*env(n,0.003,0.12)*1.2
siz=hp(rng.standard_normal(n),2500)*env(n,0.05,0.7)*0.6
cr=np.zeros(n)
for k in range(260):
    st=rng.integers(int(0.05*SR),n-400); L=rng.integers(40,300)
    cr[st:st+L]+=rng.standard_normal(L)*np.exp(-np.arange(L)/60)*(1-st/n)
x=boom*1.2+burst+siz+hp(cr,1500)*0.5
save('rust_blast',fade(reverb(x,1.0,0.3,5000),0.001,0.3),0.9)

# 9 rust hiss
d=1.4; n=int(SR*d)
x=hp(rng.standard_normal(n),2200)*env(n,0.04,0.45)
x+=bp(rng.standard_normal(n),600,1400)*env(n,0.02,0.2)*0.4
save('rust_hiss',fade(x,0.005,0.2),0.75)

# 10 rust crackle loop
d=4.0; n=int(SR*(d+0.4)); x=np.zeros(n)
for k in range(140):
    st=rng.integers(0,n-600); L=rng.integers(30,500)
    x[st:st+L]+=rng.standard_normal(L)*np.exp(-np.arange(L)/rng.integers(20,120))*(0.3+rng.random())
x=hp(x,900)+lp(rng.standard_normal(n),120)*0.15
save('rust_crackle',loopify(x,0.4),0.55)
print('ok')
