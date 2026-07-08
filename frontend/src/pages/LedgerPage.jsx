import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { ClipboardList, Plus } from 'lucide-react';
import api from '../api/client';

export default function LedgerPage() {
  const [tasks, setTasks] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    api.get('/tasks')
      .then(res => setTasks(res.data))
      .catch(() => setError('Failed to load ledger records. Is the backend running?'))
      .finally(() => setLoading(false));
  }, []);

  function fmt(n) {
    return '₹' + Number(n).toFixed(2);
  }

  function formatDate(iso) {
    return new Date(iso).toLocaleString('en-IN', {
      day: '2-digit',
      month: 'short',
      year: 'numeric',
      hour: '2-digit',
      minute: '2-digit',
    });
  }

  if (loading) {
    return (
      <div className="page page--wide" style={{ display: 'flex', justifyContent: 'center', paddingTop: '4rem' }}>
        <span className="spinner" style={{ width: 36, height: 36, borderWidth: 3 }} />
      </div>
    );
  }

  return (
    <div className="page page--wide animate-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <div>
          <h1 className="page-title" style={{ marginBottom: '0.25rem' }}>Task Ledger</h1>
          <p style={{ color: 'var(--text-secondary)', fontSize: '0.85rem' }}>
            Your full history of deliveries and verified earnings
          </p>
        </div>
        <Link to="/capture" className="btn btn-primary btn--sm" style={{ display: 'flex', alignItems: 'center', gap: '4px' }}>
          <Plus size={16} /> New Task
        </Link>
      </div>

      {error && <div className="alert alert-error" style={{ marginBottom: '1.5rem' }}>⚠ {error}</div>}

      {tasks.length === 0 ? (
        <div className="card">
          <div className="empty-state">
            <ClipboardList size={48} style={{ color: 'var(--text-muted)', marginBottom: '1rem' }} />
            <h3>No tasks logged yet</h3>
            <p>Your delivery ledger is currently empty. Tap Capture to start logging your deliveries.</p>
            <Link to="/capture" className="btn btn-primary" style={{ marginTop: '1.5rem' }}>
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
                      {task.payoutLogged ? (
                        <span className="amount amount-actual">{fmt(task.actualAmount)}</span>
                      ) : (
                        <span style={{ color: 'var(--text-muted)' }}>—</span>
                      )}
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
                          state={{ taskId: task.id }}
                          className="btn btn-ghost btn--sm"
                          style={{ whiteSpace: 'nowrap' }}
                        >
                          Log Payout
                        </Link>
                      )}
                      {task.payoutLogged && task.deductionReason && (
                        <span
                          title={task.deductionReason}
                          style={{ cursor: 'help', color: 'var(--text-muted)', fontSize: '0.78rem', display: 'flex', alignItems: 'center', gap: '2px' }}
                        >
                          ⚠️ Reason
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
