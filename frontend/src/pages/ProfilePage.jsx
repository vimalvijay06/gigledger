import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Globe, Moon, FileOutput, LogOut, ShieldAlert, MapPin, Fuel, Gauge, Compass } from 'lucide-react';
import api from '../api/client';

export default function ProfilePage() {
  const navigate = useNavigate();
  const user = JSON.parse(localStorage.getItem('gl_user') || '{}');
  
  // Settings states
  const [language, setLanguage] = useState(user.languagePref || 'en');
  const [theme, setTheme] = useState('dark');
  const [showConfirmLogout, setShowConfirmLogout] = useState(false);

  const [platform, setPlatform] = useState('Swiggy');
  const [shortfallThreshold, setShortfallThreshold] = useState(500);
  const [severityThreshold, setSeverityThreshold] = useState('high');
  const [emailNotifications, setEmailNotifications] = useState(true);
  const [saveStatus, setSaveStatus] = useState('');

  // Fuel configuration states
  const [district, setDistrict] = useState('Chennai');
  const [state, setState] = useState('Tamil Nadu');
  const [vehicleType, setVehicleType] = useState('bike');
  const [fuelEfficiency, setFuelEfficiency] = useState(45.00);

  useEffect(() => {
    api.get('/user/settings')
      .then(res => {
        setPlatform(res.data.platformPreference || 'Swiggy');
        setShortfallThreshold(res.data.shortfallThreshold || 500);
        setSeverityThreshold(res.data.severityThreshold || 'high');
        setEmailNotifications(res.data.emailNotificationsEnabled !== false);
        setDistrict(res.data.district || 'Chennai');
        setState(res.data.state || 'Tamil Nadu');
        setVehicleType(res.data.vehicleType || 'bike');
        setFuelEfficiency(res.data.fuelEfficiency || 45.00);
      })
      .catch(err => console.error("Failed to load settings:", err));
  }, []);

  function handleSaveSettings() {
    setSaveStatus('Saving settings...');
    api.put('/user/settings', {
      platformPreference: platform,
      shortfallThreshold: Number(shortfallThreshold),
      severityThreshold: severityThreshold,
      emailNotificationsEnabled: emailNotifications,
      district: district,
      state: state,
      vehicleType: vehicleType,
      fuelEfficiency: Number(fuelEfficiency)
    })
      .then(res => {
        const updatedUser = { ...user, platformPreference: platform, district: district, state: state, vehicleType: vehicleType };
        localStorage.setItem('gl_user', JSON.stringify(updatedUser));
        setSaveStatus('Settings saved successfully!');
        setTimeout(() => setSaveStatus(''), 3000);
      })
      .catch(err => {
        console.error("Failed to save settings:", err);
        setSaveStatus('Failed to save settings.');
      });
  }

  const userInitials = user.name
    ? user.name.split(' ').map(n => n[0]).join('').toUpperCase().slice(0, 2)
    : 'U';

  function handleLogout() {
    localStorage.removeItem('gl_token');
    localStorage.removeItem('gl_user');
    window.location.href = '/login';
  }

  function handleExportData() {
    alert("Exporting your ledger database to CSV... Done!");
  }

  return (
    <div className="page animate-in page--wide">
      {/* Premium Avatar Header */}
      <div className="profile-header" style={{ borderBottom: 'none', marginBottom: 'var(--space-8)' }}>
        <div className="avatar-container">
          <div className="avatar-glow"></div>
          <div className="avatar-inner">
            {userInitials}
          </div>
        </div>
        <h2 className="profile-name" style={{ fontSize: '1.8rem', background: 'linear-gradient(135deg, #fff 0%, var(--color-accent) 100%)', WebkitBackgroundClip: 'text', WebkitTextFillColor: 'transparent' }}>
          {user.name || 'Worker Partner'}
        </h2>
        <p className="profile-email">{user.email || 'partner@gigledger.com'}</p>
      </div>

      <div className="bento-grid">
        {/* Bento Box 1: App Settings */}
        <div className="glass-panel">
          <h3 className="section-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldAlert size={20} style={{ color: 'var(--color-accent)' }} /> App & Notifications
          </h3>
          <div className="bento-item">
            <span className="settings-item-label">
              <Globe size={18} /> Language
            </span>
            <select className="form-select" style={{ width: 'auto', padding: '4px 10px' }} value={language} onChange={e => setLanguage(e.target.value)}>
              <option value="en">English</option>
              <option value="hi">हिन्दी (Hindi)</option>
              <option value="ta">தமிழ் (Tamil)</option>
            </select>
          </div>
          <div className="bento-item">
            <span className="settings-item-label">
              <Moon size={18} /> Theme
            </span>
            <select className="form-select" style={{ width: 'auto', padding: '4px 10px' }} value={theme} disabled>
              <option value="dark">Dark</option>
            </select>
          </div>
          <div className="bento-item">
            <span className="settings-item-label">Email Alerts</span>
            <label className="switch" style={{ position: 'relative', display: 'inline-block', width: '38px', height: '20px' }}>
              <input type="checkbox" checked={emailNotifications} onChange={e => setEmailNotifications(e.target.checked)} style={{ opacity: 0, width: 0, height: 0 }} />
              <span className="slider round" style={{ position: 'absolute', cursor: 'pointer', top: 0, left: 0, right: 0, bottom: 0, backgroundColor: emailNotifications ? 'var(--color-accent)' : '#475569', transition: '.2s', borderRadius: '20px' }}>
                <span style={{ position: 'absolute', content: '""', height: '14px', width: '14px', left: emailNotifications ? '20px' : '3px', bottom: '3px', backgroundColor: 'white', transition: '.2s', borderRadius: '50%' }}></span>
              </span>
            </label>
          </div>
        </div>

        {/* Bento Box 2: Fuel Configuration */}
        <div className="glass-panel">
          <h3 className="section-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Fuel size={20} style={{ color: 'var(--color-accent)' }} /> Vehicle Profile
          </h3>
          <div className="bento-item">
            <span className="settings-item-label"><MapPin size={18} /> District</span>
            <input type="text" className="form-input" style={{ width: '130px', padding: '4px 8px', textAlign: 'right' }} value={district} onChange={e => setDistrict(e.target.value)} />
          </div>
          <div className="bento-item">
            <span className="settings-item-label"><Compass size={18} /> State</span>
            <input type="text" className="form-input" style={{ width: '130px', padding: '4px 8px', textAlign: 'right' }} value={state} onChange={e => setState(e.target.value)} />
          </div>
          <div className="bento-item">
            <span className="settings-item-label"><Fuel size={18} /> Vehicle</span>
            <select className="form-select" style={{ width: 'auto', padding: '4px 10px' }} value={vehicleType} onChange={e => setVehicleType(e.target.value)}>
              <option value="bike">Bike</option>
              <option value="scooter">Scooter</option>
              <option value="e-bike">EV</option>
              <option value="bicycle">Bicycle</option>
            </select>
          </div>
          {vehicleType !== 'bicycle' && vehicleType !== 'e-bike' && (
            <div className="bento-item">
              <span className="settings-item-label"><Gauge size={18} /> Efficiency (km/L)</span>
              <input type="number" className="form-input" style={{ width: '80px', padding: '4px 8px', textAlign: 'right' }} value={fuelEfficiency} onChange={e => setFuelEfficiency(e.target.value)} />
            </div>
          )}
        </div>

        {/* Bento Box 3: Grievance Engine */}
        <div className="glass-panel" style={{ gridColumn: '1 / -1' }}>
          <h3 className="section-title" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldAlert size={20} style={{ color: 'var(--color-accent)' }} /> Auto-Grievance Engine
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))', gap: '1rem' }}>
            <div>
              <span className="settings-item-label" style={{ display: 'block', marginBottom: '8px' }}>Primary Platform</span>
              <select className="form-select" value={platform} onChange={e => setPlatform(e.target.value)}>
                <option value="Swiggy">Swiggy (Verified)</option>
                <option value="Zomato">Zomato (Verified)</option>
                <option value="Blinkit">Blinkit (Pending)</option>
              </select>
            </div>
            <div>
              <span className="settings-item-label" style={{ display: 'block', marginBottom: '8px' }}>Shortfall Threshold (₹)</span>
              <input type="number" className="form-input" value={shortfallThreshold} onChange={e => setShortfallThreshold(e.target.value)} />
            </div>
            <div>
              <span className="settings-item-label" style={{ display: 'block', marginBottom: '8px' }}>Min Severity Level</span>
              <select className="form-select" value={severityThreshold} onChange={e => setSeverityThreshold(e.target.value)}>
                <option value="high">High Severity Only</option>
                <option value="medium">Medium & High</option>
                <option value="low">All Severities</option>
              </select>
            </div>
          </div>
        </div>
      </div>

      <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', marginBottom: '2rem' }}>
        <button className="btn btn-primary" style={{ flex: 1, padding: '1rem', fontSize: '1rem' }} onClick={handleSaveSettings}>
          Save Settings
        </button>
        <button className="btn btn-ghost" style={{ flex: 1, padding: '1rem', fontSize: '1rem', color: '#ffb000', borderColor: 'rgba(255, 176, 0, 0.3)' }} onClick={handleExportData}>
          <FileOutput size={18} /> Export Ledger to CSV
        </button>
      </div>

      {saveStatus && (
        <p style={{ textAlign: 'center', color: saveStatus.includes('successfully') ? 'var(--color-success)' : 'var(--color-warning)', fontWeight: 600, marginTop: '-1rem', marginBottom: '2rem' }}>
          {saveStatus}
        </p>
      )}

      {/* Logout Action area */}
      <div style={{ padding: '0 var(--space-4)', textAlign: 'center' }}>
        {!showConfirmLogout ? (
          <button className="btn btn-ghost" style={{ borderColor: 'rgba(239, 68, 68, 0.4)', color: '#ef4444', display: 'inline-flex', gap: '8px', padding: '0.5rem 2rem' }} onClick={() => setShowConfirmLogout(true)}>
            <LogOut size={18} /> Log Out
          </button>
        ) : (
          <div className="glass-panel" style={{ maxWidth: '400px', margin: '0 auto', textAlign: 'center' }}>
            <p style={{ fontSize: '0.95rem', fontWeight: 600, marginBottom: '1.5rem', color: 'var(--text-primary)' }}>
              Are you sure you want to log out?
            </p>
            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
              <button className="btn btn-ghost" onClick={() => setShowConfirmLogout(false)}>Cancel</button>
              <button className="btn btn-danger" onClick={handleLogout}>Log Out</button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
}
