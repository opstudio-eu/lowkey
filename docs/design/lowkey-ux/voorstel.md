# Lowkey × OpStudio Signal — ontwerpvoorstel

12 november 2026 · **Ter bespreking, geen goedgekeurde implementatiespecificatie.**

**Actuele uitwerking:** na de drie reviews is op verzoek van de gebruiker versie 3 gebouwd, inclusief startup-motion samen met Opus. Zie [v3-uitwerking.md](v3-uitwerking.md) voor de werkelijk uitgewerkte toestanden, controles en grenzen. De scores in `reviews/` horen bij de eerdere versie.

## Doel en afbakening

Maak de companion-app rustiger, begrijpelijker en herkenbaar als OpStudio-product. Laat de gebruiker snel zien of het toetsenbord werkt, het proberen en gericht aanpassen. Behoud alle mogelijkheden; toon technische details wanneer ze nodig zijn.

Alleen ontwerpvoorbereiding in deze worktree, vanaf `origin/main` (`06374f6`). Geen Kotlin-, build-, versie-, release- of keyboardwijzigingen. De aparte keyboard-track is alleen als opgegeven toekomstige scope meegenomen, niet gekopieerd of getest. Geen emulator of fysieke telefoon gebruikt. Het bestaande OpStudio-beeldmerk blijft geometrisch ongewijzigd.

## Bronnen en betrouwbaarheid

Dit is een **bronaudit**, geen geobserveerde gebruikerstest of volledige visuele audit van de Android-app. De bevindingen hieronder zijn afgeleid uit de actuele Compose-schermen. We hebben geen nieuwe Lowkey-screenshots of TalkBack-observaties: de gedeelde emulator is voor de keyboard-track gereserveerd.

### Lowkey, gelezen op de basiscommit

Alle paden hieronder zijn vanaf de repository-root. De schermen staan onder `app/src/main/java/com/rheerkens/androidkeyboard/companion/`.

| Bron | Wat is vastgesteld |
| --- | --- |
| `SlimApp.kt` | Vier tabs; permanente Test keyboard-actie; apart Try it-scherm; kleine backstack; deep links naar gescrolde instellingen. |
| `screens/HomeScreen.kt` | Vier installatiestappen blijven zichtbaar na voltooiing; updatebanner, introductie, playground, vier overzichttegels, diagnostiek en informatie. |
| `screens/SettingsScreen.kt` | Eén lange lijst met toetsenbord, spraak, appearance, beeldbewerking, updates en meer; layouts en modelgeheugen staan inline. |
| `screens/ModelsScreen.kt` | Modelgeheugen en opslag vóór modelkeuze; lijst per modelfamilie; download/annuleren/activeren/verwijderen; actief model beschermd. |
| `screens/DictionaryScreen.kt` | Termen en spraakregels zijn gescheiden; zoekveld voor termen; toevoegen/bewerken/verwijderen; import/export onderaan is uitgeschakeld (Coming later); globale regelschakelaar. |
| `screens/LanguagesScreen.kt` | Geïnstalleerde talen en downloads; layouts elders; download activeert de taal meteen. |
| `screens/Common.kt`, `screens/KeyboardTestSheet.kt` | Herbruikbare rijen, kaarten en keuzeknoppen; testpaneel met terugkeer naar bronscherm. |
| `theme/Theme.kt`, `screens/OpStudioCredit.kt` | OpStudio-blauw bestaat al; lichte/donkere modus; merk vooral in informatieblok; geen aangepaste typografie. |
| `settings/Settings.kt`, `models/ModelCatalog.kt` | Huidige gedragsinstellingen en echte modelcatalogus; geen gemeten snelheidsclaims. |

### Signal, alleen gelezen

