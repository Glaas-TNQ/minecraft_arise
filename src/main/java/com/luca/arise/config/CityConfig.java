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
		/**
		 * Coordinata X del <strong>centro dell'anello</strong> su cui stanno le cinque città.
		 *
		 * <p>Lontano dallo spawn: nessuno costruisce laggiù.
		 */
		int originX,
		/** Coordinata Z del centro dell'anello. */
		int originZ,
		/**
		 * Raggio dell'anello: quanto ogni città dista dal centro della costellazione.
		 *
		 * <p>Prima le città stavano <strong>in fila</strong>: stessa Z, X a distanza fissa. Nessuno
		 * se ne era accorto finché non è esistita una mappa da guardare, e sulla mappa erano cinque
		 * punti allineati come i buchi di una scheda perforata — che è l'aspetto di una cosa
		 * generata, non di un mondo. Su un anello ogni città ha una direzione sua, le distanze fra
		 * l'una e l'altra non sono più tutte uguali, e il viaggio fra due Associazioni comincia ad
		 * avere una geografia.
		 *
		 * <p>Ventimila blocchi: le città adiacenti restano a circa ventitremila l'una dall'altra,
		 * poco più di prima, e le opposte a quarantamila.
		 */
		int ringRadius,
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
		 * Quanti millisecondi al massimo può durare un battito di costruzione.
		 *
		 * <p>È questo, e non {@code blocksPerTick}, la voce che tiene in piedi il server. Un blocco
		 * che cade in un chunk mai generato non costa un blocco: costa un <em>chunk</em>, cioè fra
		 * un decimo e mezzo secondo. Contare i blocchi non dice niente sul tempo, e un solo battito
		 * da sessanta secondi fa dichiarare il server bloccato dal watchdog.
		 *
		 * <p>Otto millisecondi su cinquanta: la costruzione si prende un sesto del battito e lascia
		 * il resto al gioco. Alzarlo velocizza le città e fa scattare tutto il resto.
		 */
		int msPerTick,
		/**
		 * Se all'avvio del server si verifica che le città ci siano, e si costruiscono quelle che
		 * mancano.
		 *
		 * <p>Le città nascono con i chunk, quindi su un mondo nuovo qui non c'è niente da fare. È
		 * la rete di sicurezza per i mondi dove quel terreno esisteva già prima della mod: lì il
		 * generatore non passa più, e il cantiere a budget le posa blocco per blocco. Chi le vuole
		 * costruire quando decide lui lo spegne qui, e resta {@code /arise city build}.
		 */
		boolean autoBuild,
		/** Il Quartiere del Mercato: le nove botteghe e la Moneta d'Anima. */
		MarketConfig market,
		/**
		 * Quanto si aspetta fra due usi del Sigillo dell'Associazione.
		 *
		 * <p>Cinque minuti. Le citta' stanno a duecentomila blocchi dallo spawn e a decine di
		 * migliaia l'una dall'altra sull'anello: senza un modo di raggiungerle il viaggio fra Associazioni e' una rete
		 * il cui primo nodo non si raggiunge, e con un modo <em>istantaneo</em> il mondo smette di
		 * avere distanze. L'attesa e' cio' che tiene in piedi tutte e due le cose.
		 */
		int sealCooldownTicks) {

	public static final CityConfig DEFAULT =
			new CityConfig(200000, 200000, 20000, 512, 32, 6, 60000, 8, true, MarketConfig.DEFAULT,
					6000);

	public static final Codec<CityConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("origin_x").forGetter(CityConfig::originX),
			Codec.INT.fieldOf("origin_z").forGetter(CityConfig::originZ),
			Codec.INT.fieldOf("ring_radius").forGetter(CityConfig::ringRadius),
			Codec.INT.fieldOf("size").forGetter(CityConfig::size),
			Codec.INT.fieldOf("block_size").forGetter(CityConfig::blockSize),
			Codec.INT.fieldOf("road_width").forGetter(CityConfig::roadWidth),
			Codec.INT.fieldOf("blocks_per_tick").forGetter(CityConfig::blocksPerTick),
			Codec.INT.fieldOf("ms_per_tick").forGetter(CityConfig::msPerTick),
			Codec.BOOL.fieldOf("auto_build").forGetter(CityConfig::autoBuild),
			MarketConfig.CODEC.fieldOf("market").forGetter(CityConfig::market),
			Codec.INT.fieldOf("seal_cooldown_ticks").forGetter(CityConfig::sealCooldownTicks)
	).apply(instance, CityConfig::new));

	/**
	 * Di quanto è ruotata la costellazione.
	 *
	 * <p>Venticinque gradi, ed è un numero scelto per esclusione. Le città sono cinque, quindi
	 * stanno a settantadue gradi l'una dall'altra: con lo sfasamento a zero la prima cade
	 * esattamente a est del centro, con diciotto la seconda cade esattamente a nord. Una
	 * costellazione con anche un solo punto perfettamente su un asse è la cosa che fa sembrare tutto
	 * disegnato con un righello — che è il difetto da cui l'anello è nato. Venticinque non allinea
	 * nessuna delle cinque.
	 *
	 * <p>C'è una prova che lo verifica, e non è pedanteria: è esattamente il genere di dettaglio che
	 * un giorno qualcuno arrotonda a zero per farlo sembrare più pulito.
	 */
	private static final double RING_PHASE = Math.toRadians(25.0);

	/** Dove sta una città sull'anello, in radianti. */
	private double angle(City city) {
		return RING_PHASE + city.index() * (2.0 * Math.PI / City.values().length);
	}

	/** Il centro della città: dove sta la piazza, e quindi l'Associazione. */
	public int centreX(City city) {
		return originX + (int) Math.round(Math.cos(angle(city)) * ringRadius);
	}

	public int centreZ(City city) {
		return originZ + (int) Math.round(Math.sin(angle(city)) * ringRadius);
	}

	/** L'angolo nord-ovest della città: il centro, meno mezzo lato. */
	public int cityX(City city) {
		return centreX(city) - size / 2;
	}

	public int cityZ(City city) {
		return centreZ(city) - size / 2;
	}

	/** Lato interno di un isolato, strada esclusa. */
	public int lotSize() {
		return blockSize - roadWidth;
	}
}
