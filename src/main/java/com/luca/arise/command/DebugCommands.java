package com.luca.arise.command;

import java.util.List;
import java.util.function.BiConsumer;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import com.luca.arise.fx.AriseFx;
import com.luca.arise.progress.Rank;

/**
 * Comandi di comodo per provare la mod senza andare a caccia di zombie di notte.
 *
 * <p>Non fanno parte del gioco: servono a rendere la verifica di una fase questione di minuti
 * invece che di mezz'ora (CLAUDE.md §14). Richiedono i permessi da gamemaster, come i comandi
 * vanilla che barano.
 */
public final class DebugCommands {

	/** Un set completo di equipaggiamento, dal più scarso al più assurdo. */
	private enum Gear {
		IRON("iron", Items.IRON_SWORD, Items.IRON_HELMET, Items.IRON_CHESTPLATE,
				Items.IRON_LEGGINGS, Items.IRON_BOOTS, 0),
		DIAMOND("diamond", Items.DIAMOND_SWORD, Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE,
				Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS, 3),
		NETHERITE("netherite", Items.NETHERITE_SWORD, Items.NETHERITE_HELMET, Items.NETHERITE_CHESTPLATE,
				Items.NETHERITE_LEGGINGS, Items.NETHERITE_BOOTS, 5);

		private final String name;
		private final Item sword;
		private final Item helmet;
		private final Item chestplate;
		private final Item leggings;
		private final Item boots;
		private final int enchantLevel;

		Gear(String name, Item sword, Item helmet, Item chestplate, Item leggings, Item boots, int enchantLevel) {
			this.name = name;
			this.sword = sword;
			this.helmet = helmet;
			this.chestplate = chestplate;
			this.leggings = leggings;
			this.boots = boots;
			this.enchantLevel = enchantLevel;
		}
	}

	/**
	 * Gli effetti provabili con {@code /arise fx}.
	 *
	 * <p>Senza, verificare un particellare vorrebbe dire trovare il mob giusto, ucciderlo, sperare
	 * che l'estrazione riesca — e ripetere tutto per cambiare un numero. Qui si guarda, si corregge
	 * la config e si riguarda.
	 */
	private enum Effect {
		ARISE("arise", (level, player) -> {
			AriseFx.extractionRitual(level, player.position());
			AriseFx.extractionSuccess(level, player.position(), Rank.S);
		}),
		ARISE_FAIL("arise_fail", (level, player) -> AriseFx.extractionFailed(level, player.position())),
		SUMMON("summon", (level, player) -> {
			AriseFx.summon(level, player.position(), DEBUG_COLOR);
			AriseFx.summonSound(level, player.position());
		}),
		RECALL("recall", (level, player) -> AriseFx.recall(level, player.position(), DEBUG_COLOR)),
		FALLEN("fallen", (level, player) -> AriseFx.shadowFell(level, player.position(), DEBUG_COLOR)),
		LEVEL_UP("level_up", (level, player) -> AriseFx.levelUp(player)),
		RANK_UP("rank_up", (level, player) -> AriseFx.rankUp(level, player.position(), Rank.S)),
		GATE_OPEN("gate_open", (level, player) -> AriseFx.gateEntered(level, player.position(), Rank.B)),
		GATE_AMBIENCE("gate_ambience", (level, player) -> AriseFx.gateAmbience(player)),
		GATE_BOSS("gate_boss", (level, player) -> AriseFx.gateBoss(level, player.position(), Rank.A)),
		GATE_CLEAR("gate_clear", (level, player) -> AriseFx.gateClear(player, Rank.A)),
		DASH("dash", (level, player) -> AriseFx.dash(level, player.position(), ahead(player))),
		EXCHANGE("exchange", (level, player) -> AriseFx.exchange(level, player.position(), ahead(player), DEBUG_COLOR)),
		DOMAIN("domain", (level, player) -> AriseFx.domain(level, player.position(), 4.5)),
		AUTHORITY("authority", (level, player) -> AriseFx.authority(level, player.position(), 12.0));

		private final String name;
		private final BiConsumer<ServerLevel, ServerPlayer> action;

		Effect(String name, BiConsumer<ServerLevel, ServerPlayer> action) {
			this.name = name;
			this.action = action;
		}
	}

