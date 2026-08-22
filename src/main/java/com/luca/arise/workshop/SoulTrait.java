package com.luca.arise.workshop;

import com.mojang.serialization.Codec;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Il tratto che un'anima guadagna uscendo dal Crogiolo.
 *
 * <p>È il motivo per cui vale la pena fondere invece che accumulare. Quattro anime scarse messe
 * insieme non danno solo un'anima meno scarsa: danno un'anima che sa fare <em>qualcosa</em>, e
 * quel qualcosa cambia in quale macchina conviene metterla.
 *
 * <p>Quattro tratti su cinque valgono solo mentre l'anima lavora; {@link #FEROCIA} vale solo se
 * l'anima viene arruolata. È voluto: fondere per l'officina e fondere per l'esercito devono essere
 * due strade diverse, altrimenti ce n'è una sola ed è sempre la migliore.
 */
public enum SoulTrait implements StringRepresentable {

	/** Più giri di lavorazione nello stesso tempo. */
	ARDORE("ardore", ChatFormatting.GOLD),
	/** Più soul coin dal Pozzo dell'Abisso. */
	AVIDITA("avidita", ChatFormatting.YELLOW),
	/** Ogni tanto la macchina produce il doppio. */
	TENACIA("tenacia", ChatFormatting.GREEN),
	/** Ogni tanto il Crogiolo non consuma il catalizzatore. */
	RISONANZA("risonanza", ChatFormatting.AQUA),
	/** Vale solo da ombra evocata: colpisce più forte. */
	FEROCIA("ferocia", ChatFormatting.RED);

	public static final Codec<SoulTrait> CODEC = StringRepresentable.fromEnum(SoulTrait::values);

	private final String name;
	private final ChatFormatting color;

	SoulTrait(String name, ChatFormatting color) {
		this.name = name;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public Component label() {
		return Component.translatable("arise.soul.trait." + name).withStyle(color);
	}

	public Component description() {
		return Component.translatable("arise.soul.trait." + name + ".desc")
				.withStyle(ChatFormatting.DARK_GRAY);
	}

	/** I tratti che servono a una macchina: tutti tranne quello da combattimento. */
	public boolean worker() {
		return this != FEROCIA;
	}
}
