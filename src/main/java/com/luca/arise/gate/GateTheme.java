package com.luca.arise.gate;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * La composizione di un Gate: di che cosa è fatto quello che ci si trova dentro.
 *
 * <p>Serve a due cose insieme. Dà un'informazione vera da leggere <em>prima</em> di entrare — due
 * varchi dello stesso rango non sono più identici — e rompe la monotonia di un dungeon dove ogni
 * stanza è uguale alla precedente.
 *
 * <p>Il tema <strong>non tocca il guscio</strong>, che resta deepslate rinforzato in ogni Gate. Non
 * è una dimenticanza: quel guscio è la contromisura ai creeper che aprivano buchi sul vuoto, e non
 * va messa in mano all'estetica. Il tema cambia ciò che si calpesta, i pilastri e la luce.
 */
public enum GateTheme implements StringRepresentable {

	/** Pietra scura e luce fredda: il Gate ordinario. */
	DEPTHS("depths", Blocks.DEEPSLATE_TILES, Blocks.POLISHED_DEEPSLATE, Blocks.SEA_LANTERN, 0x7A8CA3),
	/** Basalto e luce arancione. */
	ASH("ash", Blocks.POLISHED_BASALT, Blocks.BASALT, Blocks.SHROOMLIGHT, 0xE8873F),
	/** Ghiaccio: scivola, e si vede da lontano che è diverso. */
	FROST("frost", Blocks.PACKED_ICE, Blocks.BLUE_ICE, Blocks.SEA_LANTERN, 0x8FD8F0),
	/** Blackstone e glowstone: rovina bruciata. */
	RUIN("ruin", Blocks.POLISHED_BLACKSTONE_BRICKS, Blocks.BLACKSTONE, Blocks.GLOWSTONE, 0xD9B25A),
	/** Sculk: il tema che somiglia di più alle ombre. */
	SCULK("sculk", Blocks.SCULK, Blocks.DEEPSLATE_TILES, Blocks.VERDANT_FROGLIGHT, 0x4FBF9F),
	/** Pietra dell'End: raro e sbagliato, come dev'essere. */
	VOID("void", Blocks.END_STONE_BRICKS, Blocks.PURPUR_PILLAR, Blocks.PEARLESCENT_FROGLIGHT, 0xC9A7E8);

	public static final Codec<GateTheme> CODEC = StringRepresentable.fromEnum(GateTheme::values);

	public static final StreamCodec<ByteBuf, GateTheme> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	private final String name;
	private final BlockState floor;
	private final BlockState pillar;
	private final BlockState lamp;
	private final int color;

	GateTheme(String name, net.minecraft.world.level.block.Block floor,
			net.minecraft.world.level.block.Block pillar,
			net.minecraft.world.level.block.Block lamp, int color) {
		this.name = name;
		this.floor = floor.defaultBlockState();
		this.pillar = pillar.defaultBlockState();
		this.lamp = lamp.defaultBlockState();
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public BlockState floor() {
		return floor;
	}

	public BlockState pillar() {
		return pillar;
	}

	public BlockState lamp() {
		return lamp;
	}

	/** Colore per la schermata di analisi e per i particellari del varco. */
	public int color() {
		return color;
	}

	public Component label() {
		return Component.translatable("arise.gate.theme." + name);
	}
}
