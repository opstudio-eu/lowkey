# Ontwerpreview — Grok

Subjectief ontwerpordeel over het herziene concept. Dit is geen gebruikstest en geen oordeel over Compose, de IME of TalkBack.

## Bekeken

Gelezen: `AGENTS.md`, `README.md`, `REFERENCES.md` (een lokaal `AGENTS.local.md` ontbrak), `docs/design/lowkey-ux/voorstel.md`, `docs/design/lowkey-ux/reviews/README.md` en `docs/design/lowkey-ux/signal-sources.json`. Signal, alleen gelezen: `src/app/globals.css`, `src/components/Brand.tsx`, `src/components/overview/today.module.css`, `docs/branding/new-opstudio/README.md` en `implementation.md`. Ter controle in Lowkey: `companion/theme/Theme.kt`, `companion/screens/HomeScreen.kt` en `res/drawable/opstudio_logo_blue.xml`.

Beeld via html-preview van `artifacts/lowkey-ux/prototype.html` (Home, 390 px, licht) en scenario’s in dezelfde map: Home 320 licht en 390 donker; Instellingen 390 en 320 licht plus 390 donker; Stem 390 licht; Woordenboek 390 en 320 licht; onboarding 390 en 320 licht; Formaat en positie 390 en 320 licht; Home-herstel 320 licht. Eigen kopieën onder de gitignored artifactmap: Talen en indelingen, Dicteergedrag en spraakregels op 390 px, Voeg spraak toe op 320 px. Een meetkopie liep op 320 px alle routes in licht, een donkere set, en een zoom van 2×. De balk boven het toestelkader is demonstratiechrome.

Niet bekeken: emulator, fysiek toestel, TalkBack, de systeemkiezer, de keyboard-track, en de tekstbladen Updates, Over Lowkey, Klembord, Typen en Aanpassen als uitgewerkt scherm. De eenhandige zijdekeuze is in de draaiende pagina gemeten, niet als apart beeld. Donker voor Stem en Woordenboek is alleen op overloop gemeten. Zoom is geen Android-letterschaal.

## Scores

| Criterium | Score |
| --- | --- |
| Informatiearchitectuur | 7 |
| Gebruiksgemak | 6,5 |
| Visuele rust en hiërarchie | 8 |
| OpStudio/Signal-samenhang | 8 |
| Toegankelijkheid en adaptiviteit | 6,5 |
| **Gewogen totaal** | **7,2** |

Berekening: 7×0,25 + 6,5×0,25 + 8×0,20 + 8×0,20 + 6,5×0,10 = 7,2.

Het concept is een rustige, herkenbare companion. Home en de instellingenindex zijn bruikbaar. Een paar routes en tekstacties wijzen nog de verkeerde kant op.

De productregel LOWKEY is kleiner en wijder gespatieerd dan de huidige Signal-regel voor het product (13 px, gewicht 450, krappe spatiëring). Het beeldmerk zelf wijkt niet af.

## Opmerkingen

1. **Hoog — Home en Talen en indelingen.** De Home-rij heet Toetsenbord en opent Talen en indelingen. In Instellingen heet een andere rij ook Toetsenbord en opent Formaat en typen. Talen en indelingen heeft Instellingen als ouder: de teruglink zegt Instellingen en die tab is actief, ook na de Home-rij. Effect: één woord, twee bestemmingen, en terug wisselt van tab. Aanpassing: noem de Home-rij Taal en indeling en kondig de ouder aan, zoals “Spraak en dicteren” al “Openen in tab Stem” zegt. Eén ouder per scherm kan blijven.

2. **Hoog — Formaat en positie (390 px) en Voeg spraak toe (320 px).** Tekstacties staan op één regel zonder tussenruimte. Gezien: “Herstel voorbeeldToetsenbord proberen” en “Ander model kiezenLater instellen”. Op 320 px vallen de twee links op Formaat en positie onder elkaar, omdat de regel daar breekt. Effect: twee taken lezen als één zin. Overslaan en een ander model kiezen zijn zo niet te scheiden. Op het formaatscherm zijn “Aanpassen op het toetsenbord” en “Toetsenbord proberen” allebei een test. Aanpassing: elke tekstactie op een eigen regel met ruimte, en één testactie op Formaat en positie.

