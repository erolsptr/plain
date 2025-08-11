from flask import Flask, request, jsonify
import time
import random
import requests
import google.generativeai as genai
import json

app = Flask(__name__)

# ÖNEMLİ GÜVENLİK UYARISI: Bu API anahtarını GitHub gibi halka açık yerlerde paylaşma.
# Gerçek bir uygulamada, bunu bir ortam değişkeni (environment variable) olarak ayarlamak en iyisidir.
GOOGLE_API_KEY = "dikkat"
genai.configure(api_key=GOOGLE_API_KEY)

JAVA_API_CALLBACK_URL = "http://localhost:8080/api/internal/ai-vote"

def get_ai_estimation(task_data):
    """
    Google Gemini modelini kullanarak görev için bir tahmin ve gerekçe üretir.
    Artık görevi teknik adımlarına ayırıp analiz ederek daha derin bir gerekçe sunar.
    """
    task_title = task_data.get('title', 'Başlık Yok')
    task_description = task_data.get('description', 'Açıklama Yok')
    card_set = task_data.get('cardSet', ['?'])
    task_history = task_data.get('taskHistory', [])

    # Duruma göre prompt'u dinamik olarak oluştur
    if task_history:
        history_context = "For context, here are some previously estimated tasks by this team:\n"
        for i, task in enumerate(task_history[:5]): # En son 5 görevi referans alalım
            history_context += f"- Title: '{task.get('title')}', Consensus Score: {task.get('consensusScore')}\n"
        
        prompt = (
            "You are an expert software developer named 'plAIn' in a planning poker session. "
            "Your task is to provide an estimate for the new user story below. Follow these steps:\n"
            "1. **Break Down:** Deconstruct the new story into its core technical tasks (e.g., 'create API endpoint', 'update UI component', 'write database migration').\n"
            "2. **Analyze Complexity:** Briefly assess the complexity, risks, or unknowns for each technical task.\n"
            "3. **Estimate:** Based on your analysis and the team's past estimations provided below, choose the most suitable story point from the available options.\n"
            "4. **Justify:** Your reasoning should be a concise summary of your analysis.\n\n"
            f"**Team's Past Estimations (for reference):**\n{history_context}\n"
            f"**New User Story to Estimate:**\n"
            f"- **Title:** {task_title}\n"
            f"- **Description:** {task_description}\n\n"
            f"**Available Story Points:** {', '.join(map(str, card_set))}\n\n"
            "You MUST respond with ONLY a valid JSON object with two keys: 'vote' and 'reasoning'."
        )
    else:
        prompt = (
            "You are an expert software developer named 'plAIn' in a planning poker session. "
            "This is the very first task for the team. Your task is to provide an estimate for the user story below. Follow these steps:\n"
            "1. **Break Down:** Deconstruct the story into its core technical tasks (e.g., 'create API endpoint', 'update UI component', 'write database migration').\n"
            "2. **Analyze Complexity:** Briefly assess the complexity, risks, or unknowns for each technical task.\n"
            "3. **Estimate:** Based on your analysis, choose the most suitable story point from the available options.\n"
            "4. **Justify:** Your reasoning should be a concise summary of your analysis. Do not refer to any past data.\n\n"
            f"**User Story to Estimate:**\n"
            f"- **Title:** {task_title}\n"
            f"- **Description:** {task_description}\n\n"
            f"**Available Story Points:** {', '.join(map(str, card_set))}\n\n"
            "You MUST respond with ONLY a valid JSON object with two keys: 'vote' and 'reasoning'."
        )

    print("--- AI Beyni: Google Gemini'a son derece detaylı bir istek gönderiliyor... ---")
    print(f"Geçmiş referansı: {len(task_history)} adet görev.")
    try:
        model = genai.GenerativeModel('gemini-1.5-flash-latest')
        response = model.generate_content(prompt)
        
        response_text = response.text
        print(f"--- Gemini'den gelen ham cevap: {response_text} ---")

        if "```json" in response_text:
            response_text = response_text.split("```json")[1].strip().rstrip("`")

        response_data = json.loads(response_text)
        ai_vote = str(response_data.get("vote"))
        ai_reasoning = response_data.get("reasoning", "No reasoning provided.")

        if ai_vote in card_set:
            print(f"==> AI Kararı: {ai_vote}")
            print(f"==> Gerekçe: {ai_reasoning}\n")
            return ai_vote, ai_reasoning
        else:
            print(f"!!! UYARI: Gemini geçersiz bir oy üretti ('{ai_vote}'). Rastgele bir oy seçiliyor.")
            return random.choice(card_set), "AI'ın ürettiği oy geçersiz olduğu için rastgele bir seçim yapıldı."

    except Exception as e:
        print(f"!!! HATA: Google Gemini ile iletişimde bir sorun oluştu: {e}. Rastgele bir oy seçiliyor.")
        return random.choice(card_set), "AI servisiyle iletişimde bir hata oluştuğu için rastgele bir seçim yapıldı."

@app.route('/estimate', methods=['POST'])
def estimate_task():
    task_data = request.get_json()
    if not task_data:
        return jsonify({"error": "Geçersiz istek. JSON verisi bulunamadı."}), 400
    
    room_id = task_data.get('roomId')
    ai_vote, ai_reasoning = get_ai_estimation(task_data)
    
    callback_payload = {
        "roomId": room_id,
        "voterName": "plAIn Asistanı",
        "voteValue": ai_vote,
        "reasoning": ai_reasoning
    }

    try:
        print(f"--> Java backend'e oy gönderiliyor: {JAVA_API_CALLBACK_URL}")
        requests.post(JAVA_API_CALLBACK_URL, json=callback_payload, timeout=5)
        print("--> Oy başarıyla gönderildi.")
    except requests.exceptions.RequestException as e:
        print(f"!!! HATA: Java backend'e oy gönderilemedi. Hata: {e}")
        
    return jsonify({"status": "AI estimation triggered successfully"}), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=True)