package com.luca.arise.shadow;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;

/**
 * Le ombre che hanno un nome.
 *
 * <p>Un esercito di trenta ombre estratte a caso e' una lista ordinata per potenza: si prendono le
 * prime quattro e non c'e' nient'altro da decidere. Sette ombre <strong>uniche</strong> la
 * trasformano in una collezione da comporre, e la regola che le rende tali e' una sola, ripetuta
 * qui perche' e' facile perderla di vista:
 *
 * <p><strong>Un'ombra nominata non e' statisticamente migliore. E' unica in cio' che fa.</strong>
 *
 * <p>Iron non e' un Colosso con piu' vita: e' il Colosso la cui provocazione arriva al doppio della
 * distanza, e per questo vale un posto in squadra anche quando ce ne sono di piu' forti. Bellion
 * non ha numeri fuori scala: ha l'unica aura che vale per tutto l'esercito invece che per chi e' in
 * campo. Se un giorno una di queste sette diventasse semplicemente "la piu' forte", avrebbe smesso
 * di fare il suo lavoro.
 *
 * <p>Ognuna si ottiene <strong>una volta sola per giocatore</strong>, da una condizione precisa che
 * il resto del gioco non produce per caso: {@link com.luca.arise.gate.GateManager} le concede alla
 * caduta del Sovrano di un varco che soddisfi tema e rango. Non compaiono nel bottino, non si
 * comprano, non si estraggono da un cadavere qualunque.
 *
 * <p>Sullo stampo di {@code GearUnique}, che fa la stessa cosa per l'equipaggiamento.
 */
public enum NamedShadow implements StringRepresentable {

	/**
	 * Il cavaliere rosso, la prima ombra vera.
	 *
	 * <p>Nasce gia' forte perche' non e' un premio di percorso, e' un rito di passaggio: arriva
	 * quando il Cacciatore chiude il suo primo varco di rango C, ed e' il momento in cui l'esercito
	 * smette di essere fatto di zombie. Al grado di Maresciallo <em>parla</em> — una riga sola,
	 * quando lo si chiama, ed e' l'unica volta che qualcosa dell'esercito dice qualcosa.
	 */
	IGRIS("igris", ShadowArchetype.GUARD, 0xFFE05A5A, 12, 90.0, 22.0, "wither_skeleton"),

	/**
	 * Il muro. Estratto dal cadavere di un Cacciatore caduto in un varco sigillato.
	 *
	 * <p>La sua provocazione arriva al doppio della distanza di quella di qualunque altro Colosso:
	 * dodici blocchi diventano ventiquattro, ed e' la differenza fra tenere una stanza e tenere
	 * una sala. E' anche l'unica risposta seria al difetto classico delle build a evocazioni —
	 * i nemici che ignorano i minion e corrono in faccia a chi li comanda.
	 */
	IRON("iron", ShadowArchetype.TANK, 0xFF9BA8B8, 10, 140.0, 14.0, "iron_golem"),

	/**
	 * L'orso bianco alfa. Lento, enorme, e non lo sposta niente.
	 *
	 * <p>Contraccolpo azzerato del tutto, non ridotto: gli si puo' sparare addosso con un arco
	 * incantato e resta dov'e'. In un gioco dove ogni colpo spinge indietro, un'ombra che non si
	 * muove e' una pedina che si puo' <em>piazzare</em>.
	 */
	TANK("tank", ShadowArchetype.BEAST, 0xFFBFE8F0, 8, 110.0, 16.0, "polar_bear"),

	/**
	 * Lo sciamano orco. La sua lancia d'ombra parte da ventiquattro blocchi invece di sedici.
	 *
	 * <p>Otto blocchi in piu' non sembrano molto finche' non si nota che sono la differenza fra
	 * colpire dall'ingresso di una sala e doverci entrare.
	 */
	TUSK("tusk", ShadowArchetype.MAGE, 0xFF8FD9A0, 10, 70.0, 24.0, "evoker"),

	/**
	 * L'orco avido. Raddoppia i soul coin che l'esercito raccoglie mentre e' in campo.
	 *
	 * <p>E' l'unica ombra che paga invece di combattere, e la sola ragione per portarsi dietro un
	 * posto in meno di potenza. Chi la tiene in squadra sta facendo una scelta economica.
	 */
	GREED("greed", ShadowArchetype.GUARD, 0xFFFFD54F, 14, 100.0, 20.0, "piglin_brute"),

