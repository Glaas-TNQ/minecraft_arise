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

## 8. Equipaggiamento, Abyss Shop e gemme

Il pilastro che mancava. Fino a qui la mod ha **progressione senza acquisizione**: i numeri
crescono, ma non si ottiene mai niente, e i soul coin hanno due soli sbocchi (potenziare un'ombra,
pagare un'abilità). Questa sezione chiude il ciclo *Gate → bottino → equipaggiamento → Gate più
profondi*.

### 8.1 L'equipaggiamento è un dato, non un item

**Decisione, ed è la più importante di tutta la sezione.** Un pezzo di equipaggiamento è un
record dentro un attachment del giocatore, non un `ItemStack` registrato.

La ragione è aritmetica. Gli slot previsti sono una ventina; moltiplicati per sei ranghi e per le
varianti di base fanno diverse centinaia di texture da disegnare a mano — e il progetto ha già un
debito aperto sulla texture dell'ombra. Nessuna di quelle immagini può essere generata: le regole
di Modrinth vietano gli asset prodotti da IA.

È lo stesso principio già adottato per l'esercito (§3.5): *le ombre non evocate non sono entità*.
Qui: **l'equipaggiamento posseduto non è un oggetto**. Lo disegniamo noi nella nostra schermata,
con un'icona per tipo di slot tinta del colore del rango.

Cosa si guadagna:

- zero texture, zero modelli, zero data generation, zero registrazioni di item;
- nessuna collisione con i quattro slot armatura di vanilla, che venti slot non potrebbero
  contenere comunque;
- niente duplicazioni, niente pezzi persi nella lava, niente interazione con le mod di inventario;
- un pezzo può essere **generato** invece che disegnato: base × rango × modificatori estratti,
  varietà infinita a costo zero (stessa idea di `GateOffer`, tirata da un seed).

Cosa si perde, detto chiaramente: il pezzo non sta nell'inventario, non si butta per terra, non si
passa a un amico infilandolo in un baule. Lo scambio fra giocatori, se servirà, diventerà un
trasferimento fra due schermate — fattibile, ma va scritto.

> **Revisione (blocco D1).** Questa decisione è stata **rovesciata**. La premessa era che
> «oggetto» significasse «texture nuova»: non è vero. Il corpo di un pezzo lo prestano gli item
> vanilla — cuoio → rame → maglia → ferro → diamante → netherite per le armature, legno → rame →
> pietra → ferro → diamante → netherite per le armi: due scale che il giocatore legge già da anni,
> sei gradini per sei ranghi — e per gli accessori uno sprite riconoscibile per tipo. Il
> `GearPiece` resta esattamente il record che era, ma viaggia dentro un componente dati
> (`arise:gear_piece`) invece che dentro una lista.
>
> Cosa si guadagna, e che una lista non avrebbe mai dato: il bottino **cade per terra** invece di
> comparire in chat; testa, torso, gambe, piedi e mano tornano alle caselle di vanilla, dove si
> vedono addosso al personaggio; trascinamento, shift-click e tasti della barra rapida arrivano
> gratis; un pezzo si mette in un baule, si ripara all'incudine, si incanta.
>
> Cosa si è dovuto costruire in cambio: uno **spazio dimensionale** (27 caselle) che raccoglie da
> solo l'equipaggiamento che passa dall'inventario, e un **vincolo all'anima** — alla morte i pezzi
> vengono ritirati e restituiti al respawn — perché adesso sarebbero oggetti veri per terra.
>
> Restano nostre solo le otto famiglie di caselle che vanilla non ha: guanti, cintura, spalline,
> mantello, collana, talismano, orecchini, anelli. Il §8.4 sugli sblocchi vale ancora, ed è per
> quelle.

### 8.2 Le sorgenti di statistica si sommano prima di diventare modificatori

Oggi ogni `Stat` ha un modificatore con id fisso, rimpiazzato a ogni ricalcolo (regola §6 di
`CLAUDE.md`). Se anche equipaggiamento e gemme concedono forza, **non** si può aggiungere un
secondo modificatore per sorgente: sarebbe esattamente il bug che quella regola previene.

Il modello diventa:

```
punti spesi  ┐
equipaggio   ├─→  totale per statistica  ─→  UN modificatore per statistica (id invariato)
gemme        │
buff a tempo ┘
```

La regola §6 non cambia, cambia solo *cosa* ci si scrive dentro. Questa rifattorizzazione va fatta
**prima** dell'equipaggiamento, non durante.

Ne consegue che `Stat` si allarga oltre le quattro statistiche spendibili: quelle restano le sole
su cui si spendono punti (`spendable`), ma l'equipaggiamento può estrarre anche tempra, prontezza,
saldezza, slancio, allungo, assorbimento, fortuna e impeto. Senza questa varietà due pezzi dello
stesso slot sarebbero indistinguibili.

### 8.3 Il rango è la rarità

Non si inventano *comune / raro / epico*: esiste già `Rank` E→S, con i suoi colori e le sue
etichette, usato da ombre e Gate. Un anello è **di rango A**, e questo determina:

| | E | D | C | B | A | S |
|---|---|---|---|---|---|---|
| modificatori estratti | 1 | 1 | 2 | 3 | 3 | 4 |
| incastonature | 0 | 0 | 1 | 2 | 3 | 4 |
| moltiplicatore di potenza | 1,0 | 1,4 | 2,0 | 2,8 | 4,0 | 5,6 |

Tutti numeri di config.

### 8.4 Gli slot si sbloccano

Venti caselle vuote davanti a un giocatore di livello 1 si leggono come una lista di faccende. Gli
slot esistono tutti, ma si aprono al salire del rango del Cacciatore: si comincia con due anelli e
si arriva a dieci, si comincia con due orecchini e si arriva a quattro. Ogni sblocco è un momento,
e narrativamente regge — la capacità di un Cacciatore cresce.

Gli anelli restano i contributori **più deboli** proprio perché sono i più numerosi: il tetto non
deve banalizzare tutto il resto.

### 8.5 Da dove arriva la roba

Se l'unica fonte è il negozio, i Gate diventano un bancomat e il gioco si riduce a "farma monete,
compra". Le fonti si dividono:

- **i Gate lasciano cadere l'equipaggiamento**, di rango scalato su quello del Gate;
- **i boss lasciano le gemme** e le scroll;
- **l'Abyss Shop vende in modo affidabile ma caro** — è la rete di sicurezza di chi non è stato
  fortunato, non la fonte principale.

Le scroll che alzano una statistica in permanenza **non si comprano**: i soul coin si farmano
all'infinito, e statistiche comprabili significherebbero statistiche infinite. Cadono dai boss.

### 8.6 Le gemme

Una gemma incastonata dà statistiche *e* un effetto passivo: furto di vita, probabilità di
estrarre un'ombra da sola alla morte di un nemico, bonus ai soul coin sopra un certo rango di Gate.

**Non abilità attive**, almeno per ora: quelle sono un enum chiuso di quattro voci, legato a
quattro tasti e a quattro caselle nell'HUD, e aggiungerne una quinta è un lavoro a sé. Una gemma
che *sblocchi* un quinto slot abilità è un buon obiettivo successivo.

Incastonare si fa ovunque. **Estrarre una gemma senza distruggerla si fa solo al banco
dell'Associazione dei Cacciatori**, in città: è un pozzo di risorse elegante e finalmente un motivo
per viaggiare.

### 8.7 Ordine di costruzione

| Blocco | Contenuto | Criterio di "fatto" |
|---|---|---|
| **B1** | Somma delle sorgenti; slot, zaino, schermata del Cacciatore; comandi di debug | equipaggi un anello, le statistiche cambiano, sopravvive a morte e riavvio |
| **B2** | Abyss Shop: stock a rotazione tirato da un seed, prezzi in config, consumabili | spendi soul coin e ti ritrovi il pezzo addosso |
| **B3** | Varchi spontanei nel mondo | esci di casa e trovi un Gate che non hai evocato tu |
| **B4** | Gemme, incastonature, effetti passivi, estrazione al banco | una gemma di furto vita si vede funzionare |
| **B5** | Bottino: Gate e boss lasciano pezzi, gemme e scroll | chiudi un Gate di rango B ed esci con qualcosa che non avevi |

---

## 9. Il risveglio e la catena degli incarichi

Fino a qui si nasceva con tutto acceso. Il problema non era che fosse troppo: era che non c'era
**nessun momento in cui qualcosa arrivava**. Una mod con nove sistemi e zero consegne.

### 9.1 Il primo incarico non si può cercare

Si comincia da persona qualunque: niente HUD, niente XP, niente soul coin. Prima del risveglio non
esiste nessun Sistema che misuri, quindi uccidere non dà niente — ma **conta lo stesso** per la
catena, perché è così che ci si arriva.

Il risveglio si completa arrivando a un passo dalla morte. Si intercetta in `ALLOW_DEATH`, che è
l'unico evento capace di dire *no, questa morte non avviene*: rianimare dopo il fatto sarebbe stato
visibile — schermata di morte, inventario a terra, punto di respawn.

### 9.2 Lo stato è un numero

A che punto della lista si è arrivati, e quanto si è fatto dell'incarico corrente. Gli incarichi
completati sono quelli prima dell'indice; i sistemi sbloccati sono quelli che quegli incarichi
concedono. Nessun elenco di cose fatte, nessun insieme di permessi salvato a parte: **un solo numero
non può entrare in contraddizione con se stesso**.

Ne consegue che l'ordine di dichiarazione dell'enum *è* la catena, e che aggiungere un incarico in
mezzo sposta tutti quelli dopo — accettabile, e molto meglio di un grafo di prerequisiti da tenere
coerente.

### 9.3 Ogni incarico apre un sistema

| # | Incarico | Chiede | Apre |
|---|---|---|---|
| 1 | Il risveglio | arrivare a un passo dalla morte | il Sistema |
| 2 | Primi passi | livello 3 | le statistiche |
| 3 | Il conto dei caduti | 15 creature | l'esercito d'ombra |
| 4 | La prima ombra | estrarne 1 | le abilità |
| 5 | Il potere si usa | 3 abilità | l'equipaggiamento *(+ un pezzo di rango E)* |
| 6 | Qualcosa addosso | indossarne 1 | i Gate |
| 7 | Il primo varco | chiudere 1 Gate | l'Abyss Shop |
| 8 | Il primo affare | comprare 1 voce | il viaggio fra Associazioni |
| 9 | L'Associazione | metterci piede | le gemme *(+ un pezzo di rango D)* |

L'incarico successivo parla quasi sempre del sistema appena concesso. È il modo più economico di
insegnare una mod senza scrivere un tutorial.

### 9.4 Chi sa cosa

`QuestManager.advance` viene chiamato **da dentro il sistema che sa se l'azione è riuscita davvero**
— estrarre lo dice `ShadowManager`, comprare lo dice `ShopManager`. `QuestManager.require` sta in
cima a ogni gestore come guardiano. Nessun sistema sa quali incarichi esistono, e gli incarichi non
sanno come funzionano i sistemi.

Il rifiuto lato client, quando si preme un tasto per qualcosa che non si ha ancora, è una cortesia e
non una difesa: un client modificato aprirebbe la schermata comunque, e non servirebbe a niente
perché ogni azione dentro passa dal server.

---

## 10. L'Officina delle Anime — costruzione di base e automazione

### 10.1 Cosa fanno le altre mod, e cosa prendiamo

Quattro scuole, tutte vive, tutte con una risposta diversa alla stessa domanda: *cosa costringe il
giocatore a costruire invece che a cliccare?*

| Scuola | Esempio | La valuta | Cosa insegna |
|---|---|---|---|
| **Energia astratta** | Thermal Expansion, Mekanism | RF/FE in un cavo | La macchina è una scatola con una barra. Facile da estendere, ma ogni macchina nuova è la stessa scatola con un'altra ricetta. Da Thermal prendiamo i **lati riconfigurabili** e gli **slot di potenziamento**: la macchina è un oggetto che si *regola*, non solo che si accende. |
| **Meccanica visibile** | Create | Rotazione su alberi e cinghie | Niente barre: il processo si **vede** girare nel mondo. Costa molto più lavoro di rendering, ma è l'unica scuola dove una fabbrica è bella da guardare. Prendiamo il principio, non l'implementazione: **la macchina deve dire da fuori cosa sta facendo** (particellari, luce, suono). |
| **Vita e decadimento** | Botania | Mana da fiori che consumano qualcosa | I generatori passivi **decadono**; quelli attivi no ma vogliono un input. È il modo migliore che conosco per impedire che l'automazione diventi "piazzo e dimentico". |
| **Rete e domanda** | Applied Energistics | Canali, richieste su ordinazione | La rete ha un **costo di struttura** oltre che di energia. Troppo per noi adesso, ma è la direzione se un giorno le officine si collegheranno fra loro. |

**La conclusione per Arise.** Non aggiungiamo una valuta energetica: ne abbiamo già una, e ha un
peso narrativo che nessun RF avrà mai. **L'anima è l'energia.** Ma un'anima non si *brucia*: si
*mette a lavorare*. È la differenza che tiene insieme automazione e Solo Leveling — il Monarca non
consuma i suoi morti, li impiega.

### 10.2 Le anime in esubero

L'esercito ha un tetto (`shadows.capacityAt(livello)`). Fino a oggi estrarre a esercito pieno
significava buttare via il cadavere. Da qui in avanti l'estrazione riuscita che non trova posto
produce un'**Anima Errante**: un oggetto, con un UUID, un mob d'origine, un livello e un rango.
Anche il congedo di un'ombra restituisce la sua anima, oltre ai soul coin.

Un'Anima Errante fa esattamente tre cose:

1. **lavora** — infilata in una macchina è un operaio. **Non viene consumata**: si rimette dentro,
   si toglie, si sposta su un'altra macchina. Il suo *vigore* (potenza del mob × livello) decide
   quanto va veloce la macchina che la ospita;
2. **si fonde** — quattro anime più un catalizzatore diventano un'anima sola, più forte e con un
   **tratto**;
3. **si arruola** — click destro, e se c'è posto nell'esercito diventa un'ombra vera, con il
   livello che ha raggiunto nel Crogiolo.

Questo chiude il cerchio: l'automazione non è un ramo laterale della mod, è il modo in cui si
coltiva l'esercito quando non si sta combattendo.

### 10.3 I quattro macchinari

Quattro blocchi, un anello. Ognuno produce quello che serve al successivo.

| # | Blocco | Cosa fa | Caselle |
|---|---|---|---|
| 1 | **Richiamo d'Anime** (`arise:soul_lure`) | Le anime installate attirano l'attenzione dell'Abisso: ogni tanto materializza un'Anima Errante nuova, di rango legato al vigore installato. È l'ingresso dell'anello. | 2 operai → 3 uscite |
| 2 | **Crogiolo delle Anime** (`arise:soul_crucible`) | **La fusione.** Quattro anime + un catalizzatore → una sola anima, con la somma dei livelli, il rango del pezzo migliore e un **tratto** nuovo. Il catalizzatore decide quanti tratti può reggere il risultato. | 4 anime + 1 catalizzatore → 1 uscita |
| 3 | **Fucina d'Ombra** (`arise:shadow_forge`) | Gli operai fondono e macinano **senza carburante**: qualunque cosa abbia una ricetta di fusione, più il raddoppio dei minerali. Il collegamento con l'automazione vanilla: tramoggia sopra, tramoggia sotto. | 3 operai + 1 ingresso → 1 uscita |
| 4 | **Pozzo dell'Abisso** (`arise:abyss_well`) | Gli operai vengono *munti*: soul coin al proprietario e, ogni tanto, un **catalizzatore**. È ciò che rende un'anima scarsa comunque utile, e chiude l'anello alimentando il Crogiolo. | 4 operai → 1 uscita |

**Perché non hanno una barra dell'energia.** Il livello di riempimento di una macchina è il numero
e la qualità delle anime che ci stanno dentro, e quello si vede aprendo la macchina. Una seconda
barra racconterebbe la stessa cosa due volte.

**Perché gli operai non si consumano mai.** Perché il gesto interessante è *scegliere dove
metterli*, non *ricomprarli*. Un'anima di rango S nel Pozzo è sprecata; nella Fucina fa volare la
produzione. Se le anime bruciassero, la scelta la farebbe la scorta, non il giocatore.

**Il decadimento che ci prendiamo da Botania**, in versione mite: nulla marcisce, ma il Richiamo
rallenta se lo si lascia con le stesse anime troppo a lungo — no. *Rinunciato*: sarebbe
manutenzione senza decisione. Al suo posto il limite è il tetto di uscita: il Richiamo si ferma
quando le sue tre caselle sono piene, quindi un'officina che nessuno svuota si spegne da sola.

### 10.4 I catalizzatori

Consumabili, sei gradi come i ranghi. Il grado decide **quanti tratti** può reggere l'anima che
esce dal Crogiolo (E–D: uno; C–B: due; A–S: tre) e quanta parte dei livelli sopravvive alla
fusione. Si ottengono dal Pozzo dell'Abisso, dai boss dei Gate e dall'Abyss Shop.

### 10.5 I tratti

Cinque, esclusivi, uno per fusione. Sono il motivo per cui vale la pena fondere invece che
accumulare.

| Tratto | Sull'operaio | Sull'ombra arruolata |
|---|---|---|
| **Ardore** | +25% velocità della macchina | — |
| **Avidità** | +50% soul coin dal Pozzo | — |
| **Tenacia** | 25% di raddoppiare l'uscita | — |
| **Risonanza** | 30% che il Crogiolo non consumi il catalizzatore | — |
| **Ferocia** | — | +20% danno |

### 10.6 Il tempo di recupero delle ombre

Regola nuova e indipendente dall'officina, ma della stessa famiglia: **un'ombra caduta non è
disponibile per sessanta secondi**. Non muore, non si perde — si riprende. Premere il tasto di
evocazione durante il recupero evoca **le altre**, e il messaggio dice quante sono ancora a terra.

È la prima volta che una morte in questa mod costa qualcosa senza togliere niente per sempre, ed
è ciò che rende una postura aggressiva una decisione invece che l'unica scelta sensata.

### 10.7 Il Laboratorio

`/arise arena` costruisce ora un **laboratorio**: la stanza chiusa di prima, più sei varchi già
aperti (uno per rango) su una parete, i quattro macchinari già alimentati sulla parete opposta, e
una cassa con equipaggiamento, gemme, anime, catalizzatori e i materiali vanilla per costruire
tutto da zero. Serve a provare ogni sistema della mod in due minuti invece che in mezz'ora.

---

## 11. La citta' viva — mercato, NPC e la Via dell'Artigiano

### 11.1 Il problema

Le citta' esistono da due cicli e non servono a niente. Sono trecentoventi blocchi di facciate
vuote con una sola porta che si apre — quella dell'Associazione — e dentro non c'e' nessuno. Un
giocatore ci arriva, guarda in alto, e torna a combattere: **non c'e' un motivo per restare**.

Allo stesso tempo l'Officina delle Anime e' arrivata tutta insieme. Quattro macchinari, sei gradi
di catalizzatore, cinque tratti: chi apre il gioco oggi li trova gia' tutti, e la prima cosa che
fa e' leggere una tabella. E' esattamente il difetto che il risveglio (§9) aveva risolto per gli
altri sistemi, tornato a farsi vedere in un angolo nuovo.

Tre lavori, una direzione sola: **dare alla citta' un mestiere, e all'Officina una strada**.

### 11.2 Le citta' nascono col mondo

Oggi le Associazioni si tirano su alla prima entrata di un giocatore. E' il momento sbagliato: chi
entra per la prima volta in un mondo nuovo si trova un messaggio di avanzamento addosso mentre sta
ancora capendo dove guardare.

Il momento giusto e' **l'avvio del server**. Su un mondo nuovo e' letteralmente la creazione del
mondo; su un mondo che le ha gia', il controllo di esistenza costa cinque letture e non fa niente.
Nessuna bandiera da salvare: la prova che una citta' esiste resta la citta' stessa.

E le citta' crescono: **da 320 a 512 blocchi di lato**, cioe' due volte e mezzo la superficie.
E' il numero che governa tutto il resto — isolati, quartieri, distanza fra un monumento e il
mercato — e cambiarlo qui basta perche' le cinque piante si riscalino da sole.

### 11.3 Il Quartiere del Mercato

Attorno alla piazza dell'Associazione, sui quattro lati, **nove botteghe**. Ognuna e' un edificio
vero, con il bancone, la merce sugli scaffali e **una persona dietro il bancone**.

Le persone sono nove, e sono *persone*: il modello e' quello umanoide, e le texture sono le nove
skin predefinite che Minecraft si porta dietro dal 1.19 — alex, ari, efe, kai, makena, noor,
steve, sunny, zuri. Nove volti diversi, riconoscibili, e **zero file nuovi**. Non sono villager:
non camminano, non dormono, non si trasformano in zombie e non cambiano mestiere.

**Cinque vendono, quattro fanno.**

| Bottega | Chi c'e' | Cosa da' |
|---|---|---|
| **Fonderia** | il Fonditore | lingotti, carbone, bacchette di blaze |
| **Cava** | il Cavatore | ardesia, ossidiana, ossidiana piangente |
| **Casa delle Anime** | la Mercante d'Anime | sabbia delle anime, frammenti d'eco, catalizzatori bassi |
| **Cartoleria** | il Cartografo dell'Abisso | i **Progetti** dei macchinari, tramogge, calderoni |
| **Dispensa** | l'Erborista | cibo, mele d'oro, materiale da spedizione |
| **Banco dell'Abisso** | il Cambiavalute | converte soul coin in **Monete d'Anima**, e viceversa |
| **Intermediazione** | il Sensale dei Varchi | apre un varco del rango che chiedi, a pagamento |
| **Bottega del Gemmiere** | la Gemmiera | il banco delle gemme, fuori dall'Associazione |
| **Sportello** | il Segretario | viaggio fra citta' e Abyss Shop |

### 11.4 La Moneta d'Anima

Il mercato ha un problema che il resto della mod non aveva: **la finestra di scambio di Minecraft
sa contare solo oggetti**. I soul coin sono un numero nell'attachment del giocatore, e un numero
non si puo' mettere sul bancone.

Quindi il numero diventa un oggetto. La **Moneta d'Anima** si conia al Banco dell'Abisso — soul
coin dentro, monete fuori — e si riporta indietro allo stesso sportello. E' l'unica valuta che i
cinque mercanti accettano.

Non e' un giro a vuoto: e' cio' che rende il denaro **trasportabile e perdibile**. Un soul coin
sta al sicuro nel Sistema; una moneta sta nello zaino, e se muori in un Gate resta li'.

### 11.5 La Via dell'Artigiano — nove incarichi

La catena degli incarichi si allunga da nove a diciotto, e il secondo arco insegna l'Officina
un pezzo per volta. Ogni incarico apre esattamente cio' che serve al successivo.

| # | Incarico | Cosa chiede | Cosa apre |
|---|---|---|---|
| 10 | **Il mercato** | parlare con qualcuno dietro un bancone | la Moneta d'Anima |
| 11 | **Il conio** | coniare otto monete al Banco | l'Officina: le anime in esubero |
| 12 | **L'esubero** | raccogliere tre Anime Erranti | il **Progetto del Richiamo** |
| 13 | **Il richiamo** | piazzarlo e farlo lavorare una volta | il **Progetto del Crogiolo** |
| 14 | **La fusione** | fondere quattro anime in una | il **Progetto della Fucina** |
| 15 | **La fucina** | fondere sedici oggetti senza carbone | il **Progetto del Pozzo** |
| 16 | **Il pozzo** | duecento soul coin munti dalle anime | l'arruolamento |
| 17 | **La recluta** | arruolare un'anima fusa | i servizi del mercato |
| 18 | **Il sensale** | comprare un varco su ordinazione | la catena e' finita |

**I Progetti sono la chiave di volta.** Un macchinario non si costruisce senza il suo Progetto, e
il Progetto **si consuma** nella ricetta. Il primo di ognuno lo regala la catena; i successivi si
comprano in Cartoleria. E' il motivo per cui i quattro macchinari smettono di essere una tabella
da leggere e diventano quattro cose che *arrivano*, una dopo l'altra, ciascuna quando si e' finito
di capire la precedente.

### 11.6 Perche' cosi'

**Perche' non villager veri.** Un villager e' una macchina complicata: dorme, cerca un letto, si
lega a un punto d'interesse, cambia mestiere se gli togli il blocco di lavoro, e un fulmine lo
trasforma in strega. Tutto questo esiste per un gioco di villaggi, non per una bottega che deve
stare dietro un bancone finche' il mondo esiste. La nostra entita' non ha IA, non ha inventario e
non si muove: e' un bancone che parla.

**Perche' la finestra di scambio vanilla.** Perche' e' l'unica interfaccia di questa mod che
nessuno deve imparare. Chi ha giocato a Minecraft ha gia' comprato qualcosa da un villager, e la
prima bottega di Arise gli chiede zero istruzioni.

**Perche' i Progetti si consumano.** Perche' altrimenti sarebbero un permesso, non un oggetto: si
otterrebbero una volta e non si guarderebbero mai piu'. Consumandosi diventano un costo che
qualcuno vende, e la Cartoleria ha un motivo di esistere anche a catena finita.

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
- [Thermal Expansion — lati riconfigurabili e potenziamenti](https://technicpack.fandom.com/wiki/Thermal_Expansion)
- [Mekanism — progressione delle macchine](https://www.minecraft-guides.com/mod/mekanism/)
- [Create — meccanica visibile invece di GUI](https://rocketnode.com/blog/create-mod-101)
- [Botania — fiori generatori passivi e attivi](https://wiki.gtnewhorizons.com/wiki/Mana_Generating_Flowers)
- [Fabric — Creating Your First Block](https://docs.fabricmc.net/develop/blocks/first-block)
