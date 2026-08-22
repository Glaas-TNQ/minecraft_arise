# Creare mod di Minecraft con Claude — Report di fattibilità e setup

_Data: 21 agosto 2026 — ricerca web + verifica dell'ambiente locale_

---

## 1. Risposta breve

**Sì, è già stato fatto, e il momento è insolitamente favorevole.**

Esistono già: skill/plugin per Claude Code dedicati al modding, repository reali di mod mantenute con Claude, mod che portano Claude *dentro* il gioco, e server MCP che permettono a Claude di lanciare, ispezionare e testare Minecraft da solo.

Il fattore che cambia tutto è arrivato a dicembre 2025: **Minecraft Java non è più offuscato**. Da 26.1 in poi Mojang distribuisce il codice con nomi reali (`CreeperEntity` invece di `brc`), inclusi i nomi dei parametri. Questo elimina lo strato di "mappings" che storicamente rendeva il modding ostile agli LLM: prima il modello doveva conoscere mapping di terze parti (Yarn/MCP) che cambiavano a ogni versione; oggi legge e scrive codice con nomi leggibili. Fabric ha di conseguenza **dismesso Yarn** da 26.1 in avanti: si usano le mappings ufficiali Mojang.

Il rischio residuo non è più l'offuscamento, ma la **velocità di cambiamento delle API** (vedi §6).

---

## 2. Lo stato dell'ecosistema (agosto 2026)

| Elemento | Stato attuale |
|---|---|
| Versione Minecraft corrente | **26.2** (giugno 2026); prima 26.1.x, prima ancora 1.21.11 |
| Offuscamento | **Rimosso** da 26.1 — mappings ufficiali Mojang, niente Yarn |
| JDK richiesto | **Java 25** (sia Fabric che NeoForge per le versioni 26.x) |
| Build system | Gradle — Fabric: Loom 1.17 + Gradle 9.5.1 su 26.2; NeoForge: ModDevGradle (consigliato) o NeoGradle |
| Loader consigliati | **Fabric** (leggero, aggiornamenti rapidi, performance) o **NeoForge** (standard de-facto per mod di contenuto/tech su 1.21+) |
| Forge "classico" | coda lunga, non consigliato per progetti nuovi |
| Quilt | nicchia |

**Nota importante:** Fabric e NeoForge non sono intercambiabili — una mod scritta per uno non gira sull'altro. Chi vuole entrambi usa **Architectury** (codice comune + due moduli), che è quello che fa il repo `chimericdream/minecraft-mods` citato sotto.

### Fabric o NeoForge?

Per un primo progetto assistito da AI consiglio **Fabric**:

- API più piccola e più regolare → meno superficie su cui il modello può sbagliare;
- ciclo di aggiornamento quasi immediato sulle nuove versioni;
- la documentazione Fabric è versionata per versione di Minecraft (26.2, 26.1, 1.21.11...), quindi si può puntare Claude alla pagina esatta;
- il generatore di template ufficiale produce un progetto compilabile in un minuto.

NeoForge ha senso se l'obiettivo è integrarsi con l'ecosistema tech (Create, AE2, Mekanism) o pubblicare in modpack grossi.

---

## 3. Chi l'ha già fatto — riferimenti concreti

### 3.1 Skill / plugin per Claude Code (modding vero e proprio)

- **`chouzz/minecraft-mod-dev`** — skill Claude Code per NeoForge/Fabric 1.21+. Fetch dinamico della documentazione API aggiornata, rilevamento automatico della configurazione del progetto, pattern moderni (Data Components, Data Generation, Convention Tags), integrazioni JEI/AE2/Create.
  Installazione: `/plugin marketplace add chouzz/minecraft-mod-dev` poi `/plugin install minecraft-mod-dev@chouzz-plugins`
