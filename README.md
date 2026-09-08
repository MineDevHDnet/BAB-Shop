# ByteBitShop

Eigenständige Minecraft-1.8.9-Client-Erweiterung für unterstützte Byte-&-Bit-Botshops auf GrieferGames.

Die JAR ist als **LabyMod-3-Addon** und als **Forge-1.8.9-Mod** aufgebaut. Es besteht keine Laufzeit-Abhängigkeit zu GrieferUtils.

## Funktionen

- moderne eigene Shop-GUI statt Chest-GUI
- Suchfeld
- automatische Kategorien
- Produktkarten mit Item, Preis und Bestand
- Detailansicht pro Angebot
- Mengenwahl
- Warenkorb mit +/- Mengensteuerung
- Gesamtpreis und Anzahl der Zahlungsvorgänge
- Bestätigungsdialog vor dem Kauf
- verzögerte `/pay`-Queue, damit mehrere Warenkorbpositionen sauber nacheinander bezahlt werden
- `/babshop cancel` zum Abbrechen einer noch laufenden Queue
- automatische Erkennung unterstützter Byte-&-Bit-Bots
- signierter Abruf der Shopdaten über das Minecraft-Spielerzertifikat
- Lagerbestandsprüfung auch bei Angeboten, die aus mehreren Item-Komponenten bestehen
- gemeinsame Lagerbestände verschiedener Preisvarianten werden im Warenkorb berücksichtigt
- frei konfigurierbarer Hotkey über Minecraft → Optionen → Steuerung → ByteBitShop
- RETURN/Enter als Standardtaste

## Installation

### LabyMod 3

`ByteBitShop-1.0.2.jar` nach:

```text
.minecraft/LabyMod/addons-1.8/
```

Je nach Installation kann der Addon-Ordner auch direkt unter `.minecraft/addons-1.8/` liegen.

### Forge 1.8.9

`ByteBitShop-1.0.2.jar` nach:

```text
.minecraft/mods/
```

Benötigt Forge `1.8.9-11.15.1.2318` oder eine kompatible 1.8.9-Forge-Version.

## Bedienung

- `RETURN` / `Enter` — Shop des erkannten Bots öffnen (Standard)
- Hotkey frei änderbar unter `Optionen → Steuerung → ByteBitShop`
- `/babshop` — Shop öffnen
- `/babshop refresh` — Botquellen neu laden
- `/babshop bots` — erkannte Bots / Status anzeigen
- `/babshop cancel` — laufenden Checkout abbrechen
- `/babshop config` — Config-Pfad anzeigen

## Config

Datei:

```text
.minecraft/config/bytebitshop.json
```

Wichtige Optionen:

```json
{
  "staticApiUrl": "https://api.grieferutils.l3g7.dev/v6/",
  "additionalBotSources": [],
  "openKeyCode": 28,
  "paymentDelayMs": 2500,
  "requireBotZone": true,
  "showHudHint": true,
  "sourceRefreshSeconds": 300,
  "botSyncSeconds": 20
}
```

`additionalBotSources` ermöglicht zusätzliche Byte-&-Bit-kompatible BotSource-URLs, ohne den Code neu zu bauen.

## Technischer Ablauf

1. unterstützte Bot-UUIDs werden aus den konfigurierten BotSources geladen;
2. erscheint ein unterstützter Spieler/Bot in der Welt, werden dessen Shopdaten synchronisiert;
3. `item/getItems/<botUuid>` liefert Zone, Items, Preise und Lagerbestand;
4. Angebote werden intern nach exaktem Preis gruppiert, weil eine Zahlung mehrere Item-Komponenten auslösen kann;
5. der Warenkorb reserviert den gemeinsamen Lagerbestand lokal;
6. beim Checkout wird pro Kaufvorgang genau ein `/pay <bot> <preis>` gesendet.

Das Minecraft-Session-Token wird **nicht gespeichert**. Es wird ausschließlich zur Laufzeit genutzt, um über `api.minecraftservices.com/player/certificates` das benötigte Spielerschlüssel-Zertifikat abzurufen.

## Grundlage / Kompatibilität

Die Byte-&-Bit-Protokollkompatibilität wurde anhand der öffentlich einsehbaren Apache-2.0-Implementierung in `L3g7/GrieferUtils` nachvollzogen. ByteBitShop verwendet eigenen Code und bindet GrieferUtils nicht als Bibliothek ein.
