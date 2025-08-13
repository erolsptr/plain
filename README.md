# plAIn - Yapay Zeka Destekli Planlama Pokeri

![Proje Durumu: Sürüm 1.0 - Stabil](https://img.shields.io/badge/status-version%201.0-brightgreen)
![Backend: Java & Spring Boot](https://img.shields.io/badge/backend-Spring%20Boot-green)
![Frontend: React](https://img.shields.io/badge/frontend-React-blue)
![AI: Python & Gemini](https://img.shields.io/badge/ai-Gemini%20Pro-purple)

**plAIn**, yazılım geliştirme ekiplerinin görev karmaşıklığını tahmin etmek için kullandığı planlama pokeri sürecini modernize eden, yapay zeka destekli, web tabanlı bir platformdur. Popüler araçlara bir alternatif olarak geliştirilen bu projenin en benzersiz özelliği, oylama oturumlarına insan gibi katılan ve gerekçeli tahminler sunan bir yapay zeka ajanıdır.

## ✨ Temel Özellikler

Proje, tüm ana özellikleri tamamlanmış, stabil ve son kullanıcıya sunulmaya hazır bir aşamadadır.

-   **Güvenli Kullanıcı Yönetimi:** Kayıt, giriş ve JWT (JSON Web Token) tabanlı oturum yönetimi. Kullanıcı bilgileri ve şifreler güvenli bir şekilde saklanır.
-   **Oda Yönetim Paneli:** Kullanıcıların katıldıkları odaları listeleyebildiği, yeni odalar oluşturabildiği ve sahip oldukları odaları silebildiği kişisel bir kontrol paneli.
-   **Gerçek Zamanlı Oylama Odası:** WebSockets kullanılarak inşa edilmiş, katılımcıların anlık olarak etkileşime girebildiği, aktif/pasif durumlarının ve oylarının senkronize olduğu dinamik bir oylama ortamı.
-   **Benzersiz AI Asistanı (plAIn Agent):**
    -   Oturumlara "plAIn Asistanı" adıyla katılan, Google Gemini tarafından güçlendirilmiş bir yapay zeka.
    -   Oylanacak görevin başlığını, açıklamasını ve **o odaya ait geçmiş oylamaları** analiz eder.
    -   Bu analize dayanarak kendi oyunu verir ve bu oyunun arkasındaki mantıksal gerekçeyi tüm kullanıcılara sunar.
-   **Moderatör Yetkileri:** Oda sahipleri (moderatörler) yeni görevler ekleyebilir, oylamayı başlatabilir, sonuçları gösterebilir, sonucu veritabanına kalıcı olarak kaydedebilir ve katılımcıları odadan atabilir.
-   **Kişiselleştirilebilir Kart Desteleri:** Fibonacci, Scrum, Saat bazlı ve en sık kullanılanları içeren "Favoriler" destesi gibi farklı oylama setleri arasından seçim yapma imkanı.
-   **Kullanıcı Profili Yönetimi:** Kullanıcılar görünen isimlerini, şifrelerini ve avatarlarını güvenli bir şekilde güncelleyebilirler.
-   **Modern ve Tematik Arayüz:** Oylama ve sonuç kartları için, projenin "poker" temasına uygun, şık ve modern bir iskambil kartı tasarımı.

## 🛠️ Teknoloji Yığını ve Mimari

Proje, her iş için en uygun teknolojiyi kullanma prensibiyle, birbirinden izole üç ana bileşenden oluşur:

-   **Backend (Java):**
    -   Java 17, Spring Boot 3.x
    -   Spring Web, WebSocket, Security, Data JPA
    -   PostgreSQL (Veritabanı)
    -   JWT ile Güvenlik

-   **Frontend (React):**
    -   React.js, React Router
    -   `@stomp/stompjs` ve `sockjs-client` ile WebSocket bağlantısı
    -   Modern CSS ile stil yönetimi

-   **AI Sunucusu (Python):**
    -   Python 3.x, Flask
    -   `google-generativeai` kütüphanesi ile Google Gemini Pro entegrasyonu

### Kritik Mimari Kararlar
-   **"İki Beyin" Mimarisi:** Ana uygulama (Java) ile AI mantığı (Python) birbirinden tamamen izole iki ayrı sunucudur ve birbirleriyle REST API üzerinden haberleşirler.
-   **Hibrit Hafıza Modeli:** Anlık oda verileri (aktif kullanıcılar, anlık oylar) hız için hafızada (In-Memory), kalıcı veriler (kullanıcılar, tamamlanmış görevler) ise PostgreSQL veritabanında tutulur.

## 🚀 Projeyi Yerel Makinede Çalıştırma

Projeyi kendi bilgisayarınızda çalıştırmak için aşağıdaki adımları izleyin.

### Gereksinimler
-   Java JDK 17+
-   Node.js ve npm
-   Python 3.8+
-   PostgreSQL veritabanı

### Kurulum

1.  **Backend (Java - `plain` klasörü):**
    -   `plain/src/main/resources/application.properties` dosyasını açın.
    -   `spring.datasource.url`, `username` ve `password` alanlarını kendi PostgreSQL ayarlarınızla güncelleyin.
    -   `jwt.secret` için rastgele bir anahtar belirleyin.
    -   Ana proje dizininde terminali açıp çalıştırın: `mvnw spring-boot:run`

2.  **AI Sunucusu (Python - `ai-server` klasörü):**
    -   `ai-server` klasöründe bir `.env` dosyası oluşturun.
    -   İçine `GOOGLE_API_KEY="Sizin_Google_API_Anahtarınız"` satırını ekleyin.
    -   Gerekli kütüphaneleri kurun: `pip install -r requirements.txt` (Eğer `requirements.txt` yoksa: `pip install Flask google-generativeai python-dotenv requests`)
    -   Sunucuyu başlatın: `python app.py`

3.  **Frontend (React - `frontend` klasörü):**
    -   `frontend` klasörüne gidin.
    -   Bağımlılıkları kurun: `npm install`
    -   Uygulamayı başlatın: `npm start`

Uygulama artık `http://localhost:3000` adresinde çalışıyor olacaktır.