- **`chapmanjw/minecraft-java-fabric-claude-plugin`** — più ambizioso: 4 skill di setup guidato (`setup-fabric`, `setup-mod`, `setup-server`, `setup-connect`), un agent `minecraft-builder` e 28 skill specializzate. Include un **server MCP embedded in una mod Fabric** che esegue le tool call sul thread principale del server, più un server di ispezione (porta 8766) che dà a Claude la vista in prima persona del giocatore per verificare il risultato. Versioni supportate: 1.21.11, 26.1.1, 26.1.2, 26.2.
- Skill equivalenti pubblicate su marketplace terzi (mcpmarket, claudemarketplaces).

### 3.2 Progetti reali mantenuti con Claude

- **`chimericdream/minecraft-mods`** — monorepo Architectury con 15 mod attive, Minecraft 26.2, Java 25, mappings Mojang (migrazione da Yarn completata), Fabric Loader 0.19.3 + NeoForge 26.2.0.15-beta. Il suo `CLAUDE.md` è il miglior esempio pratico di come si istruisce Claude su un progetto di modding: comandi di build, regole su cosa non committare, workaround specifici della versione (es. su 26.2 i data component non sono legati finché il server non ricarica, quindi ogni metodo di datagen deve chiamare esplicitamente `BuiltInRegistries.DATA_COMPONENT_INITIALIZERS.build(...)`), convenzioni di versioning e changelog.

### 3.3 Server MCP per il ciclo di test

Sono la parte che trasforma "Claude scrive codice" in "Claude verifica quello che ha scritto":

- **`InventivetalentDev/minecraft-mcp`** — Claude Code interagisce con server *e* client in esecuzione: leggere log, eseguire comandi, installare plugin, screenshot, controllare il player, chiamare API Bukkit/Paper via reflection.
- **`cuspymd/mcp-server-mod`** — mod Fabric che espone un server MCP HTTP dentro il client o il server dedicato.
- **`MCDxAI/minecraft-dev-mcp`** — decompila, rimappa, cerca e analizza il sorgente di Minecraft su richiesta dell'AI.
- **`ogmatrix/mcmodding-mcp`** — knowledge base di modding aggiornata.
- **McClaude** — bridge Claude Code ↔ server Minecraft live (lato plugin Bukkit/Paper).

### 3.4 Claude *dentro* il gioco (categoria diversa, ma esiste)

- **Claudecraft** (Modrinth) — mod Forge che porta Claude nei computer/turtle di CC:Tweaked; si aggancia a una sessione Claude Code locale, senza API key.
- **`Enablement-Engineering/claude-craft`** — Claude Code come assistente in-game.

---

## 4. Cosa serve — checklist operativa

### 4.1 Stato attuale della tua macchina (verificato ora)

| Requisito | Stato |
|---|---|
| Git | ✅ installato (`C:\Program Files\Git`) |
| JDK 25 | ❌ **assente** — nessun Java nel PATH, nessuna cartella JDK trovata |
| IntelliJ IDEA | ❌ assente |
| Gradle | ⚠️ non serve a livello globale (si usa il wrapper `gradlew` del progetto) |
| Cartella progetto | vuota (`C:\Users\Luca\Desktop\Progetti\mod_minecraft`) |

### 4.2 Da installare

1. **JDK 25 (64-bit)** — Microsoft OpenJDK o Eclipse Temurin. È l'unico requisito bloccante.
2. **IntelliJ IDEA Community** — non obbligatorio se lavori con Claude Code + VS Code, ma serve davvero per: debugger, breakpoint, navigazione nel sorgente decompilato di Minecraft e "vai alla definizione" quando bisogna capire come funziona una classe vanilla. Consiglio di installarlo comunque.
3. **Account Minecraft Java valido** — l'ambiente di sviluppo lancia il gioco vero (`runClient`) e richiede il login.
4. Opzionale: **Prism Launcher / MultiMC** per testare il jar compilato in un'istanza pulita separata dal tuo profilo di gioco.

### 4.3 Da generare (non da scrivere a mano)

