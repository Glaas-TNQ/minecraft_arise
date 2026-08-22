package com.luca.arise.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * L'equipaggiamento del Cacciatore, lato server.
 *
 * <p>Il client non decide nulla: manda "voglio indossare questo pezzo" e qui si verifica che il
 * pezzo esista, che sia suo, che lo slot sia sbloccato e che ci sia posto. Vale anche in
 * singleplayer, che e' comunque un server (CLAUDE.md §4).
 *
 * <p>Ogni modifica finisce con un {@link ProgressManager#applyAttributes}: e' l'unico punto in cui
 * le statistiche diventano attributi, e passa dalla somma delle sorgenti registrate in
 * {@link StatSources}.
 */
public final class GearManager {

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
	 * Il rango del giocatore, che decide quali slot sono aperti.
	 *
	 * <p>Si ricava dal livello invece di essere un dato a parte: un secondo valore da tenere in
	 * sincronia con il primo e' un secondo valore che prima o poi ci va in disaccordo. Stessa
	 * scelta gia' fatta per il rango delle ombre.
	 */
	public static Rank hunterRank(ServerPlayer player) {
		PlayerProgress progress = player.getAttachedOrCreate(ModAttachments.PROGRESS);
		return AriseConfig.get().hunterRank(progress.level());
	}

	// ---------------------------------------------------------------- operazioni

	/** Mette un pezzo nello zaino. */
	public static Component grant(ServerPlayer player, GearPiece piece) {
		PlayerGear gear = get(player);
		int limit = AriseConfig.get().gear().stashSize();

		if (gear.stash().size() >= limit) {
			return Component.translatable("arise.msg.gear.stash_full", limit);
		}

		set(player, gear.withStashed(piece));
		return Component.translatable("arise.msg.gear.granted", piece.displayName());
	}

	/** Indossa un pezzo che sta nello zaino. */
	public static Component equip(ServerPlayer player, UUID id) {
		Component locked = QuestManager.require(player, Unlock.GEAR);
		if (locked != null) {
			return locked;
		}

		PlayerGear gear = get(player);
		Optional<GearPiece> found = gear.find(id);

		if (found.isEmpty()) {
			return Component.translatable("arise.msg.gear.unknown");
		}

		GearPiece piece = found.get();
		if (gear.isEquipped(id)) {
			return Component.translatable("arise.msg.gear.already_worn", piece.displayName());
		}

		GearSlot slot = piece.slot();
		Rank rank = hunterRank(player);
		int capacity = slot.capacity(rank);

		if (capacity == 0) {
			Rank needed = slot.nextUnlock(rank);
			return Component.translatable("arise.msg.gear.slot_locked", slot.label(),
					needed == null ? Rank.S.label() : needed.label());
		}

		if (gear.equippedIn(slot).size() >= capacity) {
			return Component.translatable("arise.msg.gear.slot_full", slot.label(), capacity);
		}

		set(player, gear.withEquipped(piece));
		QuestManager.advance(player, Objective.EQUIP);

		return Component.translatable("arise.msg.gear.equipped", piece.displayName());
	}

	/** Si toglie un pezzo e torna nello zaino. */
	public static Component unequip(ServerPlayer player, UUID id) {
		PlayerGear gear = get(player);
		Optional<GearPiece> found = gear.find(id);

		if (found.isEmpty() || !gear.isEquipped(id)) {
			return Component.translatable("arise.msg.gear.unknown");
		}

		int limit = AriseConfig.get().gear().stashSize();
		if (gear.stash().size() >= limit) {
			return Component.translatable("arise.msg.gear.stash_full", limit);
		}

		set(player, gear.withUnequipped(found.get()));
		return Component.translatable("arise.msg.gear.unequipped", found.get().displayName());
	}

	/** Butta via un pezzo. Non si recupera: la conferma la chiede la schermata. */
	public static Component discard(ServerPlayer player, UUID id) {
		PlayerGear gear = get(player);
		Optional<GearPiece> found = gear.find(id);

		if (found.isEmpty()) {
			return Component.translatable("arise.msg.gear.unknown");
		}

		set(player, gear.without(id));
		return Component.translatable("arise.msg.gear.discarded", found.get().displayName());
	}

	/** Toglie tutto. Serve ai comandi di prova. */
	public static void clear(ServerPlayer player) {
		set(player, PlayerGear.EMPTY);
	}

	/**
	 * Rimanda nello zaino i pezzi in posizioni che il rango attuale non regge piu'.
	 *
	 * <p>Il caso normale e' un {@code /arise level} all'indietro durante le prove, ma vale anche
	 * per un reset del Sistema: senza questo resterebbero addosso otto anelli a un Cacciatore di
	 * rango E, e i loro attributi conterebbero.
	 */
	public static void enforce(ServerPlayer player) {
		PlayerGear gear = get(player);
		if (gear.equipped().isEmpty()) {
			return;
		}

		Rank rank = hunterRank(player);
		List<GearPiece> kept = new ArrayList<>();
		List<GearPiece> removed = new ArrayList<>();

		for (GearSlot slot : GearSlot.values()) {
			int capacity = slot.capacity(rank);
			int worn = 0;

			for (GearPiece piece : gear.equippedIn(slot)) {
				if (worn < capacity) {
					kept.add(piece);
					worn++;
				} else {
					removed.add(piece);
				}
			}
		}

		if (removed.isEmpty()) {
			return;
		}

		List<GearPiece> stash = new ArrayList<>(gear.stash());
		stash.addAll(removed);
		set(player, new PlayerGear(kept, stash, gear.pouch()));

		player.sendSystemMessage(Component.translatable("arise.msg.gear.demoted", removed.size()));
	}

	// ---------------------------------------------------------------- sorgente di statistiche

	private static void contribute(ServerPlayer player, BiConsumer<Stat, Double> out) {
		for (GearPiece piece : get(player).equipped()) {
			for (Map.Entry<Stat, Double> entry : piece.stats().entrySet()) {
				out.accept(entry.getKey(), entry.getValue());
			}

			// Le gemme incastonate contano come parte del pezzo che le porta: una gemma nella
			// sacca non fa niente, ed e' proprio la differenza fra averla e usarla.
			for (Gem gem : piece.gems()) {
				for (Map.Entry<Stat, Double> entry : gem.stats().entrySet()) {
					out.accept(entry.getKey(), entry.getValue());
				}
			}
		}
	}

	/** Il totale che l'equipaggiamento indossato concede, per la schermata e per i comandi. */
	public static double bonus(ServerPlayer player, Stat stat) {
		double total = 0.0;
		for (GearPiece piece : get(player).equipped()) {
			total += piece.stats().getOrDefault(stat, 0.0);
		}
		return total;
	}

	/** Un pezzo nuovo di zecca, tirato al momento. */
	public static GearPiece roll(ServerPlayer player, GearSlot slot, Rank rank) {
		GearConfig config = AriseConfig.get().gear();
		return GearRoll.roll(config, slot, rank, player.level().getRandom());
	}
}
