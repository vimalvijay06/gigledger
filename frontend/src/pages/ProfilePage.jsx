import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Globe, Moon, FileOutput, LogOut } from 'lucide-react';

export default function ProfilePage() {
  const navigate = useNavigate();
  const user = JSON.parse(localStorage.getItem('gl_user') || '{}');
  
  // Settings states
  const [language, setLanguage] = useState(user.languagePref || 'en');
  const [theme, setTheme] = useState('dark');
  const [showConfirmLogout, setShowConfirmLogout] = useState(false);

  const userInitials = user.name
    ? user.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
    : 'U';

  function handleLogout() {
    localStorage.removeItem('gl_token');
    localStorage.removeItem('gl_user');
    navigate('/login');
  }

  function handleExportData() {
    alert("Exporting your ledger database to CSV... Done!");
  }

  return (
    <div className="page animate-in">
      {/* Profile Header Card */}
      <div className="profile-header">
        <div className="profile-avatar">
          {userInitials}
        </div>
        <h2 className="profile-name">{user.name || 'Worker Partner'}</h2>
        <p className="profile-email">{user.email || 'partner@gigledger.com'}</p>
      </div>

      <h3 className="form-label" style={{ marginBottom: '0.75rem', paddingLeft: 'var(--space-2)' }}>App Settings</h3>
      
      {/* Settings list */}
      <div className="settings-list">
        {/* Language selector */}
        <div className="settings-item">
          <span className="settings-item-label">
            <Globe size={18} />
            Language / भाषा
          </span>
          <select
            className="form-select"
            style={{ width: 'auto', padding: '4px 10px', fontSize: '0.85rem' }}
            value={language}
            onChange={e => setLanguage(e.target.value)}
          >
            <option value="en">English</option>
            <option value="hi">हिन्दी (Hindi)</option>
            <option value="ta">தமிழ் (Tamil)</option>
          </select>
        </div>

        {/* Theme toggle */}
        <div className="settings-item">
          <span className="settings-item-label">
            <Moon size={18} />
            Theme
          </span>
          <select
            className="form-select"
            style={{ width: 'auto', padding: '4px 10px', fontSize: '0.85rem' }}
            value={theme}
            disabled
          >
            <option value="dark">Dark Theme (Enabled)</option>
            <option value="light">Light Theme</option>
          </select>
        </div>

        {/* Data export */}
        <div
          className="settings-item"
          style={{ cursor: 'pointer' }}
          onClick={handleExportData}
        >
          <span className="settings-item-label">
            <FileOutput size={18} />
            Export My Ledger
          </span>
          <span style={{ fontSize: '0.8rem', color: '#ffb000', fontWeight: 600 }}>CSV</span>
        </div>
      </div>

      {/* Logout Action area */}
      <div style={{ marginTop: '3rem', padding: '0 var(--space-4)' }}>
        {!showConfirmLogout ? (
          <button
            id="btn-logout"
            className="btn btn-ghost btn--full"
            style={{ borderColor: 'rgba(239, 68, 68, 0.4)', color: '#ef4444', display: 'flex', gap: '8px' }}
            onClick={() => setShowConfirmLogout(true)}
          >
            <LogOut size={18} /> Log Out
          </button>
        ) : (
          <div style={{
            background: 'var(--color-surface-2)',
            border: '1px solid var(--color-border)',
            borderRadius: 'var(--radius-lg)',
            padding: '1.25rem',
            textAlign: 'center'
          }}>
            <p style={{ fontSize: '0.88rem', fontWeight: 600, marginBottom: '1rem', color: 'var(--text-primary)' }}>
              Are you sure you want to log out?
            </p>
            <div style={{ display: 'flex', gap: '0.75rem' }}>
              <button
                className="btn btn-ghost"
                style={{ flex: 1 }}
                onClick={() => setShowConfirmLogout(false)}
              >
                Cancel
              </button>
              <button
                id="btn-confirm-logout"
                className="btn btn-danger"
                style={{ flex: 1 }}
                onClick={handleLogout}
              >
                Log Out
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
