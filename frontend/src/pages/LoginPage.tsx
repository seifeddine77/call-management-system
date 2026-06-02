import React, { useState, useEffect, useRef } from 'react';
import { safeRandom } from '../utils/cryptoRandom';
import { useAuth } from '../contexts/AuthContext';
import { useTheme } from '../contexts/ThemeContext';
import Logo from '../components/ui/Logo';
import { motion, AnimatePresence, useMotionValue, useSpring, useTransform, MotionValue } from 'framer-motion';
import toast from 'react-hot-toast';
import { Mail, Lock, Eye, EyeOff, Sun, Moon, Shield, Brain, Mic, Headphones, BarChart3, Activity, Car, Check, AlertCircle } from 'lucide-react';

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

const STATIC_PARTICLES = Array.from({ length: 20 }, (_, i) => ({
  id: `p-${i}`,
  w: 2 + safeRandom() * 3,
  h: 2 + safeRandom() * 3,
  left: safeRandom() * 100,
  top: safeRandom() * 100,
  bgRand1: 0.15 + safeRandom() * 0.2,
  bgRand2: 0.1 + safeRandom() * 0.15,
  animDur: 8 + safeRandom() * 12,
  animDelay: safeRandom() * -20,
}));

/* ── Animated Background ── */
function AnimatedBg({ isDark }: Readonly<{ isDark: boolean }>) {
  return (
    <div className="absolute inset-0 overflow-hidden pointer-events-none">
      {[
        { id: 'blur-1', w:'50vw',h:'50vw',top:'-15%',left:'-10%',c:isDark?'rgba(37,99,235,0.15)':'rgba(37,99,235,0.08)',dur:22,dx:80,dy:60 },
        { id: 'blur-2', w:'40vw',h:'40vw',bottom:'-10%',right:'-5%',c:isDark?'rgba(6,182,212,0.12)':'rgba(6,182,212,0.06)',dur:26,dx:-60,dy:-50 },
        { id: 'blur-3', w:'35vw',h:'35vw',top:'40%',left:'30%',c:isDark?'rgba(16,185,129,0.08)':'rgba(16,185,129,0.04)',dur:30,dx:40,dy:70 },
      ].map((b) => (
        <motion.div key={b.id} className="absolute rounded-full" style={{ width:b.w,height:b.h,top:b.top,bottom:b.bottom,left:b.left,right:b.right,
          background:`radial-gradient(circle, ${b.c} 0%, transparent 70%)`,filter:'blur(60px)' }}
          animate={{ x:[0,b.dx,-b.dx/2,0], y:[0,b.dy/2,-b.dy,0] }}
          transition={{ duration:b.dur, repeat:Infinity, ease:'easeInOut' }} />
      ))}
      <div className="absolute inset-0" style={{
        backgroundImage:`linear-gradient(${isDark?'rgba(59,130,246,0.03)':'rgba(37,99,235,0.04)'} 1px,transparent 1px),linear-gradient(90deg,${isDark?'rgba(59,130,246,0.03)':'rgba(37,99,235,0.04)'} 1px,transparent 1px)`,
        backgroundSize:'60px 60px',
      }}/>
      {STATIC_PARTICLES.map((p) => (
        <div key={p.id} className="absolute rounded-full" style={{
          width: `${p.w}px`, height: `${p.h}px`,
          left: `${p.left}%`, top: `${p.top}%`,
          background: isDark ? `rgba(59,130,246,${p.bgRand1})` : `rgba(37,99,235,${p.bgRand2})`,
          animation: `loginFloat ${p.animDur}s ease-in-out infinite`, animationDelay: `${p.animDelay}s`,
        }}/>
      ))}
    </div>
  );
}

/* ── Animated SVG Scene — Connected Neural Network ── */
/* ── Sub-components for LoginPage neural network to reduce cognitive complexity (S3776) ── */
interface OrbitingElementProps {
  icon: React.ComponentType<{ size: number; color: string }>;
  label: string;
  x: string;
  y: string;
  pX: MotionValue<number>;
  pY: MotionValue<number>;
  delay: number;
  color: string;
  isDark: boolean;
}

