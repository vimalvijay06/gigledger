import { useState, useEffect } from 'react';
import { AlertCircle, TrendingDown, Percent, Coins, RefreshCw, ShieldCheck, ShieldAlert, FileDown } from 'lucide-react';
import api from '../api/client';

export default function AnalyticsPage() {
  const [tasks, setTasks] = useState([]);
  const [flags, setFlags] = useState([]);
  const [reportData, setReportData] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [triggering, setTriggering] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [scanMessage, setScanMessage] = useState('');

  const fetchData = () => {
    return Promise.all([
      api.get('/tasks'),
      api.get('/analytics/flags'),
      api.get('/reports/data')
    ])
      .then(([tasksRes, flagsRes, reportRes]) => {
        setTasks(tasksRes.data);
        setFlags(flagsRes.data);
        setReportData(reportRes.data);
      })
      .catch(() => setError('Failed to load analytics data.'));
  };

  useEffect(() => {
    fetchData().finally(() => setLoading(false));
  }, []);

  const runDetection = () => {
    setTriggering(true);
    setScanMessage('');
    api.post('/analytics/run-detection')
      .then(res => {
        const created = res.data.flagsCreated || 0;
        if (created > 0) {
          setScanMessage(`Scan complete! Found ${created} new systemic pattern discrepancy flag(s).`);
        } else {
          setScanMessage('Scan complete! No new systemic anomalies detected.');
        }
        fetchData();
      })
      .catch(() => setError('Failed to run anomaly scan. Please try again.'))
      .finally(() => setTriggering(false));
  };

  const downloadReport = async () => {
    setDownloading(true);
    setError('');
    try {
      const response = await api.get('/reports/generate', {
        responseType: 'blob'
      });
      const blob = new Blob([response.data], { type: 'application/pdf' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `gigledger-verification-report-${new Date().toISOString().slice(0, 10)}.pdf`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      console.error('Failed to download PDF report', err);
      setError('Failed to generate tamper-evident PDF report. Please try again.');
    } finally {
      setDownloading(false);
    }
  };

  const loggedPayouts = tasks.filter(t => t.payoutLogged);
  const underpaidTasks = loggedPayouts.filter(t => Number(t.difference) > 0);

  const totalUnderpaid = underpaidTasks.reduce((s, t) => s + Number(t.difference), 0);
  const avgUnderpayment = underpaidTasks.length > 0
    ? totalUnderpaid / underpaidTasks.length
    : 0;

  const underpaidPercent = loggedPayouts.length > 0
    ? Math.round((underpaidTasks.length / loggedPayouts.length) * 100)
    : 0;

  // Fuel Cost Analytics Calculations
  const fuelTrackedTasks = tasks.filter(t => t.estimatedFuelCost !== null && Number(t.estimatedFuelCost) >= 0);
  const fuelFlaggedTasks = fuelTrackedTasks.filter(t => t.fuelCostFlagged);
  
  const totalFuelCost = fuelTrackedTasks.reduce((s, t) => s + Number(t.estimatedFuelCost), 0);
  const avgFuelCost = fuelTrackedTasks.length > 0 ? totalFuelCost / fuelTrackedTasks.length : 0;
  const fuelConcernPercent = fuelTrackedTasks.length > 0
    ? Math.round((fuelFlaggedTasks.length / fuelTrackedTasks.length) * 100)
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
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 className="page-title" style={{ marginBottom: '0.25rem' }}>Earnings Analytics</h1>
          <p className="page-subtitle" style={{ marginBottom: 0 }}>
            Analyze underpayment rates and identify patterns in shortfalls.
          </p>
        </div>
        <div style={{ display: 'flex', gap: '8px' }}>
          <button
            className="btn btn-secondary"
            onClick={downloadReport}
            disabled={downloading}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '8px 12px',
              fontSize: '0.85rem',
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              cursor: downloading ? 'not-allowed' : 'pointer'
            }}
          >
            <FileDown size={14} className={downloading ? 'spin' : ''} />
            {downloading ? 'Compiling PDF...' : 'Download PDF Report'}
          </button>
          <button
            className="btn btn-secondary"
            onClick={runDetection}
            disabled={triggering || loggedPayouts.length < 30}
            style={{
              display: 'flex',
              alignItems: 'center',
              gap: '8px',
              padding: '8px 12px',
              fontSize: '0.85rem',
              background: 'var(--color-surface)',
              border: '1px solid var(--color-border)',
              cursor: loggedPayouts.length < 30 ? 'not-allowed' : 'pointer',
              opacity: loggedPayouts.length < 30 ? 0.5 : 1
            }}
            title={loggedPayouts.length < 30 ? "Requires at least 30 payouts" : "Trigger detection scan"}
          >
            <RefreshCw size={14} className={triggering ? 'spin' : ''} />
            {triggering ? 'Scanning...' : 'Scan Now'}
          </button>
        </div>
      </div>

      {error && <div className="alert alert-error" style={{ marginBottom: '1.5rem' }}>⚠ {error}</div>}
      {scanMessage && <div className="alert alert-success" style={{ marginBottom: '1.5rem', background: 'rgba(34, 197, 94, 0.08)', border: '1px solid rgba(34, 197, 94, 0.25)', color: 'var(--color-success)' }}>✓ {scanMessage}</div>}

      {/* Cryptographic Ledger Verification Status Card */}
      {reportData && (
        <div style={{
          display: 'flex',
          gap: '12px',
          background: reportData.integrityValid ? 'rgba(34, 197, 94, 0.05)' : 'rgba(239, 68, 68, 0.05)',
          border: reportData.integrityValid ? '1px solid rgba(34, 197, 94, 0.3)' : '1px solid rgba(239, 68, 68, 0.3)',
          borderRadius: 'var(--radius-md)',
          padding: '12px 16px',
          marginBottom: '1.5rem',
          alignItems: 'center'
        }}>
          {reportData.integrityValid ? (
            <>
              <ShieldCheck size={24} style={{ color: 'var(--color-success)', flexShrink: 0 }} />
              <div>
                <h4 style={{ fontSize: '0.88rem', fontWeight: 700, color: 'var(--color-success)', margin: 0 }}>
                  Cryptographic Ledger Seal Intact
                </h4>
                <p style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', margin: '2px 0 0 0' }}>
                  All {reportData.integrityTotalRecords} payout and discrepancy logs verified against the secure SHA-256 chain block.
                </p>
              </div>
            </>
          ) : (
            <>
              <ShieldAlert size={24} style={{ color: 'var(--color-danger)', flexShrink: 0 }} />
              <div>
                <h4 style={{ fontSize: '0.88rem', fontWeight: 700, color: 'var(--color-danger)', margin: 0 }}>
                  Ledger Integrity Compromised!
                </h4>
                <p style={{ fontSize: '0.78rem', color: 'var(--text-secondary)', margin: '2px 0 0 0' }}>
                  Warning: Data modification detected! Reference broken link: {reportData.integrityBrokenAt || 'N/A'}.
                </p>
              </div>
            </>
          )}
        </div>
      )}

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

      {/* Fuel Cost Fairness Insights Section */}
      <div className="card" style={{ marginBottom: '1.5rem' }}>
        <h3 className="section-title" style={{ fontSize: '1.1rem', marginBottom: '0.5rem', display: 'flex', alignItems: 'center', gap: '8px' }}>
          <AlertCircle size={20} style={{ color: 'var(--color-accent)' }} />
          Fuel-Cost Fairness Analysis
        </h3>
        <p style={{ color: 'var(--text-secondary)', fontSize: '0.8rem', marginBottom: '1.25rem' }}>
          Comparing your fares directly to estimated fuel expenditures based on real-world local petrol prices.
        </p>

        {fuelTrackedTasks.length === 0 ? (
          <div style={{ textAlign: 'center', padding: '1.5rem 1rem', color: 'var(--text-secondary)', border: '1px dashed var(--color-border)', borderRadius: 'var(--radius-md)' }}>
            <p style={{ fontSize: '0.88rem', fontWeight: 500, marginBottom: '0.25rem' }}>No Fuel Tracking Data Available</p>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              Configure your vehicle settings (efficiency, city) under your Profile and log tasks with distances to see fuel cost analysis.
            </p>
          </div>
        ) : (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '1rem', marginBottom: '1.5rem' }}>
              {/* Stat 1 */}
              <div style={{ background: 'var(--color-surface-2)', padding: '12px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                <span style={{ display: 'block', fontSize: '0.72rem', color: 'var(--text-secondary)', textTransform: 'uppercase', fontWeight: 600 }}>Fuel Concern Rate</span>
                <span style={{ display: 'block', fontSize: '1.4rem', fontWeight: 800, color: fuelConcernPercent > 20 ? 'var(--color-warning)' : 'var(--text-primary)', marginTop: '4px' }}>
                  {fuelConcernPercent}%
                </span>
                <span style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>fares barely cover fuel.</span>
              </div>

              {/* Stat 2 */}
              <div style={{ background: 'var(--color-surface-2)', padding: '12px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                <span style={{ display: 'block', fontSize: '0.72rem', color: 'var(--text-secondary)', textTransform: 'uppercase', fontWeight: 600 }}>Avg Fuel Cost / Trip</span>
                <span style={{ display: 'block', fontSize: '1.4rem', fontWeight: 800, color: 'var(--text-primary)', marginTop: '4px' }}>
                  {fmt(avgFuelCost)}
                </span>
                <span style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>spent on fuel per trip.</span>
              </div>

              {/* Stat 3 */}
              <div style={{ background: 'var(--color-surface-2)', padding: '12px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
                <span style={{ display: 'block', fontSize: '0.72rem', color: 'var(--text-secondary)', textTransform: 'uppercase', fontWeight: 600 }}>Fuel Analyzed Trips</span>
                <span style={{ display: 'block', fontSize: '1.4rem', fontWeight: 800, color: 'var(--text-primary)', marginTop: '4px' }}>
                  {fuelTrackedTasks.length}
                </span>
                <span style={{ fontSize: '0.7rem', color: 'var(--text-secondary)' }}>trips checked for fuel fairness.</span>
              </div>
            </div>

            {/* List of Fuel Concern Alerts */}
            {fuelFlaggedTasks.length > 0 ? (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
                <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600, textTransform: 'uppercase' }}>Recent Low-Fare Concerns</span>
                {fuelFlaggedTasks.slice(0, 5).map(task => (
                  <div key={task.id} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: 'rgba(245, 158, 11, 0.05)', border: '1px solid rgba(245, 158, 11, 0.25)', padding: '8px 12px', borderRadius: 'var(--radius-md)' }}>
                    <div>
                      <span style={{ fontSize: '0.8rem', color: 'var(--text-primary)', fontWeight: 600 }}>
                        {task.distanceKm} km Delivery
                      </span>
                      <span style={{ display: 'block', fontSize: '0.72rem', color: 'var(--text-secondary)', marginTop: '2px' }}>
                        Fare: {fmt(task.promisedAmount)} | Fuel Cost: {fmt(task.estimatedFuelCost)}
                      </span>
                    </div>
                    <span style={{ fontSize: '0.72rem', fontWeight: 700, color: 'var(--color-warning)', textTransform: 'uppercase', background: 'var(--color-bg)', padding: '2px 8px', borderRadius: '4px', border: '1px solid rgba(245,158,11,0.3)' }}>
                      {task.fuelCostSeverity} Severity
                    </span>
                  </div>
                ))}
              </div>
            ) : (
              <p style={{ fontSize: '0.8rem', color: 'var(--color-success)', margin: 0, fontStyle: 'italic' }}>
                ✓ Great! All analyzed delivery fares comfortably cover your estimated fuel expenses.
              </p>
            )}
          </div>
        )}
      </div>

      {/* Discrepancy Pattern Warnings */}
      <div className="card">
        <h3 className="section-title" style={{ fontSize: '1.1rem', marginBottom: '1rem' }}>Sustained Pattern Alerts</h3>
        
        {loggedPayouts.length < 30 ? (
          <div style={{ textAlign: 'center', padding: '1.5rem 1rem', color: 'var(--text-secondary)', border: '1px dashed var(--color-border)', borderRadius: 'var(--radius-md)' }}>
            <p style={{ fontSize: '0.88rem', fontWeight: 500, marginBottom: '0.25rem' }}>Insufficient Baseline Data</p>
            <p style={{ fontSize: '0.8rem', color: 'var(--text-muted)' }}>
              You currently have {loggedPayouts.length}/30 verified payouts logged. The ML anomaly detector requires at least 30 historical payouts to build your custom rolling earnings baseline and prevent false positives.
            </p>
          </div>
        ) : flags.length > 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
            {flags.map(flag => {
              const isHigh = flag.severity === 'high';
              const isMedium = flag.severity === 'medium';
              const borderCol = isHigh ? 'rgba(239, 68, 68, 0.4)' : isMedium ? 'rgba(249, 115, 22, 0.4)' : 'rgba(245, 158, 11, 0.4)';
              const bgCol = isHigh ? 'rgba(239, 68, 68, 0.05)' : isMedium ? 'rgba(249, 115, 22, 0.05)' : 'rgba(245, 158, 11, 0.05)';
              const textCol = isHigh ? 'var(--color-danger)' : isMedium ? 'var(--color-accent)' : 'var(--color-warning)';
              
              return (
                <div key={flag.id} style={{
                  display: 'flex',
                  gap: '1rem',
                  background: bgCol,
                  border: `1px solid ${borderCol}`,
                  borderRadius: 'var(--radius-md)',
                  padding: '1rem',
                  alignItems: 'flex-start'
                }}>
                  <AlertCircle size={20} style={{ color: textCol, flexShrink: 0, marginTop: '2px' }} />
                  <div style={{ flexGrow: 1 }}>
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.25rem', flexWrap: 'wrap', gap: '8px' }}>
                      <h4 style={{ fontSize: '0.9rem', fontWeight: 700, color: textCol, textTransform: 'capitalize' }}>
                        {flag.severity} Severity Discrepancy — {flag.bucket === 'peak' ? 'Peak Hours' : 'Off-Peak Hours'}
                      </h4>
                      <span style={{ fontSize: '0.72rem', color: 'var(--text-secondary)', background: 'var(--color-bg)', padding: '2px 8px', borderRadius: '4px', border: '1px solid var(--color-border)' }}>
                        {flag.periodStart} to {flag.periodEnd}
                      </span>
                    </div>
                    <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', lineHeight: 1.4 }}>
                      Systemic drop detected: Average rate was <strong>₹{Number(flag.observedRate).toFixed(2)}/km</strong>, 
                      which is <strong>{Number(flag.sdBelow).toFixed(2)} SD</strong> below your 30-day baseline of <strong>₹{Number(flag.baselineRate).toFixed(2)}/km</strong>.
                    </p>
                  </div>
                </div>
              );
            })}
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
