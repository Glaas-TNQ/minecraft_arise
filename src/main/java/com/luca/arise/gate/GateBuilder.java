package com.luca.arise.gate;

import java.util.Map;

import com.luca.arise.config.GateConfig;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Traduce una {@link GateLayout} in blocchi, e la cancella quando il Gate finisce.
 *
 * <p>Due passate, e l'ordine è tutto: prima si riempiono di pietra i volumi <em>esterni</em> di
 * stanze e corridoi, poi si scavano quelli <em>interni</em>. Così un corridoio scava da solo la
 * porta nel muro della stanza che tocca, senza calcoli sulle aperture. Farlo in un'unica passata
 * significherebbe murare corridoi già scavati.
 *
 * <p>Il guscio è di <strong>deepslate rinforzato</strong>: resistenza alle esplosioni 1200, quindi
 * un creeper non apre buchi sul vuoto. È la contromisura a un problema reale, non una precauzione
 * teorica — cadere fuori dal dungeon per un'esplosione significa perdere tutto senza rimedio.
 */
public final class GateBuilder {

	/** Non propagare aggiornamenti ai blocchi vicini: su decine di migliaia di blocchi è la differenza fra un attimo e vari secondi di server fermo. */
	private static final int FLAGS = 2;

	private static final int CORRIDOR_HALF_WIDTH = 1;
	private static final int CORRIDOR_HEIGHT = 4;

	private GateBuilder() {
	}

	public static void build(ServerLevel level, GateConfig config, GateLayout layout, GateTheme theme,
			int originX, int originZ) {
		BlockState shell = Blocks.REINFORCED_DEEPSLATE.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();

		for (Map.Entry<GateLayout.Cell, GateLayout.Kind> room : layout.rooms().entrySet()) {
			fillRoom(level, config, room.getKey(), room.getValue(), originX, originZ, shell, true);
		}
		for (GateLayout.Link link : layout.links()) {
			fillCorridor(level, config, layout, link, originX, originZ, shell, true);
		}

		for (Map.Entry<GateLayout.Cell, GateLayout.Kind> room : layout.rooms().entrySet()) {
			fillRoom(level, config, room.getKey(), room.getValue(), originX, originZ, air, false);
		}
		for (GateLayout.Link link : layout.links()) {
			fillCorridor(level, config, layout, link, originX, originZ, air, false);
		}

		decorate(level, config, layout, theme, originX, originZ);
	}

	/** Cancella tutto quello che questa pianta occupava, con un margine di sicurezza. */
	public static void clear(ServerLevel level, GateConfig config, GateLayout layout, int originX, int originZ) {
		int[] bounds = layout.boundsInCells();
		int margin = config.halfRoom(GateLayout.Kind.BOSS) + 3;

		fill(level,
				originX + bounds[0] * config.cellSize() - margin, config.floorY() - 3,
				originZ + bounds[2] * config.cellSize() - margin,
				originX + bounds[1] * config.cellSize() + margin,
				config.floorY() + config.roomHeight() + 3,
				originZ + bounds[3] * config.cellSize() + margin,
				Blocks.AIR.defaultBlockState());
	}

	private static void fillRoom(ServerLevel level, GateConfig config, GateLayout.Cell room,
			GateLayout.Kind kind, int originX, int originZ, BlockState state, boolean outer) {
		int centerX = originX + room.x() * config.cellSize();
		int centerZ = originZ + room.z() * config.cellSize();
		int half = config.halfRoom(kind) + (outer ? 1 : 0);
		int height = config.heightOf(kind);

		// Il guscio esterno scende di due sotto il pavimento: un blocco solo lascerebbe il
		// pavimento come unica difesa fra il giocatore e il vuoto.
		int bottom = config.floorY() - (outer ? 2 : 0);
		int top = config.floorY() + height - 1 + (outer ? 1 : 0);

		fill(level, centerX - half, bottom, centerZ - half, centerX + half, top, centerZ + half, state);
	}

