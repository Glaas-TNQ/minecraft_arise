package com.luca.arise.network;

import java.util.List;

import com.luca.arise.AriseMod;
import com.luca.arise.city.City;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server → client: quali Associazioni esistono davvero, e apri la schermata di viaggio.
 *
 * <p>Il client non può saperlo da solo: le città stanno a duecentomila blocchi e i loro chunk non
 * sono caricati. L'elenco lo compila il server quando qualcuno tocca un terminale.
 */
public record CityListPayload(List<City> cities) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<CityListPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("city_list"));

	public static final StreamCodec<RegistryFriendlyByteBuf, CityListPayload> STREAM_CODEC =
			StreamCodec.composite(
					City.STREAM_CODEC.apply(ByteBufCodecs.list()), CityListPayload::cities,
					CityListPayload::new);

	public CityListPayload {
		cities = List.copyOf(cities);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
