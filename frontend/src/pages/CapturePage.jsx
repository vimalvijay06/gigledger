import { useState, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  Camera, Edit, Upload, CheckCircle, AlertTriangle,
  X, RefreshCw, ImageIcon, ArrowRight, Loader
} from 'lucide-react';

const API = 'http://localhost:8080';

// ── Helper to get JWT from localStorage ─────────────────────────────────────
function getToken() {
  return localStorage.getItem('gl_token') || sessionStorage.getItem('gl_token') || '';
}

// ── Confidence level helper ──────────────────────────────────────────────────
function confidenceInfo(score) {
  if (score >= 0.75) return { level: 'HIGH',   color: '#22c55e', icon: '✓', bg: 'rgba(34,197,94,0.12)'  };
  if (score >= 0.45) return { level: 'MEDIUM', color: '#f59e0b', icon: '!', bg: 'rgba(245,158,11,0.12)' };
  return               { level: 'LOW',    color: '#ef4444', icon: '!', bg: 'rgba(239,68,68,0.12)'   };
}

// ── Step 1: Capture Method Chooser ───────────────────────────────────────────
function ChooserView({ onManual, onUpload }) {
  return (
    <div className="page animate-in">
      <h1 className="page-title">Capture Task</h1>
      <p className="page-subtitle">
        Choose how you'd like to record your new delivery task fare.
      </p>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>

        {/* Manual Entry */}
        <button
          className="card"
          style={{
            cursor: 'pointer', border: 'none', textAlign: 'left', width: '100%',
            display: 'flex', alignItems: 'center', gap: '1.5rem',
            transition: 'transform 0.18s ease, box-shadow 0.18s ease',
          }}
          onClick={onManual}
          onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.borderColor = '#ffb000'; }}
          onMouseLeave={e => { e.currentTarget.style.transform = 'none'; e.currentTarget.style.borderColor = 'var(--color-border)'; }}
        >
          <div style={{ background:'var(--color-accent-dim)', color:'#ffb000', width:52, height:52, borderRadius:'var(--radius-md)', display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
            <Edit size={24} />
          </div>
          <div>
            <h3 style={{ fontSize:'1.1rem', fontWeight:700, marginBottom:'0.25rem', color:'var(--text-primary)' }}>Enter Manually</h3>
            <p style={{ fontSize:'0.85rem', color:'var(--text-secondary)' }}>Type in the promised fare, distance, and date details yourself.</p>
          </div>
        </button>

        {/* Upload Screenshot OCR */}
        <button
          className="card"
          style={{
            cursor: 'pointer', border: 'none', textAlign: 'left', width: '100%',
            display: 'flex', alignItems: 'center', gap: '1.5rem',
            transition: 'transform 0.18s ease, box-shadow 0.18s ease',
            background: 'linear-gradient(135deg, rgba(255,176,0,0.06) 0%, rgba(255,140,0,0.04) 100%)',
          }}
          onClick={onUpload}
          onMouseEnter={e => { e.currentTarget.style.transform = 'translateY(-2px)'; e.currentTarget.style.borderColor = '#ffb000'; }}
          onMouseLeave={e => { e.currentTarget.style.transform = 'none'; e.currentTarget.style.borderColor = 'var(--color-border)'; }}
        >
          <div style={{ background:'var(--color-accent-dim)', color:'#ffb000', width:52, height:52, borderRadius:'var(--radius-md)', display:'flex', alignItems:'center', justifyContent:'center', flexShrink:0 }}>
            <Camera size={24} />
          </div>
          <div style={{ flex: 1 }}>
            <div style={{ display:'flex', alignItems:'center', gap:'8px', marginBottom:'0.25rem' }}>
              <h3 style={{ fontSize:'1.1rem', fontWeight:700, color:'var(--text-primary)', margin:0 }}>Upload Screenshot</h3>
              <span style={{ background:'rgba(255,176,0,0.2)', color:'#ffb000', fontSize:'0.62rem', fontWeight:700, padding:'2px 8px', borderRadius:'99px', textTransform:'uppercase', letterSpacing:'0.06em' }}>
                NEW — OCR
              </span>
            </div>
            <p style={{ fontSize:'0.85rem', color:'var(--text-secondary)', margin:0 }}>
              Snap or upload a Zomato/Swiggy order card — we extract the fare automatically.
            </p>
          </div>
          <ArrowRight size={18} style={{ color:'var(--text-secondary)', flexShrink:0 }} />
        </button>
      </div>

      <div className="card" style={{ marginTop:'2rem', display:'flex', gap:'1rem', background:'transparent', borderStyle:'dashed' }}>
        <p style={{ fontSize:'0.82rem', color:'var(--text-secondary)', lineHeight:1.5, margin:0 }}>
          <strong>Tip:</strong> Record tasks immediately after accepting them. This prevents platforms from silently modifying fare details later.
        </p>
      </div>
    </div>
  );
}

