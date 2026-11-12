# Behoud van instellingen en taalstrategie

> Historische ontwerpaudit. De gebruiker koos daarna Engels voor de app. De native uitvoering en actuele dekking staan in [native-uitvoering.md](native-uitvoering.md); de onderstaande prototypebeperkingen zijn geen inventaris van de native uitvoering.

12 november 2026. Aanvulling op het ontwerp, na de vraag of alle instellingen behouden en duidelijk zijn en of de app meertalig moet worden. Geen wijziging aan productcode of prototype in deze audit.

## Conclusie

De bestaande Android-instellingen zijn niet verwijderd of gewijzigd. Maar het prototype is **niet volledig qua instellingen**: het demonstreert vooral de structuur en hoofdscenario's. Enkele pagina's bevatten alleen uitleg, enkele keuzes zijn versimpeld. Alle instellingen moeten vóór productimplementatie expliciet een plek én dezelfde betekenis houden. Visuele rust mag niet worden bereikt door mogelijkheden weg te laten.

De app-interface is momenteel grotendeels hardcoded Engels; het prototype is Nederlands. Dat is geen lokalisatievoorziening. Toetsenbordtaal, dicteertaal en app-taal zijn drie onafhankelijke begrippen.

## Inventaris van Settings

Bron: `app/src/main/java/com/rheerkens/androidkeyboard/settings/Settings.kt` en `SettingsStore.kt`; bestaande schermen onder `companion/screens/`. Er zijn 26 opgeslagen velden in `Settings`: onderstaande tabel bevat ze allemaal. Dit is een inventaris van die klasse; modellen, taalpakketten en persoonlijke woorden hebben daarnaast eigen opslag en acties.

**Demo** = als tijdelijke instelling bedienbaar, geen echte opslag. **Deels** = versimpeld voorbeeld. **Open** = wel een ontwerpbestemming, nog geen volledige bediening. **Intern** = onthouden gebruikstoestand, geen nieuwe zichtbare schakelaar nodig.

| Bestaand veld | Betekenis / nieuwe plek | Prototype |
| --- | --- | --- |
| `keyboardHeight` | Toetsenbord → Formaat en positie; Compact / Normaal / Hoog | Demo |
| `vibrateOnKeyPress` | Toetsenbord → Aanpassen; toetsvibratie | Open |
| `autoCapitalize` | Toetsenbord → Typen; automatische hoofdletters | Open |
| `autocorrect` | Toetsenbord → Typen; autocorrectie | Open |
| `suggestionStrip` | Toetsenbord → Typen; suggestiebalk | Open |
| `enabledKeyboards` | Talen en indelingen; actieve taal-/layoutcombinaties; minimaal één | Deels: alleen Nederlands met enkele voorbeeldlayouts |
| `currentKeyboard` | Talen en indelingen; huidige layout, ook wisselbaar in IME | Deels: geen volledige taalwissel in de demo |
| `showWithPhysicalKeyboard` | Toetsenbord → Fysiek toetsenbord; tonen bij hardwaretoetsenbord | Open |
| `recentScreenshots` | Klembord en afbeeldingen; recente screenshots + toestemmingsstatus | Open |
| `keyboardFirstDictation` | Stem → Dicteergedrag; starten vanuit toetsen of aparte spraakmodus | Demo |
| `autoStartVoice` | Stem → Dicteergedrag; automatisch opnemen bij openen van spraakmodus | Demo, alleen bedienbaar bij aparte spraakmodus |
| `rememberLastMode` | Stem → Dicteergedrag; laatst gebruikte invoermodus onthouden | Demo, zelfde afhankelijkheid |
| `liveTextInEditor` | Stem → Dicteergedrag; voorlopige tekst tijdens spreken | Demo |
| `autoStop` | Stem → Dicteergedrag; automatisch stoppen na stilte | Deels: demo zegt Uit/Kort/Lang; moet exact Uit / 2 seconden / 4 seconden worden |
| `continuousDictation` | Stem → Dicteergedrag; opnieuw opnemen na invoegen | Demo, alleen bedienbaar bij aparte spraakmodus |
| `applyPhraseRules` | Woordenboek → Spraakregels; vervangingen na stoppen | Demo; standaard uit |
| `matchTermCase` | Woordenboek → Mijn woorden; exacte spelling bij dicteren | Demo; standaard aan |
| `speechLanguage` | Stem → Dicteertaal; voorkeur voor meertalig model | Deels: demo toont Engels/Nederlands/volgen; auto-detectie en volledige modellijst ontbreken |
| `modelRetention` | Stem → Opslag en prestaties; geladen model in RAM vasthouden | Open: zes echte keuzes ontbreken nog als bediening |
| `theme` | Weergave; Systeem / Licht / Donker | Deels: demo-werkbalk wisselt licht/donker, geen productinstelling |
| `checkForUpdates` | Updates; automatisch controleren aan/uit | Open: tekst aanwezig, geen schakelaar |
| `loupeCorners` | Klembord en afbeeldingen → Afbeeldingseditor; positie van de loep | Open: Boven / Links / Rechts nog niet bedienbaar |
| `audioInput` | Problemen oplossen; microfoon versus debug-testclips | Open: alleen tonen waar testclips beschikbaar zijn, niet als normale productieoptie |
| `lastMode` | Onthouden toetsen-/spraakmodus | Intern; behouden, geen Home-tegel nodig |
| `minimizedWithPhysicalKeyboard` | Onthouden of de balk geminimaliseerd is met hardwaretoetsenbord | Intern; behouden |
| `minimizedWithoutPhysicalKeyboard` | Onthouden of de balk geminimaliseerd is zonder hardwaretoetsenbord | Intern; behouden |

