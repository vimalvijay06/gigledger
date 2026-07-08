import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';

export default function LoginPage() {
  const [tab, setTab] = useState('login'); // 'login' | 'signup'
  const navigate = useNavigate();

  // Shared form state
  const [name, setName]         = useState('');
  const [email, setEmail]       = useState('');
  const [password, setPassword] = useState('');
  const [error, setError]       = useState('');
  const [loading, setLoading]   = useState(false);

  function switchTab(t) {
    setTab(t);
    setError('');
    setName(''); setEmail(''); setPassword('');
  }

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      let res;
      if (tab === 'signup') {
        res = await api.post('/auth/signup', { name, email, password });
      } else {
        res = await api.post('/auth/login', { email, password });
      }

      const { token, name: userName, email: userEmail } = res.data;
      localStorage.setItem('gl_token', token);
      localStorage.setItem('gl_user', JSON.stringify({ name: userName, email: userEmail }));
      navigate('/');
    } catch (err) {
      const msg = err.response?.data?.message || err.response?.data || 'Something went wrong. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="login-screen-bg">
      <div style={{ width: '100%', maxWidth: 400 }} className="animate-in">
        
        {/* Brand header */}
        <div style={{ textAlign: 'center', marginBottom: '2.5rem' }}>
          <div style={{ fontSize: '3rem', fontWeight: 900, letterSpacing: '-0.05em', lineHeight: 1 }}>
            Gig<span style={{ color: '#ffb000' }}>Ledger</span>
          </div>
          <p className="login-tagline">
            Independent, worker-owned proof to track and verify your delivery fares.
          </p>
        </div>

        <div className="card" style={{
          background: 'rgba(26, 29, 39, 0.65)',
          backdropFilter: 'blur(20px)',
          WebkitBackdropFilter: 'blur(20px)',
          boxShadow: '0 10px 40px rgba(0, 0, 0, 0.4)',
          border: '1px solid rgba(46, 50, 72, 0.6)'
        }}>
          {/* Tab switcher */}
          <div className="tabs" style={{ marginBottom: '1.75rem' }}>
            <button
              id="tab-login"
              type="button"
              className={`tab-btn ${tab === 'login' ? 'active' : ''}`}
              onClick={() => switchTab('login')}
              style={tab === 'login' ? { background: '#ffb000', color: '#0f1117' } : {}}
            >
              Log In
            </button>
            <button
              id="tab-signup"
              type="button"
              className={`tab-btn ${tab === 'signup' ? 'active' : ''}`}
              onClick={() => switchTab('signup')}
              style={tab === 'signup' ? { background: '#ffb000', color: '#0f1117' } : {}}
            >
              Sign Up
            </button>
          </div>

          {/* Error alert */}
          {error && (
            <div className="alert alert-error" style={{ marginBottom: '1.25rem' }}>
              ⚠ {error}
            </div>
          )}

          <form onSubmit={handleSubmit} id="auth-form">
            {/* Name field — signup only */}
            {tab === 'signup' && (
              <div className="form-group">
                <label className="form-label" htmlFor="input-name">Full Name</label>
                <input
                  id="input-name"
                  className="form-input"
                  type="text"
                  placeholder="Rajan Kumar"
                  value={name}
                  onChange={e => setName(e.target.value)}
                  required
                  style={{
                    padding: '12px 16px',
                    borderColor: 'rgba(46, 50, 72, 0.6)'
                  }}
                  autoFocus
                />
              </div>
            )}

            <div className="form-group">
              <label className="form-label" htmlFor="input-email">Email</label>
              <input
                id="input-email"
                className="form-input"
                type="email"
                placeholder="you@example.com"
                value={email}
                onChange={e => setEmail(e.target.value)}
                required
                style={{
                  padding: '12px 16px',
                  borderColor: 'rgba(46, 50, 72, 0.6)'
                }}
                autoFocus={tab === 'login'}
              />
            </div>

            <div className="form-group">
              <label className="form-label" htmlFor="input-password">Password</label>
              <input
                id="input-password"
                className="form-input"
                type="password"
                placeholder={tab === 'signup' ? 'At least 6 characters' : '••••••••'}
                value={password}
                onChange={e => setPassword(e.target.value)}
                required
                minLength={tab === 'signup' ? 6 : undefined}
                style={{
                  padding: '12px 16px',
                  borderColor: 'rgba(46, 50, 72, 0.6)'
                }}
              />
            </div>

            <button
              id="btn-submit-auth"
              type="submit"
              className="btn btn-primary btn--full"
              disabled={loading}
              style={{
                marginTop: '1.25rem',
                padding: '14px',
                background: '#ffb000',
                color: '#0f1117',
                boxShadow: '0 4px 15px rgba(255, 176, 0, 0.25)',
                fontWeight: 700
              }}
            >
              {loading ? <span className="spinner" style={{ borderColor: 'rgba(0,0,0,0.1)', borderTopColor: '#0f1117' }} /> : (tab === 'signup' ? 'Create Account' : 'Log In')}
            </button>
          </form>

          <div className="divider">or</div>

          <p style={{ textAlign: 'center', color: 'var(--text-secondary)', fontSize: '0.85rem', margin: 0 }}>
            {tab === 'login' ? "Don't have an account? " : 'Already have an account? '}
            <button
              type="button"
              className="btn btn-ghost btn--sm"
              style={{
                display: 'inline',
                padding: '0',
                border: 'none',
                color: '#ffb000',
                background: 'none',
                cursor: 'pointer',
                fontWeight: 700
              }}
              onClick={() => switchTab(tab === 'login' ? 'signup' : 'login')}
            >
              {tab === 'login' ? 'Sign Up' : 'Log In'}
            </button>
          </p>
        </div>

        <p style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.78rem', marginTop: '2rem' }}>
          Your records are fully encrypted and stored securely.
        </p>
      </div>
    </div>
  );
}
