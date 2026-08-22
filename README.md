# Arise

Mod di progressione per **Minecraft 26.2** (Fabric), ispirata a *Solo Leveling*.

Uccidere mostri fa salire di livello, i livelli danno punti statistica, le statistiche diventano
attributi veri. Da lì: estrarre l'ombra dei nemici caduti e comandarne un esercito, aprire i Gate ed
esplorarli, viaggiare fra le città e le loro Associazioni dei Cacciatori.

## Cosa c'è dentro

| Sistema | Cosa fa |
|---|---|
| **Il Sistema** | XP dai mob, livelli, punti statistica, quattro statistiche mappate su attributi vanilla |
| **Interfaccia** | HUD del Sistema e schermata di stato, disegnate a mano |
| **Esercito d'ombra** | Estrazione, evocazione, ranghi, livellamento, posture, arena di comando |
| **Gate** | Dimensione dedicata, layout procedurale a stanze, boss, ricompense |
| **Varchi** | I Gate si materializzano nel mondo e si analizzano — rango, abitanti, terreno — prima di entrare |
| **Città** | Cinque hub (New York, Tokyo, Roma, Madrid, Berlino) costruiti a blocchi, con rete di viaggio |
| **Abilità** | Quattro abilità attive con cooldown, validate lato server |

## Ambiente

| | |
|---|---|
| Minecraft | 26.2 |
| Fabric Loader | 0.19.3 |
| Fabric API | 0.157.0+26.2 |
| Loom | 1.17-SNAPSHOT |
| Java | 25 |
| Mappings | ufficiali Mojang |

## Come si costruisce

**Windows** (PowerShell):

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.7-hotspot"
.\gradlew.bat build        # compila e remappa il jar in build/libs/
.\gradlew.bat runClient    # client di sviluppo
.\gradlew.bat runServer    # server di sviluppo
```

**macOS e Linux**:

```sh
export JAVA_HOME=$(/usr/libexec/java_home -v 25)   # su macOS; altrove indica il tuo JDK 25
./gradlew build
./gradlew runClient
./gradlew runServer
```

### Da doppio clic

| | |
|---|---|
| Windows | `avvia-client.bat` · `avvia-server.bat` |
| macOS | `avvia-client.command` · `avvia-server.command` |

Su Linux gli stessi `.command` si lanciano da terminale con `./avvia-server.command`:
dentro sono `sh` normale, l'estensione serve solo al Finder di macOS.

Gli avviatori trovano Java da soli, e quello del server chiede una volta se accetti
l'[EULA di Mojang](https://aka.ms/MinecraftEULA) — senza, un server non parte.

## Documentazione

- [`DESIGN-solo-leveling.md`](DESIGN-solo-leveling.md) — il design tecnico completo
- [`CLAUDE.md`](CLAUDE.md) — regole di sviluppo e trappole note delle API 26.2
- [`docs/`](docs/) — note di verifica e riepiloghi dei blocchi di lavoro

## Licenza e disclaimer

Codice sotto [CC0 1.0](LICENSE).

Progetto **fan-made e non commerciale**, non affiliato né approvato da Chugong, D&C Media,
Redice Studio o dai detentori dei diritti di *Solo Leveling*. Non contiene asset ufficiali:
nessuna texture, immagine, musica o testo tratti dall'opera.
