# Opus-review: Lowkey × OpStudio Signal

12 november 2026 · Review van `voorstel.md` en `prototype.html` (plus gebouwde scenario-snapshots). **Ontwerpreview, geen implementatie.**

## Samenvatting

De richting klopt: vier tabs, typen los van optionele spraak, een categorie-index in plaats van één lange lijst, en familie via structuur in plaats van alleen kleur. Het voorstel is zorgvuldig over wat het niet weet. Hieronder staan de punten waar de bron iets anders zegt dan het voorstel of prototype aanneemt. Die moeten vóór een implementatie-handoff worden opgelost.

Vijf blockers:

1. **De Signal-fonts zijn Latin-1-subsets.** Pools, Tsjechisch, Turks en Cyrillisch ontbreken, terwijl Lowkey die talen met hun eigen naam toont. Het buildscript kopieert daarnaast de verkeerde licentietekst.
2. **Dicteertaal is een eigenschap van het model, niet van het toetsenbord.** De standaardwaarde is Engels, en het voorstel geeft dicteertaal twee plekken.
3. **Eén scherm, twee ouders.** Dicteergedrag en Dicteertaal zijn vanuit twee tabs bereikbaar, met verschillend terug-gedrag.
4. **Formaat en positie gebruikt twee bedieningselementen voor één begrip.** Er is een Split-keuze én een auto-split-schakelaar, en de bestaande hoogte-instelling ontbreekt.
5. **Het woordenboek promoot functies die niet bestaan of uit staan.** Import en export zijn uitgeschakeld ("Coming later"). Spraakregels staan standaard uit, maar het prototype toont ze aan.

## Bronnen en werkwijze

Alleen gelezen, geen bestanden buiten dit document gewijzigd.

| Bron | Gebruikt voor |
| --- | --- |
| Lowkey `06374f6`: `companion/SlimApp.kt`, `CompanionLinks.kt`, `screens/{Home,Settings,Models,Dictionary,Languages,KeyboardTestSheet,About}Screen.kt`, `theme/Theme.kt` | Huidige IA, deep links, teksten, kleurschema's |
| `settings/Settings.kt`, `models/ModelCatalog.kt` | Standaardwaarden, modelcatalogus, taalkeuze per model |
| `ime/SlimKeyboardService.kt` (alleen `openCompanion`-aanroepen) | Welke bestemmingen het toetsenbord opent |
| `app/src/main/assets/licenses/` | Huidige notices (geen fonts) |
| Signal-werkbestanden (HEAD `e59879e`, met ongecommitte wijzigingen): `src/app/globals.css`, `src/components/Brand.tsx`, `src/components/AppShell.tsx`, `src/components/overview/{Overview.tsx,today.module.css}`, `docs/branding/new-opstudio/{README.md,implementation.md}`, `public/fonts/*` | Tokens, componentpatronen, fontdekking en licenties |
| Gebouwd prototype en snapshots, bekeken via HTML-preview op 320 en 390 px, licht en donker hostthema | Visuele controle van het concept |

Lowkey-paden zijn relatief aan `app/src/main/java/com/rheerkens/androidkeyboard/`. Signal-paden zijn relatief aan de root van die repository.

Contrastwaarden zijn berekend met de WCAG 2.x-formule. Fontdekking is bepaald door de `cmap`-tabel van beide WOFF2-bestanden uit te lezen.

## 1. Informatiearchitectuur, onboarding en Home

### Wat goed is

- **Vier tabs met Stem** is de juiste keuze. Drie tabs verstopt downloads en fouten, vijf maakt de navigatie voller.
- **Twee vereiste toetsenbordstappen en optionele spraak** lost het echte probleem in `HomeScreen.kt` op. Daar telt "Setup · x of 4 done" microfoon en model mee als voorwaarde.
- **Herstel vervangt succes** alleen als het toetsenbord echt uit staat of niet gekozen is. Dat sluit aan op de bestaande controle bij terugkeer (`LifecycleResumeEffect` en de window-focus-refresh in `HomeScreen.kt`). Behoud die.

### Problemen

