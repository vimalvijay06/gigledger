import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ClipboardList, ArrowRight, ShieldCheck, AlertTriangle, Scale } from 'lucide-react';
import api from '../api/client';

export default function DashboardPage() {
  const user = JSON.parse(localStorage.getItem('gl_user') || '{}');
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [flags, setFlags] = useState([]);
  const [pendingComplaints, setPendingComplaints] = useState([]);

  useEffect(() => {
    Promise.all([
      api.get('/tasks'),
      api.get('/analytics/flags'),
      api.get('/complaints')
    ])
      .then(([tasksRes, flagsRes, complaintsRes]) => {
        setTasks(tasksRes.data);
        setFlags(flagsRes.data);
        setPendingComplaints(complaintsRes.data);
      })
      .catch(() => setError('Failed to load dashboard metrics.'))
      .finally(() => setLoading(false));
  }, []);

  // ─── Computed stats ────────────────────────────────────────────────────
  const totalTasks = tasks.length;
  const loggedPayouts = tasks.filter(t => t.payoutLogged);
  const totalPromised = tasks.reduce((s, t) => s + Number(t.promisedAmount), 0);
  const totalActual = loggedPayouts.reduce((s, t) => s + Number(t.actualAmount), 0);
  const totalUnderpaid = loggedPayouts
    .filter(t => Number(t.difference) > 0)
    .reduce((s, t) => s + Number(t.difference), 0);
  const pendingCount = tasks.filter(t => !t.payoutLogged).length;

  // Fairness Score calculated from systemic anomaly flags
  const hasHighFlag = flags.some(f => f.severity === 'high');
  const hasMediumFlag = flags.some(f => f.severity === 'medium');
  const hasLowFlag = flags.some(f => f.severity === 'low');

  let fairnessScore = 100;
  if (hasHighFlag) fairnessScore = 30;
  else if (hasMediumFlag) fairnessScore = 60;
  else if (hasLowFlag) fairnessScore = 85;

  function fmt(n) {
    return '₹' + Number(n).toFixed(2);
  }

  // Removed full-screen loading block to prevent transition flash

  return (
    <div className="page animate-in">
      {/* Welcome Header */}
      <div style={{ marginBottom: '1.5rem' }}>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem', textTransform: 'uppercase', letterSpacing: '0.05em', fontWeight: 600 }}>
          Welcome back
        </p>
        <h1 className="page-title" style={{ fontSize: '2rem', marginBottom: 0 }}>{user.name}</h1>
      </div>

      {error && <div className="alert alert-error">⚠ {error}</div>}

      {pendingComplaints.length > 0 && (
        <div className="card" style={{ 
          background: 'rgba(255, 176, 0, 0.08)', 
          border: '1px solid rgba(255, 176, 0, 0.25)', 
          borderRadius: 'var(--radius-lg)', 
          padding: '1rem', 
          marginBottom: '1.5rem',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          gap: '12px'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
            <Scale size={24} style={{ color: 'var(--color-accent)', flexShrink: 0 }} />
            <div>
              <h4 style={{ color: 'var(--color-accent)', margin: 0, fontSize: '0.9rem', fontWeight: 700 }}>
                Grievance Draft Ready
              </h4>
              <p style={{ color: 'var(--text-secondary)', margin: '4px 0 0 0', fontSize: '0.8rem', lineHeight: 1.4 }}>
                You have {pendingComplaints.length} pending legal complaint draft{pendingComplaints.length > 1 ? 's' : ''} ready for review.
              </p>
            </div>
          </div>
          <Link 
            to="/complaints" 
            className="btn btn-primary" 
            style={{ 
              padding: '6px 12px', 
              fontSize: '0.8rem', 
              textDecoration: 'none',
              whiteSpace: 'nowrap'
            }}
          >
            Review & Send
          </Link>
        </div>
      )}

      {/* Fairness Gauge */}
      <div className="card" style={{ textAlign: 'center', marginBottom: '1.5rem', position: 'relative', overflow: 'hidden' }}>
        <h3 className="form-label" style={{ marginBottom: '1rem', letterSpacing: '0.08em' }}>Fairness Gauge</h3>
        
        <div style={{ position: 'relative', width: 160, height: 160, margin: '0 auto 1rem auto', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
          {/* Circular progress bar SVG */}
          <svg width="160" height="160" style={{ transform: 'rotate(-90deg)' }}>
            <circle
              cx="80"
              cy="80"
              r="70"
              fill="transparent"
              stroke="var(--color-border)"
              strokeWidth="10"
            />
            <circle
              cx="80"
              cy="80"
              r="70"
              fill="transparent"
              stroke={fairnessScore >= 80 ? 'var(--color-success)' : fairnessScore >= 50 ? 'var(--color-warning)' : 'var(--color-danger)'}
              strokeWidth="10"
              strokeDasharray={2 * Math.PI * 70}
              strokeDashoffset={2 * Math.PI * 70 * (1 - fairnessScore / 100)}
              strokeLinecap="round"
              style={{ transition: 'stroke-dashoffset 1s ease' }}
            />
          </svg>
          <div style={{ position: 'absolute', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
            <span style={{ fontSize: '2.2rem', fontWeight: 800, color: 'var(--text-primary)', lineHeight: 1 }}>
              {fairnessScore}%
            </span>
            <span style={{ fontSize: '0.72rem', color: 'var(--text-secondary)', marginTop: '4px', textTransform: 'uppercase', letterSpacing: '0.04em' }}>
              Fair Payouts
            </span>
          </div>
        </div>

        <p style={{ fontSize: '0.9rem', color: 'var(--text-secondary)', maxWidth: '300px', margin: '0 auto' }}>
          {loggedPayouts.length < 30
            ? "Need at least 30 verified payouts to establish your statistical baseline."
            : flags.length === 0
            ? "Excellent! No sustained underpayment patterns detected in your logs."
            : hasHighFlag
            ? "Critical: Sustained underpayment patterns detected in your history!"
            : hasMediumFlag
            ? "Warning: Medium-severity systemic rate discrepancies flagged."
            : "Caution: Minor rate deviations flagged in your payout logs."
          }
        </p>
      </div>


      {/* Primary Metrics Grid */}
      <div className="stats-grid" style={{ marginBottom: '1.5rem' }}>
        <div className="stat-card">
          <div className="stat-label">Total Promised</div>
          <div className="stat-value" style={{ fontSize: '1.25rem' }}>{fmt(totalPromised)}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Total Paid</div>
          <div className="stat-value success" style={{ fontSize: '1.25rem' }}>{fmt(totalActual)}</div>
        </div>
        <div className="stat-card">
          <div className="stat-label">Total Shortfall</div>
          <div className="stat-value danger" style={{ fontSize: '1.25rem' }}>{fmt(totalUnderpaid)}</div>
        </div>
      </div>

      {/* Anomaly Detection Alerts */}
      {flags.length > 0 && (
        <div className="alert alert-error" style={{
          background: 'rgba(239, 68, 68, 0.08)',
          border: '1px solid rgba(239, 68, 68, 0.25)',
          color: 'var(--color-danger)',
          justifyContent: 'space-between',
          marginBottom: '1.5rem'
        }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <AlertTriangle size={18} />
            Systemic underpayment detected! {flags.length} weekly pattern alert{flags.length > 1 ? 's' : ''} active.
          </span>
          <Link to="/analytics" className="btn btn-primary btn--sm" style={{
            background: 'var(--color-danger)',
            boxShadow: 'none',
            color: '#ffffff'
          }}>
            View Details
          </Link>
        </div>
      )}

      {/* Action Prompt */}
      {pendingCount > 0 && (
        <div className="alert alert-success" style={{
          background: 'rgba(245, 158, 11, 0.08)',
          border: '1px solid rgba(245, 158, 11, 0.25)',
          color: 'var(--color-warning)',
          justifyContent: 'space-between',
          marginBottom: '1.5rem'
        }}>
          <span style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <AlertTriangle size={18} />
            You have {pendingCount} task{pendingCount > 1 ? 's' : ''} awaiting payouts
          </span>
          <Link to="/log-payout" className="btn btn-primary btn--sm" style={{
            background: 'var(--color-warning)',
            boxShadow: 'none',
            color: '#0f1117'
          }}>
            Log Payout
          </Link>
        </div>
      )}


      {/* Recent Activity Section */}
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
          <h3 className="section-title" style={{ margin: 0, fontSize: '1.1rem' }}>Recent Tasks</h3>
          <Link to="/ledger" style={{ fontSize: '0.85rem', color: '#ffb000', display: 'flex', alignItems: 'center', gap: '4px', textDecoration: 'none', fontWeight: 600 }}>
            Full Ledger <ArrowRight size={14} />
          </Link>
        </div>

        {tasks.length === 0 ? (
          <div className="empty-state" style={{ padding: '2rem 1rem' }}>
            <ClipboardList size={36} style={{ color: 'var(--text-muted)', marginBottom: '0.5rem' }} />
            <p style={{ fontSize: '0.9rem' }}>No tasks logged yet.</p>
            <Link to="/capture" className="btn btn-primary btn--sm" style={{ marginTop: '1rem' }}>
              Log a Task
            </Link>
          </div>
        ) : (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '10px' }}>
            {tasks.slice(0, 3).map(task => {
              const diff = task.payoutLogged ? Number(task.difference) : null;
              const isUnderpaid = diff !== null && diff > 0;
              return (
                <div key={task.id} style={{
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  background: 'var(--color-surface-2)',
                  padding: '10px 14px',
                  borderRadius: 'var(--radius-md)',
                  border: '1px solid var(--color-border)'
                }}>
                  <div>
                    <div style={{ fontSize: '0.88rem', fontWeight: 600 }}>
                      Promised: {fmt(task.promisedAmount)}
                    </div>
                    <div style={{ fontSize: '0.75rem', color: 'var(--text-secondary)' }}>
                      {new Date(task.acceptedAt).toLocaleDateString('en-IN', { day: '2-digit', month: 'short' })}
                      {task.distanceKm ? ` · ${task.distanceKm} km` : ''}
                    </div>
                  </div>
                  <div>
                    {task.payoutLogged ? (
                      <span className={`diff-badge ${isUnderpaid ? 'underpaid' : 'paid-ok'}`} style={{ fontSize: '0.75rem' }}>
                        {isUnderpaid ? `▼ ${fmt(diff)}` : `✓ OK`}
                      </span>
                    ) : (
                      <span className="diff-badge pending" style={{ fontSize: '0.75rem' }}>Pending</span>
                    )}
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
}
