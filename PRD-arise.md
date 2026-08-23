# PRD — Arise: da mod completa a gioco che si continua a giocare

> Documento di prodotto. Scritto il 23 agosto 2026, dopo aver letto **tutto** il codice della mod
> (178 file Java, 704 chiavi di traduzione, 18 incarichi) e aver fatto ricerca sulle pratiche
> consolidate di game design per ARPG, dungeon procedurali, economie di gioco e mod Minecraft.
>
> Il documento di design originale è `DESIGN-solo-leveling.md` e resta valido: descrive **cosa** è
> Arise. Questo descrive **cosa le manca**, e in che ordine aggiungerlo.

---

## 0. Il verdetto, in cinque righe

Arise ha finito di costruire i suoi sistemi e non ha ancora cominciato a costruire il suo **gioco**.
Ci sono dodici sistemi che funzionano, si insegnano da soli e si parlano fra loro — ed è un
risultato raro. Ma la catena degli incarichi finisce al diciottesimo passo con la frase «da qui in
avanti il Sistema tace e guarda», e da lì il gioco letteralmente **tace**: nessun obiettivo, nessuna
sfida che scali, nessun oggetto da inseguire, nessun motivo per aprire il varco numero cinquanta
dopo aver aperto il quarantanovesimo.

Questo PRD è il piano per riempire quel silenzio, e per correggere le sei cose che nel frattempo
sono venute storte.

---

## 1. Dove siamo — il contenuto vero, contato

Non stime: valori letti dal codice.

### 1.1 Cosa c'è

| Sistema | Stato | Contenuto misurato |
|---|---|---|
| Il Sistema | completo | 100 livelli, curva `20·n^1.6`, 1.234.987 XP totali, 3 punti a livello (297 in tutto) |
| Statistiche | completo | 12 statistiche → 12 attributi vanilla, di cui **4 spendibili** |
| Ranghi | completo | 6 (E→S) ai livelli 1/10/20/35/55/80 |
| Risveglio | completo | Sala del Risveglio, Araldo, 6 pagine di dialogo |
| Incarichi | completo | **18**, catena lineare, 18 sblocchi, 10.400 soul coin |
| Esercito | completo | 4 archetipi, 8 gradi, squadra di 4, 2 ordini, posture, dono del Monarca |
| Abilità | completo | **4**, ai livelli 5/10/20/30 |
| Gate | completo | 6 temi, 5-8 stanze + 1-3 diramazioni, 1 boss, 6 ranghi |
| Equipaggiamento | completo | 25 posizioni, 19 basi, 12 affissi, **1 pezzo unico** |
| Gemme | completo | 5 tipi × 6 ranghi |
| Abyss Shop | completo | 6 voci, rotazione giornaliera, ritiro a prezzo crescente |
| Città | completo | 5 città 512×512, 5 piante, 5 monumenti, 9 botteghe, 10 NPC |
| Officina | completo | 4 macchinari, 5 tratti, 6 gradi di catalizzatore, 14 mob nel Richiamo |
| Mappa | completo | città, varchi, zoom, segni sul bordo |

### 1.2 Quanto dura

Stimando 22 XP per zombie e la curva reale:

| Traguardo | XP cumulata | Uccisioni equivalenti |
|---|---|---|
| Rango D (liv. 10) | 2.669 | ~120 |
| Rango C (liv. 20) | 19.779 | ~900 |
| Rango B (liv. 35) | ~78.000 | ~3.500 |
| Rango A (liv. 55) | ~266.000 | ~12.000 |
| Rango S (liv. 80) | ~693.567 | ~31.500 |
| Livello 100 | 1.234.987 | ~56.000 |

La catena dei 18 incarichi si chiude molto prima: chiede il livello 3, 15 uccisioni, un varco, otto
monete, 200 soul coin dal Pozzo. **Un giocatore che segue gli incarichi finisce tutto il contenuto
guidato intorno al livello 20-25, cioè a un quarto del rango C.** Restano 75 livelli, e sono vuoti.

---

## 2. Le lacune

Ogni voce ha un'evidenza nel codice e una conseguenza per chi gioca.

### L1 — Dopo il diciottesimo incarico non c'è niente

**Evidenza.** `Unlock.MASTERY` è l'ultimo, e la sua hint dice «Non manca più niente: da qui in avanti
il Sistema tace e guarda». `QuestManager` risponde `arise.msg.quest.chain_done`. Nessun altro sistema
genera obiettivi.

**Conseguenza.** Il gioco toglie la mano dal manubrio esattamente nel punto in cui il giocatore ha
appena imparato tutti i sistemi e vorrebbe usarli. È il momento peggiore possibile.

**Riferimento.** «Quando i giocatori raggiungono un traguardo, ne compare un altro»: è il meccanismo
comune a Greater Rift, Atlas, Monolith e Nightmare Dungeon, e funziona *perché difficoltà e
ricompensa scalano insieme*.

### L2 — Il tetto della sfida è più basso del tetto della progressione

**Evidenza.** Il Gate più difficile è di rango S, e il rango S si raggiunge al livello 80 su 100. Il
boss di un Gate S è un ravager o un golem di ferro con ×6 vita e ×2 danno. Il rango del varco
spontaneo è legato al rango del Cacciatore ±1: **non esiste un modo di chiedere una sfida più dura
di quella che il gioco ti assegna**.

**Conseguenza.** Da metà rango A in poi il giocatore vince sempre, e la ricompensa migliore che il
gioco possa offrirgli è un pezzo di rango S che gli serve per… vincere di più. La power fantasy
funziona solo se qualcosa, da qualche parte, resiste ancora.

### L3 — La build non è una scelta

**Evidenza.** Quattro statistiche spendibili su dodici, tutte utili a tutti, tutte lineari (+2 vita a
punto, +0,4 danno a punto). Le altre otto arrivano solo dall'equipaggiamento, cioè dai tiri di dado.
Quattro abilità fisse su quattro tasti fissi, senza scelta e senza alternative. Nessun respec,
perché non c'è niente da cui tornare indietro.

