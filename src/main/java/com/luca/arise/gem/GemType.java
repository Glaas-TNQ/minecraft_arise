package com.luca.arise.gem;

import java.util.List;

import com.luca.arise.progress.Stat;
import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Le gemme, e cosa fanno una volta incastonate.
 *
 * <p>Ogni gemma da' due cose: qualche statistica, come un modificatore in piu' sul pezzo, e un
 * <strong>effetto passivo</strong>, che e' il motivo per cui esistono. Le statistiche le sa gia'
 * dare l'equipaggiamento; quello che l'equipaggiamento non sa fare e' cambiare le regole.
 *
 * <p>Passivi e non abilita' attive, almeno per ora. Le abilita' sono un enum chiuso di quattro
 * voci legato a quattro tasti e a quattro caselle dell'HUD: aggiungerne una quinta e' un lavoro a
 * se'. Un passivo costa un decimo ed e' altrettanto interessante (design §8.6).
 *
 * <p>Ogni effetto e' agganciato a un momento che il codice della mod gia' intercetta — un colpo
 * inferto, un colpo incassato, un nemico che muore — e questo non e' un caso: un passivo che
 * richieda un mixin nuovo per esistere e' un passivo che aspetta.
 */
public enum GemType implements StringRepresentable {

	/** Ematite: una quota del danno inferto torna come vita. */
	HEMATITE("hematite", Stat.STRENGTH, Stat.VITALITY),
	/** Ossidiana: alla morte di un nemico, una probabilita' che l'ombra si estragga da sola. */
	OBSIDIAN("obsidian", Stat.ENDURANCE, Stat.TOUGHNESS),
	/** Zaffiro: piu' soul coin da ogni uccisione. */
	SAPPHIRE("sapphire", Stat.FORTUNE, Stat.HASTE),
	/** Ametista: piu' XP da ogni uccisione. */
	AMETHYST("amethyst", Stat.FORTUNE, Stat.REACH),
	/** Diaspro: una quota del danno incassato torna a chi l'ha inferto. */
	JASPER("jasper", Stat.IMPACT, Stat.RESOLVE);

	public static final Codec<GemType> CODEC = StringRepresentable.fromEnum(GemType::values);

	private final String name;
	private final List<Stat> affinities;

	GemType(String name, Stat... affinities) {
		this.name = name;
		this.affinities = List.of(affinities);
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public List<Stat> affinities() {
		return affinities;
	}

	public Component label() {
		return Component.translatable("arise.gem.type." + name);
	}

	/** La descrizione dell'effetto, col valore gia' dentro. */
	public Component describe(double magnitude) {
		return Component.translatable("arise.gem.effect." + name, format(magnitude));
	}

	/**
	 * Il valore come lo legge un giocatore.
	 *
	 * <p>Tutti e cinque gli effetti sono quote di qualcosa, quindi tutti si leggono in percentuale.
	 * Se un giorno ne arrivera' uno che non lo e', questa e' la riga da spezzare.
	 */
	private static String format(double magnitude) {
		return String.format("%.1f%%", magnitude * 100.0);
	}
}
