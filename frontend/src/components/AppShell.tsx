import { useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'react-router-dom';
import { AnimatePresence, motion } from 'framer-motion';
import { Toaster } from 'react-hot-toast';
import Sidebar, { SidebarProvider } from './Sidebar';
import Topbar from './Topbar';
import CommandPalette from './ui/CommandPalette';

export default function AppShell() {
  const location = useLocation();
  const navigate = useNavigate();

  // Redirect root to dashboard
  useEffect(() => {
    if (location.pathname === '/') navigate('/dashboard', { replace: true });
  }, [location.pathname, navigate]);

  return (
    <SidebarProvider>
      <div className="h-screen flex bg-[var(--bg)] text-[var(--text)] mesh-bg relative overflow-hidden">
        {/* Ambient orbs — fixed, behind everything */}
        <div className="orb w-[280px] h-[280px] bg-[rgba(99,91,255,0.05)] hidden md:block" style={{ top: '10%', left: '15%' }} />
        <div className="orb w-[220px] h-[220px] bg-[rgba(0,212,170,0.04)] hidden md:block" style={{ bottom: '15%', right: '10%', animationDelay: '7s' }} />
        <div className="orb w-[180px] h-[180px] bg-[rgba(167,139,250,0.035)] hidden md:block" style={{ top: '55%', left: '60%', animationDelay: '12s' }} />

        {/* Sidebar */}
        <Sidebar />

        {/* Main area */}
        <div className="flex-1 flex flex-col min-w-0 relative z-[1]">
          <Topbar />
          <main className="flex-1 overflow-y-auto p-3 md:p-5 mobile-main">
            <AnimatePresence mode="wait">
              <motion.div
                key={location.pathname}
                initial={{ opacity: 0, y: 8 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0, y: -4 }}
                transition={{ duration: 0.2, ease: [0.22, 1, 0.36, 1] }}
              >
                <Outlet />
              </motion.div>
            </AnimatePresence>
          </main>
        </div>

        {/* Command Palette ⌘K */}
        <CommandPalette />

        {/* Global toaster */}
        <Toaster
          position="bottom-right"
          toastOptions={{
            duration: 3500,
            style: {
              background: 'var(--surface)',
              color: 'var(--text)',
              border: '1px solid var(--border)',
              fontSize: '13px',
              fontFamily: 'var(--font)',
              borderRadius: '12px',
              boxShadow: '0 8px 24px rgba(0,0,0,0.15)',
            },
          }}
        />
      </div>
    </SidebarProvider>
  );
}