**Conseguenza.** Due Cacciatori di livello 60 sono lo stesso Cacciatore con equipaggiamento diverso.
Non c'è un «io gioco così».

**Riferimento.** La critica documentata ai punti statistica puri è che «è difficile notare un
qualsiasi effetto diretto sul gioco mentre giochi». La soluzione consolidata non è abolirli: è
affiancarli a **soglie che concedono un effetto qualitativo**, e a una seconda economia di
progressione slegata dall'XP (il modello Devotion di Grim Dawn: punti che si guadagnano
*facendo cose nel mondo*, non salendo di livello).

### L4 — Un Gate è sempre lo stesso Gate

**Evidenza.** `GateLayout.generate` produce una spina dorsale di 5-8 stanze quadrate uguali più 1-3
vicoli ciechi. `GateTheme` cambia **tre blocchi**: pavimento, pilastri, lampada. I mob sono vanilla
presi da una lista per rango. Il boss è un mob vanilla con due attributi moltiplicati. Non c'è una
stanza scritta a mano, non c'è una serratura, non c'è un bivio, non c'è un evento.

**Conseguenza.** Il decimo varco è indistinguibile dal primo, e la schermata di analisi — che è un
bel pezzo di interfaccia — non ha niente di interessante da dire, perché la risposta è sempre la
stessa.

**Riferimento.** L'approccio che funziona nel procedurale (Diablo, Hades, Vault Hunters) è
**stanze scritte a mano mescolate al generato**: il layout è casuale, ma i pezzi che lo compongono
sono progettati. E la struttura «percorso critico + diramazioni opzionali» ha bisogno che le
diramazioni offrano *qualcosa che il percorso critico non dà*, altrimenti nessuno le percorre.

### L5 — Il combattimento non è leggibile

**Evidenza.** Nessuna telegrafia in tutta la mod. `AriseFx` disegna effetti *dopo* che qualcosa è
successo (l'estrazione, l'evocazione, il boss che si sveglia), mai *prima*. Il boss non ha fasi, non
ha attacchi propri, non modifica l'arena. La lancia d'ombra del Mago è istantanea. Nessun mob ha
affissi.

**Conseguenza.** L'unico modo di alzare la difficoltà oggi è alzare i numeri, ed è esattamente il
modo che rende un gioco frustrante invece che difficile.

**Riferimento.** La leggibilità si scompone in **telegrafia** (cosa sta per succedere) e
**aspettative** (cosa succederà dopo), ed è il fattore col maggiore impatto sulla frustrazione. Le
leve per alzare la difficoltà senza rompere la leggibilità sono otto, e nessuna è «più HP».

### L6 — L'esercito è una collezione, non uno strumento

**Evidenza.** `baseCapacity 6` + 0,25 a livello → **30 ombre** a livello 100. `maxSummoned = 4`. Le
26 ombre che restano a casa non fanno assolutamente niente: non danno bonus, non lavorano, non
scelgono, non esistono. L'unica decisione è quali quattro portare, e siccome le aure di comando si
sommano e il dono del Monarca scala tutto, la risposta ottimale è quasi sempre la stessa quattro.

**Conseguenza.** Estrarre la ventesima ombra non cambia niente. Il verbo centrale della mod smette
di avere conseguenze intorno al livello 30.

### L7 — Non c'è niente da inseguire

**Evidenza.** `GearUnique` ha **un solo valore**: l'Occhio dell'Oscurità, di rango E, regalato dal
quinto incarico. Le ombre non hanno nomi propri predefiniti né identità. Non esiste un drop raro,
non esiste un boss speciale, non esiste una condizione difficile che paghi qualcosa di unico.

**Conseguenza.** Il bottino è statistica, mai evento. Nessun drop di Arise fa dire «guarda cosa mi è
uscito», perché tutti i drop sono lo stesso drop con numeri diversi.

**Riferimento.** Quattro condizioni perché un drop faccia esclamare: un **segnale sensoriale
dedicato e mai riutilizzato**, rarità vera, impatto leggibile immediato, e un tetto visibile che
dica «questo è vicino al massimo». Arise oggi non ne ha nessuna delle quattro.

### L8 — Le probabilità hanno un tetto che nessuno può toccare

**Evidenza.** `extractionChanceAt(level) = min(0.95, 0.25 + 0.005·(level-1))`. Al livello 100 —
il massimo — la probabilità è **74,5%**. Il tetto di 0,95 richiederebbe il livello 141. È
configurazione morta.

**Conseguenza.** Un Cacciatore al massimo livello fallisce ancora un'estrazione su quattro, e non ha
alcun modo di migliorare (l'unica leva è la gemma Ossidiana, che fa un'altra cosa: estrae da sola).
Il verbo che dà il nome alla mod resta un tiro di dado per sempre.

### L9 — L'economia non ha un rubinetto chiuso

**Evidenza.** I soul coin arrivano da: ogni uccisione (1 + ¼ della vita del mob), ogni Gate (fino a
1.133), gli incarichi (10.400), il Pozzo dell'Abisso (metà del vigore installato **ogni 20 secondi**,
senza limite). Se ne vanno in: potenziamento ombre, rinomina, colore, estrazione gemme (750),
negozio, sensale (400).

Il Pozzo è il problema: quattro anime forti producono centinaia di soul coin al minuto, per sempre,
senza rischio. E i soul coin comprano equipaggiamento.

**Conseguenza.** Un giocatore che capisce l'Officina smette di aver bisogno dei Gate, cioè smette di
giocare al gioco. In Minecraft questo non è un rischio teorico: è la prima cosa che succede.

**Riferimento.** La difesa strutturale è ancorare ciò che conta a una valuta **che non si può
produrre in automatico** — che i mob dell'overworld e i macchinari non generano.

