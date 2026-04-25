import { Outlet, useLocation } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import Sidebar from './Sidebar';
import Topbar from './Topbar';

export default function AppShell() {
  const location = useLocation();

  return (
    <div className="mesh-bg flex h-screen overflow-hidden relative">
      {/* Ambient orbs */}
      <div className="orb" style={{ width: 500, height: 500, background: 'rgba(99,91,255,0.05)', top: -120, left: 220 }} />
      <div className="orb" style={{ width: 350, height: 350, background: 'rgba(0,212,170,0.05)', bottom: 0, right: 150 }} />
      <div className="orb" style={{ width: 200, height: 200, background: 'rgba(167,139,250,0.03)', top: 300, right: 80 }} />

      {/* Sidebar */}
      <Sidebar />

      {/* Main area */}
      <div className="flex-1 flex flex-col min-w-0 relative z-[1]">
        <Topbar />
        <AnimatePresence mode="wait">
          <motion.main
            key={location.pathname}
            className="flex-1 overflow-y-auto p-6"
            initial={{ opacity: 0, y: 12 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -8 }}
            transition={{ duration: 0.2, ease: 'easeOut' }}
          >
            <Outlet />
          </motion.main>
        </AnimatePresence>
      </div>
    </div>
  );
}
