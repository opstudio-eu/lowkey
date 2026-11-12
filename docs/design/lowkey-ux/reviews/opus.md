# Opus-review — herzien Lowkey-concept (ronde 2)

## Waarneming

- **Gelezen:** `AGENTS.md`, `README.md`, `REFERENCES.md`, `reviews/README.md`, `../voorstel.md`; ter controle `ModelCatalog.kt` (Whisper base ≈ 160 MB en small ≈ 375 MB kloppen; base is `recommended`) en `Settings.kt` (`speechLanguage = "en"`, `matchTermCase = true` kloppen).
- **Signal (alleen gelezen):** `src/app/globals.css`, `src/components/Brand.tsx`, `src/components/AppShell.tsx`, `src/components/overview/today.module.css`. De tokens (`#17659C`, `#101B27`, `#526476`, `#E6EDF3`, `#DCEDF8`) en de vier logopaden zijn in het prototype byte-identiek overgenomen. De opbouw met woordmerk plus productaanduiding volgt `Brand.tsx`.
- **Gerenderd:** `artifacts/lowkey-ux/`. Home klaar op 390 licht, Home herstel op 390 donker, onboarding op 390 licht, Stem op 320 donker, Woordenboek op 320 licht, Instellingen op 320 licht, Formaat en positie op 320 donker.
- **Interactie:** afgeleid uit het ingebedde script (routes, `aria-current`, focusherstel, dialoog). Ik heb niet live geklikt.
- **Niet bekeken:** Compose, IME, TalkBack, 200% tekst, echte Android-terugnavigatie en een breed scherm. HTML bewijst daar niets over.

## Scores

| Criterium | Gewicht | Score |
| --- | --- | --- |
| Informatiearchitectuur | 25% | 7,5 |
| Gebruiksgemak | 25% | 6,5 |
| Visuele rust en hiërarchie | 20% | 8,0 |
| OpStudio/Signal-samenhang | 20% | 8,0 |
| Toegankelijkheid en adaptiviteit | 10% | 6,5 |
| **Gewogen totaal** | | **7,4 / 10** |

**Eindoordeel:** het concept is rustig, herkenbaar als OpStudio en structureel veel helderder dan de huidige app. De resterende zwaktes zitten in randtoestanden (overslaan, download, verwijderen) en in een paar naamgevings- en navigatienaden, niet in de grote lijn.

## Belangrijkste opmerkingen

**1. "Later instellen" komt uit op een onjuiste herstelstatus — hoog**
- *Scherm:* Onboarding → Later instellen → `recovery`.
- *Waargenomen:* een nieuwe gebruiker die de setup overslaat, krijgt "Kies Lowkey opnieuw" te zien, met "Een ander toetsenbord is nu actief. Je instellingen zijn bewaard." De Spraak-rij toont bovendien "Whisper base · Engels", terwijl er nog niets is ingesteld.
- *Effect:* dit is precies de fictieve status die het voorstel zelf verbiedt. De gebruiker begrijpt niet wat er "opnieuw" moet.
- *Aanpassing:* maak aparte Home-toestanden voor "nog niet geactiveerd", "geactiveerd maar niet gekozen" en "ander toetsenbord actief", elk met eigen kop, tekst en actie. Laat de Spraak-rij "Nog niet ingesteld" tonen. Dit is een kleine ontwerpaanpassing.

**2. Downloadflow bij Stem is te dun voor de beloofde rijstatussen — hoog**
- *Scherm:* Stem → Meertalige modellen.
- *Waargenomen:* Download springt direct naar "Gedownload · voorbeeld". Er is geen voortgang, geen Annuleer, geen fouttoestand en geen "Gebruik"-actie. Voortgang en annuleren staan wel in het voorstel en zijn ook in de huidige app een kernkwaliteit. Het label "Aanbevolen", dat de catalogus voor base wel heeft, ontbreekt. Na de download blijft onduidelijk welk model actief is.
- *Effect:* het moeilijkste deel van Stem (downloaden, falen, activeren) is niet te beoordelen.
- *Aanpassing:* voeg aan de scenarioselectie de toestanden "downloaden 40% + Annuleer", "fout + Opnieuw" en "gedownload + Gebruik" toe, met de status direct onder de modelrij. Toon "Aanbevolen" alleen bij base, zoals de catalogus bepaalt.

