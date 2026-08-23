package com.luca.arise.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * La Quest Giornaliera e la Zona di Penalità.
 *
 * <p>Nel canone sono cento flessioni, cento addominali, cento squat e dieci chilometri di corsa —
 * e se il giorno scade con anche un solo obiettivo aperto, il Sistema ti <strong>sposta</strong>:
 * ti ritrovi in un deserto senza sole né stelle, con una sola quest, <em>sopravvivi</em>.
 *
 * <p>Il pezzo di design che conta, e la ragione per cui questi numeri sono qui invece che in un
 * commento: <strong>la penalità non toglie niente.</strong> Non leva livelli, non brucia
 * l'inventario, non cancella l'esercito. Costa otto minuti e la faccia. È il momento in cui il
 * Sistema smette di essere un'interfaccia e diventa un carceriere, e una penalità che
 * <em>sottraesse</em> sarebbe la ricetta per disinstallare la mod invece che per ricordarsela.
 *
 * <p>Il giorno è un <strong>giorno di Minecraft</strong> e non un giorno vero: rispetta il ritmo
 * del gioco, e non punisce chi la sera chiude la partita.
 */
public record DailyConfig(
		/** Se la giornaliera esiste. Spenta, il Sistema non chiede niente ogni mattina. */
		boolean enabled,
		/** Quanti blocchi scavare. Le cento flessioni. */
		int blocks,
		/** Quanti colpi mettere a segno. I cento addominali. */
		int hits,
		/** Quanti salti. I cento squat. */
		int jumps,
		/** Quanti blocchi percorrere di corsa. I dieci chilometri. */
		int sprint,
		/** Punti statistica per una giornata chiusa. */
		int reward,
		/**
		 * A quale frazione del giorno trascorsa arriva l'avviso, se manca ancora qualcosa.
		 *
		 * <p>Tre quarti: abbastanza tardi da non essere un promemoria continuo, abbastanza presto da
		 * lasciare un quarto di giornata per rimediare.
		 */
		double warnAt) {

	public static final Codec<DailyConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("enabled").forGetter(DailyConfig::enabled),
			Codec.INT.fieldOf("blocks").forGetter(DailyConfig::blocks),
			Codec.INT.fieldOf("hits").forGetter(DailyConfig::hits),
			Codec.INT.fieldOf("jumps").forGetter(DailyConfig::jumps),
			Codec.INT.fieldOf("sprint").forGetter(DailyConfig::sprint),
			Codec.INT.fieldOf("reward").forGetter(DailyConfig::reward),
			Codec.DOUBLE.fieldOf("warn_at").forGetter(DailyConfig::warnAt)
	).apply(instance, DailyConfig::new));

	/**
	 * Cento, cento, cento e mille, come nel canone — tranne i chilometri, che sono blocchi.
	 *
	 * <p>Sono numeri che una giornata di gioco normale copre quasi da sola: chi scava, combatte e
	 * cammina li fa senza accorgersene. Devono essere <strong>un promemoria di giocare</strong>, non
	 * un secondo lavoro — la penalità deve capitare a chi ha passato la giornata fermo in una
	 * fattoria, non a chi ha giocato.
	 */
	public static final DailyConfig DEFAULT =
			new DailyConfig(true, 100, 100, 100, 1000, 1, 0.75);
}
