package com.luca.arise.shop;

import java.util.Optional;
import java.util.UUID;

import com.luca.arise.gear.GearPiece;
import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Una voce dell'assortimento.
 *
 * <p>Due forme, e la differenza e' tutta nel {@code piece}. Una voce <em>visibile</em> porta con
 * se' il pezzo gia' tirato: si legge cosa fa prima di pagarlo, e cio' che si compra e' esattamente
 * quello. Una voce <em>sigillata</em> non ha pezzo: si compra un rango, e il pezzo viene tirato al
 * momento dell'acquisto.
 *
 * <p>Il sigillato costa meno perche' il rischio lo prende il compratore. E' l'unico posto della mod
 * dove si paga senza sapere cosa si prende, ed e' voluto che sia una scelta e non l'unica strada.
 */
public record ShopOffer(UUID id, Kind kind, Rank rank, Optional<GearPiece> piece, long price) {

	public enum Kind implements StringRepresentable {
		/** Il pezzo e' li', si vede, si compra quello. */
		VISIBLE("visible"),
		/** Si compra il rango; il pezzo esce all'acquisto. */
		SEALED("sealed");

		public static final Codec<Kind> CODEC = StringRepresentable.fromEnum(Kind::values);

		private final String name;

		Kind(String name) {
			this.name = name;
		}

		@Override
		public String getSerializedName() {
			return name;
		}
	}

	public static final Codec<ShopOffer> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.fieldOf("id").forGetter(ShopOffer::id),
			Kind.CODEC.fieldOf("kind").forGetter(ShopOffer::kind),
			Rank.CODEC.fieldOf("rank").forGetter(ShopOffer::rank),
			GearPiece.CODEC.optionalFieldOf("piece").forGetter(ShopOffer::piece),
			Codec.LONG.fieldOf("price").forGetter(ShopOffer::price)
	).apply(instance, ShopOffer::new));

	public static ShopOffer visible(GearPiece piece, long price) {
		return new ShopOffer(UUID.randomUUID(), Kind.VISIBLE, piece.rank(), Optional.of(piece), price);
	}

	public static ShopOffer sealed(Rank rank, long price) {
		return new ShopOffer(UUID.randomUUID(), Kind.SEALED, rank, Optional.empty(), price);
	}

	public boolean isSealed() {
		return kind == Kind.SEALED;
	}

	/** Il nome mostrato: il pezzo vero, oppure la promessa che lo sostituisce. */
	public Component label() {
		return piece.map(GearPiece::displayName)
				.orElseGet(() -> Component.translatable("arise.shop.sealed", rank.label())
						.withStyle(rank.chatColor()));
	}
}