function OrbitingElement({ icon: Icon, label, x, y, pX, pY, delay, color, isDark }: Readonly<OrbitingElementProps>) {
  return (
    <motion.div className="absolute flex flex-col items-center gap-1 z-20"
      style={{ left: x, top: y, x: pX, y: pY }}
      initial={{ opacity: 0, scale: 0 }}
      animate={{ opacity: 1, scale: 1, y: [0, -5, 0] }}
      transition={{ opacity: { delay: 0.5+delay }, scale: { delay: 0.5+delay, type: 'spring', stiffness: 200 }, y: { duration: 3+delay*0.5, repeat: Infinity, ease: 'easeInOut', delay: delay } }}>
      <div className="w-11 h-11 rounded-xl flex items-center justify-center"
        style={{ background: isDark ? 'rgba(15,23,42,0.8)' : 'rgba(255,255,255,0.9)', border: `1px solid ${color}30`, backdropFilter: 'blur(8px)',
          boxShadow: `0 8px 25px ${isDark ? 'rgba(0,0,0,0.3)' : 'rgba(0,0,0,0.08)'}` }}>
        <Icon size={18} color={color} />
      </div>
      <span className="text-[8px] font-mono tracking-wider whitespace-nowrap" style={{ color: isDark ? '#94a3b8' : '#64748b' }}>{label}</span>
    </motion.div>
  );
}

function ConnectionLines({ lineColor }: Readonly<{ lineColor: string }>) {
  const paths = [
    'M 70 80 Q 200 120 250 180', 'M 380 65 Q 300 100 250 180',
    'M 395 260 Q 320 230 250 180', 'M 65 270 Q 150 240 250 180',
    'M 250 30 Q 250 100 250 180', 'M 240 320 Q 245 250 250 180',
    'M 410 150 Q 320 160 250 180',
  ];
  return (
    <>
      {paths.map((d, i) => (
        <motion.path key={d} d={d} fill="none" stroke={lineColor} strokeWidth="1.5" strokeDasharray="6 4"
          initial={{ pathLength: 0, opacity: 0 }}
          animate={{ pathLength: 1, opacity: 1 }}
          transition={{ duration: 1.5, delay: 0.3+i*0.15, ease: 'easeOut' }} />
      ))}
    </>
  );
}

function TravelingDots({ claimColor, accent }: Readonly<{ claimColor: string; accent: string }>) {
  const paths = [
    'M 70 80 Q 200 120 250 180', 'M 380 65 Q 300 100 250 180',
    'M 395 260 Q 320 230 250 180', 'M 65 270 Q 150 240 250 180',
    'M 410 150 Q 320 160 250 180',
  ];
  return (
    <>
      {paths.map((d, i) => (
        <motion.circle key={`dot-${d}`} r="3" fill={i === 4 ? claimColor : accent}
          initial={{ offsetDistance: '0%' }}
          animate={{ offsetDistance: ['0%', '100%'] }}
          transition={{ duration: 2.5, repeat: Infinity, delay: i*0.6, ease: 'linear' }}
          style={{ offsetPath: `path('${d}')` }} />
      ))}
    </>
  );
}

