# Analiza aplikacji Safe Messages

**Data aktualizacji:** 2026-09-22 (aktualizacja: tlo MMS, skalowanie ~300 KB, GIF/Glide, kontakty, insety, ikona)  
**Zakres:** moduł `app`, manifest, zasoby XML, kod Java, dokumentacja oraz walidacja Gradle.  
**Nazwa aplikacji:** Safe Messages.

## Podsumowanie

Repozytorium zostało przebudowane z lokalizatora FamilySonar w aplikację **Safe Messages**: natywnego klienta SMS/MMS w Javie i XML, który może pełnić rolę domyślnej aplikacji SMS. Jednocześnie zachowano kluczową funkcję bezpieczeństwa: automatyczną odpowiedź lokalizacją na dokładną komendę SMS `?loc?` od zaufanego numeru.

## Zaimplementowany zakres

Aktualizacja (wersja z tlem MMS/GIF/kontakty):

- Wysylanie SMS i MMS dziala w tle (Executor + Handler main), UI sie nie blokuje (brak ANR); bledy pokazuje lokalizowany Toast.
- Zalaczniki obrazkowe sa skalowane i kompresowane (najdluzszy bok <= 1024 px, JPEG ~300 KB); animowane GIF-y wysylane bez rekompresji gdy < ~300 KB, wieksze odrzucane z jasnym komunikatem.
- Animowane GIF-y odtwarzane przez Glide 4.16.0 (podglad zalacznika i dymki historii); Glide czyta tez content://mms.
- Realny builder PDU M-Send.req (`MmsPduBuilder`, kodowanie WSP/MMS, czesc SMIL + obraz) zapisywany do cache i udostepniany przez FileProvider do `SmsManager.sendMultimediaMessage`; status sent/failed aktualizowany przez PendingIntent, plik PDU usuwany.
- Rozpoznawanie nazw kontaktow przez `ContactsContract.PhoneLookup` (z cache, uprawnienie READ_CONTACTS) na liscie rozmow, w tytule rozmowy, w powiadomieniach i w inicjale awatara; fallback do sformatowanego numeru.
- Obsluga WindowInsets na wszystkich ekranach (naglowki pod status barem, composer/lista/FAB nad paskiem nawigacji), adaptacyjna ikona (mipmap-anydpi-v26) z warstwa monochrome i odswiezona paleta kolorow.
- Funkcja lokalizacji `?loc?` (SMS_DELIVER/SMS_RECEIVED -> LocationService) pozostaje dostępna przy wygaszonym ekranie. Usługa działa jako `START_STICKY` w trybie niskiego zużycia co 20 minut, zapisuje ostatnią lokalizację trwale i po żądaniu SMS wysyła najpierw ostatni pomiar, a następnie świeży pomiar, jeśli uda się go pobrać w ciągu 30 sekund.

- Nowy ekran główny `MainActivity` pokazuje listę rozmów na podstawie `Telephony.Sms` oraz najlepsze dostępne podsumowania MMS z systemowego providera.
- `ConversationActivity` pokazuje historię rozmowy, pole tekstowe i wysyła wiadomości przez `SmsManager.sendMultipartTextMessage`.
- Composer udostępnia klawiaturę emoji, wybór obrazu/GIF-a oraz podgląd załącznika przed wysłaniem; historia MMS odczytuje i pokazuje obrazowe części wiadomości.
- Statusy wysłania i dostarczenia obsługuje `MessageStatusReceiver` przez `PendingIntent`; rekord w providerze SMS jest aktualizowany do stanu wysłany, nieudany albo dostarczony.
- `NotificationHelper` wystawia powiadomienia `MessagingStyle` dla przychodzących SMS/MMS.
- Manifest zawiera osobne komponenty wymagane dla roli SMS: `SMS_DELIVER`, fallback `SMS_RECEIVED`, `WAP_PUSH_DELIVER`, `SENDTO` oraz `RESPOND_VIA_MESSAGE`.
- Stary ekran konfiguracji przeniesiono do `SettingsActivity`; nadal obsługuje zaufane kontakty, SOS, ręczne żądanie lokalizacji, ustawienia baterii i uprawnienia lokalizacji.
- `SMSBroadcastReceiver` nadal rozpoznaje multipart SMS, sprawdza dokładną komendę `?loc?`, dopasowuje numer przez `PhoneNumberUtils` i uruchamia `LocationService`.
- `DefaultSmsReceiver` zapisuje odebrane SMS-y do `Telephony.Sms.Inbox`; `MmsBroadcastReceiver` pobiera MMS przez `SmsManager.downloadMultimediaMessage()` do systemowego providera i odświeża historię po zakończeniu pobierania.
- Komenda `?status?` odsyła zaufanemu kontaktowi operatora, poziom baterii i stan ładowania. Gdy `?loc?` nie może działać z powodu braku lokalizacji w tle, aplikacja odsyła jasną informację o konieczności wyboru „Zawsze zezwalaj”.
- Użytkownik może ustawić awaryjne hasło lokalizacji. Dokładna komenda `?loc?hasło` jest wtedy akceptowana z dowolnego numeru, a odpowiedź trafia na numer nadawcy. Przechowywany jest wyłącznie skrót SHA-256 hasła; zwykłe `?loc?` nadal wymaga zaufanego kontaktu.
- Lista rozmów obsługuje archiwizację gestem; w widoku archiwum ten sam gest uruchamia potwierdzone trwałe usunięcie wiadomości.
- Dodano ikonę wektorową Safe Messages łączącą tarczę i dymek wiadomości.

