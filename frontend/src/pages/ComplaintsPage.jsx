import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { ArrowLeft, Send, Trash2, FileText, Download, Scale, CheckCircle2 } from 'lucide-react';
import api from '../api/client';

export default function ComplaintsPage() {
  const navigate = useNavigate();
  const [drafts, setDrafts] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const [activeDraft, setActiveDraft] = useState(null);
  const [editedText, setEditedText] = useState('');
  const [showSendModal, setShowSendModal] = useState(false);
  const [showDismissModal, setShowDismissModal] = useState(false);
  const [dismissReason, setDismissReason] = useState('');
  const [downloading, setDownloading] = useState(false);

  useEffect(() => {
    fetchDrafts();
  }, []);

  function fetchDrafts() {
    setLoading(true);
    api.get('/complaints')
      .then(res => {
        setDrafts(res.data);
        if (res.data.length > 0) {
          setActiveDraft(res.data[0]);
          setEditedText(res.data[0].draftText);
        } else {
          setActiveDraft(null);
          setEditedText('');
        }
      })
      .catch(() => setError('Failed to load pending grievance drafts.'))
      .finally(() => setLoading(false));
  }

  function handleSelectDraft(draft) {
    setActiveDraft(draft);
    setEditedText(draft.draftText);
  }

  async function handleDownloadPDF() {
    setDownloading(true);
    try {
      const response = await api.get('/analytics/report/pdf', { responseType: 'blob' });
      const blob = new Blob([response.data], { type: 'application/pdf' });
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.setAttribute('download', `gigledger-discrepancy-report.pdf`);
      document.body.appendChild(link);
      link.click();
      link.remove();
    } catch (err) {
      console.error(err);
      alert("Failed to download PDF report.");
    } finally {
      setDownloading(false);
    }
  }

  function confirmSend() {
    if (!activeDraft) return;
    api.post(`/complaints/${activeDraft.id}/send`, { draftText: editedText })
      .then(() => {
        setSuccess('Grievance complaint sent successfully (Simulated!).');
        setShowSendModal(false);
        setTimeout(() => setSuccess(''), 4000);
        fetchDrafts();
      })
      .catch(err => {
        console.error(err);
        setError('Failed to send grievance complaint.');
      });
  }

  function confirmDismiss() {
    if (!activeDraft) return;
    api.post(`/complaints/${activeDraft.id}/dismiss`, { reason: dismissReason })
      .then(() => {
        setSuccess('Complaint draft dismissed.');
        setShowDismissModal(false);
        setDismissReason('');
        setTimeout(() => setSuccess(''), 4000);
        fetchDrafts();
      })
      .catch(err => {
        console.error(err);
        setError('Failed to dismiss complaint draft.');
      });
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
      {/* Header with Back button */}
      <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem', marginBottom: '1.5rem' }}>
        <button
          className="top-bar-icon-btn"
          onClick={() => navigate('/')}
          style={{ width: 36, height: 36 }}
        >
          <ArrowLeft size={20} />
        </button>
        <div>
          <h1 className="page-title" style={{ margin: 0, fontSize: '1.4rem' }}>Grievance Redressal</h1>
        </div>
      </div>

      {error && <div className="alert alert-error" style={{ marginBottom: '1rem' }}>⚠ {error}</div>}
      {success && <div className="alert alert-success" style={{ marginBottom: '1rem' }}>✓ {success}</div>}

      {drafts.length === 0 ? (
        <div className="card">
          <div className="empty-state" style={{ padding: '3rem 1rem' }}>
            <div style={{
              background: 'rgba(34, 197, 94, 0.08)',
              color: 'var(--color-success)',
              width: 56, height: 56,
              borderRadius: '50%',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
              margin: '0 auto 1rem auto'
            }}>
              <CheckCircle2 size={32} />
            </div>
            <h3>No Pending Drafts</h3>
            <p>You have no pending grievance complaints. New drafts will generate automatically if pay rate anomalies cross your settings threshold.</p>
          </div>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {/* Draft selector list */}
          {drafts.length > 1 && (
            <div style={{ display: 'flex', gap: '8px', overflowX: 'auto', paddingBottom: '4px' }}>
              {drafts.map(d => (
                <button
                  key={d.id}
                  onClick={() => handleSelectDraft(d)}
                  style={{
                    background: activeDraft?.id === d.id ? 'var(--color-accent)' : 'var(--color-surface-2)',
                    color: activeDraft?.id === d.id ? '#000' : 'var(--text-primary)',
                    border: '1px solid var(--color-border)',
                    borderRadius: '20px',
                    padding: '6px 12px',
                    fontSize: '0.75rem',
                    fontWeight: 700,
                    cursor: 'pointer',
                    whiteSpace: 'nowrap'
                  }}
                >
                  {d.platformName} ({new Date(d.createdAt).toLocaleDateString()})
                </button>
              ))}
            </div>
          )}

          {/* Active draft review card */}
          {activeDraft && (
            <div className="card" style={{ display: 'flex', flexDirection: 'column', gap: '1rem', padding: '1.25rem' }}>
              {/* Target contact header */}
              <div style={{ 
                background: 'var(--color-surface-2)', 
                border: '1px solid var(--color-border)', 
                borderRadius: 'var(--radius-md)', 
                padding: '0.75rem 1rem'
              }}>
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                  <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 }}>Platform Recipient</span>
                  <span style={{ 
                    fontSize: '0.7rem', 
                    background: activeDraft.grievanceEmail.includes('@') ? 'rgba(34,197,94,0.1)' : 'rgba(239,68,68,0.1)', 
                    color: activeDraft.grievanceEmail.includes('@') ? 'var(--color-success)' : 'var(--color-danger)', 
                    padding: '2px 6px',
                    borderRadius: '4px',
                    fontWeight: 700
                  }}>
                    {activeDraft.grievanceEmail.includes('@') ? 'VERIFIED EMAIL' : 'PENDING VERIFICATION'}
                  </span>
                </div>
                <h4 style={{ margin: '4px 0 0 0', color: 'var(--text-primary)', fontSize: '0.95rem', fontWeight: 800 }}>
                  {activeDraft.platformName} Officer
                </h4>
                <p style={{ margin: '2px 0 0 0', color: 'var(--text-secondary)', fontSize: '0.8rem' }}>
                  Grievance Email: <code style={{ color: 'var(--color-accent)' }}>{activeDraft.grievanceEmail}</code>
                </p>
              </div>

              {/* Subject */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 }}>Email Subject</label>
                <input
                  type="text"
                  value={activeDraft.subject}
                  disabled
                  style={{
                    background: 'var(--color-surface-2)',
                    border: '1px solid var(--color-border)',
                    color: 'var(--text-primary)',
                    borderRadius: 'var(--radius-md)',
                    padding: '8px 12px',
                    fontSize: '0.85rem',
                    outline: 'none'
                  }}
                />
              </div>

              {/* Draft text area */}
              <div style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
                <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 }}>Grievance Body (Editable)</label>
                <textarea
                  value={editedText}
                  onChange={e => setEditedText(e.target.value)}
                  style={{
                    background: 'var(--color-surface-2)',
                    border: '1px solid var(--color-border)',
                    color: 'var(--text-primary)',
                    borderRadius: 'var(--radius-md)',
                    padding: '10px 12px',
                    fontSize: '0.82rem',
                    lineHeight: 1.5,
                    minHeight: '260px',
                    resize: 'vertical',
                    outline: 'none',
                    fontFamily: 'monospace'
                  }}
                />
              </div>

              {/* Audit trail attachment */}
              <div style={{ 
                border: '1px dashed var(--color-border)',
                borderRadius: 'var(--radius-md)',
                padding: '10px 12px',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'space-between',
                fontSize: '0.8rem'
              }}>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', color: 'var(--text-secondary)' }}>
                  <FileText size={18} />
                  <span>Linked Verification PDF Report</span>
                </div>
                <button
                  className="btn btn-ghost"
                  onClick={handleDownloadPDF}
                  disabled={downloading}
                  style={{ padding: '4px 10px', fontSize: '0.75rem', display: 'flex', alignItems: 'center', gap: '4px' }}
                >
                  {downloading ? 'Downloading...' : <><Download size={14} /> Download</>}
                </button>
              </div>

              {/* Action Buttons */}
              <div style={{ display: 'flex', gap: '10px', marginTop: '8px' }}>
                <button
                  className="btn btn-danger"
                  onClick={() => setShowDismissModal(true)}
                  style={{ flex: 1, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                >
                  <Trash2 size={16} />
                  Dismiss Draft
                </button>

                <button
                  className="btn btn-primary"
                  onClick={() => setShowSendModal(true)}
                  style={{ flex: 2, display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '6px' }}
                >
                  <Send size={16} />
                  Send Complaint
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Confirmation Modals */}
      {showSendModal && activeDraft && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(4px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          padding: '1.5rem', zIndex: 10000
        }}>
          <div className="card" style={{ width: '100%', maxWidth: 400, textAlign: 'center', padding: '1.5rem' }}>
            <Scale size={36} style={{ color: 'var(--color-accent)', margin: '0 auto 12px auto' }} />
            <h3 style={{ margin: 0, color: 'var(--text-primary)' }}>Confirm Grievance Dispatch</h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '8px', lineHeight: 1.4 }}>
              This will dispatch a formal legal complaint letter to <strong style={{ color: 'var(--text-primary)' }}>{activeDraft.grievanceEmail}</strong> on your behalf.
            </p>
            <div style={{ display: 'flex', gap: '8px', marginTop: '1.5rem' }}>
              <button className="btn btn-ghost" style={{ flex: 1 }} onClick={() => setShowSendModal(false)}>
                Cancel
              </button>
              <button className="btn btn-primary" style={{ flex: 2 }} onClick={confirmSend}>
                Yes, Send Complaint
              </button>
            </div>
          </div>
        </div>
      )}

      {showDismissModal && activeDraft && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(4px)',
          display: 'flex', alignItems: 'center', justifyContent: 'center',
          padding: '1.5rem', zIndex: 10000
        }}>
          <div className="card" style={{ width: '100%', maxWidth: 400, padding: '1.5rem' }}>
            <h3 style={{ margin: 0, color: 'var(--text-primary)', textAlign: 'center' }}>Dismiss Complaint Draft</h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)', marginTop: '8px', lineHeight: 1.4, textAlign: 'center' }}>
              Are you sure you want to dismiss this complaint draft?
            </p>
            
            <div style={{ display: 'flex', flexDirection: 'column', gap: '4px', marginTop: '1rem' }}>
              <label style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600 }}>Reason for Dismissal</label>
              <input
                type="text"
                placeholder="e.g. payout rectified, calculation error..."
                value={dismissReason}
                onChange={e => setDismissReason(e.target.value)}
                style={{
                  background: 'var(--color-surface-2)',
                  border: '1px solid var(--color-border)',
                  color: 'var(--text-primary)',
                  borderRadius: 'var(--radius-md)',
                  padding: '8px 12px',
                  fontSize: '0.85rem',
                  outline: 'none'
                }}
              />
            </div>

            <div style={{ display: 'flex', gap: '8px', marginTop: '1.5rem' }}>
              <button className="btn btn-ghost" style={{ flex: 1 }} onClick={() => setShowDismissModal(false)}>
                Cancel
              </button>
              <button className="btn btn-danger" style={{ flex: 1 }} onClick={confirmDismiss}>
                Dismiss
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
