# plAIn - Kod Analizi ve Raporlama Yapan AI Destekli Planlama Pokeri

![Proje Durumu: Gelişmiş Özellikler - Stabil](https://img.shields.io/badge/status-advanced%20features-success)
![Backend: Java & Spring Boot](https://img.shields.io/badge/backend-Spring%20Boot-green)
![Frontend: React](https://img.shields.io/badge/frontend-React-blue)
![AI: Llama 4 & RAG](https://img.shields.io/badge/ai-Llama%204%20(RAG)-purple)
![Veritabanı: PostgreSQL & pgvector](https://img.shields.io/badge/database-PostgreSQL%20%26%20pgvector-blue)

**plAIn**, yazılım geliştirme ekiplerinin görev karmaşıklığını tahmin etme sürecini, **doğrudan projenin kod tabanını analiz eden** ve **detaylı raporlar sunan** bir yapay zeka ile bir üst seviyeye taşıyan, web tabanlı bir planlama pokeri platformudur. Projenin en benzersiz özelliği, oylama oturumlarına katılan ve tahminlerini, o görevle ilgili gerçek kod parçacıklarına dayandıran bir yapay zeka ajanıdır.

## ✨ Temel Özellikler

-   **Güvenli Kullanıcı Yönetimi:** Kayıt, giriş ve JWT tabanlı oturum yönetimi.
-   **Kişiselleştirilebilir Profiller ve Temalar:** Kullanıcılar avatarlarını, isimlerini yönetebilir ve Açık/Koyu tema arasında geçiş yapabilir.
-   **Oda Yönetim Paneli:** Kullanıcıların katıldıkları odaları listeleyebildiği ve yeni odalar oluşturabildiği kişisel kontrol paneli.
-   **Gerçek Zamanlı Oylama Odası:** WebSocket tabanlı, anlık etkileşime olanak tanıyan dinamik oylama ortamı. Oylama süresini gösteren bir sayaç içerir.
-   **Gelişmiş Moderatör Yetkileri:** Oda sahipleri görev ekleyebilir, silebilir, oylamayı başlatabilir, iptal edebilir, atlayabilir, sonuçları kaydedebilir ve katılımcıları yönetebilir.

## 🚀 Gelişmiş ve Benzersiz Özellikler

-   **Kod Analizi Yapan AI Asistanı (RAG Mimarisi):**
    -   Kullanıcılar, kendi GitHub projelerini sisteme tanıtabilir ve tek tıkla kod tabanının **indekslenmesini** (vektör fihristi oluşturulmasını) sağlayabilir.
    -   Oylama sırasında, seçilen proje referans alınır. AI, görev tanımına **anlamsal olarak en yakın kod parçacıklarını** veritabanından bulur.
    -   AI, tahminini ve gerekçesini bu **gerçek kod parçacıklarına** ve **takımın geçmiş oylama alışkanlıklarına** dayandırarak, son derece isabetli ve teknik olarak derin analizler sunar.

-   **Tam Jira Entegrasyonu:**
    -   Kullanıcılar, profil ayarlarından Jira bilgilerini (URL, API Token, Proje Anahtarı, Puan/Saat oranı) güvenli bir şekilde kaydedebilir.
    -   Tamamlanan görevler, **tek tek veya toplu olarak**, oylama sonucu (`Story Points`) ve hesaplanan saatlik tahmin (`Original Estimate`) ile birlikte doğrudan kullanıcının Jira projesine bir görev olarak gönderilir.

-   **Detaylı Raporlama Paneli (YENİ):**
    -   Kullanıcılar, sahip oldukları tüm odalardaki oylama geçmişini analiz eden özel bir raporlama sayfasına erişebilir.
    -   Bu panel, toplam oda sayısı, toplam oylanan görev ve tüm odalardaki toplam story puanı gibi genel özetler sunar.
    -   Oda bazında, her bir görevin karar oyunu ve oylamaya katılan tüm üyelerin (AI dahil) verdiği bireysel oylar detaylı bir tabloda gösterilir.

## 🛠️ Teknoloji Yığını ve Mimari

Proje, her iş için en uygun teknolojiyi kullanma prensibiyle, birbirinden izole üç ana bileşenden oluşur:

-   **Backend (Java):**
    -   Java 17, Spring Boot 3.x
    -   Spring Web, WebSocket, Security, Data JPA
    -   **PostgreSQL** ve **pgvector** eklentisi (Vektör veritabanı olarak)
    -   JWT ile Güvenlik

-   **Frontend (React):**
    -   React.js, React Router
    -   `@stomp/stompjs` ve `sockjs-client` ile WebSocket bağlantısı

-   **AI Sunucusu (Python):**
    -   Python 3.x, Flask
    -   **LangChain:** Karmaşık AI iş akışlarını (kod parçalama, embedding) yönetmek için.
    -   **Groq & Llama 4:** Ultra hızlı ve akıllı dil anlama ve cevap üretme için.
    -   **Google Gemini Embedding:** Kodun anlamsal vektörlerini oluşturmak için.
    -   **GitPython & SQLAlchemy:** GitHub repolarını yönetmek ve veritabanı ile iletişim kurmak için.

### Kritik Mimari Kararlar
-   **"İki Beyin" Mimarisi:** Ana uygulama (Java) ile AI mantığı (Python) tamamen izole iki ayrı sunucudur.
-   **Retrieval-Augmented Generation (RAG):** AI'ın, tahmin yapmadan önce ilgili bilgiyi (kod parçacıklarını) bir dış kaynaktan (pgvector veritabanı) alarak prompt'unu zenginleştirmesi.
-   **Asenkron İşlemler:** Kod indeksleme gibi uzun süren işlemler, kullanıcının arayüzünü kilitlememek için arka planda çalıştırılır.

## 🚀 Projeyi Yerel Makinede Çalıştırma

### Gereksinimler
-   Java JDK 17+
-   Node.js ve npm
-   Python 3.8+
-   **Docker ve Docker Compose** (PostgreSQL + pgvector için şiddetle tavsiye edilir)

### Kurulum

1.  **Veritabanı (Docker):**
    -   Proje ana dizininde bir `docker-compose.yml` dosyası oluşturun.
    -   İmaj olarak `pgvector/pgvector:pg16` veya üstünü kullanın ve şifre gibi ortam değişkenlerini ayarlayın.
    -   `docker-compose up -d` ile veritabanını başlatın.
    -   Veritabanına bağlanıp `CREATE EXTENSION vector;` komutunu çalıştırın.

2.  **AI Sunucusu (Python - `ai-server` klasörü):**
    -   Bir `.env` dosyası oluşturup `GOOGLE_API_KEY`, `GROQ_API_KEY` ve veritabanı bağlantı bilgilerinizi (`DB_USER`, `DB_PASSWORD` vb.) ekleyin.
    -   Bağımlılıkları kurun: `pip install -r requirements.txt`
    -   Sunucuyu başlatın: `python app.py`

3.  **Backend (Java - `plain` klasörü):**
    -   `plain/src/main/resources/application.properties` dosyasını, Docker'daki veritabanı ayarlarınızla güncelleyin.
    -   `./mvnw spring-boot:run` ile sunucuyu başlatın.

4.  **Frontend (React - `frontend` klasörü):**
    -   Bağımlılıkları kurun: `npm install`
    -   Uygulamayı başlatın: `npm start`

Uygulama artık `http://localhost:3000` adresinde çalışıyor olacaktır. İlk olarak bir kullanıcı kaydı oluşturun, ardından "Projelerim" sayfasından bir GitHub projesi ekleyip indeksleyerek başlayın.