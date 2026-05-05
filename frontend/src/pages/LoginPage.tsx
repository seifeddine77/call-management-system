import { useState, useEffect } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { useTheme } from '../contexts/ThemeContext';
import { motion, AnimatePresence, useMotionValue, useSpring, useTransform } from 'framer-motion';
import toast from 'react-hot-toast';
import { Mail, Lock, Eye, EyeOff, Sun, Moon, Shield, Brain, Mic, Car, Headphones, BarChart3, Activity } from 'lucide-react';

function useTyping(texts: string[], speed = 50, pause = 3000) {
  const [display, setDisplay] = useState('');
  const [idx, setIdx] = useState(0);
  useEffect(() => {
    let i = 0, del = false, t: ReturnType<typeof setTimeout>;
    const tick = () => {
      const full = texts[idx];
      if (!del) { setDisplay(full.slice(0, ++i)); if (i === full.length) { t = setTimeout(() => { del = true; tick(); }, pause); return; } }
      else { setDisplay(full.slice(0, --i)); if (i === 0) { del = false; setIdx(p => (p + 1) % texts.length); return; } }
      t = setTimeout(tick, del ? 20 : speed);
    };
    t = setTimeout(tick, 500);
    return () => clearTimeout(t);
  }, [idx, texts, speed, pause]);
  return display;
}

function useParallax() {
  const mx = useMotionValue(0), my = useMotionValue(0);
  const sx = useSpring(mx, { stiffness: 40, damping: 18 });
  const sy = useSpring(my, { stiffness: 40, damping: 18 });
  useEffect(() => {
    const h = (e: MouseEvent) => { mx.set((e.clientX/globalThis.innerWidth-0.5)*2); my.set((e.clientY/globalThis.innerHeight-0.5)*2); };
    globalThis.addEventListener('mousemove', h);
    return () => globalThis.removeEventListener('mousemove', h);
  }, [mx, my]);
  return { sx, sy };
}

/* ── Animated Background ── */
function AnimatedBg({ isDark }: Readonly<{ isDark: boolean }>) {
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      {[
        { w:'50vw',h:'50vw',top:'-15%',left:'-10%',c:isDark?'rgba(37,99,235,0.12)':'rgba(37,99,235,0.08)',dur:22,dx:80,dy:60 },
        { w:'40vw',h:'40vw',bottom:'-10%',right:'-5%',c:isDark?'rgba(6,182,212,0.1)':'rgba(6,182,212,0.06)',dur:26,dx:-60,dy:-50 },
        { w:'35vw',h:'35vw',top:'40%',left:'30%',c:isDark?'rgba(16,185,129,0.06)':'rgba(16,185,129,0.04)',dur:30,dx:40,dy:70 },
      ].map((b,i) => (
        <motion.div key={i} className="absolute rounded-full" style={{ width:b.w,height:b.h,top:b.top,bottom:b.bottom,left:b.left,right:b.right,
          background:`radial-gradient(circle, ${b.c} 0%, transparent 70%)`,filter:'blur(60px)' } as any}
          animate={{ x:[0,b.dx,-b.dx/2,0], y:[0,b.dy/2,-b.dy,0] }}
          transition={{ duration:b.dur, repeat:Infinity, ease:'easeInOut' }} />
      ))}
      <div className="absolute inset-0" style={{
        backgroundImage:`linear-gradient(${isDark?'rgba(59,130,246,0.03)':'rgba(37,99,235,0.04)'} 1px,transparent 1px),linear-gradient(90deg,${isDark?'rgba(59,130,246,0.03)':'rgba(37,99,235,0.04)'} 1px,transparent 1px)`,
        backgroundSize:'60px 60px',
      }}/>
      {Array.from({length:20}).map((_,i) => (
        <div key={`p${i}`} className="absolute rounded-full" style={{
          width:`${2+Math.random()*3}px`,height:`${2+Math.random()*3}px`,
          left:`${Math.random()*100}%`,top:`${Math.random()*100}%`,
          background:isDark?`rgba(59,130,246,${0.15+Math.random()*0.2})`:`rgba(37,99,235,${0.1+Math.random()*0.15})`,
          animation:`loginFloat ${8+Math.random()*12}s ease-in-out infinite`,animationDelay:`${Math.random()*-20}s`,
        }}/>
      ))}
    </div>
  );
}

