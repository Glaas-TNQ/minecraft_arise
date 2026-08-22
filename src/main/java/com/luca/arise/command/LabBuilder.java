package com.luca.arise.command;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GateConfig;
import com.luca.arise.gate.GateEntity;
import com.luca.arise.gate.GateOffer;
import com.luca.arise.gear.GearItems;
import com.luca.arise.gear.GearManager;
import com.luca.arise.gear.GearSlot;
import com.luca.arise.gem.GemManager;
import com.luca.arise.gem.GemType;
import com.luca.arise.progress.Rank;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.registry.ModBlocks;
import com.luca.arise.workshop.LooseSoul;
import com.luca.arise.workshop.MachineBlockEntity;
import com.luca.arise.workshop.MachineKind;
import com.luca.arise.workshop.SoulItems;
import com.luca.arise.workshop.SoulTrait;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Il Laboratorio: una stanza in cui ogni sistema della mod si prova in due minuti.
 *
 * <p>Era un'arena — quattro muri, un tetto e delle lanterne — e serviva a una cosa sola: picchiare
 * qualcosa al chiuso. Il problema e' che nel frattempo la mod ha messo su nove sistemi, e provarne
 * uno voleva dire ricordarsi la sequenza esatta di sei comandi diversi. Adesso la stanza arriva
 * <em>arredata</em>: i varchi sono gia' aperti, i macchinari gia' alimentati, la cassa gia' piena.
 *
 * <p>Tre regole che valgono per tutto quello che c'e' qui dentro:
 * <ul>
 *   <li><strong>niente che scada</strong>. I varchi del laboratorio durano una settimana di gioco
 *       invece di trenta secondi: un varco che si chiude mentre si legge il pannello di analisi
 *       renderebbe il laboratorio inutile proprio nel momento in cui serve;
 *   <li><strong>niente da cercare</strong>. Ogni stazione ha sotto una lastra colorata, e il
 *       comando scrive in chat dove sta cosa;
 *   <li><strong>tutto sbloccato</strong>. La catena degli incarichi viene completata: qui si prova
 *       il gioco, non lo si gioca.
 * </ul>
 *
 * <p><strong>Il tetto non e' una rifinitura.</strong> Un'arena a cielo aperto da' fuoco a
 * qualunque non-morto ci si evochi dentro, perche' quel che li brucia non e' la luce ma il vedere
 * il cielo: Minecraft lo decide guardando se sopra la loro testa c'e' un blocco, non quanto e'
 * illuminata la stanza. Meta' delle prove sulle ombre estratte da zombie e scheletri finivano in
 * fumo prima ancora di cominciare.
 */
public final class LabBuilder {

	/** Altezza interna: quanto spazio libero c'e' dal pavimento al soffitto. */
	private static final int HEIGHT = 12;

	/** A che altezza corre la fascia di lanterne nei muri. */
	private static final int LAMP_BAND = HEIGHT / 3;

	/**
	 * Passo della griglia di lanterne nel pavimento.
	 *
	 * <p>Otto blocchi: una lanterna marina emette luce 15, che scende di uno al blocco, quindi a
	 * meta' strada fra due lanterne si resta sopra 11. Gli ostili spawnano solo a luce zero, cosi'
	 * il laboratorio resta vuoto mentre si prova.
	 */
	private static final int LAMP_SPACING = 8;

	/** Una settimana di gioco: in pratica, "finche' non lo attraversi tu". */
	private static final int GATE_LIFETIME = 24000 * 7;

	/** Lato minimo perche' le tre pareti arredate non si pestino i piedi. */
	private static final int MIN_FURNISHED_SIDE = 24;

	private LabBuilder() {
	}

	/**
	 * Costruisce il laboratorio attorno al giocatore, ovunque si trovi.
	 *
	 * <p>Sostituisce il terreno: e' un comando da gamemaster e non chiede conferma, come
	 * {@code /fill}.
	 *
	 * @return quanti blocchi sono stati piazzati
	 */
	public static int build(ServerPlayer player, int side) {
		ServerLevel level = player.level();
		BlockPos centre = player.blockPosition();

		int placed = raiseRoom(level, centre, side);

		// Sotto il lato minimo la stanza c'e' ma resta vuota: meglio una scatola pulita che tre
		// file di arredi incastrate una nell'altra.
		if (side >= MIN_FURNISHED_SIDE) {
			QuestManager.grantAll(player);

			placed += raiseGates(level, player, centre, side);
			placed += raiseMachines(level, player, centre, side);
			placed += raiseSupplies(level, player, centre);

			grantGems(player);
		}

		return placed;
	}

