# Arise — mod Minecraft (Fabric)

Mod di progressione ispirata a Solo Leveling: Sistema con livelli e statistiche, Gate/dungeon
procedurali, esercito d'ombra. Design completo in `DESIGN-solo-leveling.md`, contesto
sull'ecosistema in `REPORT-modding-con-claude.md`. **Leggi il design prima di implementare un
sistema nuovo.**

---

## Ambiente — valori esatti, non negoziabili

| | |
|---|---|
| Minecraft | **26.2** |
| Loader | Fabric Loader **0.19.3** |
| Fabric API | **0.157.0+26.2** |
| Loom | **1.17-SNAPSHOT** |
| Java | **25** (`JAVA_HOME=C:\Program Files\Microsoft\jdk-25.0.4.7-hotspot`) |
| Mappings | **ufficiali Mojang** — Minecraft è non offuscato da 26.1 |
| mod id | `arise` |
| package | `com.luca.arise` (client: `com.luca.arise.client`) |

Le versioni stanno in `gradle.properties`. Se una versione va cambiata, si cambia lì e si
aggiorna questa tabella nello stesso commit.

## Comandi

```powershell
$env:JAVA_HOME = "C:\Program Files\Microsoft\jdk-25.0.4.7-hotspot"
.\gradlew.bat build        # compila + remappa il jar
.\gradlew.bat runClient    # avvia il client di sviluppo (account fittizio, nessun login)
.\gradlew.bat runServer    # avvia il server di sviluppo
.\gradlew.bat --stop       # ferma il daemon se il build si comporta in modo strano
```

Su macOS e Linux gli stessi comandi sono `./gradlew ...`, con
`export JAVA_HOME=$(/usr/libexec/java_home -v 25)`. Gli avviatori da doppio clic sono
`avvia-*.bat` su Windows e `avvia-*.command` altrove: fanno la stessa cosa e vanno
tenuti allineati fra loro.

Il jar finale finisce in `build/libs/arise-<versione>.jar` (ignora quello con suffisso `-dev`).

---

## Regole per Claude

### Mappings e API

1. **Mappings ufficiali Mojang.** Non usare nomi Yarn (`Identifier` sta in
   `net.minecraft.resources`, non in `net.minecraft.util`), non usare nomi MCP/Forge.
   Fabric ha dismesso Yarn da 26.1 in avanti.
