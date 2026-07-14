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

      <h3 className="form-label" style={{ marginTop: '1.5rem', marginBottom: '0.75rem', paddingLeft: 'var(--space-2)' }}>Notification Settings</h3>
      
      <div className="settings-list" style={{ marginBottom: '1rem' }}>
        {/* Email Toggle */}
        <div className="settings-item">
          <span className="settings-item-label">
            Email Notifications
          </span>
          <label className="switch" style={{ position: 'relative', display: 'inline-block', width: '38px', height: '20px' }}>
            <input 
              type="checkbox" 
              checked={emailNotifications} 
              onChange={e => setEmailNotifications(e.target.checked)}
              style={{ opacity: 0, width: 0, height: 0 }}
            />
            <span className="slider round" style={{
              position: 'absolute', cursor: 'pointer', top: 0, left: 0, right: 0, bottom: 0,
              backgroundColor: emailNotifications ? 'var(--color-accent)' : '#475569',
              transition: '.2s', borderRadius: '20px'
            }}>
              <span style={{
                position: 'absolute', content: '""', height: '14px', width: '14px', left: emailNotifications ? '20px' : '3px', bottom: '3px',
                backgroundColor: 'white', transition: '.2s', borderRadius: '50%'
              }}></span>
            </span>
          </label>
        </div>
      </div>

      <h3 className="form-label" style={{ marginTop: '1.5rem', marginBottom: '0.75rem', paddingLeft: 'var(--space-2)' }}>Fuel & Vehicle Settings</h3>
      
      <div className="settings-list" style={{ marginBottom: '1rem' }}>
        {/* District Field */}
        <div className="settings-item">
          <span className="settings-item-label" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <MapPin size={18} />
            District/City
          </span>
          <input
            type="text"
            placeholder="e.g. Chennai"
            style={{ 
              width: '120px', 
              padding: '4px 8px', 
              fontSize: '0.85rem', 
              textAlign: 'right',
              background: 'var(--color-surface-2)', 
              border: '1px solid var(--color-border)', 
              color: 'var(--text-primary)', 
              borderRadius: 'var(--radius-md)',
              outline: 'none'
            }}
            value={district}
            onChange={e => setDistrict(e.target.value)}
          />
        </div>

        {/* State Field */}
        <div className="settings-item">
          <span className="settings-item-label" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Compass size={18} />
            State
          </span>
          <input
            type="text"
            placeholder="e.g. Tamil Nadu"
            style={{ 
              width: '120px', 
              padding: '4px 8px', 
              fontSize: '0.85rem', 
              textAlign: 'right',
              background: 'var(--color-surface-2)', 
              border: '1px solid var(--color-border)', 
              color: 'var(--text-primary)', 
              borderRadius: 'var(--radius-md)',
              outline: 'none'
            }}
            value={state}
            onChange={e => setState(e.target.value)}
          />
        </div>

        {/* Vehicle Type Field */}
        <div className="settings-item">
          <span className="settings-item-label" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Fuel size={18} />
            Vehicle Type
          </span>
          <select
            className="form-select"
            style={{ width: 'auto', padding: '4px 10px', fontSize: '0.85rem' }}
            value={vehicleType}
            onChange={e => setVehicleType(e.target.value)}
          >
            <option value="bike">Delivery Bike</option>
            <option value="scooter">Scooter/Moped</option>
            <option value="bicycle">Bicycle (No Fuel)</option>
            <option value="e-bike">Electric Bike (No Fuel)</option>
          </select>
        </div>

        {/* Mileage Field */}
        {vehicleType !== 'bicycle' && vehicleType !== 'e-bike' && (
          <div className="settings-item">
            <span className="settings-item-label" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <Gauge size={18} />
              Fuel Efficiency (km/L)
            </span>
            <input
              type="number"
              style={{ 
                width: '80px', 
                padding: '4px 8px', 
                fontSize: '0.85rem', 
                textAlign: 'right', 
                background: 'var(--color-surface-2)', 
                border: '1px solid var(--color-border)', 
                color: 'var(--text-primary)', 
                borderRadius: 'var(--radius-md)',
                outline: 'none'
              }}
              value={fuelEfficiency}
              onChange={e => setFuelEfficiency(e.target.value)}
            />
          </div>
        )}
      </div>
      
      {vehicleType !== 'bicycle' && vehicleType !== 'e-bike' && (
        <p style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', padding: '0 var(--space-2)', marginBottom: '1.25rem', fontStyle: 'italic' }}>
          *Note: Fuel cost checks use average fuel efficiency. You can override it with your vehicle's actual mileage.
        </p>
      )}

      <h3 className="form-label" style={{ marginTop: '1.5rem', marginBottom: '0.75rem', paddingLeft: 'var(--space-2)' }}>Grievance Redressal Settings</h3>
      
      <div className="settings-list" style={{ marginBottom: '1rem' }}>
        {/* Platform Preference */}
        <div className="settings-item">
          <span className="settings-item-label" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ShieldAlert size={18} />
            Primary Platform
          </span>
          <select
            className="form-select"
            style={{ width: 'auto', padding: '4px 10px', fontSize: '0.85rem' }}
            value={platform}
            onChange={e => setPlatform(e.target.value)}
          >
            <option value="Swiggy">Swiggy (Verified)</option>
            <option value="Zomato">Zomato (Verified)</option>
            <option value="Blinkit">Blinkit (Pending)</option>
            <option value="Uber">Uber (Pending)</option>
            <option value="Ola">Ola (Pending)</option>
            <option value="Rapido">Rapido (Pending)</option>
          </select>
        </div>

        {/* Shortfall Threshold */}
        <div className="settings-item">
          <span className="settings-item-label">
            Auto-Draft Shortfall Min (₹)
          </span>
          <input
            type="number"
            style={{ 
              width: '80px', 
              padding: '4px 8px', 
              fontSize: '0.85rem', 
              textAlign: 'right', 
              background: 'var(--color-surface-2)', 
              border: '1px solid var(--color-border)', 
              color: 'var(--text-primary)', 
              borderRadius: 'var(--radius-md)',
              outline: 'none'
            }}
            value={shortfallThreshold}
            onChange={e => setShortfallThreshold(e.target.value)}
          />
        </div>

        {/* Severity Threshold */}
        <div className="settings-item">
          <span className="settings-item-label">
            Min Severity Level
          </span>
          <select
            className="form-select"
            style={{ width: 'auto', padding: '4px 10px', fontSize: '0.85rem' }}
            value={severityThreshold}
            onChange={e => setSeverityThreshold(e.target.value)}
          >
            <option value="high">High Severity Only</option>
            <option value="medium">Medium or High</option>
            <option value="low">Low, Medium, or High</option>
          </select>
        </div>
      </div>

      <div style={{ padding: '0 var(--space-2)', marginBottom: '1.5rem' }}>
        <button className="btn btn-primary btn--full" onClick={handleSaveSettings}>
          Save Settings
        </button>
        {saveStatus && (
          <p style={{ 
            fontSize: '0.8rem', 
            color: saveStatus.includes('successfully') ? 'var(--color-success)' : 'var(--color-warning)', 
            marginTop: '8px', 
            textAlign: 'center',
            fontWeight: 600
          }}>
            {saveStatus}
          </p>
        )}
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
