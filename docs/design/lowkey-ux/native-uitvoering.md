# Native uitvoering van het companion-ontwerp

> Release-integratie: de uitvoering is voor 0.7.0 samengevoegd met main 0.6.1. De onderstaande inventaris van 26 velden beschrijft de eerste UI-preview. De vier inmiddels toegevoegde velden `placement`, `numberRow`, `bottomKey` en `recentEmoji` blijven behouden; de bijbehorende bediening staat onder Keyboard. Hoogte en positie worden met de actuele resize-bediening aangepast, met behoud van de oude hoogtewaarde als migratiebron. De nieuwe fototoegangsafhandeling, standaardwaarde en optionele Home-stap blijven eveneens behouden. Zie [0.7.0](../../releases/0.7.0.md).

De gebruiker heeft implementatie en een afzonderlijke test-APK gevraagd. De app-interface blijft voorlopig Engels. Toetsenbordtalen, dicteertaal en gebruikersinhoud blijven onafhankelijk daarvan. De keyboard-track wordt niet overgenomen.

## Wat is uitgevoerd

- Vier vaste tabs: Home, Voice, Dictionary, Settings. Detailpagina's en bestaande IME-snelkoppelingen hebben vaste terugpaden.
- Home heeft een hoofdactie met echte Android-status. Toetsenbord activeren en selecteren zijn de enige verplichte setupstappen. Spraak is optioneel. Setup overslaan blijft binnen de sessie behouden bij tabwissels.
- Settings is een categorie-index. Waarden en toelichting staan bij de bediening; afhankelijke spraakvoorkeuren blijven zichtbaar en opgeslagen.
- Voice toont de volledige modelcatalogus, microfoontoestemming, voortgang, annuleren, opnieuw proberen, selectie, details en verwijderen. Een actief model is beschermd tegen verwijderen.
- Dictionary heeft zoeken, toevoegen, bewerken, verwijderen met bevestiging, juiste meervouden en de afzonderlijke globale schakelaar voor spraakregels.
- Signal's blauwe kleur, origineel OpStudio-logo en lokaal meegeleverd Bricolage voor koppen. Bedieningen en gebruikersinhoud gebruiken het systeemfont. De bestaande IME-kleuren blijven behouden.
- Een eenmalige, niet-blokkerende overgang van 500 ms bij het openen. Deze volgt Androids animatieschaal. Geen extra splash-scherm of wachttijd; de geometrie van het logo blijft gelijk.
- Native selectie- en schakelaarsemantiek. Instellingskeuzes staan onder elkaar; modelacties lopen om en de layoutgrid krijgt minder kolommen bij grotere tekst. Vanaf 150% tekst staan de vier navigatieknoppen in twee rijen zodat labels niet midden in woorden afbreken.

## Behoud van alle 26 Settings-velden

`Settings.kt` en `SettingsStore.kt` zijn niet gewijzigd. Er is geen voorkeurmigratie of reset.

| Velden | Bereikbaar via / gedrag |
| --- | --- |
| `keyboardHeight`, `showWithPhysicalKeyboard` | Settings → Keyboard |
| `vibrateOnKeyPress` | Settings → Keyboard → Key feedback |
| `autoCapitalize`, `autocorrect`, `suggestionStrip` | Settings → Keyboard → Typing |
| `enabledKeyboards` | Settings → Languages & layouts → Keyboard layouts; minimaal één bruikbare layout |
| `currentKeyboard` | Bestaande taalkeuze via wereldbol/spatiebalk; huidige keuze in de app zichtbaar |
| `recentScreenshots`, `loupeCorners` | Settings → Clipboard & images |
| `keyboardFirstDictation`, `autoStartVoice`, `rememberLastMode`, `continuousDictation`, `liveTextInEditor`, `autoStop`, `speechLanguage` | Voice → Dictation settings |
| `applyPhraseRules`, `matchTermCase` | Dictionary → Speech rules / My words |
| `modelRetention` | Voice → Storage & performance; alle zes bestaande keuzes |
| `theme` | Settings → Appearance |
| `checkForUpdates` | Settings → Updates |
| `audioInput` | Settings → Diagnostics; testclips alleen in debug |
| `lastMode`, `minimizedWithPhysicalKeyboard`, `minimizedWithoutPhysicalKeyboard` | Bestaande onthouden IME-toestand; geen extra schakelaar |

Ook de eigen stores voor modellen, taalpakketten, persoonlijke woorden en regels blijven behouden. Geen wijziging aan hun bestandsformaat, catalogus of sleutels. Import/export was niet beschikbaar en wordt niet als werkende actie getoond.

## Bewuste verschillen met het HTML-prototype

- De bestaande modelstore activeert een model zodra de download klaar is. Dat gedrag blijft behouden: de actie heet **Download & use** en de uitleg vermeldt dit. Een reeds gedownload model kiest men met **Use**. Het prototype liet downloaden en activeren volledig los van elkaar zien.
- Native Material-bediening vervangt de HTML-elementen. De rustige hiërarchie en merkstijl worden overgenomen, geen pixelkopie van webcontrols.
- De browseranimatie bevat een luisterring; de Android-uitvoering gebruikt alleen een korte overgang van merk en werkvlak om een tweede splash te vermijden.
- Instellingen voor de toekomstige keyboard-modi komen pas bij integratie van die afzonderlijke track. De volledige huidige instellingen zijn hier beschikbaar.