### L10 — Il mondo non sa che esiste una mod

**Evidenza.** Nessun advancement. Nessun libro-guida. Nessuna integrazione JEI/EMI. Nessuna
schermata di configurazione (il file è `arise.json` a mano). `fabric.mod.json` non dichiara né
`suggests` né `breaks`. Il bottino nel mondo normale ha probabilità **1,2%**.

**Conseguenza.** Fuori dalle sue undici schermate, Arise è invisibile. Un giocatore di modpack che
non trova la mod nel menu delle ricette pensa che non ci sia.

### L11 — Sei cose iconiche di Solo Leveling non ci sono

Il Gate rosso che si sigilla dietro di te. Il Dungeon Break, cioè il varco che se non lo chiudi
esplode e riversa i mostri nel mondo. La Quest Giornaliera con la sua penalità. Il Job Change, cioè
il momento in cui smetti di essere un Cacciatore e diventi il Monarca delle Ombre. Le ombre con un
nome — Igris, Beru, Tusk. I Monarchi come nemico finale.

Sono, in ordine, le sei cose che chiunque conosca l'opera si aspetta di trovare. Il dettaglio che
conta: **cinque su sei sono meccaniche, non narrativa**, e quattro delle cinque risolvono da sole
una delle lacune sopra.

### L12 — Sei difetti concreti, trovati leggendo

| # | Difetto | Stato |
|---|---|---|
| 1 | `/arise gear` regalava un set di netherite a chiunque, senza permessi (collisione di nodi Brigadier) | **corretto** in `ed865b3` |
| 2 | La lore dell'ultimo incarico, in inglese, era la lettera «W» | **corretto** in `ed865b3` |
| 3 | L'obiettivo della prima ombra nominava il tasto R a lettere | **corretto** in `ed865b3` |
| 4 | Tre `hint` di schermata nominano i tasti a lettere («Press K or Esc», «P to close», «G summons») | aperto → **B0** |
| 5 | Il tema `VOID` è documentato come raro ma è estratto uniformemente come gli altri | aperto → **B4** |
| 6 | I catalizzatori hanno una sola fonte in gioco (Pozzo, 8% a giro) più due gradi bassi dal mercante | aperto → **B7** |

---

## 3. I principi

Sette regole che governano tutto ciò che segue. Ognuna viene da una pratica consolidata, e ognuna
si può verificare su una riga di codice.

### P1 — Due binari, mai uno solo

Una curva di potere soddisfacente è **una rampa più una scala**: numeri che salgono in continuo, e
sblocchi che cambiano *come* si gioca. Arise oggi ha solo la rampa. Ogni blocco di questo PRD deve
aggiungere almeno un gradino alla scala.

> *Corollario operativo*: **nessun livello deve essere vuoto per più di tre livelli di fila.** Se
> dal 30 al 100 l'unica cosa che succede sono punti statistica, il gioco è piatto per settanta
> livelli.

### P2 — La difficoltà la sceglie il giocatore, il mondo non scala

L'overworld resta a livello fisso: tornare al villaggio iniziale e vedere una sola ombra spazzare
via un branco di zombie **è la ricompensa**, e va protetta. Tutta la sfida che scala vive dentro i
Gate, ed è **chiesta**, non imposta. È il modello Greater Rift / Nightmare Sigil / Pact of
Punishment, ed è l'unico che tiene insieme power fantasy e sfida.

### P3 — Più regole, non più numeri

Un livello di difficoltà in più deve aggiungere **una regola**, non un moltiplicatore. Otto leve
disponibili — concatenare pattern, accorciare finestre, aggiungere modificatori, alzare la velocità
*dopo* che il pattern è compreso, aggiungere un compagno, clonare il nemico, mettere pressione
temporale, drenare risorse — e nessuna delle otto è «più vita».

### P4 — Niente succede senza preavviso

Tre segnali fissi in tutta la mod, **mai riusati per altro**:

| Segnale | Significato | Anticipo |
|---|---|---|
| **Anello rosso a terra** | danno ad area, spostati | 20-30 tick |
| **Raggio verticale** | attacco puntato su di te | 20 tick |
| **Suono grave dedicato** | cambio di fase | immediato |

Passano tutti da `AriseFx` e `ModSounds`, come già impone la regola 13 del progetto. Il colore
significa la stessa cosa in tutti e sei i temi. Il particellare a terra si disegna col trucco già
documentato: `sendParticles` con conteggio zero e dx/dy/dz come vettore.

### P5 — Il giocatore comanda, e comandare costa

Il rischio numero uno di una mod con un esercito è il **gioco che si gioca da solo**. La cura non è
meno ombre: è dare al giocatore un verbo attivo che **paghi** e che **costi esposizione**. È la
lezione della frusta di Terraria: i minion sono autonomi, ma il giocatore li *dirige*, e per
dirigerli deve stare dove fa male.

### P6 — Il colore non porta mai informazione da solo

Ogni cosa che oggi si distingue per colore — ranghi, gradi, gemme, archetipi, temi — deve avere
anche una **forma, un glifo o un'etichetta**. `Glyphs` fa già la cosa giusta con le cinque gemme
(distinte dal taglio, non dal colore): quella è la regola, non l'eccezione.

### P7 — Niente si può automatizzare in Minecraft

Qualunque cosa il giocatore possa produrre con una torre di zombie e una tramoggia **verrà**
prodotta con una torre di zombie e una tramoggia. Ciò che conta va ancorato a valute e materiali che
**solo i Gate** generano, e le fonti automatiche vanno tenute su valute di flusso.

---

## 4. Il piano

Sedici blocchi. Ognuno è consegnabile da solo, ha le sue prove e i suoi criteri di accettazione, e
non rompe niente di ciò che c'è.

L'ordine è per **rapporto valore/costo**, non per tema.

---

