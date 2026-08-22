package com.luca.arise.workshop;

import java.util.function.Consumer;

import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

/**
 * Il catalizzatore: l'unica cosa che il Crogiolo consuma davvero.
 *
 * <p>Un solo campo, il grado, e non e' pigrizia: il grado <em>e'</em> il rango, che in questa mod
 * e' l'unita' di misura di tutto. Un catalizzatore di rango B dice da solo quanto e' raro, di che
 * colore ha il nome e quanti tratti regge l'anima che ne esce.
 *
 * <p>Sta in un componente e non in un item registrato per lo stesso motivo dell'equipaggiamento:
 * sei gradi sarebbero sei item e sei texture, mentre cosi' sono sei nomi colorati sullo stesso
 * sprite. Due gradi diversi non si impilano insieme, perche' il componente e' diverso.
 */
public record Catalyst(Rank grade) implements TooltipProvider {

	public static final Codec<Catalyst> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Rank.CODEC.fieldOf("grade").forGetter(Catalyst::grade)
	).apply(instance, Catalyst::new));

	public static final StreamCodec<ByteBuf, Catalyst> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	/**
	 * Quanti tratti regge l'anima fusa con questo catalizzatore.
	 *
	 * <p>E-D uno, C-B due, A-S tre. E' la sola progressione che i catalizzatori hanno, e basta:
	 * il terzo tratto e' gia' un obiettivo di fine partita.
	 */
	public int traitCapacity() {
		return grade.ordinal() / 2 + 1;
	}

	/**
	 * Quanta parte dei livelli sommati sopravvive alla fusione.
	 *
	 * <p>Sempre sotto l'uno: fondere deve costare qualcosa, altrimenti quattro anime di livello
	 * cinque ne darebbero una di venti e il Crogiolo sarebbe una moltiplicazione gratuita.
	 */
	public double levelYield() {
		return 0.45 + 0.09 * grade.ordinal();
	}

	public Component displayName() {
		return Component.translatable("arise.catalyst.name", grade.label())
				.withStyle(grade.chatColor());
	}

	@Override
	public void addToTooltip(Item.TooltipContext context, Consumer<Component> out, TooltipFlag flag,
			DataComponentGetter components) {
		out.accept(Component.translatable("arise.catalyst.tooltip.traits", traitCapacity())
				.withStyle(ChatFormatting.GRAY));
		out.accept(Component.translatable("arise.catalyst.tooltip.yield",
				(int) Math.round(levelYield() * 100)).withStyle(ChatFormatting.DARK_GRAY));
	}
}
