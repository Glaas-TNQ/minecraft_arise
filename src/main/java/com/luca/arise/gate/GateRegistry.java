package com.luca.arise.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.luca.arise.registry.ModAttachments;
import com.mojang.serialization.Codec;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

/**
 * L'elenco dei varchi aperti, per la mappa.
 *
 * <p>Il principio del generatore dei varchi resta vero: <em>quanti varchi ci sono si scopre
 * guardando il mondo</em>, e chi decide se aprirne un altro continua a contarli nel mondo. Ma la
 * mappa ha una domanda a cui il mondo non sa rispondere: dov'è il varco che si è aperto a
 * centocinquanta blocchi e poi è rimasto indietro quando ci si è allontanati? Il suo chunk è
 * scaricato, l'entità dorme nel file della regione e nessuno può interrogarla. Senza un appunto
 * preso quando era sveglia, sulla mappa quel varco non ci sarebbe — ed è esattamente il varco
 * che la mappa serve a ritrovare.
 *
 * <p>L'appunto è <strong>un indice, non la verità</strong>, e si riconcilia ogni volta che lo si
 * legge: se il chunk di un varco è caricato e l'entità non c'è, la voce cade. Se il chunk è
 * scaricato, la voce resta e la mappa lo dice — "in sospeso" — perché un varco che non batte i
 * suoi tick non scade, e il tempo segnato è quello dell'ultima volta che si è fatto sentire.
 *
 * <p>Vive come attachment persistente dell'Overworld, qualunque dimensione abbia il varco: un
 * solo posto dove guardare. Il tipo è immutabile e si sostituisce tutto, come ogni altro dato
 * della mod.
 */
public record GateRegistry(List<GateRecord> gates) {

	public static final GateRegistry EMPTY = new GateRegistry(List.of());

	public static final Codec<GateRegistry> CODEC =
			GateRecord.CODEC.listOf().xmap(GateRegistry::new, GateRegistry::gates);

	public GateRegistry {
		gates = List.copyOf(gates);
	}

	/** Questo elenco con la voce aggiunta o, se c'era già con lo stesso id, aggiornata. */
	public GateRegistry with(GateRecord record) {
		List<GateRecord> next = new ArrayList<>(gates.size() + 1);
		boolean replaced = false;

		for (GateRecord existing : gates) {
			if (existing.id().equals(record.id())) {
				next.add(record);
				replaced = true;
			} else {
				next.add(existing);
			}
		}

		if (!replaced) {
			next.add(record);
		}

		return new GateRegistry(next);
	}

	/** Questo elenco senza la voce con quell'id. Se non c'era, è lo stesso elenco. */
	public GateRegistry without(UUID id) {
		List<GateRecord> next = new ArrayList<>(gates.size());

		for (GateRecord existing : gates) {
			if (!existing.id().equals(id)) {
				next.add(existing);
			}
		}

		return next.size() == gates.size() ? this : new GateRegistry(next);
	}

	// ---------------------------------------------------------------- l'indice del server

	private static ServerLevel home(MinecraftServer server) {
		return server.overworld();
	}

	/** L'indice com'è scritto, senza riconciliare. */
	public static GateRegistry stored(MinecraftServer server) {
		return home(server).getAttachedOrElse(ModAttachments.GATE_REGISTRY, EMPTY);
	}

	/** Prende nota di un varco: nuovo o aggiornato, con il tempo che gli resta adesso. */
	public static void update(GateEntity varco) {
		if (varco.offer() == null || !(varco.level() instanceof ServerLevel level)) {
			return;
		}

		ServerLevel home = home(level.getServer());
		home.setAttached(ModAttachments.GATE_REGISTRY, stored(level.getServer()).with(GateRecord.of(varco)));
	}

	/** Dimentica un varco che non c'è più: attraversato, rifiutato o scaduto. */
	public static void forget(MinecraftServer server, UUID id) {
		GateRegistry current = stored(server);
		GateRegistry next = current.without(id);

		if (next != current) {
			home(server).setAttached(ModAttachments.GATE_REGISTRY, next);
		}
	}

	/**
	 * Vero se il varco è sveglio: il suo chunk simula le entità, quindi l'entità batte i suoi tick
	 * e il tempo rimasto segnato è quello vero. Falso se dorme in un chunk scaricato.
	 */
	public static boolean awake(MinecraftServer server, GateRecord record) {
		ServerLevel level = server.getLevel(record.dimension());
		return level != null && level.isPositionEntityTicking(record.pos());
	}

	/**
	 * L'elenco riconciliato con il mondo: le voci il cui varco, sveglio, non esiste più vengono
	 * tolte — e l'indice riscritto, così la pulizia si fa una volta sola.
	 */
	public static List<GateRecord> reconciled(MinecraftServer server) {
		GateRegistry current = stored(server);
		List<GateRecord> alive = new ArrayList<>(current.gates().size());
		boolean dropped = false;

		for (GateRecord record : current.gates()) {
			ServerLevel level = server.getLevel(record.dimension());

			if (level == null) {
				dropped = true;
				continue;
			}

			// Solo dove il chunk simula le entita' si puo' dire che l'entita' non c'e': in un chunk
			// caricato a meta' la risposta sarebbe "no" anche per un varco vivo e vegeto.
			if (level.isPositionEntityTicking(record.pos())
					&& !(level.getEntity(record.id()) instanceof GateEntity)) {
				dropped = true;
				continue;
			}

			alive.add(record);
		}

		if (dropped) {
			home(server).setAttached(ModAttachments.GATE_REGISTRY, new GateRegistry(alive));
		}

		return List.copyOf(alive);
	}
}
