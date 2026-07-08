import { NavLink } from 'react-router-dom';
import { Home, ClipboardList, Camera, LineChart } from 'lucide-react';

export default function BottomNav() {
  const user = JSON.parse(localStorage.getItem('gl_user') || '{}');
  const userInitials = user.name
    ? user.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
    : 'U';

  return (
    <nav className="bottom-nav">
      {/* Tab 1: Dashboard/Home */}
      <NavLink
        to="/"
        className={({ isActive }) => `bottom-nav-item ${isActive ? 'active' : ''}`}
        end
      >
        <Home size={22} strokeWidth={2} />
        <span>Home</span>
      </NavLink>

      {/* Tab 2: Task Ledger */}
      <NavLink
        to="/ledger"
        className={({ isActive }) => `bottom-nav-item ${isActive ? 'active' : ''}`}
      >
        <ClipboardList size={22} strokeWidth={2} />
        <span>Ledger</span>
      </NavLink>

      {/* Tab 3: Capture (Center raised button) */}
      <NavLink
        to="/capture"
        className={({ isActive }) => `bottom-nav-item bottom-nav-item--capture ${isActive ? 'active' : ''}`}
      >
        <div className="capture-btn-inner">
          <Camera size={26} strokeWidth={2.5} />
        </div>
      </NavLink>

      {/* Tab 4: Analytics */}
      <NavLink
        to="/analytics"
        className={({ isActive }) => `bottom-nav-item ${isActive ? 'active' : ''}`}
      >
        <LineChart size={22} strokeWidth={2} />
        <span>Analytics</span>
      </NavLink>

      {/* Tab 5: Profile */}
      <NavLink
        to="/profile"
        className={({ isActive }) => `bottom-nav-item ${isActive ? 'active' : ''}`}
      >
        <div style={{
          width: 24,
          height: 24,
          borderRadius: '50%',
          border: '2px solid currentColor',
          display: 'flex',
          alignItems: 'center',
          justify-content: 'center',
          fontSize: '0.625rem',
          fontWeight: 800,
          background: 'rgba(255, 255, 255, 0.05)',
        }}>
          {userInitials}
        </div>
        <span>Profile</span>
      </NavLink>
    </nav>
  );
}
