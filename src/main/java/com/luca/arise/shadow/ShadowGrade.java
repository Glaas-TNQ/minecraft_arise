package com.luca.arise.shadow;

import java.util.List;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Il grado di un'ombra: la scala del manhwa, da Normale a Gran Maresciallo.
 *
 * <p>Il {@link com.luca.arise.progress.Rank rango} E-S dice <em>da cosa e' stata estratta</em>, e
 * non cambia mai: e' la stessa etichetta dei Gate e dell'equipaggiamento, e serve a confrontare
 * cose diverse fra loro. Il grado dice invece <em>quanto vale adesso</em>, e sale combattendo. Sono
 * due assi diversi di proposito: un'ombra di rango D che ha fatto trenta livelli e' un Comandante,
 * e deve poterlo dire.
 *
 * <p>Come il rango, <strong>non e' un dato salvato</strong>: si ricava dalla potenza effettiva
 * (base × livello × archetipo). Nessun secondo valore da tenere in sincronia, e ritoccare le soglie
 * in config riclassifica l'intero esercito senza migrazioni.
 *
 * <p>Il grado non e' solo un'etichetta. Da Cavaliere in su un'ombra puo' ricevere un nome — nel
 * manhwa e' esattamente il privilegio che il Monarca concede ai suoi — e da Comandante in su
 * emana l'<em>aura di comando</em>, che rende piu' forti le altre ombre in campo. E' il motivo per
 * cui la squadra si compone invece di limitarsi a mandare fuori le quattro piu' grosse.
 */
public enum ShadowGrade implements StringRepresentable {
	NORMAL("normal", ChatFormatting.GRAY, 0xFF9BA8B8),
	ELITE("elite", ChatFormatting.WHITE, 0xFFE8F2FF),
	KNIGHT("knight", ChatFormatting.GREEN, 0xFF7FD97F),
	ELITE_KNIGHT("elite_knight", ChatFormatting.AQUA, 0xFF4FC3F7),
	COMMANDER("commander", ChatFormatting.BLUE, 0xFF6A7BE8),
	GENERAL("general", ChatFormatting.LIGHT_PURPLE, 0xFFC77FE8),
	MARSHAL("marshal", ChatFormatting.GOLD, 0xFFFFD54F),
	GRAND_MARSHAL("grand_marshal", ChatFormatting.RED, 0xFFE86A6A);

	public static final Codec<ShadowGrade> CODEC = StringRepresentable.fromEnum(ShadowGrade::values);

	private final String name;
	private final ChatFormatting chatColor;
	private final int color;

	ShadowGrade(String name, ChatFormatting chatColor, int color) {
		this.name = name;
		this.chatColor = chatColor;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public ChatFormatting chatColor() {
		return chatColor;
	}

	public Component label() {
		return Component.translatable("arise.grade." + name).withStyle(chatColor);
	}

	/**
	 * ARGB per il disegno nella schermata.
	 *
	 * <p>Scritto a mano e non ricavato da {@link ChatFormatting}: in 26.2 quell'enum non espone
	 * piu' il suo colore. E' la stessa coppia che tiene {@link com.luca.arise.progress.Rank}.
	 */
	public int color() {
		return color;
	}

	/**
	 * Puo' ricevere un nome proprio?
	 *
	 * <p>Da Cavaliere in su. Prima di allora un'ombra e' "Ombra di zombie" e basta: dare un nome a
	 * una recluta che fra dieci minuti sara' congedata non e' un privilegio, e' rumore. E' anche
	 * la regola del manhwa — i nomi arrivano col grado.
	 */
	public boolean canBeNamed() {
		return ordinal() >= KNIGHT.ordinal();
	}

	/**
	 * Quanti gradini sopra Comandante, contando Comandante come uno. Zero se non comanda.
	 *
	 * <p>E' il moltiplicatore dell'aura: un Comandante da' un'unita' di bonus, un Gran Maresciallo
	 * quattro.
	 */
	public int commandSteps() {
		return Math.max(0, ordinal() - COMMANDER.ordinal() + 1);
	}

	public boolean commands() {
		return commandSteps() > 0;
	}

	/**
	 * Il grado piu' alto la cui soglia e' raggiunta da questa potenza.
	 *
	 * <p>Stessa forma di {@code Rank.fromScore}, stessa tolleranza: se le soglie in config sono
	 * meno o piu' degli otto gradi, si usa quel che c'e' invece di andare in errore.
	 */
	public static ShadowGrade fromPower(double power, List<Double> thresholds) {
		ShadowGrade result = NORMAL;

		for (int i = 0; i < values().length && i < thresholds.size(); i++) {
			if (power >= thresholds.get(i)) {
				result = values()[i];
			}
		}

		return result;
	}
}