3. **Hoog — Welkom, Later instellen.** Die knop opent het herstelscherm: titel “Kies Lowkey opnieuw” en rode status “Lowkey is niet het huidige toetsenbord”. Later instellen bij spraak toevoegen gaat wel naar Home. Effect: uitstellen tijdens de eerste start ziet eruit als een mislukte toetsenbordwissel. Aanpassing: uitweg naar Home met een neutrale status “Toetsenbord nog niet gekozen” en één herstelactie. De rode status blijft voor de route waarin een ander toetsenbord actief is.

4. **Midden — Dicteergedrag.** De keuze “Vanuit de toetsen” of “Aparte spraakmodus” verandert de rest van het scherm niet. Automatisch starten, onthouden en continu zitten in het dichte blok “Bij aparte spraakmodus”. “Tekst en stoppen” is een alinea. Effect: je ziet niet welke keuzes nu uit staan. Aanpassing: toon die drie opties uitgeschakeld, met één uitlegregel, zolang dicteren vanuit de toetsen start, en maak ze beschikbaar in de andere modus. Bewaarde voorkeuren blijven staan.

5. **Midden — Tabbalk bij grotere tekst.** Op 320 en 390 px passen Home, Stem, Woorden en Instellingen: 0 px overloop, kolom 70 px. Bij zoom 2× op 320 px loopt Woorden 15 px en Instellingen 31 px uit de kolom, en de pagina is 69 px breder dan het venster. De tab gebruikt `white-space: nowrap` en vaste px. Effect: de hoofdnavigatie blijft heel op standaardgrootte en breekt bij grotere tekst. Het kortere “Woorden” koopt die ruimte niet. Aanpassing: labels laten wrappen binnen de kolom, en 200% tekst apart van 320 px controleren. De berekende contrasten van de gebruikte paren zijn minstens 5,2; selectietekst op de selectietint is 5,18 licht en 5,17 donker.

## Behouden

- Home na setup. Eén statuszin met stip en tekst, één primaire actie “Toetsenbord proberen”, en spraak als gewone rij. De huidige Home telt microfoon en model mee in dezelfde vier stappen (`HomeScreen.kt`). Die checklist staat hier terecht niet meer in het midden.
- De lichte familie. Vier logopaden gelijk aan `Brand.tsx` en `opstudio_logo_blue.xml`, wit vlak, blueprint `#17659C`, Bricolage op woordmerk en paginatitel, systeemfont op bediening, open rijen, en de genummerde staplijn met blauwe rand zoals `.start` en `.steps` in `today.module.css`. Donker houdt hetzelfde ritme op `#101E2B`, met actie `#83C9F4` en schil `#0A1621`.

## Eerstvolgende

1. Ontwerpaanpassing: tekstacties onder elkaar, en op Formaat en positie één testactie.
2. Ontwerpaanpassing: Later instellen naar een neutrale Home-status; de Home-rij Taal en indeling, met de ouder vooraf zichtbaar.
3. Keuze voor de keyboard-track: “Op een breed scherm: Zelfde als telefoon / Splitsen” is een bespreekalternatief. Handmatige split blijft buiten dit concept tot er één prioriteitsregel is voor een handmatige override tegenover die voorkeur.

Daarna, kleiner dan die drie: de afhankelijkheden op Dicteergedrag zichtbaar maken, en op Stem dezelfde modelrij gebruiken voor voortgang, fout, annuleren en een model dat alleen Engels herkent. Stem of Voice als productnaam, en Bricolage tegenover een uitgebreider lettertype voor bediening, blijven de open keuzes uit het voorstel.