- **Fabric:** template dal generatore ufficiale <https://fabricmc.net/develop/template/> (licenza CC0) — scegli versione Minecraft, package e nome mod, e produce un progetto Gradle già compilabile.
- **NeoForge:** <https://neoforged.net/mod-generator/> (oppure clone di un MDK da `github.com/NeoForgeMDKs`), con ModDevGradle come plugin di build.

Il primo `gradlew build` scarica le dipendenze e decompila Minecraft: **la prima volta ci mette parecchio** (diversi minuti, spesso più di dieci). È normale, non è un blocco.

### 4.4 Da configurare lato Claude

1. **`CLAUDE.md` nella root del progetto** — la cosa che fa più differenza in assoluto. Deve contenere almeno:
   - versione Minecraft target, loader e versione loader, versione Java, versione Loom/ModDevGradle — **scritti esplicitamente**;
   - comandi di build e run (`./gradlew build`, `./gradlew runClient`, `./gradlew runDatagen`);
   - la regola "usa mappings ufficiali Mojang, non Yarn, non MCP";
   - convenzioni di registrazione (dove stanno i registry di block/item, come si nominano gli ID);
   - la regola "prima di scrivere codice su un'API che non conosci, apri la documentazione della versione esatta";
   - i workaround noti della versione in uso.
2. **Uno skill/plugin di modding** — `chouzz/minecraft-mod-dev` è il più a basso attrito per iniziare.
3. **Opzionale ma potente: un server MCP** per far testare a Claude la mod in gioco (log, comandi, screenshot) invece di fermarsi alla compilazione.
4. **Permessi/allowlist** per `gradlew`, così che io possa compilare senza chiederti conferma a ogni ciclo.

---

## 5. Flusso di lavoro consigliato

1. Installi JDK 25 (+ IntelliJ).
2. Generiamo il template Fabric per **26.2** dentro `mod_minecraft`.
3. Primo `gradlew build` "a vuoto", per scaricare tutto e verificare che l'ambiente sia sano.
4. Scriviamo il `CLAUDE.md` con versioni e comandi.
5. **Primo obiettivo minimo:** un item nuovo con texture e ricetta, registrato correttamente e visibile in creative. Serve a validare l'intera catena (registro → datagen → asset → build → gioco), non a fare qualcosa di interessante.
6. Da lì si sale: blocco con block entity, comando, evento, entità, GUI, mixin (per ultimo).
7. Ciclo di lavoro: **Claude scrive → `gradlew build` → `runClient` → verifica in gioco → correzione**. Il passaggio "verifica in gioco" non è saltabile: una compilazione riuscita non dimostra che la mod funzioni.

---

## 6. Rischi e limiti reali

- **API che si muovono in fretta.** Le classi di Minecraft cambiano posto a ogni release. Solo in 26.2: gli ID di blocchi e item sono stati separati in `BlockIds`/`BlockItemIds`/`ItemIds`, i metodi di GUI/HUD sono stati spostati in classi dedicate, la gestione delle schermate è passata da `Minecraft` a `Minecraft.gui`, e le chiamate OpenGL dirette vanno migrate all'API Blaze3D. Un modello che si affida alla memoria produce codice di una versione precedente che **sembra giusto** e non compila — o peggio, compila e si comporta male. Contromisura: versione pinnata nel `CLAUDE.md`, documentazione letta al momento, e la build come giudice finale.
- **Mixin.** Sono la tecnica per modificare il comportamento vanilla e sono anche il modo più rapido per rompere il gioco in modo silenzioso o incompatibile con altre mod. Vanno introdotti solo quando servono davvero, uno alla volta, con test.
- **Asset grafici.** Il codice si genera bene; texture e modelli no. Vanno fatti a mano o con tool grafici — e vedi §7 sulle regole di pubblicazione.
- **Datagen.** L'approccio moderno genera i JSON (ricette, loot table, model, tag) da codice invece di scriverli a mano. È il modo giusto, ma aggiunge un passaggio (`runDatagen`) che va ricordato e che ha quirk specifici di versione.
- **Lato client vs lato server.** Sbagliare su quale lato gira un pezzo di codice è l'errore classico e non emerge in singleplayer.
- **Tempi di build.** Ogni ciclo di verifica costa minuti, non secondi. Conviene accorpare le modifiche invece di iterare su micro-cambiamenti.