	/** Il viola delle ombre senza padrone: serve solo a far vedere che il colore arriva. */
	private static final int DEBUG_COLOR = 0x8E6BFF;

	/** Sei blocchi davanti al naso: la seconda estremità degli effetti che ne vogliono due. */
	private static Vec3 ahead(ServerPlayer player) {
		return player.position().add(player.getLookAngle().multiply(6.0, 0.0, 6.0));
	}

	private DebugCommands() {
	}

	/** Aggiunge i sottocomandi di debug al ramo {@code /arise}. */
	public static void addTo(LiteralArgumentBuilder<CommandSourceStack> root, CommandBuildContext buildContext) {
		root.then(Commands.literal("spawn")
				.requires(AriseCommands::canCheat)
				.then(Commands.argument("tipo", ResourceArgument.resource(buildContext, Registries.ENTITY_TYPE))
						.executes(context -> spawn(context.getSource(),
								ResourceArgument.getResource(context, "tipo", Registries.ENTITY_TYPE), 1))
						.then(Commands.argument("quantita", IntegerArgumentType.integer(1, 64))
								.executes(context -> spawn(context.getSource(),
										ResourceArgument.getResource(context, "tipo", Registries.ENTITY_TYPE),
										IntegerArgumentType.getInteger(context, "quantita"))))));

		LiteralArgumentBuilder<CommandSourceStack> gear = Commands.literal("gear")
				.requires(AriseCommands::canCheat)
				.executes(context -> giveGear(context.getSource(), Gear.NETHERITE, buildContext));

		for (Gear tier : Gear.values()) {
			gear.then(Commands.literal(tier.name)
					.executes(context -> giveGear(context.getSource(), tier, buildContext)));
		}

		root.then(gear);

		LiteralArgumentBuilder<CommandSourceStack> fx = Commands.literal("fx")
				.requires(AriseCommands::canCheat);

		for (Effect effect : Effect.values()) {
			fx.then(Commands.literal(effect.name)
					.executes(context -> playEffect(context.getSource(), effect)));
		}

		root.then(fx);

		root.then(Commands.literal("arena")
				.requires(AriseCommands::canCheat)
				.executes(context -> arena(context.getSource(), 50))
				.then(Commands.argument("lato", IntegerArgumentType.integer(8, 128))
						.executes(context -> arena(context.getSource(),
								IntegerArgumentType.getInteger(context, "lato")))));
	}

