package com.luca.arise.mana;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Il Mana di un Cacciatore: quanto gliene resta, e da quando puo' tornare a crescere.
 *
 * <p>Tre numeri, e nessuno di essi e' il massimo. Il massimo non si salva: si <em>ricava</em> dal
 * livello ({@link ManaManager#max}), come tutto il resto della progressione. Salvarlo vorrebbe
 * dire avere due verita' sullo stesso fatto, e il giorno che si cambia la curva in config la
 * seconda resterebbe indietro in silenzio.
 *
 * <p>{@code busyUntil} e {@code lastRegen} sono <strong>tempi di gioco assoluti</strong>, non
 * conteggi di battiti. E' la stessa scelta della giornaliera, e per la stessa ragione: chi chiama
 * il battito, e con che ritmo, non deve poter cambiare quanto in fretta si rigenera il Mana. Se un
 * giorno il giro passasse da quattro volte al secondo a venti, qui non cambierebbe niente.
 *
 * @param current   quanto ne resta adesso
 * @param busyUntil prima di questo istante la rigenerazione e' ferma: e' il respiro dopo una spesa
 * @param lastRegen l'ultimo istante gia' contato, per non perdere i decimi fra un battito e l'altro
 */
public record Mana(int current, long busyUntil, long lastRegen) {

	/**
	 * Il valore d'esordio.
	 *
	 * <p>Meno uno, che non e' un Mana: e' «non lo so ancora». Un Cacciatore che riceve il Sistema
	 * deve trovarsi la riserva piena, e piena vuol dire quanto il suo livello concede — un numero
	 * che questo record non conosce. Il primo battito lo riempie ({@link ManaManager#tick}), e da
	 * li' in poi il valore e' vero.
	 */
	public static final Mana INITIAL = new Mana(-1, 0L, 0L);

	public static final Codec<Mana> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.INT.fieldOf("current").forGetter(Mana::current),
			Codec.LONG.optionalFieldOf("busy_until", 0L).forGetter(Mana::busyUntil),
			Codec.LONG.optionalFieldOf("last_regen", 0L).forGetter(Mana::lastRegen)
	).apply(instance, Mana::new));

	public static final StreamCodec<ByteBuf, Mana> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	/** Vero finche' il primo battito non ha riempito la riserva. */
	public boolean unset() {
		return current < 0;
	}

	public Mana with(int newCurrent, long now) {
		return new Mana(Math.max(0, newCurrent), busyUntil, now);
	}

	/** Dopo una spesa: la rigenerazione si ferma per un momento, e il conto riparte da adesso. */
	public Mana spent(int newCurrent, long now, int pauseTicks) {
		return new Mana(Math.max(0, newCurrent), now + Math.max(0, pauseTicks), now);
	}
}
