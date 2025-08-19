from flask import Flask, request, jsonify
import os
import random
import requests
import json
from dotenv import load_dotenv
import google.generativeai as genai
from langchain_google_genai import GoogleGenerativeAIEmbeddings
from pgvector.sqlalchemy import Vector
from sqlalchemy import create_engine, text, Column, BigInteger, String, Text, ForeignKey
from sqlalchemy.orm import sessionmaker, declarative_base

# .env dosyasındaki değişkenleri yükle
load_dotenv()

app = Flask(__name__)

# --- Google AI Kurulumu ---
GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")
if not GOOGLE_API_KEY:
    raise ValueError("HATA: GOOGLE_API_KEY ortam değişkeni bulunamadı.")
genai.configure(api_key=GOOGLE_API_KEY)

# --- Veritabanı Kurulumu (GÜVENLİ VE DOĞRU VERSİYON) ---
DB_USER = os.getenv("DB_USER")
DB_PASSWORD = os.getenv("DB_PASSWORD")
DB_HOST = os.getenv("DB_HOST")
DB_PORT = os.getenv("DB_PORT")
DB_NAME = os.getenv("DB_NAME")

# Eğer herhangi bir bilgi eksikse, hata ver ve sunucuyu başlatma.
if not all([DB_USER, DB_PASSWORD, DB_HOST, DB_PORT, DB_NAME]):
    raise ValueError("HATA: .env dosyasında veritabanı bağlantı bilgileri eksik (DB_USER, DB_PASSWORD, DB_HOST, DB_PORT, DB_NAME).")

DATABASE_URL = f"postgresql+psycopg2://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"

# SQLAlchemy ve Veritabanı Kurulumu (DATABASE_URL'den SONRA gelmeli)
engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

# Veritabanı tablosunu temsil eden SQLAlchemy modeli
class CodeChunk(Base):
    __tablename__ = 'code_chunks'
    id = Column(BigInteger, primary_key=True)
    project_id = Column(BigInteger)
    file_path = Column(String)
    chunk_content = Column(Text)
    embedding = Column(Vector(768))

JAVA_API_CALLBACK_URL = "http://localhost:8080/api/internal/ai-vote"
AI_PARTICIPANT_NAME = "plAIn Asistanı"


def retrieve_relevant_code(query_text, project_id):
    """
    Verilen metne anlamsal olarak en yakın kod parçacıklarını veritabanından bulur.
    """
    if not query_text or project_id is None:
        return ""

    db_session = SessionLocal()
    try:
        embeddings_model = GoogleGenerativeAIEmbeddings(model="models/embedding-001", google_api_key=GOOGLE_API_KEY)
        query_embedding = embeddings_model.embed_query(query_text)

        sql_query = text("""
        SELECT chunk_content, file_path FROM code_chunks
        WHERE project_id = :pid
        ORDER BY embedding <-> CAST(:embedding AS vector)
        LIMIT 3
        """)
        
        results = db_session.execute(sql_query, {'pid': project_id, 'embedding': query_embedding}).fetchall()
        
        if not results:
            return "No relevant code snippets found in the project's indexed files."

        context = "For additional context, here are the most relevant code snippets from the project codebase:\n\n"
        for i, (content, file_path) in enumerate(results):
            context += f"--- Relevant Code Snippet #{i+1} from `{file_path}` ---\n"
            context += f"```\n{content}\n```\n\n"
        return context

    except Exception as e:
        print(f"HATA: Vektör aramasında bir sorun oluştu: {e}")
        return "Could not retrieve code snippets due to a database error."
    finally:
        db_session.close()