**B3. Eén scherm, twee ouders.** In het prototype hoort Dicteergedrag bij Stem (de terugknop zegt "‹ Stem"). Toch opent Instellingen → Dicteren hetzelfde scherm: de actieve tab springt van Instellingen naar Stem. Andersom gaat Stem → Dicteertaal naar Talen en indelingen, en springt de tab naar Instellingen. Op Android maakt dat de terugknop onvoorspelbaar.

Regel: **elk detailscherm heeft precies één ouder-tab.** Een rij in een andere tab mag een **expliciete tabwissel** zijn (bijvoorbeeld "Spraak en dicteren — openen in Stem ›"), maar geen tweede pad naar hetzelfde detailscherm.

**Deep links uitwerken naar de echte bestemmingen.** Het toetsenbord opent vandaag vijf bestemmingen: `SETTINGS_KEYBOARDS`, `SETTINGS_VOICE`, `MODELS`, `DICTIONARY` en `HOME` (onboarding of setup). Leg de nieuwe doelen vast in een tabel. Deze tabel hoort in de handoff:

| Huidig | Nieuw doel | Ouder |
| --- | --- | --- |
| `SETTINGS_KEYBOARDS` (scrollt naar index 8) | Talen en indelingen | Instellingen |
| `SETTINGS_VOICE` (scrollt naar index 10) | Stem → Dicteergedrag | Stem |
| `MODELS` | Stem (modellenlijst) | Stem |
| `DICTIONARY` | Woordenboek, tab Woorden | Woordenboek |
| `HOME` | Home; tijdens setup de onboardingstap | Home |
| (komend) snelmenu → positie | Toetsenbord → Formaat en positie | Instellingen |

**Onboarding toont de onderste navigatie.** Op de setup-snapshot staan de vier tabs al onder de welkomstap. Een nieuwe gebruiker kan zo naar Woordenboek dwalen voordat het toetsenbord werkt. Op 320 px breekt "Woordenboek" bovendien af als "Woordenbo-ek". Voorstel: onboarding als volledig scherm zonder tabbalk, met **Overslaan** als uitweg. Na setup of overslaan verschijnt Home met de tabs.

**Home bevat te veel dubbele ingangen.** Het voorstel noemt twee open rijen (Toetsenbord en Spraak). Het prototype toont er drie: "Je woorden" herhaalt de tab Woordenboek. Laat die rij weg. In het prototype verwijst de rij Toetsenbord ("Nederlands · QWERTY") bovendien naar Formaat en positie. Die rij hoort naar Talen en indelingen te gaan.

**De permanente testactie verdwijnt zonder vervanging.** Vandaag staat "Test keyboard" in de bovenbalk van elk scherm (`SlimApp.kt`). Het voorstel zet de testactie op Home en enkele detailpagina's. Maak het een vaste plek: een bovenbalkactie op **elk detailscherm dat typen of dicteren beïnvloedt** (Typen, Aanpassen, Formaat en positie, Talen en indelingen, Dicteergedrag, Stem). Home houdt de grote primaire knop. Zo hoeft de gebruiker niet te zoeken waar "proberen" deze keer staat.

**Updates.** De README beschrijft **Settings → Updates → Check now**. Het voorstel verplaatst Updates naar Over Lowkey. Het kleinere alternatief: houd **Updates** als eigen categorierij in Instellingen. De schakelaar `checkForUpdates` is een echte instelling, en README en gebruikersgids blijven dan kloppen. Over Lowkey linkt ernaar.

## 2. Plaats van de komende toetsenbordfuncties

Het voorstel houdt dit terecht bij de toetsenbord-track. Het ontwerp moet wel een besturingsmodel kiezen dat later niet hoeft te worden gesloopt.

**B4. Formaat en positie: één begrip, één bedieningselement.** Het prototype heeft vier radio's (Standaard / Eénhandig / Zwevend / Split) plus een aparte schakelaar "Automatisch splitsen". Dat levert tegenstrijdige toestanden op. Wat gebeurt er bij Split op een smal staand scherm? Wat wint bij Eenhandig met auto-split aan op een breed scherm? Ook ontbreekt de bestaande `KeyboardHeight`-instelling (Compact/Normal/Tall), terwijl het voorstel "huidige hoogte" belooft.

