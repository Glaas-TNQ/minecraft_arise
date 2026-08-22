package com.luca.arise.progress;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;

/**
 * Tutto cio' che concede statistiche a un giocatore, sommato prima di diventare attributi.
 *
 * <p>E' la premessa architetturale dell'equipaggiamento (design §8.2). La regola di
 * {@code CLAUDE.md} §6 dice: <em>un solo modificatore per statistica, con id fisso, rimpiazzato a
 * ogni cambiamento</em>. Con una sola sorgente — i punti spesi — era immediato. Con quattro
 * (punti, equipaggiamento, gemme, buff a tempo) la tentazione sarebbe aggiungere un modificatore
 * per sorgente, che e' esattamente il bug che quella regola previene: modificatori che si
 * accumulano e non se ne vanno piu'.
 *
 * <p>Quindi le sorgenti si registrano qui, si sommano qui, e {@link ProgressManager} continua a
 * scrivere un modificatore solo per statistica. Aggiungere una sorgente nuova non tocca nulla di
 * tutto questo: si chiama {@link #register}.
 *
 * <p>Nessuna sincronizzazione: le sorgenti si registrano all'avvio, dal thread principale, prima
 * che esista un giocatore.
 */
public final class StatSources {

	/** Una sorgente scrive i propri contributi chiamando l'accumulatore che riceve. */
	@FunctionalInterface
	public interface Source {
		void contribute(ServerPlayer player, BiConsumer<Stat, Double> out);
	}

	private static final List<Source> SOURCES = new ArrayList<>();

	static {
		// I punti spesi salendo di livello: la sorgente originale, sempre presente.
		register(StatSources::fromSpentPoints);
	}

	private StatSources() {
	}

	/** Aggiunge una sorgente. Da chiamare all'avvio, non a gioco iniziato. */
	public static void register(Source source) {
		SOURCES.add(source);
	}

	/**
	 * Il totale per statistica, sommando ogni sorgente.
	 *
	 * <p>Le statistiche a zero non compaiono nella mappa: chi la percorre per scrivere i
	 * modificatori deve comunque passare da {@code Stat.values()} per poterne <em>togliere</em>
	 * uno che prima c'era.
	 */
	public static Map<Stat, Double> totals(ServerPlayer player) {
		EnumMap<Stat, Double> totals = new EnumMap<>(Stat.class);
		BiConsumer<Stat, Double> out = (stat, amount) -> {
			if (amount != null && amount != 0.0) {
				totals.merge(stat, amount, Double::sum);
			}
		};

		for (Source source : SOURCES) {
			source.contribute(player, out);
		}

		return totals;
	}

	/** Il totale di una sola statistica. Comodo per i messaggi e per le verifiche. */
	public static double total(ServerPlayer player, Stat stat) {
		return totals(player).getOrDefault(stat, 0.0);
	}

	private static void fromSpentPoints(ServerPlayer player, BiConsumer<Stat, Double> out) {
		AriseConfig config = AriseConfig.get();
		PlayerProgress progress = player.getAttachedOrCreate(ModAttachments.PROGRESS);

		for (Stat stat : Stat.SPENDABLE) {
			out.accept(stat, progress.stat(stat) * config.perPoint(stat));
		}
	}
}
