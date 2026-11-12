# Ontwerpverificatie

De meest recente prototypeversie, inclusief uitgewerkte reviewpunten en startup-motion, staat beschreven in [v3-uitwerking.md](v3-uitwerking.md). Onderstaande eerdere metingen blijven als historie behouden.

12 november 2026. Dit betreft alleen het ontwerpvoorbeeld en de ongewijzigde Android-basis. Geen productimplementatie of emulatoracceptatie.

## Bronnen

- Signal opgezocht via het geregistreerde project en lokaal alleen gelezen. De actuele blauwe componenten onderscheiden van oudere groen-mosterdvoorstellen.
- `signal-sources.json` bevat SHA-256 van de gelezen actuele referentiebestanden, met relatieve paden. Dit legt de gelezen werkbestanden vast, inclusief reeds aanwezige ongecommitte wijzigingen.
- Het bestaande zelfstandige Signal-voorbeeld `docs/branding/new-opstudio/today.html` op 390 px gerenderd en bekeken. Een ontwerpvoorbeeld, geen live-app-audit. Geen screenshot daarvan overgenomen in Lowkey.
- Lowkey onderzocht via Compose-bron. Geen Android-schermvisuele of TalkBack-claims.

## Uitgevoerde controles

- `testDebugUnitTest`: **308 tests, 42 suites, 0 fouten, 0 overgeslagen**. JDK 21. Bestaande Gradle-deprecatiewaarschuwing; build geslaagd. De 327 tests uit de andere keyboard-worktree horen niet bij deze basis.
- Prototype gebouwd met `build-prototype.py`; fonts lokaal ingebed, vier logopaden rechtstreeks uit bestaande drawable. Geen nieuwe Android-assets of dependencies.
- `html_preview`: eerste versie Home, Instellingen, Stem en Formaat en positie (donker) visueel bekeken op 390 px; na Opus-correcties Home, Instellingen, onboarding en Formaat en positie (donker) opnieuw bekeken op 320 px.
- Gescripte HTML-controle na Opus-correcties op **320, 390 en 728 px: 116 assertions per breedte geslaagd**. Die controleren 23 routes in licht en donker op horizontale overloop, plus onboarding/spraak overslaan, testen openen/sluiten/focusherstel, navigatie, conditionele links/rechts, reset, zoeken/leeg resultaat, voorbeeldwoord, spraakregels en download zonder modelactivatie. Toegevoegd: geen onboardingtabs, eenduidige ouder-tab voor dicteertaal, regels standaard uit, geen importactie, hoogte aanwezig en geen dubbele splitbediening.
- Eén aanvankelijk gevonden focusprobleem bij gescripte activatie van het testpaneel gecorrigeerd: de opener krijgt expliciet focus voordat het native dialoogvenster opent. Daarna alle controles opnieuw geslaagd.
- Dit zijn gescripte controles in de HTML-renderer, geen handmatige browser- of Android-gebruikstest. De getoonde download, regels en instellingen blijven fictieve demonstratiestaten.
- Na de review `testDebugUnitTest` opnieuw aangeroepen: succesvol, alle taken up-to-date (Android-code ongewijzigd). Het eerdere volledige resultaat blijft 308 geslaagde tests.
- Lekcontrole op alle nieuwe ontwerpteksten én bestaande tracked files geslaagd, met private denylist. `git diff --check` schoon. Alleen nieuwe bestanden onder `docs/design/lowkey-ux/`; media en scenario's onder gitignored `artifacts/`.
- Generator syntactisch gecontroleerd; het ingebedde voorbeeld bevat geen externe URL-verzoeken of onopgeloste templatevelden. Instrument Sans-licentietekst bytegelijk aan de juiste Signal-fontlicentie.

De lokale controlescriptbron en scenario-HTML staan onder gitignored `artifacts/lowkey-ux/`. Het publieke bronbestand `prototype.html` en de generator zijn voldoende om het concept opnieuw te bouwen met een beschikbare Signal-checkout. De juiste externe fontlicenties worden alleen naar die artifactmap gekopieerd. Het prototype gebruikt nu systeemtekst voor bediening; referentiefonts blijven lokaal ingebed voor koppen/vergelijking. Voor toekomstige bundeling in Android zijn ruimere fontdekking, notices en afzonderlijke controles vereist.

## Nog nodig vóór productacceptatie

- Keuze van tablabel en zichtbaarheid van de merkstrook bespreken.
- Echte Android-onboarding, toestemmingsweigering, terugkeer uit systeeminstellingen en ontbrekend model verifiëren.
- Nieuw navigatiegedrag/deep links implementeren met regressies; huidige numerieke scrollfocus vervangen zonder oude ingangen te breken.
- TalkBack, vergroting tot 200%, donkere modus, IME-insets en vouwen/draaien testen op een vrijgegeven emulator.
- Integratie met de keyboard-track pas apart na diens acceptatie; geen afgeleide aannames over persistentie of auto-split vastleggen als feit.