/* ── Animated Background Scene ── */
function AnimatedScene({ isDark, sx, sy }: Readonly<{ isDark: boolean; sx: MotionValue<number>; sy: MotionValue<number> }>) {
  const carX = useTransform(sx, [-1,1], [-12,12]);
  const carY = useTransform(sy, [-1,1], [-8,8]);
  const el1X = useTransform(sx, [-1,1], [8,-8]);
  const el1Y = useTransform(sy, [-1,1], [6,-6]);
  const el2X = useTransform(sx, [-1,1], [-6,6]);
  const el2Y = useTransform(sy, [-1,1], [-4,4]);
  const accent = isDark ? '#3b82f6' : '#2563eb';
  const accent2 = isDark ? '#06b6d4' : '#0891b2';
  const accent3 = isDark ? '#10b981' : '#059669';
  const claimColor = '#f43f5e'; // Rose/coral highlighting auto claims
  const lineColor = isDark ? 'rgba(59,130,246,0.15)' : 'rgba(37,99,235,0.12)';

  const orbitElements = [
    { icon: Headphones, label: "Centre d'appels", x: '10%', y: '20%', pX: el1X, pY: el1Y, delay: 0, color: accent },
    { icon: Brain, label: 'IA Gemma', x: '72%', y: '15%', pX: el2X, pY: el2Y, delay: 0.5, color: accent2 },
    { icon: Mic, label: 'Whisper STT', x: '75%', y: '70%', pX: el1X, pY: el2Y, delay: 1, color: accent3 },
    { icon: BarChart3, label: 'Analytics', x: '8%', y: '72%', pX: el2X, pY: el1Y, delay: 1.5, color: accent },
    { icon: Activity, label: 'Temps réel', x: '45%', y: '5%', pX: el1X, pY: el1Y, delay: 0.8, color: accent2 },
    { icon: Shield, label: 'Sécurité', x: '42%', y: '88%', pX: el2X, pY: el2Y, delay: 1.2, color: accent3 },
    { icon: Car, label: 'Sinistres Auto', x: '82%', y: '42%', pX: el2X, pY: el1Y, delay: 0.7, color: claimColor },
  ];

  return (
    <div className="relative w-full max-w-[500px] mx-auto" style={{ height: 360 }}>
      {/* Central Interactive Logo Portal */}
      <motion.div className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 z-10"
        style={{ x: carX, y: carY }}
        animate={{ y: [0, -8, 0] }}
        transition={{ duration: 5, repeat: Infinity, ease: 'easeInOut' }}>
        <div className="w-32 h-32 rounded-full flex items-center justify-center p-0 m-0 border backdrop-blur-md relative overflow-hidden"
          style={{ 
            background: isDark ? 'rgba(15, 23, 42, 0.45)' : 'rgba(255, 255, 255, 0.55)',
            borderColor: isDark ? 'rgba(59, 130, 246, 0.25)' : 'rgba(37, 99, 235, 0.15)',
            boxShadow: isDark 
              ? '0 20px 50px rgba(6, 182, 212, 0.15), inset 0 0 20px rgba(59, 130, 246, 0.1)' 
              : '0 20px 50px rgba(37, 99, 235, 0.08), inset 0 0 20px rgba(255, 255, 255, 0.5)'
          }}>
          {/* Neon radial glow behind logo */}
          <div className="absolute inset-4 rounded-full blur-xl opacity-30"
            style={{ background: 'linear-gradient(135deg, #2563eb, #06b6d4)' }}
          />
          <Logo size={68} animated={false} className="relative z-10 block w-[68px] h-[68px] flex-shrink-0" />
        </div>
        {/* Pulse rings around logo */}
        {[1,2,3].map(r => (
          <motion.div key={r} className="absolute inset-0 rounded-full border"
            style={{ 
              borderColor: isDark ? 'rgba(6, 182, 212, 0.15)' : 'rgba(37, 99, 235, 0.08)',
              boxShadow: isDark ? '0 0 15px rgba(6, 182, 212, 0.05)' : 'none'
            }}
            animate={{ scale: [1, 1.4+r*0.25], opacity: [0.45, 0] }}
            transition={{ duration: 2.5+r*0.6, repeat: Infinity, delay: r*0.5, ease: 'easeOut' }} />
        ))}
      </motion.div>

      {/* Orbiting elements */}
      {orbitElements.map((el) => (
        <OrbitingElement key={`orbit-${el.label}`} icon={el.icon} label={el.label} x={el.x} y={el.y} pX={el.pX} pY={el.pY} delay={el.delay} color={el.color} isDark={isDark} />
      ))}

      {/* Animated connection lines (SVG) */}
      <svg className="absolute inset-0 w-full h-full z-0" viewBox="0 0 500 360">
        <ConnectionLines lineColor={lineColor} />
        <TravelingDots claimColor={claimColor} accent={accent} />
      </svg>
    </div>
  );
}

const stagger = { hidden:{opacity:0},show:{opacity:1,transition:{staggerChildren:0.08,delayChildren:0.2}} } as const;
const pop = { hidden:{opacity:0,y:20,scale:0.95},show:{opacity:1,y:0,scale:1,transition:{type:'spring' as const,stiffness:300,damping:24}} };

