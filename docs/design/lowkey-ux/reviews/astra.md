# Astra — onafhankelijke ontwerpreview

12 november 2026. **Gewogen oordeel: 7,9/10.** Een overtuigende, rustige OpStudio-vertaling met een begrijpelijke hoofdstructuur; de concrete bediening en enkele belangrijke toestanden zijn nog minder uitgewerkt dan de visuele richting.

Dit zijn subjectieve ontwerpscores, geen gemeten gebruikssucces. Andere reviewresultaten zijn niet gelezen.

| Criterium | Gewicht | Score |
| --- | --- | --- |
| Informatiearchitectuur | 25% | 8,0 |
| Gebruiksgemak | 25% | 7,0 |
| Visuele rust en hiërarchie | 20% | 8,0 |
| OpStudio/Signal-samenhang | 20% | 9,0 |
| Toegankelijkheid en adaptiviteit | 10% | 7,0 |

Berekening: 8 × 0,25 + 7 × 0,25 + 8 × 0,20 + 9 × 0,20 + 7 × 0,10 = 7,85, afgerond 7,9.

## Waarnemingsbasis

Gelezen: `AGENTS.md`, `README.md`, `REFERENCES.md`, de gedeelde reviewrubric, `../voorstel.md` en `../prototype.html`. Er was geen lokale instructiefile op de voorgeschreven plek. Het zelfstandige `artifacts/lowkey-ux/prototype.html` en de bijbehorende scenario's zijn werkelijk met `html_preview` bekeken:

- Home en onboarding: 390 px licht, 320 px donker.
- Instellingen, Stem en Woordenboek: 320 px licht, 390 px donker.
- Formaat en positie: 390 px licht, 320 px donker; aanvullend Eenhandig op 320 px licht.
- Een eigen tijdelijke artifactvariant controleerde de spraakregeltab, terugbestemmingen, de dicteertaallink en de twee testacties bij Formaat en positie.

Signal is alleen gelezen: de actuele `src/app/globals.css`, `src/components/Brand.tsx`, `src/components/overview/today.module.css` en `docs/branding/new-opstudio/{README.md,implementation.md}`. Het zelfstandige `today.html` is op 390 px bekeken. De overeenkomst is concreet: hetzelfde blauw `#17659C`, witte werkvlakken, Bricolage-koppen, haarlijnen, open secties, pilknoppen en status met tekst. De oorspronkelijke logo-opbouw blijft herkenbaar. Donker is terecht een Lowkey-afleiding; daarvoor is geen Signal-equivalent aangetoond.

Geen Android-app, IME, TalkBack, echte toestemming, downloads of terugkeer uit systeemschermen getest. Geen volledige 200%-tekstcontrole of weergave met geopende softwaretoetsenbord. De HTML-preview geeft volledige paginahoogte; dit bewijst geen bediening binnen een werkelijk telefoonvenster.

## Vijf gerichte opmerkingen

1. **Hoog — maak de mobiele hoofdnavigatie werkelijk beoordeelbaar.** Bron: alle renders, vooral Stem op 320 px; `.nav` in `../prototype.html`. De tablabels zijn daar 11 px en de balk staat na de volledige pagina-inhoud. Bij Stem verschijnt hij pas onder een lange modellenpagina. Dat maakt bestemming wisselen onnodig lastig en verkleint de leesbaarheid. Het voorstel noemt native bestemmingen, maar het voorbeeld bewijst hun bruikbaarheid nog niet. Toon een telefoonvenster met een blijvend bereikbare onderbalk, scrollende inhoud en grotere labels. Controleer daarna expliciet 200% tekst en de uiteindelijke Engelse labels. Dit is een beperking van het huidige bewijs, geen vastgestelde Android-bug.

2. **Midden — laat de hele modelkeuze zien vóór goedkeuring van Stem.** Bron: Stem-render en het downloadgedrag in `../prototype.html`. De actieve installatie en één alternatief zien er rustig uit; na downloaden resteert alleen “Gedownload · voorbeeld”, zonder zichtbare volgende stap om dat model te gebruiken. De Engelse modellen zijn slechts uitlegtekst. Daardoor is nog niet zichtbaar of geïnstalleerd, actief, downloaden en fout even begrijpelijk blijven bij een echte catalogus. Werk enkele fictieve toestanden uit met respectievelijk **Gebruik**, **Actief**, **Annuleer** en **Opnieuw proberen**. Het voorstel beschrijft deze al goed; de ontbrekende uitwerking is de onzekerheid.

3. **Midden — geef Formaat en positie één duidelijke testactie.** Bron: beide renders van dit scherm en de gecontroleerde twee `data-test`-knoppen. “Aanpassen op het toetsenbord” en “Toetsenbord proberen” openen hetzelfde paneel. Op 390 px lopen bovendien “Herstel voorbeeld” en de tweede testtekst visueel tegen elkaar. Dit herintroduceert precies de concurrerende testingangen die het voorstel wil opruimen. Behoud één actie met één resultaat en zet herstel apart. Laat die actie later aantoonbaar terugkeren naar dit detail. De keuze over handmatige Split versus breed-schermvoorkeur blijft afzonderlijk voor gebruiker/keyboard-track.

4. **Midden — zet woordenbeheer vóór dicteergedrag.** Bron: Woordenboek op 320 px licht en 390 px donker. De aangevinkte regel “Spelling herstellen bij dicteren” staat tussen de korte uitleg en het zoekveld. Voor iemand die een woord zoekt of toevoegt krijgt een globale gedragsinstelling zo onverwacht veel nadruk. Plaats zoeken en toevoegen direct na de tab, gevolgd door resultaten; zet spellingherstel in een herkenbaar instellingenonderdeel binnen dezelfde bestemming. Voeg het in het voorstel genoemde resultaataantal toe. Behoud wel één canonieke plek voor deze waarde.

5. **Midden — maak terugpaden consequent met de categorieboom.** Bron: eigen interactiecontrole van Typen/Aanpassen en Talen en indelingen. Typen en Aanpassen keren rechtstreeks terug naar Instellingen, terwijl ze onder Toetsenbord staan. “Dicteertaal kiezen” opent eerst Stem in plaats van het taalkeuzescherm. Dit kost oriëntatie en extra tikken. Laat beide details naar Toetsenbord terugkeren; laat de taallink het taalkeuzedetail openen met Stem als duidelijke ouder. Leg deze routes in het prototype vast voordat de nieuwe informatiearchitectuur wordt overgenomen.

## Behouden

- Home heeft één overtuigende hoofdactie en slechts twee nuttige configuratierijen. De scheiding tussen noodzakelijk toetsenbord en optionele spraak in onboarding maakt het product begrijpelijker.
- De merkfamilie ontstaat door typografie, open compositie en kleur samen. Het behouden logo en de rustige donkere afleiding geven Lowkey identiteit zonder de mobiele taak te overschaduwen.

## Eerstvolgende verbeteringen

1. Kleine ontwerpaanpassing: navigatie, terugpaden en de dubbele testactie herstellen; opnieuw bekijken op een begrensd telefoonvenster.
2. Kleine ontwerpuitwerking: modeltoestanden en woordenbeheer afmaken, daarna grotere tekst en lange labels controleren.
3. Besluit met gebruiker/keyboard-track: prioriteit van handmatige Split tegenover breed-schermvoorkeur vastleggen. Pas daarna de effectieve modus en herstelactie definitief ontwerpen; geen keyboardwijziging afleiden uit deze review.