2. **Non scrivere codice su un'API a memoria.** Le classi di Minecraft cambiano posto a ogni
   release e quasi tutto il codice trovabile online è per 1.20.1 o 1.21.x. Prima di usare
   un'API che non hai già visto **in questo repo**, apri la documentazione della versione 26.2
   o leggi il sorgente decompilato. Punti caldi noti in 26.2:
   - **permessi dei comandi**: `CommandSourceStack.hasPermission(int)` **non esiste più**. Si usa
     `Commands.LEVEL_GAMEMASTERS.check(source.permissions())` (`PermissionCheck` +
     `PermissionSet`). `LEVEL_ALL` / `MODERATORS` / `GAMEMASTERS` / `ADMINS` / `OWNERS`
     sostituiscono i vecchi livelli 0-4;
   - `Identifier` sta in `net.minecraft.resources`, **non** è `ResourceLocation`;
   - id di blocchi e item separati in `BlockIds` / `BlockItemIds` / `ItemIds`;
   - **`GuiGraphics` NON ESISTE PIÙ.** Tutto il disegno 2D passa da `GuiGraphicsExtractor`, e
     `Renderable.render(...)` è diventato `extractRenderState(GuiGraphicsExtractor, int, int,
     float)`. Vale per `Screen` e per i widget. Ogni tutorial di GUI in circolazione è obsoleto;
   - `Minecraft.setScreen(...)` → **`setScreenAndShow(...)`**;
   - HUD: si implementa `HudElement.extractRenderState(GuiGraphicsExtractor, DeltaTracker)` e si
     registra con `HudElementRegistry.attachElementAfter(VanillaHudElements.X, id, element)`;
   - keybind: il modulo è `fabric-key-mapping-api-v1`, la classe **`KeyMappingHelper`**
     (era `KeyBindingHelper`), e la categoria è un `KeyMapping.Category` tipizzato, non più una
     stringa;
   - entità: `addAdditionalSaveData`/`readAdditionalSaveData` usano `ValueOutput`/`ValueInput`,
     non più `CompoundTag`; `Level.random` è protetto, si usa `getRandom()`;
     `Level.addFreshEntity` sta su `ServerLevel`/`LevelWriter`;
     per trovare un'entità dall'UUID: `ServerLevel.getEntityInAnyDimension(UUID)`;
   - modelli entità: il registro Fabric è `ModelLayerRegistry` (non `EntityModelLayerRegistry`);
   - rendering 3D: chiamate OpenGL dirette da migrare a Blaze3D;
   - **`RenderType` si è sdoppiato**: la classe base sta in
     `net.minecraft.client.renderer.rendertype.RenderType` e non ha più i metodi di fabbrica —
     `entityTranslucent(...)`, `entityCutout(...)`, `eyes(...)` stanno in **`RenderTypes`**
     (plurale), stesso package. Un modello sceglie il materiale passando la funzione al
     costruttore: `super(root, RenderTypes::entityTranslucent)`;
   - `DustParticleOptions(int colore, float scala)` — il colore è RGB impacchettato, ed è
     l'unico particellare vanilla che accetta un colore arbitrario;
   - `ServerLevel.sendParticles(tipo, x, y, z, 0, dx, dy, dz, velocità)` con **conteggio zero**
     spara *una* particella con `dx/dy/dz` come vettore velocità: è il modo di disegnare anelli e
     spirali invece di nuvole casuali;
   - **i blocchi colorati non hanno più una costante per colore**: `Blocks.LIGHT_GRAY_CONCRETE`
     non esiste, si usa `Blocks.CONCRETE.lightGray()` o `.pick(DyeColor.X)`. Vale anche per
     `STAINED_GLASS`, `DYED_TERRACOTTA`, `CONCRETE_POWDER` — sono `ColorCollection<Block>`;
   - `Entity.interact` prende tre argomenti (`Player`, `InteractionHand`, `Vec3`), e `hurtServer`
     è astratto anche per le entità che non si possono colpire;
   - `ValueInput.getIntOr(nome, default)` sostituisce `getInt`; `ValueOutput.store(nome, codec, v)`
     salva qualsiasi cosa abbia un Codec;
   - registri dei pacchetti: `PayloadTypeRegistry.clientboundPlay()` (non `playS2C`);
   - `ServerPlayer` non ha `playNotifySound`: per un suono a un solo giocatore o si costruisce il
     pacchetto a mano, o lo si suona alla sua posizione con `Level.playSound(null, x, y, z, ...)`.
   - **il codec di un record si ferma a sedici campi**: `RecordCodecBuilder.group(...)` non ne
     accetta di più. Quando `AriseConfig` ci è arrivata, la via è stata annidare (`HunterConfig`
     tiene rango, equipaggiamento, negozio e gemme), non spezzare la radice;
   - `ServerLivingEntityEvents.AFTER_DAMAGE` ha firma `(LivingEntity, DamageSource, float base,
     float inflitto, boolean parato)`; il danno da spine rientra da lì, quindi ogni effetto che
     restituisce danno ha bisogno di una guardia contro il rimbalzo infinito;
   - `DamageSources.thorns(Entity)` esiste; il danno si applica con
     `LivingEntity.hurtServer(ServerLevel, DamageSource, float)`;
   - `LevelReader.hasChunkAt(BlockPos)` è il controllo da fare **prima** di leggere il terreno
     lontano: senza, si obbliga il server a generare chunk che nessuno ha chiesto. `getMinY()`
     sostituisce `getMinBuildHeight()`, e la solidità si chiede a `BlockState.isSolidRender()`;
   - gli attributi vanilla utili sono molti più dei quattro ovvi: `ARMOR_TOUGHNESS`, `ATTACK_SPEED`,
     `KNOCKBACK_RESISTANCE`, `JUMP_STRENGTH`, `ENTITY_INTERACTION_RANGE`, `MAX_ABSORPTION`, `LUCK`,
     `ATTACK_KNOCKBACK`. Attenzione a `KNOCKBACK_RESISTANCE`, che si ferma a 1;
   - **`String.format` usa la lingua di sistema**: su una macchina italiana `%.2f` scrive la
     virgola, quindi ogni trucco del tipo `.replace(".00", "")` non trova mai niente da tagliare.
     I decimali si tolgono guardando il numero, non la stringa.
   - **`Screen.mouseClicked` è cambiato**: prende `(MouseButtonEvent, boolean doppioClick)`, non
     più tre argomenti. Le coordinate stanno in `event.x()` e `event.y()`. `mouseScrolled` invece
     ha ancora quattro `double`;
   - `LocalPlayer` non ha `displayClientMessage`: sul client si usa `sendSystemMessage(Component)`;
   - `GuiGraphicsExtractor` ha `enableScissor/disableScissor`, `outline`, `fillGradient` e
     `setComponentTooltipForNextFrame`: liste che scorrono e riquadri si disegnano senza texture.
