package com.luca.arise.shadow;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Come si comportano le ombre in combattimento. Vale per tutto l'esercito insieme.
 *
 * <p>Una postura per giocatore e non per singola ombra: nel mezzo di uno scontro si vuole un
 * comando solo, non un pannello. Il controllo fine sulla singola ombra è la schermata di dettaglio.
 */
public enum ShadowStance implements StringRepresentable {
	/** Attaccano ogni ostile nel raggio, senza aspettare. */
	AGGRESSIVE("aggressive"),
	/** Attaccano chi prende di mira il proprietario, chi lo colpisce e chi lui colpisce. */
	DEFENSIVE("defensive"),
	/** Non attaccano mai: seguono e basta. */
	PASSIVE("passive");

	public static final Codec<ShadowStance> CODEC = StringRepresentable.fromEnum(ShadowStance::values);

	public static final StreamCodec<ByteBuf, ShadowStance> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	private final String name;

	ShadowStance(String name) {
		this.name = name;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public Component label() {
		return Component.translatable("arise.stance." + name);
	}

	/** Il prossimo nella rotazione, per il tasto che cicla fra le posture. */
	public ShadowStance next() {
		return values()[(ordinal() + 1) % values().length];
	}
}