### B0 — Le ultime tre bugie
*Costo: mezz'ora · Lacuna: L12*

Tre `hint` di schermata nominano i tasti a lettere: `arise.screen.status.hint` («Press K or Esc»),
`arise.screen.quest.hint` («P to close»), `arise.screen.army.hint` («G summons the squad, H
recalls · Y orders the kill, U holds»). Chi rimappa legge istruzioni false.

**Cosa si fa.** Le tre chiavi diventano formato con argomenti, e le schermate passano
`Component.keybind("key.arise.…")`. `AriseScreen.hint()` restituisce già un `Component`: cambia solo
chi lo costruisce.

**Criterio di accettazione.** Rimappa `army` su F6 in gioco: la riga in fondo alla schermata
dell'esercito dice F6.

---

### B1 — L'estrazione ha di nuovo un tetto, e si può guardare prima
*Costo: mezz'ora · Lacuna: L8*

Due difetti in uno.

**Il tetto morto.** `extractionChanceAt = min(0.95, 0.25 + 0.005·(liv-1))`: al livello 100 dà 74,5%,
e il tetto di 0,95 richiederebbe il livello 141. Si porta `extractionChancePerLevel` a **0,007**, che
al livello 100 dà 94,3% e tocca il tetto a 101 — cioè il tetto esiste ed è quasi raggiungibile,
che è la sua ragione d'essere. Il valore resta in config.

**Guardare prima di tirare.** `shift + tasto Arise` **non estrae**: dice in barra d'azione cosa c'è
in quel cadavere e con che probabilità — rango, archetipo, percentuale. Costa niente e toglie il
peggior momento del gioco, quello in cui si brucia il Warden appena ucciso senza sapere che si stava
tirando al 30%.

**Prove.** `extractionChanceAt(maxLevel)` deve stare fra 0,90 e il tetto. La formula deve essere
monotona e non superare mai il tetto.

---

### B2 — Il Dungeon Break: i varchi ignorati esplodono
*Costo: mezza giornata · Lacune: L1, L2 · Fonte: canone*

Oggi un varco ignorato scade a costo zero. Nel canone un gate lasciato aperto **erutta**, e i
mostri si riversano nel mondo. È la modifica che trasforma la mappa da elenco di attività a mappa di
minacce, e dà **conseguenza all'inazione** — cosa che oggi in Arise non esiste da nessuna parte.

**Cosa si fa.**

1. `SpawnConfig` guadagna `breakChance` (default **0,35**): alla scadenza, un varco su tre non si
   richiude — **esplode**.
2. L'esplosione: `AriseFx.gateBreak` (colonna, anello che si allarga, suono grave nuovo), poi
   **un'ondata di mob del tema e del rango del varco** piazzati in superficie entro 40 blocchi, con
   `setPersistenceRequired()`. Quantità = `mobsPerRoom(rango) × breakWaves` (default 3).
3. **Preavviso.** Sotto il 25% di vita residua del varco, il rombo sulla mappa **pulsa in rosso**,
   e se il giocatore è entro 200 blocchi arriva una riga in chat: *«Il varco di rango %s sta per
   cedere.»* Il giocatore deve poter scegliere di correre a chiuderlo. Senza preavviso è una tassa;
   con preavviso è una decisione (P4).
4. **Le città si difendono male.** Se il varco che esplode sta entro il raggio di un'Associazione, i
   mob puntano il Quartiere del Mercato. **Gli NPC non muoiono** (sono già invulnerabili) ma restano
   assediati finché non si ripulisce. `/arise city npcs` è già idempotente ed è la riparazione.

**Perché non uccide gli NPC.** Perdere per sempre il Cartografo perché si era disconnessi è
esattamente il tipo di punizione che fa disinstallare una mod. La minaccia deve costare **tempo e
fastidio**, mai contenuto.

**Prove.** La scelta esplodi/richiudi è deterministica dato un seed. L'ondata non supera il tetto di
entità. Nessun mob viene piazzato dentro un blocco.

**Criterio di accettazione.** `/arise gate spawn`, aspetta cinque minuti senza entrare: o si chiude,
o il mondo lì attorno diventa un problema.

---

### B3 — La Quest Giornaliera e la Zona di Penalità
*Costo: un giorno · Lacune: L1, L3 · Fonte: canone (la scena più riconoscibile che manchi)*

La prima quest che il Sistema assegna a Jinwoo — cento flessioni, cento addominali, cento squat,
dieci chilometri — e la penalità se scade: **il teletrasporto senza preavviso** in un deserto
infinito senza sole né stelle, con i millepiedi, e una sola quest: *sopravvivi*.

Il pezzo di design che conta: **la penalità non toglie niente. Ti sposta.** È il momento in cui il
Sistema smette di essere un'interfaccia e diventa un carceriere.

**Cosa si fa.**

*Gli obiettivi*, tradotti in verbi che Minecraft misura già, tutti in config:

| Canone | Arise | Contatore | Default |
|---|---|---|---|
| 100 flessioni | blocchi scavati | `PlayerBlockBreakEvents.AFTER` | 100 |
| 100 addominali | colpi a segno | `AFTER_DAMAGE` | 100 |
| 100 squat | salti | transizione `onGround` | 100 |
| 10 km | blocchi percorsi in corsa | delta posizione per tick | 1000 |

*Il tempo* è **un giorno di Minecraft** (24000 tick), non un giorno reale: rispetta il ritmo del
gioco e non punisce chi chiude la partita. Il conto riparte all'alba.

*Il pannello* sta in alto a destra, quattro righe con barra, compare all'alba e si nasconde dopo
dieci secondi. Al 75% del tempo trascorso con obiettivi aperti, un titolo: *«Sei ore alla
penalità.»*

*La ricompensa*: **1 punto statistica** e recupero pieno. È la seconda fonte di punti dopo le
pergamene dei Gate, e la prima che non richieda di entrare in un varco.