	/**
	 * Il Re delle Formiche. Cura il Monarca quando scende sotto meta' vita.
	 *
	 * <p>La sola ombra che guarda <em>te</em> invece del nemico, e la piu' difficile da ottenere:
	 * il Sovrano di un varco Sculk di rango S. In una mod dove non esistono classi di supporto,
	 * Beru e' la classe di supporto.
	 */
	BERU("beru", ShadowArchetype.BEAST, 0xFF4FBF9F, 20, 160.0, 34.0, "ravager"),

	/**
	 * Il Gran Maresciallo. Non si estrae: si eredita.
	 *
	 * <p>La sua aura di comando e' l'unica che vale per <strong>tutto</strong> l'esercito e non
	 * solo per chi e' in campo — cioe' l'unica cosa in tutta la mod che dia un motivo alle
	 * ventisei ombre che restano a casa. E' anche il "hai finito" reso visibile, e per questo non
	 * cade da nessun boss: arriva alla fine della catena, da chi l'ha comandata prima di te.
	 */
	BELLION("bellion", ShadowArchetype.TANK, 0xFF8E6BFF, 30, 240.0, 48.0, "wither_skeleton");

	public static final Codec<NamedShadow> CODEC = StringRepresentable.fromEnum(NamedShadow::values);

	/** Quanto lontano provoca Iron, rispetto a un Colosso qualunque. */
	public static final double IRON_TAUNT_FACTOR = 2.0;

	/** Da quanto lontano tira Tusk, in blocchi. */
	public static final double TUSK_LANCE_RANGE = 24.0;

	/** Quanto Greed moltiplica i soul coin raccolti mentre e' in campo. */
	public static final double GREED_SOUL_FACTOR = 2.0;

	/** Sotto quale frazione di vita Beru interviene, e quanta ne restituisce per volta. */
	public static final float BERU_THRESHOLD = 0.5F;
	public static final float BERU_HEAL = 2.0F;

	/** Ogni quanti tick Beru puo' curare. Un cuore ogni cinque secondi, non una fontana. */
	public static final int BERU_INTERVAL = 100;

	/** Da quanto lontano Beru puo' curare. Oltre, sarebbe rigenerazione passiva travestita. */
	public static final double BERU_REACH = 24.0;

	private final String name;
	private final ShadowArchetype archetype;
	private final int color;
	private final int level;
	private final double health;
	private final double damage;
	private final String sourcePath;

	NamedShadow(String name, ShadowArchetype archetype, int color, int level,
			double health, double damage, String sourcePath) {
		this.name = name;
		this.archetype = archetype;
		this.color = color;
		this.level = level;
		this.health = health;
		this.damage = damage;
		this.sourcePath = sourcePath;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public ShadowArchetype archetype() {
		return archetype;
	}

	public int color() {
		return color;
	}

	/** Il livello a cui nasce: non parte da uno, perche' non e' un'ombra come le altre. */
	public int level() {
		return level;
	}

	public double health() {
		return health;
	}

	public double damage() {
		return damage;
	}

	/**
	 * Il mob a cui la si appoggia.
	 *
	 * <p>Conta poco — l'ombra si disegna col suo modello e porta un nome proprio — ma
	 * {@code ShadowData} ha bisogno di un id valido, e appoggiarla a qualcosa di sensato significa
	 * che se un giorno il renderer distinguesse le sagome, queste sarebbero gia' giuste.
	 */
	public Identifier sourceType() {
		return Identifier.withDefaultNamespace(sourcePath);
	}

	public Component label() {
		return Component.translatable("arise.named." + name).withStyle(style -> style.withColor(color));
	}

	/** Cosa fa, in una riga. Compare nel dettaglio dell'ombra e nel messaggio di acquisizione. */
	public Component description() {
		return Component.translatable("arise.named." + name + ".desc");
	}

	/** La riga che Igris dice, e che nessun'altra ombra ha. */
	public Component line() {
		return Component.translatable("arise.named." + name + ".line")
				.withStyle(ChatFormatting.ITALIC, ChatFormatting.RED);
	}

	/** L'ombra appena consegnata: nome fisso, colore fisso, livello e statistiche sue. */
	public ShadowData create() {
		return new ShadowData(java.util.UUID.randomUUID(), sourceType(), archetype, level, 0L,
				health, damage, java.util.Optional.empty(), color, java.util.Optional.of(this));
	}
}
