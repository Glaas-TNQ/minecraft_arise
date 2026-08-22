package com.luca.arise.network;

import com.luca.arise.AriseMod;
import com.mojang.serialization.Codec;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.util.StringRepresentable;

/**
 * Client → server: cosa si è deciso di fare del varco esaminato.
 *
 * <p>Viaggia solo l'id dell'entità e l'intenzione. Rango, ricompense e pianta il server li ha già:
 * riceverli dal client vorrebbe dire lasciargli scegliere quanto vale un Gate.
 */
public record GateActionPayload(int entityId, Action action) implements CustomPacketPayload {

	public enum Action implements StringRepresentable {
		/** Attraversa: è qui che il dungeon viene costruito davvero. */
		ENTER("enter"),
		/** Lascia perdere: il varco si chiude subito invece di aspettare la scadenza. */
		DISMISS("dismiss");

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

	public static final CustomPacketPayload.Type<GateActionPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("gate_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GateActionPayload> STREAM_CODEC =
			StreamCodec.composite(
					ByteBufCodecs.VAR_INT, GateActionPayload::entityId,
					ByteBufCodecs.fromCodec(Action.CODEC), GateActionPayload::action,
					GateActionPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
