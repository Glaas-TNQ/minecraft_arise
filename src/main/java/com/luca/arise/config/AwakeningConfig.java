package com.luca.arise.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * La prima ora di gioco: il risveglio, la Sala e i suggerimenti.
 *
 * <p>Sta dentro {@link HunterConfig} e non nella radice per il motivo scritto la': il codec di un
 * record si ferma a sedici campi e {@link AriseConfig} ci e' gia' arrivata. Ma ci sta bene anche
 * per senso — di tutto quello che questa mod fa, il risveglio e' la cosa che riguarda il Cacciatore
 * piu' da vicino: e' il momento in cui lo diventa.
 *
 * <p><strong>Tutto si puo' spegnere.</strong> Chi rifa' la catena degli incarichi per la terza
 * volta non vuole rivedere la Sala, e chi installa la mod su un server dove i giocatori si aiutano
 * fra loro non ha bisogno dei suggerimenti. Due booleani, e il gioco torna quello di prima senza
 * togliere una riga di codice.
 *
 * @param sanctum     se il risveglio porta nella Sala invece di limitarsi a rimettere in piedi
 * @param sanctumX    dove sta la Sala nella dimensione dei varchi
 * @param sanctumZ    l'altra coordinata
 * @param floorY      la quota del pavimento della Sala
 * @param radius      mezzo lato interno: la Sala e' un quadrato di {@code 2*radius+1}
 * @param height      altezza interna
 * @param delayTicks  quanto passa fra il colpo che non uccide e il buio che porta via
 * @param graceTicks  per quanto si e' protetti al rientro nel mondo
 * @param welcomeSouls l'acconto del Sistema, per vedere il contatore muoversi la prima volta
 * @param hints       se ogni sistema concesso spiega anche come si usa
 */
public record AwakeningConfig(
		boolean sanctum,
		int sanctumX,
		int sanctumZ,
		int floorY,
		int radius,
		int height,
		int delayTicks,
		int graceTicks,
		long welcomeSouls,
		boolean hints) {

	public static final Codec<AwakeningConfig> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.BOOL.fieldOf("sanctum").forGetter(AwakeningConfig::sanctum),
			Codec.INT.fieldOf("sanctum_x").forGetter(AwakeningConfig::sanctumX),
			Codec.INT.fieldOf("sanctum_z").forGetter(AwakeningConfig::sanctumZ),
			Codec.INT.fieldOf("floor_y").forGetter(AwakeningConfig::floorY),
			Codec.INT.fieldOf("radius").forGetter(AwakeningConfig::radius),
			Codec.INT.fieldOf("height").forGetter(AwakeningConfig::height),
			Codec.INT.fieldOf("delay_ticks").forGetter(AwakeningConfig::delayTicks),
			Codec.INT.fieldOf("grace_ticks").forGetter(AwakeningConfig::graceTicks),
			Codec.LONG.fieldOf("welcome_souls").forGetter(AwakeningConfig::welcomeSouls),
			Codec.BOOL.fieldOf("hints").forGetter(AwakeningConfig::hints)
	).apply(instance, AwakeningConfig::new));

	/**
	 * La Sala sta a ovest dell'origine, i varchi a est.
	 *
	 * <p>Le istanze dei Gate occupano {@code indice * regionSpacing} con l'indice che parte da
	 * zero, quindi tutto il semiasse negativo e' libero e resta libero comunque cresca il numero di
	 * giocatori. Meta' del passo fra due regioni e' abbastanza lontano da qualunque cosa: se un
	 * giorno la Sala si trovasse dentro un dungeon, il difetto sarebbe questo numero — e c'e' una
	 * prova che lo guarda.
	 */
	public static final AwakeningConfig DEFAULT =
			new AwakeningConfig(true, -4096, 0, 64, 9, 7, 45, 300, 50L, true);

	/** Il lato interno della Sala, muri esclusi. */
	public int side() {
		return radius * 2 + 1;
	}
}
