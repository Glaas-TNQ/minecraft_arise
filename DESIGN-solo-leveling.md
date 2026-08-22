# "Sistema" — mod stile Solo Leveling: documento di design tecnico

_21 agosto 2026 — analisi di fattibilità, architettura e roadmap_

---

## 0. Verdetto in tre righe

Il concept è fattibile e ben definito, ma **non è un progetto singolo: sono cinque sottosistemi indipendenti**, di cui due (dungeon procedurali ed esercito d'ombra) sono difficili da soli. Fatto in ordine sbagliato, il progetto muore al 40%. Fatto per fasi, con ogni fase giocabile, arriva in fondo.

La parte che sembra più difficile (le statistiche, il livello) è la più facile. La parte che sembra semplice ("i nemici uccisi diventano minion") nasconde il problema architetturale più serio dell'intera mod: **la persistenza dell'esercito** (§4.5).

---

## 1. Chi l'ha già fatto — e cosa ci dice

| Mod | Piattaforma | Cosa copre |
|---|---|---|
| **Solo Leveling: Reawakening (SLR)** | Forge **1.20.1** | Il riferimento più completo: ranghi Hunter e classi, il Sistema, Gate ranked, dungeon, boss, quest giornaliere, prove di Job Change, poteri Ruler/Monarch, **Shadow Army completo** (estrazione, storage, evocazione, comandi, livellamento, ranghi, equipaggiamento, formazioni salvate), party |
| **DALeveling** | Fabric | HUD RPG animata; alla morte di un mob c'è una **chance di estrarne l'ombra**; le ombre si evocano come lupi-ombra addomesticati con HP/danno/velocità scalati sul livello |
| **Solo Leveling: Rise Of The Monarchs** | Forge | Risveglio dei mob uccisi con parola chiave, soldati-ombra controllabili e nominabili tramite menu |

**Cosa se ne ricava:**

1. **È fattibile davvero** — SLR dimostra che l'intero set di feature che hai descritto sta in una mod sola.
2. **DALeveling ha già risolto il problema difficile prendendo una scorciatoia intelligente**: le ombre non sono copie del mob ucciso, sono **lupi-ombra** con statistiche scalate. Un solo tipo di entità da modellare, renderizzare e far ragionare. È il compromesso che consiglio per la prima versione (§4.5).
3. **La reference migliore è su Forge 1.20.1, non su 26.2.** È una tensione reale: 1.20.1 ha l'ecosistema e gli esempi, 26.2 ha il codice non offuscato che rende il lavoro con me molto più affidabile. La mia raccomandazione resta **26.2**, ma sappi che quando cercheremo "come ha fatto X" troveremo codice di 1.20.1 da tradurre.
4. **Lo spazio è occupato ma non saturo.** Ha senso farla se l'obiettivo è il tuo (imparare, avere la *tua* versione, meccaniche che le altre non hanno). Non ha senso come "faccio la stessa cosa ma meglio" al primo progetto di modding.

---

## 2. Scomposizione in sistemi

Cinque pilastri, in ordine di dipendenza (ognuno usa i precedenti):

```
S1 — Il Sistema (dati giocatore: livello, XP, punti statistica, stato)
     ↓
S2 — Statistiche → attributi (vita, velocità, forza…)
     ↓
S3 — Interfaccia (HUD "Sistema", pannello statistiche, notifiche di livello)
     ↓
S4 — Gate/Dungeon procedurali (fonte di XP e di sfida)
     ↓
S5 — Esercito d'ombra (il potere firma)
     ↓
S6 — Abilità attive (trasversale: dipende da S1, usa S5)
```

| Sistema | Difficoltà | Rischio principale |
|---|---|---|
| S1 Dati giocatore | 🟢 Bassa | Persistenza al respawn / cambio dimensione |
| S2 Statistiche | 🟢 Bassa | Limiti (range) degli attributi vanilla |
| S3 UI/HUD | 🟡 Media | API HUD di Fabric cambiata di recente |
| S6 Abilità | 🟡 Media | Cooldown e validazione lato server |
| S4 Dungeon | 🔴 Alta | Generazione, istanze, reset, multiplayer |
| S5 Esercito d'ombra | 🔴 Alta | Persistenza, AI, performance, pathfinding |

---

## 3. Decisioni architetturali (con raccomandazione)

### 3.1 Dove vivono i dati del giocatore

**Raccomandato: Fabric Data Attachment API.** Permette di attaccare dati a un giocatore con persistenza su disco (via Codec) e sincronizzazione automatica verso il client (via `syncWith` + un predicato che decide chi riceve cosa). È l'alternativa moderna ai mixin su `PlayerEntity` e ai "capability" di Forge.

Regola d'oro documentata: **usare tipi immutabili** e aggiornarli solo tramite i metodi dell'API, altrimenti persistenza e sync si rompono in modo silenzioso.

**Trappola nota da mettere subito nel `CLAUDE.md`:** alla morte/respawn e al cambio dimensione il giocatore viene *ricreato*. I dati vanno copiati esplicitamente (`ServerPlayerEvents.COPY_FROM`). È il bug numero uno di ogni mod RPG: perdi il livello quando muori, e te ne accorgi tardi.

### 3.2 Come le statistiche diventano effetti reali

**Raccomandato: `EntityAttributeModifier` sugli attributi vanilla.**

- `minecraft:max_health` → statistica Vitalità
- `minecraft:movement_speed` → Agilità
- `minecraft:attack_damage` → Forza
- `minecraft:armor` / `armor_toughness` → Resistenza
- `minecraft:attack_speed`, `luck`, `knockback_resistance` → secondarie

Da sapere:
- dalla 1.21.2 gli id hanno perso il prefisso `generic` (`generic.max_health` → `minecraft:max_health`);
- dalla 1.21 i modificatori sono identificati da un **Identifier namespacato**, non più da UUID;
- **modificatori con lo stesso id sullo stesso attributo non si sommano**: vale l'ultimo applicato. Questo è un vantaggio — significa che a ogni level-up ricalcoliamo e *rimpiazziamo* un unico modificatore per statistica, invece di accumularne uno per livello (che è l'errore classico che moltiplica la vita per 40);
- gli attributi hanno un **range massimo**: esiste una mod famosa (AttributeFix) che nasce apposta per rimuovere quei limiti. Se punti a numeri stile Solo Leveling (velocità assurda, migliaia di HP) va verificato il tetto della versione target e, se serve, previsto un cap di design nostro.