/* ── Animated SVG Scene — Car + Call Center ── */
function AnimatedScene({ isDark, sx, sy }: Readonly<{ isDark: boolean; sx: any; sy: any }>) {
  const carX = useTransform(sx, [-1,1], [-12,12]);
  const carY = useTransform(sy, [-1,1], [-8,8]);
  const el1X = useTransform(sx, [-1,1], [8,-8]);
  const el1Y = useTransform(sy, [-1,1], [6,-6]);
  const el2X = useTransform(sx, [-1,1], [-6,6]);
  const el2Y = useTransform(sy, [-1,1], [-4,4]);
  const accent = isDark ? '#3b82f6' : '#2563eb';
  const accent2 = isDark ? '#06b6d4' : '#0891b2';
  const accent3 = isDark ? '#10b981' : '#059669';
  const lineColor = isDark ? 'rgba(59,130,246,0.15)' : 'rgba(37,99,235,0.12)';

  return (
    <div className="relative w-full max-w-[500px] mx-auto" style={{ height: 360 }}>
      {/* Central car icon */}
      <motion.div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-10"
        style={{ x: carX, y: carY }}
        animate={{ y: [0, -6, 0] }}
        transition={{ duration: 4, repeat: Infinity, ease: 'easeInOut' }}>
        <div className="w-28 h-28 rounded-3xl flex items-center justify-center"
          style={{ background: `linear-gradient(135deg, ${accent}, ${accent2})`, boxShadow: `0 20px 60px ${isDark ? 'rgba(37,99,235,0.3)' : 'rgba(37,99,235,0.2)'}` }}>
          <Car size={48} color="white" strokeWidth={1.5} />
        </div>
        {/* Pulse rings around car */}
        {[1,2,3].map(r => (
          <motion.div key={r} className="absolute inset-0 rounded-3xl border"
            style={{ borderColor: `${accent}20` }}
            animate={{ scale: [1, 1.3+r*0.2], opacity: [0.4, 0] }}
            transition={{ duration: 2+r*0.5, repeat: Infinity, delay: r*0.4 }} />
        ))}
      </motion.div>

      {/* Orbiting elements */}
      {[
        { icon: Headphones, label: 'Centre d\'appels', x: '10%', y: '20%', pX: el1X, pY: el1Y, delay: 0, color: accent },
        { icon: Brain, label: 'IA Gemma', x: '72%', y: '15%', pX: el2X, pY: el2Y, delay: 0.5, color: accent2 },
        { icon: Mic, label: 'Whisper STT', x: '75%', y: '70%', pX: el1X, pY: el2Y, delay: 1, color: accent3 },
        { icon: BarChart3, label: 'Analytics', x: '8%', y: '72%', pX: el2X, pY: el1Y, delay: 1.5, color: accent },
        { icon: Activity, label: 'Temps réel', x: '45%', y: '5%', pX: el1X, pY: el1Y, delay: 0.8, color: accent2 },
        { icon: Shield, label: 'Sécurité', x: '42%', y: '88%', pX: el2X, pY: el2Y, delay: 1.2, color: accent3 },
      ].map((el, i) => (
        <motion.div key={i} className="absolute flex flex-col items-center gap-1 z-20"
          style={{ left: el.x, top: el.y, x: el.pX, y: el.pY }}
          initial={{ opacity: 0, scale: 0 }}
          animate={{ opacity: 1, scale: 1, y: [0, -5, 0] }}
          transition={{ opacity: { delay: 0.5+el.delay }, scale: { delay: 0.5+el.delay, type: 'spring', stiffness: 200 }, y: { duration: 3+i*0.5, repeat: Infinity, ease: 'easeInOut', delay: el.delay } }}>
          <div className="w-11 h-11 rounded-xl flex items-center justify-center"
            style={{ background: isDark ? 'rgba(15,23,42,0.8)' : 'rgba(255,255,255,0.9)', border: `1px solid ${el.color}30`, backdropFilter: 'blur(8px)',
              boxShadow: `0 8px 25px ${isDark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.08)'}` }}>
            <el.icon size={18} color={el.color} />
          </div>
          <span className="text-[8px] font-mono tracking-wider whitespace-nowrap" style={{ color: isDark ? '#94a3b8' : '#64748b' }}>{el.label}</span>
        </motion.div>
      ))}

      {/* Animated connection lines (SVG) */}
      <svg className="absolute inset-0 w-full h-full z-0" viewBox="0 0 500 360">
        {[
          'M 70 80 Q 200 120 250 180', 'M 380 65 Q 300 100 250 180',
          'M 395 260 Q 320 230 250 180', 'M 65 270 Q 150 240 250 180',
          'M 250 30 Q 250 100 250 180', 'M 240 320 Q 245 250 250 180',
        ].map((d, i) => (
          <motion.path key={i} d={d} fill="none" stroke={lineColor} strokeWidth="1.5" strokeDasharray="6 4"
            initial={{ pathLength: 0, opacity: 0 }}
            animate={{ pathLength: 1, opacity: 1 }}
            transition={{ duration: 1.5, delay: 0.3+i*0.15, ease: 'easeOut' }} />
        ))}
        {/* Animated dots traveling along paths */}
        {[
          'M 70 80 Q 200 120 250 180', 'M 380 65 Q 300 100 250 180',
          'M 395 260 Q 320 230 250 180', 'M 65 270 Q 150 240 250 180',
        ].map((d, i) => (
          <motion.circle key={`dot${i}`} r="3" fill={accent}
            initial={{ offsetDistance: '0%' }}
            animate={{ offsetDistance: ['0%', '100%'] }}
            transition={{ duration: 2.5, repeat: Infinity, delay: i*0.6, ease: 'linear' }}
            style={{ offsetPath: `path('${d}')` } as any} />
        ))}
      </svg>
    </div>
  );
}

