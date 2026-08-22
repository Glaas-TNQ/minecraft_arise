package com.luca.arise.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GearConfig;
import com.luca.arise.gem.Gem;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.Rank;
import com.luca.arise.progress.Stat;
import com.luca.arise.progress.StatSources;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * L'equipaggiamento del Cacciatore, lato server.
 *
 * <p>Un pezzo indossato adesso e' un oggetto in una casella, e le caselle sono di due famiglie
 * (vedi {@link GearSlot}): quelle di vanilla, che il gioco gia' gestisce da solo, e le nostre.
 * Quindi qui non c'e' piu' nessun metodo per indossare o togliere: indossare un pezzo e'
 * trascinarlo in una casella, e a quel punto conta. Restano tre lavori.
 *
 * <ul>
 *   <li><strong>Contare</strong>: leggere cosa il giocatore ha addosso e trasformarlo in
 *       statistiche, che e' il solo motivo per cui il Sistema si accorge di un anello.
 *   <li><strong>Consegnare</strong>: infilare un pezzo appena trovato nello spazio dimensionale.
 *   <li><strong>Far rispettare i ranghi</strong>: se il rango scende, le posizioni che non
 *       reggono piu' si svuotano.
 * </ul>
 *
 * <p>Ogni modifica finisce con un {@link ProgressManager#applyAttributes}: e' l'unico punto in cui
 * le statistiche diventano attributi, e passa dalla somma delle sorgenti registrate in
 * {@link StatSources}. In particolare le statistiche dei pezzi <em>non</em> viaggiano come
 * modificatori vanilla sull'oggetto: si conterebbero due volte e la regola 6 di CLAUDE.md
 * salterebbe.
 */
public final class GearManager {

	/** Le caselle di vanilla in cui un nostro pezzo puo' finire. */
	private static final List<EquipmentSlot> VANILLA_SLOTS = List.of(
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET, EquipmentSlot.MAINHAND);

	private GearManager() {
	}

	/** Registra l'equipaggiamento come sorgente di statistiche. Chiamato dall'entrypoint. */
	public static void init() {
		StatSources.register(GearManager::contribute);
	}

	public static PlayerGear get(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.GEAR);
	}

	private static void set(ServerPlayer player, PlayerGear gear) {
		player.setAttached(ModAttachments.GEAR, gear);
		ProgressManager.applyAttributes(player);
	}

	// ---------------------------------------------------------------- rango del Cacciatore

	/**
	 * Il rango del giocatore, che decide quali caselle sono aperte.
	 *
	 * <p>Si ricava dal livello invece di essere un dato a parte: un secondo valore da tenere in
	 * sincronia con il primo e' un secondo valore che prima o poi ci va in disaccordo. Stessa
	 * scelta gia' fatta per il rango delle ombre.
	 */
	public static Rank hunterRank(ServerPlayer player) {
		PlayerProgress progress = player.getAttachedOrCreate(ModAttachments.PROGRESS);
		return AriseConfig.get().hunterRank(progress.level());
	}

	// ---------------------------------------------------------------- consegna

	/**
	 * Consegna un pezzo appena trovato.
	 *
	 * <p>Prima lo spazio dimensionale, che e' il suo posto; poi l'inventario normale, perche' un
	 * pezzo che non entra da nessuna parte non deve sparire; per terra come ultima spiaggia. Non
	 * fallisce mai in silenzio.
	 */
	public static Component grant(ServerPlayer player, GearPiece piece) {
		ItemStack stack = GearItems.stack(piece);

		if (store(player, stack)) {
			return Component.translatable("arise.msg.gear.granted", piece.displayName());
		}

		if (player.addItem(stack)) {
			return Component.translatable("arise.msg.gear.granted_pack", piece.displayName());
		}

		player.drop(stack, false);
		return Component.translatable("arise.msg.gear.granted_ground", piece.displayName());
	}

	/** Mette un oggetto nella prima casella libera dello spazio dimensionale. */
	public static boolean store(ServerPlayer player, ItemStack stack) {
		PlayerGear gear = get(player);
		List<ItemStack> space = new ArrayList<>(gear.dimensional());

		for (int i = 0; i < space.size(); i++) {
			if (space.get(i).isEmpty()) {
				space.set(i, stack);
				set(player, gear.withDimensional(space));
				return true;
			}
		}

		return false;
	}

	/**
	 * Sposta nello spazio dimensionale l'equipaggiamento del Sistema finito nell'inventario.
	 *
	 * <p>E' l'automatismo dello spazio dimensionale: qualunque strada abbia preso un pezzo per
	 * arrivare — raccolto da terra, tirato fuori da un baule, dato da un comando — un attimo dopo
	 * e' al suo posto. Si fa guardando l'inventario a intervalli invece che agganciando la
	 * raccolta: un solo pezzo di codice copre tutte le strade, e non serve un mixin per nessuna.
	 *
	 * <p>Quello che non entra resta dov'e'. Uno spazio dimensionale pieno non e' un motivo per far
	 * sparire niente.
	 */
	public static void sweep(ServerPlayer player) {
		List<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
		boolean moved = false;

		for (int i = 0; i < inventory.size(); i++) {
			// La barra rapida no. E' dove sta quello che si sta usando — a partire dall'arma in
			// mano, che e' letteralmente una di queste caselle: uno spazio dimensionale che se la
			// prende mentre si combatte sarebbe un sabotaggio, non una comodita'.
			if (Inventory.isHotbarSlot(i)) {
				continue;
			}

			ItemStack stack = inventory.get(i);

			if (!GearItems.isGear(stack)) {
				continue;
			}

			if (store(player, stack.copy())) {
				inventory.set(i, ItemStack.EMPTY);
				moved = true;
			}
		}

		if (moved) {
			player.containerMenu.broadcastChanges();
		}
	}

	// ---------------------------------------------------------------- ranghi e caselle

	/**
	 * Svuota le posizioni che il rango attuale non regge piu'.
	 *
	 * <p>Il caso normale e' un {@code /arise level} all'indietro durante le prove, ma vale anche
	 * per un reset del Sistema: senza questo resterebbero addosso otto anelli a un Cacciatore di
	 * rango E, e i loro attributi conterebbero.
	 */
	public static void enforce(ServerPlayer player) {
		PlayerGear gear = get(player);
		Rank rank = hunterRank(player);

		List<ItemStack> slots = new ArrayList<>(gear.hunter());
		List<ItemStack> removed = new ArrayList<>();

		for (GearSlot slot : GearSlot.HUNTER) {
			int first = slot.firstIndex();

			for (int i = slot.capacity(rank); i < slot.positions(); i++) {
				ItemStack stack = slots.get(first + i);

				if (!stack.isEmpty()) {
					removed.add(stack);
					slots.set(first + i, ItemStack.EMPTY);
				}
			}
		}

		if (removed.isEmpty()) {
			return;
		}

		set(player, gear.withHunter(slots));

		for (ItemStack stack : removed) {
			if (!store(player, stack) && !player.addItem(stack)) {
				player.drop(stack, false);
			}
		}

		player.sendSystemMessage(Component.translatable("arise.msg.gear.demoted", removed.size()));
	}

	/** Toglie tutto: caselle, spazio dimensionale, sacca e quel che si ha addosso. Per le prove. */
	public static void clear(ServerPlayer player) {
		for (EquipmentSlot slot : VANILLA_SLOTS) {
			if (GearItems.isGear(player.getItemBySlot(slot))) {
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		}

		List<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
		for (int i = 0; i < inventory.size(); i++) {
			if (GearItems.isGear(inventory.get(i))) {
				inventory.set(i, ItemStack.EMPTY);
			}
		}

		set(player, PlayerGear.EMPTY);
	}

	// ---------------------------------------------------------------- ricerca di un pezzo

	/**
	 * Dove sta il pezzo con questo identificativo: addosso, nelle nostre caselle o da parte.
	 *
	 * <p>Restituisce lo {@code ItemStack} vero e non una copia, perche' chi lo cerca (le gemme) ci
	 * deve scrivere dentro. Il pezzo si indirizza ancora per UUID come quando era un dato in una
	 * lista: e' l'unico modo che il client ha di dire "quello" senza sapere dove sia finito.
	 */
	public static ItemStack findStack(ServerPlayer player, UUID pieceId) {
		for (EquipmentSlot slot : VANILLA_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);
			if (matches(stack, pieceId)) {
				return stack;
			}
		}

		for (ItemStack stack : get(player).stacks()) {
			if (matches(stack, pieceId)) {
				return stack;
			}
		}

		for (ItemStack stack : player.getInventory().getNonEquipmentItems()) {
			if (matches(stack, pieceId)) {
				return stack;
			}
		}

		return ItemStack.EMPTY;
	}

	private static boolean matches(ItemStack stack, UUID pieceId) {
		GearPiece piece = GearItems.piece(stack);
		return piece != null && piece.id().equals(pieceId);
	}

	/** Riscrive un pezzo dove si trova. Vero se l'ha trovato. */
	public static boolean replace(ServerPlayer player, GearPiece piece) {
		ItemStack stack = findStack(player, piece.id());

		if (stack.isEmpty()) {
			return false;
		}

		GearItems.withPiece(stack, piece);

		// L'oggetto e' stato modificato sul posto: se stava in una nostra casella l'attachment va
		// riscritto, o la modifica non arriverebbe mai al salvataggio.
		set(player, get(player));
		return true;
	}

	/** Tutti i pezzi che il giocatore possiede, ovunque siano. Serve alle gemme e ai comandi. */
	public static List<GearPiece> owned(ServerPlayer player) {
		List<GearPiece> pieces = new ArrayList<>();

		for (EquipmentSlot slot : VANILLA_SLOTS) {
			GearPiece piece = GearItems.piece(player.getItemBySlot(slot));
			if (piece != null) {
				pieces.add(piece);
			}
		}

		for (ItemStack stack : get(player).stacks()) {
			GearPiece piece = GearItems.piece(stack);
			if (piece != null) {
				pieces.add(piece);
			}
		}

		return pieces;
	}

	/**
	 * I pezzi che il giocatore sta <em>indossando</em> davvero.
	 *
	 * <p>Non e' la stessa cosa di {@link #owned}: un pezzo nello spazio dimensionale e' posseduto
	 * ma non conta, e nemmeno una corazza tenuta in mano — un pezzo vale solo nella casella che gli
	 * compete. Da qui passano le statistiche e gli effetti delle gemme, cioe' tutto cio' che
	 * distingue l'avere dall'usare.
	 */
	public static List<GearPiece> worn(ServerPlayer player) {
		List<GearPiece> pieces = new ArrayList<>();

		for (EquipmentSlot slot : VANILLA_SLOTS) {
			GearPiece piece = GearItems.piece(player.getItemBySlot(slot));

			if (piece != null && piece.slot().vanillaSlot() == slot) {
				pieces.add(piece);
			}
		}

		PlayerGear gear = get(player);
		Rank rank = hunterRank(player);

		for (GearSlot slot : GearSlot.HUNTER) {
			int first = slot.firstIndex();

			for (int i = 0; i < slot.capacity(rank); i++) {
				GearPiece piece = GearItems.piece(gear.hunter().get(first + i));

				if (piece != null && piece.slot() == slot) {
					pieces.add(piece);
				}
			}
		}

		return pieces;
	}

	// ---------------------------------------------------------------- morte e ritorno

	/**
	 * Ritira l'equipaggiamento del Sistema un istante prima della morte.
	 *
	 * <p>Un pezzo di rango A perso in fondo a un Gate sarebbe ore di gioco buttate per una caduta
	 * nel vuoto, ed e' il modo classico di far smettere di giocare. Le nostre caselle e lo spazio
	 * dimensionale sopravvivono gia' da soli (l'attachment ha {@code copyOnDeath}); qui si salva
	 * quel che sta nelle caselle di vanilla e nell'inventario, che invece cadrebbe per terra.
	 */
	public static void recover(ServerPlayer player) {
		List<ItemStack> saved = new ArrayList<>();

		for (EquipmentSlot slot : VANILLA_SLOTS) {
			ItemStack stack = player.getItemBySlot(slot);

			if (GearItems.isGear(stack)) {
				saved.add(stack.copy());
				player.setItemSlot(slot, ItemStack.EMPTY);
			}
		}

		List<ItemStack> inventory = player.getInventory().getNonEquipmentItems();
		for (int i = 0; i < inventory.size(); i++) {
			ItemStack stack = inventory.get(i);

			if (GearItems.isGear(stack)) {
				saved.add(stack.copy());
				inventory.set(i, ItemStack.EMPTY);
			}
		}

		if (!saved.isEmpty()) {
			player.setAttached(ModAttachments.GEAR, get(player).withRecovered(saved));
		}
	}

	/**
	 * Restituisce quello che era stato ritirato.
	 *
	 * <p>Ogni pezzo torna nella casella da cui e' partito se quella casella e' libera: si
	 * riappare vestiti come si era, che e' l'unico modo perche' il vincolo all'anima si senta come
	 * una promessa mantenuta invece che come un mucchio di roba da risistemare.
	 */
	public static void restore(ServerPlayer player) {
		PlayerGear gear = get(player);

		if (gear.recovered().isEmpty()) {
			return;
		}

		for (ItemStack stack : gear.recovered()) {
			GearPiece piece = GearItems.piece(stack);
			EquipmentSlot slot = piece == null ? null : piece.slot().vanillaSlot();

			if (slot != null && player.getItemBySlot(slot).isEmpty()) {
				player.setItemSlot(slot, stack);
				continue;
			}

			if (!store(player, stack) && !player.addItem(stack)) {
				player.drop(stack, false);
			}
		}

		int count = gear.recovered().size();
		set(player, get(player).withRecovered(List.of()));
		player.sendSystemMessage(Component.translatable("arise.msg.gear.restored", count));
	}

	// ---------------------------------------------------------------- sorgente di statistiche

	private static void contribute(ServerPlayer player, BiConsumer<Stat, Double> out) {
		for (GearPiece piece : worn(player)) {
			contribute(piece, out);
		}
	}

	private static void contribute(GearPiece piece, BiConsumer<Stat, Double> out) {
		for (Map.Entry<Stat, Double> entry : piece.stats().entrySet()) {
			out.accept(entry.getKey(), entry.getValue());
		}

		// Le gemme incastonate contano come parte del pezzo che le porta: una gemma nella sacca
		// non fa niente, ed e' proprio la differenza fra averla e usarla.
		for (Gem gem : piece.gems()) {
			for (Map.Entry<Stat, Double> entry : gem.stats().entrySet()) {
				out.accept(entry.getKey(), entry.getValue());
			}
		}
	}

	/** Il totale che l'equipaggiamento indossato concede, per la schermata e per i comandi. */
	public static double bonus(ServerPlayer player, Stat stat) {
		double[] total = {0.0};

		contribute(player, (contributed, amount) -> {
			if (contributed == stat) {
				total[0] += amount;
			}
		});

		return total[0];
	}

	/**
	 * Il battito dell'equipaggiamento: quello che va guardato di continuo, in un posto solo.
	 *
	 * <p>Tre cose che nessun evento ci racconta piu'. I pezzi raccolti vanno smistati; le caselle
	 * che il rango non regge vanno svuotate; e l'incarico che chiede di indossare qualcosa deve
	 * potersi accorgere che qualcosa e' addosso — da quando i pezzi stanno nelle caselle di
	 * vanilla, infilare un elmo non passa da nessun nostro metodo.
	 */
	public static void tick(ServerPlayer player) {
		// Il ritiro alla morte scatta prima che la morte sia certa: se un'altra mod la annulla, il
		// giocatore resta vivo con l'equipaggiamento in sospeso. Qui gli torna subito.
		if (player.isAlive()) {
			restore(player);
		}

		sweep(player);
		enforce(player);

		int count = worn(player).size();
		if (count > 0) {
			QuestManager.advance(player, Objective.EQUIP, count);
		}
	}

	/** Un pezzo nuovo di zecca, tirato al momento. */
	public static GearPiece roll(ServerPlayer player, GearSlot slot, Rank rank) {
		GearConfig config = AriseConfig.get().gear();
		return GearRoll.roll(config, slot, rank, player.level().getRandom());
	}
}