def get_ai_estimation(task_data):
    """
    Google Gemini modelini kullanarak görev için bir tahmin ve gerekçe üretir.
    Artık görevi, hem ilgili kod parçacıklarına hem de takımın geçmiş oylamalarına
    bakarak, hibrit bir yaklaşımla analiz eder.
    """
    task_title = task_data.get('title', 'Başlık Yok')
    task_description = task_data.get('description', 'Açıklama Yok')
    card_set = task_data.get('cardSet', ['?'])
    project_id = task_data.get('projectId')
    task_history = task_data.get('taskHistory', []) # Geçmiş oylamaları al

    # Adım 1: Görev tanımına göre ilgili kod parçacıklarını bul
    search_query = f"{task_title}\n{task_description}"
    code_context = retrieve_relevant_code(search_query, project_id)

    # Adım 2: Geçmiş oylama verisini prompt için formatla
    history_context = ""
    if task_history:
        history_context += "For team calibration, here are some of their recent estimations:\n"
        for task in task_history: # Artık Java'dan limitli geldiği için burada tekrar limitlemeye gerek yok
            history_context += f"- Title: '{task.get('title')}', Consensus Score: {task.get('consensusScore')}\n"
        history_context += "\n"


    # Adım 3: Yeni, hibrit prompt'u oluştur
    prompt = (
        "You are an expert software developer named 'plAIn'. Your task is to provide an estimate by synthesizing two types of information: the technical details from the code and the team's past estimation patterns.\n\n"
        f"{code_context}" # Bulunan kod parçacıklarını ekle
        f"{history_context}" # Takımın geçmiş oylamalarını ekle
        "Based on BOTH the provided code AND the team's past estimations, analyze the new user story below:\n"
        f"**New User Story to Estimate:**\n"
        f"- Title: {task_title}\n"
        f"- Description: {task_description}\n\n"
        f"**Available Story Points:** {', '.join(map(str, card_set))}\n\n"
        "**CRITICAL RULE:** Your final `vote` MUST be selected from the 'Available Story Points' list. To provide the most precise estimate, you are strongly encouraged to use fractional points (like '0.5', '1.5', '2.5') if they are available and the task's complexity falls between two whole numbers. Your ability to make nuanced, non-integer estimations is a key measure of your expertise.\n\n"
        "You MUST respond with ONLY a valid JSON object with `vote` and `reasoning` keys. Your reasoning should explain how both the code complexity and the team's past estimations influenced your final vote. **IMPORTANT: The `reasoning` field MUST be in Turkish.**"
    )

    print("--- AI Beyni: Hibrit (kod + geçmiş) prompt gönderiliyor... ---")
    try:
        model = genai.GenerativeModel('gemini-1.5-pro-latest')
        response = model.generate_content(prompt)
        
        response_text = response.text
        print(f"--- Gemini'den gelen ham cevap: {response_text} ---")

        if "```json" in response_text:
            response_text = response_text.split("```json")[1].strip().rstrip("`")

        response_data = json.loads(response_text)
        
        ai_vote = str(response_data.get("vote"))
        ai_reasoning = response_data.get("reasoning", "No reasoning provided.")
        if ai_vote == '0.5' and '½' in card_set:
            ai_vote = '½'

        if ai_vote in card_set:
            print(f"==> AI Kararı: {ai_vote}")
            print(f"==> Gerekçe: {ai_reasoning}\n")
            return ai_vote, ai_reasoning
        else:
            print(f"!!! UYARI: Gemini geçersiz bir oy üretti ('{ai_vote}'). Geçerli oylar: {card_set}. Rastgele bir oy seçiliyor.")
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
        "voterName": AI_PARTICIPANT_NAME,
        "voteValue": ai_vote,
        "reasoning": ai_reasoning
    }

    try:
        print(f"--> Java backend'e oy gönderiliyor: {JAVA_API_CALLBACK_URL}")
        requests.post(JAVA_API_CALLBACK_URL, json=callback_payload, timeout=10) # Timeout'u artıralım
        print("--> Oy başarıyla gönderildi.")
    except requests.exceptions.RequestException as e:
        print(f"!!! HATA: Java backend'e oy gönderilemedi. Hata: {e}")
        
    return jsonify({"status": "AI estimation triggered successfully"}), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=True)