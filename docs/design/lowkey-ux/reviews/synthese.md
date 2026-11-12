# Drie onafhankelijke beoordelingen van Lowkey

12 november 2026. Astra, Opus 5.5 en Grok 4.7 beoordeelden hetzelfde herziene concept. De ontwerpbronnen zijn tijdens de reviews niet gewijzigd; gecontroleerd tegen `source-snapshot.json`. Dit document bundelt hun opmerkingen, zonder het ontwerp al aan te passen.

## Scores op 10

| Criterium | Gewicht | Astra | Opus | Grok |
| --- | --- | --- | --- | --- |
| Informatiearchitectuur | 25% | 8,0 | 7,5 | 7,0 |
| Gebruiksgemak | 25% | 7,0 | 6,5 | 6,5 |
| Visuele rust en hiërarchie | 20% | 8,0 | 8,0 | 8,0 |
| OpStudio/Signal-samenhang | 20% | 9,0 | 8,0 | 8,0 |
| Toegankelijkheid en adaptiviteit | 10% | 7,0 | 6,5 | 6,5 |
| **Gewogen totaal** | **100%** | **7,9** | **7,4** | **7,2** |

Nagekeken berekeningen: Astra 7,85 → 7,9; Opus 7,35 → 7,4; Grok 7,225 → 7,2. Dit zijn subjectieve ontwerpoordelen, geen gemeten gebruikssucces of kwaliteitscertificaten. De kleine scoreverschillen rechtvaardigen geen rangschikking van de reviewers. De deelscores zijn informatiever: visuele rust krijgt overal 8; gebruiksgemak en adaptiviteit blijven achter.

## Gezamenlijk oordeel

**De richting behouden. Eerst bediening en ontbrekende toestanden uitwerken; geen nieuwe huisstijlronde.** Alle drie waarderen de rustige Home, categorie-index en herkenbare OpStudio-vertaling. Het goedgekeurde logo blijft behouden. Astra waardeert de merkverwantschap iets hoger; Opus en Grok wegen misleidende of onvolledige toestanden zwaarder.

Het prototype toont de rustige hoofdtoestand overtuigender dan eerste gebruik, fouten, downloads of groot ingestelde tekst. Een aantal punten is een tekort van de demonstratie, geen bewezen bug in de huidige Android-app. De huidige app heeft bijvoorbeeld al downloadvoortgang, annuleren en actief-modelbescherming; die moeten in een herontwerp zichtbaar behouden blijven.

## Concrete verbeteringen, in aanbevolen volgorde

| Prioriteit | Opmerking | Wie noemt dit | Voorgestelde ontwerpaanpassing |
| --- | --- | --- | --- |
| 1 | Setup overslaan voelt als een fout; er lijkt al een model ingesteld | Opus, Grok | Aparte Home-toestanden voor nog niet geactiveerd, wel geactiveerd/niet gekozen, volledig ingesteld en later andere IME actief. Geen rode hersteltekst bij bewust uitstel. Spraak zonder model: Nog niet ingesteld. |
| 1 | Navigatie en terugpaden zijn nog niet overal eenduidig | Alle drie | Home-rij benoemen als Taal en indeling; tabovergang aankondigen. Typen/Aanpassen terug naar Toetsenbord. Woordenboek en subtab Mijn woorden onderscheiden. Herstel blijft Home actief markeren. |
| 1 | Mobiele navigatie is onvoldoende bewezen | Alle drie | Begrensd telefoonvenster met scrollende inhoud en blijvend bereikbare, native-achtige navigatie. Labels met passende iconen en 200% tekst toetsen; niet oplossen door steeds kleinere letters. Zoom uit Groks test is geen Android-fontschaling. |
| 2 | Modelkeuze toont de lastige toestanden niet | Astra, Opus; Grok als vervolg | Dezelfde modelrij voor beschikbaar, downloaden + Annuleer, fout + Opnieuw proberen, geïnstalleerd + Gebruik en Actief. Aanbevolen alleen waar de catalogus dat bepaalt. Engels-only modellen als echte keuze tonen. |
| 2 | Woordenbeheer krijgt minder nadruk dan een globale instelling | Astra, Opus | Zoeken/toevoegen/resultaten bovenaan; resultaataantal, zichtbaar bewerken/rijmenu, eigen lege tekst per tab en Uit-status voor inactieve spraakregels. Globale spellinginstelling op een rustige, eenduidige plek binnen die bestemming. |
| 2 | Twee testacties en aaneengeplakte tekstlinks | Astra, Grok | Eén testactie op Formaat en positie; herstel daarvan scheiden. Ander model kiezen en Later instellen als aparte, ruim geplaatste acties. |
| 3 | Dicteergedrag en hoogte vragen concretere bediening | Grok, Opus | Afhankelijke spraakopties zichtbaar koppelen aan de startmodus; niet alleen beschrijven. Hoogte als volwaardige keuzerij tonen. Geen nieuwe gedragsregels introduceren. |

## Wat ik als volgende ontwerpstap zou doen

Een kleine tweede uitwerking van hetzelfde ontwerp, met drie doelen:

1. **Eerlijk eerste gebruik:** setup overslaan en spraak nog niet ingesteld, naast de bestaande klaar-/hersteltoestand.
2. **Voorspelbare bediening:** één testactie, consistente terugroutes en navigatie in een echt begrensd mobiel voorbeeld, inclusief grote tekst.
3. **Volledig beheer:** modelstatussen en woordenboekacties tonen in dezelfde rustige componenten.

Geen extra tabs, nieuwe kleuren, logo of decoratie nodig. Dit is een aanbeveling, geen reeds uitgevoerde ontwerpwijziging. De gebruiker vroeg deze ronde om reviews en scores.

## Keuzes die open blijven

- Engels productlabel Voice of Models; Nederlandse bespreeklabels zijn geen opdracht tot app-lokalisatie.
- Home-links naar andere tabs: expliciete overgang met vaste ouder, of terug naar de herkomst. Eén consistente regel kiezen.
- Handmatige Split tegenover automatisch splitsen op breed scherm: samen met de keyboard-track prioriteit en herstel vastleggen. Geen besluiten uit dit prototype afleiden.
- Systeemtekst met Bricolage-koppen blijft een verdedigbare voorkeur; uitgebreider Instrument Sans blijft een alternatief. Deze reviews vragen geen nieuwe merkidentiteit.

## Bewijs en beperkingen

Alle drie bekeken daadwerkelijk HTML-schermen en de actuele blauwe Signal-bronnen. Astra voerde aanvullende gescripte interactiecontroles uit; Grok mat ook overloop/zoom. Opus leidde interacties uit de bron af en meldde dat expliciet. Niemand testte deze ronde de Android-app, IME, TalkBack of de keyboard-track. De afzonderlijke verslagen geven de bekeken schermen en breedtes.

- [Astra](astra.md)
- [Opus](opus.md)
- [Grok](grok.md)
- [Gemeenschappelijke rubric](README.md)

Nieuwe reviewteksten worden op privégegevens gecontroleerd. Geen emulator, fysieke telefoon, publicatie, commit of productwijziging voor deze beoordelingsronde.
