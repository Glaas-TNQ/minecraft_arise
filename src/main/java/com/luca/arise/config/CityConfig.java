package com.luca.arise.config;

import com.luca.arise.city.City;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Dove stanno le città e quanto in fretta si costruiscono.
 *
 * <p>{@code blocksPerTick} è la voce che conta davvero: una città è più di dieci milioni di
 * blocchi, e piazzarli tutti in una volta bloccherebbe il server per minuti — con il client che nel
 * frattempo dichiara la partita morta. Il costruttore ne mette un tot per battito e riprende al
 * successivo. Alzarlo va più veloce e fa scattare il gioco; abbassarlo lo rende impercettibile e
 * lento. Le cinque città sono costruite una alla volta proprio perché questo numero valga sempre,
 * e non cinque volte tanto quando si tira su un mondo da zero.
 *
 * <p>{@code size} e {@code blockSize} sono la <em>griglia di base</em>, non la misura finale: ogni
 * città la riscala secondo il suo stile, così Tokyo resta fitta e Roma larga anche dopo che si è
 * cambiato un numero solo qui.
 */
public record CityConfig(
		/** Coordinata X della prima città. Lontano dallo spawn: nessuno costruisce laggiù. */
		int originX,
		/** Coordinata Z di tutte le città: stanno in fila. */
		int originZ,
		/** Distanza fra una città e la successiva. */
		int spacing,
		/**
		 * Lato della città, in blocchi.
		 *
		 * <p>Cinquecentododici: due volte e mezzo la superficie di prima. È il numero che governa
		 * tutto il resto — quanti isolati, quanto è lontano il monumento dal mercato, quanto si
		 * cammina — e le cinque piante si riscalano da sole quando cambia. Il prezzo è il tempo di
		 * costruzione: mille chunk per città invece di quattrocento.
		 */
		int size,
		/** Passo della griglia: isolato più strada. */
		int blockSize,
		/** Larghezza della carreggiata. */
		int roadWidth,
		/** Quanti blocchi si piazzano per battito del server. */
		int blocksPerTick,
		/**
		 * Se le città si tirano su da sole la prima volta che qualcuno entra in un mondo.
		 *
		 * <p>Acceso di default: un mondo dove le cinque Associazioni non esistono è un mondo dove
		 * metà dei sistemi non ha un posto dove succedere. Chi le vuole costruire quando decide lui
		 * lo spegne qui, e resta {@code /arise city build}.
		 */
		boolean autoBuild,
		/** Il Quartiere del Mercato: le nove botteghe e la Moneta d'Anima. */
		MarketConfig market) {

	public static final CityConfig DEFAULT =
			new CityConfig(200000, 200000, 15000, 512, 32, 6, 60000, true, MarketConfig.DEFAULT);

	public static final Codec<CityConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("origin_x").forGetter(CityConfig::originX),
			Codec.INT.fieldOf("origin_z").forGetter(CityConfig::originZ),
			Codec.INT.fieldOf("spacing").forGetter(CityConfig::spacing),
			Codec.INT.fieldOf("size").forGetter(CityConfig::size),
			Codec.INT.fieldOf("block_size").forGetter(CityConfig::blockSize),
			Codec.INT.fieldOf("road_width").forGetter(CityConfig::roadWidth),
			Codec.INT.fieldOf("blocks_per_tick").forGetter(CityConfig::blocksPerTick),
			Codec.BOOL.fieldOf("auto_build").forGetter(CityConfig::autoBuild),
			MarketConfig.CODEC.fieldOf("market").forGetter(CityConfig::market)
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