3. **La compilazione non è una verifica.** Un sistema è "fatto" quando lo si è visto
   funzionare in `runClient`. Vale soprattutto per AI delle ombre, generazione dungeon e HUD.

### Architettura

4. **La logica sta sul server.** XP, livelli, cooldown, evocazioni, danni: tutto server-side.
   Il client riceve stato da disegnare e manda solo intenzioni ("ho premuto il tasto 1"),
   validate lato server. Vale anche in singleplayer, che è comunque un server.
   Sorgenti separati: `src/main` (comune/server), `src/client` (solo client).
5. **Dati giocatore: Fabric Data Attachment API**, con `persistent()` (Codec) e `syncWith()`.
   Niente NBT scritto a mano su `PlayerEntity`, niente mixin per aggiungere campi.
   - usa **tipi immutabili** e aggiornali solo tramite i metodi dell'API, altrimenti
     persistenza e sync falliscono in silenzio;
   - **il dato deve sopravvivere alla morte**: l'API ha `.copyOnDeath()` nel builder, ed è la
     prima cosa da mettere. Se un caso non è coperto, fallback su `ServerPlayerEvents.COPY_FROM`.
     Perdere il livello quando si muore è il bug numero uno di questo genere di mod.
6. **Statistiche → attributi:** un solo `EntityAttributeModifier` per statistica, con
   `Identifier` fisso, **ricalcolato e rimpiazzato** a ogni cambiamento. Mai aggiungere un
   modificatore per livello. Riapplicare al login.
   - gli id vanilla non hanno più il prefisso `generic` (`minecraft:max_health`);
   - gli attributi hanno un range massimo: verificarlo prima di promettere numeri assurdi;
   - `movement_speed` oltre ~3× rompe la fisica del client: la velocità estrema va gestita
     con abilità a tempo, non come statistica passiva.
7. **Le ombre non evocate non sono entità.** L'esercito vive come dati nell'attachment del
   giocatore e diventa entità solo all'evocazione (vedi §3.5 del design). Non tenere mai mob
   "parcheggiati" nel mondo.
8. **Mixin: ultima risorsa.** Prima si cercano gli eventi di Fabric API. Un mixin si aggiunge
   uno alla volta, con una motivazione scritta nel commento.
9. **Niente numeri di bilanciamento hardcoded.** Curva XP, moltiplicatori, cap, probabilità di
   estrazione: tutto in config fin dall'inizio.

### Stile e struttura

