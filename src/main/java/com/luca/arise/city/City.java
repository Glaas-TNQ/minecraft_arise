package com.luca.arise.city;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;

/**
 * Le città che ospitano un'Associazione dei Cacciatori.
 *
 * <p>Sono <em>hub</em>: posti dove si torna, non da conquistare. Ognuna esiste in un punto fisso
 * dell'Overworld, lontano dalle altre e lontanissima dallo spawn — costruire una città addosso a
 * quello che qualcuno ha già tirato su sarebbe il modo più veloce di far disinstallare la mod.
 *
 * <p>Quello che le distingue sono tre cose, e servono tutte e tre. La {@link CityPalette} è il
 * colore, e si vede per primo. Lo {@link CityStyle} è la pianta — la maglia stradale, l'altezza,
 * la forma degli isolati — ed è quello che si sente camminando: Tokyo è un labirinto di vicoli,
 * Berlino un viale che non finisce mai. Il {@link Landmark} è il motivo per andarci: il Colosseo,
 * la porta di Brandeburgo, il traliccio rosso. Senza il terzo, le prime due restano scenografia.
 */
public enum City implements StringRepresentable {

	/** Grattacieli di vetro azzurro sull'asfalto nero, e la torre a scalini. */
	NEW_YORK("new_york", 0, CityStyle.GRIDIRON, Landmark.EMPIRE_STATE, 0x6FB7E8,
			CityPalette.of(Blocks.CONCRETE.lightGray(), Blocks.SMOOTH_QUARTZ, Blocks.POLISHED_BASALT,
					Blocks.POLISHED_ANDESITE, Blocks.CONCRETE.gray(), Blocks.CONCRETE.black(),
					Blocks.SMOOTH_STONE, DyeColor.LIGHT_BLUE, Blocks.SEA_LANTERN,
					Blocks.OAK_LEAVES, Blocks.OAK_LOG)),

	/** Bianco, rosso e insegne: fitta, bassa, e il traliccio che si vede da ogni vicolo. */
	TOKYO("tokyo", 1, CityStyle.SHITAMACHI, Landmark.TOKYO_TOWER, 0xE8607A,
			CityPalette.of(Blocks.CONCRETE.white(), Blocks.SMOOTH_QUARTZ, Blocks.CONCRETE.red(),
					Blocks.POLISHED_DEEPSLATE, Blocks.DEEPSLATE_TILES, Blocks.CONCRETE.gray(),
					Blocks.CONCRETE.lightGray(), DyeColor.PINK, Blocks.SHROOMLIGHT,
					Blocks.CHERRY_LEAVES, Blocks.CHERRY_LOG)),

	/** Travertino, cotto e sampietrini, e in mezzo l'anfiteatro. */
	ROME("rome", 2, CityStyle.ANTICA, Landmark.COLOSSEUM, 0xD9A05A,
			CityPalette.of(Blocks.SMOOTH_SANDSTONE, Blocks.CUT_SANDSTONE, Blocks.TERRACOTTA,
					Blocks.CHISELED_SANDSTONE, Blocks.BRICKS, Blocks.COBBLESTONE,
					Blocks.SMOOTH_STONE, DyeColor.BROWN, Blocks.LANTERN,
					Blocks.SPRUCE_LEAVES, Blocks.SPRUCE_LOG)),

	/** Intonaco chiaro, tetti di coccio, isolati chiusi con il patio dentro. */
	MADRID("madrid", 3, CityStyle.MANZANA, Landmark.ALCALA, 0xE8B84F,
			CityPalette.of(Blocks.DYED_TERRACOTTA.white(), Blocks.CALCITE,
					Blocks.DYED_TERRACOTTA.red(), Blocks.CUT_SANDSTONE, Blocks.TERRACOTTA,
					Blocks.ANDESITE, Blocks.SMOOTH_SANDSTONE, DyeColor.YELLOW, Blocks.GLOWSTONE,
					Blocks.OAK_LEAVES, Blocks.OAK_LOG)),

	/** Pietra e ardesia, viali larghi, e in fondo al viale la porta. */
	BERLIN("berlin", 4, CityStyle.BOULEVARD, Landmark.BRANDENBURG, 0x9BB0C4,
			CityPalette.of(Blocks.STONE_BRICKS, Blocks.POLISHED_DIORITE, Blocks.DEEPSLATE_BRICKS,
					Blocks.CHISELED_STONE_BRICKS, Blocks.DEEPSLATE_TILES, Blocks.CONCRETE.gray(),
					Blocks.POLISHED_ANDESITE, DyeColor.LIGHT_GRAY, Blocks.SEA_LANTERN,
					Blocks.OAK_LEAVES, Blocks.OAK_LOG));

	public static final Codec<City> CODEC = StringRepresentable.fromEnum(City::values);

	public static final StreamCodec<ByteBuf, City> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	/** Quanto è alto un piano, in blocchi: soffitto compreso. */
	public static final int FLOOR_HEIGHT = 4;

	private final String name;
	private final int index;
	private final CityStyle style;
	private final Landmark landmark;
	private final int color;
	private final CityPalette palette;

	City(String name, int index, CityStyle style, Landmark landmark, int color, CityPalette palette) {
		this.name = name;
		this.index = index;
		this.style = style;
		this.landmark = landmark;
		this.color = color;
		this.palette = palette;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	/** Posizione nella fila di città: decide le coordinate, e non va cambiato a città costruita. */
	public int index() {
		return index;
	}

	public CityStyle style() {
		return style;
	}

	public Landmark landmark() {
		return landmark;
	}

	public CityPalette palette() {
		return palette;
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