const computeInputStyle = (
  field: 'email' | 'password',
  error: string | null,
  touched: Record<string, boolean>,
  focused: string | null,
  authError: string | null,
  isDark: boolean
) => {
  const isTouched = touched[field];
  const isFocused = focused === field;
  
  let borderColor = isDark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)';
  if (isFocused) {
    borderColor = '#2563eb';
  } else if (authError) {
    borderColor = '#ef4444';
  } else if (isTouched) {
    borderColor = error ? '#ef4444' : '#10b981';
  }
  
  return {
    background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)',
    border: `1.5px solid ${borderColor}`,
    color: isDark ? '#f1f5f9' : '#0f172a'
  };
};

const computeIconColor = (
  field: 'email' | 'password',
  error: string | null,
  touched: Record<string, boolean>,
  focused: string | null,
  authError: string | null
) => {
  const isTouched = touched[field];
  const isFocused = focused === field;
  if (isFocused) return '#2563eb';
  if (authError) return '#ef4444';
  if (isTouched) return error ? '#ef4444' : '#10b981';
  return '#64748b';
};

const computeBoxShadow = (
  field: 'email' | 'password',
  error: string | null,
  touched: Record<string, boolean>,
  focused: string | null,
  authError: string | null
) => {
  if (focused === field) return '0 0 0 3px rgba(37,99,235,0.12)';
  if (touched[field] || authError) {
    return (error || authError) ? '0 0 0 3px rgba(239,68,68,0.1)' : '0 0 0 3px rgba(16,185,129,0.1)';
  }
  return '0 0 0 0px transparent';
};

interface LeftPanelProps {
  isDark: boolean;
  sx: MotionValue<number>;
  sy: MotionValue<number>;
}

/* ── Email field sub-component ── */
interface EmailFieldProps {
  email: string;
  focused: string | null;
  touched: Record<string, boolean>;
  authError: string | null;
  getEmailError: () => string | null;
  getInputStyle: (f: 'email'|'password', e: string|null) => object;
  getIconColor: (f: 'email'|'password', e: string|null) => string;
  getBoxShadow: (f: 'email'|'password', e: string|null) => string;
  onEmailChange: (v: string) => void;
  onFocus: () => void;
  onBlur: () => void;
}
function EmailField({ email, focused, touched, authError, getEmailError, getInputStyle, getIconColor, getBoxShadow, onEmailChange, onFocus, onBlur }: Readonly<EmailFieldProps>) {
  const emailErr = getEmailError();
  const showBadge = (touched.email && focused !== 'email') || !!authError;
  return (
    <div>
      <label htmlFor="email-input" className="block mb-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color:'#64748b' }}>Identifiant</label>
      <div className="relative group">
        <div className="absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-200" style={{ color: getIconColor('email', emailErr) }}>
          <Mail size={16} />
        </div>
        <motion.input id="email-input" type="email" className="w-full h-11 pl-11 pr-10 rounded-xl text-sm outline-none transition-all duration-200"
          style={getInputStyle('email', emailErr)}
          animate={{ boxShadow: getBoxShadow('email', emailErr) }}
          transition={{ type:'spring',stiffness:300,damping:25 }}
          placeholder="vous@autoassist.tn" value={email} onChange={e=>onEmailChange(e.target.value)}
          onFocus={onFocus} onBlur={onBlur} autoFocus/>
        {showBadge && (
          <div className="absolute right-3.5 top-1/2 -translate-y-1/2">
            {(emailErr || authError) ? (
              <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 400, damping: 15 }}>
                <AlertCircle size={16} color="#ef4444" />
              </motion.div>
            ) : (
              <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 400, damping: 15 }}
                className="w-5 h-5 rounded-full flex items-center justify-center" style={{ background: 'rgba(16,185,129,0.15)' }}>
                <Check size={12} color="#10b981" strokeWidth={3} />
              </motion.div>
            )}
          </div>
        )}
      </div>
      <AnimatePresence>
        {touched.email && focused !== 'email' && emailErr && (
          <motion.p initial={{ opacity: 0, y: -5, height: 0 }} animate={{ opacity: 1, y: 0, height: 'auto' }} exit={{ opacity: 0, y: -5, height: 0 }}
            transition={{ duration: 0.2 }}
            className="mt-1.5 text-[11px] flex items-center gap-1" style={{ color: '#ef4444' }}>
            <AlertCircle size={11} /> {emailErr}
          </motion.p>
        )}
      </AnimatePresence>
    </div>
  );
}