10. Registrazioni centralizzate per tipo (`ModItems`, `ModEntities`, `ModAttachments`,
    `ModCommands`, …), chiamate dall'entrypoint. Niente registrazioni sparse.
11. Ogni identificatore passa da `AriseMod.id("...")`.
12. Testi visibili al giocatore sempre tradotti (`Component.translatable`), mai stringhe
    letterali. Lingua base: `en_us`, più `it_it`.
13. Asset generabili da codice (ricette, loot table, model, tag) si generano con la
    **data generation**, non si scrivono a mano.
    - **suoni**: eventi propri nel namespace `arise` (`ModSounds`), mai le costanti di
      `SoundEvents` sparse nella logica. Un evento nostro ha il sottotitolo tradotto, si prova con
      `/playsound`, e il giorno in cui ci sarà un `.ogg` vero si cambia una riga di `sounds.json`.
      Finché non c'è, le voci di `sounds.json` puntano ai file vanilla con il prefisso
      `minecraft:` — i riferimenti fra namespace funzionano;
    - **effetti**: particellari e suoni passano tutti da `AriseFx`, mai `sendParticles` o
      `playSound` sparsi nei gestori. Serve a non far suonare uguali due momenti diversi.
14. Comandi di debug (`/arise ...`) per ogni sistema nuovo: senza, ogni test costa mezz'ora
    di gioco.

---

## Stato del progetto

- [x] **F0** — ambiente, template 26.2, build verde
- [x] **F1** — Il Sistema: XP dai mob, livelli, punti statistica, attributi applicati
- [x] **F2** — HUD e schermata di stato
- [x] **F3** — Prima ombra: estrazione, evocazione, segue e combatte
- [x] **F4** — Esercito: ranghi, livellamento, schermata di gestione
- [x] **Blocco 5** — Comando esercito: posture, soul coin, rinomina/colore/potenzia/congeda, arena
- [x] **F5** — Gate: dimensione `arise:gate`, layout procedurale, boss, ricompense
- [ ] **F6** — Abilità con cooldown: 4 abilità, barra HUD, validazione server — *compilato, da verificare*
- [ ] **F7** — Rifinitura: suoni propri, particellari, ombra translucida e incurvata — *compilato, da verificare*
- [ ] **F8** — Il varco: i Gate compaiono nel mondo e si analizzano prima di entrare, con temi —
      *compilato, da verificare*
- [ ] **F9** — Le città: cinque hub con Associazione dei Cacciatori, costruzione a budget e rete
      di viaggio — *compilato, da verificare*
- [ ] **B1** — Somma delle sorgenti e inventario del Cacciatore: `StatSources`, dodici statistiche,
      ventiquattro slot che si sbloccano col rango, zaino, schermata — *compilato, da verificare*
- [ ] **B2** — Abyss Shop: assortimento tirato da un seed, voci sigillate, ritiro a prezzo
      crescente — *compilato, da verificare*
- [ ] **B3** — Varchi spontanei: i Gate si aprono da soli vicino a chi gioca — *compilato, da
      verificare*
- [ ] **B4** — Gemme e incastonature: cinque effetti passivi, estrazione al banco dell'Associazione
      — *compilato, da verificare*
- [ ] **B5** — Bottino: i Gate lasciano pezzi, gemme e pergamene — *compilato, da verificare*
- [ ] **C1** — Mondo già pronto: le Associazioni si tirano su alla prima entrata — *compilato, da
      verificare*
- [ ] **C2** — Bottino dai mob dentro i Gate — *compilato, da verificare*
- [ ] **C3** — Telaio comune per le otto schermate: liste che scorrono, dettaglio a fianco, icone
      disegnate nel codice — *compilato, da verificare*
- [ ] **C4** — Il risveglio e la catena di nove incarichi che apre i sistemi uno alla volta —
      *compilato, da verificare*

Aggiorna questa lista quando una fase è **verificata in gioco**, non quando compila.