*La penalità*, allo scadere:

1. titolo rosso **PENALITÀ**, suono grave;
2. il giocatore finisce in `arise:penalty` — una dimensione piatta, `has_skylight: false`,
   `ambient_light: 1.0`, nessun sole e nessuna stella eppure si vede tutto. È la descrizione del
   canone, ed è gratis in un `DimensionType`;
3. quest **Sopravvivenza**: **8 minuti** in barra d'azione (config);
4. i millepiedi: `Silverfish` riscalati con `Attributes.SCALE`, veleno al morso, resistenza al
   contraccolpo alta, **nessun bottino**. Ondate crescenti dal buio;
5. **non si può morire dentro**: la morte rimanda all'ingresso a metà vita e il timer riparte. La
   penalità è tempo perso e umiliazione, non perdita di inventario. Se la penalità potesse
   cancellare l'equipaggiamento, il giocatore disinstallerebbe.

**Prove.** I quattro contatori si azzerano all'alba e non prima. Il punto si concede una volta sola
per giorno. La dimensione di penalità rilascia sempre il giocatore, anche dopo un riavvio dentro.

---

### B4 — Le ombre che hanno un nome
*Costo: mezza giornata · Lacune: L6, L7 · Fonte: canone*

Sette ombre uniche. **Non statisticamente migliori: uniche in ciò che fanno.** È così che un
esercito smette di essere una lista ordinata per potenza e diventa una collezione da comporre.

| Ombra | Archetipo | Come si ottiene | Cosa fa che nessun'altra fa |
|---|---|---|---|
| **Igris** | Guardia | Ricompensa fissa dell'esame di rango C (B12) | Parte già Cavaliere. Al grado Maresciallo **dice una riga** quando lo evochi |
| **Iron** | Colosso | Solo dal Gate Rosso (B5) | La sua provocazione ha **raggio doppio** |
| **Tank** | Bestia | Solo da un Gate a tema Gelo di rango B+ | Aura che **annulla il gelo del Gate Rosso** |
| **Tusk** | Mago | Boss di un Gate a tema Rovina di rango B+ | Lancia d'ombra a **raggio 24** invece di 16 |
| **Greed** | Guardia | Boss di un Gate a tema Cenere di rango A+ | Raddoppia i soul coin che l'esercito raccoglie |
| **Beru** | Bestia | Boss di un Gate a tema Sculk di rango S | **Cura il Monarca** quando è sotto metà vita |
| **Bellion** | Colosso | **Non si estrae: si eredita**, alla fine della catena finale (B13) | Nasce Gran Maresciallo. La sua aura vale **per tutte** le ombre, evocate e no |

**Come si implementa.** `ShadowData` guadagna un campo opzionale `named` con l'id dell'ombra unica.
Un `NamedShadow` enum, sullo stampo di `GearUnique` che esiste già: nome fisso non rinominabile,
colore fisso, grado minimo, e un **tratto** che `ShadowGoals` legge. Il resto — livelli, gradi,
squadra, dono del Monarca — funziona già e non va toccato.

**La regola d'oro**: un'ombra nominata **non si può ottenere due volte**. `ShadowArmy` controlla, e
il boss che la lascia la lascia una volta sola per giocatore.

**Perché prima delle altre cose.** L'infrastruttura c'è tutta (archetipi, gradi, aure, colori,
persistenza): sono sette voci in un enum e sette righe di comportamento. È il blocco col miglior
rapporto emozione/righe di codice dell'intero PRD.

---

### B5 — Il Gate Rosso
*Costo: mezza giornata · Lacune: L2, L4 · Fonte: canone (la migliore idea di design dell'opera)*

Un varco che **all'esterno è identico a tutti gli altri**. Lo scopri solo dopo essere entrato,
perché si chiude alle tue spalle.

**Cosa si fa.**

1. `redGateChance` in `SpawnConfig`, default **0,05**. L'analisi del varco **non lo rivela**: è il
   punto.
2. All'ingresso: titolo rosso **VARCO ROSSO — sigillato**. La pietra del ritorno non esiste finché
   il Sovrano non cade. **Il Sigillo dell'Associazione non funziona qui**, ed è la prima volta in
   tutta la mod che un oggetto del giocatore viene disattivato: si nota, e deve notarsi.
   `/arise leave` resta come uscita d'emergenza — un tester bloccato è un tester perso — ma costa
   **tutto il bottino della run**.
3. **L'ambiente uccide.** Ogni secondo fuori dal raggio di una fonte di calore: rallentamento e
   mezzo cuore. Le fonti di calore **le pianti tu**, col tuo inventario: falò, blocchi di magma,
   fuochi. Il dungeon diventa una traversata gestita invece di una passeggiata, e finalmente lo
   zaino conta.
4. Ricompensa: bottino **doppio**, e l'unica fonte di **Iron** (B4).

**Prove.** Un giocatore dentro un Gate Rosso non può uscire con il Sigillo. Il gelo non si applica
entro il raggio di una fonte di calore. La morte dentro non perde l'esercito.

---

### B6 — Gli obiettivi del varco
*Costo: un giorno · Lacune: L1, L4 · Fonte: Vault Hunters*

Oggi ogni Gate si percorre allo stesso modo, perché l'obiettivo è sempre lo stesso. Quattro
obiettivi diversi sullo stesso generatore fanno **quattro giochi**, ed è il miglior rapporto
lavoro/varietà disponibile.

| Obiettivo | Cosa chiede | Come cambia il percorso |
|---|---|---|
| **Il Sovrano** | uccidi il guardiano | quello di oggi: si corre in fondo |
| **Raccolta d'Essenza** | riempi una barra saccheggiando, minando e uccidendo | si apre tutto, si esplora ogni ramo |
| **I Sigilli** | trova N reliquie e posale sull'altare all'ingresso | si torna indietro, la mappa conta |
| **I Bracieri d'Ombra** | accendine N; ognuno dà un buff **e** un debuff | si sceglie quale prezzo pagare |

L'obiettivo è **tirato a sorte alla comparsa del varco**, sta nel `GateOffer` (quindi nel seme, e
quindi non può mentire), e **si legge nell'analisi prima di entrare**. Il contatore usa la barra
d'azione che P1 ha già costruito.