**3. "Woorden" betekent twee dingen; Home springt ongemarkeerd naar een andere tab — midden**
- *Schermen:* hoofdnavigatie, Woordenboek-subtabs, Dicteergedrag ("Openen in tab Woorden") en Home → Toetsenbord.
- *Waargenomen:* het navigatielabel "Woorden" staat boven een pagina "Woordenboek" met een subtab die ook "Woorden" heet. "Openen in tab Woorden" is daardoor dubbelzinnig. De Home-rij Toetsenbord opent Talen en indelingen onder Instellingen, met de terugknop "‹ Instellingen". Instellingen wordt dan actief, zonder dat de rij een tabwissel aankondigt. Dat botst met de eigen regel "kruisverwijzing zegt dat zij een andere tab opent".
- *Effect:* dit verstoort de oriëntatie, vooral bij Android-terug.
- *Aanpassing:* gebruik "Woordenboek" als tablabel en accepteer bij 320 px eventueel een kleinere letter (te toetsen bij 200% tekst). Noem de subtab "Mijn woorden" of "Spelling". Geef de Home-rijen de subregel "Openen in Instellingen", of laat terug altijd naar Home gaan. De keuze tussen die twee laatste opties ligt bij de gebruiker.

**4. Woordenboek-rijen missen beheer; de schakelaar staat tussen uitleg en zoekveld — midden**
- *Scherm:* Woordenboek (320 licht).
- *Waargenomen:* termrijen tonen alleen het woord. Er is geen chevron of rijmenu, dus bewerken en verwijderen zijn niet zichtbaar. "Spelling herstellen bij dicteren" staat tussen de uitleg en het zoekveld en drukt de lijst naar beneden. In de tab Spraakregels zegt de lege zoektekst "Geen woorden gevonden". De voorbeeldregel ziet er actief uit terwijl de globale schakelaar uit staat.
- *Effect:* het woordenboek oogt als een alleen-lezenlijst, en de status van regels is misleidend.
- *Aanpassing:* geef elke rij een subregel en een zichtbaar rijmenu (⋮). Zet de globale schakelaar onder de lijst of in een kopregel. Geef elke tab een eigen lege toestand, en toon regels gedimd met "Uit — regels toepassen staat uit".

**5. Bottomnavigatie zonder iconen, labels 11–12 px — midden**
- *Schermen:* alle tabschermen op 320 px.
- *Waargenomen:* alleen tekst, op 12 px (11 px onder 350 px). Signal gebruikt in `AppShell.tsx` wel een icoon per bestemming. Op Home herstel is geen tab gemarkeerd. "Hoogte" bij Formaat en positie is een kleine inline select, terwijl de rest uit volle optierijen bestaat.
- *Effect:* lage scanbaarheid en weinig ruimte bij grotere tekst. De herstelstatus valt buiten de navigatie.
- *Aanpassing:* gebruik een Material NavigationBar met icoon en label van minimaal 12 sp, en markeer Home ook in de herstelstatus. Maak Hoogte een radiogroep en zet die bovenaan, omdat dit de meest gebruikte bestaande optie is.

## Te behouden

1. **Home met één hoofdtaak.** Kop, tekststatus, één pilknop "Toetsenbord proberen" en twee open rijen. Spraak is optioneel zonder waarschuwingskleur. Dit is de grootste winst ten opzichte van de huidige checklist en tegels.
2. **Instellingen als categorie-index met één ouder per detail.** De regel "Spraak en dicteren · Openen in tab Stem" en de terugknoppen met de oudernaam maken de structuur voorspelbaar. Het merk blijft beperkt tot de shell en de hoofdactie.

## Eerstvolgende verbeteringen

1. **Toestanden aanvullen (klein, ontwerp):** het eerste-start-Home en drie downloadtoestanden bij Stem (opmerkingen 1 en 2). Zonder die toestanden is gebruiksgemak niet te valideren.
2. **Naamgeving en tabgrenzen vastleggen (gebruikersbesluit):** "Woorden" of "Woordenboek" als tab, en of Home-rijen naar een andere tab mogen springen. Koppel dit aan het al geopende besluit Voice/Stem voor de Engelse labels.
3. **Woordenboekrij en navigatie verfijnen (klein, ontwerp):** rijmenu, lege toestand per tab, iconen in de navigatie en Hoogte als radiogroep. De breed-scherm- en Split-prioriteit blijft een **keyboard-track-besluit**. Het prototype houdt die terecht als bespreekalternatief.
