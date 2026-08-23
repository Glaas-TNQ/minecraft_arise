package com.luca.arise.gate;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Quanto in fondo e' arrivato un Cacciatore, e quanto ci ha messo.
 *
 * <p>Due numeri, ed e' tutto quello che serve perche' l'Abisso funzioni. La <strong>profondita'
 * massima</strong> e' un traguardo che ne apre un altro — l'unica cosa che questa mod non aveva
 * dopo il diciottesimo incarico, quando il Sistema smetteva di chiedere. Il <strong>tempo
 * migliore</strong> e' il motore dell'«ancora una discesa»: senza cronometro un contenuto
 * ripetibile e' ripetizione, con il cronometro diventa una prova di ottimizzazione, e la differenza
 * fra le due cose e' tutta la differenza fra un endgame e un grind.
 *
 * @param deepest la profondita' piu' alta chiusa, zero se non si e' mai sceso
 * @param bestTicks quanto e' durata la discesa piu' veloce, zero se non ce n'e' ancora una
 */
public record AbyssRecord(int deepest, long bestTicks) {

	public static final Codec<AbyssRecord> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("deepest").forGetter(AbyssRecord::deepest),
			Codec.LONG.fieldOf("best_ticks").forGetter(AbyssRecord::bestTicks)
	).apply(instance, AbyssRecord::new));

	public static final StreamCodec<ByteBuf, AbyssRecord> STREAM_CODEC =
			ByteBufCodecs.fromCodec(CODEC);

	public static final AbyssRecord NONE = new AbyssRecord(0, 0L);

	/**
	 * A quale profondita' questo Cacciatore puo' scendere.
	 *
	 * <p>Una piu' in giu' della piu' bassa gia' chiusa, e nessun salto: l'Abisso e' una scala, non
	 * un menu di difficolta'. Scendere al quindicesimo senza aver visto il quattordicesimo
	 * significherebbe incontrare tre regole insieme senza averne imparata nessuna.
	 */
	public int next() {
		return deepest + 1;
	}

	/** Il record aggiornato dopo una discesa chiusa. Non peggiora mai. */
	public AbyssRecord with(int depth, long ticks) {
		int newDeepest = Math.max(deepest, depth);

		// Il tempo migliore vale per l'Abisso intero e non per profondita': tenerne uno per gradino
		// vorrebbe dire una tabella che cresce per sempre e una riga che nessuno guarda. Un numero
		// solo e' un numero che si ricorda — ed e' quello che si prova a battere.
		long newBest = bestTicks <= 0 || ticks < bestTicks ? ticks : bestTicks;

		return new AbyssRecord(newDeepest, newBest);
	}
}
