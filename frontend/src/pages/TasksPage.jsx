import { useState, useEffect } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import api from '../api/client';

/**
 * TasksPage — the main dashboard showing the worker's full earnings ledger.
 *
 * Features:
 * - Summary stats: total tasks, total promised, total underpaid
 * - Table: Date | Promised (₹) | Actual (₹) | Difference | Status
 * - Difference badge: red (underpaid), green (exact/overpaid), grey (pending)
 * - "Log Payout" button on pending rows
 * - Empty state if no tasks yet
 */
export default function TasksPage() {
  const navigate = useNavigate();
  const user = JSON.parse(localStorage.getItem('gl_user') || '{}');

  const [tasks, setTasks]     = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError]     = useState('');

  useEffect(() => {
    api.get('/tasks')
      .then(res => setTasks(res.data))
      .catch(() => setError('Failed to load tasks. Is the backend running?'))
      .finally(() => setLoading(false));
  }, []);

  // ─── Computed stats ────────────────────────────────────────────────────
  const totalPromised   = tasks.reduce((s, t) => s + Number(t.promisedAmount), 0);
  const totalActual     = tasks.filter(t => t.payoutLogged).reduce((s, t) => s + Number(t.actualAmount), 0);
  const totalUnderpaid  = tasks.filter(t => t.payoutLogged && Number(t.difference) > 0)
                               .reduce((s, t) => s + Number(t.difference), 0);
  const pendingCount    = tasks.filter(t => !t.payoutLogged).length;

  function fmt(n) {
    return '₹' + Number(n).toFixed(2);
  }

  function formatDate(iso) {
    return new Date(iso).toLocaleString('en-IN', {
      day: '2-digit', month: 'short', year: 'numeric',
      hour: '2-digit', minute: '2-digit',
    });
  }

  // ─── Loading state ─────────────────────────────────────────────────────
  if (loading) {
    return (
      <div className="page page--wide" style={{ display: 'flex', justifyContent: 'center', paddingTop: '4rem' }}>
        <span className="spinner" style={{ width: 36, height: 36, borderWidth: 3 }} />
      </div>
    );
  }

  return (
    <div className="page page--wide animate-in">

      {/* Welcome */}
      <div style={{ marginBottom: '2rem' }}>
        <h1 className="page-title">My Ledger</h1>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.9rem' }}>
          Welcome back, <strong style={{ color: 'var(--text-primary)' }}>{user.name}</strong>
          {pendingCount > 0 && (
            <span style={{ marginLeft: '0.75rem', color: 'var(--color-warning)' }}>
              · {pendingCount} task{pendingCount > 1 ? 's' : ''} awaiting payout
            </span>
          )}
        </p>
      </div>

      {/* Stats */}
      {tasks.length > 0 && (
        <div className="stats-grid">
          <div className="stat-card">
            <div className="stat-label">Tasks</div>
            <div className="stat-value accent">{tasks.length}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Total Promised</div>
            <div className="stat-value">{fmt(totalPromised)}</div>
          </div>
          <div className="stat-card">
            <div className="stat-label">Total Underpaid</div>
            <div className="stat-value" style={{ color: totalUnderpaid > 0 ? 'var(--color-danger)' : 'var(--color-success)' }}>
              {fmt(totalUnderpaid)}
            </div>
          </div>
        </div>
      )}

      {error && <div className="alert alert-error" style={{ marginBottom: '1.5rem' }}>⚠ {error}</div>}

      {/* Action bar */}
      <div className="action-bar">
        <h2 className="section-title" style={{ margin: 0 }}>All Tasks</h2>
        <div style={{ display: 'flex', gap: '0.75rem' }}>
          {pendingCount > 0 && (
            <Link to="/log-payout" className="btn btn-ghost btn--sm">Log Payout</Link>
          )}
          <Link to="/log-task" id="btn-add-task" className="btn btn-primary btn--sm">+ New Task</Link>
        </div>
      </div>

      {/* Empty state */}
      {tasks.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <div className="empty-state-icon">📋</div>
            <h3>No tasks yet</h3>
            <p>Start by logging your first delivery task.</p>
            <Link to="/log-task" className="btn btn-primary" style={{ marginTop: '1.5rem' }}>
              Log Your First Task
            </Link>
          </div>
        </div>
      ) : (
        <div className="table-wrapper">
          <table className="data-table">
            <thead>
              <tr>
                <th>Date & Time</th>
                <th>Distance</th>
                <th>Promised (₹)</th>
                <th>Actual (₹)</th>
                <th>Difference</th>
                <th>Status</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {tasks.map(task => {
                const diff = task.payoutLogged ? Number(task.difference) : null;
                const isUnderpaid = diff !== null && diff > 0;
                const isOk        = diff !== null && diff <= 0;

                return (
                  <tr key={task.id}>
                    <td style={{ fontSize: '0.82rem', color: 'var(--text-secondary)' }}>
                      {formatDate(task.acceptedAt)}
                    </td>
                    <td>
                      {task.distanceKm ? `${task.distanceKm} km` : <span style={{ color: 'var(--text-muted)' }}>—</span>}
                    </td>
                    <td>
                      <span className="amount amount-promised">{fmt(task.promisedAmount)}</span>
                    </td>
                    <td>
                      {task.payoutLogged
                        ? <span className="amount amount-actual">{fmt(task.actualAmount)}</span>
                        : <span style={{ color: 'var(--text-muted)' }}>—</span>}
                    </td>
                    <td>
                      {task.payoutLogged ? (
                        <span className={`diff-badge ${isUnderpaid ? 'underpaid' : 'paid-ok'}`}>
                          {isUnderpaid ? '▼' : '✓'} {fmt(Math.abs(diff))}
                          {isUnderpaid ? ' short' : ' ok'}
                        </span>
                      ) : (
                        <span className="diff-badge pending">Pending</span>
                      )}
                    </td>
                    <td>
                      <span className={`status-pill ${task.payoutLogged ? 'paid' : 'pending'}`}>
                        {task.payoutLogged ? 'Paid' : 'Pending'}
                      </span>
                    </td>
                    <td>
                      {!task.payoutLogged && (
                        <Link
                          to="/log-payout"
                          className="btn btn-ghost btn--sm"
                          style={{ whiteSpace: 'nowrap' }}
                        >
                          Log Payout
                        </Link>
                      )}
                      {task.payoutLogged && task.deductionReason && (
                        <span
                          title={task.deductionReason}
                          style={{ cursor: 'help', color: 'var(--text-muted)', fontSize: '0.78rem' }}
                        >
                          ⚠ Reason
                        </span>
                      )}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