## Test-APK en validatie

De Gradle-optie `-PuiPreview=true` maakt een afzonderlijke debug-app met naam **Lowkey UI Preview**. Versienaam en versiecode veranderen niet. Deze preview deelt geen gegevens met de normale app, en dient niet als update van een release-installatie. Gewone builds behouden de bestaande applicatie-identiteit.

Alleen het officiële applicatiepakket gebruikt productie-updates. Afzonderlijke demo-/preview-installaties tonen een uitleg voor handmatige APK-updates en kunnen geen productie-APK downloaden of installeren. Dit is tijdens de emulatortest toegevoegd nadat de preview een onterechte productie-updatemelding liet zien. De normale app behoudt de bestaande updatebediening en opgeslagen voorkeur.

De onderstaande resultaten zijn daadwerkelijk uitgevoerd. De schermafbeeldingen blijven in de gitignored map `artifacts/native-ui/`.

- `testDebugUnitTest`: 320 tests, nul fouten, nul overgeslagen. Inclusief controle dat alleen het officiële pakket productie-updates accepteert.
- `assembleDebug` en `lintDebug` met `-PuiPreview=true`: geslaagd. Lint heeft bestaande waarschuwingen; geen fouten.
- APK-handtekening geverifieerd; pakket `com.rheerkens.androidkeyboard.uipreview`, versie 0.5.8 (49).
- Lekcontrole van zowel gevolgde als nieuwe publieke bestanden en alle APK-inhoud: geen bevindingen, inclusief private denylist.
- De preview-APK is als expliciete testversie via de geautoriseerde bestandsdeling verstuurd. Dit is geen release of productie-update.

### Praktijkcontrole op een afzonderlijke Android 15-emulator

- Getest op 411 dp en 320 dp schermbreedte; normale en 200% systeemtekst; licht en donker. Langere koppen, volledige navigatielabels, layoutkeuzes en modelacties blijven leesbaar. Schermen scrollen bij grote tekst.
- Setup overslaan geeft een eerlijke status. Activeren en kiezen via Android werken; Home toont daarna **Ready to type**. Het testveld kreeg tekst via echte Lowkey-toetsaanslagen.
- De vier tabs en terugpaden zijn gebruikt. Voice → Dictation en Storage hebben Voice als vaste ouder. Talen en de vijf layoutopties blijven bereikbaar; de laatste actieve layout blijft beschermd.
- Hoogte **Tall**, autocorrectie uit en het thema bleven bewaard na herstart en het installeren van de bijgewerkte preview. De zes bestaande keuzes voor modelretentie zijn zichtbaar.
- Woorden toevoegen, zoeken, bewerken en verwijderen met bevestiging zijn uitgevoerd. De globale schakelaar voor spraakregels is aan en uit gezet; individuele regels behouden hun keuze en tonen wanneer ze inactief zijn.
- De productie-updatemelding is na de correctie verdwenen. De preview legt uit dat test-APK's handmatig worden geïnstalleerd.
- Geen Lowkey-crash waargenomen. De crashbuffer bevatte een fout van het hulpmiddel voor de apparaatweergave, niet van Lowkey. De screenshottool werkte niet; na expliciete toestemming zijn de controles en captures via de host uitgevoerd. De gedeelde emulator en fysieke telefoon zijn niet gebruikt voor tests.
- Niet uitgevoerd: TalkBack met spraak, echte microfoontranscriptie/modeldownloads, alle talen en een fysieke vouwtelefoon. Er wordt geen volledige toegankelijkheidscertificering of spraakkwaliteitsclaim gemaakt.

## Korte gebruikstest

1. Open **Lowkey UI Preview**. Controleer de korte opening en kies eventueel **Set up later**. Home moet dan de werkelijke toetsenbordstatus tonen; Voice blijft optioneel.
2. Open alle vier de tabs. Ga in Settings naar Keyboard, Typing en Key feedback en controleer telkens de terugknop.
3. Kies een andere hoogte en thema. Sluit de app, open opnieuw en controleer of de keuzes blijven staan. De afzonderlijke preview begint met eigen standaardinstellingen.
4. Voeg in Dictionary een woord toe, zoek het op, bewerk het en verwijder het. Controleer een spraakregel met de globale schakelaar uit en daarna aan.
5. Bekijk Voice en Dictation settings. De voorkeuren voor aparte spraakmodus blijven zichtbaar wanneer **Dictate from the keys** aan staat. Een download is expliciet ook een modelkeuze.
6. Herhaal de navigatie met grote systeemtekst en licht/donker. Controleer vooral lange titels, knoppen, taal-/layoutkeuzes en dialoogvelden.

De nieuwe formaat-, eenhandige, zwevende en split-functies van de parallelle keyboard-track zitten niet in deze APK. Die worden afzonderlijk geïntegreerd nadat beide tracks getest zijn.
