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
.\gradlew.bat build        # compila, esegue le prove, remappa il jar
.\gradlew.bat test         # solo le prove
.\gradlew.bat runClient    # avvia il client di sviluppo (account fittizio, nessun login)
.\gradlew.bat runServer    # avvia il server di sviluppo
.\gradlew.bat --stop       # ferma il daemon se il build si comporta in modo strano

python tools/collaudo.py   # il collaudo statico: esce con 1 se trova qualcosa
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
   - **componenti dati**: le costanti vanilla stanno in `net.minecraft.core.component.DataComponents`,
     e un componente proprio si registra con
     `Registry.register(BuiltInRegistries.DATA_COMPONENT_TYPE, id, DataComponentType.builder()...)`.
     Se il tipo implementa `TooltipProvider`, le sue righe compaiono nel tooltip solo dopo averlo
     dichiarato a `ItemComponentTooltipProviderRegistry` (modulo `fabric-item-api-v1`);
   - **menu e caselle**: il modulo Fabric si chiama ora `fabric-menu-api-v1` (`ExtendedMenuType`,
     `ExtendedMenuProvider`) — `fabric-screen-handler-api-v1` non esiste piu'. Lato client la
     registrazione e' `MenuScreens.register(tipo, Schermata::new)`, accessibile grazie al
     classtweaker di quel modulo;
   - **`Button` e' astratto** in 26.2: una sottoclasse deve implementare
     `extractContents(GuiGraphicsExtractor, int, int, float)` (`extractWidgetRenderState` e'
     `final`). Lo sfondo di un widget si disegna con `extractDefaultSprite(...)`;
   - **`AbstractContainerScreen` non ha piu' `renderBg`**: il fondo si dipinge in
     `extractBackground(...)`, come per le nostre schermate;
   - vanilla ha **rame** fra i materiali di armi e armature: la scala completa e' cuoio → rame →
     maglia → ferro → oro → diamante → netherite, che sono sei gradini utili per sei ranghi;
   - `ItemStack.OPTIONAL_CODEC` per le caselle vuote, e `ByteBufCodecs.fromCodecWithRegistries`
     per mandarne una lista in rete. Gli `ItemStack` si possono salvare in un attachment perche'
     l'API passa da `ValueOutput.store(nome, codec, valore)`, che i registri ce li ha;
   - `LocalPlayer` non ha `displayClientMessage`: sul client si usa `sendSystemMessage(Component)`;
   - `GuiGraphicsExtractor` ha `enableScissor/disableScissor`, `outline`, `fillGradient` e
     `setComponentTooltipForNextFrame`: liste che scorrono e riquadri si disegnano senza texture;
   - **registrare un blocco**: `BlockBehaviour.Properties.setId(ResourceKey<Block>)` e' obbligatorio
     — senza, il costruttore lancia invece di registrarsi col nome sbagliato. Idem per l'oggetto:
     `new BlockItem(block, new Item.Properties().useBlockDescriptionPrefix().setId(itemKey))`.
     `BlockEntityType` non ha piu' il `Builder`: `new BlockEntityType<>(fabbrica, Set.of(blocchi))`;
   - **`BaseEntityBlock.getRenderShape` risponde `INVISIBLE`**. Un macchinario che non sia un
     forziere deve riscriverlo a `MODEL`, altrimenti esiste, si apre, funziona — e non si vede;
   - `BaseContainerBlockEntity` da' Container, MenuProvider e Nameable in un colpo solo, ma
     `getContainerSize()` resta da implementare. Gli oggetti si salvano con
     `ContainerHelper.saveAllItems/loadAllItems(ValueOutput|ValueInput, NonNullList)`;
   - **automazione con le tramogge**: serve `WorldlyContainer` (tre metodi) — un `Container` e
     basta accetta da ogni lato e lascia portare via tutto, comprese le caselle che non si devono
     toccare. La barra di avanzamento passa da `ContainerData` + `addDataSlots`, non da un
     pacchetto proprio: e' l'unico canale che il gioco aggiorna da solo e solo a chi guarda;
   - **ricette dal codice**: `serverLevel.recipeAccess().getRecipeFor(RecipeType.SMELTING,
     new SingleRecipeInput(stack), level)`, e `Recipe.assemble(input)` prende **un solo**
     argomento in 26.2;
   - **percorsi degli asset**: `assets/<ns>/blockstates/`, `assets/<ns>/models/block/`, e il
     modello dell'oggetto in **`assets/<ns>/items/`** (`{"model":{"type":"minecraft:model",
     "model":"..."}}`), non piu' in `models/item/`. Dati: `data/<ns>/recipe/` e
     `data/<ns>/loot_table/blocks/` (singolare). **Senza loot table il blocco non lascia niente**;
   - le costanti di `CreativeModeTabs` sono **private** in 26.2: la chiave di una scheda vanilla
     si ricostruisce con `ResourceKey.create(Registries.CREATIVE_MODE_TAB, ...)`;
   - **`AbstractVillager` non esiste piu'** e i villager stanno in
     `net.minecraft.world.entity.npc.villager`. Per un NPC che commercia non serve ereditare da
     loro: basta implementare `net.minecraft.world.item.trading.Merchant` (tredici metodi banali),
     e il suo `openTradingScreen(Player, Component, int)` apre la finestra di scambio vanilla.
     `MerchantOffer(ItemCost, ItemStack, usi, xp, moltiplicatore)`, e `ItemCost` confronta
     l'**oggetto**, non i componenti — una valuta appoggiata su un item vanilla e' comprabile con
     quell'item vanilla;
   - `javax.annotation.Nullable` **non e' nel classpath**: o si usa l'annotazione di JetBrains, o
     non si annota;
   - un NPC umanoide non ha bisogno di texture nuove: vanilla ne ha **nove** in
     `textures/entity/player/wide/` (alex, ari, efe, kai, makena, noor, steve, sunny, zuri). Il
     modello e' `HumanoidModel` su un layer proprio, e il renderer `HumanoidMobRenderer`;
   - imbardata di Minecraft: **0 e' sud**, 90 ovest, 180 nord, 270 est. Vale per `snapTo` e per
     `setYHeadRot`/`setYBodyRot`, che vanno impostati entrambi o l'entita' nasce col collo storto;
   - **costruire lontano dallo spawn: il costo sono i chunk, non i blocchi.** Un `setBlock` in un
     chunk mai generato lo fa generare *sul thread del server*, e costa fra un decimo e mezzo
     secondo. Un budget contato in blocchi non ne sa niente. Le tre regole, ognuna pagata con un
     difetto vero:
     1. **iterare per chunk**, non per colonne: per colonne si attraversano 32 chunk a passata, e
        un solo tick ne toccava 190 — sessanta secondi, e il watchdog spegne il server;
     2. **chiedere i chunk in anticipo** con
        `ServerChunkCache.addTicketAndLoadWithRadius(TicketType, ChunkPos, raggio)` e saltare il
        battito finche' `getChunkNow` risponde `null`. **`TicketType.UNKNOWN` non va**: ha un
        timeout, e la chiamata lancia *"can expire before it loads, cannot fetch asynchronously"*.
        `TicketType` e' un record con costruttore pubblico —
        `new TicketType(TicketType.NO_TIMEOUT, TicketType.FLAG_LOADING)` carica senza simulare e
        senza persistere. **I ticket vanno rimossi**, o si tiene in memoria mezzo mondo;
     3. **fermarsi da soli** quando `server.getAverageTickTimeNanos()` e' gia' sopra il battito.
   - **mettere qualcosa nel mondo "dal seme"**: una `Feature<NoneFeatureConfiguration>` registrata
     in `BuiltInRegistries.FEATURE`, la sua `configured_feature`/`placed_feature` in JSON sotto
     `data/<ns>/worldgen/`, e l'aggancio ai biomi con
     `BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(), passo, chiave)`. Senza
     modificatori di piazzamento la feature gira **una volta per chunk**, all'angolo del chunk; si
     scrive **solo dentro quel chunk**. La vegetazione vanilla sporge nei chunk vicini: ciò che deve
     restare pulito si spiana al passo `RAW_GENERATION` (gli alberi non nascono su un marciapiede)
     e si completa a `TOP_LAYER_MODIFICATION`, dopo strutture e alberi. Ciò che la feature calcola
     va in una cache thread-safe: i chunk nascono su più thread insieme;
   - **`SavedDataType` pretende un `DataFixTypes` non nullo** in 26.2 (`readTagFromDisk` lo
     chiama senza controllare): per un dato di mondo si usa un attachment persistente sul
     `ServerLevel` (`level.getAttachedOrElse` / `setAttached`), come per i giocatori;
   - `Entity.remove(RemovalReason)` è sovrascrivibile, e `reason.shouldDestroy()` distingue
     "sparito davvero" da "chunk scaricato". `ServerLevel.isPositionEntityTicking(pos)` dice se
     in quel punto le entità battono i tick: solo lì `getEntity(uuid) == null` vuol dire assente;
   - **quello che una schermata di Arise disegna in `content(...)` finisce sotto i widget**: il
     corpo si dipinge dentro `extractBackground`, quindi un `Button` messo sopra qualcosa di
     disegnato la copre — il suo sfondo grigio non e' trasparente. Se una cosa dev'essere vista
     *e* cliccata si disegna, e il click si risolve con l'aritmetica in `mouseClicked`, come le
     righe di `ListPanel`. Le pastiglie del colore delle ombre sono state otto quadrati grigi
     identici per due blocchi interi;
   - **le entita' evocate non seguono chi cambia dimensione**, e restano dove sono: il chunk si
     scarica, `getEntityInAnyDimension` risponde "non c'e'", l'elenco delle evocazioni le dimentica
     e alla prossima chiamata ne nascono di nuove — mentre le vecchie aspettano sul disco. Serve un
     aggancio a `ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL` (in 26.2 il nome ha
     `Level`, non `World`) e un'entita' che si tolga di mezzo da sola quando non risulta piu'
     evocata;
   - `Screen.mouseDragged(MouseButtonEvent, double, double)`: lo spostamento conviene ricavarlo da
     `event.x()/y()` rispetto all'ultima posizione, senza fidarsi dei due `double`;
   - **le entita' di un chunk in cui non c'e' nessuno non esistono, per chi le cerca**:
     `getEntitiesOfClass` su una regione senza giocatori risponde sempre "vuoto", anche se quelle
     entita' sono salvate su disco. Il criterio di questa mod — *guarda il mondo invece di tenere un
     registro* — vale per i blocchi, che `getBlockState` carica, ma **non** per le entita': quelle si
     controllano solo con qualcuno li' dentro. Ripopolare in base a quella risposta duplica, e in
     silenzio: la Sala del Risveglio si e' presa un Araldo in piu' a ogni avvio del server finche' il
     controllo non e' stato spostato a un secondo dopo l'arrivo del giocatore;
   - **un testo che nomina un tasto non deve nominare una lettera**: `Component.keybind("key.arise.status")`
     viene risolto dal client col tasto che quel giocatore ha davvero configurato. Vale per i
     suggerimenti dei sistemi e per il discorso dell'Araldo;
   - **titolo a schermo e barra d'azione non hanno un metodo**: `ServerPlayer` non ha ne'
     `displayClientMessage` ne' `showTitle`, si mandano i pacchetti (`ClientboundSetTitleTextPacket`,
     `ClientboundSetSubtitleTextPacket`, `ClientboundSetTitlesAnimationPacket`,
     `ClientboundSetActionBarTextPacket`). In Arise passano tutti da `fx/Overlay`, come i
     particellari passano da `AriseFx`;
   - **`optionalFieldOf(nome, default)` non scrive il campo** quando il valore e' quello di default:
     una sezione di config nuova resta invisibile nel file, quindi non modificabile da chi non legge
     il codice. Qui si usa `fieldOf`, e la tolleranza ai file vecchi la da' `AriseConfig.withDefaults`;
   - **i package delle entita' sono stati rimescolati in 26.2**: `Zombie` sta in
     `world/entity/monster/zombie/`, `Spider` in `monster/spider/`, `AbstractSkeleton` in
     `monster/skeleton/`, `Evoker` in `monster/illager/`, `Wolf` in `animal/wolf/`, `IronGolem` in
     `animal/golem/`. Prima di scrivere un `instanceof` su una classe di vanilla, cercarla:
     `unzip -l ~/.gradle/caches/fabric-loom/26.2/minecraft-common.jar | grep NomeClasse`, e per le
     firme `javap -cp minecraft-common.jar net.minecraft....`. Sono due secondi e tolgono ogni
     dubbio;
   - **gli attachment sincronizzati non tornano da soli al client**: il client li tiene sull'entita',
     e morire o cambiare dimensione ne costruisce una nuova, che nasce vuota. Il server li rimanda
     solo quando qualcuno li riscrive, quindi un dato che cambia di rado (gli incarichi) puo' non
     tornare mai — e una schermata che dipende da quello resta vuota per sempre. `setAttached` non
     confronta col valore vecchio, quindi **riscrivere lo stesso valore basta**: vedi
     `ModAttachments.resync`, agganciata a `JOIN`, `AFTER_RESPAWN` e `AFTER_PLAYER_CHANGE_LEVEL`;
   - **`ResourceKey.location()` non esiste piu'**: e' `identifier()`. Il nome di una dimensione si
     legge con `level.dimension().identifier()`;
   - **`ChatFormatting` non espone piu' il suo colore**: niente `getColor()`, niente `isFormat()`.
     Un enum che deve anche riempire dei pixel tiene la coppia — la formattazione e un ARGB scritto
     a mano — come fanno `Rank` e `ShadowGrade`;
   - **`Entity` non ha `hasLineOfSight`**: sta su `LivingEntity` (e un `Mob` ha anche
     `getSensing().hasLineOfSight(Entity)`, che e' quello che usano i goal perche' e' cachato);
   - **classificare un mob senza averne uno vivo**: `EntityType` espone `getWidth()`, `getHeight()`
     e `getCategory()`, quindi la forma si puo' chiedere al solo id. Quello che <em>non</em> si puo'
     chiedere e' se sa colpire da lontano: `RangedAttackMob` e' un'interfaccia, e le interfacce le
     ha solo un'istanza. Se una decisione va presa in entrambi i casi — con il cadavere e senza —
     la fonte di verita' deve essere un elenco in config, non l'`instanceof`;
   - **`Attributes.SCALE` esiste** ed e' il modo piu' economico di far vedere che due entita' dello
     stesso tipo sono cose diverse. Sta gia' in `createLivingAttributes`, come
     `KNOCKBACK_RESISTANCE`: si scrive con `getAttribute(...).setBaseValue(...)`, e conviene
     comunque passare da un metodo che tollera il `null`;
   - **le bandiere di un goal decidono chi zittisce chi**: un goal con `Flag.LOOK` a priorita' alta
     impedisce a `MeleeAttackGoal` (che vuole `MOVE` **e** `LOOK`) di partire. E' il modo giusto di
     ottenere un'entita' che spara invece di caricare — ma se quel goal resta attivo quando non ha
     niente da fare, l'entita' si pianta. La condizione che fa sparare e la condizione di `canUse`
     devono essere la stessa;
   - **`RecordCodecBuilder` si ferma a sedici campi anche nei record annidati**: `SpawnConfig` ci e'
     arrivata la seconda volta, e la via e' stata la stessa di `AriseConfig` — annidare per
     argomento (`SpawnConfig.Hazard` tiene cedimento e varco rosso) con le scorciatoie sulla radice,
     non spezzare a caso;
   - **gli attachment di Fabric valgono su qualunque entita'**, non solo sui giocatori:
     `ModAttachments.MOB_AFFIX` sta su un `Mob` ed e' persistente, che e' il modo giusto di dare
     uno stato a un mob senza un mixin e senza una mappa da tenere allineata a mano;
   - **`ServerLivingEntityEvents.ALLOW_DAMAGE`** dice si' o no e basta: non esiste un modo pulito di
     *ridurre* il danno. Le riduzioni parziali si fanno curando dopo, e si vedono — la barra scende
     e risale, lo schermo lampeggia lo stesso. Se un effetto difensivo deve essere leggibile, va
     progettato come rifiuto assoluto, non come sconto;
   - **i nomi dei file di suono vanilla non si indovinano**: `entity/generic/explode1` non esiste,
     `random/explode3` si'; `mob/warden/heartbeat` non esiste, `mob/warden/heartbeat_1` si'. Il
     collaudo statico li verifica contro l'indice degli asset, ed e' l'unico posto che se ne accorge
     prima del silenzio in gioco;
   - **il collaudo si tara sugli enum con il nome per primo**: `NOME("nome", ...)`. Un enum che
     mette il nome in seconda posizione non viene letto, e il collaudo lo dice — `MobAffix`,
     `NamedShadow` e `StatThreshold` sono nati con l'ordine sbagliato tutti e tre;
   - **le costanti dei tipi di entita' vanilla non stanno piu' su `EntityType`**: sono in
     **`EntityTypes`** (plurale), stesso sdoppiamento di `RenderType`/`RenderTypes`. E per chiedere
     se un tipo sta in un tag non c'e' `EntityType.is(tag)`: si passa da
     `type.builtInRegistryHolder().is(tag)`;
   - **advancement**: stanno in `data/<ns>/advancement/` (singolare), il criterio
     `minecraft:impossible` e' quello da usare per un traguardo che concede il codice, e la
     concessione e' `player.getAdvancements().award(holder, "nome_criterio")` con l'holder preso da
     `server.getAdvancements().get(id)`. Un advancement mancante deve restare silenzioso: e'
     decorazione sopra una cosa che funziona;
   - **`damageSources().magic()` sta nel tag `bypasses_armor`**. Un colpo ad area scritto con quello
     ignora l'armatura, e con lei la Resistenza: `generic()` per un danno che l'armatura deve
     attutire, `mobAttack(chi)` quando c'e' un colpevole;
   - **leggere l'altezza del terreno senza generarlo**: `level.getHeight(...)` pretende il chunk —
     venticinque campioni sparsi su mezzo chilometro erano tredici secondi di server fermo.
     `getGenerator().getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState())`
     non genera niente, **ma non e' economico**: costa circa **7 ms a colonna**, perche' costruisce
     una colonna di rumore intera con falde e interpolazione per restituire un numero. Va bene per
     piazzare una struttura ogni tanto; per campionare a migliaia (una mappa, un'anteprima) sono
     otto secondi ogni quattromila punti, cioe' il watchdog;
   - **campionare il terreno a migliaia**: si scende di un piano, a
     `level.getChunkSource().randomState().sampler().sample(quartX, quartY, quartZ)` — i sei rumori
     del clima in una chiamata, **29 µs**. Da li' il bioma si ottiene senza ricampionare con
     `((MultiNoiseBiomeSource) biomeSource).getNoiseBiome(targetPoint)`, la profondita' del mare
     dalla continentalita', e il rilievo dal **dislivello** della `depth()` fra campioni vicini —
     letta a quota fissa, perche' e' una funzione di quanto si e' sotto la superficie e a quota
     variabile darebbe lo stesso numero dappertutto. Il confronto misurato: 7867 ms contro 118 ms
     per lo stesso riquadro da 4096 campioni;
   - `ServerLevel.getSharedSpawnPos()` **non esiste piu'**: e' `getRespawnData().pos()`;
   - **`GuiGraphicsExtractor.blit(Identifier, ...)` a nove argomenti vuole gli angoli**, non la
     misura: `(id, x0, y0, x1, y1, u0, u1, v0, v1)`. Le altre sovrapposizioni ne vogliono quattro
     con larghezza e altezza, e scambiarle non da' nessun errore — da' una texture disegnata fin
     dove capita;
   - **`TextureManager.release(id)` chiude gia' la texture**. Chiuderla anche a mano libera due
     volte lo stesso puntatore nativo: non un'eccezione di Java, un processo che se ne va;
   - `NativeImage.setPixelABGR(x, y, abgr)` pretende il formato RGBA, e vuole i byte in
     quell'ordine — da un ARGB si scambiano rosso e blu;
   - **`Climate.TargetPoint` sono `long` decimillesimi**, non `float`: `continentalness()` a
     `-10000` e' mare aperto, e un dislivello vero di `depth()` fra due campioni vicini sta nelle
     centinaia;
3. **La compilazione non è una verifica.** Un sistema è "fatto" quando lo si è visto
   funzionare in `runClient`. Vale soprattutto per AI delle ombre, generazione dungeon e HUD.

   Fra "compila" e "ci ho giocato" ci sono però due reti che si stendono da sole, e vanno tirate
   **prima** di aprire il gioco, perché trovano in due secondi cose che a mano costerebbero un'ora:

   - **`python tools/collaudo.py`** — il collaudo statico. Chiavi di traduzione mancanti o con gli
     argomenti che non tornano, suoni che puntano a file inesistenti, modelli e loot table assenti,
     ricette che nominano oggetti che non esistono, incarichi che aspettano un obiettivo che
     nessuno fa avanzare, sistemi mai concessi, entità senza renderer, pacchetti senza
     destinatario. Nessuno di questi dà errore: danno testo grezzo, silenzio, cubi viola e partite
     bloccate. Esce con 1 se trova qualcosa.
   - **`.\gradlew.bat test`** — il banco di prova (`src/test`). Ci va la logica che è aritmetica
     pura: indici delle caselle, vigore di un'anima, curva dei livelli, resa del Banco, geometria
     del mercato, e soprattutto **i codec che scrivono su disco** — perché un codec sbagliato non
     dà un errore, dà un giocatore che riapre il mondo senza esercito.

   Quando si aggiunge un sistema si aggiungono anche le sue righe qui. E se una regola sta dentro
   un metodo che pretende un `ServerPlayer`, la si estrae in una funzione pura e si prova quella:
   `ShadowManager.callUpOrder` esiste separata da `callUp` solo per questo, e l'ordine di chiamata
   sbagliato e' esattamente il genere di difetto che non da' nessun errore. Il collaudo si tara da
   solo per le chiavi composte: se nasce un enum nuovo che compone chiavi, va aggiunto alla
   tabella `DYNAMIC` dentro `tools/collaudo.py`.

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
- [ ] **C5** — Città grandi e diverse: cinque piante stradali, cinque tavolozze, cinque forme di
      isolato e un monumento riconoscibile per città — *compilato, da verificare*
- [ ] **D1** — L'equipaggiamento è un oggetto: componente `arise:gear_piece` su item vanilla, armi
      e armature vere nelle caselle del gioco, menu del Cacciatore con spazio dimensionale, bottino
      che cade per terra, vincolo all'anima — *compilato, da verificare*
- [ ] **D2** — L'Officina delle Anime: le anime in esubero diventano operai, quattro macchinari
      (Richiamo, Crogiolo, Fucina, Pozzo), catalizzatori e tratti, ombre cadute con un minuto di
      recupero, `/arise arena` diventata laboratorio — *compilato, server verde, da verificare in
      gioco*
- [x] **E1** — La citta' viva: le citta' nascono all'avvio del server invece che alla prima
      entrata, e passano da 320 a 512 blocchi di lato — **verificato**: cinque mondi nuovi su
      server dedicato, nessun giocatore collegato, coda di cinque città nove secondi dopo l'avvio,
      ~4 minuti a città. È tutto comportamento di server: non c'è niente da guardare sul client
- [ ] **E2** — Il Quartiere del Mercato: nove botteghe sulla piazza, cinque mercanti con la
      finestra di scambio vanilla e quattro servizi, Moneta d'Anima coniata al Banco — *compilato,
      server verde, da verificare in gioco*
- [ ] **E3** — La Via dell'Artigiano: nove incarichi nuovi (18 in tutto) che aprono l'Officina un
      pezzo per volta, e quattro Progetti consumati dalle ricette dei macchinari — *compilato,
      server verde, da verificare in gioco*
- [ ] **M1** — Le città nel seme: feature di worldgen in due passate (`CityFeature`, `CityPlans`),
      zero costo all'avvio, il cantiere a budget resta per ricostruzione e mondi vecchi —
      *compilato, server verde su mondo nuovo, da verificare in gioco (viaggio in città)*
- [ ] **P1** — La prima ora: il colpo che non uccide porta nella **Sala del Risveglio**, l'Araldo
      spiega in sei pagine (una per clic), ogni sistema concesso dice anche **come si usa**, il
      contatore dell'incarico compare sopra la hotbar, e il primo ingresso nel mondo saluta —
      *compilato, server verde; Sala e Araldo verificati leggendo le region del mondo salvato; il
      risveglio e il dialogo restano da verificare in gioco*
- [ ] **P2** — Quello che il gioco non diceva: otto difetti trovati **giocando** e le loro
      correzioni — il primo regalo che non si poteva indossare, le pastiglie del colore invisibili,
      le ombre duplicate a ogni cambio di dimensione, nessuna uscita dal varco dopo il boss, le
      citta' irraggiungibili (Sigillo dell'Associazione), gli incarichi senza lore ne' istruzioni,
      le statistiche mute (tooltip + riga d'effetto), lo spazio dimensionale che rimbalzava i pezzi
      invece di indossarli — *compilato, 60 prove verdi, server pulito; da riverificare in gioco*
- [ ] **O1** — L'esercito che obbedisce: quattro **archetipi** (Guardia, Bestia, Mago, Colosso) con
      comportamenti veri — provocazione, lancia d'ombra, balzo, interposizione — otto **gradi** dal
      Normale al Gran Maresciallo con nome e aura di comando, la **squadra** che decide chi esce col
      tasto, due **ordini** puntati (Y uccidetelo, U restate qui) e il **dono del Monarca** che fa
      crescere l'esercito insieme al giocatore. Design §12 — *compilato, 69 prove verdi, collaudo
      pulito, server verde; da verificare in gioco*
- [ ] **M2** — La mappa del mondo (tasto **M**): città, varchi aperti, tu; trascina/zoom, frecce
      sul bordo per ciò che sta fuori. Indice dei varchi `GateRegistry` riconciliato; `/arise map`,
      `/arise gate list` — *compilato, da verificare in gioco*

- [ ] **G1** — Cinque cose trovate giocando, tutte e cinque chieste da Luca:
      **il Mana** (riserva dal livello, rigenerazione contata sul tempo e non sui battiti, prezzo per
      ogni evocazione e ogni abilità — prima chiamare l'esercito non costava niente e il tetto delle
      evocazioni non serviva a nulla), il **Volo del Monarca** al livello 10 (tasto <kbd>L</kbd>,
      otto MP al secondo, si cade quando finisce), la **Chiave del Varco** (l'incarico «chiudi un
      varco» era un'attesa, non un compito: adesso il varco lo apri tu, e la catena si riprende la
      chiave quando hai finito), i **passi degli incarichi** (ogni incarico dichiara cosa fare in
      due o tre righe numerate, in un riquadro sul bordo destro — <kbd>F7</kbd>: disteso, stretto,
      spento) e la **mappa vera**: terreno dipinto dal rumore del generatore senza toccare un chunk,
      118 ms a riquadro dopo che la prima versione ne costava 7867, e le cinque città su un anello
      invece che in fila. `/arise mana`, `/arise map bench` — *compilato, 115 prove verdi, collaudo
      pulito, server verde su mondo nuovo; **da verificare in gioco***

- [ ] **B-PRD** — la prima ondata del `PRD-arise.md`: **tredici blocchi e due meta' su diciassette** contro il
      silenzio dopo il diciottesimo incarico. **B0** le tre istruzioni che mentivano a chi rimappa,
      **B1** il tetto dell'estrazione reso raggiungibile e `shift+R` che guarda il cadavere senza
      consumarlo, **B10** la Pergamena del Rimpianto (respec), **B4** le sette ombre con un nome
      (Igris, Iron, Tank, Tusk, Greed, Beru, Bellion), **B2** il Dungeon Break, **B9** le dodici
      soglie delle statistiche, **B8** i sei affissi dei nemici, **B5** il Gate Rosso, **B6** i tre
      obiettivi del varco, **B7** il Sovrano a tre fasi, **B14** il Cubo dell'Abisso, **B11**
      l'Abisso a profondita' infinita, **B3** la Quest Giornaliera (la Zona di Penalita' e' stata tolta dopo la prova), piu'
      **dodici advancement** con i toast, i **tag**, `/arise config reload` e la meta' cerimoniale
      di **B12** — il rango che smette di cambiare di nascosto: titolo, suono, elenco delle caselle
      aperte, e al rango S un messaggio a tutti quelli collegati — *compilato, 96 prove verdi,
      collaudo pulito, server verde su mondo nuovo ×9; **quasi niente di tutto questo e' ancora
      stato visto girare in gioco***

      Restano fuori: **B13** i Monarchi, **B15** il Registro del Sistema, la meta' aperta di **B16**
      (interruttori per sistema, menu radiale) e l'esame vero di **B12** (Esaminatore, arena,
      ondate). La consegna sta in `docs/blocco-prd-prima-ondata.html`.

- [x] **`/arise doctor`** — la scheda del Cacciatore in undici righe: versione, livello e rango,
      dimensione e coordinate, punto della catena, sblocchi concessi e chiusi, esercito ed evocate,
      giornaliera, varchi in piedi, percorso di `arise.json`, piu' tre righe che distinguono uno
      stato lecito da un difetto. Senza `requires`: non cambia niente, e su un server chi ha un
      problema di solito non e' chi ha i permessi — **verificato** da console su mondo nuovo

- [x] **Il primo collaudo vero** — tre cose trovate giocando, e una tolta: **l'HUD spariva e non
      tornava** (il client tiene gli attachment sull'entita', e morire o cambiare dimensione ne
      costruisce una nuova che nasce vuota → `ModAttachments.resync` su join, respawn e cambio
      dimensione, piu' il tasto <kbd>'</kbd> per nasconderlo e rimetterlo a mano), e la **Zona di
      Penalita' e' stata rimossa** — una penalita' che sposta e' un'interruzione, e l'interruzione
      arriva sempre nel momento sbagliato

Aggiorna questa lista quando una fase è **verificata in gioco**, non quando compila.

---

## Dove sta scritto cosa

| File | Cosa contiene |
|---|---|
| `DESIGN-solo-leveling.md` | **cosa è** Arise: i dodici sistemi, in dettaglio. Resta valido |
| `PRD-arise.md` | **cosa le manca**: dodici lacune con l'evidenza nel codice, sette principi, sedici blocchi ordinati per valore diviso costo, e uno scope negativo scritto |
| `GUIDA-TESTER.html` | la guida per chi collauda, nella root. Scritta leggendo il codice: se la guida e il gioco non dicono la stessa cosa, uno dei due ha un difetto |
| `REPORT-modding-con-claude.md` | il contesto sull'ecosistema |
| `docs/blocco-*.html` | la consegna di ogni blocco, uno per file |