Gevonden via het geregistreerde project **OpStudio, Signal**, repository `opstudio-eu/opstudio-signal`. Gelezen checkout: HEAD `e59879e9a49b4e9e0cce6b00bb5ec5121f38fb53`, met bestaande ongecommitte wijzigingen. De **gelezen werkbestanden**, niet alleen HEAD, zijn de referentie. Geen bestanden daar gewijzigd, geen server gestart, geen gegevens opgevraagd.

| Relatieve Signal-bron | Betekenis |
| --- | --- |
| `src/app/globals.css` | Actuele blauwe shell, wit werkvlak, open secties, haarlijnen, pilknoppen, status met tekst. |
| `src/components/Brand.tsx` | Vier oorspronkelijke logopaden; OpStudio-woordmerk met aparte productaanduiding. |
| `src/components/AppShell.tsx` | Merk in navigatie, mobiele bovenbalk; productinhoud los van shell. |
| `src/components/overview/{Overview.tsx,today.module.css}` | Volgende actie vóór overzichten; korte teksten, uitleg inklapbaar. |
| `src/components/settings/{SettingsPage.tsx,settings.module.css}` | Onderdelen gegroepeerd met samenvatting en verdieping. |
| `docs/branding/new-opstudio/README.md` en `implementation.md` | Nieuwere blauwe identiteit, Bricolage-koppen en Instrument Sans; uitvoering beschreven. |
| `docs/superpowers/plans/2026-11-11-signal-brand-rollout.md` | Nieuwere richting en scheiding van hoofd- en secundaire taken. |
| `docs/branding/new-opstudio/today.html` | Bestaand zelfstandig visueel voorbeeld; opnieuw gerenderd en bekeken op 390 px. Geen bewijs van live-data of livegedrag. |

**Versieverschil:** `docs/branding/README.md`, `redesign/` en `today-focus/` beschrijven ook oudere groen-mosterdvarianten. Die zijn niet het gekozen vertrekpunt voor Lowkey. De nieuwere implementatienotities én actuele CSS wijzen op blauw/wit. Geen oude kleuren stilzwijgend overgenomen. De blauw/wit-referentie is geen toezegging over de actuele publieke Signal-deployment.

## Bronaudit: problemen en behouden kwaliteiten

Prioriteit P1 = grote invloed op oriëntatie; P2 = gerichte verbetering. Dit zijn ontwerpprioriteiten, geen gemeten ernstscores.

| Stap | Bronbevinding | Effect / risico | Ontwerpbesluit |
| --- | --- | --- | --- |
| 1. Eerste start (P1) | Home telt microfoon en model mee in dezelfde vier stappen als toetsenbord activeren. | Typen lijkt onvolledig zonder optionele spraak. | Twee vereiste toetsenbordstappen; spraak als optionele uitbreiding. |
| 2. Terugkerend Home (P1) | Afgeronde checklist blijft; even zware tegels voor model, woordenboek, laatste modus, logs. | Geen duidelijke hoofdtaak, blijvende installatieruis. | Compacte status en prominente testactie. Alleen herstelstap als iets ontbreekt. |
| 3. Instellingen (P1) | Veel schakelaars, meerregelige uitleg en keuzereeksen in één lijst. | Veel lezen/scrollen; verwante instellingen raken uit beeld. | Categorie-index met huidige waarden en korte detailpagina’s. |
| 4. Taal instellen (P1) | Download onder Talen, actieve layouts onder Instellingen, spraaktaal weer elders. | Drie betekenissen van taal zijn lastig te onderscheiden. | Eén ingang Talen en indelingen; expliciet toetsenbordtaal versus dicteertaal. |
| 5. Stem kiezen (P1) | Geheugen/opslag/broninformatie vóór de modelkeuze. | Technische kosten vóór gebruikersdoel. | Actief model, passende keuze en downloadstatus eerst; opslag en runtime apart. |
| 6. Woordenboek (P2) | Correcte scheiding termen/regels; lange uitleg, uitgeschakelde import/export onderaan; direct verwijderen. | Niet beschikbare functies kosten aandacht; weinig herstel bij wissen. | Korte labels, voorbeeld per tab; import/export niet promoten; undo of bevestiging als afzonderlijk te ontwerpen gedrag. |
| 7. Proberen (P2) | Permanente Test keyboard-actie én apart playground. | Twee concurrerende testbegrippen. | Eén herbruikbaar testpaneel; uitgebreide veldtypen als verdieping daarin. |
| 8. Identiteit (P2) | Blauwe tokens bestaan al; standaard Material-typografie en gelijk gewicht voor secties. | Recolour alleen lost de ervaren chaos niet op. | Familie via koppen, open secties, beperkt actieaccent en onveranderd merk. |