**Ricompensa.** Ogni obiettivo ha la sua cassa di completamento, e l'uscita si apre al completamento
dell'obiettivo, chiunque esso sia.

---

### B7 — Il Sovrano ha tre fasi, e si vede arrivare
*Costo: un giorno · Lacuna: L5 · Principio: P3, P4*

Oggi il boss è un mob vanilla con due attributi moltiplicati. Diventa un incontro.

**Tre fasi, una regola nuova per fase.**

- **Fase 1** (100-66%): il pattern base. Due attacchi telegrafati — un colpo ad area (anello rosso,
  25 tick) e una carica puntata (raggio verticale, 20 tick).
- **Fase 2** (66-33%): **una** meccanica in più, e la meccanica è quella del tema. Gelo congela il
  terreno, Sculk chiama rinforzi, Vuoto toglie il pavimento a scacchiera, Cenere riempie di fumo,
  Profondità allaga lentamente, Rovina fa crollare i pilastri.
- **Fase 3** (33-0%): **add**, non un terzo attacco. Mai più di quattro pattern in tutto.

**Il boss sa che hai un esercito.** Tre contromisure, dichiarate:

1. un attacco ad area che **spazza le ombre** — le fa cadere, non le cancella (riusa il minuto di
   recupero che D2 ha già);
2. una fase in cui **l'aggro è forzato sul Monarca**: il Sovrano vede attraverso l'ombra. È il
   momento in cui il giocatore deve giocare in prima persona, almeno una volta per incontro;
3. l'arena è **grande e senza colli di bottiglia** — la sala boss è già 25×25, che basta, ma il
   soffitto va alzato a 12 per le Bestie che saltano.

**Nessun colpo singolo toglie più del 40%** della vita massima (config), e ogni colpo letale ha
almeno un secondo di preavviso. La correttezza non è gentilezza: è la condizione perché la
difficoltà sia leggibile.

**La vittoria è un momento**: titolo, due secondi di silenzio, poi la pietra si accende.

---

### B8 — Gli affissi dei nemici
*Costo: mezza giornata · Lacune: L4, L5 · Principio: P3*

Otto affissi in config, applicati a un mob per stanza a partire dal rango C. Un mob con un affisso
ha un nome, un'aura e un solo comportamento in più.

| Affisso | Cosa fa | Contromisura |
|---|---|---|
| **Esplosivo** | area a terra alla morte, telegrafata | non stare vicino quando cade |
| **Rapido** | +50% velocità | non scappare in linea retta |
| **Corazzato** | immune finché la sua scorta è viva | uccidi prima gli altri |
| **Assetato** | si cura colpendo | non farti colpire |
| **Fulminante** | colpisce chi resta fermo tre secondi | muoviti |
| **Divoratore d'Ombre** | danno doppio contro le ombre | cambia la squadra, o combatti tu |
| **Riflesso** | rimanda una quota del danno | armi lente, colpi grossi |
| **Ancora** | rallenta chi gli sta vicino | tienilo a distanza |

**Due regole dure**, e vengono dall'errore più documentato del genere: **mai due affissi sullo
stesso mob**, e **mai più di un mob con affisso per stanza**. Gli affissi che tolgono il controllo
al giocatore sono i più odiati di tutti, e qui non ce ne sono: ognuno degli otto chiede un
**cambio di comportamento**, non nega l'input.

**Divoratore d'Ombre è il più prezioso**: è l'unico che rende la composizione della squadra una
decisione tattica invece di una lista dei quattro più forti.

---

### B9 — Le soglie: quando un punto cambia qualcosa
*Costo: un giorno · Lacuna: L3 · Principio: P1*

Le quattro statistiche spendibili sono lineari e prevedibili, e questo è esattamente il difetto: «è
difficile notare un qualsiasi effetto diretto sul gioco mentre giochi». La cura consolidata non è
abolire i punti: è dare loro **soglie con un effetto qualitativo**.

Tre soglie per statistica — **25, 50, 100** — e dodici effetti in tutto.

| Statistica | 25 | 50 | 100 |
|---|---|---|---|
| **Vitalità** | la rigenerazione parte a 15 di fame invece di 18 | le cadute sotto 4 blocchi non fanno danno | una volta ogni 10 minuti, un colpo letale ti lascia a mezzo cuore |
| **Forza** | i colpi caricati rompono lo scudo | +1 ombra evocabile in campo | i colpi critici colpiscono anche chi sta accanto |
| **Resistenza** | l'armatura non si consuma dentro i Gate | immune al fuoco per due secondi dopo esserne uscito | il gelo del Gate Rosso non ti tocca |
| **Agilità** | niente danno da caduta mentre corri | il Passo d'ombra ha una carica in più | attraversare l'acqua non rallenta |

Le soglie stanno in config come tutto il resto, e la schermata di stato **le mostra prima**: la
barra verso il tetto diventa una barra verso la prossima soglia, con scritto cosa dà. Vedere il
lucchetto prima della chiave è metà del motivo per spendere il punto.

---

### B10 — La Pergamena del Rimpianto
*Costo: due ore · Lacuna: L3 · Fonte: la richiesta più universale nelle mod RPG*

Non c'è respec. Chi ha sbagliato la spesa a livello 40 non ha nessun modo di tornare indietro, e la
letteratura è unanime: è la prima cosa che i giocatori chiedono, e la sua assenza è il motivo per
cui smettono.