	private static void fillCorridor(ServerLevel level, GateConfig config, GateLayout layout,
			GateLayout.Link link, int originX, int originZ, BlockState state, boolean outer) {
		int fromX = originX + link.from().x() * config.cellSize();
		int fromZ = originZ + link.from().z() * config.cellSize();
		int toX = originX + link.to().x() * config.cellSize();
		int toZ = originZ + link.to().z() * config.cellSize();

		int grow = outer ? 1 : 0;
		int bottom = config.floorY() - (outer ? 2 : 0);
		int top = config.floorY() + CORRIDOR_HEIGHT - 1 + grow;
		int half = CORRIDOR_HALF_WIDTH + grow;

		// Il volume parte dentro la parete di ciascuna stanza: è così che si apre la porta. Le due
		// stanze possono avere raggi diversi, quindi ognuna contribuisce il proprio.
		int reachFrom = config.halfRoom(layout.kindOf(link.from()));
		int reachTo = config.halfRoom(layout.kindOf(link.to()));

		if (fromZ == toZ) {
			boolean forward = toX > fromX;
			int minX = forward ? fromX + reachFrom : toX + reachTo;
			int maxX = forward ? toX - reachTo : fromX - reachFrom;
			fill(level, minX, bottom, fromZ - half, maxX, top, fromZ + half, state);
		} else {
			boolean forward = toZ > fromZ;
			int minZ = forward ? fromZ + reachFrom : toZ + reachTo;
			int maxZ = forward ? toZ - reachTo : fromZ - reachFrom;
			fill(level, fromX - half, bottom, minZ, fromX + half, top, maxZ, state);
		}
	}

	private static void decorate(ServerLevel level, GateConfig config, GateLayout layout,
			GateTheme theme, int originX, int originZ) {
		for (Map.Entry<GateLayout.Cell, GateLayout.Kind> entry : layout.rooms().entrySet()) {
			GateLayout.Cell room = entry.getKey();
			GateLayout.Kind kind = entry.getValue();

			int centerX = originX + room.x() * config.cellSize();
			int centerZ = originZ + room.z() * config.cellSize();

			floor(level, config, kind, theme, centerX, centerZ);
			lights(level, config, kind, theme, centerX, centerZ);

			if (kind == GateLayout.Kind.HALL || kind == GateLayout.Kind.BOSS) {
				pillars(level, config, kind, theme, centerX, centerZ);
			}
		}
	}

	/**
	 * Il pavimento del tema, steso sopra il guscio.
	 *
	 * <p>Sostituisce solo lo strato calpestabile, non il guscio sotto: quello resta deepslate
	 * rinforzato in ogni tema, perché è la difesa contro le esplosioni e non è materia di gusto.
	 */
	private static void floor(ServerLevel level, GateConfig config, GateLayout.Kind kind,
			GateTheme theme, int centerX, int centerZ) {
		int half = config.halfRoom(kind);
		int y = config.floorY() - 1;

		fill(level, centerX - half, y, centerZ - half, centerX + half, y, centerZ + half, theme.floor());
	}

	/** Lanterne nel pavimento: illuminano senza intralciare e tengono fuori gli spawn vanilla. */
	private static void lights(ServerLevel level, GateConfig config, GateLayout.Kind kind,
			GateTheme theme, int centerX, int centerZ) {
		// La stanza del boss ha sempre la stessa luce, in ogni tema: è un segnale, e un segnale
		// che cambia aspetto ogni volta smette di essere tale.
		BlockState lamp = kind == GateLayout.Kind.BOSS
				? Blocks.CRYING_OBSIDIAN.defaultBlockState()
				: theme.lamp();

		int floor = config.floorY() - 1;
		int offset = config.halfRoom(kind) - 2;

		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				level.setBlock(new BlockPos(centerX + dx * offset, floor, centerZ + dz * offset), lamp, FLAGS);
			}
		}

		level.setBlock(new BlockPos(centerX, floor, centerZ), lamp, FLAGS);
	}

	/** Quattro pilastri nelle sale ampie: coperture da usare, e qualcosa da guardare. */
	private static void pillars(ServerLevel level, GateConfig config, GateLayout.Kind kind,
			GateTheme theme, int centerX, int centerZ) {
		BlockState pillar = theme.pillar();
		int offset = config.halfRoom(kind) / 2;
		int height = config.heightOf(kind);

		for (int dx = -1; dx <= 1; dx += 2) {
			for (int dz = -1; dz <= 1; dz += 2) {
				fill(level, centerX + dx * offset, config.floorY(), centerZ + dz * offset,
						centerX + dx * offset, config.floorY() + height - 1, centerZ + dz * offset, pillar);
			}
		}
	}

	private static void fill(ServerLevel level, int minX, int minY, int minZ,
			int maxX, int maxY, int maxZ, BlockState state) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int x = minX; x <= maxX; x++) {
			for (int z = minZ; z <= maxZ; z++) {
				for (int y = minY; y <= maxY; y++) {
					cursor.set(x, y, z);
					level.setBlock(cursor, state, FLAGS);
				}
			}
		}
	}
}