Behouden: vier herkenbare tabs, lokale verwerking, aparte termen en spraakregels, zichtbare downloadfouten, annuleren, bescherming actief model, terugkeer uit testpaneel, systeemthema, toegankelijke semantiek die al aanwezig is. Geen functionaliteit verwijderen om een rustiger screenshot te krijgen.

## Informatiearchitectuur

**Voorkeur: vier tabs behouden: Home · Stem · Woordenboek · Instellingen.** Stem vervangt het technische label Models en omvat dicteren plus modellen. Het technische woord blijft in de pagina staan: Spraakmodellen. Alle labels zijn voorlopig Nederlands voor bespreking; dit is geen opdracht om app-lokalisatie toe te voegen.

Overwogen alternatief: drie tabs (Home, Woordenboek, Instellingen), met Stem onder Instellingen. Dit oogt rustiger maar verstopt een kernfunctie met downloads en foutafhandeling. Een hub met vijf tabs inclusief Toetsenbord maakt navigatie juist voller. Vier past bij het huidige mentale model en beperkt migratie.

| Bestemming | Eerste niveau | Verdieping / ingang |
| --- | --- | --- |
| Home | Werkt Lowkey? · Toetsenbord proberen · huidige taal en stem | Herstel setup; configuratie via gerichte links |
| Stem | Spraakstatus · Actief spraakmodel · Ander model | Dicteertaal; dicteergedrag; opslag/geheugen; bron/licentie |
| Woordenboek | Woorden · Spraakregels | Zoek/toevoegen/bewerken; uitleg; spellingherstel en regelschakelaar ieder op één plek |
| Instellingen | Toetsenbord · Talen en indelingen · Klembord en afbeeldingen · Weergave · Updates · Over Lowkey | Korte waarden onder elke categorienaam; expliciete verwijzing “Spraak en dicteren — openen in Stem”; geen dubbele detailpagina |

### Onboarding en Home

1. Welkom: “Typ op jouw manier. Dicteer op je toestel.” Eén knop **Stel toetsenbord in**; beknopte uitleg over lokale verwerking en downloads.
2. **Activeer Lowkey** → Android-invoermethode-instellingen. Terugkeer controleert systeemstatus, geen optimistische vinkjes.
3. **Kies Lowkey** → systeemkiezer; alleen beschikbaar als geactiveerd. Terugkeer controleert opnieuw.
4. **Klaar om te typen** → Toetsenbord proberen. Secundair **Voeg spraak toe**, overslaan blijft mogelijk.
5. Optionele spraaksetup: microfoon met rationale, passend model uit de bestaande catalogus met taal/omvang, **bevestig dicteertaal**, expliciete downloadknop en secundair Ander model kiezen. De huidige standaard is Engels (`speechLanguage = "en"`), niet automatisch de toetsenbordtaal. Volgen of Nederlands kiezen vergt bevestiging. Bij een Engels-only model is de taal vast. Bij weigering mag typen doorgaan; permanent geweigerd → gerichte app-instellingenlink. Downloadfout → probeer opnieuw/annuleer.

Tijdens onboarding geen hoofdtabs. Later instellen is een expliciete uitweg naar Home met herstelstatus. Pas na setup/overslaan verschijnt de hoofdnavigatie; overslaan mag geen fictieve successtatus geven. De korte, genummerde stappen met blauwe linkerlijn zijn vertaald uit Signals `.start`/`.steps`.

