package com.luca.arise.city;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * I materiali di una città.
 *
 * <p>Undici blocchi sono il vero motivo per cui due città non si assomigliano. La pianta la si
 * capisce dall'alto e solo dopo un po'; il colore lo si vede appena si mette piede in strada. Sono
 * tenuti a parte dall'enum {@link City} perché lì erano già dieci parametri posizionali, e il
 * decimo argomento di un costruttore non lo ricorda nessuno.
 *
 * <p>Nota su 26.2: i blocchi colorati sono {@code ColorCollection}, quindi il cemento grigio chiaro
 * è {@code Blocks.CONCRETE.lightGray()} e il vetro colorato {@code Blocks.STAINED_GLASS.pick(...)}.
 */
public record CityPalette(
		/** Il muro corrente, quello di cui è fatta la maggior parte dei metri quadri. */
		BlockState wall,
		/** Il muro dei pezzi importanti: facciate nobili, monumenti, basamenti. */
		BlockState wallAlt,
		/** Il colore forte: cornicioni, lesene, lampioni. */
		BlockState accent,
		/** La finitura: davanzali, fasce marcapiano, bordi. */
		BlockState trim,
		/** Il tetto. */
		BlockState roof,
		/** La carreggiata. */
		BlockState road,
		/** Il marciapiede, e il pavimento delle piazze. */
		BlockState sidewalk,
		/** Le finestre. */
		BlockState glass,
		/** Quello che fa luce: cambia di parecchio il colore della città di notte. */
		BlockState lamp,
		/** Le chiome degli alberi nei parchi. */
		BlockState foliage,
		/** I tronchi. */
		BlockState wood) {

	public static CityPalette of(Block wall, Block wallAlt, Block accent, Block trim, Block roof,
			Block road, Block sidewalk, DyeColor glass, Block lamp, Block foliage, Block wood) {
		return new CityPalette(
				wall.defaultBlockState(),
				wallAlt.defaultBlockState(),
				accent.defaultBlockState(),
				trim.defaultBlockState(),
				roof.defaultBlockState(),
				road.defaultBlockState(),
				sidewalk.defaultBlockState(),
				Blocks.STAINED_GLASS.pick(glass).defaultBlockState(),
				lamp.defaultBlockState(),
				foliage.defaultBlockState(),
				wood.defaultBlockState());
	}

	/** L'erba dei parchi: uguale ovunque, perché l'erba è erba. */
	public static BlockState grass() {
		return Blocks.GRASS_BLOCK.defaultBlockState();
	}

	public static BlockState air() {
		return Blocks.AIR.defaultBlockState();
	}

	public static BlockState water() {
		return Blocks.WATER.defaultBlockState();
	}
}
