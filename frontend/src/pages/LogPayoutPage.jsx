import { useState, useEffect } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import api from '../api/client';

/**
 * LogPayoutPage — log the actual amount received for a pending task.
 */
export default function LogPayoutPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const stateTaskId = location.state?.taskId;

  const [tasks, setTasks]               = useState([]);   // all pending (no payout) tasks
  const [loadingTasks, setLoadingTasks] = useState(true);
  const [selectedTaskId, setSelectedTaskId] = useState('');
  const [actualAmount, setActualAmount] = useState('');
  const [deductionReason, setDeductionReason] = useState('');
  const [error, setError]               = useState('');
  const [success, setSuccess]           = useState(false);
  const [loading, setLoading]           = useState(false);

  // Fetch pending tasks on mount
  useEffect(() => {
    api.get('/tasks')
      .then(res => {
        const pending = res.data.filter(t => !t.payoutLogged);
        setTasks(pending);
        if (stateTaskId) {
          setSelectedTaskId(stateTaskId);
        } else if (pending.length > 0) {
          setSelectedTaskId(pending[0].id);
        }
      })
      .catch(() => setError('Failed to load tasks'))
      .finally(() => setLoadingTasks(false));
  }, [stateTaskId]);

  const selectedTask = tasks.find(t => t.id === selectedTaskId);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      const payload = {
        actualAmount: parseFloat(actualAmount),
        deductionReason: deductionReason.trim() || null,
      };

      await api.post(`/tasks/${selectedTaskId}/payout`, payload);
      setSuccess(true);
      setTimeout(() => navigate('/tasks'), 1500);
    } catch (err) {
      const msg = typeof err.response?.data === 'string'
        ? err.response.data
        : 'Failed to log payout. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  // ─── Empty state (all tasks already have payouts) ──────────────────────
  if (!loadingTasks && tasks.length === 0) {
    return (
      <div className="page animate-in">
        <h1 className="page-title">Log Payout</h1>
        <div className="card">
          <div className="empty-state">
            <div className="empty-state-icon">✓</div>
            <h3>All caught up!</h3>
            <p>All your tasks already have payouts logged.</p>
            <button
              className="btn btn-primary"
              style={{ marginTop: '1.5rem' }}
              onClick={() => navigate('/log-task')}
            >
              Log a New Task
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="page animate-in">
      <h1 className="page-title">Log Payout</h1>
      <p className="page-subtitle">
        Enter the actual amount that was credited to your wallet for this delivery.
      </p>

      <div className="card">
        {error && <div className="alert alert-error">⚠ {error}</div>}
        {success && <div className="alert alert-success">✓ Payout logged! Redirecting…</div>}

        {loadingTasks ? (
          <div style={{ display: 'flex', justifyContent: 'center', padding: '2rem' }}>
            <span className="spinner" style={{ width: 28, height: 28, borderWidth: 3 }} />
          </div>
        ) : (
          <form id="form-log-payout" onSubmit={handleSubmit}>
            {/* Task selector */}
            <div className="form-group">
              <label className="form-label" htmlFor="select-task">Select Task</label>
              <select
                id="select-task"
                className="form-select"
                value={selectedTaskId}
                onChange={e => setSelectedTaskId(e.target.value)}
                required
              >
                {tasks.map(task => {
                  const date = new Date(task.acceptedAt).toLocaleString('en-IN', {
                    dateStyle: 'medium', timeStyle: 'short'
                  });
                  return (
                    <option key={task.id} value={task.id}>
                      {date} — ₹{task.promisedAmount} promised
                    </option>
                  );
                })}
              </select>
            </div>

            {/* Context: show the promised amount for reference */}
            {selectedTask && (
              <div style={{
                background: 'var(--color-surface-2)',
                border: '1px solid var(--color-border)',
                borderRadius: 'var(--radius-md)',
                padding: '0.75rem 1rem',
                marginBottom: '1.25rem',
                fontSize: '0.85rem',
                color: 'var(--text-secondary)',
              }}>
                Promised fare: <strong style={{ color: 'var(--text-primary)' }}>
                  ₹{selectedTask.promisedAmount}
                </strong>
                {selectedTask.distanceKm && (
                  <span> · {selectedTask.distanceKm} km</span>
                )}
              </div>
            )}

            {/* Actual amount */}
            <div className="form-group">
              <label className="form-label" htmlFor="input-actual">
                Actual Amount Received (₹)
              </label>
              <input
                id="input-actual"
                className="form-input"
                type="number"
                step="0.01"
                min="0"
                placeholder="e.g. 45.00"
                value={actualAmount}
                onChange={e => setActualAmount(e.target.value)}
                required
                autoFocus
              />
              <p className="form-hint">What actually appeared in your wallet?</p>
            </div>

            {/* Deduction reason */}
            <div className="form-group">
              <label className="form-label" htmlFor="input-reason">
                Deduction Reason <span style={{ color: 'var(--text-muted)', fontWeight: 400 }}>optional</span>
              </label>
              <input
                id="input-reason"
                className="form-input"
                type="text"
                placeholder="e.g. Weather surcharge reversal"
                value={deductionReason}
                onChange={e => setDeductionReason(e.target.value)}
              />
              <p className="form-hint">Any reason the platform gave for a deduction</p>
            </div>

            <div style={{ display: 'flex', gap: '0.75rem', marginTop: '0.5rem' }}>
              <button
                type="button"
                className="btn btn-ghost"
                style={{ flex: 1 }}
                onClick={() => navigate('/tasks')}
              >
                Cancel
              </button>
              <button
                id="btn-submit-payout"
                type="submit"
                className="btn btn-primary"
                style={{ flex: 2 }}
                disabled={loading || success}
              >
                {loading ? <span className="spinner" /> : 'Save Payout'}
              </button>
            </div>
          </form>
        )}
      </div>
    </div>
  );
}