Home ná setup: titel **Klaar om te typen**, status met tekst, één primaire **Toetsenbord proberen**. Daarna twee open rijen: Toetsenbord (taal/layout, linkt naar Talen en indelingen) en Spraak (actief model en gekozen dicteertaal, of “Nog niet ingesteld”). Geen extra woordenboekrij naast de bestaande tab, sessieteller, laatste-modustegel of uitgebreide privacyverklaring. **Setup controleren** blijft onder Over Lowkey bereikbaar. Bij gedeactiveerd toetsenbord of andere huidige IME vervangt een gerichte herstelactie de successtatus. Spraak ontbreekt? Geen waarschuwingskleur op Home: dat is een toegestane keuze.

Updates: alleen bij beschikbare update een rustige regel met versie en Bekijken; geen automatische installatie. Diagnostiek via Over Lowkey → Problemen oplossen. Downloads en updatechecks blijven transparant beschreven; “lokaal” betekent geen verbod op die netwerkacties.

### Instellingen en de komende keyboard-track

| Categorie | Inhoud |
| --- | --- |
| Toetsenbord → Formaat en positie | Bestaande hoogte (Compact / Normaal / Hoog); telefoonmodus Standaard / Eenhandig / Zwevend. Links/rechts alleen bij Eenhandig. Optionele breed-schermkeuze Splitsen versus Zelfde als telefoon, als bespreekalternatief voor de komende track. Slepen en grootte aanpassen gebeuren ín het toetsenbord, niet op een nagemaakt klavier. |
| Toetsenbord → Aanpassen | Trilling, optionele cijferrij, snelmenu; suggestiestrook en autocorrect onder Typen. Geen dubbele schakelaars op Home. |
| Toetsenbord → Typen | Autohoofdletter, autocorrect, suggesties; Fysiek toetsenbord als eigen rij onder Toetsenbord. |
| Talen en indelingen | Per taal geïnstalleerd/actief, bijbehorende layouts, huidige layout; taal toevoegen. Behoud “minstens één layout actief” en zichtbaar downloadgedrag. |
| Spraak en dicteren | Expliciete tabwissel naar Stem. Dicteertaal en Dicteergedrag hebben Stem als enige ouder. Auto-start, onthouden en continu staan onder Aparte spraakmodus; live tekst en stoppen blijven algemene keuzes; geheugenretentie onder Opslag en prestaties. |
| Klembord en afbeeldingen | Recente screenshots met toestemmingsstatus; loephoeken onder Afbeeldingseditor. Geen nieuwe klembordfeatures. |
| Weergave | Systeem / Licht / Donker. Companion en toetsenbord blijven consistent zonder een nieuwe onafhankelijkheidsschakelaar. |
| Updates | Eigen categorierij met automatisch controleren, handmatig controleren en versie-informatie; behoudt de ingang uit README. |
| Over Lowkey | Versie, link naar Updates, privacy/netwerk, licenties, Problemen oplossen en Setup controleren. |

**Automatisch split blijft een gebruikerskeuze.** Opus wees terecht op de dubbelzinnigheid van een losse Split-radio plus auto-split-schakelaar. Het herziene prototype toont als **bespreekalternatief** “Op een breed scherm: Zelfde als telefoon / Splitsen”, standaard Zelfde als telefoon. Dit mag handmatige Split-bediening uit de keyboard-track niet stilzwijgend vervangen of verwijderen. Als die handmatige modus behouden moet blijven, is vóór implementatie één expliciete prioriteitsregel nodig voor handmatige override versus breed-schermvoorkeur. Drempel, effectieve modus, herstel, vouwen/draaien en persistentie blijven bij de keyboard-track. Geen wijziging van die track in dit voorstel.