	// ---------------------------------------------------------------- la stanza

	private static int raiseRoom(ServerLevel level, BlockPos centre, int side) {
		int half = side / 2;
		int floorY = centre.getY() - 1;

		BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();

		// Flag 2 = avvisa i client senza propagare aggiornamenti ai blocchi vicini. Su decine di
		// migliaia di blocchi la differenza fra questo e un aggiornamento completo e' fra un attimo
		// e diversi secondi di server bloccato.
		int flags = 2;
		BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
		int placed = 0;

		for (int dx = -half; dx <= half; dx++) {
			for (int dz = -half; dz <= half; dz++) {
				boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;

				// Lanterne annegate nel pavimento invece che appese: illuminano tutta l'area senza
				// ostacoli in mezzo al campo, e restano a filo per non intralciare il movimento.
				boolean floorLamp = !edge
						&& Math.floorMod(dx, LAMP_SPACING) == 0
						&& Math.floorMod(dz, LAMP_SPACING) == 0;

				cursor.set(centre.getX() + dx, floorY, centre.getZ() + dz);
				level.setBlock(cursor, floorLamp ? lamp : floor, flags);
				placed++;

				for (int dy = 0; dy < HEIGHT; dy++) {
					cursor.set(centre.getX() + dx, floorY + 1 + dy, centre.getZ() + dz);

					if (edge) {
						// Muri per tutta l'altezza: con il tetto sopra, un muro basso lascerebbe
						// una fessura che gira intorno. Fascia di lanterne a un terzo d'altezza,
						// per illuminare i bordi dove il pavimento non arriva.
						boolean wallLamp = dy == LAMP_BAND
								&& (Math.floorMod(dx, LAMP_SPACING) == 0
										|| Math.floorMod(dz, LAMP_SPACING) == 0);
						level.setBlock(cursor, wallLamp ? lamp : wall, flags);
					} else {
						level.setBlock(cursor, air, flags);
					}

					placed++;
				}

				// Il tetto. Chiude anche sopra i muri, cosi' il bordo non resta scoperto.
				cursor.set(centre.getX() + dx, floorY + 1 + HEIGHT, centre.getZ() + dz);
				level.setBlock(cursor, wall, flags);
				placed++;
			}
		}

		return placed;
	}

	// ---------------------------------------------------------------- la parete dei varchi

	/**
	 * Sei varchi, uno per rango, sulla parete a nord.
	 *
	 * <p>Il preventivo si tira adesso, come nel gioco vero: il pannello di analisi dira' il vero e
	 * il dungeon nascera' identico a com'e' stato annunciato. La sola differenza e' la durata.
	 */
	private static int raiseGates(ServerLevel level, ServerPlayer player, BlockPos centre, int side) {
		GateConfig config = AriseConfig.get().gates();
		int half = side / 2;
		int z = centre.getZ() - half + 4;
		int floorY = centre.getY() - 1;
		int placed = 0;

		Rank[] ranks = Rank.values();
		for (int i = 0; i < ranks.length; i++) {
			int x = centre.getX() - 8 + i * 3;

			level.setBlock(new BlockPos(x, floorY, z),
					Blocks.CONCRETE.pick(markerColor(ranks[i])).defaultBlockState(), 2);
			placed++;

			GateEntity varco = com.luca.arise.registry.ModEntities.GATE
					.create(level, EntitySpawnReason.EVENT);
			if (varco == null) {
				continue;
			}

			varco.snapTo(x + 0.5, floorY + 1, z + 0.5, 180.0F, 0.0F);
			varco.configure(GateOffer.roll(config, ranks[i], level.getRandom().nextLong()),
					GATE_LIFETIME);

			level.addFreshEntity(varco);
		}

		return placed;
	}

	/** Il colore della lastra sotto un varco: si legge il rango dal pavimento. */
	private static DyeColor markerColor(Rank rank) {
		return switch (rank) {
			case E -> DyeColor.GRAY;
			case D -> DyeColor.WHITE;
			case C -> DyeColor.LIME;
			case B -> DyeColor.LIGHT_BLUE;
			case A -> DyeColor.MAGENTA;
			case S -> DyeColor.YELLOW;
		};
	}