Voorgestelde structuur, ter bespreking met de toetsenbord-track:

```
Formaat en positie
  Op telefoonbreedte     ( ) Standaard  ( ) Eenhandig  ( ) Zwevend
      └ alleen bij Eenhandig: Zijde  Links | Rechts
  Op een breed scherm    ( ) Zelfde als telefoon (standaard)  ( ) Splitsen
  Hoogte                 Compact | Normaal | Hoog      ← bestaande instelling
  [Aanpassen op het toetsenbord]   ← opent testveld; slepen/grootte gebeurt ín het toetsenbord
  Herstel standaardpositie
  Nu actief: Standaard · telefoonbreedte   ← effectieve modus in tekst
```

- **Auto-split wordt een keuze per schermklasse.** Standaard is "Zelfde als telefoon". Splitsen blijft daarmee opt-in, zonder een losse schakelaar die met de modus kan botsen. De drempel, het gedrag bij vouwen of draaien en de opslag per houding blijven bij de toetsenbord-track.
- **Slepen en vrij formaat horen in het toetsenbord zelf**, met de doel-app zichtbaar. De companion-app kiest alleen de modus, toont de huidige waarden en biedt herstel. Het snelmenu kan wisselen tussen Eenhandig links en rechts en verwijst voor de rest naar deze pagina.
- **Eén maatbegrip per modus.** Leg vast of Eenhandig en Zwevend hun eigen grootte hebben of `KeyboardHeight` hergebruiken. Anders heeft de gebruiker straks twee "hoogtes".
- Spelling: **"Eénhandig" is fout.** Gebruik "Eenhandig" of "Met één hand".

**Cijferrij en snelmenu** horen onder Toetsenbord → Aanpassen, zoals voorgesteld. Autocorrect, suggesties en hoofdletters onder Typen. "Tonen met fysiek toetsenbord" verwijst in het voorstel naar een categorie "Gedrag" die niet in de IA-tabel staat. Zet het als eigen rij onder Toetsenbord ("Fysiek toetsenbord").

## 3. Stem, modellen, talen en woordenboek

**B2. Taal, indeling en model zijn drie dingen, en dicteertaal hoort bij het model.**

- `Settings.speechLanguage` staat standaard op `"en"`, niet op "volgt toetsenbord". Een gebruiker die een Nederlands toetsenbord kiest, dicteert dus Engels tot die dit zelf wijzigt. Het prototype toont "Nederlands · volgt toetsenbord" alsof dat vanzelf gaat.
- Dicteertaal is alleen kiesbaar bij meertalige modellen. Bij `*.en`-modellen en Distil-Large v3.5 ligt de taal vast op Engels (`ModelCatalog.kt`, en de tak "set by the model" in `SettingsScreen.kt`).
- **Voorstel:** dicteertaal heeft één ouder, **Stem**, en staat direct onder het actieve model. Talen en indelingen toont alleen een alleen-lezenregel: "Dicteren: Nederlands, ingesteld onder Stem ›". Dat is een expliciete tabwissel.
- In de optionele spraakonboarding: **bevestig de dicteertaal** ("Dicteer in Nederlands (zelfde als toetsenbord)") voordat het model wordt gedownload. Kiest de gebruiker een Engels-only model terwijl de toetsenbordtaal geen Engels is, dan volgt een duidelijke waarschuwing in tekst.

**De modellijst moet op taal sorteren, niet op familie.** `ModelCatalog.models` heeft voor alle acht modellen dezelfde `family` ("OpenAI Whisper"). De huidige groepering levert dus één kop op. De beslissende assen voor de gebruiker zijn **Meertalig versus Alleen Engels** en **omvang**. Voorstel:

- Bovenaan **Geïnstalleerd**, met het actieve model als tekststatus.
- Daaronder **Beschikbaar**, met eerst modellen die de huidige dicteertaal ondersteunen. "Alleen Engels" komt daaronder, ingeklapt als de dicteertaal niet Engels is.
- Per rij: naam, **taal**, **omvang** (tiny ≈ 103 MB, base ≈ 160 MB, small ≈ 375 MB, Turbo en Distil ≈ 1 GB, afgeleid uit de artefactgroottes in de catalogus) en één actie.

