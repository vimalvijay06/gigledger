import { useState, useRef, useEffect } from 'react';
import { Mic, X, Volume2, Loader2, Sparkles, MessageSquare, AlertCircle } from 'lucide-react';
import api from '../api/client';

export default function VoiceAssistantModal({ isOpen, onClose }) {
  const [lang, setLang] = useState('en');
  const [micState, setMicState] = useState('idle'); // idle, listening, processing, speaking, error
  const [transcript, setTranscript] = useState('');
  const [response, setResponse] = useState('');
  const [audioUrl, setAudioUrl] = useState('');
  const [errorMessage, setErrorMessage] = useState('');
  const [permissionState, setPermissionState] = useState('NOT YET ASKED');
  const [volume, setVolume] = useState(0);

  const mediaRecorderRef = useRef(null);
  const audioChunksRef = useRef([]);
  const playbackRef = useRef(null);
  const audioContextRef = useRef(null);
  const analyserRef = useRef(null);
  const animationFrameRef = useRef(null);

  useEffect(() => {
    if (isOpen) {
      if (navigator.permissions && navigator.permissions.query) {
        navigator.permissions.query({ name: 'microphone' }).then((result) => {
          setPermissionState(result.state.toUpperCase());
          result.onchange = () => {
            setPermissionState(result.state.toUpperCase());
          };
        }).catch(() => {
          setPermissionState('UNKNOWN');
        });
      } else {
        setPermissionState('GRANTED_OR_PROMPT');
      }
    }
  }, [isOpen]);

  // Suggested Prompts based on selected language
  const suggestions = {
    en: [
      "log a task of 180 rupees and 6.5 kilometers",
      "log a payout of 120 rupees",
      "what are my earnings today?",
      "what is my fairness score?",
      "read the latest policy update"
    ],
    ta: [
      "180 ரூபாய் மற்றும் 6.5 கிமீ பணி சேர்க்கவும்",
      "120 ரூபாய் பணம் வாங்கினேன்",
      "இன்று நான் எவ்வளவு சம்பாதித்தேன்?",
      "எனது நேர்மை மதிப்பெண் என்ன?",
      "சமீபத்திய கொள்கை செய்தியைப் படிக்கவும்"
    ],
    hi: [
      "180 रुपये और 6.5 किमी का काम दर्ज करें",
      "120 रुपये का भुगतान दर्ज करें",
      "आज मैंने कितना कमाया?",
      "मेरा निष्पक्षता स्कोर क्या है?",
      "नवीनतम नीति अपडेट पढ़ें"
    ]
  };

  useEffect(() => {
    // Cleanup playback on close
    if (!isOpen) {
      stopAudio();
      setMicState('idle');
      setTranscript('');
      setResponse('');
      setAudioUrl('');
      setErrorMessage('');
    }
  }, [isOpen]);

  function stopAudio() {
    if (playbackRef.current) {
      playbackRef.current.pause();
      playbackRef.current = null;
    }
  }

  // Press-and-Hold Voice recording (WhatsApp voice note interaction pattern)
  async function handleStartRecording(e) {
    e.preventDefault();
    stopAudio();
    setTranscript('');
    setResponse('');
    setErrorMessage('');
    setMicState('listening');
    audioChunksRef.current = [];
    setVolume(0);

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      console.log("Recording started");
      
      // Setup Web Audio Analyser for volume level meter
      try {
        const audioContext = new (window.AudioContext || window.webkitAudioContext)();
        const source = audioContext.createMediaStreamSource(stream);
        const analyser = audioContext.createAnalyser();
        analyser.fftSize = 256;
        source.connect(analyser);
        
        audioContextRef.current = audioContext;
        analyserRef.current = analyser;
        
        const bufferLength = analyser.frequencyBinCount;
        const dataArray = new Uint8Array(bufferLength);
        
        const updateVolume = () => {
          if (analyserRef.current) {
            analyser.getByteFrequencyData(dataArray);
            let sum = 0;
            for (let i = 0; i < bufferLength; i++) {
              sum += dataArray[i];
            }
            const average = sum / bufferLength;
            setVolume(average);
            animationFrameRef.current = requestAnimationFrame(updateVolume);
          }
        };
        updateVolume();
      } catch (err) {
        console.warn("Could not start audio volume analyzer:", err);
      }

      // Use standard supported MIME types for browsers (webm, ogg, wav)
      const options = { mimeType: 'audio/webm' };
      let recorder;
      try {
        recorder = new MediaRecorder(stream, options);
      } catch (err) {
        recorder = new MediaRecorder(stream); // Fallback to default
      }

      mediaRecorderRef.current = recorder;

      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          audioChunksRef.current.push(event.data);
        }
      };

      recorder.onstop = async () => {
        // Cleanup volume analyser
        if (animationFrameRef.current) {
          cancelAnimationFrame(animationFrameRef.current);
          animationFrameRef.current = null;
        }
        if (audioContextRef.current) {
          audioContextRef.current.close().catch(() => {});
          audioContextRef.current = null;
        }
        analyserRef.current = null;
        setVolume(0);

        // Stop all mic tracks
        stream.getTracks().forEach(track => track.stop());
        
        const audioBlob = new Blob(audioChunksRef.current, { type: 'audio/wav' });
        console.log(`Recording stopped, size: ${(audioBlob.size / 1024).toFixed(2)} KB`);

        if (audioBlob.size < 2000) {
          setMicState('error');
          setErrorMessage('Recording was too short. Please try holding the button down longer.');
          return;
        }

        uploadAudioCommand(audioBlob);
      };

      recorder.start();
    } catch (err) {
      console.error(err);
      setMicState('error');
      setErrorMessage('Microphone access denied or not supported by this browser.');
    }
  }

  function handleStopRecording(e) {
    e.preventDefault();
    if (mediaRecorderRef.current && mediaRecorderRef.current.state === 'recording') {
      mediaRecorderRef.current.stop();
    }
  }

  async function uploadAudioCommand(audioBlob) {
    setMicState('processing');
    
    const formData = new FormData();
    // Save as voice_command.wav so python fallback regex transcription behaves correctly
    formData.append('file', audioBlob, 'voice_command.wav');

    try {
      const token = localStorage.getItem('gl_token');
      // POST command to Spring Boot API
      const res = await api.post(`/voice/command?lang=${lang}`, formData, {
        headers: { 'Content-Type': 'multipart/form-data' }
      });

      const { transcript, textResponse, actionStatus, audioUrl } = res.data;

      setTranscript(transcript || "(No transcript returned)");
      setResponse(textResponse);
      setMicState('speaking');

      if (audioUrl) {
        setAudioUrl(audioUrl);
        playSpeechResponse(audioUrl);
      } else {
        setMicState('idle');
      }
    } catch (err) {
      console.error(err);
      setMicState('error');
      setErrorMessage('Failed to connect to the voice assistant. Please check your network connection.');
    }
  }

  function playSpeechResponse(url) {
    stopAudio();
    const token = localStorage.getItem('gl_token');
    const fullUrl = `${api.defaults.baseURL}${url}`;
    
    fetch(fullUrl, {
      headers: { Authorization: `Bearer ${token}` }
    })
      .then(res => {
        if (!res.ok) throw new Error('Audio download failed');
        return res.blob();
      })
      .then(blob => {
        const objectUrl = URL.createObjectURL(blob);
        const player = new Audio(objectUrl);
        playbackRef.current = player;
        
        player.onended = () => {
          setMicState('idle');
        };
        player.onerror = () => {
          setMicState('idle');
        };
        player.play().catch(err => {
          console.error(err);
          setMicState('idle');
        });
      })
      .catch(err => {
        console.error("Audio playback error:", err);
        setMicState('idle');
      });
  }

  if (!isOpen) return null;

  return (
    <div style={{
      position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
      background: 'rgba(0,0,0,0.8)', backdropFilter: 'blur(8px)',
      display: 'flex', alignItems: 'flex-end', justifyContent: 'center',
      zIndex: 9999, transition: 'opacity 0.3s'
    }}>
      {/* Slide up card */}
      <div style={{
        width: '100%', maxWidth: 480, background: 'var(--color-surface)',
        borderTopLeftRadius: 'var(--radius-xl)', borderTopRightRadius: 'var(--radius-xl)',
        padding: '1.5rem', display: 'flex', flexDirection: 'column',
        boxShadow: '0 -8px 24px rgba(0,0,0,0.25)', border: '1px solid var(--color-border)',
        maxHeight: '85vh', overflowY: 'auto'
      }}>
        {/* Header */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <Sparkles size={20} style={{ color: 'var(--color-accent)' }} />
            <span style={{ fontWeight: 700, fontSize: '1.1rem', color: 'var(--text-primary)' }}>GigVoice Assistant</span>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            {/* Lang Dropdown */}
            <select
              value={lang}
              onChange={(e) => setLang(e.target.value)}
              style={{
                background: 'var(--color-surface-2)', border: '1px solid var(--color-border)',
                color: 'var(--text-primary)', borderRadius: 'var(--radius-md)',
                padding: '4px 8px', fontSize: '0.8rem', outline: 'none'
              }}
            >
              <option value="en">English</option>
              <option value="ta">தமிழ் (Tamil)</option>
              <option value="hi">हिन्दी (Hindi)</option>
            </select>

            <button onClick={onClose} style={{ background: 'none', border: 'none', color: 'var(--text-secondary)', cursor: 'pointer', display: 'flex' }}>
              <X size={20} />
            </button>
          </div>
        </div>

        {/* Response Feed Area */}
        <div style={{ flex: 1, minHeight: 180, display: 'flex', flexDirection: 'column', justifyContent: 'center', gap: '12px', marginBottom: '1.5rem' }}>
          {errorMessage ? (
            <div style={{ display: 'flex', gap: '8px', padding: '1rem', background: 'rgba(239,68,68,0.1)', border: '1px solid rgba(239,68,68,0.25)', borderRadius: 'var(--radius-md)', color: '#ef4444' }}>
              <AlertCircle size={20} style={{ flexShrink: 0 }} />
              <div style={{ fontSize: '0.875rem' }}>{errorMessage}</div>
            </div>
          ) : !transcript && !response && micState === 'idle' ? (
            <div style={{ textAlign: 'center', color: 'var(--text-secondary)' }}>
              <MessageSquare size={36} style={{ margin: '0 auto 10px', color: 'var(--color-accent-dim)' }} />
              <p style={{ fontWeight: 600, fontSize: '0.9rem', color: 'var(--text-primary)' }}>How can I help you today?</p>
              <p style={{ fontSize: '0.8rem', marginTop: '4px' }}>Try holding the microphone button below and telling me to log tasks or payouts.</p>
            </div>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
              {transcript && (
                <div style={{ alignSelf: 'flex-end', background: 'var(--color-surface-2)', border: '1px solid var(--color-border)', borderRadius: 'var(--radius-lg)', padding: '0.75rem 1rem', maxWidth: '85%' }}>
                  <span style={{ fontSize: '0.75rem', display: 'block', color: 'var(--text-secondary)', marginBottom: '4px', textTransform: 'uppercase', fontWeight: 600 }}>Spoken Command</span>
                  <span style={{ color: 'var(--text-primary)', fontSize: '0.9rem' }}>{transcript}</span>
                </div>
              )}
              {response && (
                <div style={{ alignSelf: 'flex-start', background: 'rgba(255,176,0,0.08)', border: '1px solid rgba(255,176,0,0.2)', borderRadius: 'var(--radius-lg)', padding: '0.75rem 1rem', maxWidth: '85%' }}>
                  <span style={{ fontSize: '0.75rem', display: 'block', color: 'var(--color-accent)', marginBottom: '4px', textTransform: 'uppercase', fontWeight: 600 }}>Action Response</span>
                  <span style={{ color: 'var(--text-primary)', fontSize: '0.9rem' }}>{response}</span>
                  {micState === 'speaking' && (
                    <div style={{ display: 'flex', alignItems: 'center', gap: '6px', marginTop: '6px', color: 'var(--color-accent)', fontSize: '0.75rem' }}>
                      <Volume2 size={12} />
                      <span>Speaking...</span>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </div>

        {/* Suggestions Card */}
        {micState === 'idle' && (
          <div style={{ marginBottom: '1.25rem' }}>
            <span style={{ fontSize: '0.75rem', color: 'var(--text-secondary)', fontWeight: 600, display: 'block', marginBottom: '6px' }}>Suggested Commands:</span>
            <div style={{ display: 'flex', flexDirection: 'column', gap: '6px' }}>
              {suggestions[lang].map((s, idx) => (
                <button
                  key={idx}
                  onClick={() => setTranscript(s)}
                  style={{
                    background: 'var(--color-surface-2)', border: '1px solid var(--color-border-dim)',
                    borderRadius: 'var(--radius-md)', padding: '8px 12px', fontSize: '0.8rem',
                    color: 'var(--text-primary)', textAlign: 'left', cursor: 'pointer',
                    transition: 'border-color 0.2s'
                  }}
                >
                  "{s}"
                </button>
              ))}
            </div>
          </div>
        )}

        {/* Debug Diagnostic Panel */}
        <div style={{
          background: 'rgba(255, 255, 255, 0.03)',
          border: '1px dashed var(--color-border)',
          borderRadius: 'var(--radius-md)',
          padding: '10px',
          fontSize: '0.8rem',
          marginBottom: '1rem',
          color: 'var(--text-secondary)'
        }}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span>🎙️ Microphone Permission:</span>
            <strong style={{ color: permissionState === 'GRANTED' ? '#22c55e' : permissionState === 'DENIED' ? '#ef4444' : '#eab308' }}>
              {permissionState}
            </strong>
          </div>
          {micState === 'listening' && (
            <div style={{ marginTop: '8px' }}>
              <div style={{ marginBottom: '4px' }}>Live Audio Level Meter:</div>
              <div style={{ height: '8px', background: 'var(--color-surface-2)', borderRadius: '4px', overflow: 'hidden', border: '1px solid var(--color-border)' }}>
                <div style={{ height: '100%', width: `${Math.min(100, volume * 1.5)}%`, background: '#22c55e', transition: 'width 0.05s' }}></div>
              </div>
            </div>
          )}
        </div>

        {/* Mic Control Bar */}
        <div style={{ display: 'flex', flexDirection: 'column', alignItems: 'center', gap: '10px', marginTop: 'auto', borderTop: '1px solid var(--color-border-dim)', paddingTop: '1.25rem' }}>
          
          {/* Animated visual state descriptions */}
          {micState === 'listening' && (
            <span style={{ fontSize: '0.85rem', color: '#ef4444', fontWeight: 700, animation: 'pulse 1.5s infinite' }}>
              ● LISTENING (RELEASE TO SEND)
            </span>
          )}
          {micState === 'processing' && (
            <span style={{ fontSize: '0.85rem', color: 'var(--color-accent)', fontWeight: 700, display: 'flex', alignItems: 'center', gap: '6px' }}>
              <Loader2 size={14} className="spin" /> PROCESSING AUDIO COMMAND...
            </span>
          )}
          {micState === 'idle' && (
            <span style={{ fontSize: '0.8rem', color: 'var(--text-secondary)' }}>
              Press and hold the microphone button to speak
            </span>
          )}

          {/* Large Press-and-hold Trigger Mic */}
          <button
            onMouseDown={handleStartRecording}
            onMouseUp={handleStopRecording}
            onTouchStart={handleStartRecording}
            onTouchEnd={handleStopRecording}
            style={{
              width: 76, height: 76, borderRadius: '50%',
              background: micState === 'listening' ? '#ef4444' : 'var(--color-accent)',
              color: micState === 'listening' ? '#fff' : '#000',
              border: 'none', display: 'flex', alignItems: 'center', justifyContent: 'center',
              cursor: micState === 'processing' ? 'not-allowed' : 'pointer',
              boxShadow: micState === 'listening' ? '0 0 20px rgba(239,68,68,0.4)' : '0 4px 12px rgba(255,176,0,0.2)',
              transition: 'background 0.2s, transform 0.1s',
              transform: micState === 'listening' ? 'scale(1.15)' : 'scale(1)'
            }}
            disabled={micState === 'processing'}
            title="Press and hold to talk"
          >
            <Mic size={32} />
          </button>
        </div>
      </div>
    </div>
  );
}
