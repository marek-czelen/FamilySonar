# FindMe — profil kolorystyczny i design

Aplikacja lokalizacyjno-ratunkowa (SOS + udostępnianie lokalizacji przez SMS).
Charakter interfejsu: **nowoczesny, jasny, lekki, intuicyjny**.

## Marka
- **Logo:** pomarańczowa pinezka lokalizacji z niebieskim punktem środkowym.
- **Ikona aplikacji:** pinezka na niebieskim tle z białym sygnałem „sonaru" (koncentryczne pierścienie).

## Paleta — motyw jasny
| Rola | HEX | Zastosowanie |
|------|-----|--------------|
| Primary (blue) | `#2D6CDF` | główne akcje, przyciski, dymki wychodzące, wskaźniki |
| On primary | `#FFFFFF` | tekst/ikony na primary |
| Primary container | `#E4EDFF` | delikatne tła akcji, banery info |
| On primary container | `#0F3D8C` | tekst na primary container |
| Secondary (orange) | `#F97316` | akcent marki, logo, FAB „dodaj kontakt" |
| Secondary container | `#FFE7D2` | podświetlenia akcentu |
| Background | `#F4F6F9` | tło ekranów |
| Surface | `#FFFFFF` | karty, paski |
| On surface | `#1B2532` | tekst główny |
| Muted (gray) | `#64748B` | tekst pomocniczy, ikony nieaktywne |
| Outline | `#DBE2EA` | obramowania kart i pól |
| Surface variant | `#EEF2F7` | tła drugorzędne |
| Error / SOS | `#E5484D` | alarm, przycisk SOS |

## Paleta — motyw ciemny
Primary `#7FA6FF` · Secondary `#FFB27A` · Background `#0F1620` · Surface `#18212E` ·
On surface `#E7EDF5` · Muted `#93A1B5` · Outline `#2A3646` · Error `#FFB3B3`.

## Zasady projektowe
- **Niebieski** = zaufanie i lokalizacja → główne interakcje.
- **Pomarańcz** = energia i tożsamość marki → akcenty, logo, pinezka, FAB.
- **Szarości** = neutralne tła i typografia; zachowanie oddechu i lekkości.
- **Czerwony** wyłącznie dla funkcji SOS/awaryjnych (semantyka alarmu).
- Zaokrąglenia: karty 16–22 dp, przyciski 12–16 dp, pola 14 dp.
- Płaska elewacja: obramowanie (`outline`) zamiast cieni; czyste, lekkie karty.
- Kontrast tekstu zgodny z WCAG AA na tłach `surface`/`background`.