**"Geen snelheidslabels" is te streng.** Zonder enige vergelijking kan de gebruiker niet kiezen. De app zegt vandaag al: "Base is a good start; small and the 1 GB models are more accurate but slower." Voorstel: een **ordinale tekst zonder getallen**, afgeleid van wat de app al zegt, bijvoorbeeld "Kleiner · sneller" tot "Groter · nauwkeuriger, trager". Geen percentages of meetclaims.

**Onboarding: download direct.** De spraakstap eindigt nu op "Bekijk spraakmodellen". Het voorstel belooft een "expliciete download". Maak dat ook zo: **[Download Whisper base · ongeveer 160 MB]**, met een wifi-hint en daaronder "Ander model kiezen". De microfoontoestemming mag in dezelfde stap, want een IME kan zelf geen runtime-toestemming vragen. De huidige afhandeling van permanente weigering (link naar app-instellingen) blijft.

**Uitsluitende voice-instellingen.** Met `keyboardFirstDictation = true` (de standaard) zijn drie van de vijf geavanceerde opties uitgeschakeld. Een ingeklapt blok "Geavanceerd" vol uitgeschakelde schakelaars voelt kapot. Hetzelfde gedrag, beter gepresenteerd:

```
Hoe start dicteren?
  (•) Vanuit de toetsen (aanbevolen) — tik op de microfoon, daarna terug naar toetsen
  ( ) Aparte spraakmodus
        └ Automatisch starten · Laatste modus onthouden · Continu dicteren
Altijd: Live tekst · Automatisch stoppen na stilte
```

De afhankelijke opties staan zo alleen onder de keuze waar ze bij horen. Opgeslagen waarden blijven bewaard, en er verandert geen gedrag.

**Opslag en prestaties.** Prima als aparte pagina. "Model geladen houden" (`ModelRetention`) en de `ModelMemoryCard` horen daar. Herkomst (Hugging Face, vastgepinde commit, SHA-256) en licentie (MIT) staan in de modeldetails, zoals voorgesteld.

**B5. Woordenboek: toon alleen wat bestaat en wat echt geldt.**

- Import en export zijn in `DictionaryScreen.kt` uitgeschakelde knoppen met "Coming later". Het voorstel en het prototype maken er een Meer-menu met "kies een bestand" van. **Toon niets tot de functie bestaat.** Een menu dat naar een niet-bestaande functie leidt, is erger dan geen menu.
- `applyPhraseRules` staat standaard **uit**, maar het prototype toont "Spraakregels toepassen" aangevinkt. Ontwerp de uit-toestand als normaal geval: bovenaan de tab staat "Regels staan uit" met de schakelaar. Bij het toevoegen van de eerste regel verschijnt een eenmalige vraag: "Regels aanzetten?".
- `matchTermCase` (spelling van termen in dictaat herstellen) hoort bij de tab **Woorden**, `applyPhraseRules` bij **Spraakregels**. Dicteergedrag toont deze schakelaars niet nog eens, alleen een link. Eén plek per waarde.
- Direct verwijderen zonder herstel blijft een bekend risico. Undo als aparte gedragswijziging, zoals het voorstel zegt, is terecht.

**Privacy en licenties.** De huidige Home-tekst ("nothing you type or say leaves it") claimt te veel, want de app downloadt modellen en talen en controleert updates. Het voorstel corrigeert dat. Maak het concreet onder Over Lowkey → **Privacy en netwerk**, als korte lijst van wat het toestel verlaat en waarom:

- Modeldownloads van Hugging Face, met vastgepinde versies en checksums.
- Downloads van toetsenbordtalen.
- Een updatecheck bij GitHub, ongeveer eens per dag en bij openen, uit te zetten.
- Verder niets.

Licenties blijven via "About and licenses" bereikbaar. Als er fonts meekomen, moeten de OFL-teksten van die fonts in `assets/licenses/` en in `README.txt` (zie §4).

## 4. Familie met Signal, Android-thema's en toegankelijkheid

### Wat klopt met de bron

