package com.luca.arise.workshop;

import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import io.netty.buffer.ByteBuf;

/**
 * I quattro macchinari dell'Officina, e la forma delle loro caselle.
 *
 * <p>Un enum invece di quattro classi di blocco perche' i quattro condividono tutto tranne il
 * lavoro che fanno: stesso blocco, stessa {@code BlockEntity}, stesso menu, stessa schermata.
 * Quello che cambia e' quante caselle ci sono e cosa succede quando il contatore arriva in fondo.
 * Quattro gerarchie parallele per esprimere quella differenza sarebbero costate quattro volte il
 * codice per la stessa cosa.
 *
 * <p><strong>L'ordine dei gruppi e' l'indirizzo delle caselle nei pacchetti</strong> — anime,
 * catalizzatore, ingressi, uscite — quindi non si tocca, esattamente come nel menu del Cacciatore.
 *
 * @param souls    caselle per le anime: operaie ovunque tranne nel Crogiolo, dove sono la materia
 * @param catalyst se c'e' la casella del catalizzatore
 * @param inputs   caselle di ingresso per gli oggetti da lavorare
 * @param outputs  caselle di uscita, da cui si prende e basta
 */
public enum MachineKind implements StringRepresentable {

	/** Il Richiamo d'Anime: le operaie chiamano altre anime dall'Abisso. */
	LURE("soul_lure", 2, false, 0, 3),

	/** Il Crogiolo: quattro anime piu' un catalizzatore diventano un'anima sola. */
	CRUCIBLE("soul_crucible", 4, true, 0, 1),

	/** La Fucina d'Ombra: fonde e macina senza carburante, finche' ci sono operaie. */
	FORGE("shadow_forge", 3, false, 1, 1),

	/** Il Pozzo dell'Abisso: le operaie rendono soul coin e, ogni tanto, un catalizzatore. */
	WELL("abyss_well", 4, false, 0, 1);

	public static final Codec<MachineKind> CODEC = StringRepresentable.fromEnum(MachineKind::values);

	/** Per l'apertura del menu: quattro voci, un byte basterebbe. */
	public static final StreamCodec<ByteBuf, MachineKind> STREAM_CODEC = ByteBufCodecs.VAR_INT.map(
			index -> values()[Math.clamp(index, 0, values().length - 1)], MachineKind::ordinal);

	private final String path;
	private final int souls;
	private final boolean catalyst;
	private final int inputs;
	private final int outputs;

	MachineKind(String path, int souls, boolean catalyst, int inputs, int outputs) {
		this.path = path;
		this.souls = souls;
		this.catalyst = catalyst;
		this.inputs = inputs;
		this.outputs = outputs;
	}

	@Override
	public String getSerializedName() {
		return path;
	}

	/** Il percorso dell'identificatore: {@code arise:soul_lure} e compagnia. */
	public String path() {
		return path;
	}

	public int souls() {
		return souls;
	}

	public boolean hasCatalyst() {
		return catalyst;
	}

	public int inputs() {
		return inputs;
	}

	public int outputs() {
		return outputs;
	}

	public int containerSize() {
		return souls + (catalyst ? 1 : 0) + inputs + outputs;
	}

	// ---------------------------------------------------------------- indirizzi

	public int firstSoul() {
		return 0;
	}

	/** L'indice della casella del catalizzatore, o {@code -1} se questo macchinario non l'ha. */
	public int catalystSlot() {
		return catalyst ? souls : -1;
	}

	public int firstInput() {
		return souls + (catalyst ? 1 : 0);
	}

	public int firstOutput() {
		return firstInput() + inputs;
	}

	public boolean isSoulSlot(int index) {
		return index >= 0 && index < souls;
	}

	public boolean isOutputSlot(int index) {
		return index >= firstOutput() && index < containerSize();
	}

	/**
	 * Le anime del Crogiolo si consumano, quelle di tutti gli altri no.
	 *
	 * <p>E' la regola che tiene in piedi l'idea di fondo: un'anima messa a lavorare non e' un
	 * ingrediente, e' un'operaia. La si rivuole indietro quando serve altrove.
	 */
	public boolean consumesSouls() {
		return this == CRUCIBLE;
	}

	public Component label() {
		return Component.translatable("block.arise." + path);
	}

	public Component hint() {
		return Component.translatable("arise.machine." + path + ".hint");
	}
}
