package com.luca.arise.daily;

import com.luca.arise.config.DailyConfig;

import net.minecraft.network.chat.Component;

/**
 * I quattro obiettivi di una giornata.
 *
 * <p>Cento flessioni, cento addominali, cento squat e dieci chilometri, tradotti nei soli quattro
 * gesti che Minecraft sa gia' contare da solo: scavare, colpire, saltare, correre. La traduzione
 * non e' poetica — sono <em>esattamente</em> le quattro cose che un Cacciatore fa in una giornata
 * qualunque, ed e' il punto: la giornaliera deve essere un promemoria di giocare, non un secondo
 * lavoro.
 *
 * <p>Un enum in un file suo e non annidato in {@code DailyQuest}: e' la stessa regola che vale per
 * {@code AbyssRule} e per {@code MobAffix}, e la ragione e' pratica prima che estetica — il collaudo
 * statico verifica le chiavi composte leggendo i file degli enum, e un enum annidato non lo vede.
 */
public enum DailyTask {

	/** Le cento flessioni. */
	BLOCKS("blocks"),

	/** I cento addominali: colpi <em>a segno</em>, non fendenti a vuoto. */
	HITS("hits"),

	/** I cento squat. */
	JUMPS("jumps"),

	/** I dieci chilometri, contati in blocchi e solo di corsa. */
	SPRINT("sprint");

	private final String name;

	DailyTask(String name) {
		this.name = name;
	}

	public String getSerializedName() {
		return name;
	}

	public Component label() {
		return Component.translatable("arise.daily." + name);
	}

	/** Quanto ne serve, secondo la config. */
	public int target(DailyConfig config) {
		return switch (this) {
			case BLOCKS -> config.blocks();
			case HITS -> config.hits();
			case JUMPS -> config.jumps();
			case SPRINT -> config.sprint();
		};
	}
}