`modelRetention` moet alle bestaande waarden behouden: direct vrijgeven, 30 seconden, 2 minuten, 5 minuten, 15 minuten en tot het app-proces sluit. Geheugendruk kan eerder vrijgeven; dat moet leesbaar blijven.

Naast de tabel behouden: actieve modelkeuze, alle modeldownloads en fout/annuleer/verwijderacties; volledige taalpakketlijst en vijf Latijnse indelingen (QWERTY, AZERTY, QWERTZ, Dvorak, Colemak); persoonlijke termen en regels; instellingen-/toestemmingsingangen, updatebediening, diagnostiek en licenties. Import/export is in de huidige app uitgeschakeld en wordt niet als bestaande werkende functie meegeteld.

De nieuwe keyboardfuncties staan alleen als ontwerpbestemming in deze worktree. Hun definitieve opgeslagen velden en gedrag komen uit de aparte keyboard-track en moeten bij integratie aan deze inventaris worden toegevoegd. Deze audit las of veranderde die track niet.

## Zijn de instellingen duidelijk genoeg?

Nog niet aantoonbaar voor elke instelling. De nieuwe categorieën zijn helderder, maar het prototype heeft bijvoorbeeld een vage Kort/Lang-keuze en alleen verklarende tekst op enkele instellingenpagina's. Dat moet worden afgemaakt vóór een claim van volledige vervanging.

Acceptatieregel per instelling:

1. Een herkenbare taaknaam, actuele waarde en bediening.
2. Eén korte uitleg over het gevolg, zonder implementatietaal.
3. Exacte waarden en eenheden waar die bestaan (2 seconden, geen Kort).
4. Afhankelijkheden verklaren; voorkeuren niet wissen als bediening tijdelijk niet beschikbaar is.
5. Eén vaste plek, met gerichte links vanaf elders.
6. Dezelfde voorkeurssleutel, opgeslagen waarde en betekenis bij migratie; een andere tekst mag instellingen niet terugzetten.

Dit vereist later een echte gebruikscontrole; agentscores zijn geen bewijs dat iedere gebruiker iedere instelling begrijpt.

