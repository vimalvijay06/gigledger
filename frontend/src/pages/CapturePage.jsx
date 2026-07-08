import { useNavigate } from 'react-router-dom';
import { Camera, Edit, HelpCircle } from 'lucide-react';

export default function CapturePage() {
  const navigate = useNavigate();

  return (
    <div className="page animate-in">
      <h1 className="page-title">Capture Task</h1>
      <p className="page-subtitle">
        Choose how you would like to record your new delivery task fare.
      </p>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
        {/* Option 1: Manual entry */}
        <div
          className="card"
          style={{
            cursor: 'pointer',
            transition: 'transform 0.2s ease, border-color 0.2s ease',
            display: 'flex',
            alignItems: 'center',
            gap: '1.5rem',
          }}
          onClick={() => navigate('/log-task')}
          onMouseEnter={e => {
            e.currentTarget.style.transform = 'translateY(-2px)';
            e.currentTarget.style.borderColor = '#ffb000';
          }}
          onMouseLeave={e => {
            e.currentTarget.style.transform = 'none';
            e.currentTarget.style.borderColor = 'var(--color-border)';
          }}
        >
          <div style={{
            background: 'var(--color-accent-dim)',
            color: '#ffb000',
            width: 52,
            height: 52,
            borderRadius: 'var(--radius-md)',
            display: 'flex',
            alignItems: 'center',
            justify-content: 'center',
            flexShrink: 0
          }}>
            <Edit size={24} />
          </div>
          <div>
            <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '0.25rem', color: 'var(--text-primary)' }}>
              Enter Manually
            </h3>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              Type in the promised fare, distance, and date details yourself.
            </p>
          </div>
        </div>

        {/* Option 2: Upload screenshot (OCR) */}
        <div
          className="card"
          style={{
            cursor: 'pointer',
            transition: 'transform 0.2s ease, border-color 0.2s ease',
            display: 'flex',
            alignItems: 'center',
            gap: '1.5rem',
            opacity: 0.85
          }}
          onClick={() => alert('Screenshot OCR upload is coming in Phase 2!')}
          onMouseEnter={e => {
            e.currentTarget.style.transform = 'translateY(-2px)';
            e.currentTarget.style.borderColor = '#ffb000';
          }}
          onMouseLeave={e => {
            e.currentTarget.style.transform = 'none';
            e.currentTarget.style.borderColor = 'var(--color-border)';
          }}
        >
          <div style={{
            background: 'var(--color-surface-2)',
            color: 'var(--text-secondary)',
            width: 52,
            height: 52,
            borderRadius: 'var(--radius-md)',
            display: 'flex',
            alignItems: 'center',
            justify-content: 'center',
            flexShrink: 0
          }}>
            <Camera size={24} />
          </div>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <h3 style={{ fontSize: '1.1rem', fontWeight: 700, marginBottom: '0.25rem', color: 'var(--text-primary)' }}>
                Upload Screenshot
              </h3>
              <span style={{
                background: 'rgba(255, 255, 255, 0.1)',
                color: 'var(--text-secondary)',
                fontSize: '0.65rem',
                fontWeight: 700,
                padding: '2px 6px',
                borderRadius: '4px',
                textTransform: 'uppercase'
              }}>
                Coming Soon
              </span>
            </div>
            <p style={{ fontSize: '0.85rem', color: 'var(--text-secondary)' }}>
              Take a screenshot of Zomato/Swiggy order card; we'll scan the fare.
            </p>
          </div>
        </div>
      </div>

      {/* Help info */}
      <div className="card" style={{ marginTop: '2rem', display: 'flex', gap: '1rem', background: 'transparent', borderStyle: 'dashed' }}>
        <HelpCircle size={20} style={{ color: 'var(--text-secondary)', flexShrink: 0 }} />
        <p style={{ fontSize: '0.82rem', color: 'var(--text-secondary)', lineHeight: 1.5 }}>
          <strong>Tip:</strong> Always record tasks immediately after accepting them. Doing this stops the platforms from modifying details later without your knowledge.
        </p>
      </div>
    </div>
  );
}
