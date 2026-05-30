import { motion } from 'framer-motion';
import { useTheme } from '../../contexts/ThemeContext';

interface LogoProps {
  size?: number;
  animated?: boolean;
  className?: string;
}

/**
 * AutoAssist Premium SVG Logo
 * Combines a headset silhouette with AI neural-network nodes
 * Adapts automatically to dark/light theme
 */
export default function Logo({ size = 28, animated = true, className = '' }: Readonly<LogoProps>) {
  const { isDark } = useTheme();

  // Gradient IDs must be unique per instance
  const gradId = `logo-grad-${Math.random().toString(36).slice(2, 6)}`;
  const glowId = `logo-glow-${Math.random().toString(36).slice(2, 6)}`;

  const svg = (
    <svg
      width={size}
      height={size}
      viewBox="0 0 64 64"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      className={className}
    >
      <defs>
        {/* Primary blue→cyan gradient */}
        <linearGradient id={gradId} x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stopColor="#2563eb" />
          <stop offset="50%" stopColor="#3b82f6" />
          <stop offset="100%" stopColor="#06b6d4" />
        </linearGradient>

        {/* Subtle glow filter */}
        <filter id={glowId} x="-20%" y="-20%" width="140%" height="140%">
          <feGaussianBlur stdDeviation="1.5" result="blur" />
          <feMerge>
            <feMergeNode in="blur" />
            <feMergeNode in="SourceGraphic" />
          </feMerge>
        </filter>
      </defs>

      {/* ═══ Background circle ═══ */}
      <circle
        cx="32"
        cy="32"
        r="30"
        fill={isDark ? 'rgba(37, 99, 235, 0.08)' : 'rgba(37, 99, 235, 0.06)'}
        stroke={`url(#${gradId})`}
        strokeWidth="1.5"
        opacity="0.7"
      />

      {/* ═══ Headset arc (top) ═══ */}
      <path
        d="M18 30 C18 20, 46 20, 46 30"
        stroke={`url(#${gradId})`}
        strokeWidth="3"
        strokeLinecap="round"
        fill="none"
        filter={`url(#${glowId})`}
      />

      {/* ═══ Left earpiece ═══ */}
      <rect
        x="14"
        y="28"
        width="6"
        height="12"
        rx="3"
        fill={`url(#${gradId})`}
        opacity="0.9"
      />

      {/* ═══ Right earpiece ═══ */}
      <rect
        x="44"
        y="28"
        width="6"
        height="12"
        rx="3"
        fill={`url(#${gradId})`}
        opacity="0.9"
      />

      {/* ═══ Microphone arm ═══ */}
      <path
        d="M44 38 C44 46, 36 48, 32 48"
        stroke={`url(#${gradId})`}
        strokeWidth="2.5"
        strokeLinecap="round"
        fill="none"
      />

      {/* ═══ Mic tip ═══ */}
      <circle
        cx="32"
        cy="48"
        r="2.5"
        fill={`url(#${gradId})`}
      />

      {/* ═══ AI Neural nodes ═══ */}
      {/* Central brain node */}
      <circle
        cx="32"
        cy="28"
        r="3"
        fill={isDark ? '#60a5fa' : '#2563eb'}
        filter={`url(#${glowId})`}
      >
        <animate
          attributeName="r"
          values="2.5;3.5;2.5"
          dur="2s"
          repeatCount="indefinite"
        />
      </circle>

      {/* Left neural node */}
      <circle cx="25" cy="32" r="1.8" fill="#06b6d4" opacity="0.8">
        <animate
          attributeName="opacity"
          values="0.5;1;0.5"
          dur="1.8s"
          repeatCount="indefinite"
        />
      </circle>

      {/* Right neural node */}
      <circle cx="39" cy="32" r="1.8" fill="#06b6d4" opacity="0.8">
        <animate
          attributeName="opacity"
          values="0.5;1;0.5"
          dur="2.2s"
          repeatCount="indefinite"
        />
      </circle>

      {/* ═══ Neural connections ═══ */}
      <line
        x1="25" y1="32" x2="32" y2="28"
        stroke={isDark ? '#60a5fa' : '#3b82f6'}
        strokeWidth="0.8"
        opacity="0.4"
      />
      <line
        x1="39" y1="32" x2="32" y2="28"
        stroke={isDark ? '#60a5fa' : '#3b82f6'}
        strokeWidth="0.8"
        opacity="0.4"
      />

      {/* ═══ Tiny signal waves (left) ═══ */}
      <path
        d="M12 32 C10 30, 10 34, 12 32"
        stroke={`url(#${gradId})`}
        strokeWidth="1"
        fill="none"
        opacity="0.4"
      >
        <animate
          attributeName="opacity"
          values="0.2;0.6;0.2"
          dur="1.5s"
          repeatCount="indefinite"
        />
      </path>

      {/* ═══ Tiny signal waves (right) ═══ */}
      <path
        d="M52 32 C54 30, 54 34, 52 32"
        stroke={`url(#${gradId})`}
        strokeWidth="1"
        fill="none"
        opacity="0.4"
      >
        <animate
          attributeName="opacity"
          values="0.2;0.6;0.2"
          dur="1.5s"
          begin="0.5s"
          repeatCount="indefinite"
        />
      </path>
    </svg>
  );

  if (!animated) return svg;

  return (
    <motion.div
      className="flex items-center justify-center cursor-pointer"
      whileHover={{
        scale: 1.1,
        rotate: [0, -3, 3, 0],
      }}
      whileTap={{ scale: 0.93 }}
      transition={{ type: 'spring', stiffness: 400, damping: 15 }}
    >
      {svg}
    </motion.div>
  );
}