/* ── Password field sub-component ── */
interface PasswordFieldProps {
  password: string;
  showPwd: boolean;
  focused: string | null;
  touched: Record<string, boolean>;
  authError: string | null;
  getPasswordError: () => string | null;
  getInputStyle: (f: 'email'|'password', e: string|null) => object;
  getIconColor: (f: 'email'|'password', e: string|null) => string;
  getBoxShadow: (f: 'email'|'password', e: string|null) => string;
  onPasswordChange: (v: string) => void;
  onFocus: () => void;
  onBlur: () => void;
  onTogglePwd: () => void;
}
function PasswordField({ password, showPwd, focused, touched, authError, getPasswordError, getInputStyle, getIconColor, getBoxShadow, onPasswordChange, onFocus, onBlur, onTogglePwd }: Readonly<PasswordFieldProps>) {
  const pwdErr = getPasswordError();
  const showBadge = (touched.password && focused !== 'password') || !!authError;
  return (
    <div>
      <label htmlFor="password-input" className="block mb-1.5 text-[11px] font-semibold uppercase tracking-wider" style={{ color:'#64748b' }}>Mot de passe</label>
      <div className="relative group">
        <div className="absolute left-3.5 top-1/2 -translate-y-1/2 transition-colors duration-200" style={{ color: getIconColor('password', pwdErr) }}>
          <Lock size={16} />
        </div>
        <motion.input id="password-input" type={showPwd?'text':'password'} className="w-full h-11 pl-11 pr-20 rounded-xl text-sm outline-none transition-all duration-200"
          style={getInputStyle('password', pwdErr)}
          animate={{ boxShadow: getBoxShadow('password', pwdErr) }}
          transition={{ type:'spring',stiffness:300,damping:25 }}
          placeholder="••••••••" value={password} onChange={e=>onPasswordChange(e.target.value)}
          onFocus={onFocus} onBlur={onBlur}/>
        <div className="absolute right-3.5 top-1/2 -translate-y-1/2 flex items-center gap-2">
          {showBadge && (
            (pwdErr || authError) ? (
              <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 400, damping: 15 }}>
                <AlertCircle size={16} color="#ef4444" />
              </motion.div>
            ) : (
              <motion.div initial={{ scale: 0 }} animate={{ scale: 1 }} transition={{ type: 'spring', stiffness: 400, damping: 15 }}
                className="w-5 h-5 rounded-full flex items-center justify-center" style={{ background: 'rgba(16,185,129,0.15)' }}>
                <Check size={12} color="#10b981" strokeWidth={3} />
              </motion.div>
            )
          )}
          <button type="button" onClick={onTogglePwd} className="cursor-pointer transition-colors" style={{color:'#64748b'}}>
            {showPwd?<EyeOff size={16}/>:<Eye size={16}/>}
          </button>
        </div>
      </div>
      <AnimatePresence>
        {((touched.password && focused !== 'password' && pwdErr) || authError) && (
          <motion.p initial={{ opacity: 0, y: -5, height: 0 }} animate={{ opacity: 1, y: 0, height: 'auto' }} exit={{ opacity: 0, y: -5, height: 0 }}
            transition={{ duration: 0.2 }}
            className="mt-1.5 text-[11px] flex items-center gap-1" style={{ color: '#ef4444' }}>
            <AlertCircle size={11} /> {pwdErr || authError}
          </motion.p>
        )}
      </AnimatePresence>
    </div>
  );
}

