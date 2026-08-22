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
 * Client → server: un'operazione su una gemma.
 *
 * <p>{@code pieceId} vale solo per {@code SOCKET}, dove serve sapere <em>dove</em> va la gemma.
 * Estrarre e rompere partono dalla gemma e basta: il pezzo che la ospita lo trova il server, che e'
 * l'unico a sapere con certezza dove sta.
 */
public record GemActionPayload(UUID gemId, UUID pieceId, Action action) implements CustomPacketPayload {

	public enum Action implements StringRepresentable {
		/** Mette una gemma della sacca in un'incastonatura libera. Ovunque. */
		SOCKET("socket"),
		/** La toglie intatta. Solo al banco dell'Associazione, a pagamento. */
		EXTRACT("extract"),
		/** La rompe per liberare il posto. Ovunque, gratis, senza ritorno. */
		SHATTER("shatter");

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

	private static final UUID NONE = new UUID(0L, 0L);

	public static final CustomPacketPayload.Type<GemActionPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("gem_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GemActionPayload> STREAM_CODEC =
			StreamCodec.composite(
					UUIDUtil.STREAM_CODEC, GemActionPayload::gemId,
					UUIDUtil.STREAM_CODEC, GemActionPayload::pieceId,
					ByteBufCodecs.fromCodec(Action.CODEC), GemActionPayload::action,
					GemActionPayload::new);

	public static GemActionPayload of(UUID gemId, Action action) {
		return new GemActionPayload(gemId, NONE, action);
	}

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
