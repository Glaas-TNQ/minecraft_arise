package com.luca.arise.daily;

import com.luca.arise.gate.GateManager;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * La Zona di Penalità: un deserto senza cielo, e una sola cosa da fare.
 *
 * <p>Nel canone e' un deserto infinito «senza vento, senza sole, senza luna, senza stelle — eppure
 * si vede tutto», abitato dai millepiedi. Qui e' una piana quadrata di sabbia delle anime nella
 * dimensione dei varchi, illuminata da sotto, con un muro invalicabile attorno e niente sopra.
 *
 * <p><strong>Perche' non una dimensione nuova.</strong> Sarebbe stato piu' fedele e molto piu'
 * rischioso: un {@code DimensionType} in piu' significa un file di datapack che i mondi vecchi non
 * hanno, e un mondo che si apre senza quella dimensione e' un giocatore che il Sistema puo'
 * teletrasportare in nessun posto. La Sala del Risveglio ha gia' risolto lo stesso problema nello
 * stesso modo — sta a ovest dell'origine, dove le istanze dei Gate non arrivano mai — e questa sta
 * accanto a lei, piu' a ovest ancora.
 *
 * <p>La piana e' <strong>illuminata dal pavimento</strong>: pietraluce sotto il vetro, come la Sala.
 * Non e' decorazione. Al buio, nella dimensione dei varchi, nascono ostili veri, e la Zona deve
 * contenere soltanto quello che ci mette il Sistema — altrimenti la penalita' diventerebbe una
 * lotteria su quanti scheletri sono comparsi mentre nessuno guardava.
 */
public final class PenaltyZone {

	/**
	 * Dove sta, nella dimensione dei varchi.
	 *
	 * <p>Ancora piu' a ovest della Sala del Risveglio, che sta a -4096. Il semiasse negativo e'
	 * tutto libero — le istanze dei Gate si allocano da zero in su — e queste due stanze sono le
	 * uniche cose permanenti che ci vivono. Ottomila blocchi fra loro perche' non si vedano.
	 */
	public static final int X = -12288;
	public static final int Z = 0;

	/** Quota del pavimento, la stessa di tutto il resto della dimensione. */
	public static final int FLOOR = 64;

	/** Semilato della piana: 41 blocchi di lato interno. Grande abbastanza per correre. */
	public static final int RADIUS = 20;

	/** Altezza del muro. Alto, perche' non e' un recinto da scavalcare: e' una penalita'. */
	private static final int WALL = 12;

	/** Passo della griglia di luci nel pavimento. */
	private static final int LAMP_SPACING = 6;

	private PenaltyZone() {
	}

	/** Il centro della piana: e' li' che si arriva. */
	public static Vec3 centre() {
		return new Vec3(X + 0.5, FLOOR, Z + 0.5);
	}

	/**
	 * Vero se il giocatore e' dentro la Zona.
	 *
	 * <p>Si chiede al mondo dove si trova, non a un flag salvato: e' la stessa scelta della Sala del
	 * Risveglio, e per la stessa ragione — un booleano e la posizione vera divergono alla prima
	 * disconnessione andata male, e la divergenza lascia un giocatore chiuso in una stanza senza
	 * porte.
	 */
	public static boolean contains(ServerPlayer player) {
		if (!player.level().dimension().equals(GateManager.GATE_DIMENSION)) {
			return false;
		}

		return Math.abs(player.getX() - X) <= RADIUS + 2
				&& Math.abs(player.getZ() - Z) <= RADIUS + 2
				&& player.getY() >= FLOOR - 4
				&& player.getY() <= FLOOR + WALL + 4;
	}

	/**
	 * Costruisce la piana, se non c'e' gia'.
	 *
	 * <p>Idempotente come {@code Sanctum.ensureRoom}, e riconosce se stessa dallo stesso trucco:
	 * guarda un blocco al centro invece di tenere un flag da qualche parte. Il mondo e' la fonte di
	 * verita' su cosa c'e' nel mondo.
	 */
	public static void ensure(ServerLevel level) {
		BlockPos heart = new BlockPos(X, FLOOR - 1, Z);

		if (level.getBlockState(heart).is(Blocks.SEA_LANTERN)) {
			return;
		}

		build(level);
	}

	private static void build(ServerLevel level) {
		BlockState shell = Blocks.REINFORCED_DEEPSLATE.defaultBlockState();
		BlockState sand = Blocks.SOUL_SAND.defaultBlockState();
		BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();

		int top = FLOOR + WALL;

		// Guscio pieno e poi svuotato, come la Sala e come i Gate: nessuno spessore da sbagliare.
		fill(level, X - RADIUS - 1, FLOOR - 3, Z - RADIUS - 1,
				X + RADIUS + 1, top, Z + RADIUS + 1, shell);
		fill(level, X - RADIUS, FLOOR, Z - RADIUS,
				X + RADIUS, top - 1, Z + RADIUS, Blocks.AIR.defaultBlockState());

		// Il suolo: sabbia delle anime. Rallenta chi ci cammina sopra, ed e' esattamente cio' che
		// deve fare — la Sopravvivenza non e' una corsa attorno a un pilastro.
		fill(level, X - RADIUS, FLOOR - 1, Z - RADIUS, X + RADIUS, FLOOR - 1, Z + RADIUS, sand);

		// Le luci stanno *sotto* il suolo e filtrano appena: il canone dice «nessun sole, nessuna
		// stella, eppure si vede tutto». Servono anche a impedire che la Zona generi i suoi ostili.
		for (int x = X - RADIUS; x <= X + RADIUS; x += LAMP_SPACING) {
			for (int z = Z - RADIUS; z <= Z + RADIUS; z += LAMP_SPACING) {
				level.setBlock(new BlockPos(x, FLOOR - 2, z), lamp, FLAGS);
			}
		}

		// Il centro e' anche il segno che la piana esiste: una luce affiorante, l'unica.
		level.setBlock(new BlockPos(X, FLOOR - 1, Z), lamp, FLAGS);
	}

	/** Nessuna propagazione ai vicini: su decine di migliaia di blocchi e' la sola cosa che conta. */
	private static final int FLAGS = 2;

	private static void fill(ServerLevel level, int x0, int y0, int z0, int x1, int y1, int z1,
			BlockState state) {
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

		for (int x = x0; x <= x1; x++) {
			for (int y = y0; y <= y1; y++) {
				for (int z = z0; z <= z1; z++) {
					level.setBlock(cursor.set(x, y, z), state, FLAGS);
				}
			}
		}
	}
}
