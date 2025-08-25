from flask import Flask, request, jsonify
import os
import random
import re
import requests
import json
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI
from langchain_core.messages import HumanMessage
from langchain_google_genai import GoogleGenerativeAIEmbeddings
from pgvector.sqlalchemy import Vector
from sqlalchemy import create_engine, text, Column, BigInteger, String, Text, ForeignKey
from sqlalchemy.orm import sessionmaker, declarative_base

load_dotenv(override=True)

app = Flask(__name__)

GOOGLE_API_KEY = os.getenv("GOOGLE_API_KEY")
GROQ_API_KEY = os.getenv("GROQ_API_KEY")

if not GOOGLE_API_KEY:
    raise ValueError("HATA: GOOGLE_API_KEY .env dosyasında bulunamadı.")
if not GROQ_API_KEY:
    raise ValueError("HATA: GROQ_API_KEY .env dosyasında bulunamadı.")

ai_model = ChatOpenAI(
    model_name="meta-llama/llama-4-scout-17b-16e-instruct",
    temperature=0.3,
    openai_api_key=GROQ_API_KEY,
    openai_api_base="https://api.groq.com/openai/v1"
)

DB_USER = os.getenv("DB_USER")
DB_PASSWORD = os.getenv("DB_PASSWORD")
DB_HOST = os.getenv("DB_HOST")
DB_PORT = os.getenv("DB_PORT")
DB_NAME = os.getenv("DB_NAME")

if not all([DB_USER, DB_PASSWORD, DB_HOST, DB_PORT, DB_NAME]):
    raise ValueError("HATA: .env dosyasında veritabanı bağlantı bilgileri eksik.")

DATABASE_URL = f"postgresql+psycopg2://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"
engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

class Project(Base):
    __tablename__ = 'projects'
    id = Column(BigInteger, primary_key=True)

class CodeChunk(Base):
    __tablename__ = 'code_chunks'
    id = Column(BigInteger, primary_key=True)
    project_id = Column(BigInteger, ForeignKey('projects.id', ondelete="CASCADE"))
    file_path = Column(String(1024))
    chunk_content = Column(Text)
    embedding = Column(Vector(768))

JAVA_API_CALLBACK_URL = "http://localhost:8080/api/internal/ai-vote"
AI_PARTICIPANT_NAME = "plAIn Asistanı"

def extract_json_from_string(text):
    match = re.search(r'\{.*\}', text, re.DOTALL)
    if match:
        try:
            return json.loads(match.group(0))
        except json.JSONDecodeError:
            return None
    return None

def retrieve_relevant_code(query_text, project_id):
    if not query_text or not project_id:
        print("--- DEBUG: Proje ID'si sağlanmadığı için kod araması atlanıyor. ---")
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
            print(f"--- DEBUG: Proje ID {project_id} için veritabanında alakalı kod bulunamadı. ---")
            return "No relevant code snippets were found for this task.\n\n"

        context = "Context from project code:\n"
        print("--- DEBUG: Veritabanından aşağıdaki ilgili kodlar bulundu: ---")
        for i, (content, file_path) in enumerate(results):
            print(f"  -> Snippet #{i+1}: {file_path}")
            context += f"--- Relevant Code Snippet #{i+1} from `{file_path}` ---\n"
            context += f"```\n{content}\n```\n\n"
        print("---------------------------------------------------------")
        return context

    except Exception as e:
        print(f"HATA: Vektör aramasında bir sorun oluştu: {e}")
        return "Could not retrieve code snippets due to a database error.\n\n"
    finally:
        db_session.close()



def get_ai_estimation(task_data):
    task_title = task_data.get('title', 'Başlık Yok')
    task_description = task_data.get('description', 'Açıklama Yok')
    card_set = task_data.get('cardSet', ['?'])
    project_id = task_data.get('projectId')
    task_history = task_data.get('taskHistory', [])

    search_query = f"{task_title}\n{task_description}"
    code_context = retrieve_relevant_code(search_query, project_id)
    has_code_context = "No relevant code snippets" not in code_context and "Could not retrieve" not in code_context

    history_context = ""
    has_history_context = len(task_history) > 0

    persona = "You are 'plAIn', a Senior Software Architect. Your reasoning must be insightful, clear, and technically grounded. Respond ONLY with a valid JSON object with `vote` and `reasoning` keys. The reasoning MUST be in Turkish.\n\n"
    
    analysis_requirements = "Your `reasoning` text must include:\n- A breakdown of the core technical tasks.\n- An analysis of complexities and risks.\n"
    
    if has_code_context:
        analysis_requirements += "- A specific analysis based on the provided Code Context.\n"
    
    if has_history_context:
        analysis_requirements += "- A comparison to a relevant past task from the Past Estimations.\n"
        
    analysis_requirements += "- A final justification for your vote.\n\n"
    
    if has_history_context:
        history_context_text = "Past Estimations:\n"
        for task in task_history:
            history_context_text += f"- Title: '{task.get('title')}', Consensus Score: {task.get('consensusScore')}\n"
    else:
        history_context_text = "Past Estimations: None provided.\n"

    # Tüm parçaları birleştir
    prompt = (
        f"{persona}"
        f"--- ANALYSIS REQUIREMENTS ---\n{analysis_requirements}"
        f"--- CONTEXT ---\n{code_context}\n{history_context_text}\n"
        f"--- NEW TASK ---\n"
        f"Title: {task_title}\n"
        f"Description: {task_description}\n"
        f"Available Points: {', '.join(map(str, card_set))}"
    )

    print("\n" + "="*80)
    print("AI MODELİNE GÖNDERİLEN TAM PROMPT:")
    print(prompt)
    print("="*80 + "\n")
    
    try:
        response = ai_model.invoke([HumanMessage(content=prompt)])
        print(f"--- Llama 4'ten gelen ham cevap: {response.content} ---")

        response_json = extract_json_from_string(response.content)

        if not response_json:
            raise ValueError("Modelden geçerli bir JSON alınamadı. Ham cevap: " + response.content)

        ai_vote = str(response_json.get("vote"))
        if ai_vote == '0.5' and '½' in card_set:
            ai_vote = '½'
        
        ai_reasoning = response_json.get("reasoning", "No reasoning provided.")

        if ai_vote in card_set:
            print(f"==> AI Kararı: {ai_vote}")
            print(f"==> Gerekçe:\n{ai_reasoning}\n")
            return ai_vote, ai_reasoning
        else:
            print(f"!!! UYARI: Llama 4 geçersiz bir oy üretti ('{ai_vote}'). Geçerli oylar: {card_set}. Rastgele bir oy seçiliyor.")
            return random.choice(card_set), "AI'ın ürettiği oy geçersiz olduğu için rastgele bir seçim yapıldı."

    except Exception as e:
        print(f"!!! HATA: AI servisiyle iletişimde bir sorun oluştu: {e}")
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
        requests.post(JAVA_API_CALLBACK_URL, json=callback_payload, timeout=10)
        print("--> Oy başarıyla gönderildi.")
    except requests.exceptions.RequestException as e:
        print(f"!!! HATA: Java backend'e oy gönderilemedi. Hata: {e}")
        
    return jsonify({"status": "AI estimation triggered successfully"}), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5001, debug=True)