Het actuele `globals.css` bevestigt de tokens uit het voorstel: `--surround`/`--brand` `#17659C`, `--paper` `#FFFFFF`, `--ink` `#101B27`, `--quiet` `#526476`, `--soft` `#F6F9FC`, `--line` `#E6EDF3`, `--brand-tint` `#DCEDF8`, `--good` `#176B51`, `--bad` `#A93A2B`. Ook het principe "één wit werkvlak in de blauwe shell, geen geneste kaarten, status = tekst + stip" komt uit dat bestand. `Brand.tsx` bevat de vier logopaden. Die zijn identiek aan de vier `pathData` in Lowkeys `opstudio_logo_blue.xml`, en het buildscript controleert dat aantal. Het logo blijft dus onaangetast. Signal heeft `color-scheme: light` en geen donkere modus. Het voorstel noemt donker terecht een Lowkey-afleiding.

### Wat sterker kan: echte Signal-componenten gebruiken

Familie zit nu vooral in kleur, de koptekst en open rijen. Signal heeft twee herkenbare patronen die precies op Lowkey's kernmomenten passen (`today.module.css`):

- **`.start` + `.steps`**: een blok met een **blauwe linkerlijn van 3 px**, genummerde stappen in cirkels van 28 px, de eerste open stap gevuld blauw en haarlijnen ertussen. Dit is het natuurlijke patroon voor de onboarding (2 stappen) en voor herstel op Home ("Kies Lowkey opnieuw"). Het prototype gebruikt nu een kale genummerde lijst.
- **`.primary`**: één volgende actie met dezelfde linkerlijn, een Bricolage-kop van 24 px en de acties eronder. Dat is de vorm voor de Home-status ("Klaar om te typen" plus Toetsenbord proberen).
- **`.status`**: een stip van 7 px met een halo van 3 px en een toonklasse (`tone-good`, `tone-bad`, `tone-warn`). Urgentie maakt de tekst ook vet. De `.status` van het prototype heeft geen halo en geen vette fouttoestand. Neem beide over, zodat een fout niet alleen aan kleur herkenbaar is.

**Niet overnemen:** in `globals.css` krijgt `.brand-product` in de shell `color: var(--brand)` op een `--surround`-achtergrond met dezelfde waarde. Dat is een contrast van 1:1, dus onzichtbaar. Het witte "LOWKEY"-label van het prototype is de juiste afwijking. Neem die Signal-regel niet letterlijk over.

### B1. Typografie: de fonts dekken Lowkey's talen niet

Uit de `cmap` van beide bestanden: `instrument-sans-latin.woff2` heeft 208 codepoints, `bricolage-grotesque.woff2` 226. Beide missen onder meer **ł ś ż ą ę ć ń ź ř č š ž ů ě ő ű ğ ş İ**, alle **Cyrillische** en **Griekse** tekens, en →. Lowkey biedt Pools, Russisch en andere talen. `LanguagesScreen.kt` toont `language.name`, de eigen naam van de taal, en gebruikerstermen en testtekst kunnen in elk schrift staan. Android valt dan per teken terug op het systeemfont. Dat geeft woorden in gemengde fonts, zoals "Čeština" met een ander Č.

Daarnaast neemt `build-prototype.py` `public/brand/ATLAS-FONT-LICENSE.txt` op als licentie voor Instrument Sans. Dat bestand is de **Noto**-licentie. De juiste tekst is `public/fonts/INSTRUMENT-SANS-LICENSE.txt` (SIL OFL 1.1). Het gebouwde `font-license-1.txt` is daardoor fout.

**Klein alternatief, mijn voorkeur:** gebruik **Bricolage alleen voor het woordmerk en paginatitels**, in een build met minstens latin-ext. Gebruik voor bediening en alle gebruikersinhoud het **systeemfont**. Signal zelf beperkt Bricolage ook tot woordmerk en koppen (README "Gerichte vertaling"). De familie blijft herkenbaar via titels, kleur en ritme. Dit heeft vier voordelen: volledige dekking van alle schriften, het gedrag van Android-fontschaling blijft voorspelbaar, er komt één font minder in de APK van een "slim keyboard", en er is één licentie-notice minder. Kiest men toch voor Instrument Sans, dan alleen met een latin-ext-build en het systeemfont voor gebruikersinhoud (woordenboektermen, testveld, taalnamen).

