package com.luca.arise.city;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Le città che ospitano un'Associazione dei Cacciatori.
 *
 * <p>Sono <em>hub</em>: posti dove si torna, non da conquistare. Ognuna esiste in un punto fisso
 * dell'Overworld, lontano dalle altre e lontanissima dallo spawn — costruire una città addosso a
 * quello che qualcuno ha già tirato su sarebbe il modo più veloce di far disinstallare la mod.
 *
 * <p>Quello che le distingue è la <strong>tavolozza</strong> e il profilo di altezza: New York fa
 * grattacieli di vetro, Roma non supera i quattro piani. Non è realismo — è che due città che si
 * assomigliano non varrebbe la pena di visitarle entrambe.
 *
 * <p>Nota su 26.2: i blocchi colorati non hanno più una costante per colore. Il cemento grigio
 * chiaro è {@code Blocks.CONCRETE.lightGray()}, non {@code Blocks.LIGHT_GRAY_CONCRETE}.
 */
public enum City implements StringRepresentable {

	/** Grattacieli, vetro azzurro, asfalto nero. */
	NEW_YORK("new_york", 0,
			Blocks.CONCRETE.lightGray(), Blocks.CONCRETE.gray(), Blocks.SMOOTH_STONE,
			Blocks.CONCRETE.black(), Blocks.POLISHED_ANDESITE, DyeColor.LIGHT_BLUE,
			4, 11, 0x6FB7E8),

	/** Bianco e rosso, tetti scuri, altezze medie fitte. */
	TOKYO("tokyo", 1,
			Blocks.CONCRETE.white(), Blocks.CONCRETE.red(), Blocks.DEEPSLATE_TILES,
			Blocks.CONCRETE.gray(), Blocks.SMOOTH_QUARTZ, DyeColor.WHITE,
			3, 7, 0xE8607A),

	/** Travertino e cotto, bassa e larga. */
	ROME("rome", 2,
			Blocks.SMOOTH_SANDSTONE, Blocks.TERRACOTTA, Blocks.BRICKS,
			Blocks.COBBLESTONE, Blocks.SMOOTH_SANDSTONE, DyeColor.BROWN,
			2, 4, 0xD9A05A),

	/** Intonaco chiaro e tetti di coccio. */
	MADRID("madrid", 3,
			Blocks.DYED_TERRACOTTA.white(), Blocks.DYED_TERRACOTTA.red(), Blocks.TERRACOTTA,
			Blocks.ANDESITE, Blocks.SMOOTH_SANDSTONE, DyeColor.YELLOW,
			2, 5, 0xE8B84F),

	/** Pietra e mattoni scuri, ordinata. */
	BERLIN("berlin", 4,
			Blocks.STONE_BRICKS, Blocks.CONCRETE.gray(), Blocks.DEEPSLATE_BRICKS,
			Blocks.CONCRETE.gray(), Blocks.STONE, DyeColor.GRAY,
			3, 6, 0x9BB0C4);

	public static final Codec<City> CODEC = StringRepresentable.fromEnum(City::values);

	public static final StreamCodec<ByteBuf, City> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	/** Quanto è alto un piano, in blocchi: soffitto compreso. */
	public static final int FLOOR_HEIGHT = 4;

	private final String name;
	private final int index;
	private final BlockState wall;
	private final BlockState accent;
	private final BlockState roof;
	private final BlockState road;
	private final BlockState sidewalk;
	private final BlockState glass;
	private final int minFloors;
	private final int maxFloors;
	private final int color;

	City(String name, int index, Block wall, Block accent, Block roof, Block road, Block sidewalk,
			DyeColor glass, int minFloors, int maxFloors, int color) {
		this.name = name;
		this.index = index;
		this.wall = wall.defaultBlockState();
		this.accent = accent.defaultBlockState();
		this.roof = roof.defaultBlockState();
		this.road = road.defaultBlockState();
		this.sidewalk = sidewalk.defaultBlockState();
		this.glass = Blocks.STAINED_GLASS.pick(glass).defaultBlockState();
		this.minFloors = minFloors;
		this.maxFloors = maxFloors;
		this.color = color;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** Posizione nella fila di città: decide le coordinate, e non va cambiato a città costruita. */
	public int index() {
		return index;
	}

	public BlockState wall() {
		return wall;
	}

	public BlockState accent() {
		return accent;
	}

	public BlockState roof() {
		return roof;
	}

	public BlockState road() {
		return road;
	}

	public BlockState sidewalk() {
		return sidewalk;
	}

	public BlockState glass() {
		return glass;
	}

	public int minFloors() {
		return minFloors;
	}

	public int maxFloors() {
		return maxFloors;
	}

	/** Colore per la schermata di viaggio. */
	public int color() {
		return color;
	}

	public Component label() {
		return Component.translatable("arise.city." + name);
	}

	public static City byName(String name) {
		for (City city : values()) {
			if (city.name.equals(name)) {
				return city;
			}
		}
		return null;
	}
}
