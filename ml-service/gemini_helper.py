import os
import re
import json
import urllib.request

# Configure API Keys
GROQ_API_KEY = os.getenv("GROQ_API_KEY")

# Predefined translations for mock updates to support Tamil & Hindi out-of-the-box
MOCK_TRANSLATIONS = {
    "ta": {
        "The Ministry of Labour has opened registration on the e-Shram portal for gig and platform workers. Registered workers get access to accidental insurance up to Rs 2 Lakh and general welfare support.":
            "மத்திய தொழிலாளர் அமைச்சகம் ஜிக் மற்றும் பிளாட்பார்ம் ஊழியர்களுக்காக இ-ஷ்ரம் போர்ட்டலில் பதிவைத் தொடங்கியுள்ளது. பதிவுசெய்யப்பட்ட ஊழியர்களுக்கு ரூ. 2 லட்சம் வரையிலான விபத்துக் காப்பீடு மற்றும் பொதுவான நல உதவி கிடைக்கும்.",
        "The government is drafting rules to make app aggregators pay 1-2% of their turnover into a welfare fund for gig workers. This fund will support health insurance and pension plans.":
            "ஜிக் ஊழியர்களுக்கான நல நிதியில் ஆப் அக்ரிகேட்டர்கள் தங்களது வருவாயில் 1-2% செலுத்த அரசு விதிகளை வகுத்து வருகிறது. இந்த நிதி மருத்துவக் காப்பீடு மற்றும் ஓய்வூதியத் திட்டங்களை ஆதரிக்கும்.",
        "A state cabinet has passed a Platform Workers Protection bill. This creates a welfare board, a register for all delivery partners, and guarantees grievance channels to contest arbitrary bans.":
            "மாநில அமைச்சரவை பிளாட்பார்ம் ஊழியர்கள் பாதுகாப்பு மசோதாவை நிறைவேற்றியுள்ளது. இது ஒரு நல வாரியம், அனைத்து டெலிவரி கூட்டாளர்களுக்கான பதிவேடு ஆகியவற்றை உருவாக்குகிறது, மேலும் தன்னிச்சையான கணக்கு தடைகளை எதிர்த்துப் போட்டியிட குறைதீர்க்கும் வழித்தடங்களை உறுதி செய்கிறது."
    },
    "hi": {
        "The Ministry of Labour has opened registration on the e-Shram portal for gig and platform workers. Registered workers get access to accidental insurance up to Rs 2 Lakh and general welfare support.":
            "श्रम मंत्रालय ने गिग और प्लेटफॉर्म श्रमिकों के लिए ई-श्रम पोर्टल पर पंजीकरण शुरू किया है। पंजीकृत श्रमिकों को 2 लाभ रुपये तक का दुर्घटना बीमा और सामान्य कल्याण सहायता मिलेगी।",
        "The government is drafting rules to make app aggregators pay 1-2% of their turnover into a welfare fund for gig workers. This fund will support health insurance and pension plans.":
            "सरकार गिग श्रमिकों के लिए कल्याण कोष में ऐप एग्रीगेटर्स को अपने टर्नओवर का 1-2% भुगतान करने के नियम तैयार कर रही है। यह कोष स्वास्थ्य बीमा और पेंशन योजनाओं का समर्थन करेगा।",
        "A state cabinet has passed a Platform Workers Protection bill. This creates a welfare board, a register for all delivery partners, and guarantees grievance channels to contest arbitrary bans.":
            "एक राज्य कैबिनेट ने प्लेटफॉर्म श्रमिक संरक्षण विधेयक पारित किया है। यह एक कल्याण बोर्ड, सभी डिलीवरी भागीदारों के लिए एक रजिस्टर बनाता है, और मनमाने ढंग से खाता प्रतिबंधों का विरोध करने के लिए शिकायत निवारण चैनलों की गारंटी देता है।"
    }
}

def call_groq_api(system_prompt: str, user_prompt: str, is_json: bool = False) -> str:
    if not GROQ_API_KEY:
        raise ValueError("Groq API key not set")
    
    payload = {
        "model": "llama-3.1-8b-instant", # active highly available model
        "messages": [
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_prompt}
        ],
        "temperature": 0.0
    }
    if is_json:
        payload["response_format"] = {"type": "json_object"}
        
    try:
        url = "https://api.groq.com/openai/v1/chat/completions"
        req = urllib.request.Request(url, data=json.dumps(payload).encode("utf-8"))
        req.add_header("Authorization", f"Bearer {GROQ_API_KEY}")
        req.add_header("Content-Type", "application/json")
        req.add_header("User-Agent", "Mozilla/5.0")
        
        with urllib.request.urlopen(req, timeout=12) as resp:
            res_data = json.loads(resp.read().decode("utf-8"))
        return res_data["choices"][0]["message"]["content"].strip()
    except Exception as e:
        print(f"Groq API completion request failed: {e}")
        raise e