### Android light/dark en ruimte

- **Brandkop van 72 px op elk scherm** kost veel verticale ruimte, zeker als het testpaneel met toetsenbord openstaat. Voorstel: de merkkop alleen op Home (en eventueel inklappend bij scrollen). Detailschermen krijgen een gewone bovenbalk met terugpijl, titel en testactie.
- **Werkvlak afronden:** rond alleen de bovenhoeken af. Laat de navigatiebalk tot de rand lopen boven de gebarennavigatie. Een afgeronde onderrand binnen een blauwe lijst van 6 px botst met gebarenhints en systeembalken.
- **Donker:** in het voorstel hebben shell en werkvlak allebei `#101E2B`, waardoor het "werkvlak in een lijst"-gevoel verdwijnt. Klein alternatief: een shell in `#0A1621` (bestaat al als `surfaceContainerLowest` in `Theme.kt`) met het werkvlak in `#101E2B`.
- **Bestaande `Theme.kt` versus Signal:** de huidige lichte `background` is `#F4F8FB` en de fout-kleur `#C2161D`. Het prototype gebruikt wit en `#A93A2B` van Signal. Beslis of de app de Signal-statuskleuren overneemt. Het toetsenbord gebruikt dezelfde merkpalet, dus houd ze in sync of leg het verschil bewust vast.

### Contrast (berekend, WCAG 2.x)

| Paar | Ratio | Oordeel |
| --- | --- | --- |
| `#526476` op wit / op `#F6F9FC` | 6,10 / 5,77 | AA |
| `#17659C` op wit / op `#DCEDF8` (actieve tab) | 6,22 / 5,18 | AA |
| `#176B51` en `#A93A2B` op wit | 6,45 / 6,31 | AA |
| Donker: `#A7BCCD` op `#101E2B` / `#182A3A` | 8,63 / 7,49 | AA |
| Donker: `#83C9F4` op `#1F4A6B` (actieve tab) | 5,17 | AA |
| Haarlijn `#E6EDF3` op wit | 1,18 | Alleen decoratief. Rijen moeten herkenbaar blijven via chevron of tekst, niet via de lijn alleen |

### Toegankelijkheid in het prototype

- **Radio's en checkboxen zonder `color-scheme`.** In een donkere hostomgeving rendert de lichte app niet-geselecteerde radio's als **gevulde donkere schijven** (snapshot Formaat en positie). Die lijken op een geselecteerde toestand. Zet `color-scheme: light` op `.app` en `dark` op `.app.dark`. Op Android geldt de les ook: geef geselecteerd en niet-geselecteerd een verschillende vorm, niet alleen een andere kleur.
- `.back`-knoppen zijn 36 px hoog. Het voorstel eist 48 dp, dus ook in het prototype 48 aanhouden.
- Tablabels bij 200 % tekst en op 320 dp: "Woordenboek" breekt al bij 100 %. In de Engelse app is het "Dictionary" (zie §5). Toets de echte labels met grote tekst. Een `NavigationBar` kapt af en breekt niet midden in een woord.

## 5. Concrete revisies

### Voor root, vóór de handoff

