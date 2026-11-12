# Startup-motion voor het Lowkey-ontwerpprototype

Alleen voor het HTML-ontwerpprototype; geen Android-implementatie. Bestanden: `startup.css` en `startup.js`, zonder dependencies, netwerk, opslag of logging. De generator voegt ze inline in.

## Idee

Eén korte, rustige opening die het merk en het werkvlak in de juiste volgorde laat landen. De blauwe OpStudio-rand staat er meteen; het merk komt erin; daarna schuift het witte (of donkere) werkvlak een paar pixels omhoog, zoals een toetsenbord dat opkomt. Eén zachte ring rond het logo knipoogt naar lokaal luisteren (spraak op het toestel) en verdwijnt.

Geen splash, geen wachtscherm, geen herhaling. Na 560 ms is de pagina exact zoals zonder motion.

## Tijdlijn (ms)

| Element | Start | Duur | Eigenschap |
| --- | --- | --- | --- |
| Logo (svg als geheel) | 0 | 320 | opacity 0→1, translateY 4px→0, scale .94→1 |
| De vier originele logopaden | 0, 40, 80, 120 | 240 | alleen opacity, in bronvolgorde |
| Luisterring | 80 | 440 | opacity 0→.3→0, scale .55→1.45; daarna verwijderd |
| Woordmerk OpStudio | 90 | 300 | opacity, translateX −6px→0 |
| LOWKEY | 150 | 300 | opacity, clip-path van links naar rechts |
| Werkvlak `.surface` (incl. navigatie) | 60 (0 zonder merk) | 420 | opacity, translateY 14px→0 |
| Regels van de view | 160 + min(i,4)×40 | 240 | opacity, translateY 6px→0 |

Langste spoor: 560 ms. Easing overal `cubic-bezier(.2,.7,.1,1)`, een afremmende curve dicht bij Androids *emphasized decelerate*. De Signal-referentie gebruikt korte, sobere overgangen (120–200 ms, `ease`) en zet die uit bij reduced motion; deze opening is langer omdat ze eenmalig is, maar blijft even sober: geen bounce, kleur of glans.

## Regels die de implementatie bewaakt

- **Logo:** de vier paden en hun geometrie blijven onaangeroerd. Alleen opacity per pad en een transform op de hele svg; de ring is een los, tijdelijk element ernaast.
- **Geen layout shift:** alleen `transform`, `opacity` en `clip-path`. De ring is absoluut gepositioneerd en `pointer-events:none`.
- **Geen flits:** de animaties gebruiken `fill: backwards`, zodat de begintoestand meteen geldt. Voorwaarde: root roept `play()` synchroon aan na de eerste `render()`, in dezelfde taak, vóór de eerste paint. Later aanroepen geeft een korte zichtbaar-onzichtbaar-zichtbaar-flits.
- **Geen wachttijd of klikblokkade:** alles blijft klikbaar en focusbaar; niets wordt `inert`, `hidden` of `aria-hidden`. De eerste `pointerdown`, `keydown` of `wheel` op `#app` rondt de motion direct af.
- **Eenmalig per document**, ook als hij werd overgeslagen. Tabwissels en `render()` herhalen niets. Alleen `{force:true}` (demo Replay) speelt opnieuw.
- **Robuust:** ontbrekende of verborgen `.brand`, svg, woordmerk of view worden overgeslagen (op Woorden/Stem animeert alleen het werkvlak). Een view die tijdens de motion wordt vervangen, laat de promise gewoon afronden. Bij een verborgen browsertab start niets; wordt de tab tijdens het spelen verborgen, dan springt alles naar het eind. Een vangnet van 900 ms rondt altijd af. Zonder `Element.animate` keert `play` direct terug.
- **Opruimen:** na afloop geen resterende animaties, ring, `data-lk-startup` of `data-lk-ring`.

## Reduced motion

Bij `prefers-reduced-motion: reduce` keert `play` direct terug (`reason: 'reduced-motion'`) zonder één animatie en zonder iets te verbergen. Verandert de voorkeur tijdens het spelen, dan springt alles naar het eind. Bij `forced-colors` verbergt CSS de ring. Er is bewust geen "kleine" alternatieve animatie: de opening voegt geen informatie toe.

