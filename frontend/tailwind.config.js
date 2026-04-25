/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{js,ts,jsx,tsx}'],
  darkMode: ['class', '[data-theme="dark"]'],
  theme: {
    extend: {
      colors: {
        primary: {
          DEFAULT: 'var(--primary)',
          light: 'var(--primary-light)',
          soft: 'var(--primary-soft)',
        },
        secondary: {
          DEFAULT: 'var(--secondary)',
          soft: 'var(--secondary-soft)',
        },
        surface: 'var(--surface)',
        elevated: 'var(--elevated)',
        overlay: 'var(--overlay)',
        muted: 'var(--muted)',
        success: { DEFAULT: 'var(--success)', soft: 'var(--success-soft)' },
        warning: { DEFAULT: 'var(--warning)', soft: 'var(--warning-soft)' },
        danger: { DEFAULT: 'var(--danger)', soft: 'var(--danger-soft)' },
        info: { DEFAULT: 'var(--info)', soft: 'var(--info-soft)' },
        'canal-call': 'var(--canal-call)',
        'canal-email': 'var(--canal-email)',
        'canal-web': 'var(--canal-web)',
        border: {
          DEFAULT: 'var(--border)',
          subtle: 'var(--border-subtle)',
          visible: 'var(--border-visible)',
        },
        txt: {
          DEFAULT: 'var(--text)',
          2: 'var(--text-2)',
          3: 'var(--text-3)',
        },
      },
      fontFamily: {
        sans: ['Plus Jakarta Sans', 'system-ui', 'sans-serif'],
        mono: ['JetBrains Mono', 'Fira Code', 'monospace'],
      },
      borderRadius: {
        card: '14px',
      },
      animation: {
        'pulse-slow': 'pulse 2s ease-in-out infinite',
        'mesh': 'meshMove 25s ease-in-out infinite',
        'waveform': 'wv 0.8s ease-in-out infinite',
      },
      keyframes: {
        meshMove: {
          '0%, 100%': { transform: 'translate(0, 0) scale(1)' },
          '50%': { transform: 'translate(2%, 3%) scale(1.02)' },
        },
        wv: {
          '0%, 100%': { height: '3px' },
          '50%': { height: '16px' },
        },
      },
    },
  },
  plugins: [],
};
