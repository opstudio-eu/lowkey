# Onafhankelijke eindcontrole Lowkey v3 (Opus)

12 november 2026. Gelezen: AGENTS.md, README, REFERENCES, `v3-uitwerking.md`, `prototype.html/css/js`, `startup.js/css` en `startup-notes.md`. Het gegenereerde `artifacts/lowkey-ux/prototype.html` en de snapshots `large-v3` en `words-v3` heb ik met html_preview bekeken op 320 en 390 px. Daarnaast heb ik met een eigen, tijdelijke kopie met een synchrone testscript de acties echt uitgevoerd. Die kopie gebruikt een nagebootste timer en staat niet in de repository. Er zijn geen bronbestanden gewijzigd.

## Oordeel

**Geen blocker.** De kern klopt. Setup overslaan is eerlijk: Home toont dan "Stel Lowkey in · Nog niet geactiveerd" en Spraak "Nog niet ingesteld · optioneel", zonder rode fout. De ouderroutes zijn vast. Een download activeert niet vanzelf en het actieve model is beschermd. Woorden toevoegen, bewerken, verwijderen en ongedaan maken werkt. Spraakregels staan zichtbaar uit. Formaat en positie heeft één testactie. De startup speelt één keer, blokkeert geen invoer, rondt af bij invoer of navigatie en slaat over bij reduced motion. Er blijven drie reproduceerbare punten met hoge prioriteit over. Ze gaan allemaal over toegankelijkheid of de blijvende onderbalk.

## High

1. **Focus verdwijnt elke 800 ms tijdens een download.** Ga naar Stem, start bij Whisper small een download en zet de focus op "Dicteergedrag". Na één timertick staat de focus op `BODY`. `startDownload` roept `render(false)` aan (`prototype.js:152`), en dat vervangt de hele pagina. Een toetsenbord- of TalkBack-gebruiker raakt zo zijn plek kwijt, en dat herhaalt zich tot de download klaar is.
   *Minimale fix:* werk bij een tick alleen `progress` en het percentage bij, met `querySelector('[data-model=…] progress')`. Render alleen bij een statuswissel, en zet dan de focus terug op de bijbehorende knop in het modelblok, bijvoorbeeld "Gebruik".

2. **Melding bedekt de onderbalk bij 200% tekst.** Gemeten op 320 px met Tekst 200%: verwijder in Woordenboek een woord. De onderkant van de melding ligt op 851 px en de bovenkant van de navigatie op 797 px. Ongeveer 54 px van de navigatie, met alle iconen, valt onder de melding, en die blijft staan tot Sluiten. Oorzaak: `.notice{bottom:100px}` is een vaste afstand, maar de navigatie is bij 200% ongeveer 150 px hoog.
   *Minimale fix:* zet `#notice` in `.surface` direct vóór `<nav>` als gewoon flexblok (niet absoluut), of reken `bottom` uit met `nav.offsetHeight + 12`.

3. **Focusring heeft te weinig contrast in licht thema.** `outline:3px solid #83c9f4` (`prototype.css:14`) op het witte werkvlak geeft ongeveer 1,8:1. Voor een focusindicator is minstens 3:1 nodig. Alleen `.preference` gebruikt het donkere blauw.
   *Minimale fix:* gebruik in `.surface` en in `dialog` als lichte variant `outline-color:var(--blue)` (#17659c). Houd #83c9f4 voor het donkere thema en het blauwe merkvlak.

## Medium

4. **Dicteertaal wordt stil overschreven.** Kies Nederlands, activeer Whisper tiny.en en daarna weer Whisper base. De dicteertaal is dan "Engels". `activate` zet `state.speech='Engels'` (`prototype.js:183`). Dat is overbodig, want de views tonen bij een Engels-only model toch al "Engels". Het gaat ook in tegen "bestaande voorkeuren blijven bewaard".
   *Fix:* verwijder die toewijzing.

5. **Setup met spraak eindigt zonder resultaat.** Kies onder "Voeg spraak toe" Download Whisper base. Na het downloaden is er geen actief model, staat er nog "Spraak nog niet ingesteld" en verschijnt er geen melding. De gebruiker koos in de setup expliciet dit model. *Fix:* activeer automatisch als er nog geen actief model is en de download uit de setup komt, of geef bij voltooiing een melding: "Whisper base staat op je toestel. Tik Gebruik."

## Kleinere punten

- De telling gebruikt geen enkelvoud: "1 regels" (standaardtoestand van Spraakregels) en "1 woorden". Verwerk het enkelvoud op `prototype.js:117` en `:218`.
- Bij een actief tiny.en-model verschijnt de kop "Alleen Engels" met een lege sectie. Laat lege secties weg. Zonder actief model is "Andere meertalige modellen" ook de verkeerde kop.
- Na Bewaren of Ongedaan maken gaat de focus naar de pagina of naar `BODY`. De opener is dan al vervangen door `render`. Zet de focus terug op de knop "Woord toevoegen", of op de bewerkknop met hetzelfde `data-id`.
- In de situatie "Model wordt gedownload" blijft de voortgang op 40% staan, want er start geen timer. Dat mag voor een snapshot, maar label het dan, of start de timer.
- Na het toestaan van de microfoon blijft "Toestemming gegeven" een actieve knop. Toon het als status.
- Dicteergedrag → "Spelling en spraakregels" opent het tabblad Mijn woorden, niet Spraakregels.
- Terug vanaf "Kies Lowkey" leidt naar "Activeer Lowkey", ook als Lowkey al geactiveerd is.
- Er is geen Android-terug of geschiedenis (`history`). Voor een prototype is dat acceptabel, maar neem het op in de overdracht.

## Startup

Ik vond geen fouten. `play()` speelt één keer per document, ook als het werd overgeslagen. Reduced motion wordt zowel via het systeem als via de demo-optie direct afgehandeld. Alle animaties gebruiken `fill:backwards` en worden opgeruimd. `navigate` en `openDialog` roepen `finish()` aan. Er is niets `inert` of `aria-hidden`. Het logo en de logopaden zijn onaangeroerd.

## Score

Kwalitatief **7,5/10** voor v3 als ontwerpprototype. Met de fixes 1 t/m 3 is het overdraagbaar zonder voorbehoud.
