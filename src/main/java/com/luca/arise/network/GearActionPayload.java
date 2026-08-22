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
 * Client → server: un'operazione su un pezzo di equipaggiamento.
 *
 * <p>Come per le ombre, viaggia l'id del pezzo e non la sua posizione in lista: lo zaino si
 * riordina da solo quando cambia, e un indice sfasato butterebbe via il pezzo sbagliato.
 */
public record GearActionPayload(UUID pieceId, Action action) implements CustomPacketPayload {

	public enum Action implements StringRepresentable {
		EQUIP("equip"),
		UNEQUIP("unequip"),
		/** Butta via il pezzo. Non si recupera: la conferma la chiede la schermata. */
		DISCARD("discard");

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

	public static final CustomPacketPayload.Type<GearActionPayload> TYPE =
			new CustomPacketPayload.Type<>(AriseMod.id("gear_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GearActionPayload> STREAM_CODEC =
			StreamCodec.composite(
					UUIDUtil.STREAM_CODEC, GearActionPayload::pieceId,
					ByteBufCodecs.fromCodec(Action.CODEC), GearActionPayload::action,
					GearActionPayload::new);

	@Override
	public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
