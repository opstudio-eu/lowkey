# Lowkey — uitgewerkte prototypeversie met startup-motion

12 november 2026. Deze versie werkt de drie onafhankelijke reviews uit, op verzoek van de gebruiker, met een startupanimatie van Opus. **Ontwerp en lokaal prototype; geen wijziging van de Android-app, keyboard-track of release.**

De ontwerpbron is nu opgesplitst in `prototype.html`, `prototype.css` en `prototype.js`. De bestaande generator maakt nog steeds één zelfstandig `artifacts/lowkey-ux/prototype.html`. De startupbestanden van Opus worden daarin inline opgenomen. Geen server, netwerk, bundeldependency of blijvende gegevensopslag nodig.

## Wat is verbeterd

| Reviewpunt | Uitwerking |
| --- | --- |
| Uitgestelde setup leek mislukt | Eerste start, geactiveerd/niet gekozen, actieve invoer en optionele spraak zonder model hebben aparte toestand. Later instellen bewaart die toestand en gaat naar Home zonder verzonnen succes of rode fout. |
| Mobiele navigatie niet te beoordelen | Een begrensd telefoonvenster van 720 CSS-pixels, een scrollend inhoudsgebied en een altijd bereikbare onderbalk. Bestaande projecticonen hergebruikt. Geen kleiner lettertype voor smalle schermen; labels mogen afbreken en hebben hun volledige toegankelijke naam. |
| Dubbele namen en terugroutes | Home gebruikt Taal en indeling, met Instellingen als expliciete bestemming. Woordenboek als tab, Mijn woorden als subtab. Typen/Aanpassen keren terug naar Toetsenbord; dicteertaal naar Stem. Kruisverwijzingen benoemen de tab. |
| Modeltoestanden ontbraken | Beschikbaar, downloaden met voortgang/annuleren, fout/opnieuw proberen, geïnstalleerd/Gebruik en actief. Een download activeert niet vanzelf. Actief model is beschermd tegen verwijderen. Aanbevolen verschijnt bij Whisper base. Engels-only is een concrete modelkeuze. |
| Woordenboek leek alleen-lezen | Zoeken, telling en toevoegen vóór de lijst; zichtbare bewerkactie; dialoog met bewaren en verwijderen; ongedaan maken na verwijderen. Spraakregels hebben eigen lege toestand, zichtbare Uit-status en blijven uit tot de gebruiker kiest. Spellingherstel staat onder de lijst. |
| Dubbele en aaneengeplakte acties | Eén testactie op Formaat en positie. Tekstacties staan apart met ruimte. Herstel is onderscheiden van proberen. |
| Hoogte en afhankelijk dicteergedrag onduidelijk | Hoogte als radio-opties; bij Eenhandig verschijnt Zijde. Bij dicteren zijn auto-start, onthouden en continu alleen bedienbaar in aparte spraakmodus. Bestaande voorkeuren blijven bewaard. |

De prototypecatalogus gebruikt drie echte modelnamen en afgeronde groottes uit de bestaande catalogus om de toestanden compact te laten zien. Dit is geen voorstel om de overige modellen te verwijderen. De overdracht naar productcode moet de volledige catalogus behouden.

## Startupanimatie, samen met Opus

De vier oorspronkelijke logopaden blijven exact behouden. Het logo komt subtiel op, met een zachte eenmalige ring en een korte onthulling van het woordmerk. Het werkvlak beweegt licht omhoog. Geen extra splashpagina en geen kunstmatige laadtijd.

- Langste animatiespoor: **560 ms**.
- Eén keer per document; tabwissels starten de animatie niet opnieuw.
- Eerste aanraking, toetsenbordinvoer of scroll in de app rondt de animatie af.
- `prefers-reduced-motion` slaat de motion over. De demo heeft daarnaast Minder beweging en Opening opnieuw, buiten de app geplaatst.
- Geen focusverplaatsing, invoerblokkade of verandering aan logo-geometrie.
- Android krijgt later hooguit een korte overgang ná de systeemsplash, geen tweede wachtscherm. Geen animatie in de IME zelf.

Details en de eigen verificatie van Opus staan in `startup-notes.md`. De integratie voegt de expliciete demo-optie `reducedMotion` toe aan `play()`.