def summarize_article(raw_content: str) -> dict:
    if not raw_content:
        return {"summary": "NOT_RELEVANT", "relevant": False}
        
    # Try live Groq summarization
    if GROQ_API_KEY:
        try:
            system_prompt = (
                "Summarize this government announcement in 2-3 plain, simple sentences for a gig/delivery worker. "
                "Do not add information not present in the source text. "
                "If it doesn't clearly relate to gig/delivery workers, respond with ONLY the word 'NOT_RELEVANT'."
            )
            response_text = call_groq_api(system_prompt, raw_content)
            if "NOT_RELEVANT" in response_text:
                return {"summary": "NOT_RELEVANT", "relevant": False}
            return {"summary": response_text, "relevant": True}
        except Exception:
            print("Groq summarization failed. Using rule-based fallback.")
            
    # Mock / Rule-based Fallback
    text_lower = raw_content.lower()
    
    if "e-shram" in text_lower or "onboard gig" in text_lower:
        return {
            "summary": "The Ministry of Labour has opened registration on the e-Shram portal for gig and platform workers. Registered workers get access to accidental insurance up to Rs 2 Lakh and general welfare support.",
            "relevant": True
        }
    if "welfare fund" in text_lower or "annual turnover" in text_lower:
        return {
            "summary": "The government is drafting rules to make app aggregators pay 1-2% of their turnover into a welfare fund for gig workers. This fund will support health insurance and pension plans.",
            "relevant": True
        }
    if "protection and welfare" in text_lower or "protection bill" in text_lower:
        return {
            "summary": "A state cabinet has passed a Platform Workers Protection bill. This creates a welfare board, a register for all delivery partners, and guarantees grievance channels to contest arbitrary bans.",
            "relevant": True
        }
        
    # Default slicing if it contains keywords but doesn't match mock
    sentences = [s.strip() for s in re.split(r'[.!?]', raw_content) if s.strip()]
    if len(sentences) >= 2:
        summary = ". ".join(sentences[:2]) + "."
        return {"summary": summary, "relevant": True}
        
    return {"summary": "NOT_RELEVANT", "relevant": False}

def parse_voice_intent(transcript: str) -> dict:
    if not transcript:
        return {"intent": "UNKNOWN_INTENT", "entities": {}}
        
    # Try live Groq parsing
    if GROQ_API_KEY:
        try:
            system_prompt = (
                "You are the intent classifier for GigVoice, a voice assistant for gig delivery workers.\n"
                "Classify the user's spoken command into one of these 5 intents:\n"
                "  - LOG_PAYOUT (extra amount)\n"
                "  - LOG_TASK (extract amount and distance if present)\n"
                "  - CHECK_TODAY_EARNINGS\n"
                "  - CHECK_FAIRNESS_SCORE\n"
                "  - READ_LATEST_POLICY\n"
                "If it does not match any of these, return UNKNOWN_INTENT. Do not guess.\n"
                "Extract entities where applicable (amount as number, distance as number).\n\n"
                "Return ONLY a clean JSON object. Example:\n"
                "{\"intent\": \"LOG_PAYOUT\", \"entities\": {\"amount\": 150.00}}"
            )
            response_text = call_groq_api(system_prompt, transcript, is_json=True)
            return json.loads(response_text)
        except Exception:
            print("Groq intent classification failed. Using regex fallback.")
            
    # Rule-based fallback
    t_lower = transcript.lower()
    
    # Extract numbers for entities
    numbers = [float(x) for x in re.findall(r'\d+(?:\.\d+)?', t_lower)]
    
    if "payout" in t_lower or "pay" in t_lower or "பணம்" in t_lower or "வாங்கிய" in t_lower:
        amount = numbers[0] if numbers else 150.0
        return {"intent": "LOG_PAYOUT", "entities": {"amount": amount}}
        
    if "task" in t_lower or "delivery" in t_lower or "பணி" in t_lower or "சேர்த்தேன்" in t_lower:
        amount = numbers[0] if len(numbers) > 0 else 100.0
        distance = numbers[1] if len(numbers) > 1 else 5.0
        return {"intent": "LOG_TASK", "entities": {"amount": amount, "distance": distance}}
        
    if "earn" in t_lower or "today" in t_lower or "சம்பாதித்தேன்" in t_lower or "இன்று" in t_lower:
        return {"intent": "CHECK_TODAY_EARNINGS", "entities": {}}
        
    if "fairness" in t_lower or "score" in t_lower or "நேர்மை" in t_lower or "மதிப்பெண்" in t_lower:
        return {"intent": "CHECK_FAIRNESS_SCORE", "entities": {}}
        
    if "policy" in t_lower or "news" in t_lower or "கொள்கை" in t_lower or "செய்தி" in t_lower:
        return {"intent": "READ_LATEST_POLICY", "entities": {}}
        
    return {"intent": "UNKNOWN_INTENT", "entities": {}}

