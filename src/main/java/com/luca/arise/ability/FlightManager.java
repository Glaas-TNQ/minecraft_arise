package com.luca.arise.ability;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.ManaConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.mana.ManaManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;

/**
 * Il volo del Monarca: acceso finche' c'e' Mana.
 *
 * <p>Non e' l'elytra e non e' la creativa: e' il volo della creativa <em>affittato</em>. Si accende
 * pagando, si paga a ogni secondo passato in aria, e nell'istante in cui la riserva non basta piu'
 * si spegne — con il giocatore dove si trova, che quasi sempre vuol dire in caduta. La caduta e'
 * voluta: e' cio' che rende il volo una decisione invece di uno stato.
 *
 * <h2>Le due cose che possono andare storte, e come sono chiuse</h2>
 *
 * <p>La prima e' <strong>restare col volo addosso</strong>. {@code mayfly} e' un permesso del
 * giocatore, non un effetto a tempo: nessuno lo toglie da solo. Se il server si spegne, se si
 * cambia dimensione, se si muore mentre e' acceso, quel permesso resterebbe scritto nel salvataggio
 * — un volo creativo permanente ottenuto morendo. Per questo si spegne da ogni uscita:
 * {@link #stop} e' chiamata alla disconnessione, alla morte, al risveglio e al cambio di
 * dimensione, oltre che dal battito quando il Mana finisce.
 *
 * <p>La seconda e' <strong>togliere il volo a chi ce l'ha di suo</strong>. In creativa e da
 * spettatore {@code mayfly} appartiene alla modalita', e spegnerlo qui vorrebbe dire che una
 * mod ha rotto la creativa. Percio' su chi e' in creativa o spettatore questo gestore non tocca
 * niente: ne' per accendere, ne' — soprattutto — per spegnere.
 */
public final class FlightManager {

	/**
	 * Chi sta volando, e da quale istante non gli si addebita piu' nulla.
	 *
	 * <p>In memoria e non in un attachment perche' il volo <strong>non deve sopravvivere a
	 * niente</strong>: non a un riavvio, non a una morte, non a un cambio di mondo. Una mappa che
	 * si svuota da sola quando il server si ferma e' esattamente la garanzia che serve.
	 */
	private static final Map<UUID, Long> FLYING = new HashMap<>();

	private FlightManager() {
	}

	private static ManaConfig config() {
		return AriseConfig.get().abilities().mana();
	}

	public static boolean isFlying(ServerPlayer player) {
		return FLYING.containsKey(player.getUUID());
	}

	/** Vero se la modalita' di gioco gli da' gia' il volo: qui non si tocca niente. */
	private static boolean ownFlight(ServerPlayer player) {
		GameType mode = player.gameMode();
		return mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
	}

	/**
	 * Accende o spegne. E' la forma che serve a un'abilita' che non e' un momento ma uno stato.
	 *
	 * @return il messaggio da mostrare, o {@code null} se non e' successo niente
	 */
	public static Component toggle(ServerPlayer player) {
		if (ownFlight(player)) {
			return Component.translatable("arise.msg.flight.own");
		}

		if (isFlying(player)) {
			stop(player);
			return Component.translatable("arise.msg.flight.off");
		}

		ManaConfig config = config();

		// Il pavimento: accendersi con quattro punti in riserva vorrebbe dire spegnersi mezzo
		// secondo dopo, in aria, senza aver fatto in tempo a capire cosa e' successo.
		if (ManaManager.current(player) < Math.max(config.flightFloor(), config.cost(Ability.SHADOW_FLIGHT))) {
			return Component.translatable("arise.msg.flight.no_mana");
		}

		if (!ManaManager.spend(player, config.cost(Ability.SHADOW_FLIGHT))) {
			return Component.translatable("arise.msg.flight.no_mana");
		}

		FLYING.put(player.getUUID(), player.level().getGameTime());

		player.getAbilities().mayfly = true;
		player.getAbilities().flying = true;
		player.onUpdateAbilities();

		AriseFx.flightOn(player);
		return Component.translatable("arise.msg.flight.on", config.flightCostPerSecond())
				.withStyle(ChatFormatting.AQUA);
	}

	/**
	 * Spegne, se acceso. Sicura da chiamare su chiunque e quante volte si vuole.
	 *
	 * <p>Non azzera la caduta di proposito: chi si spegne a cinquanta blocchi da terra cade da
	 * cinquanta blocchi. Il volo e' una risorsa, e una risorsa che non si puo' finire male non e'
	 * una risorsa.
	 */
	public static void stop(ServerPlayer player) {
		if (FLYING.remove(player.getUUID()) == null) {
			return;
		}

		if (ownFlight(player)) {
			return;
		}

		player.getAbilities().mayfly = false;
		player.getAbilities().flying = false;
		player.onUpdateAbilities();
	}

	/** Alla disconnessione: la mappa non deve crescere per sempre. */
	public static void forget(UUID player) {
		FLYING.remove(player);
	}

	/**
	 * Un battito: addebita i secondi passati in aria, e spegne quando non si paga piu'.
	 *
	 * <p>Chiamato quattro volte al secondo, ma addebita <em>a secondi interi</em> — si guarda quanto
	 * tempo e' passato dall'ultimo addebito, come per la rigenerazione. Un addebito da un quarto di
	 * secondo su un costo intero perderebbe i resti.
	 */
	public static void tick(ServerPlayer player) {
		Long since = FLYING.get(player.getUUID());

		if (since == null) {
			return;
		}

		// Qualcuno e' passato in creativa mentre volava: da adesso il volo e' suo, non nostro.
		if (ownFlight(player)) {
			FLYING.remove(player.getUUID());
			return;
		}

		if (player.isDeadOrDying()) {
			stop(player);
			return;
		}

		long now = player.level().getGameTime();
		long seconds = (now - since) / 20L;

		if (seconds <= 0) {
			return;
		}

		int cost = (int) (seconds * config().flightCostPerSecond());

		if (!ManaManager.spend(player, cost)) {
			// Non basta: si spegne, e cio' che restava se lo prende comunque il volo gia' fatto.
			ManaManager.spend(player, ManaManager.current(player));
			stop(player);
			player.sendSystemMessage(Component.translatable("arise.msg.flight.spent")
					.withStyle(ChatFormatting.RED));
			return;
		}

		FLYING.put(player.getUUID(), since + seconds * 20L);
	}

	/** All'arresto del server: nessuno sta piu' volando. */
	public static void clear() {
		FLYING.clear();
	}
}