Un oggetto, `arise:regret_scroll`, venduto all'Associazione e all'Abyss Shop. Restituisce **tutti**
i punti statistica spesi. Il costo **cresce a ogni uso** con la stessa curva del ritiro
dell'assortimento che B2 ha già scritto (`base × growth^n`), default 500 × 1,6.

Libero ciò che è tattico, costoso ciò che è identità: la **squadra** resta gratis, i **punti** si
pagano.

---

### B11 — L'Abisso
*Costo: un giorno · Lacune: L1, L2 · Principio: P2*

Il contenitore dell'endgame. Un varco solo, permanente, in ogni Associazione: una discesa a
profondità crescente.

- **La profondità è il livello di difficoltà**, e la sceglie il giocatore: si può sempre ricominciare
  dalla profondità massima raggiunta, o da qualunque profondità inferiore.
- **Ogni 5 profondità entra una regola nuova**, pescata dagli affissi di B8 e da un elenco di
  condizioni: niente rigenerazione, i mob arrivano in due ondate, l'esercito ha metà capacità, il
  tempo stringe. **Mai un moltiplicatore da solo.**
- **Cronometro e record personale.** È il vero motore del «ancora una run»: trasforma un contenuto
  ripetibile in una prova di ottimizzazione.
- **Il Monumento delle Ombre.** In ogni città, sulla piazza, una lastra che mostra profondità
  record, ombra più forte, imprese. Aspirational content che il giocatore *attraversa* invece di
  leggere in un menu.

---

### B12 — L'esame di rango
*Costo: un giorno · Lacune: L3, L10 · Fonte: canone*

Oggi il rango si regala col livello, in silenzio. Nel canone è **un esame pubblico**, ed è la scena
che tutti ricordano.

- Un **Esaminatore** all'Associazione, e un **Misuratore di Mana** (un blocco).
- **Si chiede l'esame**, non arriva da solo: *«Il tuo mana è oltre il tuo grado. Vuoi essere
  rivalutato?»*
- L'esame è **una prova**, non un dialogo: l'arena (che il Laboratorio già sa costruire) con ondate
  di rango crescente e un cronometro. Chi passa sale di rango; chi fallisce ritenta il giorno dopo.
- **Il livello resta la condizione necessaria**, l'esame diventa quella sufficiente. Chi non vuole
  fare esami può ancora salire: dopo `rankLevels[r] + 10` il rango arriva da solo. Non forzare il
  giocatore a giocare in un modo.
- **L'esame di rango S ha una regia**: la sala si riempie, il titolo, il suono, e in multiplayer
  **un messaggio a tutti**: *«%s è stato certificato Cacciatore di rango S.»* È il momento di gloria
  pubblica che il canone costruisce, e in multiplayer è gratis.
- **Igris** è la ricompensa dell'esame di rango C.

---

### B13 — La catena finale: i Monarchi
*Costo: due giorni · Lacune: L1, L7 · Fonte: canone*

Quello che viene dopo «il Sistema tace e guarda». Sei incarichi, sei Monarchi, un finale.

Ogni Monarca è un **Gate di rango S unico e non ripetibile**, con una struttura scritta a mano e un
tema: **Sillad** (Ghiaccio), **Baran** (Fiamme Bianche), **Rakan** (Zanne), **Querehsha** (Piaghe),
**Legia** (Bestie), e **Antares**, Re dei Draghi, ultimo.

- Ogni Monarca ucciso alza **permanentemente** il dono del Monarca: è dove si aggancia la
  progressione che oggi si ferma.
- Prima di Antares, **un bivio vero**: allearsi (l'esercito si tinge di rosso, l'Associazione
  diventa ostile, i mercanti chiudono) o rifiutare. Un mod che ricorda una scelta è un mod di cui si
  parla.
- Alla fine, **Ashborn**: non un boss, una consegna. Compare, nomina il giocatore suo successore, e
  lascia **Bellion** (B4). Il «hai finito» reso visibile.

---

### B14 — Il cubo, benedetto o maledetto
*Costo: tre ore · Lacuna: L7 · Fonte: canone*

Alla fine di un Gate o di un incarico, due cubi. **Uno solo.**

- **Benedetto**: tabella buona, prevedibile, tetto basso. Chiavi, gemme, monete.
- **Maledetto**: valore atteso **superiore**, ma **differito** — oggetti sigillati fino a un livello,
  un rango o un incarico. Il tooltip dice *«Sigillato. Non ancora.»* e basta.

Chi conosce la mod prende sempre il maledetto; chi è al primo mondo prende il benedetto. **Entrambe
le scelte sono giuste, in momenti diversi**, che è la definizione di una buona scelta ricorrente.

Il meccanismo del sigillo esiste già: sono le voci sigillate dell'Abyss Shop.

---

### B15 — Il Registro del Sistema
*Costo: due giorni · Lacuna: L10 · Fonte: AE2 / Create / Patchouli*

Una dodicesima schermata, costruita sul telaio comune che C3 ha già fatto. Contenuto in JSON sotto
`data/arise/guide/`, così il collaudo statico può verificarlo.

Tre regole, tutte rubate a chi lo fa bene:

1. **Agganciata agli oggetti e alle schermate.** Ogni schermata ha un **«?»** in alto a destra che
   apre la voce di *quel* sistema. Ogni oggetto Arise dichiara la sua voce e la apre da inventario.
2. **Le voci si sbloccano coi sistemi concessi**, e le altre restano **visibili e grigie** col nome
   leggibile e il testo bloccato. Vedere il lucchetto prima della chiave.
3. **Gli indici si generano dagli enum**, non si scrivono a mano: «Gli otto gradi», «I cinque
   effetti delle gemme», «I quattro archetipi». Così non possono divergere dal codice.

Più un **albero di advancement** generato con la data generation: radice gratis, un ramo per
pilastro. Costa poco, dà i toast, e diventa una checklist visibile.

---

