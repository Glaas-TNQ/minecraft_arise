package com.luca.arise.ability;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Le abilità attive del Monarca.
 *
 * <p>Un enum e non un registro: sono poche, sono parte dell'identità della mod e non ha senso che
 * un'altra mod ne aggiunga. Ognuna dichiara solo <em>cosa serve per usarla</em>; l'effetto sta in
 * {@link AbilityManager}, perché tocca il mondo e deve girare sul server.
 */
public enum Ability implements StringRepresentable {
	/** Uno scatto istantaneo nella direzione dello sguardo, attraverso ciò che si può attraversare. */
	SHADOW_STEP("shadow_step", 5, 0),
	/** Scambia posto con l'ombra evocata più lontana. La firma di Solo Leveling. */
	SHADOW_EXCHANGE("shadow_exchange", 10, 0),
	/**
	 * Il volo del Monarca: si sta in aria finche' c'e' Mana, e si cade quando finisce.
	 *
	 * <p>E' l'unica abilita' che <strong>si accende e resta accesa</strong>. Tutte le altre sono un
	 * momento — uno scatto, uno scambio, un'aura che dura un tempo deciso da noi; questa dura
	 * quanto la riserva, e chi la usa vede scendere una barra mentre vola. E' il motivo per cui e'
	 * nata insieme al Mana e non prima: un volo senza prezzo e' la fine di ogni distanza, e le
	 * citta' di questa mod stanno a duecentomila blocchi proprio perche' le distanze contino.
	 *
	 * <p>Livello dieci: abbastanza avanti da essere una conquista, abbastanza presto da servire
	 * quando il mondo comincia a essere grande.
	 */
	SHADOW_FLIGHT("shadow_flight", 10, 0),
	/** Aura che potenzia il Monarca e tutto l'esercito evocato. */
	MONARCH_DOMAIN("monarch_domain", 20, 40),
	/** Piega i nemici vicini: rallentati, indeboliti e visibili attraverso i muri. */
	SOVEREIGN_AUTHORITY("sovereign_authority", 30, 60);

	public static final Codec<Ability> CODEC = StringRepresentable.fromEnum(Ability::values);

	public static final StreamCodec<ByteBuf, Ability> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	private final String name;
	private final int requiredLevel;
	private final long soulCost;

	Ability(String name, int requiredLevel, long soulCost) {
		this.name = name;
		this.requiredLevel = requiredLevel;
		this.soulCost = soulCost;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public int requiredLevel() {
		return requiredLevel;
	}

	public long soulCost() {
		return soulCost;
	}

	public Component label() {
		return Component.translatable("arise.ability." + name);
	}

	/** Iniziale mostrata nella barra dell'HUD: quattro slot non hanno spazio per i nomi. */
	public String initial() {
		return switch (this) {
			case SHADOW_STEP -> "S";
			case SHADOW_EXCHANGE -> "X";
			case SHADOW_FLIGHT -> "^";
			case MONARCH_DOMAIN -> "D";
			case SOVEREIGN_AUTHORITY -> "A";
		};
	}
}
