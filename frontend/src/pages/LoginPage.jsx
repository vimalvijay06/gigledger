import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';

/**
 * LoginPage — handles both Sign Up and Login in a single tabbed view.
 *
 * On success:
 * - Stores the JWT in localStorage as 'gl_token'
 * - Stores basic user info (name, email) as 'gl_user' (JSON)
 * - Navigates to /tasks
 *
 * Error handling: displays the backend's error message (e.g., "Email already
 * registered" or "Invalid email or password") inline above the form.
 */
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
      navigate('/tasks');
    } catch (err) {
      const msg = typeof err.response?.data === 'string'
        ? err.response.data
        : 'Something went wrong. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div style={{ minHeight: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1.5rem' }}>
      <div style={{ width: '100%', maxWidth: 420 }} className="animate-in">

        {/* Brand header */}
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <div style={{ fontSize: '2.5rem', fontWeight: 900, letterSpacing: '-0.04em', lineHeight: 1 }}>
            Gig<span style={{ color: 'var(--color-accent)' }}>Ledger</span>
          </div>
          <p style={{ color: 'var(--text-secondary)', marginTop: '0.5rem', fontSize: '0.9rem' }}>
            Your independent earnings record
          </p>
        </div>

        <div className="card">
          {/* Tab switcher */}
          <div className="tabs">
            <button
              id="tab-login"
              className={`tab-btn ${tab === 'login' ? 'active' : ''}`}
              onClick={() => switchTab('login')}
            >
              Log In
            </button>
            <button
              id="tab-signup"
              className={`tab-btn ${tab === 'signup' ? 'active' : ''}`}
              onClick={() => switchTab('signup')}
            >
              Sign Up
            </button>
          </div>

          {/* Error alert */}
          {error && (
            <div className="alert alert-error">
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
              />
            </div>

            <button
              id="btn-submit-auth"
              type="submit"
              className="btn btn-primary btn--full"
              disabled={loading}
              style={{ marginTop: '0.5rem' }}
            >
              {loading ? <span className="spinner" /> : (tab === 'signup' ? 'Create Account' : 'Log In')}
            </button>
          </form>

          <div className="divider">or</div>

          <p style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.85rem' }}>
            {tab === 'login' ? "Don't have an account? " : 'Already have an account? '}
            <button
              className="btn btn-ghost btn--sm"
              style={{ display: 'inline', padding: '0', border: 'none', color: 'var(--color-accent)', background: 'none', cursor: 'pointer' }}
              onClick={() => switchTab(tab === 'login' ? 'signup' : 'login')}
            >
              {tab === 'login' ? 'Sign Up' : 'Log In'}
            </button>
          </p>
        </div>

        <p style={{ textAlign: 'center', color: 'var(--text-muted)', fontSize: '0.78rem', marginTop: '1.5rem' }}>
          Your data stays with you. No platform has access.
        </p>
      </div>
    </div>
  );
}