**Nota di design, non tecnica:** `movement_speed` moltiplicata oltre ~3× rompe il gioco (il client non regge la collisione, cadi nei blocchi, il server ti rimbalza indietro). La velocità va spinta con cautela e probabilmente con effetti/abilità a tempo, non come statistica passiva illimitata.

### 3.3 Come generare i dungeon

Tre strade reali:

| Approccio | Come funziona | Pro | Contro |
|---|---|---|---|
| **A. Strutture Jigsaw** in una dimensione dedicata | Costruisci le stanze in-game, le salvi come template, il gioco le incastra secondo regole | Il tempo lo spendi a *costruire*, non a scrivere algoritmi; è la via ufficiale per dungeon e villaggi | Poco controllo: difficile garantire "esattamente un boss in fondo", chiudere il dungeon, sapere quando è completato |
| **B. ChunkGenerator custom** | Scrivi tu la generazione del mondo | Controllo totale | La doc Fabric lo **sconsiglia esplicitamente** se non stai creando un mondo non basato su rumore. Costoso e fragile |
| **C. Dimensione vuota + assemblaggio a runtime** ⭐ | Dimensione "void", e quando il giocatore entra in un Gate un algoritmo *nostro* genera un grafo di stanze e piazza i template (`StructureTemplate.place()`) alle coordinate calcolate | Controllo totale sul layout (ingresso → stanze → boss), sul popolamento dei mob, sul reset dell'istanza, e sappiamo sempre in che stanza sei | Dobbiamo scrivere noi l'algoritmo di layout e la gestione delle istanze |

**Raccomandato: C.** È l'unico che regge la meccanica dei Gate come nel webtoon: un'istanza personale, con un boss garantito in fondo, che si chiude e si può rigenerare. Il precedente esiste: la mod *Instanced Dungeons* fa esattamente questo (blocco che teleporta nella propria istanza, reset che rigenera un layout casuale), e *Dimensional Dungeons* mette tutte le istanze in una sola dimensione separandole con 8 chunk di vuoto — trucco che copieremo, perché evita di creare una dimensione per giocatore.

Il layout procedurale non deve essere sofisticato per essere divertente: un grafo a stanze con corridoi (algoritmo tipo "random walk con budget di stanze" o BSP) è più che sufficiente. La difficoltà vera è la **gestione del ciclo di vita dell'istanza**: allocazione, popolamento, condizione di vittoria, uscita, pulizia dei chunk, cosa succede se esci offline dentro un dungeon.

### 3.4 Come funzionano le ombre — la decisione più importante

Due filosofie:

