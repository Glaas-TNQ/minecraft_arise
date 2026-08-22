package com.luca.arise.progress;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Il rango, da E a S. Vale per le ombre e per i Gate.
 *
 * <p>Per le ombre non è un dato salvato: si <em>ricava</em> dalla potenza, che a sua volta viene
 * dal mob d'origine. Così non c'è un secondo valore da tenere in sincronia, e ritoccare le soglie
 * in config riclassifica l'intero esercito senza migrazioni.
 */
public enum Rank implements StringRepresentable {
	E("e", ChatFormatting.GRAY, 0xFF9BA8B8),
	D("d", ChatFormatting.WHITE, 0xFFE8F2FF),
	C("c", ChatFormatting.GREEN, 0xFF7FD97F),
	B("b", ChatFormatting.AQUA, 0xFF4FC3F7),
	A("a", ChatFormatting.LIGHT_PURPLE, 0xFFC77FE8),
	S("s", ChatFormatting.GOLD, 0xFFFFD54F);

	/** Serve come chiave nelle mappe di config (tabelle mob per rango dei Gate). */
	public static final Codec<Rank> CODEC = StringRepresentable.fromEnum(Rank::values);

	private final String name;
	private final ChatFormatting chatColor;
	private final int color;

	Rank(String name, ChatFormatting chatColor, int color) {
		this.name = name;
		this.chatColor = chatColor;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** Colore ARGB per il disegno nella schermata. */
	public int color() {
		return color;
	}

	public Component label() {
		return Component.translatable("arise.rank." + name).withStyle(chatColor);
	}

	/**
	 * Il rango più alto la cui soglia è raggiunta dal punteggio.
	 *
	 * <p>Le soglie arrivano dalla config e sono ordinate dal rango più basso al più alto; se ne
	 * mancano o ne avanzano, si usa quel che c'è invece di andare in errore.
	 */
	public static Rank fromScore(double score, List<Double> thresholds) {
		Rank result = E;

		for (int i = 0; i < values().length && i < thresholds.size(); i++) {
			if (score >= thresholds.get(i)) {
				result = values()[i];
			}
		}

		return result;
	}
}