## Drie soorten taal

| Begrip | Voorbeeld | Huidige stand |
| --- | --- | --- |
| App-taal | Knoppen en instellingen in het Frans | Android-app grotendeels Engels, prototype Nederlands; geen app-taalkeuze of vertaalcatalogus |
| Toetsenbordtaal | Franse woordenlijst en AZERTY | Afzonderlijke taalpakketten en layouts, los van app-taal |
| Dicteertaal | Nederlandse spraak naar tekst | Afhankelijk van gekozen model; voorkeur in `speechLanguage`, inclusief volgen/auto-detectie |

Voorbeeld: app in het Engels, Frans toetsenbord en Nederlands dicteren moet mogelijk blijven. Een wijziging van de app-taal mag niet stilzwijgend de toetsenbordlayout of dicteertaal aanpassen.

De huidige toetsenbordcatalogus bevat 21 varianten, waaronder de zes genoemde talen. Engels heeft US/UK en Portugees heeft Brazilië/Portugal. De meertalige modelkeuzelijst bevat ook Engels, Nederlands, Frans, Duits, Spaans en Portugees. Dat bevestigt beschikbaarheid in de bron, geen gelijkwaardige kwaliteit of volledige ondersteuning van alle wereldtalen.

## Voorgestelde taalstrategie — nog geen besluit of implementatie

**Meertaligheid voorbereiden, Engels als volledige basistaal en fallback.** Voor een breed publiek is Engels-only geen ideale eindtoestand voor onboarding en instellingen. Begin met kwalitatief nagekeken Engels, Nederlands, Frans, Duits, Spaans en Portugees; breid daarna gericht uit. Leg bij Portugees vast hoe Portugal en Brazilië worden behandeld. Geen automatische vertalingen van alle wereldtalen als afgeronde ondersteuning presenteren.

- App-taal volgt standaard het toestel, met een aparte keuze **App-taal → Systeem / beschikbare talen** onder de app-instellingen. Toon taalnamen in hun eigen taal.
- De app-taal staat los van Typen en Dicteren. Geen alfabetisch megamenu waarin die drie keuzes door elkaar lopen.
- Bronteksten centraliseren in Android-resources; een volledige Engelse set als fallback. Ook foutmeldingen, toestemmingsteksten, toegankelijkheidslabels en meervoudsvormen meenemen.
- Vertalingen als volledige gebruikerservaring controleren, niet alleen knoppen. Geen halfvertaalde menu's bewust releasen.
- Layouts toetsen met langere teksten, 200% tekst, complexe schriften en rechts-naar-linksrichting. Bricolage-subsets niet voor alle schriften aannemen; gebruikersinhoud via passende systeemfonts.
- Eerst taalneutrale datavelden en Engelse basisteksten, daarna de gekozen vertalingen. Dit hoeft de productversie niet voor elke niet-ondersteunde taal te blokkeren: Engels blijft bereikbaar.

## Officiële technische bronnen

Android beschrijft gelokaliseerde resources en de noodzaak van een complete standaardset in [Localize your app](https://developer.android.com/guide/topics/resources/localization). Voor app-specifieke taalkeuze bestaan vanaf Android 13 systeeminstellingen en API's; compatibiliteit met oudere versies vraagt een passende integratie met de bestaande Activity/Compose-opzet: [Per-app language preferences](https://developer.android.com/guide/topics/resources/app-languages). Tests met langere tekst en RTL kunnen worden voorbereid met [pseudolocales](https://developer.android.com/guide/topics/resources/pseudolocales).

De huidige `MainActivity` is een `ComponentActivity`, geen `AppCompatActivity`. Compatibele taalkeuze voor oudere Android-versies is dus een implementatiekeuze die onderzocht moet worden, niet een reeds aanwezige mogelijkheid. Geen nieuwe dependency of Activity-wijziging gedaan in deze audit.