	// ---------------------------------------------------------------- la parete dell'Officina

	/**
	 * I quattro macchinari, gia' alimentati e gia' intestati a chi ha dato il comando.
	 *
	 * <p>Alimentati e' la parola importante: un Pozzo dell'Abisso vuoto e' identico a un Pozzo
	 * rotto, e distinguere i due casi e' esattamente il tempo che questo comando esiste per
	 * risparmiare. Ogni macchina parte con anime dentro e comincia a lavorare da sola.
	 */
	private static int raiseMachines(ServerLevel level, ServerPlayer player, BlockPos centre,
			int side) {
		int half = side / 2;
		int z = centre.getZ() + half - 4;
		int floorY = centre.getY() - 1;
		int placed = 0;

		MachineKind[] kinds = MachineKind.values();
		for (int i = 0; i < kinds.length; i++) {
			MachineKind kind = kinds[i];
			int x = centre.getX() - 6 + i * 4;

			level.setBlock(new BlockPos(x, floorY, z),
					Blocks.CONCRETE.pick(DyeColor.CYAN).defaultBlockState(), 2);

			BlockPos pos = new BlockPos(x, floorY + 1, z);
			level.setBlock(pos, ModBlocks.of(kind).defaultBlockState(), 3);
			placed += 2;

			if (!(level.getBlockEntity(pos) instanceof MachineBlockEntity machine)) {
				continue;
			}

			machine.setOwner(player.getUUID());

			// Anime di prova: una diversa per casella, cosi' si vede subito che il vigore cambia
			// la velocita' e che i tratti si leggono nel tooltip.
			for (int soulSlot = 0; soulSlot < kind.souls(); soulSlot++) {
				machine.setItem(soulSlot, SoulItems.stack(sampleSoul(soulSlot)));
			}

			if (kind.hasCatalyst()) {
				machine.setItem(kind.catalystSlot(), SoulItems.catalyst(Rank.B, 16));
			}

			if (kind.inputs() > 0) {
				machine.setItem(kind.firstInput(), new ItemStack(Items.RAW_IRON, 64));
			}

			machine.setChanged();
		}

		return placed;
	}

	/** Una scaletta di anime di prova, dalla piu' scarsa a una gia' fusa e con due tratti. */
	private static LooseSoul sampleSoul(int index) {
		return switch (index % 4) {
			case 0 -> LooseSoul.of(mob("zombie"), 20.0, 3.0);
			case 1 -> LooseSoul.of(mob("wither_skeleton"), 20.0, 11.0).withLevel(4);
			case 2 -> LooseSoul.of(mob("blaze"), 20.0, 17.0).withLevel(6)
					.with(SoulTrait.ARDORE, 3);
			default -> LooseSoul.of(mob("ravager"), 100.0, 17.0).withLevel(9)
					.with(SoulTrait.ARDORE, 3).with(SoulTrait.TENACIA, 3);
		};
	}

	private static Identifier mob(String path) {
		return Identifier.withDefaultNamespace(path);
	}

	// ---------------------------------------------------------------- la cassa

	/**
	 * Tre barili al centro: materiali, Officina, bottino.
	 *
	 * <p>Barili e non forzieri perche' un forziere doppio si forma da solo quando due si toccano,
	 * e tre forzieri in fila diventerebbero un doppio piu' uno spaiato con meta' del contenuto
	 * nell'altro. Un barile e' ventisette caselle e nessuna sorpresa.
	 */
	private static int raiseSupplies(ServerLevel level, ServerPlayer player, BlockPos centre) {
		int floorY = centre.getY() - 1;
		int y = floorY + 1;

		fill(level, new BlockPos(centre.getX() - 1, y, centre.getZ()), materials());
		fill(level, new BlockPos(centre.getX(), y, centre.getZ()), workshopKit());
		fill(level, new BlockPos(centre.getX() + 1, y, centre.getZ()), loot(player));

		level.setBlock(new BlockPos(centre.getX() + 3, y, centre.getZ()),
				Blocks.CRAFTING_TABLE.defaultBlockState(), 3);

		for (int dx = -1; dx <= 1; dx++) {
			level.setBlock(new BlockPos(centre.getX() + dx, floorY, centre.getZ()),
					Blocks.CONCRETE.pick(DyeColor.ORANGE).defaultBlockState(), 2);
		}

		return 7;
	}