	private static int playEffect(CommandSourceStack source, Effect effect) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		effect.action.accept(player.level(), player);
		source.sendSuccess(() -> Component.literal("fx: " + effect.name), false);
		return 1;
	}

	// ---------------------------------------------------------------- arena

	private static final int ARENA_HEIGHT = 20;
	private static final int WALL_HEIGHT = 8;

	/**
	 * Passo della griglia di lanterne nel pavimento.
	 *
	 * <p>Otto blocchi: una lanterna marina emette luce 15, che scende di uno al blocco, quindi a
	 * metà strada fra due lanterne si resta sopra 11. Gli ostili spawnano solo a luce zero, così
	 * l'arena resta vuota mentre si prova.
	 */
	private static final int LAMP_SPACING = 8;

	/**
	 * Costruisce un'arena quadrata attorno al giocatore, ovunque si trovi.
	 *
	 * <p>Serve a provare evocazioni e combattimenti senza cercare un posto piatto. Sostituisce il
	 * terreno: è un comando da gamemaster e non chiede conferma, come {@code /fill}.
	 */
	private static int arena(CommandSourceStack source, int side) throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = player.level();

		BlockPos center = player.blockPosition();
		int half = side / 2;
		int floorY = center.getY() - 1;

		BlockState floor = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
		BlockState wall = Blocks.DEEPSLATE_BRICKS.defaultBlockState();
		BlockState lamp = Blocks.SEA_LANTERN.defaultBlockState();
		BlockState air = Blocks.AIR.defaultBlockState();

		// Flag 2 = avvisa i client senza propagare aggiornamenti ai blocchi vicini. Su decine di
		// migliaia di blocchi la differenza fra questo e un aggiornamento completo è fra un attimo
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

				cursor.set(center.getX() + dx, floorY, center.getZ() + dz);
				level.setBlock(cursor, floorLamp ? lamp : floor, flags);
				placed++;

				for (int dy = 0; dy < ARENA_HEIGHT; dy++) {
					cursor.set(center.getX() + dx, floorY + 1 + dy, center.getZ() + dz);

					if (edge && dy < WALL_HEIGHT) {
						// Fascia di lanterne a metà muro, per illuminare anche i bordi dove il
						// pavimento non ne ha.
						boolean wallLamp = dy == WALL_HEIGHT / 2
								&& (Math.floorMod(dx, LAMP_SPACING) == 0 || Math.floorMod(dz, LAMP_SPACING) == 0);
						level.setBlock(cursor, wallLamp ? lamp : wall, flags);
					} else {
						level.setBlock(cursor, air, flags);
					}

					placed++;
				}
			}
		}

		int total = placed;
		source.sendSuccess(() -> Component.translatable("arise.msg.debug.arena", side, total), true);
		return side;
	}

	// ---------------------------------------------------------------- spawn

	private static int spawn(CommandSourceStack source, Holder.Reference<EntityType<?>> holder, int count)
			throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ServerLevel level = player.level();
		EntityType<?> type = holder.value();

		int spawned = 0;
		for (int i = 0; i < count; i++) {
			Entity entity = type.create(level, EntitySpawnReason.COMMAND);
			if (entity == null) {
				continue;
			}

			// In cerchio attorno al giocatore, a 4 blocchi: abbastanza lontani da non spingerti,
			// abbastanza vicini da vederli tutti. Il raggio cresce se sono tanti, così non si
			// compenetrano.
			double radius = 3.0 + count * 0.25;
			double angle = (Math.PI * 2 * i) / count;
			entity.snapTo(player.getX() + Math.cos(angle) * radius, player.getY(),
					player.getZ() + Math.sin(angle) * radius, player.getYRot() + 180.0F, 0.0F);

			if (level.addFreshEntity(entity)) {
				spawned++;
			}
		}

		int total = spawned;
		source.sendSuccess(() -> Component.translatable("arise.msg.debug.spawned",
				total, type.getDescription()), true);
		return spawned;
	}

	// ---------------------------------------------------------------- equipaggiamento

	private static int giveGear(CommandSourceStack source, Gear tier, CommandBuildContext buildContext)
			throws CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		HolderLookup.RegistryLookup<Enchantment> enchantments = buildContext.lookupOrThrow(Registries.ENCHANTMENT);

		player.setItemSlot(EquipmentSlot.HEAD, enchanted(tier.helmet, tier, enchantments, false));
		player.setItemSlot(EquipmentSlot.CHEST, enchanted(tier.chestplate, tier, enchantments, false));
		player.setItemSlot(EquipmentSlot.LEGS, enchanted(tier.leggings, tier, enchantments, false));
		player.setItemSlot(EquipmentSlot.FEET, enchanted(tier.boots, tier, enchantments, false));
		player.setItemSlot(EquipmentSlot.MAINHAND, enchanted(tier.sword, tier, enchantments, true));

		// Cibo e mele d'oro: senza, ogni prova in Sopravvivenza finisce per fame o per un errore
		// da cui non si torna indietro.
		player.addItem(new ItemStack(Items.COOKED_BEEF, 64));
		player.addItem(new ItemStack(Items.GOLDEN_APPLE, 16));

		source.sendSuccess(() -> Component.translatable("arise.msg.debug.gear", tier.name), true);
		return 1;
	}

	private static ItemStack enchanted(Item item, Gear tier, HolderLookup.RegistryLookup<Enchantment> lookup,
			boolean weapon) {
		ItemStack stack = new ItemStack(item);

		if (tier.enchantLevel > 0) {
			// Gli incantesimi sono dati da datapack: vanno risolti dal registro, non da costanti.
			// Se un datapack li rimuove, l'oggetto esce semplicemente senza incantesimi.
			List<net.minecraft.resources.ResourceKey<Enchantment>> keys = weapon
					? List.of(Enchantments.SHARPNESS, Enchantments.UNBREAKING)
					: List.of(Enchantments.PROTECTION, Enchantments.UNBREAKING);

			for (var key : keys) {
				lookup.get(key).ifPresent(enchantment -> stack.enchant(enchantment, tier.enchantLevel));
			}
		}

		return stack;
	}
}