**Opzione 1 — Un'unica entità `ShadowEntity`** che memorizza il tipo di mob originale e ne renderizza il modello con un materiale nero/traslucido.
- ✅ Look fedele al webtoon, controllo totale sull'AI, statistiche indipendenti dal mob originale, nessuna interferenza con l'AI vanilla.
- ❌ Il rendering dinamico "prendi il modello di qualsiasi mob e coloralo di nero" è la parte graficamente più complessa dell'intera mod, e in 26.2 il rendering è in movimento (migrazione a Blaze3D, backend Vulkan sperimentale).

**Opzione 2 — Riusare il mob vanilla**: spawni una copia dell'entità originale, le assegni un proprietario, le sostituisci i goal di AI, e la marchi come ombra.
- ✅ Molto più veloce da implementare, l'AI e le animazioni esistono già. È l'approccio delle mod "tame any mob" (owner via NBT + goal disabilitati).
- ❌ Aspetto non "ombra" senza un mixin sul renderer; e l'AI vanilla di certi mob combatte contro quella nostra.

**Opzione 3 (raccomandata per la v1) — La scorciatoia di DALeveling**: un solo tipo di entità-ombra (o due o tre archetipi: melee / ranged / tank) le cui statistiche sono scalate dal mob da cui è stata estratta e dal tuo livello. Il mob ucciso determina *rango, statistiche e nome* dell'ombra, non il suo modello.
- ✅ Un modello da fare, un'AI da scrivere, prestazioni prevedibili, e resta fedele alla fantasia ("il mio esercito cresce e diventa più forte").
- Migrazione futura verso l'Opzione 1 possibile senza rifare la logica, perché cambia solo il render.

### 3.5 Persistenza dell'esercito — il problema che va risolto ora, non dopo

**Le ombre non possono esistere come entità quando non sono evocate.** Un'entità nel mondo occupa un chunk, viene caricata, salvata, può despawnare, morire fuori schermo, perdersi in un'altra dimensione. Un esercito di 50 ombre "parcheggiate" è un disastro di performance e di bug.

L'architettura corretta — che per fortuna coincide con la fantasia del webtoon:

```
Esercito (dati, nell'attachment del giocatore)      Campo di battaglia (entità vive)
┌──────────────────────────────────────┐            ┌─────────────────────────┐
│ [{tipo, nome, rango, livello, stat}, │  evoca →   │ ShadowEntity × N        │
│  {…}, {…}]  — solo NBT, zero entità  │  ← richiama│ (max evocabili < totali)│
└──────────────────────────────────────┘            └─────────────────────────┘
```

Le ombre sono **dati** nell'inventario dell'anima; diventano entità solo su evocazione, e alla morte/richiamo tornano dati (con eventuale penalità). Questo risolve in un colpo solo: persistenza tra sessioni, limite di ombre evocabili contemporaneamente (il tuo "tot numero"), performance, e il tema narrativo.

### 3.6 Client e server

Tutto ciò che è **logica** (XP, livello, cooldown, evocazione, danni) vive **sul server**. Il client riceve solo lo stato da disegnare e manda solo *intenzioni* ("ho premuto il tasto abilità 1"). Serve quindi da subito il networking con payload custom nei due sensi.

È noioso e sembra sovradimensionato in singleplayer — ma in Minecraft **anche il singleplayer è un server**, e saltare questo passaggio significa riscrivere metà della mod quando la provi con un amico.

---

## 4. Cosa serve, sistema per sistema

### 4.1 S1 — Il Sistema (dati giocatore) 🟢

- Attachment persistente e sincronizzato: `livello`, `xp`, `xpAlProssimoLivello`, `puntiStatDisponibili`, `mappa statistiche`, `flag risvegliato`, `abilità sbloccate`, `esercito`.
- Curva di XP: formula parametrica in config, non hardcoded (la ribilancerai venti volte).
- Guadagno XP: evento di morte di un'entità (`ServerLivingEntityEvents.AFTER_DEATH`), attribuzione al giocatore che ha inflitto il colpo, XP in base al tipo di mob e al contesto (dentro un Gate vale di più).
- Level-up: ricalcolo attributi + notifica al client + suono + punti statistica.
- Comandi di debug (`/system level set`, `/system xp add`) — **da fare al giorno uno**, altrimenti ogni test richiede mezz'ora di gioco.
- **Il "Risveglio"**: nel webtoon non tutti hanno il Sistema. Un rito di attivazione (item, struttura, evento) rende il tutto molto più tematico ed è banale da implementare.

### 4.2 S2 — Statistiche 🟢

