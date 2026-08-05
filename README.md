# BitsCore

Paper Plugin 1.21 | Netzwerkweite Bits-Währung mit MySQL

---

## ÜBERSICHT

BitsCore ist eine eigenständige Währungsimplementierung für Minecraft-Netzwerke. Alle Spielerdaten werden zentral in einer MySQL-Datenbank gespeichert und per HikariCP Connection-Pool sowie In-Memory-Cache pro Server bereitgestellt.

Author: BlockException_

---

## FEATURES

- Zentrale MySQL-Datenbank mit Transaktionsprotokoll
- HikariCP Connection-Pooling mit konfigurierbarer Pool-Größe
- In-Memory Cache pro Serverinstanz (ConcurrentHashMap)
- Asynchrone Lese- und Schreiboperationen
- Atomare SQL-Operationen verhindern Race Conditions
- Auto-Save aller Cachedaten alle 5 Minuten
- PlaceholderAPI Integration (optional)
- Offline-Spieler-Unterstützung bei Abfragen
- Top-10 Leaderboard

---

## INSTALLATION

1. Kompilierte JAR aus `target/` in den `plugins/`-Ordner verschieben
2. Server starten
3. In `plugins/BitsCore/config.yml` MySQL-Zugangsdaten eintragen
4. Server neu starten

Die Tabellen `player_bits` und `bits_transactions` werden automatisch erstellt.

---

## KONFIGURATION

`config.yml`:

```yaml
database:
  host: "127.0.0.1"
  port: 3306
  name: "deinedatenbank"
  user: "deinuser"
  password: "deinpasswort"

pool:
  minimumIdle: 2
  maximumPoolSize: 10
```

---

## BEFEHLE

| Befehl | Beschreibung |
|---|---|
| `/bits balance` | Eigenen Kontostand anzeigen |
| `/bits balance <Spieler>` | Kontostand eines Spielers anzeigen |
| `/bits give <Spieler> <Betrag>` | Bits hinzufügen |
| `/bits take <Spieler> <Betrag>` | Bits entfernen |
| `/bits set <Spieler> <Betrag>` | Kontostand festlegen |
| `/bits top` | Top 10 Leaderboard anzeigen |

Aliase:
- `give` / `add`
- `take` / `remove`
- `balance` / `bal`

---

## BERECHTIGUNGEN

| Permission | Beschreibung | Standard |
|---|---|---|
| `bits.core.admin` | Alle Verwaltungsbefehle | OP |

---

## API

BitsCore bietet eine statische API zur Integration in andere Plugins.

### Maven Dependency

```xml
<dependency>
    <groupId>de.bitscore</groupId>
    <artifactId>BitsCore</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

### Balance Abfragen

```java
import de.bitscore.api.BitsCoreAPI;
import de.bitscore.api.BitsProvider;
import java.util.UUID;

public void checkBalance(UUID uuid) {
    BitsProvider provider = BitsCoreAPI.getProvider();
    int balance = provider.getBalance(uuid);
}
```

### Bits Hinzufügen

```java
import de.bitscore.api.BitsCoreAPI;
import de.bitscore.api.BitsProvider;
import java.util.UUID;

public void rewardPlayer(UUID uuid) {
    BitsProvider provider = BitsCoreAPI.getProvider();
    provider.addBits(uuid, 250, "daily_reward");
}
```

### Bits Entfernen Mit Check

```java
import de.bitscore.api.BitsCoreAPI;
import de.bitscore.api.BitsProvider;
import java.util.UUID;

public boolean purchaseItem(UUID uuid, int price) {
    BitsProvider provider = BitsCoreAPI.getProvider();
    if (!provider.hasEnough(uuid, price)) {
        return false;
    }
    return provider.removeBits(uuid, price, "shop_purchase");
}
```

### Kontostand Festlegen

```java
import de.bitscore.api.BitsCoreAPI;
import de.bitscore.api.BitsProvider;
import java.util.UUID;

public void resetBalance(UUID uuid) {
    BitsProvider provider = BitsCoreAPI.getProvider();
    provider.setBalance(uuid, 0);
}
```

### Soft-Dependency In plugin.yml

```yaml
softdepend: [BitsCore]
```

---

## PLACEHOLDERAPI

Verfügbare Platzhalter:

| Platzhalter | Ausgabe | Beispiel |
|---|---|---|
| `%bitscore_balance%` | Ganze Zahl | `12500` |
| `%bitscore_balance_formatted%` | Tausendertrenner DE | `12.500` |
| `%bitscore_balance_short%` | Kurzform | `12,5k` |

### Verwendung in Scoreboards / Chat

```
Geld: %bitscore_balance_formatted% Bits
```

---

## DATENBANK STRUKTUR

Tabelle `player_bits`:

| Spalte | Typ | Beschreibung |
|---|---|---|
| `uuid` | VARCHAR(36) | PRIMARY KEY |
| `player_name` | VARCHAR(16) | Letzter bekannter Name |
| `balance` | INT | Aktueller Kontostand |
| `last_updated` | TIMESTAMP | Letzte Aktualisierung |
| `created_at` | TIMESTAMP | Erstellungsdatum |

Tabelle `bits_transactions`:

| Spalte | Typ | Beschreibung |
|---|---|---|
| `id` | BIGINT | AUTO_INCREMENT PK |
| `uuid` | VARCHAR(36) | Spieler UUID |
| `amount` | INT | Betrag (negativ = Abzug) |
| `reason` | VARCHAR(64) | Transaktionsgrund |
| `server` | VARCHAR(32) | Servername |
| `timestamp` | TIMESTAMP | Ausführungszeitpunkt |

---

## PROJEKT BAUEN

```bash
mvn clean package
```

Ausgabe: `target/BitsCore-1.0.0.jar`

---

## TECHNISCHE DETAILS

- Java 21
- Paper API 1.21
- HikariCP 5.1.0 (shaded nach `de.bitscore.libs.hikari`)
- PlaceholderAPI 2.11.6 (optional)
- CompletableFuture für asynchrone Operationen
- ConcurrentHashMap für Thread-Safe Cache
