
# Spring ShedLock ile Dağıtık Scheduler Örneği


Bu proje, Spring Boot ve ShedLock kullanarak birden fazla instance arasında görevlerin senkronize bir şekilde çalıştırılmasını sağlayan bir örnektir. ShedLock, zamanlanmış görevlerin yalnızca bir instance tarafından çalıştırılmasını garanti eder ve dağıtık sistemlerde çakışmaları engeller.



## Proje Yapısı

### SpringShedlockDistributedSchedulerApplication.java Sınıfı
Uygulama, Spring Boot ile başlatılır. ShedLock ve zamanlama mekanizması şu anotasyonlarla etkinleştirilmiştir:
- `@EnableSchedulerLock`: ShedLock’un kilit yönetimini sağlar.
- `@EnableScheduling`: Spring zamanlama özelliklerini etkinleştirir.


### Kilit Yapılandırması: ShedLockConfig.java
ShedLock, veritabanını kullanarak kilit mekanizmasını yönetir. Bu yapılandırma, ShedLock'un LockProvider aracılığıyla çalışması için PostgreSQL veritabanına bağlantıyı sağlar.

```java
@Configuration
public class ShedLockConfig {
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(dataSource);
    }
}
```


### Zamanlanmış Görev: ReportGenerationTask.java
Bu sınıf, ShedLock kullanılarak bir görev tanımlar. Görev her 15 saniyede bir tetiklenir ve yalnızca bir instance tarafından çalıştırılır.

* **PT10S:** "Period of Time 10 Seconds" anlamına gelir. Yani kilit en az 10 saniye boyunca tutulur.
Bu süre boyunca başka bir instance aynı görevi çalıştıramaz, hatta görev belirtilen süreden daha erken tamamlanmış olsa bile kilit bırakılmaz. Bu, gereksiz tekrarların önlenmesine yardımcı olur.
  * Neden Gerekli? Bu, kısa sürede çok sık tetiklenen görevlerin birbirine çakışmasını engellemek için kullanılır. Örneğin, görev 2 saniye içinde bitse bile, ShedLock kilidi 10 saniye boyunca tutar.
* **PT15S:** "Period of Time 15 Seconds" anlamına gelir. Yani kilit en fazla 15 saniye boyunca tutulur.
  * Neden Gerekli? Örneğin, görev beklenenden uzun sürerse veya bir hata meydana gelirse, kilidin serbest bırakılması önemlidir. Aksi takdirde sistem diğer instance'ların çalışmasını engelleyebilir.


```java
@Component
public class ReportGenerationTask {
    @Value("${spring.application.name}")
    private String instanceName;

    @Scheduled(cron = "*/15 * * * * *")
    @SchedulerLock(name = "ReportGenerationTask.generateDailyReport", lockAtLeastFor = "PT10S", lockAtMostFor = "PT15S")
    public void scheduledTask() {
        System.out.println("Report sent by: " + instanceName + " at " + LocalDateTime.now());
    }
}
```

### Yapılandırma Dosyaları
#### application.properties
Ana yapılandırma dosyasıdır. Veritabanı bağlantı ayarları ve ShedLock log seviyesi burada belirtilmiştir:

```properties
spring.application.name=demo

spring.datasource.url=jdbc:postgresql://localhost:5432/schedlockexample
spring.datasource.username=root
spring.datasource.password=password
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=update
spring.sql.init.mode=always
spring.sql.init.platform=postgres

logging.level.net.javacrumbs.shedlock=DEBUG
```

#### application-instance1.properties
İlk instance için özel ayarlar:

```properties
server.port=8080
spring.application.name=instance1
```


#### application-instance2.properties
İkinci instance için özel ayarlar:

```properties
server.port=8081
spring.application.name=instance2
```

#### docker-compose.yml
```yaml
version: '3.8'

services:
  postgres:
    image: postgres:latest
    container_name: postgres-db
    environment:
      POSTGRES_USER: root
      POSTGRES_PASSWORD: password
      POSTGRES_DB: schedlockexample
    ports:
      - "5432:5432"
    volumes:
      - postgres_data:/var/lib/postgresql/data
    networks:
      - postgres_network

volumes:
  postgres_data:

networks:
  postgres_network:
```

#### Veritabanı ve schema.sql

ShedLock kilitleri tutmak için bir tablo oluşturur. Tablo tanımı **resources/schema.sql** dosyasında belirtilmiştir:

```sql
CREATE TABLE IF NOT EXISTS shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_at TIMESTAMP WITH TIME ZONE NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

#### Sütunların Anlamı:
* **name:** Kilidin adı (ör. ReportGenerationTask.generateDailyReport).
* **lock_until:**  Kilidin geçerlilik süresi. Bu süre dolmadan başka bir instance kilidi alamaz.
* **locked_at:** Kilidin alındığı zaman.
* **locked_by:** Kilidi alan instance adı.


### Uygulamayı Çalıştırma
1. Proje Paketleme
   Maven ile proje paketlenir:
```bash
mvn clean package
```

2. Birinci Instance Çalıştırma
```bash
java -Dspring.profiles.active=instance1 -jar target/*.jar
```

3. İkinci Instance Çalıştırma
```bash
java -Dspring.profiles.active=instance2 -jar target/*.jar
```
### Log Çıktıları
Projeyi iki instance olarak ayağa kaldırdığımızda bir instance üzerinde task çalışırken diğer instance üzerinde çalışmamasını bekleriz.
Log çıktıları şöyle olacaktır:

#### Instance 1:
```plaintext
Locked 'ReportGenerationTask.generateDailyReport', lock will be held at most until 2024-11-20T09:21:30.005Z
Report sent by: instance1 at 2024-11-20T12:21:15.012596
```

Instance1 üzerinde bu çıktıyı görürken Instance2 üzerinde aşağıdaki çıktıyı görmeliyiz:
```plaintext
 Not executing 'ReportGenerationTask.generateDailyReport'. It's locked.
```

![Log Çıktıları](/log.png)

