package com.luca.arise.gate;

import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Il dato dentro un Cubo dell'Abisso: soltanto il suo rango.
 *
 * <p>Un campo, come {@code Catalyst}, e per la stessa ragione: e' l'unica cosa che distingue sei
 * oggetti che altrimenti sarebbero lo stesso oggetto. Il rango decide cosa esce da entrambe le vie
 * — quella benedetta e quella maledetta — e non c'e' nient'altro da salvare, perche' il cubo non
 * contiene niente: <strong>lo tira nel momento in cui lo si apre</strong>.
 *
 * <p>Questo e' anche il motivo per cui non si puo' barare guardando: non c'e' niente dentro da
 * guardare, e due cubi dello stesso rango aperti nello stesso modo daranno cose diverse.
 */
public record AbyssCube(Rank rank) {

	public static final Codec<AbyssCube> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Rank.CODEC.fieldOf("rank").forGetter(AbyssCube::rank)
	).apply(instance, AbyssCube::new));

	public static final StreamCodec<ByteBuf, AbyssCube> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);
}
