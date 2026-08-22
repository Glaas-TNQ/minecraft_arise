package com.luca.arise.network;

import com.luca.arise.AriseMod;
import com.luca.arise.progress.Stat;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client → server: "voglio spendere {@code amount} punti su {@code stat}".
 *
 * <p>E' una <em>intenzione</em>, non un ordine: il server verifica punti disponibili e tetto prima
 * di applicare qualunque cosa (CLAUDE.md §4). La statistica viaggia come stringa tramite il suo
 * Codec e non come indice ordinale, cosi' un pacchetto malformato viene rifiutato invece di
 * pescare a caso nell'enum.
 */
public record SpendPointPayload(Stat stat, int amount) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SpendPointPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("spend_point"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SpendPointPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.fromCodec(Stat.CODEC), SpendPointPayload::stat,
					ByteBufCodecs.VAR_INT, SpendPointPayload::amount,
					SpendPointPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