const stagger = { hidden:{opacity:0},show:{opacity:1,transition:{staggerChildren:0.08,delayChildren:0.2}} } as const;
const pop = { hidden:{opacity:0,y:20,scale:0.95},show:{opacity:1,y:0,scale:1,transition:{type:'spring' as const,stiffness:300,damping:24}} };

export default function LoginPage() {
  const { login } = useAuth();
  const { theme, toggleTheme } = useTheme();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [showPwd, setShowPwd] = useState(false);
  const [focused, setFocused] = useState<string | null>(null);
  const isDark = theme === 'dark';
  const { sx, sy } = useParallax();
  const tagline = useTyping(['Gestion intelligente des réclamations auto','IA & Analyse vocale en temps réel','Centre d\'appels nouvelle génération']);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!email || !password) { toast.error('Veuillez remplir tous les champs'); return; }
    setLoading(true);
    try { await login(email, password); toast.success('Connexion réussie !'); }
    catch { toast.error('Email ou mot de passe incorrect'); }
    finally { setLoading(false); }
  };

  return (
    <div className="min-h-screen flex relative overflow-hidden" style={{ background: isDark ? '#060b18' : '#f0f4f8' }}>
      <AnimatedBg isDark={isDark} />

      {/* ═══ LEFT — ANIMATED SVG SCENE ═══ */}
      <div className="hidden lg:flex flex-1 items-center justify-center relative z-10">
        <motion.div className="flex flex-col items-center max-w-[550px] px-6 w-full"
          variants={stagger} initial="hidden" animate="show">
          <motion.div variants={pop} className="flex items-center gap-3 self-start mb-4">
            <div className="w-10 h-10 rounded-xl flex items-center justify-center text-white text-lg font-black"
              style={{ background:'linear-gradient(135deg,#2563eb,#3b82f6)',boxShadow:'0 0 20px rgba(37,99,235,0.3)' }}>A</div>
            <span className="font-bold text-lg tracking-tight" style={{ color:isDark?'#f1f5f9':'#0f172a' }}>AutoAssist</span>
            <span className="px-2 py-0.5 rounded text-[8px] font-mono tracking-widest uppercase text-emerald-500 border border-emerald-500/30 bg-emerald-500/5">v2.0</span>
          </motion.div>

          {/* Animated interactive scene */}
          <motion.div variants={pop} className="w-full">
            <AnimatedScene isDark={isDark} sx={sx} sy={sy} />
          </motion.div>

          <motion.h2 variants={pop} className="text-3xl font-black tracking-tight text-center leading-tight mt-2"
            style={{ color:isDark?'#f1f5f9':'#0f172a',letterSpacing:'-0.03em' }}>
            La plateforme qui{' '}
            <span style={{ background:'linear-gradient(135deg,#2563eb,#06b6d4)',WebkitBackgroundClip:'text',WebkitTextFillColor:'transparent' }}>révolutionne</span>
            {' '}l'assurance auto
          </motion.h2>
          <motion.p variants={pop} className="text-center mt-2 text-sm" style={{ color:'#64748b' }}>
            Centre d'appels intelligent propulsé par l'IA
          </motion.p>
          <motion.div variants={pop} className="flex items-center gap-2 mt-4">
            <div className="w-2 h-2 rounded-full bg-emerald-400" style={{ animation:'loginPulseRing 2s infinite' }}/>
            <span className="font-mono text-[9px] uppercase tracking-widest" style={{ color:'#64748b' }}>Système opérationnel · 99.9% uptime</span>
          </motion.div>
        </motion.div>
      </div>

      {/* ═══ RIGHT — FORM ═══ */}
      <div className="w-full lg:w-[480px] flex items-center justify-center relative z-10 p-6 sm:p-10">
        <div className="hidden lg:block absolute left-0 top-[10%] bottom-[10%] w-[1px]"
          style={{ background:`linear-gradient(to bottom,transparent,${isDark?'rgba(255,255,255,0.06)':'rgba(0,0,0,0.06)'},transparent)` }}/>
        <motion.button onClick={toggleTheme}
          className="absolute top-5 right-5 z-30 w-10 h-10 rounded-xl flex items-center justify-center cursor-pointer"
          style={{ background:isDark?'rgba(255,255,255,0.04)':'rgba(0,0,0,0.04)',border:`1px solid ${isDark?'rgba(255,255,255,0.06)':'rgba(0,0,0,0.06)'}` }}
          whileHover={{ scale:1.1,rotate:15 }} whileTap={{ scale:0.9 }}>
          {isDark?<Sun size={15} color="#94a3b8"/>:<Moon size={15} color="#475569"/>}
        </motion.button>
        <motion.div className="relative z-10 w-full max-w-[370px]" variants={stagger} initial="hidden" animate="show">
          <motion.div variants={pop} className="mb-6">
            <h1 className="text-2xl font-black tracking-tighter" style={{ color:isDark?'#f1f5f9':'#0f172a' }}>Bon retour<span style={{ color:'#2563eb' }}>.</span></h1>
            <div className="h-5 flex items-center mt-1.5">
              <span className="text-sm" style={{ color:'#94a3b8' }}>{tagline}</span>
              <span className="inline-block w-[2px] h-3.5 ml-0.5" style={{ background:'#2563eb',animation:'loginCursorBlink 0.8s infinite' }}/>
            </div>
          </motion.div>
          <motion.form onSubmit={handleSubmit} variants={pop}>
            <motion.div layout className="rounded-2xl overflow-hidden"
              style={{ background:isDark?'rgba(15,23,42,0.7)':'rgba(255,255,255,0.85)',backdropFilter:'blur(24px)',border:`1px solid ${isDark?'rgba(255,255,255,0.05)':'rgba(0,0,0,0.06)'}`,boxShadow:isDark?'0 30px 80px rgba(0,0,0,0.5)':'0 30px 80px rgba(0,0,0,0.06)' }}>
              <div className="h-[2px]" style={{ background:'linear-gradient(90deg,#2563eb,#06b6d4,#10b981)' }}/>
              <div className="px-6 py-6 space-y-4">
                <div>
                  <label className="flex items-center gap-1.5 mb-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color:'#64748b' }}><Mail size={11}/> Email</label>
                  <motion.input type="email" className="w-full h-11 px-4 rounded-xl text-sm outline-none"
                    style={{ background:isDark?'rgba(255,255,255,0.04)':'rgba(0,0,0,0.03)',border:`1.5px solid ${focused==='email'?'#2563eb':isDark?'rgba(255,255,255,0.08)':'rgba(0,0,0,0.08)'}`,color:isDark?'#f1f5f9':'#0f172a' }}
                    animate={{ boxShadow:focused==='email'?'0 0 0 3px rgba(37,99,235,0.15)':'0 0 0 0px transparent' }}
                    transition={{ type:'spring',stiffness:300,damping:25 }}
                    placeholder="vous@autoassist.tn" value={email} onChange={e=>setEmail(e.target.value)}
                    onFocus={()=>setFocused('email')} onBlur={()=>setFocused(null)} autoFocus/>
                </div>
                <div>
                  <label className="flex items-center gap-1.5 mb-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color:'#64748b' }}><Lock size={11}/> Mot de passe</label>
                  <div className="relative">
                    <motion.input type={showPwd?'text':'password'} className="w-full h-11 px-4 pr-11 rounded-xl text-sm outline-none"
                      style={{ background:isDark?'rgba(255,255,255,0.04)':'rgba(0,0,0,0.03)',border:`1.5px solid ${focused==='password'?'#2563eb':isDark?'rgba(255,255,255,0.08)':'rgba(0,0,0,0.08)'}`,color:isDark?'#f1f5f9':'#0f172a' }}
                      animate={{ boxShadow:focused==='password'?'0 0 0 3px rgba(37,99,235,0.15)':'0 0 0 0px transparent' }}
                      transition={{ type:'spring',stiffness:300,damping:25 }}
                      placeholder="••••••••" value={password} onChange={e=>setPassword(e.target.value)}
                      onFocus={()=>setFocused('password')} onBlur={()=>setFocused(null)}/>
                    <button type="button" onClick={()=>setShowPwd(!showPwd)} className="absolute right-3 top-1/2 -translate-y-1/2 cursor-pointer" style={{color:'#64748b'}}>
                      {showPwd?<EyeOff size={16}/>:<Eye size={16}/>}</button>
                  </div>
                </div>
                <motion.button type="submit" disabled={loading}
                  className="w-full h-11 rounded-xl text-white font-semibold text-sm cursor-pointer disabled:opacity-50 flex items-center justify-center gap-2 relative overflow-hidden"
                  style={{ background:'linear-gradient(135deg,#2563eb,#1d4ed8)',boxShadow:'0 4px 20px rgba(37,99,235,0.3)' }}
                  whileHover={{ scale:1.02,boxShadow:'0 8px 35px rgba(37,99,235,0.5)' }} whileTap={{ scale:0.97 }}>
                  <motion.div className="absolute inset-0" style={{ background:'linear-gradient(105deg,transparent 40%,rgba(255,255,255,0.1) 50%,transparent 60%)' }}
                    animate={{ x:['-200%','200%'] }} transition={{ duration:3,repeat:Infinity,repeatDelay:3 }}/>
                  <AnimatePresence mode="wait">
                    {loading?<motion.div key="s" initial={{opacity:0,scale:0.5}} animate={{opacity:1,scale:1}} exit={{opacity:0,scale:0.5}} className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"/>
                    :<motion.span key="t" initial={{opacity:0,y:10}} animate={{opacity:1,y:0}} exit={{opacity:0,y:-10}} className="relative flex items-center gap-2">Se connecter →</motion.span>}
                  </AnimatePresence>
                </motion.button>
                <div className="text-center"><motion.a href="#" className="text-xs inline-block" style={{color:'#64748b'}} whileHover={{color:'#2563eb',x:2}}>Mot de passe oublié ?</motion.a></div>
              </div>
            </motion.div>
          </motion.form>
          <motion.div variants={pop} className="mt-5 flex items-center justify-center gap-2">
            <Shield size={11} color="#64748b"/><span className="font-mono text-[9px]" style={{color:'#64748b'}}>AES-256 · JWT · AURORA v2.0</span>
          </motion.div>
        </motion.div>
      </div>
    </div>
  );
}
