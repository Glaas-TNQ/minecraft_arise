package com.luca.arise.shadow;

import java.util.List;
import java.util.UUID;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Quali ombre sono attualmente evocate. Serve al client per disegnare il bottone giusto.
 *
 * <p>Volutamente <strong>non persistente</strong>: le entità non sopravvivono a un riavvio, quindi
 * non deve sopravvivere nemmeno questo elenco. È solo una proiezione, sincronizzata verso il
 * proprietario, di uno stato che vive nel {@link ShadowManager}.
 */
public record SummonedShadows(List<UUID> ids) {

	public static final Codec<SummonedShadows> CODEC =
			UUIDUtil.CODEC.listOf().xmap(SummonedShadows::new, SummonedShadows::ids);

	public static final StreamCodec<ByteBuf, SummonedShadows> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public static final SummonedShadows EMPTY = new SummonedShadows(List.of());

	public SummonedShadows {
		ids = List.copyOf(ids);
	}

	public boolean contains(UUID id) {
		return ids.contains(id);
	}
}