Ieder detailscherm heeft één ouder-tab. Een kruisverwijzing zegt expliciet dat zij een andere tab opent. Deep links gaan niet meer naar numerieke lijstindices. Android-terug keert naar de ouder; tabwissel bewaart lokale scroll/zoekstatus. Terug uit testpaneel behoudt instelling en testtekst; tekst blijft sessiegebonden. Op ieder invoergerelateerd detail blijft dezelfde actie Toetsenbord proberen bereikbaar. Deze zijn ontwerpvereisten, nog geen codewijzigingen.

| Bestaande ingang | Nieuw doel | Ouder-tab |
| --- | --- | --- |
| `SETTINGS_KEYBOARDS` | Talen en indelingen | Instellingen |
| `SETTINGS_VOICE` | Dicteergedrag | Stem |
| `MODELS` | Stem, modellenlijst | Stem |
| `DICTIONARY` | Woordenboek, Woorden | Woordenboek |
| `HOME` | Home of passende setupstap | Home |
| Komende positie-ingang | Toetsenbord → Formaat en positie | Instellingen |

### Stem / modellen

Actieve taal en model eerst; bijvoorbeeld **Whisper base · Meertalig · Engels**, “Tekst verschijnt na Stop”. “Meertalig” is capaciteit, Engels de huidige voorkeur. Geen verzonnen kwaliteitspercentages of meetclaims. De bestaande kwalitatieve uitleg over grotere modellen mag onder details blijven, zonder gegarandeerde snelheid of nauwkeurigheid. Alleen “Aanbevolen” waar de catalogus dat bepaalt en taalcompatibiliteit zichtbaar is.

Geïnstalleerde modellen blijven direct bereikbaar. Beschikbare modellen als open rijen onder Meertalig en Alleen Engels; familie alleen als metadata (de huidige catalogus heeft één familie). Per rij: naam, taal, downloadomvang; informatieknop voor details. Eén relevante rijactie: Download / Annuleer / Gebruik. Na download wordt de werkelijke activatiestatus getoond; niet suggereren dat downloaden altijd activeert. Actief model heeft tekststatus en kan niet worden verwijderd. Verwijderen in Meer met verklaring als het niet beschikbaar is.

Downloadstatus toont voortgang en fout bij het betreffende model; meerdere downloads blijven vindbaar. Onvoldoende opslag blokkeert met uitleg en beheerlink; offline toestand biedt opnieuw proberen zodra verbinding terug is, zonder fictieve retry-automatiek. Opslag op toestel en geladen werkgeheugen krijgen afzonderlijke labels onder **Opslag en prestaties**. Bron, checksums en licentie staan in modeldetails, niet vóór de keuzelijst.

Dicteergedrag moet afhankelijkheden zichtbaar houden: Keyboard-first sluit automatisch starten, laatste modus onthouden en continu dicteren in de huidige implementatie uit. Toon een korte uitleg bij uitgeschakelde opties; geen opgeslagen voorkeur stilzwijgend wissen. Instellingen voor regels/casing verwijzen naar dezelfde waarden in Woordenboek.

### Woordenboek

Tabs **Woorden** en **Spraakregels**. Eén zin per tab: “Bewaar spelling zoals jij ze schrijft” versus “Vervang een herkende uitdrukking na Stop”. Voorbeeld: `project delta → Project Delta`, expliciet voorbeeld, geen training van herkenning.

Zoekveld, aantal resultaten en Toevoegen; open rijen. Bewerken door rij te openen; verwijderen in rijmenu met herstelmogelijkheid als latere afzonderlijke gedragswijziging. **Import/export niet tonen als werkende mogelijkheid:** de huidige knoppen zijn uitgeschakeld; implementatie ervan valt buiten deze track. Geen andere inhoud van woordenboek of klembord delen in diagnostiek. Lege lijst en geen zoekresultaten hebben verschillende tekst en actie. Globale spraakregelschakelaar blijft bij die tab en staat bij eerste start **uit**; uitgeschakelde regels blijven zichtbaar met reden. Spellingherstel bij dicteren (`matchTermCase`, standaard aan) hoort uitsluitend bij Woorden. Geen dubbele schakelaars onder Dicteergedrag.