	private static void fill(ServerLevel level, BlockPos pos, List<ItemStack> contents) {
		level.setBlock(pos, Blocks.BARREL.defaultBlockState(), 3);

		if (!(level.getBlockEntity(pos) instanceof Container container)) {
			return;
		}

		for (int i = 0; i < contents.size() && i < container.getContainerSize(); i++) {
			container.setItem(i, contents.get(i));
		}
	}

	/** Tutto quello che serve a costruire i quattro macchinari, piu' di che dargli da mangiare. */
	private static List<ItemStack> materials() {
		return List.of(
				new ItemStack(Items.DEEPSLATE_BRICKS, 64),
				new ItemStack(Items.SOUL_SAND, 32),
				new ItemStack(Items.ECHO_SHARD, 16),
				new ItemStack(Items.OBSIDIAN, 16),
				new ItemStack(Items.CRYING_OBSIDIAN, 16),
				new ItemStack(Items.FURNACE, 4),
				new ItemStack(Items.BLAZE_ROD, 8),
				new ItemStack(Items.CAULDRON, 4),
				new ItemStack(Items.HOPPER, 16),
				new ItemStack(Items.CHEST, 16),
				new ItemStack(Items.RAW_IRON, 64),
				new ItemStack(Items.RAW_GOLD, 64),
				new ItemStack(Items.RAW_COPPER, 64),
				new ItemStack(Items.COOKED_BEEF, 64),
				new ItemStack(Items.GOLDEN_APPLE, 16));
	}

	/** I quattro macchinari gia' pronti, le anime da mettere a lavorare e i catalizzatori. */
	private static List<ItemStack> workshopKit() {
		List<ItemStack> contents = new ArrayList<>();

		for (MachineKind kind : MachineKind.values()) {
			contents.add(new ItemStack(ModBlocks.of(kind), 4));
		}

		// Otto anime sparse su tutta la scala: bastano a riempire un Crogiolo due volte e a
		// vedere la differenza fra un'operaia di rango E e una di rango A nella stessa macchina.
		contents.add(SoulItems.stack(LooseSoul.of(mob("spider"), 16.0, 2.0)));
		contents.add(SoulItems.stack(LooseSoul.of(mob("zombie"), 20.0, 3.0)));
		contents.add(SoulItems.stack(LooseSoul.of(mob("skeleton"), 20.0, 3.5)));
		contents.add(SoulItems.stack(LooseSoul.of(mob("husk"), 20.0, 5.0)));
		contents.add(SoulItems.stack(LooseSoul.of(mob("wither_skeleton"), 20.0, 11.0).withLevel(3)));
		contents.add(SoulItems.stack(LooseSoul.of(mob("enderman"), 40.0, 9.0).withLevel(5)));
		contents.add(SoulItems.stack(LooseSoul.of(mob("evoker"), 24.0, 20.0).withLevel(7)));
		contents.add(SoulItems.stack(LooseSoul.of(mob("iron_golem"), 100.0, 22.5).withLevel(9)
				.with(SoulTrait.FEROCIA, 3)));

		for (Rank grade : Rank.values()) {
			contents.add(SoulItems.catalyst(grade, 16));
		}

		return contents;
	}

	/**
	 * Un pezzo di equipaggiamento per ogni rango, piu' un completo di rango A.
	 *
	 * <p>I pezzi si tirano davvero, con il generatore vero: un bottino finto proverebbe la
	 * schermata ma non il tiro, e il tiro e' la meta' del sistema che si vuole guardare.
	 */
	private static List<ItemStack> loot(ServerPlayer player) {
		List<ItemStack> contents = new ArrayList<>();

		for (Rank rank : Rank.values()) {
			contents.add(GearItems.stack(GearManager.roll(player, GearSlot.WEAPON, rank)));
		}

		for (GearSlot slot : GearSlot.values()) {
			contents.add(GearItems.stack(GearManager.roll(player, slot, Rank.A)));
		}

		return contents;
	}

	/** Una gemma per tipo, cosi' il banco dell'Associazione ha qualcosa da incastonare. */
	private static void grantGems(ServerPlayer player) {
		for (GemType type : GemType.values()) {
			GemManager.grant(player, GemManager.roll(player, type, Rank.B));
		}
	}

	/** Le righe che il comando scrive in chat: senza, il laboratorio va esplorato a piedi. */
	public static Component map() {
		return Component.translatable("arise.msg.debug.lab_map");
	}
}
