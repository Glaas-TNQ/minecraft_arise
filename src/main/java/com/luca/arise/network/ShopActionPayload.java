package com.luca.arise.network;

import java.util.UUID;

import com.luca.arise.AriseMod;
import com.mojang.serialization.Codec;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.StringRepresentable;

/**
 * Client → server: un'operazione sull'Abyss Shop.
 *
 * <p>{@code OPEN} non apre niente — la schermata la apre il client da solo, perche' legge un
 * attachment sincronizzato. Serve a dire al server "guarda se la rotazione e' passata": il negozio
 * si rigenera pigramente, e senza questo colpetto un giocatore vedrebbe l'assortimento di ieri
 * finche' non compra qualcosa.
 */
public record ShopActionPayload(UUID offerId, Action action) implements CustomPacketPayload {

	public enum Action implements StringRepresentable {
		OPEN("open"),
		BUY("buy"),
		/** Ritira l'assortimento pagando, senza aspettare la rotazione. */
		REFRESH("refresh");

		public static final Codec<Action> CODEC = StringRepresentable.fromEnum(Action::values);

		private final String name;

		Action(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	/** Per le azioni che non riguardano una voce in particolare. */
	private static final UUID NONE = new UUID(0L, 0L);

	public static final CustomPacketPayload.Type<ShopActionPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("shop_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ShopActionPayload> STREAM_CODEC =
			StreamCodec.composite(
					UUIDUtil.STREAM_CODEC, ShopActionPayload::offerId,
					ByteBufCodecs.fromCodec(Action.CODEC), ShopActionPayload::action,
					ShopActionPayload::new);

	public static ShopActionPayload of(Action action) {
		return new ShopActionPayload(NONE, action);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