1. **Fonts (B1):** kies tussen het alternatief (Bricolage alleen voor titels, systeemfont voor de rest) en Instrument Sans met latin-ext. Corrigeer in `build-prototype.py` de licentie naar `public/fonts/INSTRUMENT-SANS-LICENSE.txt`. Vermeld in het voorstel dat de huidige subsets alleen Latin-1 dekken.
2. **Dicteertaal (B2):** één ouder (Stem). Benoem de standaardwaarde `en` en ontwerp de bevestiging in de spraakonboarding. Groepeer de modellijst op Meertalig en Alleen Engels, en sta ordinale grootte/snelheid toe op basis van bestaande app-tekst.
3. **Eén ouder per scherm (B3):** werk de deep-linktabel hierboven in het voorstel op. Verwijder kruislings gekoppelde detailrijen of maak er een expliciete tabwissel van.
4. **Formaat en positie (B4):** vervang Split-radio plus auto-split-schakelaar door "Op een breed scherm: Zelfde als telefoon / Splitsen". Voeg de bestaande hoogte toe. Verplaats slepen naar het toetsenbord. Spelling: "Eenhandig".
5. **Woordenboek (B5):** geen Meer-menu voor import en export zolang die niet bestaan. Uit-toestand van spraakregels als standaard. Elke regel- of spellingschakelaar op één plek.
6. **Dicteergedrag:** keuze "Hoe start dicteren?" in plaats van een schakelaar plus uitgeschakelde geavanceerde opties.
7. **Onboarding:** volledig scherm zonder tabbalk, met Overslaan. De spraakstap downloadt direct.
8. **Signal-componenten:** gebruik `.start`/`.steps` voor onboarding en herstel, `.primary` voor de Home-status en `.status` met halo en vette fouttoestand.
9. **Prototypefouten:** `color-scheme` op `.app`; de rij "Je woorden" van Home weg; de rij Toetsenbord naar Talen en indelingen; `.back` 48 px hoog; de checkbox voor spraakregels standaard uit.
10. **Updates:** als eigen rij in Instellingen houden, of README en gebruikersgids in dezelfde wijziging bijwerken.

### Taal van de productcopy

De app is **Engelstalig**: alle labels in de Kotlin-bron, en de README beschrijft Engelse menupaden. Het voorstel noemt Nederlands terecht de ontwerptaal. De labelbeslissingen moeten echter in de producttaal worden genomen: **Home · Voice · Dictionary · Settings**, "Ready to type", "Size and position". De Nederlandse voorbeelden zijn daarom geen goede toets voor lengte, afbreking of grote tekst. Voeg in de handoff een Engelse labeltabel toe.

## 6. Keuzes voor gebruikersbespreking

1. **Fonts:** Bricolage alleen voor titels met het systeemfont voor de rest (aanbevolen), of Instrument Sans voor alle bediening met latin-ext-builds en extra notices.
2. **Auto-split:** akkoord met "Op een breed scherm: Zelfde als telefoon / Splitsen" als manier om opt-in te maken? De definitieve drempel en het gedrag bij vouwen blijven bij de toetsenbord-track.
3. **Dicteertaal bij setup:** moet het kiezen van een niet-Engels toetsenbord in de onboarding de dicteertaal voorstellen als "zelfde als toetsenbord"? Dat wijzigt de huidige standaard `en` alleen na bevestiging.
4. **Updates:** eigen categorie in Instellingen (README blijft kloppen) of onder Over Lowkey (README aanpassen).
5. **Statuskleuren:** Signal-waarden overnemen (`#A93A2B`/`#176B51`) of de huidige `Theme.kt`-waarden behouden, met gevolgen voor de toetsenbordpalet.
6. **Merkkop:** alleen op Home of op elk scherm.
7. **Tablabel:** "Voice" (Stem) bevestigen in de Engelse UI.

## Beperkingen

- **Bronaudit, geen gebruikersonderzoek.** Er is geen emulator, telefoon, TalkBack of Compose-rendering gebruikt. De oordelen over Android-gedrag (navigatiebalk bij grote tekst, gebarennavigatie, fontfallback) zijn verwachtingen op basis van de bron en platformconventies, geen waarnemingen.
- Visuele controle alleen op het HTML-prototype (320 en 390 px, licht en donker hostthema). Het prototype bewijst geen app-gedrag.
- De Signal-referentie is een lokale werkkopie met ongecommitte wijzigingen. Er is niets geverifieerd over de gepubliceerde Signal-omgeving. Oudere groen/mosterd-documenten zijn bewust niet als referentie gebruikt.
- De toetsenbord-track (positie, split, snelmenu, cijferrij) is niet gelezen of getest. §2 is een voorstel voor het bedieningsmodel, geen specificatie van gedrag.
- Modelgroottes zijn afgerond uit de artefactgroottes in de catalogus. Snelheid en nauwkeurigheid zijn niet gemeten.
- Het contrastprobleem in de buitenste demo-werkbalk valt buiten deze review.
