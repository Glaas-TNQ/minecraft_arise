package com.luca.arise.mana;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.ManaConfig;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;

/**
 * La riserva di Mana, lato server.
 *
 * <p>Tre soli verbi: {@link #tick} la fa risalire, {@link #spend} la fa scendere e risponde se
 * bastava, {@link #max} dice quanto puo' contenere. Tutto il resto della mod chiama solo
 * {@code spend}, e non deve sapere altro.
 *
 * <p>Il massimo non e' un dato salvato: si ricava dal livello. E' la stessa regola degli attributi
 * — una sorgente sola per ogni fatto — e ha una conseguenza comoda: chi sale di livello si trova
 * la riserva piu' capiente all'istante, senza che nessuno debba ricordarsi di aggiornarla.
 *
 * <h2>Perche' la rigenerazione si conta sul tempo e non sui battiti</h2>
 *
 * <p>Sommare {@code regenPerSecond / 4} a ogni quarto di secondo sembra la stessa cosa ed e' un
 * difetto in agguato: con un numero come 6 al secondo il quarto vale 1,5, e un intero non lo
 * contiene — si perderebbe mezzo punto per battito, cioe' due al secondo su sei. Qui si guarda
 * quanto tempo e' passato dall'ultimo conteggio, e i decimi restano nel resto invece di sparire.
 */
public final class ManaManager {

	private ManaManager() {
	}

	public static Mana get(ServerPlayer player) {
		return player.getAttachedOrCreate(ModAttachments.MANA);
	}

	private static void set(ServerPlayer player, Mana mana) {
		player.setAttached(ModAttachments.MANA, mana);
	}

	private static ManaConfig config() {
		return AriseConfig.get().abilities().mana();
	}

	/** Il tetto della riserva a questo livello. */
	public static int max(ServerPlayer player) {
		return config().max(ProgressManager.get(player).level());
	}

	/** Quanto ne resta adesso, gia' entro il tetto. */
	public static int current(ServerPlayer player) {
		Mana mana = get(player);
		int max = max(player);
		return mana.unset() ? max : Math.min(mana.current(), max);
	}

	/** Riempie la riserva. Al risveglio, al risorgere, e quando un comando lo chiede. */
	public static void refill(ServerPlayer player) {
		set(player, new Mana(max(player), 0L, player.level().getGameTime()));
	}

	/**
	 * Spende, se ce n'e' abbastanza.
	 *
	 * <p>Tutto o niente: non esiste una spesa parziale. Un'evocazione a meta' non e' un'evocazione,
	 * e chi chiama non deve dover gestire il caso «ne e' passata un po'».
	 *
	 * @return vero se il Mana e' stato tolto
	 */
	public static boolean spend(ServerPlayer player, int cost) {
		if (cost <= 0) {
			return true;
		}

		long now = player.level().getGameTime();
		int have = current(player);

		if (have < cost) {
			return false;
		}

		set(player, get(player).spent(have - cost, now, config().pauseTicks()));
		return true;
	}

	/** Restituisce Mana speso per un'azione che poi non e' avvenuta. Non supera il tetto. */
	public static void refund(ServerPlayer player, int amount) {
		if (amount <= 0) {
			return;
		}

		long now = player.level().getGameTime();
		set(player, get(player).with(Math.min(max(player), current(player) + amount), now));
	}

	/**
	 * Un battito di rigenerazione.
	 *
	 * <p>Fa tre cose, e la prima e' la piu' importante: se la riserva non e' mai stata riempita la
	 * riempie. E' cosi' che un Cacciatore che gioca dopo l'aggiornamento — e che nel suo salvataggio
	 * non ha nessun Mana scritto — non si ritrova a zero senza capire perche'.
	 */
	public static void tick(ServerPlayer player) {
		long now = player.level().getGameTime();
		Mana mana = get(player);
		int max = max(player);

		if (mana.unset()) {
			set(player, new Mana(max, 0L, now));
			return;
		}

		// Il tetto puo' essere sceso: qualcuno ha cambiato la config, o e' stata usata la Pergamena
		// del Rimpianto. Una riserva sopra il proprio tetto va ricondotta, e va fatto qui perche'
		// questo e' l'unico posto che passa comunque a ogni battito.
		if (mana.current() > max) {
			set(player, mana.with(max, now));
			return;
		}

		if (now < mana.busyUntil()) {
			// Ferma, ma il conto riparte da adesso: senza questa riga, al termine della pausa si
			// incasserebbe in un colpo solo tutta la rigenerazione che la pausa doveva impedire.
			set(player, mana.with(mana.current(), now));
			return;
		}

		if (mana.current() >= max) {
			set(player, mana.with(max, now));
			return;
		}

		long elapsed = Math.max(0L, now - mana.lastRegen());
		int gained = (int) (elapsed * config().regenPerSecond() / 20.0);

		if (gained <= 0) {
			return;
		}

		set(player, mana.with(Math.min(max, mana.current() + gained), now));
	}
}
