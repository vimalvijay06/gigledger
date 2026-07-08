import { useState, useEffect } from 'react';
import { AlertCircle, TrendingDown, Percent, Coins } from 'lucide-react';
import api from '../api/client';

export default function AnalyticsPage() {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/tasks')
      .then(res => setTasks(res.data))
      .catch(() => setError('Failed to load analytics data.'))
      .finally(() => setLoading(false));
  }, []);

  const loggedPayouts = tasks.filter(t => t.payoutLogged);
  const underpaidTasks = loggedPayouts.filter(t => Number(t.difference) > 0);

  const totalUnderpaid = underpaidTasks.reduce((s, t) => s + Number(t.difference), 0);
  const avgUnderpayment = underpaidTasks.length > 0
    ? totalUnderpaid / underpaidTasks.length
    : 0;

  const underpaidPercent = loggedPayouts.length > 0
    ? Math.round((underpaidTasks.length / loggedPayouts.length) * 100)
    : 0;

  function fmt(n) {
    return '₹' + Number(n).toFixed(2);
  }

  if (loading) {
    return (
      <div className="page" style={{ display: 'flex', justifyContent: 'center', paddingTop: '4rem' }}>
        <span className="spinner" style={{ width: 36, height: 36, borderWidth: 3 }} />
      </div>
    );
  }

  return (
    <div className="page animate-in">
      <h1 className="page-title">Earnings Analytics</h1>
      <p className="page-subtitle">
        Analyze underpayment rates and identify patterns in shortfalls.
      </p>

      {error && <div className="alert alert-error">⚠ {error}</div>}

      {/* Analytics Summary */}
      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem', marginBottom: '2rem' }}>
        {/* Metric 1 */}
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', padding: '1.25rem' }}>
          <div style={{
            background: 'rgba(239, 68, 68, 0.08)',
            color: 'var(--color-danger)',
            width: 48, height: 48,
            borderRadius: '50%',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0
          }}>
            <Percent size={20} />
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', fontWeight: 600, letterSpacing: '0.04em' }}>
              Underpayment Frequency
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: underpaidPercent > 0 ? 'var(--color-danger)' : 'var(--color-success)' }}>
              {underpaidPercent}%
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              of verified deliveries were paid less than promised.
            </p>
          </div>
        </div>

        {/* Metric 2 */}
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', padding: '1.25rem' }}>
          <div style={{
            background: 'rgba(249, 115, 22, 0.08)',
            color: 'var(--color-accent)',
            width: 48, height: 48,
            borderRadius: '50%',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0
          }}>
            <TrendingDown size={20} />
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', fontWeight: 600, letterSpacing: '0.04em' }}>
              Average Shortfall
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--text-primary)' }}>
              {fmt(avgUnderpayment)}
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              lost per underpaid delivery.
            </p>
          </div>
        </div>

        {/* Metric 3 */}
        <div className="card" style={{ display: 'flex', alignItems: 'center', gap: '1.25rem', padding: '1.25rem' }}>
          <div style={{
            background: 'rgba(34, 197, 94, 0.08)',
            color: 'var(--color-success)',
            width: 48, height: 48,
            borderRadius: '50%',
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            flexShrink: 0
          }}>
            <Coins size={20} />
          </div>
          <div>
            <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', textTransform: 'uppercase', fontWeight: 600, letterSpacing: '0.04em' }}>
              Cumulative Loss
            </div>
            <div style={{ fontSize: '1.6rem', fontWeight: 800, color: 'var(--text-primary)' }}>
              {fmt(totalUnderpaid)}
            </div>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              total earnings discrepancies tracked.
            </p>
          </div>
        </div>
      </div>

      {/* Discrepancy Pattern Warnings */}
      <div className="card">
        <h3 className="section-title" style={{ fontSize: '1.1rem', marginBottom: '1rem' }}>Sustained Pattern Alerts</h3>
        
        {underpaidPercent >= 30 ? (
          <div style={{
            display: 'flex',
            gap: '1rem',
            background: 'rgba(239, 68, 68, 0.05)',
            border: '1px solid rgba(239, 68, 68, 0.2)',
            borderRadius: 'var(--radius-md)',
            padding: '1rem'
          }}>
            <AlertCircle size={20} style={{ color: 'var(--color-danger)', flexShrink: 0 }} />
            <div>
              <h4 style={{ fontSize: '0.9rem', fontWeight: 700, color: 'var(--color-danger)', marginBottom: '0.25rem' }}>
                Systemic Discrepancy Detected
              </h4>
              <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', lineHeight: 1.4 }}>
                Underpayments have been flagged on over 30% of your verified deliveries. This indicates a consistent, automated underpayment pattern rather than a one-off error.
              </p>
            </div>
          </div>
        ) : (
          <div style={{ textAlign: 'center', padding: '1.5rem 0', color: 'var(--text-muted)' }}>
            <p style={{ fontSize: '0.88rem' }}>No systemic underpayment patterns detected yet.</p>
          </div>
        )}
      </div>
    </div>
  );
}