// ── Step 2: Upload + OCR Processing ─────────────────────────────────────────
function UploadView({ onResult, onBack }) {
  const [dragOver, setDragOver]   = useState(false);
  const [preview,  setPreview]    = useState(null);
  const [file,     setFile]       = useState(null);
  const [loading,  setLoading]    = useState(false);
  const [error,    setError]      = useState('');
  const fileRef = useRef();

  function handleFile(f) {
    if (!f) return;
    if (!f.type.startsWith('image/')) { setError('Please upload an image file (PNG, JPG, WEBP).'); return; }
    if (f.size > 10 * 1024 * 1024)   { setError('File too large — maximum 10 MB.'); return; }
    setError('');
    setFile(f);
    setPreview(URL.createObjectURL(f));
  }

  async function handleSubmit() {
    if (!file) return;
    setLoading(true); setError('');
    try {
      const form = new FormData();
      form.append('file', file);
      const res = await fetch(`${API}/tasks/upload-screenshot`, {
        method: 'POST',
        headers: { Authorization: `Bearer ${getToken()}` },
        body: form,
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message || `Server error ${res.status}`);
      }
      const data = await res.json();
      onResult(data, file);
    } catch (e) {
      setError(e.message || 'Upload failed — check that the server is running.');
    } finally {
      setLoading(false);
    }
  }

  return (
    <div className="page animate-in">
      <button onClick={onBack} style={{ background:'none', border:'none', color:'var(--text-secondary)', cursor:'pointer', display:'flex', alignItems:'center', gap:'6px', fontSize:'0.875rem', marginBottom:'1rem', padding:0 }}>
        ← Back
      </button>
      <h1 className="page-title">Upload Screenshot</h1>
      <p className="page-subtitle">Upload a delivery-app screenshot and we'll extract the fare automatically using OCR.</p>

      {/* Drop zone */}
      <div
        style={{
          border: `2px dashed ${dragOver ? '#ffb000' : 'var(--color-border)'}`,
          borderRadius: 'var(--radius-lg)',
          padding: preview ? '1rem' : '3rem 1rem',
          textAlign: 'center',
          cursor: 'pointer',
          transition: 'border-color 0.2s, background 0.2s',
          background: dragOver ? 'rgba(255,176,0,0.05)' : 'var(--color-surface)',
          position: 'relative',
          overflow: 'hidden',
        }}
        onClick={() => !loading && fileRef.current?.click()}
        onDragOver={e => { e.preventDefault(); setDragOver(true); }}
        onDragLeave={() => setDragOver(false)}
        onDrop={e => { e.preventDefault(); setDragOver(false); handleFile(e.dataTransfer.files[0]); }}
      >
        <input
          ref={fileRef}
          type="file"
          accept="image/*"
          capture="environment"
          style={{ display:'none' }}
          onChange={e => handleFile(e.target.files[0])}
        />

        {preview ? (
          <div>
            <img src={preview} alt="Preview" style={{ maxWidth:'100%', maxHeight:280, borderRadius:'var(--radius-md)', objectFit:'contain' }} />
            <button
              onClick={e => { e.stopPropagation(); setPreview(null); setFile(null); }}
              style={{ position:'absolute', top:10, right:10, background:'rgba(0,0,0,0.6)', border:'none', color:'#fff', borderRadius:'50%', width:28, height:28, cursor:'pointer', display:'flex', alignItems:'center', justifyContent:'center' }}
            >
              <X size={14} />
            </button>
          </div>
        ) : (
          <>
            <div style={{ width:56, height:56, borderRadius:'50%', background:'var(--color-accent-dim)', display:'flex', alignItems:'center', justifyContent:'center', margin:'0 auto 1rem' }}>
              <ImageIcon size={26} style={{ color:'#ffb000' }} />
            </div>
            <p style={{ fontWeight:600, color:'var(--text-primary)', marginBottom:'0.4rem' }}>Drop your screenshot here</p>
            <p style={{ fontSize:'0.82rem', color:'var(--text-secondary)', marginBottom:'1rem' }}>or tap to choose from gallery / camera</p>
            <div style={{ display:'flex', justifyContent:'center', gap:'0.75rem' }}>
              <span style={{ fontSize:'0.75rem', color:'var(--text-secondary)', background:'var(--color-surface-2)', padding:'3px 10px', borderRadius:'99px' }}>PNG</span>
              <span style={{ fontSize:'0.75rem', color:'var(--text-secondary)', background:'var(--color-surface-2)', padding:'3px 10px', borderRadius:'99px' }}>JPG</span>
              <span style={{ fontSize:'0.75rem', color:'var(--text-secondary)', background:'var(--color-surface-2)', padding:'3px 10px', borderRadius:'99px' }}>WEBP</span>
            </div>
          </>
        )}
      </div>

      {error && (
        <div style={{ marginTop:'1rem', padding:'0.75rem 1rem', background:'rgba(239,68,68,0.1)', border:'1px solid rgba(239,68,68,0.25)', borderRadius:'var(--radius-md)', color:'#ef4444', fontSize:'0.875rem', display:'flex', gap:'8px', alignItems:'flex-start' }}>
          <AlertTriangle size={16} style={{ flexShrink:0, marginTop:1 }} />
          {error}
        </div>
      )}

      <button
        onClick={handleSubmit}
        disabled={!file || loading}
        style={{
          marginTop:'1.25rem', width:'100%', padding:'0.875rem',
          background: (!file || loading) ? 'var(--color-surface-2)' : 'var(--color-accent)',
          color: (!file || loading) ? 'var(--text-secondary)' : '#000',
          border:'none', borderRadius:'var(--radius-md)', fontWeight:700, fontSize:'1rem',
          cursor: (!file || loading) ? 'not-allowed' : 'pointer',
          display:'flex', alignItems:'center', justifyContent:'center', gap:'8px',
          transition:'background 0.2s',
        }}
      >
        {loading ? (
          <><Loader size={18} style={{ animation:'spin 1s linear infinite' }} /> Scanning with OCR…</>
        ) : (
          <><Upload size={18} /> Extract Fare from Screenshot</>
        )}
      </button>

      <p style={{ textAlign:'center', fontSize:'0.78rem', color:'var(--text-secondary)', marginTop:'0.75rem' }}>
        Supported apps: Zomato, Swiggy, and more
      </p>
    </div>
  );
}

