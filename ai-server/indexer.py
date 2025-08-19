import os
import shutil
import stat
import argparse
from git import Repo
from dotenv import load_dotenv
load_dotenv(override=True)
from langchain_community.document_loaders import TextLoader
from langchain.text_splitter import Language, RecursiveCharacterTextSplitter
from langchain_google_genai import GoogleGenerativeAIEmbeddings
from pgvector.sqlalchemy import Vector
from sqlalchemy import create_engine, text, Column, BigInteger, String, Text, ForeignKey
from sqlalchemy.orm import sessionmaker, declarative_base

CLONE_DIR = os.path.join(os.path.dirname(__file__), "temp_cloned_repos")
SUPPORTED_EXTENSIONS = {
    ".java": Language.JAVA,
    ".js": Language.JS,
    ".py": Language.PYTHON,
    ".ts": Language.TS,
    ".tsx": Language.TS, 
}
# Veritabanı bağlantı bilgilerini doğrudan .env'den al
DB_USER = os.getenv("DB_USER")
DB_PASSWORD = os.getenv("DB_PASSWORD")
DB_HOST = os.getenv("DB_HOST")
DB_PORT = os.getenv("DB_PORT")
DB_NAME = os.getenv("DB_NAME")

# Eğer herhangi bir bilgi eksikse, hata ver ve programı durdur.
if not all([DB_USER, DB_PASSWORD, DB_HOST, DB_PORT, DB_NAME]):
    raise ValueError("HATA: Veritabanı bağlantı bilgileri (.env içinde DB_USER, DB_PASSWORD, DB_HOST, DB_PORT, DB_NAME) eksik.")

DATABASE_URL = f"postgresql+psycopg2://{DB_USER}:{DB_PASSWORD}@{DB_HOST}:{DB_PORT}/{DB_NAME}"

# SQLAlchemy ve Veritabanı Kurulumu
engine = create_engine(DATABASE_URL)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

class Project(Base):
    __tablename__ = 'projects'
    id = Column(BigInteger, primary_key=True)

class CodeChunk(Base):
    __tablename__ = 'code_chunks'
    id = Column(BigInteger, primary_key=True, index=True)
    project_id = Column(BigInteger, ForeignKey('projects.id', ondelete="CASCADE"), nullable=False)
    file_path = Column(String(1024), nullable=False)
    chunk_content = Column(Text, nullable=False)
    embedding = Column(Vector(768))

# --- Yardımcı Fonksiyonlar ---

def handle_remove_readonly(func, path, exc_info):
    """Windows'ta .git klasörünü silerken oluşan 'readonly' hatalarını çözer."""
    os.chmod(path, stat.S_IWRITE)
    func(path)

def clone_repo(repo_url):
    """Verilen GitHub URL'sini yerel bir klasöre klonlar."""
    if os.path.exists(CLONE_DIR):
        print(f"'{CLONE_DIR}' klasörü temizleniyor...")
        shutil.rmtree(CLONE_DIR, onerror=handle_remove_readonly)
    
    print(f"'{repo_url}' reposu klonlanıyor...")
    try:
        Repo.clone_from(repo_url, CLONE_DIR)
        print("Klonlama başarılı.")
        return CLONE_DIR
    except Exception as e:
        print(f"HATA: Repo klonlanamadı. Hata: {e}")
        return None

def find_code_files(repo_path):
    """Klonlanmış repo içinde desteklenen uzantılara sahip dosyaları bulur."""
    code_files = []
    ignore_dirs = ["node_modules", ".git", "target", "build", ".vscode", "venv"]
    for root, dirs, files in os.walk(repo_path):
        # Gereksiz klasörleri atla
        dirs[:] = [d for d in dirs if d not in ignore_dirs]
        for file in files:
            if any(file.endswith(ext) for ext in SUPPORTED_EXTENSIONS):
                code_files.append(os.path.join(root, file))
    print(f"Toplam {len(code_files)} adet kod dosyası bulundu.")
    return code_files