## API

```js
window.LowkeyStartup.play(root, { force = false, signal } = {})
  // -> Promise<{ played: boolean, reason }>
  // reason: 'done' | 'replaced' | 'timeout' | 'already-played' | 'reduced-motion'
  //         | 'unsupported' | 'no-root' | 'hidden-tab' | 'nothing-to-animate'
window.LowkeyStartup.finish()   // optioneel: lopende opening direct afronden
```

`signal` (een `AbortSignal`) rondt af zoals gebruikersinvoer. Integratie: CSS in de bestaande `<style>`, JS als apart inline script vóór het prototypescript, en direct na de eerste `render()`:

```js
render();
window.LowkeyStartup && LowkeyStartup.play(document.getElementById('app'));
```

## Wat getest is

Getest in een geïsoleerde testpagina in `artifacts/` (genegeerd), opgebouwd uit het huidige `prototype.html`, het echte vectorlogo en deze twee bestanden, zonder lettertypen. Gerenderd met html_preview (Chromium, 460 px breed, licht en donker).

- 21 gescripte checks geslaagd: duur ≤ 600 ms (langste spoor 560), alleen transform/opacity/clip-path, geen layout shift in stappen van 20 ms over de hele tijdlijn (offsets zonder transforms, plus scrollhoogte), focus en klikbaarheid ongewijzigd, opruimen, eindtoestand gelijk aan zonder motion, vier logopaden ongewijzigd, eenmalig, invoer rondt af en de klik navigeert meteen, tabwissel tijdens het spelen, verborgen merk, reduced motion (via gesimuleerde `matchMedia`), ontbrekende `animate`, ontbrekende root, dubbele Replay, AbortSignal.
- Tussenframes bekeken: licht bij ±50 ms en donker gepauzeerd op 140 ms. Dat leverde twee aanpassingen op: een subtielere ring die het woordmerk niet raakt, en alle regels van de view in leesvolgorde, zodat de knop niet vóór de kop verschijnt.
- **Niet getest:** de opening op natuurlijke snelheid op echte hardware. html_preview maakt zijn opname na ±50 ms en headless Edge rendert hier geen animatieframes. De timing komt uit de timingmodellen van de animaties en handmatig gezette `currentTime`, niet uit een opname. Ook niet getest: Firefox/Safari, een echte reduced-motion-systeeminstelling, schermlezers en een echte browsertabwissel. Nog niet gecontroleerd tegen de nieuwe `prototype.html` van root.

## Vertaling naar Android later (beperkingen)

- Een IME-instellingenapp heeft geen eigen opstartmoment zoals een webpagina. De systeem-splash (Android 12+) is er al; een tweede merkanimatie direct daarna voelt als wachten. Gebruik dit hooguit als *enter transition* van het eerste scherm, eenmalig per processtart, nooit in het toetsenbord zelf.
- Respecteer `Settings.Global.ANIMATOR_DURATION_SCALE` (0 = geen motion) en *Animaties verwijderen*; in Compose volgt dat grotendeels automatisch, bij eigen tijdmetingen niet.
- Het logo is een `VectorDrawable` met vier paden. Opacity per pad vraagt een `AnimatedVectorDrawable` met een `fillAlpha`-animator per `path`-naam. Dat raakt de geometrie niet, maar vereist dat de paden namen krijgen. Wijzig de padgegevens niet.
- De `clip-path`-onthulling van LOWKEY wordt in Compose een `drawWithContent { clipRect(...) }`. De ring is een losse `Canvas`-cirkel buiten de vector.
- `fill: backwards` heeft geen directe tegenhanger: zet de begintoestand in de eerste frame (bijvoorbeeld `Animatable` met beginwaarde) om flitsen te vermijden.
- Ook in Android geen invoerblokkade: aanraking onderbreekt de animatie direct; TalkBack-focus mag niet wachten op de motion.
- Easing: `FastOutSlowInEasing` of Material 3 `EmphasizedDecelerate` (`CubicBezier(0.05,0.7,0.1,1)`) ligt dicht bij de gebruikte curve.
