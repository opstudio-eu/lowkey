# Onafhankelijke ontwerpreview — ronde 2

Op verzoek van de gebruiker beoordelen Astra, Opus en Grok hetzelfde herziene Lowkey-concept. Deze ronde levert scores en concrete opmerkingen; geen productimplementatie of automatische verwerking van suggesties.

## Gemeenschappelijke beoordelingsbasis

- Actueel `../voorstel.md` en `../prototype.html`, na verwerking van de eerste Opus-review.
- Gebouwd voorbeeld: `artifacts/lowkey-ux/prototype.html` vanaf de repository-root. Scenario's staan in dezelfde artifactmap; de HTML is zelfstandig, zonder server of netwerk.
- Signal: actuele blauwe werkbestanden, zoals vastgelegd in `../signal-sources.json`. Oudere groen-mosterdvarianten zijn niet het vertrekpunt.
- Het bestaande OpStudio-logo blijft behouden. De keyboard-track en apparaten zijn buiten scope. Het concept is geen bewezen Android-UI.
- Beoordeel zelfstandig. Lees geen andere reviewers of oude reviewconclusies voordat je eigen oordeel en score vaststaan.

## Scores

Geef elk criterium een score op 10 en bereken het gewogen totaal op 10. Hoogstens één decimaal; subjectieve ontwerpscores zijn geen metingen van gebruikssucces.

| Criterium | Gewicht | Vraag |
| --- | --- | --- |
| Informatiearchitectuur | 25% | Zijn functies logisch gegroepeerd, vindbaar en vrij van onduidelijke dubbele ingangen? |
| Gebruiksgemak | 25% | Zijn setup, herstel, modellen, woordenboek en terugnavigatie begrijpelijk? |
| Visuele rust en hiërarchie | 20% | Is de hoofdtaak duidelijk zonder dat belangrijke inhoud verdwijnt? |
| OpStudio/Signal-samenhang | 20% | Is de familie herkenbaar, met behoud van Lowkeys mobiele taak en het bestaande logo? |
| Toegankelijkheid en adaptiviteit | 10% | Welke tekst-, contrast-, focus- en smalle-schermrisico's zijn zichtbaar? |

Een 5 is middelmatig met duidelijke problemen, 7 bruikbaar met merkbare verbeterpunten, 9 uitzonderlijk sterk met kleine resterende verbeteringen. Niet alle scores hoeven hoog of gelijk te zijn. Noem de werkelijke beperkingen van de waarneming: HTML is geen Compose, IME of TalkBack.

## Gevraagde uitkomst per reviewer

1. Bekeken bronnen, schermen en breedtes; expliciet wat niet kon worden bekeken.
2. Score per criterium, gewogen totaal en één zin eindoordeel.
3. Maximaal vijf belangrijkste opmerkingen: concreet scherm, waargenomen probleem, effect, voorgestelde verbetering, prioriteit (hoog/midden/laag).
4. Twee sterke punten die behouden moeten blijven.
5. Eerste drie verbeteringen in volgorde. Scheid kleine ontwerpaanpassingen van keuzes die de gebruiker/keyboard-track moet maken.

Reviewers schrijven uitsluitend hun eigen `astra.md`, `opus.md` of `grok.md` in deze map. Geen ontwerp- of appbestanden aanpassen; geen extra agents, emulator, fysieke telefoon, server, commit, push of publicatie. Gebruik relatieve paden in documenten, geen privégegevens. Reviews en eventuele latere synthese worden gecontroleerd op lekken.
