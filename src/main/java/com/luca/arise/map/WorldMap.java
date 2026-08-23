package com.luca.arise.map;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.city.City;
import com.luca.arise.city.CityManager;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.CityConfig;
import com.luca.arise.gate.GateManager;
import com.luca.arise.gate.GateRecord;
import com.luca.arise.gate.GateRegistry;
import com.luca.arise.network.MapPayload;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * La mappa del mondo: le città e i varchi aperti, raccolti dal server e mandati a chi la chiede.
 *
 * <p>È il posto dove due sistemi che non si parlano — le città, che sono blocchi a duecentomila
 * blocchi di distanza, e i varchi, che sono entità a cento — diventano la stessa cosa: un segno
 * con una posizione. Il client non decide niente: riceve l'elenco e lo disegna.
 */
public final class WorldMap {

	private WorldMap() {
	}

	/**
	 * Manda la mappa al giocatore, se può averla. Restituisce il messaggio da mostrare, o
	 * {@code null} se la risposta è la mappa stessa.
	 *
	 * <p>Si apre con i Gate: prima non c'è niente da cercare, e una mappa vuota insegna solo che
	 * esiste un tasto che non fa niente. Dentro un Gate non si apre: le coordinate di un dungeon
	 * non vogliono dire niente sulla mappa dell'Overworld, e un giocatore che si vede in mezzo al
	 * nulla penserebbe a un errore.
	 */
	public static Component open(ServerPlayer player) {
		Component locked = QuestManager.require(player, Unlock.GATES);
		if (locked != null) {
			return locked;
		}

		if (GateManager.isInGate(player)) {
			return Component.translatable("arise.msg.map.in_gate");
		}

		ServerPlayNetworking.send(player, build(player.level().getServer()));
		return null;
	}

	/** Il contenuto della mappa, adesso. */
	public static MapPayload build(MinecraftServer server) {
		ServerLevel overworld = server.overworld();
		CityConfig config = AriseConfig.get().cities();

		List<MapPayload.CityMark> cities = new ArrayList<>();
		for (City city : City.values()) {
			cities.add(new MapPayload.CityMark(city, config.centreX(city), config.centreZ(city),
					CityManager.exists(overworld, city)));
		}

		// Solo l'Overworld: un varco evocato altrove con un comando non sta su questa mappa.
		List<MapPayload.GateMark> gates = new ArrayList<>();
		for (GateRecord record : GateRegistry.reconciled(server)) {
			if (!record.dimension().equals(Level.OVERWORLD)) {
				continue;
			}

			gates.add(new MapPayload.GateMark(record.pos().getX(), record.pos().getY(),
					record.pos().getZ(), record.rank(), record.theme(), record.remainingTicks(),
					GateRegistry.awake(server, record)));
		}

		return new MapPayload(cities, gates);
	}
}