def split_code_into_chunks(file_paths):
    """Bulunan kod dosyalarını LangChain kullanarak mantıksal parçalara ayırır."""
    all_chunks = []
    for file_path in file_paths:
        file_extension = os.path.splitext(file_path)[1]
        language = SUPPORTED_EXTENSIONS.get(file_extension)
        if not language: continue
        
        try:
            loader = TextLoader(file_path, encoding='utf-8')
            documents = loader.load()
            text_splitter = RecursiveCharacterTextSplitter.from_language(
                language=language, chunk_size=2000, chunk_overlap=200
            )
            chunks = text_splitter.split_documents(documents)
            all_chunks.extend(chunks)
        except Exception as e:
            print(f"UYARI: '{os.path.basename(file_path)}' dosyası işlenemedi. Hata: {e}")
            
    print(f"Toplam {len(all_chunks)} adet kod parçacığı (chunk) oluşturuldu.")
    return all_chunks

def get_google_embeddings():
    """Google'ın embedding modelini başlatır."""
    google_api_key = os.getenv("GOOGLE_API_KEY")
    if not google_api_key:
        raise ValueError("GOOGLE_API_KEY ortam değişkeni bulunamadı.")
    return GoogleGenerativeAIEmbeddings(model="models/embedding-001", google_api_key=google_api_key)

def embed_and_store_chunks(chunks, project_id):
    if not chunks:
        print("Kaydedilecek kod parçacığı bulunamadı.")
        return

    print("\n--- FAZ 3: EMBEDDING VE KAYIT ---")
    
    try:
        print("Adım 3.1: Google Embedding Modeli başlatılıyor...")
        embeddings_model = get_google_embeddings()
        print(">>> Başarılı: Embedding modeli yüklendi.")
        
        print("Adım 3.2: Veritabanı oturumu başlatılıyor...")
        db_session = SessionLocal()
        print(">>> Başarılı: Veritabanı oturumu açıldı.")
        
        print(f"Adım 3.3: Proje ID'si {project_id} için eski veriler temizleniyor...")
        db_session.execute(text("DELETE FROM code_chunks WHERE project_id = :pid"), {'pid': project_id})
        print(">>> Başarılı: Eski veriler silindi.")
        
        contents_to_embed = [chunk.page_content for chunk in chunks]
        
        print(f"Adım 3.4: {len(contents_to_embed)} adet kod parçacığı Google API'sine gönderiliyor (bu işlem zaman alabilir)...")
        embedding_vectors = embeddings_model.embed_documents(contents_to_embed)
        print(f">>> Başarılı: {len(embedding_vectors)} adet vektör alındı.")

        db_chunks = []
        for i, chunk in enumerate(chunks):
            file_path = chunk.metadata.get('source', 'unknown_file').replace(CLONE_DIR, '')
            db_chunk = CodeChunk(
                project_id=project_id,
                file_path=file_path,
                chunk_content=chunk.page_content,
                embedding=embedding_vectors[i]
            )
            db_chunks.append(db_chunk)
        
        print("Adım 3.5: Veriler veritabanına toplu olarak kaydediliyor...")
        db_session.bulk_save_objects(db_chunks)
        db_session.commit()
        print(">>> BAŞARILI: Tüm parçacıklar veritabanına kaydedildi!")
        
    except Exception as e:
        db_session.rollback()
        print(f"!!! HATA: Adım 3'te bir sorun oluştu: {e}")
    finally:
        db_session.close()

# --- Ana Çalıştırma Fonksiyonu ---
def main():
    """Betiğin ana çalışma mantığı."""
    parser = argparse.ArgumentParser(description="Bir GitHub reposunu indeksler ve veritabanına kaydeder.")
    parser.add_argument("repo_url", type=str, help="Klonlanacak GitHub reposunun URL'si.")
    parser.add_argument("project_id", type=int, help="Bu reponun veritabanındaki proje ID'si.")
    args = parser.parse_args()
    
    repo_path = clone_repo(args.repo_url)
    if not repo_path:
        return
        
    code_files = find_code_files(repo_path)
    if not code_files:
        return
        
    code_chunks = split_code_into_chunks(code_files)
    
    embed_and_store_chunks(code_chunks, args.project_id)

# Betiğin sadece doğrudan çalıştırıldığında main() fonksiyonunu çağırmasını sağlar.
if __name__ == "__main__":
    main()