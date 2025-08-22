import os
import time
import json
import pandas as pd
import re
from dotenv import load_dotenv
from langchain_google_genai import ChatGoogleGenerativeAI
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage
from langchain_community.callbacks.manager import get_openai_callback

load_dotenv(override=True)

# --- DEĞERLENDİRİLECEK MODELLER ---
groq_api_key = os.getenv("GROQ_API_KEY")
google_api_key = os.getenv("GOOGLE_API_KEY")

MODELS_TO_TEST = {
    "Gemini 1.5 Flash": ChatGoogleGenerativeAI(
        model="gemini-1.5-flash",
        temperature=0.3,
        google_api_key=google_api_key
    ),
    "Llama 4 Scout (Groq)": ChatOpenAI(
        model_name="meta-llama/llama-4-scout-17b-16e-instruct",
        temperature=0.3,
        openai_api_key=groq_api_key,
        openai_api_base="https://api.groq.com/openai/v1"
    ),
    "Llama 3 70b (Groq)": ChatOpenAI(
        model_name="llama3-70b-8192",
        temperature=0.3,
        openai_api_key=groq_api_key,
        openai_api_base="https://api.groq.com/openai/v1"
    )
}

# --- YENİ VE GERÇEKÇİ TEST SENARYOLARI ---
TEST_SCENARIOS = {
    "Orta Kompleksite: Asenkron Raporlama": {
        "title": "Kullanıcı Aktivite Raporunu Asenkron Olarak E-posta İle Gönder",
        "description": "Yönetici panelinde yeni bir 'Rapor Gönder' butonu eklenecek. Bu butona tıklandığında, son 7 günün kullanıcı aktivite verileri veritabanından çekilmeli, bir CSV dosyası olarak oluşturulmalı ve bu dosya, isteği yapan yöneticinin e-posta adresine asenkron bir görev (background job) olarak gönderilmelidir. Bu süreç, ana uygulama akışını (request thread) bloke etmemelidir.",
        "card_set": ["3", "5", "8", "13", "21"]
    },
    "Yüksek Kompleksite: WebSocket Bildirim Sistemi": {
        "title": "Gerçek Zamanlı Yorum Bildirim Sistemi Kur",
        "description": "Bir kullanıcı, bir göreve yorum yaptığında, o görevle ilgili diğer tüm aktif kullanıcılara anında bir 'Yeni Yorum Var' bildirimi gösterilmelidir. Bu, WebSocket üzerinden sunucu-taraflı bir 'push' mekanizması gerektirir. Kullanıcıların hangi görevleri takip ettiğini ve anlık 'online' durumlarını yönetecek bir yapı kurulmalıdır. Birden çok sunucuya ölçeklenebilirlik (scalability) göz önünde bulundurulmalıdır.",
        "card_set": ["8", "13", "21", "34", "55"]
    }
}

def build_prompt(scenario):
    task_title = scenario["title"]
    task_description = scenario["description"]
    card_set = scenario["card_set"]
    return (
        "You are an expert software developer and agile coach. "
        "Analyze the following user story to identify its core technical components, potential risks, and overall complexity. Provide a story point estimate.\n\n"
        f"**User Story to Estimate:**\n"
        f"- Title: {task_title}\n"
        f"- Description: {task_description}\n\n"
        f"**Available Story Points:** {', '.join(card_set)}\n\n"
        "You MUST respond with ONLY a valid JSON object with `vote` and `reasoning` keys. "
        "Your reasoning must be a detailed, step-by-step analysis and must be in Turkish."
    )

def extract_json_from_string(text):
    """Bir metnin içindeki ilk geçerli JSON objesini bulur ve ayıklar."""
    match = re.search(r'\{.*\}', text, re.DOTALL)
    if match:
        try:
            json_str = match.group(0)
            return json.loads(json_str)
        except json.JSONDecodeError:
            return None
    return None

def run_evaluation():
    results = []
    for scenario_name, scenario_data in TEST_SCENARIOS.items():
        print(f"\n===== SENARYO DEĞERLENDİRMESİ: {scenario_name} =====\n")
        prompt = build_prompt(scenario_data)

        for model_name, model_instance in MODELS_TO_TEST.items():
            try:
                print(f"-> Model çalıştırılıyor: {model_name}...")
                start_time = time.time()

                total_tokens, prompt_tokens, completion_tokens = 0, 0, 0
                
                if isinstance(model_instance, ChatGoogleGenerativeAI):
                    response = model_instance.invoke([HumanMessage(content=prompt)])
                    # DEBUG ÇIKTISINA GÖRE GÜNCELLENEN DOĞRU YOL
                    token_info = response.response_metadata.get("usage_metadata", {})
                    total_tokens = token_info.get("total_tokens", 0)
                    prompt_tokens = token_info.get("prompt_tokens", 0)
                    completion_tokens = token_info.get("output_tokens", 0)
                else: # Groq/OpenAI uyumlu API'ler için
                    with get_openai_callback() as cb:
                        response = model_instance.invoke([HumanMessage(content=prompt)])
                        total_tokens = cb.total_tokens
                        prompt_tokens = cb.prompt_tokens
                        completion_tokens = cb.completion_tokens

                end_time = time.time()
                duration = end_time - start_time
                
                content = response.content
                # JSON AYIKLAMA MANTIĞINI TÜM MODELLER İÇİN KULLANALIM
                response_json = extract_json_from_string(content)

                if response_json:
                    vote = response_json.get("vote", "JSON Hatası")
                    reasoning = response_json.get("reasoning", "JSON Hatası")
                else:
                    vote = "Format Bozuk"
                    reasoning = content

                results.append({
                    "Senaryo": scenario_name, "Model": model_name, "Süre (sn)": f"{duration:.2f}",
                    "Toplam Token": total_tokens, "Girdi Token": prompt_tokens, "Çıktı Token": completion_tokens,
                    "Tahmin": vote, "Gerekçe (Özet)": str(reasoning)[:120].replace('\n', ' ') + "..."
                })
                print(f"  -> Değerlendirme tamamlandı.")

            except Exception as e:
                print(f"  -> HATA: {model_name} çalıştırılamadı. Sebep: {e}")
                results.append({ "Senaryo": scenario_name, "Model": model_name, "Süre (sn)": "HATA", "Tahmin": str(e) })

    df = pd.DataFrame(results)
    print("\n\n===== MODEL PERFORMANS RAPORU =====\n")
    pd.set_option('display.max_colwidth', 120)
    pd.set_option('display.width', 1000)
    print(df.to_string())

if __name__ == "__main__":
    if not os.getenv("GROQ_API_KEY"):
        print("UYARI: GROQ_API_KEY .env dosyasında bulunamadı. Llama modelleri atlanıyor.")
        MODELS_TO_TEST = {k: v for k, v in MODELS_TO_TEST.items() if "Llama" not in k}
    if not os.getenv("GOOGLE_API_KEY"):
        print("UYARI: GOOGLE_API_KEY .env dosyasında bulunamadı. Gemini modelleri atlanıyor.")
        MODELS_TO_TEST = {k: v for k, v in MODELS_TO_TEST.items() if "Gemini" not in k}
    run_evaluation()