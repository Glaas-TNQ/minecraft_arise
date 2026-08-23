package com.luca.arise.tutorial;

import java.util.List;

import com.luca.arise.config.AwakeningConfig;
import com.luca.arise.gate.GateManager;
import com.luca.arise.npc.Shopkeeper;
import com.luca.arise.npc.ShopkeeperEntity;
import com.luca.arise.registry.ModEntities;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * La Sala del Risveglio: una stanza sola, in fondo alla dimensione dei varchi.
 *
 * <p>Sta li' e non nell'Overworld per tre motivi che si tengono per mano. Non c'e' terreno da
 * spianare, quindi non c'e' un posto sbagliato dove costruirla; non c'e' cielo, quindi il buio
 * attorno e' gratis ed e' esattamente il buio che serve; e i giocatori non ci arrivano per sbaglio
 * passandoci davanti, perche' quella dimensione non ha strade.
 *
 * <p><strong>Una sola per mondo, e si rifa' da sola.</strong> Non e' un'istanza per giocatore come
 * i Gate: e' un posto, come una citta'. {@link #ensureRoom} si puo' chiamare quante volte si vuole
 * — costruisce solo cio' che manca, e se qualcuno ha fatto danni al mondo dopo un {@code /fill}
 * rimette a posto tutto al risveglio successivo.
 *
 * <p>Il giocatore arriva a un'estremita' e l'Araldo sta all'altra. I quattordici blocchi in mezzo
 * sono l'unica scenografia della scena: si cammina verso qualcuno, invece di trovarselo addosso.
 */
public final class Sanctum {

	/** Come in {@code GateBuilder}: niente aggiornamenti ai vicini su migliaia di blocchi. */
	private static final int FLAGS = 2;

	/** Quanto dista dal muro chi arriva e chi aspetta. */
	private static final int END_INSET = 2;

	/** Passo della griglia di lanterne a soffitto: sopra questo, restano angoli bui. */
	private static final int LAMP_SPACING = 6;

	/** Quanto si allarga la ricerca dell'Araldo rispetto alla stanza. */
	private static final double SEARCH_MARGIN = 3.0;

	private Sanctum() {
	}

	// ---------------------------------------------------------------- dove sta

	public static BlockPos centre(AwakeningConfig config) {
		return new BlockPos(config.sanctumX(), config.floorY(), config.sanctumZ());
	}

	/** Dove compare chi si risveglia: un capo della sala, rivolto verso l'altro. */
	public static Vec3 arrival(AwakeningConfig config) {
		return new Vec3(config.sanctumX() + 0.5, config.floorY(),
				config.sanctumZ() - config.radius() + END_INSET + 0.5);
	}

	/** Dove sta l'Araldo. */
	public static Vec3 herald(AwakeningConfig config) {
		return new Vec3(config.sanctumX() + 0.5, config.floorY(),
				config.sanctumZ() + config.radius() - END_INSET + 0.5);
	}

	/**
	 * L'imbardata di chi arriva: zero, cioe' sud, cioe' verso l'Araldo.
	 *
	 * <p>In Minecraft 0 e' sud e 180 e' nord (vedi CLAUDE.md). La sala e' orientata lungo Z apposta:
	 * cosi' le due direzioni sono i due valori piu' facili da non sbagliare.
	 */
	public static float arrivalYaw() {
		return 0.0F;
	}

	public static float heraldYaw() {
		return 180.0F;
	}

	/** Vero se il giocatore e' dentro la Sala adesso. Si guarda il mondo, non un flag salvato. */
	public static boolean contains(ServerPlayer player, AwakeningConfig config) {
		if (!player.level().dimension().equals(GateManager.GATE_DIMENSION)) {
			return false;
		}

		int reach = config.radius() + 2;

		return Math.abs(player.getX() - config.sanctumX()) <= reach
				&& Math.abs(player.getZ() - config.sanctumZ()) <= reach
				&& player.getY() >= config.floorY() - 4
				&& player.getY() <= config.floorY() + config.height() + 4;
	}

	// ---------------------------------------------------------------- costruzione

	/**
	 * Costruisce la Sala se non c'e'.
	 *
	 * <p>Il riconoscimento passa da un blocco solo — il vetro al centro del pavimento — e non da un
	 * dato salvato: e' lo stesso criterio degli NPC del mercato, e per la stessa ragione. Un
	 * registro di "cose gia' costruite" e il mondo vero smettono di essere d'accordo al primo
	 * comando di un gamemaster, e smettono di esserlo in silenzio.
	 *
	 * <p><strong>Qui non si tocca l'Araldo</strong>, e la separazione e' costata un difetto per
	 * essere trovata: {@code getBlockState} carica il chunk, ma le <em>entita'</em> di un chunk si
	 * caricano solo quando qualcuno le guarda. Cercare l'Araldo in una stanza vuota risponde
	 * sempre "non c'e'", e ogni avvio del server ne aggiungeva uno. Vedi {@link #ensureHerald}.
	 */
	public static void ensureRoom(ServerLevel level, AwakeningConfig config) {
		BlockPos heart = centre(config).below();

		if (!level.getBlockState(heart).is(Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE))) {
			build(level, config);
		}
	}

	private static void build(ServerLevel level, AwakeningConfig config) {
		int cx = config.sanctumX();
		int cz = config.sanctumZ();
		int floor = config.floorY();
		int radius = config.radius();
		int top = floor + config.height();

		BlockState shell = Blocks.REINFORCED_DEEPSLATE.defaultBlockState();
		BlockState glass = Blocks.STAINED_GLASS.pick(DyeColor.LIGHT_BLUE).defaultBlockState();

		// Guscio pieno, poi svuotato: la stessa passata doppia di GateBuilder, e per lo stesso
		// motivo — cosi' non c'e' nessun calcolo di spessori da sbagliare.
		fill(level, cx - radius - 1, floor - 2, cz - radius - 1,
				cx + radius + 1, top, cz + radius + 1, shell);
		fill(level, cx - radius, floor, cz - radius,
				cx + radius, top - 1, cz + radius, Blocks.AIR.defaultBlockState());

		// Pavimento: ardesia levigata, con una vena di mattoni lungo il cammino verso l'Araldo.
		fill(level, cx - radius, floor - 1, cz - radius, cx + radius, floor - 1, cz + radius,
				Blocks.POLISHED_DEEPSLATE.defaultBlockState());
		fill(level, cx - 1, floor - 1, cz - radius, cx + 1, floor - 1, cz + radius,
				Blocks.DEEPSLATE_TILES.defaultBlockState());

		// Il cuore: tre per tre di vetro con la luce sotto. E' anche il segno che la sala esiste.
		fill(level, cx - 1, floor - 2, cz - 1, cx + 1, floor - 2, cz + 1,
				Blocks.SEA_LANTERN.defaultBlockState());
		fill(level, cx - 1, floor - 1, cz - 1, cx + 1, floor - 1, cz + 1, glass);

		lights(level, cx, cz, radius, top);
		altar(level, config, glass);
	}

	/**
	 * Lanterne a soffitto.
	 *
	 * <p>Non e' rifinitura: una stanza al buio nella dimensione dei varchi genera ostili, e l'unica
	 * scena della mod in cui il giocatore e' fermo a leggere diventerebbe una scena con uno zombie
	 * alle spalle.
	 */
	private static void lights(ServerLevel level, int cx, int cz, int radius, int top) {
		for (int x = cx - radius + 1; x <= cx + radius - 1; x += LAMP_SPACING) {
			for (int z = cz - radius + 1; z <= cz + radius - 1; z += LAMP_SPACING) {
				level.setBlock(new BlockPos(x, top, z), Blocks.SEA_LANTERN.defaultBlockState(), FLAGS);
			}
		}
	}

	/** Il fondo della sala: due colonne e una vetrata alle spalle di chi parla. */
	private static void altar(ServerLevel level, AwakeningConfig config, BlockState glass) {
		int cx = config.sanctumX();
		int wall = config.sanctumZ() + config.radius();
		int floor = config.floorY();

		for (int side : new int[] { -3, 3 }) {
			fill(level, cx + side, floor, wall - 1, cx + side, floor + 2, wall - 1,
					Blocks.CRYING_OBSIDIAN.defaultBlockState());
			level.setBlock(new BlockPos(cx + side, floor + 3, wall - 1),
					Blocks.SEA_LANTERN.defaultBlockState(), FLAGS);
		}

		// La vetrata guarda il vuoto: dietro non c'e' niente, ed e' il punto.
		fill(level, cx - 2, floor + 1, wall + 1, cx + 2, floor + 3, wall + 1, glass);
	}

	private static void fill(ServerLevel level, int x1, int y1, int z1, int x2, int y2, int z2,
			BlockState state) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

		for (int x = x1; x <= x2; x++) {
			for (int y = y1; y <= y2; y++) {
				for (int z = z1; z <= z2; z++) {
					level.setBlock(pos.set(x, y, z), state, FLAGS);
				}
			}
		}
	}

	// ---------------------------------------------------------------- l'Araldo

	/**
	 * Rimette l'Araldo se non c'e' piu', e ne toglie di mezzo se ce n'e' piu' di uno.
	 *
	 * <p><strong>Va chiamata solo quando c'e' un giocatore nella stanza.</strong> Le entita' di un
	 * chunk esistono, per chi le cerca, soltanto se quel chunk e' vivo: chiamarla su una Sala vuota
	 * riceve "non c'e' nessuno" da una stanza che l'Araldo ce l'ha, e ne mette un secondo. E'
	 * successo, e il conto saliva di uno a ogni avvio del server.
	 *
	 * <p>Il taglio dei doppioni serve ai mondi dove il difetto e' gia' passato: due Araldi identici
	 * a un metro l'uno dall'altro sono peggio di nessun Araldo, perche' il giocatore si chiede
	 * quale sia quello giusto.
	 */
	public static ShopkeeperEntity ensureHerald(ServerLevel level, AwakeningConfig config) {
		Vec3 spot = herald(config);
		AABB box = new AABB(spot.x(), spot.y(), spot.z(), spot.x(), spot.y(), spot.z())
				.inflate(config.radius() + SEARCH_MARGIN);

		List<ShopkeeperEntity> found = level.getEntitiesOfClass(ShopkeeperEntity.class, box,
				npc -> npc.role() == Shopkeeper.HERALD);

		if (!found.isEmpty()) {
			for (int i = 1; i < found.size(); i++) {
				found.get(i).discard();
			}

			return found.getFirst();
		}

		ShopkeeperEntity herald = ModEntities.SHOPKEEPER.create(level, EntitySpawnReason.EVENT);

		if (herald == null) {
			return null;
		}

		herald.setRole(Shopkeeper.HERALD);
		herald.snapTo(spot.x(), spot.y(), spot.z(), heraldYaw(), 0.0F);
		herald.setYHeadRot(heraldYaw());
		herald.setYBodyRot(heraldYaw());
		level.addFreshEntity(herald);

		return herald;
	}

	/** Toglie di mezzo tutto quello che non e' l'Araldo: la Sala non e' un posto dove si combatte. */
	public static void sweep(ServerLevel level, AwakeningConfig config) {
		BlockPos heart = centre(config);
		AABB box = new AABB(heart.getX(), heart.getY(), heart.getZ(),
				heart.getX(), heart.getY(), heart.getZ()).inflate(config.radius() + 1);

		for (Entity entity : level.getEntities((Entity) null, box, candidate ->
				candidate instanceof net.minecraft.world.entity.Mob
						&& !(candidate instanceof ShopkeeperEntity))) {
			entity.discard();
		}
	}
}
