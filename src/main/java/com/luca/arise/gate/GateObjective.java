package com.luca.arise.gate;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;

/**
 * Perche' si e' entrati in questo varco.
 *
 * <p>Fino a ieri la risposta era sempre la stessa — uccidi il Sovrano — e per questo ogni Gate si
 * percorreva allo stesso modo: dritti in fondo, saltando tutto. La pianta era generata, il tema
 * cambiava tre blocchi, ma la <em>strada</em> era una sola.
 *
 * <p>Tre obiettivi sullo stesso generatore fanno tre giochi, ed e' il miglior rapporto
 * lavoro/varieta' disponibile — e' la lezione dei Vault di Vault Hunters, dove con un obiettivo
 * apri ogni forziere e con un altro corri. Qui:
 *
 * <ul>
 *   <li><strong>Il Sovrano</strong> — quello di sempre: si corre in fondo.
 *   <li><strong>La Raccolta d'Essenza</strong> — si apre tutto. Il Sovrano diventa facoltativo, e
 *       cio' che conta e' aver ripulito le stanze: e' l'obiettivo che paga l'esplorazione.
 *   <li><strong>La Caccia</strong> — il contrario esatto: il Sovrano, ma <em>in fretta</em>. Ogni
 *       stanza ripulita e' tempo che non si ha, e per la prima volta scappare e' la mossa giusta.
 * </ul>
 *
 * <p>L'obiettivo si tira dal <strong>seme</strong>, come tutto il resto del preventivo, e si legge
 * nel pannello di analisi prima di entrare. E' quello a renderlo una decisione invece di una
 * sorpresa: si guarda il varco, si vede che chiede fretta, e si decide se stasera si ha voglia di
 * correre.
 */
public enum GateObjective implements StringRepresentable {

	/** Uccidi il guardiano. L'uscita si apre quando cade. */
	SOVEREIGN("sovereign", 0xFFE8F2FF),

	/**
	 * Riempi la barra abbattendo cio' che abita il varco.
	 *
	 * <p>Il bersaglio e' una quota degli abitanti e non un numero fisso: un varco di rango S ne ha
	 * il triplo di uno di rango E, e chiedere venti uccisioni a entrambi vorrebbe dire chiedere
	 * tutto al primo e un terzo al secondo.
	 */
	ESSENCE("essence", 0xFF8E6BFF),

	/**
	 * Il Sovrano, entro il tempo.
	 *
	 * <p>Fallire non chiude niente: si perde il premio della fretta, e il varco torna a essere un
	 * Sovrano qualunque. Un obiettivo a tempo che, scaduto, ti chiude dentro sarebbe una punizione
	 * per aver scelto male prima di sapere.
	 */
	HUNT("hunt", 0xFFE8A34F);

	public static final Codec<GateObjective> CODEC =
			StringRepresentable.fromEnum(GateObjective::values);

	/**
	 * Quale quota degli abitanti va abbattuta per la Raccolta d'Essenza.
	 *
	 * <p>Tre quarti e non tutti: l'ultimo quarto sarebbe una caccia al mob incastrato in un angolo,
	 * che non e' contenuto — e' un difetto travestito da obiettivo.
	 */
	public static final double ESSENCE_SHARE = 0.75;

	/** Quanti tick dura la Caccia. Quattro minuti: si arriva in fondo, ma non passeggiando. */
	public static final int HUNT_TICKS = 4800;

	/** Quanto paga in piu' un obiettivo riuscito che non era il Sovrano. */
	public static final double BONUS = 0.5;

	private final String name;
	private final int color;

	GateObjective(String name, int color) {
		this.name = name;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public int color() {
		return color;
	}

	public Component label() {
		return Component.translatable("arise.objective." + name);
	}

	/** Cosa chiede, in una riga. Sta nel pannello di analisi, prima di entrare. */
	public Component description() {
		return Component.translatable("arise.objective." + name + ".desc");
	}

	/** Quante uccisioni servono in un varco con questi abitanti. Zero se non e' la Raccolta. */
	public int essenceTarget(int inhabitants) {
		return this == ESSENCE ? Math.max(1, (int) Math.round(inhabitants * ESSENCE_SHARE)) : 0;
	}

	/**
	 * L'obiettivo di un varco, tirato dal suo seme.
	 *
	 * <p>Il Sovrano pesa il doppio degli altri due messi insieme. Non e' timidezza: e' l'obiettivo
	 * che il giocatore conosce, quello che la catena degli incarichi gli ha insegnato, e quello che
	 * vuole quando ha in testa qualcos'altro. Gli altri due sono la variazione, e una variazione
	 * che capita una volta su due smette di essere tale.
	 */
	public static GateObjective roll(RandomSource random) {
		int roll = random.nextInt(4);

		return switch (roll) {
			case 0 -> ESSENCE;
			case 1 -> HUNT;
			default -> SOVEREIGN;
		};
	}
}
