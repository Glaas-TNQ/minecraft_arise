package com.luca.arise.workshop;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.WorkshopConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.Rank;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.registry.ModBlocks;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.WorldlyContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BaseContainerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/**
 * Il cervello di tutti e quattro i macchinari dell'Officina.
 *
 * <p>Una sola {@code BlockEntity} con uno {@code switch} sul {@link MachineKind}, e non quattro
 * classi. La ragione e' che il 90% del lavoro e' identico — caselle, salvataggio, menu, contatore,
 * automazione con le tramogge — e l'unica cosa che cambia davvero e' cosa succede quando il
 * contatore arriva in fondo. Spalmare quel 90% su quattro classi avrebbe voluto dire quattro posti
 * in cui aggiustare lo stesso bug.
 *
 * <p><strong>Il vigore installato e' l'unica leva.</strong> Non c'e' una barra dell'energia, non
 * c'e' un carburante: le anime dentro la macchina <em>sono</em> il carburante, tranne che non
 * bruciano. Piu' vigore, meno tick per un giro di lavoro; il pavimento di
 * {@link WorkshopConfig#minSpeedFactor()} impedisce che un'officina avanzata produca un oggetto al
 * tick e trasformi il server in un frullatore.
 */
public class MachineBlockEntity extends BaseContainerBlockEntity
		implements WorldlyContainer, ExtendedMenuProvider<MachineKind> {

	/** Indici dei due numeri che il menu manda al client per disegnare la barra. */
	public static final int DATA_PROGRESS = 0;
	public static final int DATA_DURATION = 1;
	public static final int DATA_COUNT = 2;

	/**
	 * Le anime che il Richiamo puo' materializzare, dalla piu' scarsa alla piu' pregiata.
	 *
	 * <p>Vita e danno sono quelli veri dei mob, cosi' un'anima tirata dal Richiamo e la stessa
	 * anima estratta da un cadavere dicono lo stesso rango. Se qui ci fosse un punteggio inventato,
	 * il Richiamo produrrebbe anime che a guardarle non tornano.
	 *
	 * <p>Sono valori scritti a mano e non letti dal registro di proposito: il Richiamo non deve
	 * poter pescare un'anima di gallina, ne' un'anima di Ender Dragon perche' una mod l'ha resa
	 * uccidibile. Questa lista e' il perimetro di cio' che l'Abisso concede.
	 */
	private record LureEntry(String mob, double health, double damage) {

		double power() {
			return health + damage * 4.0;
		}
	}

	private static final List<LureEntry> LURE_POOL = List.of(
			new LureEntry("spider", 16.0, 2.0),
			new LureEntry("zombie", 20.0, 3.0),
			new LureEntry("skeleton", 20.0, 3.5),
			new LureEntry("husk", 20.0, 5.0),
			new LureEntry("witch", 26.0, 5.0),
			new LureEntry("pillager", 24.0, 7.0),
			new LureEntry("wither_skeleton", 20.0, 11.0),
			new LureEntry("enderman", 40.0, 9.0),
			new LureEntry("blaze", 20.0, 17.0),
			new LureEntry("evoker", 24.0, 20.0),
			new LureEntry("elder_guardian", 80.0, 12.5),
			new LureEntry("ravager", 100.0, 17.0),
			new LureEntry("iron_golem", 100.0, 22.5),
			new LureEntry("warden", 300.0, 30.0));

	private final MachineKind kind;
	private final NonNullList<ItemStack> items;

	/** A che punto e' il giro di lavoro in corso. Zero quando la macchina e' ferma. */
	private int progress;

	/** Quanto dura questo giro. Si calcola all'inizio e non cambia piu' fino alla fine. */
	private int duration;

	/** Chi ha piazzato la macchina: il Pozzo dell'Abisso paga lui. */
	private UUID owner;

	/**
	 * L'ultima ricetta trovata per la Fucina, e per quale oggetto.
	 *
	 * <p>Senza questa memoria la Fucina interrogherebbe il ricettario <em>a ogni tick</em>, solo
	 * per riscoprire che il ferro grezzo si fonde ancora. Una macchina sola non se ne accorge;
	 * quaranta in fila su un server sono venti interrogazioni al secondo l'una. Non e' persistente:
	 * si ricostruisce al primo tick dopo un caricamento.
	 */
	private Item cachedInput;
	private ItemStack cachedResult = ItemStack.EMPTY;

	/**
	 * I due numeri che il client deve vedere per disegnare la barra di avanzamento.
	 *
	 * <p>Passano da {@code ContainerData} e non da un pacchetto nostro perche' e' l'unico canale
	 * che il gioco aggiorna da solo, ogni tick, solo per chi ha il menu aperto. Un pacchetto
	 * scritto a mano sarebbe traffico in piu' per informazione che nessuno guarda a finestra
	 * chiusa.
	 */
	private final ContainerData data = new ContainerData() {

		@Override
		public int get(int index) {
			return switch (index) {
				case DATA_PROGRESS -> progress;
				case DATA_DURATION -> duration;
				default -> 0;
			};
		}

		@Override
		public void set(int index, int value) {
			switch (index) {
				case DATA_PROGRESS -> progress = value;
				case DATA_DURATION -> duration = value;
				default -> {
				}
			}
		}

		@Override
		public int getCount() {
			return DATA_COUNT;
		}
	};

	public MachineBlockEntity(BlockPos pos, BlockState state) {
		super(ModBlocks.MACHINE, pos, state);

		this.kind = state.getBlock() instanceof MachineBlock machine
				? machine.kind()
				: MachineKind.LURE;
		this.items = NonNullList.withSize(kind.containerSize(), ItemStack.EMPTY);
	}

	public MachineKind kind() {
		return kind;
	}

	public void setOwner(UUID owner) {
		this.owner = owner;
		setChanged();
	}

	// ---------------------------------------------------------------- contenitore

	@Override
	public int getContainerSize() {
		return items.size();
	}

	@Override
	protected NonNullList<ItemStack> getItems() {
		return items;
	}

	@Override
	protected void setItems(NonNullList<ItemStack> replacement) {
		for (int i = 0; i < items.size(); i++) {
			items.set(i, i < replacement.size() ? replacement.get(i) : ItemStack.EMPTY);
		}
	}

	@Override
	protected Component getDefaultName() {
		return kind.label();
	}

	@Override
	public boolean canPlaceItem(int index, ItemStack stack) {
		if (kind.isOutputSlot(index)) {
			return false;
		}

		if (kind.isSoulSlot(index)) {
			return SoulItems.isSoul(stack);
		}

		if (index == kind.catalystSlot()) {
			return SoulItems.isCatalyst(stack);
		}

		// Le caselle d'ingresso della Fucina accettano qualunque cosa: e' il tick a scoprire se
		// esiste una ricetta, non la casella. Filtrare qui vorrebbe dire interrogare il gestore
		// delle ricette a ogni click del mouse.
		return true;
	}

	// ---------------------------------------------------------------- tramogge

	/**
	 * Da dove si carica e da dove si scarica.
	 *
	 * <p>Sopra si mette da lavorare, sotto si prende il prodotto, di lato si mettono le anime. E'
	 * la stessa convenzione della fornace, che e' esattamente il punto: chi ha automatizzato una
	 * fornace ha gia' automatizzato la Fucina d'Ombra senza doverlo imparare.
	 */
	@Override
	public int[] getSlotsForFace(Direction side) {
		return switch (side) {
			case DOWN -> range(kind.firstOutput(), kind.containerSize());
			case UP -> kind.inputs() > 0
					? range(kind.firstInput(), kind.firstInput() + kind.inputs())
					: range(kind.firstSoul(), kind.souls());
			default -> withCatalyst(range(kind.firstSoul(), kind.souls()));
		};
	}

	@Override
	public boolean canPlaceItemThroughFace(int index, ItemStack stack, Direction side) {
		return canPlaceItem(index, stack);
	}

	/**
	 * Si porta via solo dalle uscite.
	 *
	 * <p>Il divieto sulle caselle delle anime non e' una dimenticanza: un'anima installata e'
	 * un'operaia, e una tramoggia che se la porta via mentre lavora e' il modo piu' rapido per
	 * svuotare un'officina senza accorgersene.
	 */
	@Override
	public boolean canTakeItemThroughFace(int index, ItemStack stack, Direction side) {
		return kind.isOutputSlot(index);
	}

	private static int[] range(int from, int toExclusive) {
		int[] result = new int[Math.max(0, toExclusive - from)];
		for (int i = 0; i < result.length; i++) {
			result[i] = from + i;
		}
		return result;
	}

	private int[] withCatalyst(int[] base) {
		if (kind.catalystSlot() < 0) {
			return base;
		}

		int[] result = new int[base.length + 1];
		System.arraycopy(base, 0, result, 0, base.length);
		result[base.length] = kind.catalystSlot();
		return result;
	}

	// ---------------------------------------------------------------- salvataggio

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);

		ContainerHelper.saveAllItems(output, items);
		output.putInt("progress", progress);
		output.putInt("duration", duration);

		if (owner != null) {
			output.store("owner", UUIDUtil.CODEC, owner);
		}
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);

		items.clear();
		for (int i = 0; i < kind.containerSize(); i++) {
			items.add(ItemStack.EMPTY);
		}

		ContainerHelper.loadAllItems(input, items);
		progress = input.getIntOr("progress", 0);
		duration = input.getIntOr("duration", 0);
		owner = input.read("owner", UUIDUtil.CODEC).orElse(null);
	}

	// ---------------------------------------------------------------- menu

	@Override
	protected AbstractContainerMenu createMenu(int containerId, Inventory inventory) {
		return new MachineMenu(containerId, inventory, kind, this, data);
	}

	@Override
	public MachineKind getScreenOpeningData(ServerPlayer player) {
		return kind;
	}

	// ---------------------------------------------------------------- il lavoro

	/**
	 * Un tick di macchina.
	 *
	 * <p>Il contatore riparte da zero appena le condizioni cadono, e non si mette in pausa. E' una
	 * scelta: mettere in pausa vorrebbe dire che togliere un'anima a meta' lavoro non costa
	 * niente, e allora conviene sempre spostare le anime avanti e indietro fra le macchine invece
	 * di sceglierne una.
	 */
	public static void serverTick(Level level, BlockPos pos, BlockState state,
			MachineBlockEntity machine) {
		WorkshopConfig config = AriseConfig.get().workshop();

		if (!config.enabled() || !(level instanceof ServerLevel server)) {
			return;
		}

		if (!machine.canWork(server)) {
			if (machine.progress != 0 || machine.duration != 0) {
				machine.progress = 0;
				machine.duration = 0;
				machine.setChanged();
			}
			return;
		}

		if (machine.duration <= 0) {
			machine.duration = machine.computeDuration(config);
		}

		machine.progress++;

		// Il fumo a meta' corsa: e' l'unica cosa che dice da fuori che la macchina sta lavorando,
		// senza dover aprire la finestra. Vale la pena spendere un pacchetto ogni venti tick.
		if (machine.progress % 20 == 0) {
			AriseFx.machineWorking(server, machine.centre(), machine.tint());
		}

		if (machine.progress >= machine.duration) {
			machine.complete(server, config);
			machine.progress = 0;
			machine.duration = 0;
		}

		machine.setChanged();
	}

	private Vec3 centre() {
		return Vec3.atCenterOf(worldPosition);
	}

	/** Il colore degli effetti: quello del rango dell'anima migliore installata. */
	private int tint() {
		Rank best = Rank.E;

		for (LooseSoul soul : installedSouls()) {
			Rank rank = SoulItems.rankOf(soul);
			if (rank.ordinal() > best.ordinal()) {
				best = rank;
			}
		}

		return best.color() & 0xFFFFFF;
	}

	// ---------------------------------------------------------------- le anime installate

	public List<LooseSoul> installedSouls() {
		List<LooseSoul> souls = new ArrayList<>(kind.souls());

		for (int i = 0; i < kind.souls(); i++) {
			LooseSoul soul = SoulItems.soul(items.get(i));
			if (soul != null) {
				souls.add(soul);
			}
		}

		return souls;
	}

	/** Il vigore totale: e' questo, e nient'altro, a decidere quanto va veloce la macchina. */
	public double installedVigor() {
		double total = 0.0;
		for (LooseSoul soul : installedSouls()) {
			total += soul.vigor();
		}
		return total;
	}

	private boolean hasTrait(SoulTrait trait) {
		for (LooseSoul soul : installedSouls()) {
			if (soul.has(trait)) {
				return true;
			}
		}
		return false;
	}

	private int computeDuration(WorkshopConfig config) {
		int base = switch (kind) {
			case LURE -> config.lureIntervalTicks();
			case CRUCIBLE -> config.crucibleIntervalTicks();
			case FORGE -> config.forgeIntervalTicks();
			case WELL -> config.wellIntervalTicks();
		};

		return config.workTicks(base, installedVigor(), hasTrait(SoulTrait.ARDORE));
	}

	// ---------------------------------------------------------------- condizioni

	private boolean canWork(ServerLevel level) {
		return switch (kind) {
			case LURE -> hasAnySoul() && hasFreeOutput();
			case CRUCIBLE -> fusionReady();
			case FORGE -> hasAnySoul() && forgeResult(level) != null;
			case WELL -> hasAnySoul() && owner != null;
		};
	}

	/** C'e' almeno un'operaia? Senza allocare la lista: questa domanda si fa a ogni tick. */
	private boolean hasAnySoul() {
		for (int i = 0; i < kind.souls(); i++) {
			if (SoulItems.isSoul(items.get(i))) {
				return true;
			}
		}

		return false;
	}

	private boolean hasFreeOutput() {
		for (int i = kind.firstOutput(); i < kind.containerSize(); i++) {
			if (items.get(i).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	/** Il Crogiolo vuole tutte le caselle piene, il catalizzatore e l'uscita libera. */
	private boolean fusionReady() {
		for (int i = 0; i < kind.souls(); i++) {
			if (!SoulItems.isSoul(items.get(i))) {
				return false;
			}
		}

		return SoulItems.isCatalyst(items.get(kind.catalystSlot()))
				&& items.get(kind.firstOutput()).isEmpty();
	}

	/**
	 * Cosa uscirebbe dalla Fucina, o {@code null} se non uscirebbe niente.
	 *
	 * <p>Passa dal ricettario vero delle fusioni invece che da una tabella nostra: cosi' qualunque
	 * cosa un datapack o un'altra mod renda fondibile diventa lavorabile qui senza toccare una
	 * riga di codice. Il carburante non serve — le anime lo sostituiscono, ed e' tutto il punto
	 * della macchina.
	 */
	private ItemStack forgeResult(ServerLevel level) {
		ItemStack input = items.get(kind.firstInput());
		if (input.isEmpty()) {
			return null;
		}

		if (input.getItem() != cachedInput) {
			SingleRecipeInput recipeInput = new SingleRecipeInput(input);

			cachedInput = input.getItem();
			cachedResult = level.recipeAccess()
					.getRecipeFor(RecipeType.SMELTING, recipeInput, level)
					.map(holder -> holder.value().assemble(recipeInput))
					.orElse(ItemStack.EMPTY);
		}

		if (cachedResult.isEmpty() || !fitsInOutput(cachedResult)) {
			return null;
		}

		// Una copia, sempre: chi la riceve la fa crescere e la infila in una casella, e il
		// risultato memorizzato deve restare quello che era.
		return cachedResult.copy();
	}

	private boolean fitsInOutput(ItemStack result) {
		ItemStack out = items.get(kind.firstOutput());

		if (out.isEmpty()) {
			return true;
		}

		return ItemStack.isSameItemSameComponents(out, result)
				&& out.getCount() + result.getCount() <= out.getMaxStackSize();
	}

	// ---------------------------------------------------------------- il giro finito

	private void complete(ServerLevel level, WorkshopConfig config) {
		switch (kind) {
			case LURE -> completeLure(level, config);
			case CRUCIBLE -> completeCrucible(level, config);
			case FORGE -> completeForge(level, config);
			case WELL -> completeWell(level, config);
		}

		advance(level, Objective.MACHINE_WORK, 1);
		AriseFx.machineDone(level, centre(), tint());
	}

	/**
	 * Segnala al proprietario che la sua macchina ha fatto qualcosa.
	 *
	 * <p>Al proprietario e non a chi passa: un macchinario lavora da solo, e un incarico che
	 * avanzasse per chi si trova a passare davanti sarebbe un incarico che si completa per caso.
	 * Se il proprietario non e' collegato non succede niente, ed e' giusto — la catena degli
	 * incarichi si segue giocando, non lasciando il server acceso.
	 */
	private void advance(ServerLevel level, Objective objective, int amount) {
		if (owner == null || amount <= 0) {
			return;
		}

		ServerPlayer player = level.getServer().getPlayerList().getPlayer(owner);

		if (player != null) {
			QuestManager.advance(player, objective, amount);
		}
	}

	/**
	 * Il Richiamo materializza un'anima.
	 *
	 * <p>Quale anima lo decide il vigore installato: piu' forti sono le operaie, piu' in alto si
	 * puo' pescare nel {@link #LURE_POOL}. E' l'unico modo che il sistema ha di ripagare chi ci
	 * mette dentro le anime buone invece di parcheggiarci le peggiori.
	 */
	private void completeLure(ServerLevel level, WorkshopConfig config) {
		RandomSource random = level.getRandom();
		double reach = 20.0 + installedVigor() * config.lurePowerPerVigor();
		reach *= 0.85 + random.nextDouble() * 0.3;

		int best = 0;
		for (int i = 0; i < LURE_POOL.size(); i++) {
			if (LURE_POOL.get(i).power() <= reach) {
				best = i;
			}
		}

		// Non sempre il massimo: un tiro fra i tre gradini piu' alti raggiunti, cosi' l'esito
		// resta interessante anche quando il vigore installato non cambia da un'ora.
		LureEntry entry = LURE_POOL.get(Math.max(0, best - random.nextInt(3)));
		Identifier type = Identifier.withDefaultNamespace(entry.mob());

		if (hasTrait(SoulTrait.TENACIA) && random.nextDouble() < config.traitPower(SoulTrait.TENACIA)) {
			// La Tenacia raddoppia, ma la seconda anima e' un'altra anima e non una copia: ognuna
			// nasce col suo UUID, altrimenti due anime identiche si impilerebbero e sparirebbero.
			pushToOutput(SoulItems.stack(LooseSoul.of(type, entry.health(), entry.damage())));
		}

		pushToOutput(SoulItems.stack(LooseSoul.of(type, entry.health(), entry.damage())));
	}

	/**
	 * La fusione: quattro anime piu' un catalizzatore diventano un'anima sola.
	 *
	 * <p>L'anima che esce <em>e'</em> la piu' forte delle quattro — stesso mob d'origine, stessa
	 * potenza congelata — cresciuta di livello e con un tratto in piu'. Non e' un'anima nuova, e
	 * conta: significa che si puo' scegliere quale delle proprie anime far diventare grande,
	 * invece di subire quale esce.
	 */
	private void completeCrucible(ServerLevel level, WorkshopConfig config) {
		Catalyst catalyst = SoulItems.catalystOf(items.get(kind.catalystSlot()));
		List<LooseSoul> souls = installedSouls();

		if (catalyst == null || souls.isEmpty()) {
			return;
		}

		LooseSoul strongest = souls.get(0);
		int levelSum = 0;
		Set<SoulTrait> inherited = new LinkedHashSet<>();
		boolean resonance = false;

		for (LooseSoul soul : souls) {
			levelSum += soul.level();
			inherited.addAll(soul.traits());
			resonance |= soul.has(SoulTrait.RISONANZA);

			if (soul.vigor() > strongest.vigor()) {
				strongest = soul;
			}
		}

		int capacity = catalyst.traitCapacity();
		int newLevel = Math.max(strongest.level() + 1,
				(int) Math.round(levelSum * catalyst.levelYield()));

		LooseSoul result = strongest.withLevel(newLevel);

		// Prima i tratti che c'erano gia': fondere non deve mai far perdere quello che si aveva.
		for (SoulTrait trait : inherited) {
			result = result.with(trait, capacity);
		}

		// Poi, se c'e' ancora posto, uno nuovo tirato a sorte fra quelli che mancano.
		result = result.with(rollTrait(level.getRandom(), result), capacity);

		for (int i = 0; i < kind.souls(); i++) {
			items.set(i, ItemStack.EMPTY);
		}

		boolean spared = resonance && level.getRandom().nextDouble()
				< config.traitPower(SoulTrait.RISONANZA);
		if (!spared) {
			items.get(kind.catalystSlot()).shrink(1);
		}

		items.set(kind.firstOutput(), SoulItems.stack(result));
		advance(level, Objective.FUSE_SOUL, 1);
		AriseFx.soulFused(level, centre(), SoulItems.rankOf(result));
	}

	private static SoulTrait rollTrait(RandomSource random, LooseSoul soul) {
		List<SoulTrait> missing = new ArrayList<>();

		for (SoulTrait trait : SoulTrait.values()) {
			if (!soul.has(trait)) {
				missing.add(trait);
			}
		}

		return missing.isEmpty()
				? SoulTrait.ARDORE
				: missing.get(random.nextInt(missing.size()));
	}

	private void completeForge(ServerLevel level, WorkshopConfig config) {
		ItemStack result = forgeResult(level);
		if (result == null) {
			return;
		}

		items.get(kind.firstInput()).shrink(1);

		if (hasTrait(SoulTrait.TENACIA)
				&& level.getRandom().nextDouble() < config.traitPower(SoulTrait.TENACIA)) {
			result.grow(result.getCount());
		}

		advance(level, Objective.FORGE_SMELT, result.getCount());
		mergeIntoOutput(result);
	}

	/**
	 * Il Pozzo munge le anime: soul coin al proprietario, ogni tanto un catalizzatore.
	 *
	 * <p>Paga solo se il proprietario e' collegato. Accumulare la resa per chi non c'e' vorrebbe
	 * dire tenere un registro persistente per macchina, e la prima cosa che farebbe un giocatore
	 * sarebbe costruirne quaranta e tornare dopo una settimana.
	 */
	private void completeWell(ServerLevel level, WorkshopConfig config) {
		ServerPlayer player = owner == null ? null : level.getServer().getPlayerList().getPlayer(owner);
		if (player == null) {
			return;
		}

		double coins = installedVigor() * config.wellCoinsPerVigor();
		if (hasTrait(SoulTrait.AVIDITA)) {
			coins *= 1.0 + config.traitPower(SoulTrait.AVIDITA);
		}

		long paid = Math.max(1L, Math.round(coins));
		ProgressManager.addSouls(player, paid);
		QuestManager.advance(player, Objective.WELL_YIELD, (int) Math.min(Integer.MAX_VALUE, paid));

		if (level.getRandom().nextDouble() < config.wellCatalystChance()) {
			mergeIntoOutput(SoulItems.catalyst(catalystGrade(), 1));
		}
	}

	/** Il grado del catalizzatore che il Pozzo lascia: quello dell'anima migliore installata. */
	private Rank catalystGrade() {
		Rank best = Rank.E;

		for (LooseSoul soul : installedSouls()) {
			Rank rank = SoulItems.rankOf(soul);
			if (rank.ordinal() > best.ordinal()) {
				best = rank;
			}
		}

		return best;
	}

	// ---------------------------------------------------------------- uscite

	/** Mette lo stack nella prima casella d'uscita libera. Se non ce n'e', lo lascia cadere. */
	private void pushToOutput(ItemStack stack) {
		for (int i = kind.firstOutput(); i < kind.containerSize(); i++) {
			if (items.get(i).isEmpty()) {
				items.set(i, stack);
				return;
			}
		}

		dropOutside(stack);
	}

	/** Come sopra, ma somma agli stack compatibili: serve alle uscite impilabili. */
	private void mergeIntoOutput(ItemStack stack) {
		for (int i = kind.firstOutput(); i < kind.containerSize(); i++) {
			ItemStack out = items.get(i);

			if (out.isEmpty()) {
				items.set(i, stack);
				return;
			}

			if (ItemStack.isSameItemSameComponents(out, stack)
					&& out.getCount() + stack.getCount() <= out.getMaxStackSize()) {
				out.grow(stack.getCount());
				return;
			}
		}

		dropOutside(stack);
	}

	private void dropOutside(ItemStack stack) {
		if (level instanceof ServerLevel server) {
			net.minecraft.world.Containers.dropItemStack(server, worldPosition.getX() + 0.5,
					worldPosition.getY() + 1.1, worldPosition.getZ() + 0.5, stack);
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return net.minecraft.world.Container.stillValidBlockEntity(this, player);
	}
}