function LeftPanel({ isDark, sx, sy }: Readonly<LeftPanelProps>) {
  return (
    <div className="hidden lg:flex flex-1 items-center justify-center relative z-10">
      <motion.div className="flex flex-col items-center max-w-[550px] px-6 w-full"
        variants={stagger} initial="hidden" animate="show">
        <motion.div variants={pop} className="flex items-center gap-3.5 self-start mb-4">
          <div className="w-12 h-12 rounded-2xl flex items-center justify-center bg-[var(--elevated)] border border-[var(--border)] relative flex-shrink-0 overflow-hidden shadow-md" style={{ background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)' }}>
            <Logo size={38} animated={true} />
          </div>
          <span className="font-black text-2xl tracking-tight" style={{ color:isDark?'#f1f5f9':'#0f172a', letterSpacing: '-0.03em' }}>AutoAssist</span>
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

        {/* Glowing stats cards below the title */}
        <motion.div variants={pop} className="grid grid-cols-3 gap-3 w-full mt-8 font-mono text-[9px] text-center">
          <div className="p-3 rounded-2xl bg-[rgba(37,99,235,0.03)] dark:bg-[rgba(37,99,235,0.05)] border border-[rgba(37,99,235,0.1)] backdrop-blur-md">
            <div className="text-xs font-black text-blue-500">Gemma IA</div>
            <div className="text-slate-400 mt-0.5">Moteur Décisionnel</div>
          </div>
          <div className="p-3 rounded-2xl bg-[rgba(6,182,212,0.03)] dark:bg-[rgba(6,182,212,0.05)] border border-[rgba(6,182,212,0.1)] backdrop-blur-md">
            <div className="text-xs font-black text-cyan-500">Whisper AI</div>
            <div className="text-slate-400 mt-0.5">Transcription Vocale</div>
          </div>
          <div className="p-3 rounded-2xl bg-[rgba(16,185,129,0.03)] dark:bg-[rgba(16,185,129,0.05)] border border-[rgba(16,185,129,0.1)] backdrop-blur-md">
            <div className="text-xs font-black text-emerald-500">&lt; 2.5s</div>
            <div className="text-slate-400 mt-0.5">Traitement Pipeline</div>
          </div>
        </motion.div>
      </motion.div>
    </div>
  );
}

interface LoginFormHeaderProps {
  isDark: boolean;
  tagline: string;
}

function LoginFormHeader({ isDark, tagline }: Readonly<LoginFormHeaderProps>) {
  return (
    <motion.div variants={pop} className="mb-6">
      <div className="flex items-center gap-4 mb-4">
        <div className="w-14 h-14 rounded-2xl flex items-center justify-center bg-[var(--elevated)] border border-[var(--border)] relative flex-shrink-0 overflow-hidden shadow-lg" style={{ background: isDark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)' }}>
          <Logo size={44} animated={true} />
        </div>
        <div>
          <div className="text-base font-black tracking-tight" style={{ color: isDark ? '#f1f5f9' : '#0f172a' }}>AutoAssist</div>
          <div className="font-mono text-[8px] text-[#64748b] tracking-wider uppercase">Système Décisionnel</div>
        </div>
      </div>
      <h1 className="text-2xl font-black tracking-tighter" style={{ color:isDark?'#f1f5f9':'#0f172a' }}>Bon retour<span style={{ color:'#2563eb' }}>.</span></h1>
      <div className="h-5 flex items-center mt-1.5">
        <span className="text-sm" style={{ color:'#94a3b8' }}>{tagline}</span>
        <span className="inline-block w-[2px] h-3.5 ml-0.5" style={{ background:'#2563eb',animation:'loginCursorBlink 0.8s infinite' }}/>
      </div>
    </motion.div>
  );
}

function useLoginState() {
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

  // Real-time touched tracking & validation
  const [touched, setTouched] = useState<Record<string, boolean>>({});
  const [authError, setAuthError] = useState<string | null>(null);

  const handleBlur = (field: string) => {
    setTouched(prev => ({ ...prev, [field]: true }));
    setFocused(null);
  };

  const getEmailError = () => {
    if (!email) return "L'identifiant est requis";
    return null;
  };

  const getPasswordError = () => {
    if (!password) return "Le mot de passe est requis";
    return null;
  };

  const handleEmailChange = (val: string) => {
    setEmail(val);
    setAuthError(null);
  };

  const handlePasswordChange = (val: string) => {
    setPassword(val);
    setAuthError(null);
  };

  const getInputStyle = (field: 'email' | 'password', error: string | null) => 
    computeInputStyle(field, error, touched, focused, authError, isDark);

  const getIconColor = (field: 'email' | 'password', error: string | null) => 
    computeIconColor(field, error, touched, focused, authError);

  const getBoxShadow = (field: 'email' | 'password', error: string | null) => 
    computeBoxShadow(field, error, touched, focused, authError);

  // Ref and Motion values for 3D Tilt Card & Spotlight Glow
  const cardRef = useRef<HTMLDivElement>(null);
  const [mousePos, setMousePos] = useState({ x: 0, y: 0 });
  const [isHovered, setIsHovered] = useState(false);

  const cardX = useMotionValue(0);
  const cardY = useMotionValue(0);
  // Smooth spring animations for rotation
  const rotateX = useSpring(useTransform(cardY, [-0.5, 0.5], [8, -8]), { stiffness: 120, damping: 22 });
  const rotateY = useSpring(useTransform(cardX, [-0.5, 0.5], [-8, 8]), { stiffness: 120, damping: 22 });

  const handleMouseMove = (e: React.MouseEvent<HTMLDivElement>) => {
    if (!cardRef.current) return;
    const rect = cardRef.current.getBoundingClientRect();
    const xVal = (e.clientX - rect.left) / rect.width - 0.5;
    const yVal = (e.clientY - rect.top) / rect.height - 0.5;
    cardX.set(xVal);
    cardY.set(yVal);
    setMousePos({ x: e.clientX - rect.left, y: e.clientY - rect.top });
  };

  const handleMouseEnter = () => setIsHovered(true);
  const handleMouseLeave = () => {
    setIsHovered(false);
    cardX.set(0);
    cardY.set(0);
  };

  const handleSubmit = async (e: React.FormEvent<HTMLFormElement>) => {
    e.preventDefault();
    setTouched({ email: true, password: true });
    setAuthError(null);
    
    const emailErr = getEmailError();
    const pwdErr = getPasswordError();
    
    if (emailErr) { toast.error(emailErr); return; }
    if (pwdErr) { toast.error(pwdErr); return; }
    
    setLoading(true);
    try { 
      await login(email, password); 
      toast.success('Connexion réussie !'); 
    }
    catch (err: unknown) { 
      const msg = err instanceof Error ? err.message : 'Identifiants invalides';
      setAuthError(msg);
      toast.error('Email ou mot de passe incorrect'); 
    }
    finally { setLoading(false); }
  };

  return {
    email,
    password,
    loading,
    showPwd,
    focused,
    isDark,
    sx,
    sy,
    tagline,
    touched,
    authError,
    handleBlur,
    getEmailError,
    getPasswordError,
    handleEmailChange,
    handlePasswordChange,
    getInputStyle,
    getIconColor,
    getBoxShadow,
    cardRef,
    mousePos,
    isHovered,
    rotateX,
    rotateY,
    handleMouseMove,
    handleMouseEnter,
    handleMouseLeave,
    handleSubmit,
    toggleTheme,
    setShowPwd,
    setFocused
  };
}

export default function LoginPage() {
  const {
    email,
    password,
    loading,
    showPwd,
    focused,
    isDark,
    sx,
    sy,
    tagline,
    touched,
    authError,
    handleBlur,
    getEmailError,
    getPasswordError,
    handleEmailChange,
    handlePasswordChange,
    getInputStyle,
    getIconColor,
    getBoxShadow,
    cardRef,
    mousePos,
    isHovered,
    rotateX,
    rotateY,
    handleMouseMove,
    handleMouseEnter,
    handleMouseLeave,
    handleSubmit,
    toggleTheme,
    setShowPwd,
    setFocused
  } = useLoginState();

  return (
    <div className="min-h-screen flex relative overflow-hidden" style={{ background: isDark ? '#060b18' : '#f0f4f8' }}>
      <AnimatedBg isDark={isDark} />

      <LeftPanel isDark={isDark} sx={sx} sy={sy} />

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

        {/* 3D Perspective Card Wrapper */}
        <motion.div style={{ perspective: 1000 }} className="relative z-10 w-full max-w-[370px]" variants={stagger} initial="hidden" animate="show">
          <LoginFormHeader isDark={isDark} tagline={tagline} />
          <motion.form onSubmit={handleSubmit} variants={pop}>
            
            {/* 3D Tilting Card Container */}
            <motion.div
              ref={cardRef}
              onMouseMove={handleMouseMove}
              onMouseEnter={handleMouseEnter}
              onMouseLeave={handleMouseLeave}
              style={{
                rotateX: rotateX,
                rotateY: rotateY,
                transformStyle: 'preserve-3d',
              }}
              className="relative p-[1.5px] rounded-2xl overflow-hidden bg-gradient-to-r from-blue-500/40 via-cyan-500/40 to-emerald-500/40 dark:from-blue-500/30 dark:via-cyan-500/30 dark:to-emerald-500/30 shadow-[0_20px_50px_rgba(37,99,235,0.05)] dark:shadow-[0_20px_50px_rgba(0,0,0,0.3)] transition-all duration-300"
            >
              {/* Dynamic Interactive Cursor Spotlight */}
              {isHovered && (
                <div
                  className="absolute pointer-events-none rounded-full"
                  style={{
                    width: '320px',
                    height: '320px',
                    background: isDark
                      ? 'radial-gradient(circle, rgba(59,130,246,0.18) 0%, transparent 70%)'
                      : 'radial-gradient(circle, rgba(37,99,235,0.1) 0%, transparent 70%)',
                    left: `${mousePos.x - 160}px`,
                    top: `${mousePos.y - 160}px`,
                    filter: 'blur(25px)',
                    zIndex: 1,
                  }}
                />
              )}

              {/* Inside Glass Card Body */}
              <div className="relative rounded-[15px] overflow-hidden z-10"
                style={{
                  background:isDark?'rgba(10,15,30,0.92)':'rgba(255,255,255,0.97)',
                  backdropFilter:'blur(30px)'
                }}>
                <div className="h-[2px]" style={{ background:'linear-gradient(90deg,#2563eb,#06b6d4,#10b981)' }}/>
                <div className="px-6 py-6 space-y-4" style={{ transform: 'translateZ(20px)' }}>
                  
                  <EmailField
                    email={email}
                    focused={focused}
                    touched={touched}
                    authError={authError}
                    getEmailError={getEmailError}
                    getInputStyle={getInputStyle}
                    getIconColor={getIconColor}
                    getBoxShadow={getBoxShadow}
                    onEmailChange={handleEmailChange}
                    onFocus={() => setFocused('email')}
                    onBlur={() => handleBlur('email')}
                  />
                  <PasswordField
                    password={password}
                    showPwd={showPwd}
                    focused={focused}
                    touched={touched}
                    authError={authError}
                    getPasswordError={getPasswordError}
                    getInputStyle={getInputStyle}
                    getIconColor={getIconColor}
                    getBoxShadow={getBoxShadow}
                    onPasswordChange={handlePasswordChange}
                    onFocus={() => setFocused('password')}
                    onBlur={() => handleBlur('password')}
                    onTogglePwd={() => setShowPwd(!showPwd)}
                  />

                  {/* Submit Button with Internal Gleam Animation */}
                  <motion.button type="submit" disabled={loading}
                    className="w-full h-11 rounded-xl text-white font-semibold text-sm cursor-pointer disabled:opacity-50 flex items-center justify-center gap-2 relative overflow-hidden mt-2"
                    style={{ background:'linear-gradient(135deg,#2563eb,#1d4ed8)',boxShadow:'0 4px 20px rgba(37,99,235,0.3)' }}
                    whileHover={{ scale:1.02,boxShadow:'0 8px 30px rgba(37,99,235,0.5)' }} whileTap={{ scale:0.98 }}>
                    <motion.div className="absolute inset-0" style={{ background:'linear-gradient(105deg,transparent 40%,rgba(255,255,255,0.12) 50%,transparent 60%)' }}
                      animate={{ x:['-200%','200%'] }} transition={{ duration:3,repeat:Infinity,repeatDelay:3 }}/>
                    <AnimatePresence mode="wait">
                      {loading
                        ? <motion.div key="s" initial={{opacity:0,scale:0.5}} animate={{opacity:1,scale:1}} exit={{opacity:0,scale:0.5}} className="w-5 h-5 border-2 border-white/30 border-t-white rounded-full animate-spin"/>
                        : <motion.span key="t" initial={{opacity:0,y:10}} animate={{opacity:1,y:0}} exit={{opacity:0,y:-10}} className="relative flex items-center gap-2">Se connecter →</motion.span>
                      }
                    </AnimatePresence>
                  </motion.button>
                  
                  <div className="text-center pt-1">
                    <motion.a href="#" className="text-xs inline-block hover:underline" style={{color:'#64748b'}} whileHover={{color:'#2563eb',x:2}}>
                      Mot de passe oublié ?
                    </motion.a>
                  </div>
                </div>
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
