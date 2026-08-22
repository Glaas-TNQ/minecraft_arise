package com.luca.arise.event;

import com.luca.arise.city.CityManager;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.quest.Objective;
import com.luca.arise.quest.QuestManager;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;

import net.minecraft.server.level.ServerPlayer;

/**
 * Il risveglio, e i pochi obiettivi che nessun altro sistema puo' segnalare.
 *
 * <p>La maggior parte degli incarichi avanza da dentro il sistema che la riguarda — estrarre
 * un'ombra lo dice {@code ShadowManager}, comprare lo dice {@code ShopManager} — perche' quello e'
 * l'unico posto che sa davvero se l'azione e' riuscita. Qui restano i tre casi che non hanno un
 * gestore proprio: il colpo mortale, il livello raggiunto e l'arrivo in una citta'.
 */
public final class QuestEvents {

	/** Ogni quanti tick si guarda se qualcuno e' entrato in un'Associazione. */
	private static final int CITY_CHECK_TICKS = 40;

	private QuestEvents() {
	}

	public static void register() {
		// Il risveglio. ALLOW_DEATH e' l'unico evento che permette di dire "questa morte non
		// avviene": intercettarla altrove significherebbe far morire il giocatore e poi rianimarlo,
		// che si vede — schermata di morte, inventario a terra, punto di respawn.
		ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, damage) -> {
			if (entity instanceof ServerPlayer player) {
				return !QuestManager.awaken(player);
			}

			return true;
		});

		// Il livello raggiunto non ha un momento suo: si guarda insieme al resto, una volta ogni
		// due secondi. Un incarico che chiede "arriva al livello 3" puo' permettersi il ritardo.
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % CITY_CHECK_TICKS != 0) {
				return;
			}

			int radius = AriseConfig.get().gems().benchRadius();

			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				QuestManager.advance(player, Objective.REACH_LEVEL,
						ProgressManager.get(player).level());

				if (CityManager.atAssociation(player, radius)) {
					QuestManager.advance(player, Objective.VISIT_CITY);
				}
			}
		});

		// All'ingresso si ricorda cosa si stava facendo: la catena non deve mai lasciare senza un
		// compito, e dopo un riavvio nessuno si ricorda a che punto era.
		ServerPlayerEvents.JOIN.register(QuestManager::announceNext);
	}
}