- Enum di statistiche → mappa statistica/attributo con formula di conversione.
- Un solo `EntityAttributeModifier` per statistica, con Identifier fisso, ricalcolato e rimpiazzato a ogni cambiamento.
- Riapplicazione al login e dopo il respawn.
- Interfaccia per spendere i punti (parte di S3).

### 4.3 S3 — Interfaccia 🟡

- HUD sempre visibile: barra XP, livello, eventualmente HP stile RPG.
- Schermata "Stato" (tasto dedicato): statistiche, punti da spendere, ombre possedute.
- Notifiche stile Sistema (il riquadro azzurro che appare) — è il dettaglio che dà l'identità alla mod ed è puro rendering 2D, quindi economico rispetto all'effetto che fa.
- ⚠️ **Verifica obbligatoria sulla doc 26.2**: l'API HUD di Fabric è cambiata di recente, e in 26.2 i metodi di GUI/HUD sono stati spostati in classi dedicate con la gestione delle schermate passata da `Minecraft` a `Minecraft.gui`. Qui il codice che troveremo online sarà quasi sempre obsoleto.

### 4.4 S4 — Gate e dungeon 🔴

- Dimensione dedicata (JSON: `dimension_type` + `dimension` con generatore piatto/vuoto).
- **Gate**: struttura/blocco che appare nell'overworld, con rango (E→S) legato al livello del giocatore.
- Allocatore di istanze: assegna a ogni run un'area della dimensione, distanziata dalle altre.
- Generatore di layout: grafo di stanze → coordinate → piazzamento template.
- Set di stanze costruite a mano e salvate come template (**questo è lavoro tuo, non mio**: le stanze vanno costruite in gioco).
- Popolamento: tabella di spawn per rango, boss garantito nella stanza terminale.
- Ciclo di vita: ingresso, timer/obiettivo, morte del boss → ricompense → uscita, pulizia.
- Casi limite che vanno decisi in anticipo: disconnessione dentro il dungeon, morte, party (entrano in due?), reset, cosa succede se scavi fuori dal dungeon.

### 4.5 S5 — Esercito d'ombra 🔴

- Estrazione: sul cadavere di un mob, comando/abilità con probabilità di successo basata su livello, rango del mob e statistica dedicata.
- Storage come dati (§3.5), con capacità massima crescente.
- `ShadowEntity` con: proprietario, statistiche scalate, AI (segui il proprietario / attacca il suo bersaglio / difendilo / mantieni la posizione), stati (Segui / Attacca / Resta / Richiama).
- Limite di evocazioni simultanee = la tua meccanica del "tot numero".
- Comandi rapidi: evoca tutte, richiama tutte, attacca il bersaglio guardato.
- Progressione delle ombre: livello proprio, ranghi, eventualmente nomi.
- ⚠️ **Attenzione performance**: 20 entità con pathfinding attivo che seguono il giocatore sono già pesanti. Il limite di evocazioni non è solo una scelta di design, è un vincolo tecnico.

### 4.6 S6 — Abilità 🟡

- Registro di abilità (dato/JSON, non classi hardcoded), sbloccate per livello o per evento.
- Keybind client → payload → validazione server (livello, cooldown, risorsa) → effetto.
- Barra abilità nell'HUD con cooldown.
- Candidate tematiche: scatto/blink, aura di intimidazione, potenziamento temporaneo, evocazione di massa, estrazione, "Arise".

---

## 5. Roadmap in fasi (ogni fase è giocabile)

| Fase | Contenuto | Criterio di "fatto" |
|---|---|---|
| **F0 — Fondamenta** | Template Fabric 26.2 compilabile, `CLAUDE.md`, un item di prova, comandi di debug | `runClient` parte, l'item esiste in creative |
| **F1 — Il Sistema** | S1 + S2: XP dai mob, livelli, punti statistica, attributi che cambiano davvero | Uccidi zombie → sali di livello → hai più vita e sei più veloce, e il tutto sopravvive a morte e riavvio |
| **F2 — Il volto** | S3: HUD, schermata di stato, notifiche del Sistema | Vedi il tuo livello e spendi punti senza comandi |
| **F3 — L'ombra** | S5 in versione minima: estrazione da un solo tipo di mob, una sola ombra evocabile, che ti segue e combatte | Uccidi uno zombie, lo "arise", ti segue e attacca per te; c'è ancora dopo un riavvio |
| **F4 — Esercito** | S5 completo: storage, cap crescente, comandi, livellamento delle ombre | Evochi 5 ombre e le comandi |
| **F5 — I Gate** | S4: dimensione, un layout procedurale semplice, 3-4 stanze, boss, ricompense | Entri in un Gate, lo completi, esci con XP e un'ombra rara |
| **F6 — Abilità** | S6 + bilanciamento + config | Tre abilità funzionanti con cooldown |
| **F7 — Rifinitura** | Suoni, particellari, aspetto "ombra" vero (Opzione 1 di §3.4), traduzioni | — |

