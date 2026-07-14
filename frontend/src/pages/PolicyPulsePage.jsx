import { useState, useEffect, useRef } from 'react';
import { Newspaper, Play, Pause, ExternalLink, Globe, Volume2, Loader2 } from 'lucide-react';
import api from '../api/client';

export default function PolicyPulsePage() {
  const [feed, setFeed] = useState([]);
  const [lang, setLang] = useState('en');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [selectedCategory, setSelectedCategory] = useState('ALL');
  
  // Audio state
  const [playingId, setPlayingId] = useState(null);
  const [audioLoading, setAudioLoading] = useState(false);
  const audioRef = useRef(null);

  const categories = [
    { id: 'ALL', label: 'All' },
    { id: 'POLICY_BENEFITS', label: 'Policy' },
    { id: 'INSURANCE_WELFARE', label: 'Insurance' },
    { id: 'SAFETY_ALERT', label: 'Safety Alerts' },
    { id: 'PLATFORM_SPECIFIC', label: 'Platform News' }
  ];

  useEffect(() => {
    fetchFeed();
    // Cleanup audio on unmount
    return () => {
      if (audioRef.current) {
        audioRef.current.pause();
      }
    };
  }, [lang]);

  async function fetchFeed() {
    setLoading(true);
    setError('');
    try {
      // API call to Spring Boot GET /policy/feed?lang=...
      const res = await api.get(`/policy/feed?lang=${lang}`);
      setFeed(res.data);
    } catch (err) {
      console.error(err);
      setError('Could not load policy updates. Showing offline cached items.');
      // Load fallback mock items in case of network issue
      setFeed([
        {
          id: 'mock-1',
          title: lang === 'ta' 
            ? 'மத்திய தொழிலாளர் அமைச்சகம் ஜிக் மற்றும் பிளாட்பார்ம் ஊழியர்களுக்காக இ-ஷ்ரம் போர்ட்டலில் பதிவைத் தொடங்கியுள்ளது.' 
            : lang === 'hi' 
            ? 'श्रम मंत्रालय ने गिग और प्लेटफॉर्म श्रमिकों के लिए ई-श्रम पोर्टल पर पंजीकरण शुरू किया है।' 
            : 'Ministry of Labour extends e-Shram registration benefits to platform workers',
          summary: lang === 'ta'
            ? 'மத்திய தொழிலாளர் அமைச்சகம் ஜிக் மற்றும் பிளாட்பார்ம் ஊழியர்களுக்காக இ-ஷ்ரம் போர்ட்டலில் பதிவைத் தொடங்கியுள்ளது. பதிவுசெய்யப்பட்ட ஊழியர்களுக்கு ரூ. 2 லட்சம் வரையிலான விபத்துக் காப்பீடு மற்றும் பொதுவான நல உதவி கிடைக்கும்.'
            : lang === 'hi'
            ? 'श्रम मंत्रालय ने गिग और प्लेटफॉर्म श्रमिकों के लिए ई-श्रम पोर्टल पर पंजीकरण शुरू किया है। पंजीकृत श्रमिकों को 2 लाख रुपये तक का दुर्घटना बीमा और सामान्य कल्याण सहायता मिलेगी।'
            : 'The Ministry of Labour has opened registration on the e-Shram portal for gig and platform workers. Registered workers get access to accidental insurance up to Rs 2 Lakh and general welfare support.',
          sourceUrl: 'https://pib.gov.in/PressReleasePage.aspx?PRID=1928341',
          publishedAt: new Date().toISOString()
        }
      ]);
    } finally {
      setLoading(false);
    }
  }

  function handlePlayAudio(id) {
    // If playing this same audio, toggle pause
    if (playingId === id) {
      if (audioRef.current.paused) {
        audioRef.current.play().catch(err => console.error(err));
      } else {
        audioRef.current.pause();
        setPlayingId(null);
      }
      return;
    }

    // Stop current audio if any
    if (audioRef.current) {
      audioRef.current.pause();
    }

    setPlayingId(id);
    setAudioLoading(true);

    // Build URL: http://localhost:8080/policy/{id}/narrate?lang={lang}
    const token = localStorage.getItem('gl_token');
    const audioUrl = `${api.defaults.baseURL}/policy/${id}/narrate?lang=${lang}`;
    
    // Create new Audio object
    const audio = new Audio(audioUrl);
    // Include authorization header since endpoint is secured
    audio.src = audioUrl;
    
    // Set custom headers via fetch + object URL if browser blocks native requests
    fetch(audioUrl, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(response => {
        if (!response.ok) throw new Error('Audio load failed');
        return response.blob();
      })
      .then(blob => {
        const objectUrl = URL.createObjectURL(blob);
        audioRef.current = new Audio(objectUrl);
        
        audioRef.current.oncanplaythrough = () => {
          setAudioLoading(false);
          audioRef.current.play().catch(err => {
            console.error(err);
            setPlayingId(null);
          });
        };

        audioRef.current.onended = () => {
          setPlayingId(null);
        };

        audioRef.current.onerror = () => {
          setAudioLoading(false);
          setError('Failed to stream audio file narration.');
          setPlayingId(null);
        };
      })
      .catch(err => {
        console.error(err);
        setAudioLoading(false);
        setPlayingId(null);
        setError('Failed to download audio track stream.');
      });
  }

  return (
    <div className="page animate-in">
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <div>
          <h1 className="page-title">Policy Pulse</h1>
          <p className="page-subtitle">Multilingual Policy & Welfare Aggregator</p>
        </div>
        
        {/* Language Selector Dropdown */}
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', background: 'var(--color-surface)', padding: '6px 12px', borderRadius: 'var(--radius-md)', border: '1px solid var(--color-border)' }}>
          <Globe size={16} style={{ color: 'var(--color-accent)' }} />
          <select 
            value={lang} 
            onChange={(e) => {
              if (audioRef.current) {
                audioRef.current.pause();
                setPlayingId(null);
              }
              setLang(e.target.value);
            }} 
            style={{ background: 'none', border: 'none', color: 'var(--text-primary)', fontSize: '0.875rem', outline: 'none', cursor: 'pointer' }}
          >
            <option value="en" style={{ background: 'var(--color-surface)' }}>English</option>
            <option value="ta" style={{ background: 'var(--color-surface)' }}>தமிழ் (Tamil)</option>
            <option value="hi" style={{ background: 'var(--color-surface)' }}>हिन्दी (Hindi)</option>
          </select>
        </div>
      </div>

      {error && (
        <div style={{ padding: '0.75rem 1rem', background: 'rgba(239, 68, 68, 0.1)', border: '1px solid rgba(239, 68, 68, 0.25)', borderRadius: 'var(--radius-md)', color: '#ef4444', fontSize: '0.875rem', marginBottom: '1.25rem' }}>
          {error}
        </div>
      )}

      {/* Developer Tier Indexing Delay Warning Note */}
      <div style={{
        padding: '0.75rem 1rem',
        background: 'rgba(249, 115, 22, 0.05)',
        border: '1px solid rgba(249, 115, 22, 0.2)',
        borderRadius: 'var(--radius-md)',
        color: 'var(--color-accent)',
        fontSize: '0.82rem',
        lineHeight: '1.4',
        marginBottom: '1.25rem',
        display: 'flex',
        alignItems: 'center',
        gap: '8px'
      }}>
        <span style={{ fontSize: '1.1rem' }}>⚠️</span>
        <span>Feeds are cached and updated periodically. Note: NewsAPI free tier features a 24-hour article indexing delay.</span>
      </div>

      {/* Category Filter Chips */}
      <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginBottom: '1.5rem' }}>
        {categories.map((cat) => (
          <button
            key={cat.id}
            onClick={() => setSelectedCategory(cat.id)}
            style={{
              padding: '6px 14px',
              borderRadius: '99px',
              border: selectedCategory === cat.id ? '1px solid var(--color-accent)' : '1px solid var(--color-border)',
              background: selectedCategory === cat.id ? 'var(--color-accent-dim)' : 'var(--color-surface)',
              color: selectedCategory === cat.id ? 'var(--color-accent)' : 'var(--text-secondary)',
              fontSize: '0.82rem',
              fontWeight: 600,
              cursor: 'pointer',
              transition: 'all 0.2s'
            }}
          >
            {cat.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', padding: '4rem 0' }}>
          <Loader2 size={36} className="spin" style={{ color: 'var(--color-accent)', marginBottom: '1rem' }} />
          <span style={{ color: 'var(--text-secondary)' }}>Ingesting latest government feeds...</span>
        </div>
      ) : feed.length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem 1rem', background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)' }}>
          <Newspaper size={48} style={{ color: 'var(--text-secondary)', marginBottom: '1rem' }} />
          <p style={{ fontWeight: 600, color: 'var(--text-primary)' }}>No policies found</p>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>We couldn't find any relevant policy news matches at the moment.</p>
        </div>
      ) : feed.filter(item => selectedCategory === 'ALL' || item.category === selectedCategory).length === 0 ? (
        <div style={{ textAlign: 'center', padding: '3rem 1rem', background: 'var(--color-surface)', borderRadius: 'var(--radius-lg)' }}>
          <Newspaper size={48} style={{ color: 'var(--text-secondary)', marginBottom: '1rem' }} />
          <p style={{ fontWeight: 600, color: 'var(--text-primary)' }}>No updates in this category</p>
          <p style={{ fontSize: '0.875rem', color: 'var(--text-secondary)' }}>We couldn't find any policy updates matching the selected category.</p>
        </div>
      ) : (
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          {feed
            .filter(item => selectedCategory === 'ALL' || item.category === selectedCategory)
            .map((item) => (
              <div 
                key={item.id} 
                style={{ 
                  background: 'var(--color-surface)', 
                  border: item.urgency === 'URGENT' 
                    ? '2px solid rgba(239, 68, 68, 0.8)' 
                    : '1px solid var(--color-border)', 
                  borderRadius: 'var(--radius-lg)', 
                  padding: '1.25rem',
                  boxShadow: item.urgency === 'URGENT'
                    ? '0 4px 12px rgba(239, 68, 68, 0.15)'
                    : 'var(--shadow-sm)',
                  position: 'relative'
                }}
              >
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', flexWrap: 'wrap', gap: '8px', marginBottom: '0.5rem' }}>
                  <span style={{
                    fontSize: '0.7rem',
                    fontWeight: 700,
                    textTransform: 'uppercase',
                    background: 'var(--color-surface-2)',
                    padding: '2px 8px',
                    borderRadius: '4px',
                    color: 'var(--text-secondary)'
                  }}>
                    {item.category ? item.category.replace('_', ' ') : 'POLICY'}
                  </span>
                  
                  {item.urgency === 'URGENT' && (
                    <span className="urgent-badge-flash" style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '4px',
                      background: 'rgba(239, 68, 68, 0.1)',
                      border: '1px solid #ef4444',
                      borderRadius: '4px',
                      color: '#ef4444',
                      padding: '2px 8px',
                      fontSize: '0.72rem',
                      fontWeight: 800,
                      letterSpacing: '0.5px'
                    }}>
                      ⚠️ URGENT
                    </span>
                  )}
                </div>

                <h2 style={{ fontSize: '1.1rem', fontWeight: 700, color: 'var(--text-primary)', marginBottom: '0.5rem', lineHeight: '1.4' }}>
                  {item.title}
                </h2>
                
                <p style={{ fontSize: '0.92rem', color: 'var(--text-secondary)', lineHeight: '1.5', marginBottom: '1.25rem' }}>
                  {item.summary}
                </p>

              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', borderTop: '1px solid var(--color-border-dim)', paddingTop: '0.875rem' }}>
                {/* Audio Play Button */}
                <button 
                  onClick={() => handlePlayAudio(item.id)}
                  disabled={audioLoading && playingId === item.id}
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '6px',
                    background: playingId === item.id ? 'var(--color-accent)' : 'rgba(255,176,0,0.1)',
                    color: playingId === item.id ? '#000' : 'var(--color-accent)',
                    border: 'none',
                    borderRadius: '99px',
                    padding: '6px 14px',
                    fontSize: '0.82rem',
                    fontWeight: 700,
                    cursor: 'pointer',
                    transition: 'all 0.2s'
                  }}
                >
                  {audioLoading && playingId === item.id ? (
                    <>
                      <Loader2 size={14} className="spin" />
                      <span>Loading...</span>
                    </>
                  ) : playingId === item.id ? (
                    <>
                      <Pause size={14} />
                      <span>Pause Audio</span>
                    </>
                  ) : (
                    <>
                      <Play size={14} />
                      <span>Read Aloud</span>
                    </>
                  )}
                </button>

                {/* External Link */}
                <a 
                  href={item.sourceUrl} 
                  target="_blank" 
                  rel="noopener noreferrer"
                  style={{
                    display: 'flex',
                    alignItems: 'center',
                    gap: '4px',
                    fontSize: '0.82rem',
                    color: 'var(--text-secondary)',
                    textDecoration: 'none',
                    fontWeight: 500
                  }}
                >
                  <span>Official Source</span>
                  <ExternalLink size={12} />
                </a>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
