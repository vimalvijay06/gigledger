import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import api from '../api/client';

/**
 * LogTaskPage — form to manually record a new delivery task.
 *
 * Fields:
 * - promisedAmount: the fare shown on the delivery app before accepting
 * - distanceKm: delivery distance at acceptance (optional)
 * - acceptedAt: datetime the worker accepted (defaults to right now)
 *
 * The datetime-local input gives a natural picker in mobile browsers.
 * We convert it to an ISO string before sending to the backend because
 * Spring's @JsonFormat expects ISO-8601 for LocalDateTime.
 */
export default function LogTaskPage() {
  const navigate = useNavigate();

  // Default accepted-at to "now" (formatted for datetime-local input)
  const nowLocal = new Date(Date.now() - new Date().getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);

  const [promisedAmount, setPromisedAmount] = useState('');
  const [distanceKm, setDistanceKm]         = useState('');
  const [acceptedAt, setAcceptedAt]         = useState(nowLocal);
  const [error, setError]                   = useState('');
  const [success, setSuccess]               = useState(false);
  const [loading, setLoading]               = useState(false);

  async function handleSubmit(e) {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      // Convert the local datetime string to an ISO format Spring can parse
      const payload = {
        promisedAmount: parseFloat(promisedAmount),
        distanceKm: distanceKm ? parseFloat(distanceKm) : null,
        // datetime-local gives "YYYY-MM-DDTHH:mm"; backend expects LocalDateTime
        acceptedAt: new Date(acceptedAt).toISOString().slice(0, 19),
      };

      await api.post('/tasks', payload);
      setSuccess(true);

      // Brief pause so user can see the success message, then go to task list
      setTimeout(() => navigate('/tasks'), 1500);
    } catch (err) {
      const msg = typeof err.response?.data === 'string'
        ? err.response.data
        : 'Failed to log task. Please try again.';
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page animate-in">
      <h1 className="page-title">Log a Task</h1>
      <p className="page-subtitle">
        Record the fare your app promised you before you accepted this delivery.
      </p>

      <div className="card">
        {error && <div className="alert alert-error">⚠ {error}</div>}
        {success && <div className="alert alert-success">✓ Task logged! Redirecting…</div>}

        <form id="form-log-task" onSubmit={handleSubmit}>
          <div className="form-group">
            <label className="form-label" htmlFor="input-promised">
              Promised Fare (₹)
            </label>
            <input
              id="input-promised"
              className="form-input"
              type="number"
              step="0.01"
              min="0.01"
              placeholder="e.g. 52.00"
              value={promisedAmount}
              onChange={e => setPromisedAmount(e.target.value)}
              required
              autoFocus
            />
            <p className="form-hint">The amount shown on the app when you accepted the order</p>
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="input-distance">
              Distance (km) <span style={{ color: 'var(--text-muted)', fontWeight: 400 }}>optional</span>
            </label>
            <input
              id="input-distance"
              className="form-input"
              type="number"
              step="0.1"
              min="0"
              placeholder="e.g. 3.5"
              value={distanceKm}
              onChange={e => setDistanceKm(e.target.value)}
            />
          </div>

          <div className="form-group">
            <label className="form-label" htmlFor="input-accepted-at">
              Accepted At
            </label>
            <input
              id="input-accepted-at"
              className="form-input"
              type="datetime-local"
              value={acceptedAt}
              onChange={e => setAcceptedAt(e.target.value)}
              required
            />
            <p className="form-hint">When did you accept this order? You can backdate it.</p>
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
              id="btn-submit-task"
              type="submit"
              className="btn btn-primary"
              style={{ flex: 2 }}
              disabled={loading || success}
            >
              {loading ? <span className="spinner" /> : 'Save Task'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
}