---

## 7. Pubblicazione — regole aggiornate sui contenuti AI

Se pensi di pubblicare su **Modrinth**, le regole sono cambiate di recente ed è meglio saperlo prima, non dopo:

- i progetti **interamente o prevalentemente generati dall'AI non sono ammessi**; quelli *assistiti* dall'AI sì, purché il contributo umano sia primario e significativo;
- serve attivare la disclosure **"Contains AI-generated content"** se una parte sostanziale del codice, degli asset, del design o della pagina di progetto è prodotta da AI;
- **nessuna immagine** (icona, galleria, descrizione) può essere generata o derivata da AI;
- periodo di grazia per adeguarsi: **fino al 27 settembre 2026**.

In pratica: usare Claude come strumento di sviluppo è legittimo, ma vanno dichiarati l'uso e mantenuto un contributo umano reale — e le texture vanno disegnate, non generate.

---

## 8. Decisioni che servono da te

1. **Versione target:** 26.2 (più recente, meno mod di terze parti con cui integrarsi) oppure 1.21.11 (ecosistema più ricco, ma ancora offuscata → mappings, quindi peggiore per il flusso con AI). Consiglio: **26.2**.
2. **Loader:** Fabric (consigliato) o NeoForge.
3. **Che mod vuoi fare** — anche solo l'idea grezza. Il tipo di mod cambia molto il setup: contenuto (item/blocchi/entità) è la strada facile; modifiche alla generazione del mondo, GUI complesse o mixin pesanti sono un'altra categoria.
4. **Singleplayer o anche server dedicato?**
5. Vuoi che installi anche il **plugin MCP** per farmi testare le mod in gioco, o preferisci testare tu a mano all'inizio?

---

## Fonti

- [Fabric — Setting Up Your Development Environment](https://docs.fabricmc.net/develop/getting-started/setting-up)
- [Fabric for Minecraft 26.2](https://fabricmc.net/2026/06/15/262.html)
- [Fabric — Migrating Mappings 26.2](https://docs.fabricmc.net/develop/porting/mappings/)
- [Fabric Template Mod Generator](https://fabricmc.net/develop/template/)
- [NeoForge — Getting Started](https://docs.neoforged.net/docs/gettingstarted/)
- [NeoForge Mod Generator](https://neoforged.net/mod-generator/)
- [Minecraft Java Deobfuscation: The Game-Changing 2026 Update](https://www.javacodegeeks.com/2026/02/minecraft-java-deobfuscation-the-game-changing-2026-update.html)
- [chouzz/minecraft-mod-dev — Claude Code skill](https://github.com/chouzz/minecraft-mod-dev)
- [chapmanjw/minecraft-java-fabric-claude-plugin](https://github.com/chapmanjw/minecraft-java-fabric-claude-plugin)
- [chimericdream/minecraft-mods — CLAUDE.md](https://github.com/chimericdream/minecraft-mods/blob/main/CLAUDE.md)
- [InventivetalentDev/minecraft-mcp](https://github.com/InventivetalentDev/minecraft-mcp)
- [cuspymd/mcp-server-mod](https://github.com/cuspymd/mcp-server-mod)
- [MCDxAI/minecraft-dev-mcp](https://github.com/MCDxAI/minecraft-dev-mcp)
- [Claudecraft (Modrinth)](https://modrinth.com/project/oQ8wCTba)
- [Enablement-Engineering/claude-craft](https://github.com/Enablement-Engineering/claude-craft)
- [Modrinth — New AI rules and project disclosures](https://modrinth.com/news/article/ai-policy-and-disclosures/)
- [Modrinth — Content Rules](https://modrinth.com/legal/rules)
- [Fabric vs NeoForge: Which Minecraft Mod Loader? (2026)](https://www.icedfoxstudios.com/tutorials/fabric-vs-neoforge-minecraft-modding-2026/)
