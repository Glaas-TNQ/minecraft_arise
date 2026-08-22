package com.luca.arise.config;

import com.luca.arise.city.City;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Dove stanno le città e quanto in fretta si costruiscono.
 *
 * <p>{@code blocksPerTick} è la voce che conta davvero: una città è quasi un milione di blocchi, e
 * piazzarli tutti in una volta bloccherebbe il server per minuti — con il client che nel frattempo
 * dichiara la partita morta. Il costruttore ne mette un tot per battito e riprende al successivo.
 * Alzarlo va più veloce e fa scattare il gioco; abbassarlo lo rende impercettibile e lento.
 */
public record CityConfig(
		/** Coordinata X della prima città. Lontano dallo spawn: nessuno costruisce laggiù. */
		int originX,
		/** Coordinata Z di tutte le città: stanno in fila. */
		int originZ,
		/** Distanza fra una città e la successiva. */
		int spacing,
		/** Lato della città, in blocchi. */
		int size,
		/** Passo della griglia: isolato più strada. */
		int blockSize,
		/** Larghezza della carreggiata. */
		int roadWidth,
		/** Quanti blocchi si piazzano per battito del server. */
		int blocksPerTick) {

	public static final CityConfig DEFAULT = new CityConfig(200000, 200000, 15000, 112, 28, 6, 24000);

	public static final Codec<CityConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("origin_x").forGetter(CityConfig::originX),
			Codec.INT.fieldOf("origin_z").forGetter(CityConfig::originZ),
			Codec.INT.fieldOf("spacing").forGetter(CityConfig::spacing),
			Codec.INT.fieldOf("size").forGetter(CityConfig::size),
			Codec.INT.fieldOf("block_size").forGetter(CityConfig::blockSize),
			Codec.INT.fieldOf("road_width").forGetter(CityConfig::roadWidth),
			Codec.INT.fieldOf("blocks_per_tick").forGetter(CityConfig::blocksPerTick)
	).apply(instance, CityConfig::new));

	/** L'angolo nord-ovest della città. */
	public int cityX(City city) {
		return originX + city.index() * spacing;
	}

	public int cityZ(City city) {
		return originZ;
	}

	/** Il centro della città: dove sta la piazza, e quindi l'Associazione. */
	public int centreX(City city) {
		return cityX(city) + size / 2;
	}

	public int centreZ(City city) {
		return cityZ(city) + size / 2;
	}

	/** Lato interno di un isolato, strada esclusa. */
	public int lotSize() {
		return blockSize - roadWidth;
	}
}
