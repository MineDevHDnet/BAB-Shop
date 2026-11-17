# ByteBitShop 1.1.0

Eigenständiges LabyMod-3-Addon und Forge-1.8.9-Mod für **Byte-&-Bit-Botshops auf GrieferGames**, jetzt mit einer **klassischen, GrieferUtils-artigen Botshop-GUI**.

Die GUI wurde eigenständig umgesetzt. Keine GrieferUtils-Laufzeitabhängigkeit.

## Funktionen im GrieferUtils-Stil

- Klassische 7×7-Anzeigenfläche im Minecraft-Kistenstil mit 49 Angeboten pro Seite
- Linke Einkaufsliste mit 7 einzeln anklickbaren Kaufvorgängen
- Suchfeld (Itemname, Originalname, Lore und Preis)
- Scrollleiste und Mausrad für weitere Angebote
- Ausverkaufte Angebote markiert, Lagerbestand direkt im Tooltip
- Verfügbarkeitsangaben (verfügbar / knapp / ausverkauft)
- Gleicher Preis gruppiert mehrere Item-Komponenten zu einem Kaufvorgang
- Warenkorbsumme im Titel und grüner Kaufknopf in der mittleren Trennspalte
- Hotkey **Enter** sowie Hinweis als Bossbar bei Erkennung einer Bot-Zone
- Erkennung über die von GrieferUtils verwendeten BotSources und die signierte Byte-&-Bit-API

## Bewusst sicherere Unterschiede

- Jede Zahlung wird über eine Warteschlange mit **2,5 Sekunden Pause** an den Minecraft-Chat gesendet, statt alle /pay-Befehle auf einmal auszuführen.
- Abbrechen der noch ausstehenden Zahlungen mit `/babshop cancel`. Bereits gesendete Zahlungen lassen sich nicht zurücknehmen.
- Gemeinsame Lagerbestände über mehrere Preisvarianten werden korrekt mit den lokal reservierten Mengen verrechnet.
- Keine Abhängigkeit von der GrieferUtils-Kontostandsanzeige; die GUI kann kein Guthaben garantieren.

## Installation

Die heruntergeladene `ByteBitShop-1.1.0.jar` nach `.minecraft/LabyMod/addons-1.8/` für LabyMod 3 oder nach `.minecraft/mods/` für Forge 1.8.9 legen. Nicht beide Varianten gleichzeitig installieren.

## Bedienung

- **Enter** — Shop innerhalb einer unterstützten Bot-Zone öffnen
- **Klick auf Angebot** — einen Kaufvorgang zur Einkaufsliste hinzufügen
- **Klick auf Einkaufsliste links** — einen Kaufvorgang entfernen
- **Grüner Smaragd** — Einkaufsliste kaufen
- **Suchfeld / Mausrad / Scrollleiste** — Produkte finden
- `/babshop` — Shop öffnen
- `/babshop refresh` — Botquellen neu laden
- `/babshop bots` — bekannte Bots anzeigen
- `/babshop cancel` — ausstehende Zahlungen abbrechen
- `/babshop config` — Konfigurationspfad

Konfiguration: `.minecraft/config/bytebitshop.json`.

## Quellcode und Lizenz

Die Protokollkompatibilität sowie die klassische Gestaltung orientieren sich an der öffentlich einsehbaren Apache-2.0-Implementierung unter [L3g7/GrieferUtils](https://github.com/l3g7/GrieferUtils). Dieses Repository implementiert seine GUI selbst und bindet keine GrieferUtils-Klassen ein.