// ── Step 3: Review OCR Results + Editable Confirm Form ───────────────────────
function ConfirmView({ ocrData, imageFile, onConfirmed, onRetry }) {
  const conf    = confidenceInfo(ocrData.ocrConfidence ?? 0);
  const navigate = useNavigate();

  const [form, setForm] = useState({
    promisedAmount: ocrData.promisedAmount ?? '',
    distanceKm:     ocrData.distanceKm    ?? '',
    acceptedAt:     new Date().toISOString().slice(0, 16),
  });
  const [loading, setLoading] = useState(false);
  const [error,   setError]   = useState('');

  function update(field) {
    return e => setForm(f => ({ ...f, [field]: e.target.value }));
  }

  async function handleConfirm() {
    if (!form.promisedAmount || isNaN(Number(form.promisedAmount)) || Number(form.promisedAmount) <= 0) {
      setError('Please enter a valid promised amount.'); return;
    }
    if (!form.acceptedAt) { setError('Please select the date and time you accepted the task.'); return; }

    setLoading(true); setError('');
    try {
      const payload = {
        tempFileRef:    ocrData.tempFileRef,
        ocrConfidence:  ocrData.ocrConfidence ?? 0,
        promisedAmount: Number(form.promisedAmount),
        distanceKm:     form.distanceKm ? Number(form.distanceKm) : null,
        acceptedAt:     new Date(form.acceptedAt).toISOString().replace('Z', ''),
      };
      const res = await fetch(`${API}/tasks/confirm`, {
        method:  'POST',
        headers: { 'Content-Type':'application/json', Authorization:`Bearer ${getToken()}` },
        body:    JSON.stringify(payload),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body.message || `Server error ${res.status}`);
      }
      onConfirmed();
    } catch (e) {
      setError(e.message || 'Failed to save task.');
    } finally {
      setLoading(false);
    }
  }

  const inputStyle = {
    width: '100%', padding: '0.7rem 0.875rem', boxSizing:'border-box',
    background: 'var(--color-surface-2)', border: '1px solid var(--color-border)',
    borderRadius: 'var(--radius-md)', color: 'var(--text-primary)', fontSize: '0.95rem',
    outline: 'none',
  };
  const labelStyle = {
    fontSize: '0.8rem', fontWeight: 600, color: 'var(--text-secondary)',
    textTransform: 'uppercase', letterSpacing: '0.05em', marginBottom: '0.4rem', display:'block',
  };

  return (
    <div className="page animate-in">
      <h1 className="page-title">Review &amp; Confirm</h1>
      <p className="page-subtitle">We extracted these values from your screenshot. Correct anything that looks wrong, then confirm.</p>

      {/* Confidence badge */}
      <div style={{
        display:'flex', alignItems:'center', gap:'10px', padding:'0.75rem 1rem',
        background: conf.bg, border:`1px solid ${conf.color}40`,
        borderRadius:'var(--radius-md)', marginBottom:'1.25rem',
      }}>
        <div style={{
          width:28, height:28, borderRadius:'50%', background:`${conf.color}20`,
          border:`2px solid ${conf.color}`, display:'flex', alignItems:'center', justifyContent:'center',
          fontWeight:800, color:conf.color, fontSize:'0.85rem', flexShrink:0,
        }}>
          {conf.icon}
        </div>
        <div>
          <p style={{ margin:0, fontSize:'0.85rem', fontWeight:700, color:conf.color }}>
            OCR Confidence: {conf.level} ({Math.round((ocrData.ocrConfidence ?? 0) * 100)}%)
          </p>
          <p style={{ margin:0, fontSize:'0.78rem', color:'var(--text-secondary)' }}>
            {conf.level === 'HIGH'   && 'Values look reliable — verify and confirm.'}
            {conf.level === 'MEDIUM' && 'Extraction was partial — please verify each field carefully.'}
            {conf.level === 'LOW'    && 'OCR had difficulty reading this screenshot — please check every field.'}
          </p>
        </div>
      </div>

      {/* Editable form */}
      <div className="card" style={{ display:'flex', flexDirection:'column', gap:'1rem' }}>

        <div>
          <label style={labelStyle}>Promised Amount (₹) *</label>
          <input
            type="number" min="0" step="0.01" value={form.promisedAmount}
            onChange={update('promisedAmount')} style={inputStyle}
            placeholder="e.g. 85.00"
          />
          {ocrData.promisedAmount == null && (
            <p style={{ fontSize:'0.75rem', color:'#f59e0b', marginTop:'4px', display:'flex', alignItems:'center', gap:'4px' }}>
              <AlertTriangle size={12} /> Could not extract — please enter manually
            </p>
          )}
        </div>

        <div>
          <label style={labelStyle}>Distance (km)</label>
          <input
            type="number" min="0" step="0.1" value={form.distanceKm}
            onChange={update('distanceKm')} style={inputStyle}
            placeholder="e.g. 3.5  (optional)"
          />
        </div>

        <div>
          <label style={labelStyle}>Accepted At *</label>
          <input
            type="datetime-local" value={form.acceptedAt}
            onChange={update('acceptedAt')} style={inputStyle}
          />
        </div>
      </div>

      {/* Raw OCR output (collapsible) */}
      {ocrData.rawText && (
        <details style={{ marginTop:'1rem' }}>
          <summary style={{ cursor:'pointer', fontSize:'0.8rem', color:'var(--text-secondary)', fontWeight:600, userSelect:'none' }}>
            Show raw OCR text
          </summary>
          <pre style={{
            marginTop:'0.5rem', padding:'0.75rem', background:'var(--color-surface)',
            border:'1px solid var(--color-border)', borderRadius:'var(--radius-md)',
            fontSize:'0.75rem', color:'var(--text-secondary)', whiteSpace:'pre-wrap',
            wordBreak:'break-word', maxHeight:160, overflow:'auto',
          }}>
            {ocrData.rawText}
          </pre>
        </details>
      )}

      {error && (
        <div style={{ marginTop:'1rem', padding:'0.75rem 1rem', background:'rgba(239,68,68,0.1)', border:'1px solid rgba(239,68,68,0.25)', borderRadius:'var(--radius-md)', color:'#ef4444', fontSize:'0.875rem', display:'flex', gap:'8px' }}>
          <AlertTriangle size={16} style={{ flexShrink:0 }} /> {error}
        </div>
      )}

      <div style={{ display:'flex', gap:'0.75rem', marginTop:'1.5rem' }}>
        <button
          onClick={onRetry}
          style={{
            flex:1, padding:'0.875rem', background:'var(--color-surface-2)',
            border:'1px solid var(--color-border)', borderRadius:'var(--radius-md)',
            color:'var(--text-secondary)', fontWeight:600, cursor:'pointer',
            display:'flex', alignItems:'center', justifyContent:'center', gap:'6px',
          }}
        >
          <RefreshCw size={16} /> Retake
        </button>
        <button
          onClick={handleConfirm}
          disabled={loading}
          style={{
            flex:2, padding:'0.875rem',
            background: loading ? 'var(--color-surface-2)' : 'var(--color-accent)',
            color: loading ? 'var(--text-secondary)' : '#000',
            border:'none', borderRadius:'var(--radius-md)', fontWeight:700, fontSize:'1rem',
            cursor: loading ? 'not-allowed' : 'pointer',
            display:'flex', alignItems:'center', justifyContent:'center', gap:'8px',
          }}
        >
          {loading
            ? <><Loader size={18} style={{ animation:'spin 1s linear infinite' }} /> Saving…</>
            : <><CheckCircle size={18} /> Confirm &amp; Save Task</>
          }
        </button>
      </div>
    </div>
  );
}