## Vertaling van Signal naar Android

Familie is meer dan dezelfde blauwe kleur: dezelfde originele markering, kopkarakter, rust tussen secties, duidelijke werkwoorden en kleine hoeveelheid accent. Geen dashboardtegels, wolken, mascotte, desktopsidebar of marketingdecoratie toevoegen aan een instellingenapp.

| Token / patroon | Gelezen Signal-bron | Lowkey-voorstel |
| --- | --- | --- |
| Merk / actie | `--brand`, `--surround: #17659C` | Exact blauw; vooral shell/hoofdactie, niet iedere sectietitel blauw |
| Papier / tekst | `#FFFFFF`, `--ink: #101B27` | Wit contentvlak, inkt als hoofdtekst in licht thema |
| Secundaire tekst | `--quiet: #526476` | Hetzelfde in licht thema; niet extra verlichten |
| Zacht vlak / haarlijn | `#F6F9FC`, `#E6EDF3` | Alleen functionele accentzone / rijscheiding; geen geneste kaarten |
| Selectie | `#DCEDF8` | Selectie met tekst/icoon erbij; nooit status uitsluitend in kleur |
| Typografie | Bricolage / Instrument Sans | Voorkeursvariant: Bricolage voor kop/merk, systeemfont voor bediening en gebruikersinhoud. Bronfonts missen o.a. Pools, Tsjechisch en Cyrillisch; vóór bundeling minimaal latin-ext en fallback toetsen. Alternatief: uitgebreid Instrument Sans voor bediening. |
| Hoeken | werkvlak 18–22 px mobiel; knoppen 999 px | Voorstel 20 dp voor buitenste werkvlak, 24 dp/pil voor hoofdactie, 0 voor gewone rijen |
| Ruimte | mobiele page padding 16 px; ruime secties | Voorstel 20 dp buitenmarge (16 compact), 32 dp sectiegap, 12–16 dp rijpadding |
| Maatvoering | webbediening varieert | Android-vertaling: minimaal 48 dp aanraakdoel, geen webpixels letterlijk overnemen |
| Schaal | mobiele titel 25–28 px | Voorstel titel 28/34 sp, sectie 18/24, bediening 16/24, metadata 14/20; geen vaste hoogte bij grotere tekst |

Licht thema sluit het dichtst bij Signal aan. **Donker is een Lowkey-afleiding**, geen gevonden Signal-dark-mode: bestaande `#101E2B` basis, `#83C9F4` actie, `#EAF2F8` tekst en `#A7BCCD` metadata behouden als startpunt; `#0A1621` voor de shell. Semantisch rood blijft voorlopig Lowkeys bestaande rood; geen toetsenbordpalet wijzigen om Signals foutkleur te kopiëren. Geen wit werkvlak afdwingen in Android-systeemdonker. Keuzes voor gekoppelde toetsenbordkleuren later toetsen; deze track raakt het keyboard niet.

Logo: vier paden uit bestaande Lowkey-drawable; ongewijzigde geometrie, geen nieuwe letters tekenen. Wit in blauwe shell is een contextkleurvoorstel uit Signal; de oorspronkelijke blauwe/lichtblauwe assets blijven bestaan. Lettertypes worden alleen in het lokale prototype gebruikt vanuit Signal; er zijn geen fonts, afbeeldingen of afhankelijkheden aan de Android-app toegevoegd. Voor eventuele bundeling eerst de bijbehorende OFL-licenties en app-notices opnemen.

## Klein componentvoorstel