## Verificatie van deze versie

- Prototype-interacties: **239 controles op 320, 390 en 728 px geslaagd**; licht en donker, 100% en 200% tekst, 23 routes en negen begin-/modeltoestanden.
- Gecontroleerd: eerlijk overslaan, activeren versus kiezen, vaste ouderroutes, één testactie, annuleren/retry/activeren, actief-modelbescherming, woorden toevoegen/bewerken/verwijderen/undo, zoekresultaten, spraakregels uit/aan, behoud van afhankelijk gemaakte voorkeuren, testtekst en focusherstel.
- Geïntegreerde motion: **16 controles geslaagd** voor maximale duur, visuele animatie-eigenschappen, stabiele layout op 0/120/280/560 ms, gelijke logopaden, opruimen, focusbehoud, eenmalig spelen, reduced motion en navigeren tijdens motion. De layoutcontrole gebruikt cumulatieve layoutposities: een CSS-transform verandert de offset-parent zonder de layout te verplaatsen.
- Opus controleerde de losse motion eerder met 21 gescripte checks. Dat is aanvullend en niet hetzelfde als deze integratiecontrole.
- Visueel bekeken: Home, eerste start, instellingen, woordenboek, modeldownload, downloadfout, donkere Formaat en positie en 200% tekst. Het onderliggende prototype is klikbaar in de chat.
- Animatie wordt in de HTML-renderer op vaste tijdstippen en via timing gecontroleerd. Geen claim van natuurlijke snelheid op Android, TalkBack of een echte systeembrede instelling.

Testvarianten, oudere prototypekopie en tijdelijke scripts blijven onder gitignored `artifacts/`. De onafhankelijke reviews blijven ongewijzigd als historisch oordeel over versie 2; hun scores zijn geen scores van versie 3.

## Grenzen

Alle mutaties zijn demonstraties in geheugen. De microfoontoestemming en Android-keuze worden gesimuleerd; downloaden volgt een korte lokale timer of een expliciete gekozen fouttoestand. Geen netwerk of echte spraakherkenning. Niet-uitgewerkte secundaire pagina's zeggen dat expliciet. De definitieve Engelse productcopy, native focus/IME-insets en alle modellen blijven onderdeel van latere implementatie.

Handmatige Split versus de voorkeur op een breed scherm blijft een afzonderlijk besluit met de keyboard-track. Er is niets aan die track gekopieerd of aangepast.

## Verwerking van de laatste Opus-controle

Opus vond geen blocker, maar wel drie hoge toegankelijkheidspunten. Zijn oorspronkelijke oordeel (7,5/10 vóór onderstaande fixes) staat in `v3-opus-review.md`; er is geen nieuwe score verzonnen na herstel.

- **Downloadfocus:** voortgang verandert alleen de bestaande balk en percentage; alleen bij een statuswissel wordt de betreffende modelrij vervangen. Focus elders blijft staan. Binnen die rij gaat de focus naar de bijpassende nieuwe actie.
- **Meldingen:** de melding staat in de layout direct boven de onderbalk, met begrensde scrollbare hoogte. Ze bedekt de navigatie niet bij grote tekst.
- **Focuscontrast:** het lichte werkvlak gebruikt het donkere OpStudio-blauw; de donkere variant lichtblauw. De inverse melding krijgt een passende afzonderlijke focuskleur.
- **Voorkeuren en voltooiing:** activeren van een Engels-only model wist de meertalige dicteervoorkeur niet. Downloadvoltooiing meldt expliciet dat Gebruik de volgende stap is. De demo start voortgang ook vanuit de scenarioselectie.
- **Kleine punten:** enkelvoudige tellingen, geen lege modelgroep, focus na opslaan/undo, microfoontoestemming als status, spraakregels rechtstreeks openen en geen redundante activeringsstap bij teruggaan.

Daarvoor zijn 19 gerichte regressiecontroles toegevoegd op 320 en 390 px, naast de bestaande interactie- en motioncontroles. De prototypeknop Terug volgt vaste ouders; Android-terug, browsergeschiedenis en native IME-gedrag zijn niet geïmplementeerd of bewezen in HTML. De echte app moet die later behouden en testen.
