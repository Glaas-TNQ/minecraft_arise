package com.luca.arise.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.luca.arise.daily.DailyManager;
import com.luca.arise.daily.DailyQuest;
import com.luca.arise.daily.DailyTask;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * I quattro verbi della giornata, agganciati alle sole cose che Minecraft sa gia' contare.
 *
 * <p>Cento flessioni, cento addominali, cento squat e dieci chilometri diventano scavare, colpire,
 * saltare e correre. Non e' una traduzione poetica: sono <em>esattamente</em> i quattro gesti che un
 * Cacciatore fa in una giornata qualunque, e la giornaliera deve essere un promemoria di giocare,
 * non un secondo lavoro.
 *
 * <p>Tre dei quattro sono eventi che il gioco emette da solo. Il quarto — la corsa — non ha un
 * evento, e va misurato: due numeri per giocatore e una sottrazione a battito, che e' il prezzo piu'
 * basso possibile per non scrivere un mixin.
 */
public final class DailyEvents {

	/**
	 * giocatore → dov'era l'ultima volta che abbiamo guardato, e se stava correndo.
	 *
	 * <p>Solo la distanza <em>orizzontale</em> conta: cadere da un dirupo non e' correre, e senza
	 * questa esclusione la giornaliera si chiuderebbe da sola con un tuffo. Ed e' proprio la ragione
	 * per cui questa mappa esiste — senza un punto precedente non c'e' modo di sapere quanta strada
	 * e' stata fatta, ne' come.
	 */
	private static final Map<UUID, double[]> LAST_SEEN = new HashMap<>();

	/** Un salto conta quando i piedi lasciano terra, non a ogni tick in aria. */
	private static final Map<UUID, Boolean> WAS_GROUNDED = new HashMap<>();

	/** Oltre questo, in un solo battito, non e' corsa: e' un teletrasporto o un viaggio. */
	private static final double MAX_STEP = 12.0;

	/** Ogni quanti tick si guarda che giorno e'. Lo stesso ritmo del resto della mod. */
	private static final int SLOW_EVERY = 5;

	private DailyEvents() {
	}

	public static void register() {
		// Scavare. Solo sul server e solo i blocchi veri: quelli piazzati e ripresi contano lo
		// stesso, ed e' giusto — la giornaliera misura la fatica, non l'onesta'.
		PlayerBlockBreakEvents.AFTER.register((level, player, pos, state, blockEntity) -> {
			if (player instanceof ServerPlayer serverPlayer) {
				DailyManager.advance(serverPlayer, DailyTask.BLOCKS, 1);
			}
		});

		// Colpire. Il colpo va contato quando arriva, non quando parte: un attacco a vuoto e' un
		// braccio che si muove, e i cento addominali del canone non si fanno a vuoto.
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (taken > 0.0F && source.getEntity() instanceof ServerPlayer attacker
					&& attacker != entity) {
				DailyManager.advance(attacker, DailyTask.HITS, 1);
			}
		});

		// Saltare e correre non hanno un evento, e vanno misurati.
		//
		// Due ritmi diversi nello stesso giro, e la distinzione conta. Il salto e la corsa si
		// guardano a <em>ogni</em> tick, perche' un salto e' una transizione — piedi a terra, piedi
		// in aria — e guardarla una volta ogni cinque significa perderne quattro su cinque. La
		// logica della giornata invece si chiede «che giorno e'», e chiederselo venti volte al
		// secondo e' diciannove volte di troppo.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			boolean slow = server.getTickCount() % SLOW_EVERY == 0;

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				jumps(player);
				sprint(player);

				if (slow) {
					DailyManager.tick(player);
				}
			}
		});

		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> forget(
				handler.getPlayer().getUUID()));
	}

	/**
	 * Un salto: il tick in cui i piedi lasciano terra.
	 *
	 * <p>Contare {@code !onGround} direbbe «sei in aria» e conterebbe venti volte per salto, piu'
	 * ogni caduta. La transizione invece succede una volta sola per salto, ed e' esattamente cio'
	 * che uno squat e'.
	 */
	private static void jumps(ServerPlayer player) {
		boolean grounded = player.onGround();
		Boolean before = WAS_GROUNDED.put(player.getUUID(), grounded);

		if (before != null && before && !grounded && player.getDeltaMovement().y > 0.0) {
			DailyManager.advance(player, DailyTask.JUMPS, 1);
		}
	}

	/**
	 * La corsa: quanta strada orizzontale si e' fatta di corsa dall'ultimo battito.
	 *
	 * <p>Tre esclusioni, e ognuna toglie un modo di barare senza giocare: chi non sta correndo non
	 * conta, la componente verticale non conta (una caduta non e' una corsa), e uno spostamento piu'
	 * lungo di dodici blocchi in un battito non conta affatto — quello e' un teletrasporto, un
	 * viaggio fra citta' o l'ingresso in un varco, e nessuno dei tre e' un chilometro percorso.
	 */
	private static void sprint(ServerPlayer player) {
		double[] before = LAST_SEEN.put(player.getUUID(),
				new double[] {player.getX(), player.getZ()});

		if (before == null || !player.isSprinting()) {
			return;
		}

		double dx = player.getX() - before[0];
		double dz = player.getZ() - before[1];
		double moved = Math.sqrt(dx * dx + dz * dz);

		if (moved <= 0.0 || moved > MAX_STEP) {
			return;
		}

		// I blocchi si accumulano in numeri interi, e la parte decimale andrebbe persa a ogni
		// battito: a quattro battiti al secondo si perderebbe quasi tutto. Si tiene il resto.
		double carried = REMAINDER.merge(player.getUUID(), moved, Double::sum);
		int whole = (int) carried;

		if (whole <= 0) {
			return;
		}

		REMAINDER.put(player.getUUID(), carried - whole);
		DailyManager.advance(player, DailyTask.SPRINT, whole);
	}

	/** Il resto decimale dei blocchi percorsi, che altrimenti si perderebbe a ogni battito. */
	private static final Map<UUID, Double> REMAINDER = new HashMap<>();

	private static void forget(UUID player) {
		LAST_SEEN.remove(player);
		WAS_GROUNDED.remove(player);
		REMAINDER.remove(player);
		DailyManager.forget(player);
	}
}