// ── Step 4: Success screen ───────────────────────────────────────────────────
function SuccessView({ onAnother, onLedger }) {
  return (
    <div className="page animate-in" style={{ textAlign:'center', paddingTop:'3rem' }}>
      <div style={{
        width:72, height:72, borderRadius:'50%', background:'rgba(34,197,94,0.15)',
        border:'2px solid #22c55e', display:'flex', alignItems:'center', justifyContent:'center',
        margin:'0 auto 1.5rem',
      }}>
        <CheckCircle size={36} style={{ color:'#22c55e' }} />
      </div>
      <h1 style={{ fontSize:'1.5rem', fontWeight:800, color:'var(--text-primary)', marginBottom:'0.5rem' }}>Task Saved!</h1>
      <p style={{ color:'var(--text-secondary)', marginBottom:'2rem' }}>
        Your task has been recorded in your GigLedger.
      </p>
      <div style={{ display:'flex', flexDirection:'column', gap:'0.75rem' }}>
        <button
          onClick={onAnother}
          style={{
            width:'100%', padding:'0.875rem', background:'var(--color-accent)',
            border:'none', borderRadius:'var(--radius-md)', fontWeight:700, fontSize:'1rem',
            color:'#000', cursor:'pointer',
          }}
        >
          Capture Another Task
        </button>
        <button
          onClick={onLedger}
          style={{
            width:'100%', padding:'0.875rem', background:'transparent',
            border:'1px solid var(--color-border)', borderRadius:'var(--radius-md)',
            color:'var(--text-secondary)', fontWeight:600, cursor:'pointer',
          }}
        >
          View Ledger
        </button>
      </div>
    </div>
  );
}

// ── Main Page Orchestrator ────────────────────────────────────────────────────
export default function CapturePage() {
  const navigate = useNavigate();
  // 'choose' | 'upload' | 'confirm' | 'success'
  const [step,    setStep]    = useState('choose');
  const [ocrData, setOcrData] = useState(null);

  if (step === 'choose') {
    return (
      <ChooserView
        onManual={() => navigate('/log-task')}
        onUpload={() => setStep('upload')}
      />
    );
  }
  if (step === 'upload') {
    return (
      <UploadView
        onBack={() => setStep('choose')}
        onResult={(data) => { setOcrData(data); setStep('confirm'); }}
      />
    );
  }
  if (step === 'confirm') {
    return (
      <ConfirmView
        ocrData={ocrData}
        onConfirmed={() => setStep('success')}
        onRetry={() => setStep('upload')}
      />
    );
  }
  if (step === 'success') {
    return (
      <SuccessView
        onAnother={() => setStep('choose')}
        onLedger={() => navigate('/ledger')}
      />
    );
  }
}