### B16 — Quello che rende una mod adottabile
*Costo: un giorno · Lacuna: L10*

Quattro cose che non si vedono giocando e che decidono se qualcuno metterà Arise in un pack.

1. **Tag.** `arise:gear_piece`, `arise:gem`, `arise:blueprint`, `arise:machine`, `arise:soul`, e i
   tag di entità `arise:extractable` / `arise:boss`. Servono a ricette altrui, filtri, datapack di
   bilanciamento — e subito a `tools/collaudo.py`.
2. **Un interruttore per sistema.** Ogni pilastro ha un `enabled` in config, e **quando è spento
   sparisce da HUD, schermate, incarichi e guida** — non resta come pulsante morto. Un pack che
   vuole Arise solo per l'esercito deve poterlo fare.
3. **Meno tasti.** Sedici keybind di default collidono con qualunque modpack. Restano assegnati
   **Stato, Mappa, Esercito e le quattro abilità**; il resto passa da un **menu radiale** su un
   tasto solo (tieni premuto, ruota, rilascia) e i bind diretti restano **non assegnati per
   default**, disponibili a chi li vuole.
4. **Il server dedicato è una prova, non una speranza.** `runServer` headless su mondo nuovo va reso
   automatico: una sola classe client importata per sbaglio in `src/main` crasha ogni server con
   Arise installata.

---

## 5. Cosa NON facciamo

Detto esplicitamente, perché uno scope negativo scritto vale più di dieci discussioni.

- **Niente Mana.** È la traduzione più fedele al canone e la più costosa: toccherebbe abilità,
  estrazione, evocazione, HUD e bilanciamento insieme. Va valutata come blocco a sé, dopo che tutto
  il resto è verificato in gioco.
- **Niente gilde e niente party.** Arise è giocata in singleplayer e su un server piccolo. La tassa
  al 40% contro il 10% è un'idea eccellente, e va scritta quando ci sarà qualcuno a cui applicarla.
- **Niente cento piani del Castello del Demone.** L'Abisso (B11) fa già il lavoro della torre
  infinita, e due sistemi che fanno la stessa cosa sono peggio di uno.
- **Niente generatore ciclico dei Gate.** È la modifica più raccomandata dalla ricerca e la più
  rischiosa: riscrive `GateLayout`, cioè il pezzo che oggi funziona. Prima gli obiettivi (B6), che
  danno la stessa varietà a un decimo del rischio.
- **Niente numeri di danno fluttuanti** finché non c'è la telegrafia (B7): aggiungere rumore visivo
  prima di aver aggiunto il segnale rende il combattimento meno leggibile, non più.

---

## 6. L'ordine

| # | Blocco | Costo | Stato |
|---|---|---|---|
| 1 | **B0** — le tre bugie | mezz'ora | ✅ *da verificare in gioco* |
| 2 | **B1** — estrazione: tetto e anteprima | mezz'ora | ✅ *da verificare in gioco* |
| 3 | **B10** — Pergamena del Rimpianto | due ore | ✅ *da verificare in gioco* |
| 4 | **B4** — le ombre che hanno un nome | mezza giornata | ✅ *da verificare in gioco* |
| 5 | **B2** — Dungeon Break | mezza giornata | ✅ *da verificare in gioco* |
| 6 | **B9** — le soglie | un giorno | ✅ *da verificare in gioco* |
| 7 | **B8** — affissi dei nemici | mezza giornata | ✅ *da verificare in gioco* |
| 8 | **B5** — il Gate Rosso | mezza giornata | ✅ *da verificare in gioco* |
| 9 | **B6** — obiettivi del varco | un giorno | ✅ *da verificare in gioco* |
| 10 | **B7** — il Sovrano a tre fasi | un giorno | ✅ *da verificare in gioco* |
| 11 | **B14** — il cubo | tre ore | ✅ *da verificare in gioco* |
| 12 | **B12** — l'esame di rango | un giorno | aperto — Igris arriva intanto dal primo varco di rango C |
| 13 | **B11** — l'Abisso | un giorno | ✅ *da verificare in gioco* |
| 14 | **B15** — il Registro del Sistema | due giorni | aperto |
| 15 | **B16** — adottabilità | un giorno | aperto |
| 16 | **B3** — Quest Giornaliera e Penalità | un giorno | aperto |
| 17 | **B13** — i Monarchi | due giorni | aperto |

**Dodici su diciassette**, in una sessione. Tutti e dodici compilano, passano 91 prove, escono puliti
dal collaudo statico e fanno partire un server dedicato su un mondo nuovo — e **nessuno dei dodici
e' ancora stato visto girare in gioco**, che secondo la regola 3 del progetto vuol dire che nessuno
dei dodici e' finito.

Delle cinque che restano, tre (B13, B12, B3) sono contenuto nuovo e due (B15, B16) sono
infrastruttura.

La lacuna **L1** — dopo il diciottesimo incarico non c'e' niente — e' **chiusa**: l'Abisso e' il
traguardo che ne apre un altro, e ha il cronometro che trasforma un contenuto ripetibile in una
prova di ottimizzazione. Restano aperte **L4** in parte (un Gate e' meno uguale a se stesso di
prima, ma la pianta e' ancora quella) e **L7** in parte (sette ombre da inseguire, un pezzo unico
solo).

---

## 7. Come si sa che un blocco è finito

Le regole del progetto dicono già che «compila» non è «fatto». Questo PRD aggiunge tre domande, e
sono le stesse per ogni blocco:

1. **Il gioco risponde da solo a cos'è, come si usa e perché conviene** — senza aprire un browser?
2. **Il giocatore sa dire cosa gli darà il prossimo livello, prima di prenderlo?**
3. **Muori, esci dal mondo, rientra: è tutto ancora lì?**

Più le due reti che si stendono da sole: `python tools/collaudo.py` pulito e `.\gradlew.bat test`
verde, con le righe nuove aggiunte.