- **App shell**: merk op Home/onboarding, elders compacte categorietitel en terugpijl; vier native bestemmingen onderaan buiten onboarding. Alleen bovenhoeken van het werkvlak afgerond. De demo verkort de Nederlandse tab naar Woorden; het Engelse productlabel Dictionary blijft te toetsen bij 200% tekst.
- **Statusregel**: tekst en eventueel symbool; gereed, herstel nodig, optioneel niet ingesteld, downloaden, fout. Geen lege actieve chips.
- **Instellingenrij**: titel, huidige waarde, chevron; geen vier regels uitleg standaard.
- **Keuzedetail**: radio/keuzelijst, conditionele velden en testactie; voor Formaat en positie uitsluitend een concept, geen nagemaakte keyboardwerking.
- **Modelrij**: naam + taal/omvang, actie/status, voortgang/fout direct onder die rij.
- **Testpaneel**: hetzelfde paneel vanuit Home en instellingen; volledige testschermen alleen als verdieping.
- **Meer-menu / onderblad**: zeldzaam bestaand beheer en verwijderen; native focus en terugkeer. Geen nog niet bestaande import/export activeren.

## Bespreekbaar prototype

`prototype.html` is de herbruikbare conceptbron. `build-prototype.py --signal-root <signal-checkout>` maakt een zelfstandig `artifacts/lowkey-ux/prototype.html` met lokaal ingebedde referentiefonts en het bestaande Lowkey-logo. Geen netwerk, backend, appinstallatie of opslag. Alle interacties zijn tijdelijke demonstraties; de komende toetsenbordopties zijn nadrukkelijk niet functioneel. Geen modeldownload of microfoontoestemming wordt aangevraagd.

Het voorbeeld toont Home, Stem, Woordenboek, categorieën, Formaat en positie, optionele onboarding en een donker voorstel. Het is geen Android-emulator en bewijst geen Compose-, IME- of TalkBack-gedrag. Testmedia en ingebedde fonts blijven onder gitignored `artifacts/`.

## Beslissingen voor bespreking

1. **Voice** als Engelse producttab, met Stem in deze Nederlandse bespreking? Voorkeur: Voice, omdat de ingang ook dicteertaal en gedrag omvat. Nederlands toevoegen aan de app is geen onderdeel van deze track.
2. **Bricolage-koppen + systeemfont** als compacte Android-vertaling, of uitgebreid Instrument Sans voor bediening? Voorkeur: systeemfont voor volledige schriftdekking; de huidige referentiefonts zijn niet productierijp voor alle talen.
3. **Breed-schermvoorkeur** versus handmatige Split-modus: de onderlinge prioriteit met de keyboard-track bespreken. Het prototype toont het eenvoudiger voorstel, niet een besluit om handmatige split weg te nemen.

## Latere uitvoerbare delen, nog niet geautoriseerd

1. Shell/typografie/open rijen met bestaande navigatie en gedrag; lichte én donkere modus, fonts/licenties.
2. Instellingencategorieën en stabiele deep links; bestaande waarden behouden; keyboard-track pas na aparte integratie.
3. Home/onboarding en één testpaneel; optie om alleen te typen; terugkeer uit Android-instellingen.
4. Modellen/woordenboek verfijnen; eventuele undo apart specificeren en testen.

Per deel: `testDebugUnitTest`, passende regressies, UI-resultaat op een later vrijgegeven emulator, 320/390 dp en breed venster, 200% tekst, TalkBack, terugknop, permissieweigering, geen model, fout/download/annuleren, actieve-modelbescherming, vouwen/draaien. Geen UI-goedkeuring op basis van dit HTML-voorbeeld alleen.

## Review en verificatie

Opus 5.5 heeft bron en prototype kritisch beoordeeld. De oorspronkelijke beoordeling blijft in `opus-review.md`; de verwerkte correcties en bewust open beslissingen staan in `review.md`. Werkelijk uitgevoerde controles staan in `verificatie.md`. Het gecorrigeerde voorstel is geen stilzwijgend goedgekeurde productimplementatie.
