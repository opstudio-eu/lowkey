# Verwerking van de Opus-review

12 november 2026. Claude Opus 5.5 heeft een gerichte ontwerpbijdrage en onafhankelijke review uitgevoerd. De oorspronkelijke bevindingen staan ongewijzigd in `opus-review.md`. Root heeft feitelijke bevindingen gecontroleerd tegen de bron en onderstaand verwerkt. Dit is geen tweede review door Opus en geen goedkeuring van productcode.

## Hersteld vóór overdracht

| Bevinding | Controle en verwerking |
| --- | --- |
| B1: beperkte fontdekking / verkeerde licentie | De Instrument Sans-licentie komt nu uit `public/fonts/INSTRUMENT-SANS-LICENSE.txt`, niet uit de Atlas/Noto-licentie. Het ontwerp vermeldt de Latin-subsets expliciet. Voorlopige voorkeur: systeemfont voor bediening/inhoud, Bricolage voor kop/merk. Voor bundeling eerst uitgebreide dekking en notices. Geen Android-fonts toegevoegd. |
| B2: dicteertaal onjuist voorgesteld | `Settings.speechLanguage` is `en`. Het voorbeeld toont Engels naast een Nederlands toetsenbord. Dicteertaal heeft één plek onder Stem, met een bevestigingsstap in optionele setup. Bij Engels-only modellen vaste taal. De beschikbare modellen zijn op taalondersteuning ingedeeld, niet op de enige huidige familie. |
| B3: dubbele ouders / onduidelijke terugkeer | Dicteertaal, gedrag en opslag horen onder Stem. Instellingen noemt expliciet de tabwissel. Talen en indelingen blijft onder Instellingen. De echte bestaande deep links zijn opgenomen in een migratietabel. Opslag keert nu terug naar Stem. |
| B4: splitbediening dubbelzinnig / hoogte ontbreekt | Hoogte terug in het concept. De combinatie Split-radio + autosplit-schakelaar vervangen door een bespreekbare breed-schermvoorkeur. Geen besluit over verwijderen van bestaande handmatige split; voorrang/persistentie blijven open met de keyboard-track. Slepen gebeurt op het echte toetsenbord. Label Eenhandig gecorrigeerd. |
| B5: niet beschikbare import/export / verkeerde standaard regels | `ImportExportRow` is uitgeschakeld in de bron. Het voorstel en prototype bieden geen import/export meer aan. Spraakregels beginnen uit; spellingherstel staat bij Woorden. |
| Onboarding en Home | Tabs weg tijdens onboarding; expliciete uitweg met herstelstatus. Extra woordenboekrij van Home verwijderd. Toetsenbordrij gaat naar taal/indeling. Genummerde stappen met linkerlijn ontleend aan echte Signal-componenten. |
| Merkkop / leesruimte | Groot merk alleen op Home/onboarding/herstel. Andere pagina's hebben een compacte bovenkant. Alleen bovenhoeken afgerond. Donkere shell gebruikt bestaande donkerste Lowkey-tint. Logo-geometrie ongewijzigd. |
| Kleine UI-fouten | Lichte/donkere form controls volgen het app-thema. Terugknoppen minimaal 48 px. Nederlandse demo-tab heet Woorden om afbreken te vermijden; Engelse productlabels blijven apart te verifiëren. Foutstatus heeft tekstgewicht; statusstip een zachte halo. |
| Updates | Eigen categorierij behouden zodat de bestaande README-ingang blijft passen. |

## Bewust geen productwijziging

- Geen automatisch meesturen van toetsenbordtaal als dicteertaal. Dat is alleen een voorstel na bevestiging; opgeslagen voorkeur blijft leidend.
- Geen prestatiescores of gegarandeerde modelkwaliteit. Bestaande kwalitatieve uitleg mag, gemeten claims ontbreken.
- Geen nieuwe import/export, extra instelling of migratie. Verwijderen/undo blijft een afzonderlijk gedragsvoorstel.
- Geen kleurenwijziging aan het toetsenbord. Het lichte rood blijft voorlopig de bestaande Lowkey-kleur; volledige kleurharmonisatie kan later worden beoordeeld.
- Geen live-audit van Signal of Lowkey. Signal is gelezen als lokale werkbron; de Android-app is niet op een apparaat geopend.

## Resterende ontwerpkeuzes

1. Engelse tab **Voice** bevestigen, of **Models** behouden. Vier tabs is de gezamenlijke voorkeur; Nederlandse labels zijn uitsluitend bespreekcopy.
2. Systeemfont voor bediening met Bricolage-koppen, of uitgebreid Instrument Sans. De bestaande subsets zijn niet voldoende om zonder aanvullende controle te bundelen.
3. Breed-schermvoorkeur en handmatige split met de keyboard-track verenigen. Een statisch ontwerp kiest geen impliciete voorrang of fold-regel.

## Eindcontrole

Het herziene prototype is opnieuw gecontroleerd met gescripte interacties in de HTML-renderer en visueel bekeken. Zie `verificatie.md`. Opus heeft de eerste versie beoordeeld; de bovenstaande aanpassingen zijn door root verwerkt en nagekeken. Geen extra agent of nieuwe producttrack gestart.