def translate_text(text: str, lang_code: str) -> str:
    if not text or lang_code == "en":
        return text
        
    # Check mock translations first
    if lang_code in MOCK_TRANSLATIONS and text in MOCK_TRANSLATIONS[lang_code]:
        return MOCK_TRANSLATIONS[lang_code][text]
        
    # Try live Groq translation
    if GROQ_API_KEY:
        try:
            system_prompt = (
                f"Translate this text into standard {lang_code} (e.g. 'ta' for Tamil, 'hi' for Hindi).\n"
                "Translate it simply and naturally for a delivery worker. Return ONLY the translated text."
            )
            return call_groq_api(system_prompt, text)
        except Exception:
            print("Groq translation failed. Returning original or fallback.")
            
    return text

def classify_article(title: str, content: str) -> dict:
    if not GROQ_API_KEY:
        return {
            "category": "POLICY_BENEFITS",
            "urgency": "NORMAL",
            "reasoning": "Groq API key not set; fallback default applied."
        }
        
    try:
        system_prompt = (
            "You are an AI classifier for GigLedger, a platform for gig delivery workers in India.\n"
            "Analyze the news article title and content provided, and categorize it into exactly one of these categories:\n"
            "  - POLICY_BENEFITS: Government labor codes, welfare schemes, e-Shram registrations, or national/state-level social security updates for unorganized/gig workers.\n"
            "  - INSURANCE_WELFARE: Direct insurance policies, accident cover, medical schemes (like PMSBY, PMJAY) specifically targeting delivery partners or gig workers.\n"
            "  - SAFETY_ALERT: Delivery worker strikes, protests, severe weather/heatwave advisories, or safety notices.\n"
            "  - PLATFORM_SPECIFIC: Policies, fines, bans, pay structures, and wages directly associated with specific platforms (e.g. Swiggy, Zomato, Blinkit, Ola, Uber).\n"
            "  - NOISE: General policy, economy, politics, startup funding, stock market updates, or anything not directly related to gig/delivery workers' welfare or rates.\n\n"
            "Also determine the URGENCY level:\n"
            "  - URGENT: Critical alerts that require immediate worker awareness (e.g., immediate safety hazards, strikes, major sudden policy/rate changes, registration deadlines in next 48h).\n"
            "  - NORMAL: General news, rules in drafting stage, or standard long-term program registrations.\n\n"
            "Return ONLY a clean JSON object with exactly these fields:\n"
            "{\n"
            "  \"category\": \"POLICY_BENEFITS\" | \"INSURANCE_WELFARE\" | \"SAFETY_ALERT\" | \"PLATFORM_SPECIFIC\" | \"NOISE\",\n"
            "  \"urgency\": \"URGENT\" | \"NORMAL\",\n"
            "  \"reasoning\": \"A short 1-sentence explanation of why it was classified this way\"\n"
            "}"
        )
        user_prompt = f"Title: {title}\nContent: {content}"
        
        response_text = call_groq_api(system_prompt, user_prompt, is_json=True)
        return json.loads(response_text)
    except Exception as e:
        print(f"Groq classification failed: {e}")
        # Rule-based fallback
        text_lower = (title + " " + content).lower()
        if "strike" in text_lower or "protest" in text_lower or "heatwave" in text_lower:
            return {"category": "SAFETY_ALERT", "urgency": "URGENT", "reasoning": "Fallback: Keyword strike/protest/heatwave matched."}
        if "insurance" in text_lower or "accident cover" in text_lower:
            return {"category": "INSURANCE_WELFARE", "urgency": "NORMAL", "reasoning": "Fallback: Keyword insurance matched."}
        if any(p in text_lower for p in ["swiggy", "zomato", "blinkit", "ola", "uber"]):
            return {"category": "PLATFORM_SPECIFIC", "urgency": "NORMAL", "reasoning": "Fallback: Platform keyword matched."}
        return {"category": "POLICY_BENEFITS", "urgency": "NORMAL", "reasoning": "Fallback: Defaulting to Policy Benefits."}
