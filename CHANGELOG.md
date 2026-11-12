# Changelog

Alle noemenswaardige wijzigingen aan AutoClicky worden hier bijgehouden.

## 1.3.0

### Opname
- Universele tapregistratie: meldt een app geen touchpositie (games, canvas, WebViews, Samsung-toestellen), dan schakelt
  de opname automatisch over naar een relay-overlay die de aanrakingen ontvangt en terugstuurt naar de app.
- De Stop-knop is vervangen door een ✕ die precies onder het knipperende rode REC-punt staat.
- Een opname wordt bij het tikken op ✕ automatisch opgeslagen (**Recording saved**); bij 0 acties wordt niets opgeslagen
  (**No taps recorded**) en is de nieuwe opname direct de opname die **PLAY** afspeelt.

### Opslag
- Opnames kunnen in het hoofdmenu worden vastgepind (📌); de status blijft behouden na herstart.
- Er worden maximaal vijf niet-vastgepinde opnames bewaard: de oudste wordt automatisch verwijderd. Vastgepinde opnames
  en de actief geselecteerde opname worden nooit automatisch verwijderd.

### Playback
- Tijdens het afspelen verschijnt een dunne, langzaam knipperende groene rand (met subtiele gloed) rondom het scherm,
  plus een kleine groene ▶-indicator.
- Het floating menu verbergt zich tijdens het afspelen; de ▶-indicator is puur visueel en onderschept geen aanrakingen.
- Elke fysieke aanraking stopt het afspelen onmiddellijk; ook het uitschakelen van het scherm stopt het afspelen.
- De eigen, automatisch geïnjecteerde aanrakingen stoppen het afspelen niet.

### Overig
- Het floating menu heeft geen Stop-knop meer (deze was niet langer nodig).