## SMS/MMS/RCS

Uwaga (aktualizacja): MMS buduje i wysyla realny binarny PDU M-Send.req, ale dostarczenie przez operatora nadal zalezy od APN/provisioningu MMS i dostepnej transmisji danych; przy braku laczności z MMSC wiadomosc jest oznaczana jako nieudana.

SMS jest pełnym, zaimplementowanym transportem tekstowym. MMS jest obsługiwany ostrożnie: aplikacja odbiera `WAP_PUSH_DELIVER`, czyta dostępne podsumowania z providera MMS i wyświetla jawny komunikat o ograniczeniach wysyłki. Pełna, niezależna wysyłka MMS z załącznikami zależy od APN, operatora i transportu, których Android nie udostępnia jednolicie każdej aplikacji.

RCS pozostaje niedostępne: Android nie udostępnia publicznego API dla niezależnego klienta RCS. Projekt nie używa prywatnych API, reverse engineeringu ani vendorowych obejść; UI informuje o niedostępności RCS i fallbacku do SMS/MMS.

## Uprawnienia i bezpieczeństwo

Aplikacja prosi o uprawnienia SMS/MMS po zaakceptowaniu roli domyślnego SMS. Uprawnienia lokalizacji i lokalizacji w tle pozostają związane z konfiguracją funkcji `?loc?` oraz SOS. Nie dodano szerokiego `READ_CONTACTS`; wybór numeru działa przez systemowy picker. Usunięto niepotrzebne `WAKE_LOCK` z manifestu.

Ryzyka pozostają typowe dla SMS i lokalizacji: brak kryptograficznego uwierzytelnienia nadawcy, wrażliwe dane w SMS, zależność od operatora i urządzenia oraz lokalny format konfiguracji oparty o Java Serialization.

## Główne klasy

| Klasa | Rola |
| --- | --- |
| `MainActivity` | lista rozmów, rola SMS, uprawnienia SMS, wejście do ustawień |
| `ConversationActivity` | widok wątku, historia, komponowanie i wysyłka SMS |
| `SmsRepository` | odczyt `Telephony.Sms`, zapis wiadomości wychodzących, wysyłka, statusy |
| `MmsSupport` | best-effort odczyt podsumowań MMS z providera |
| `NotificationHelper` | kanał i powiadomienia `MessagingStyle` |
| `SettingsActivity` | zachowane kontakty zaufane, SOS i konfiguracja lokalizacji |
| `LocationService` | foreground service lokalizacji i odpowiedzi na `?loc?` |
| `SMSBroadcastReceiver` / `DefaultSmsReceiver` | fallback SMS i domyślne `SMS_DELIVER` |
| `MmsBroadcastReceiver` | `WAP_PUSH_DELIVER` i jawny status MMS |
| `RespondViaMessageService` | szybka odpowiedź przez SMS |

## Walidacja

Wykonano pełną walidację:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\openjdk\jdk-21.0.8'
.\gradlew.bat testDebugUnitTest assembleDebug lintDebug --quiet
```

Walidacja przeszła. Lint zakończył się kodem 0 i zgłosił ostrzeżenia niefatalne dotyczące głównie dostępnych aktualizacji zależności, starych zasobów szablonowych i `notifyDataSetChanged`.

## Zużycie energii i działanie w tle

- `LocationService` pozostaje uruchomiony jako foreground service, aby utrzymywać świeży cache lokalizacji i reagować na `?loc?` przy wygaszonym ekranie.
- Zwykłe aktualizacje używają interwału 20 minut, minimalnego przemieszczenia 200 metrów i nie uruchamiają GPS. GPS jest używany tylko podczas szybkiego żądania.
- Geokodowanie adresu jest wykonywane przy żądaniu lokalizacji, a nie przy każdym okresowym pomiarze.
- Aplikacja nie prosi już o wyłączenie systemowych optymalizacji baterii.

## Pozostałe ograniczenia

- Funkcje SMS/MMS wymagają fizycznego urządzenia z usługą operatora oraz ustawienia aplikacji jako domyślnego SMS.
- Dostarczalność SMS i raporty doręczeń zależą od operatora.
- MMS i RCS mają ograniczenia platformowe opisane powyżej.
- Funkcja lokalizacji wymaga osobnej zgody na lokalizację, a dla odpowiedzi w tle również lokalizacji w tle.