**Stima onesta:** F0–F2 è questione di giorni. F3–F4 è la fase dove si impara davvero. F5 è un progetto a sé. Chi ha fatto SLR ci ha messo mesi.

**Consiglio forte: non partire dai dungeon**, anche se sono la parte che immagini meglio. Sono il sistema con più casi limite e quello che dà meno soddisfazione per riga di codice scritta all'inizio.

---

## 6. Rischi specifici di questo progetto

1. **Ambizione vs. finitura.** Il concept ha la dimensione di una mod fatta da un team. La roadmap a fasi è la contromisura: ogni fase è una mod pubblicabile, non un cantiere.
2. **Testare l'AI e i dungeon è lento.** Compilare non dimostra niente qui: un'ombra che non ti segue, un dungeon che genera una stanza dentro l'altra, un boss che non spawna — si vedono solo in gioco. È il motivo per cui, per *questa* mod in particolare, il server MCP che mi permette di lanciare il gioco, eseguire comandi, leggere i log e vedere il risultato (§3.3 del report precedente) passa da "carino" a "molto utile".
3. **Bilanciamento.** Tutti i numeri (curva XP, moltiplicatori, cap) devono stare in config dal primo giorno.
4. **API in movimento su 26.2** — rendering e GUI soprattutto. Il codice trovato online sarà per 1.20.1/1.21.
5. **Proprietà intellettuale.** Solo Leveling è un'opera protetta (Chugong / D&C Media / Redice Studio). Una mod fan-made non commerciale è la norma nell'ecosistema, ma per pubblicare conviene: nessun asset ufficiale (niente texture, musiche o immagini ricavate dall'anime o dal webtoon), disclaimer "fan-made, non affiliata", e prudenza con nomi e loghi ufficiali. Nota che le mod esistenti usano nomi come "Solo Craft" o "Rise of the Monarchs" proprio per questo. Si aggiunge la regola Modrinth sulle immagini generate da AI (vietate) e sulla disclosure per il codice assistito da AI.

---

## 7. Decisioni che servono da te per iniziare

1. **Versione + loader**: confermi Fabric 26.2 (mia raccomandazione) o preferisci 1.20.1 per stare vicino alle mod di riferimento?
2. **Aspetto delle ombre**: archetipo unico scalato (veloce, raccomandato per la v1) o copia del mob originale?
3. **Nome della mod e mod id** — serve per generare il template (esempi neutri: *Ascension*, *The System*, *Monarch*, *Gate*).
4. **Solo/multiplayer**: i Gate devono supportare i party da subito o basta il singolo giocatore?
5. Vuoi che il primo obiettivo concreto sia **F1 (il Sistema con XP e statistiche)**? È la mia proposta: è la spina dorsale, si testa in due minuti in gioco, e ti dà subito la sensazione giusta.

---

## Fonti

- [Solo Leveling: Reawakening — CurseForge](https://www.curseforge.com/minecraft/mc-mods/solo-craft-reawakening) · [Modrinth](https://modrinth.com/project/YdsLXFph)
- [DALeveling — Solo Leveling Mod (Modrinth)](https://modrinth.com/project/cYju10Ms)
- [Solo Leveling: Rise Of The Monarchs (CurseForge)](https://www.curseforge.com/minecraft/mc-mods/solo-leveling-shadows)
- [Fabric — Data Attachments](https://docs.fabricmc.net/develop/data-attachments)
- [Fabric — Entity Attributes](https://docs.fabricmc.net/develop/entities/attributes)
- [Fabric Wiki — Custom Chunk Generators](https://fabricmc.net/wiki/tutorial:chunkgenerator)
- [Fabric Wiki — Jigsaws](https://wiki.fabricmc.net/tutorial:1.15:jigsaw)
- [Instanced Dungeons (Modrinth)](https://modrinth.com/mod/instanced-dungeons)
- [Dimensional Dungeons (Modrinth)](https://modrinth.com/project/Q8XlZjYF)
- [Petting — Tame any mob! (Modrinth)](https://modrinth.com/mod/petting)
- [Minecraft Wiki — Attribute](https://minecraft.wiki/w/Attribute)
- [Fabric for Minecraft 26.2](https://fabricmc.net/2026/06/15/262.html)
- [Modrinth — Content Rules](https://modrinth.com/legal/rules)
