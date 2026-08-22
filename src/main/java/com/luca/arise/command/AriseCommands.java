package com.luca.arise.command;

import java.util.function.Function;

import com.luca.arise.city.City;
import com.luca.arise.city.CityManager;
import com.luca.arise.event.CityEvents;
import com.luca.arise.config.GearConfig;
import com.luca.arise.gate.GateManager;
import com.luca.arise.gate.GateSpawner;
import com.luca.arise.gear.GearManager;
import com.luca.arise.npc.NpcManager;
import java.util.List;

import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.GearRoll;
import com.luca.arise.gear.GearSlot;
import com.luca.arise.gem.GemManager;
import com.luca.arise.gem.GemType;
import com.luca.arise.progress.Rank;
import com.luca.arise.quest.PlayerQuests;
import com.luca.arise.quest.Quest;
import com.luca.arise.quest.QuestManager;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.CityConfig;
import com.luca.arise.config.ShadowConfig;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.ProgressManager;
import com.luca.arise.progress.Stat;
import com.luca.arise.shadow.ShadowArmy;
import com.luca.arise.shop.ShopManager;
import com.luca.arise.shop.ShopOffer;
import com.luca.arise.shop.ShopStock;
import com.luca.arise.shadow.ShadowData;
import com.luca.arise.shadow.ShadowManager;
import com.luca.arise.shadow.ShadowStance;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.permissions.PermissionCheck;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Comandi di debug e di gioco.
 *
 * <p>Esistono dal primo giorno perche' senza, testare la progressione significa uccidere mob per
 * mezz'ora a ogni modifica.
 */
public final class AriseCommands {

	/**
	 * Permesso richiesto dai comandi che alterano la progressione.
	 *
	 * <p>In 26.2 i livelli di permesso numerici non esistono piu': {@code hasPermission(2)} e'
	 * diventato un {@link net.minecraft.server.permissions.PermissionCheck} da valutare contro il
	 * {@code PermissionSet} della sorgente. {@code LEVEL_GAMEMASTERS} e' l'equivalente del vecchio
	 * livello 2, quello dei comandi che barano.
	 */
	private static final PermissionCheck CHEATS = Commands.LEVEL_GAMEMASTERS;

	private AriseCommands() {
	}

	static boolean canCheat(CommandSourceStack source) {
		return CHEATS.check(source.permissions());
	}

	public static void register() {
		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
			LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("arise");

			root.then(Commands.literal("info").executes(context -> info(context.getSource())));

			LiteralArgumentBuilder<CommandSourceStack> spend = Commands.literal("spend");
			for (Stat stat : Stat.SPENDABLE) {
				spend.then(Commands.literal(stat.getSerializedName())
						.then(Commands.argument("punti", IntegerArgumentType.integer(1))
								.executes(context -> spend(context.getSource(), stat,
										IntegerArgumentType.getInteger(context, "punti")))));
			}
			root.then(spend);

			root.then(Commands.literal("xp")
					.requires(AriseCommands::canCheat)
					.then(Commands.argument("quantita", IntegerArgumentType.integer(1))
							.executes(context -> addXp(context.getSource(),
									IntegerArgumentType.getInteger(context, "quantita")))));

			root.then(Commands.literal("level")
					.requires(AriseCommands::canCheat)
					.then(Commands.argument("livello", IntegerArgumentType.integer(1))
							.executes(context -> setLevel(context.getSource(),
									IntegerArgumentType.getInteger(context, "livello")))));

			root.then(Commands.literal("reset")
					.requires(AriseCommands::canCheat)
					.executes(context -> reset(context.getSource())));

			root.then(Commands.literal("arise").executes(context -> playerAction(context.getSource(),
					ShadowManager::extract)));
			root.then(Commands.literal("summon").executes(context -> playerAction(context.getSource(),
					ShadowManager::summon)));
			root.then(Commands.literal("recall").executes(context -> playerAction(context.getSource(),
					ShadowManager::recall)));

			LiteralArgumentBuilder<CommandSourceStack> stance = Commands.literal("stance")
					.executes(context -> playerAction(context.getSource(), ShadowManager::cycleStance));
			for (ShadowStance value : ShadowStance.values()) {
				stance.then(Commands.literal(value.getSerializedName())
						.executes(context -> playerAction(context.getSource(),
								player -> ShadowManager.setStance(player, value))));
			}
			root.then(stance);

			root.then(Commands.literal("souls")
					.executes(context -> showSouls(context.getSource()))
					.then(Commands.literal("add")
							.requires(AriseCommands::canCheat)
							.then(Commands.argument("quantita", IntegerArgumentType.integer(1))
									.executes(context -> addSouls(context.getSource(),
											IntegerArgumentType.getInteger(context, "quantita"))))));

			LiteralArgumentBuilder<CommandSourceStack> gate = Commands.literal("gate");
			for (Rank rank : Rank.values()) {
				gate.then(Commands.literal(rank.getSerializedName())
						.executes(context -> playerAction(context.getSource(),
								player -> GateManager.offer(player, rank))));
			}
			gate.executes(context -> playerAction(context.getSource(),
					player -> GateManager.offer(player, Rank.E)));
			gate.then(Commands.literal("spawn")
					.requires(AriseCommands::canCheat)
					.executes(context -> playerAction(context.getSource(), GateSpawner::spawnNow)));
			root.then(gate);

			LiteralArgumentBuilder<CommandSourceStack> city = Commands.literal("city");

			LiteralArgumentBuilder<CommandSourceStack> cityBuild = Commands.literal("build")
					.requires(AriseCommands::canCheat);
			LiteralArgumentBuilder<CommandSourceStack> cityGo = Commands.literal("go")
					.requires(AriseCommands::canCheat);

			for (City target : City.values()) {
				cityBuild.then(Commands.literal(target.getSerializedName())
						.executes(context -> buildCity(context.getSource(), target)));
				cityGo.then(Commands.literal(target.getSerializedName())
						.executes(context -> playerAction(context.getSource(),
								player -> CityManager.travel(player, target))));
			}

			cityBuild.then(Commands.literal("all")
					.executes(context -> buildAll(context.getSource())));

			city.then(Commands.literal("setup")
					.requires(AriseCommands::canCheat)
					.executes(context -> setupWorld(context.getSource())));

			// Ripopolare le botteghe della citta' in cui ci si trova. E' idempotente: rimette solo
			// chi manca, quindi si puo' battere il comando a raffica senza duplicare nessuno.
			city.then(Commands.literal("npcs")
					.requires(AriseCommands::canCheat)
					.executes(context -> repopulate(context.getSource())));

			city.then(cityBuild);
			city.then(cityGo);
			root.then(city);

			LiteralArgumentBuilder<CommandSourceStack> gear = Commands.literal("gear")
					.executes(context -> listGear(context.getSource()));

			// Un nodo per slot e per rango: piu' verboso di un argomento libero, ma il
			// completamento automatico diventa l'elenco vero delle possibilita'.
			LiteralArgumentBuilder<CommandSourceStack> gearGive = Commands.literal("give")
					.requires(AriseCommands::canCheat);

			for (GearSlot slot : GearSlot.values()) {
				LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(slot.getSerializedName());

				for (Rank rank : Rank.values()) {
					node.then(Commands.literal(rank.getSerializedName())
							.executes(context -> playerAction(context.getSource(),
									player -> GearManager.grant(player, GearManager.roll(player, slot, rank)))));
				}

				gearGive.then(node);
			}
			gear.then(gearGive);

			LiteralArgumentBuilder<CommandSourceStack> gearRoll = Commands.literal("roll")
					.requires(AriseCommands::canCheat);

			for (Rank rank : Rank.values()) {
				gearRoll.then(Commands.literal(rank.getSerializedName())
						.then(Commands.argument("quantita", IntegerArgumentType.integer(1, 40))
								.executes(context -> rollGear(context.getSource(), rank,
										IntegerArgumentType.getInteger(context, "quantita"))))
						.executes(context -> rollGear(context.getSource(), rank, 1)));
			}
			gear.then(gearRoll);

			gear.then(Commands.literal("clear")
					.requires(AriseCommands::canCheat)
					.executes(context -> clearGear(context.getSource())));

			root.then(gear);

			LiteralArgumentBuilder<CommandSourceStack> gem = Commands.literal("gem");
			LiteralArgumentBuilder<CommandSourceStack> gemGive = Commands.literal("give")
					.requires(AriseCommands::canCheat);

			for (GemType type : GemType.values()) {
				LiteralArgumentBuilder<CommandSourceStack> node = Commands.literal(type.getSerializedName());

				for (Rank rank : Rank.values()) {
					node.then(Commands.literal(rank.getSerializedName())
							.executes(context -> playerAction(context.getSource(),
									player -> GemManager.grant(player, GemManager.roll(player, type, rank)))));
				}

				gemGive.then(node);
			}

			gem.then(gemGive);
			root.then(gem);

			root.then(Commands.literal("quest")
					.executes(context -> playerAction(context.getSource(), player -> {
						PlayerQuests quests = QuestManager.get(player);
						Quest quest = quests.current();

						return quest == null
								? Component.translatable("arise.msg.quest.chain_done")
								: Component.translatable("arise.msg.quest.status", quest.title(),
										quest.description(), quests.progress(), quest.amount());
					}))
					.then(Commands.literal("skip")
							.requires(AriseCommands::canCheat)
							.executes(context -> playerAction(context.getSource(), QuestManager::skip)))
					.then(Commands.literal("all")
							.requires(AriseCommands::canCheat)
							.executes(context -> playerAction(context.getSource(), QuestManager::grantAll)))
					.then(Commands.literal("reset")
							.requires(AriseCommands::canCheat)
							.executes(context -> playerAction(context.getSource(), QuestManager::reset))));

			root.then(Commands.literal("shop")
					.executes(context -> listShop(context.getSource()))
					.then(Commands.literal("refresh")
							.executes(context -> playerAction(context.getSource(), ShopManager::refresh))));

			root.then(Commands.literal("hub")
					.requires(AriseCommands::canCheat)
					.executes(context -> openHub(context.getSource())));

			root.then(Commands.literal("leave")
					.executes(context -> playerAction(context.getSource(), GateManager::leave)));

			root.then(Commands.literal("shadows")
					.executes(context -> listShadows(context.getSource()))
					.then(Commands.literal("clear")
							.requires(AriseCommands::canCheat)
							.executes(context -> clearShadows(context.getSource()))));

			DebugCommands.addTo(root, registryAccess);

			dispatcher.register(root);
		});
	}

	private static int info(CommandSourceStack source) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		PlayerProgress progress = ProgressManager.get(player);
		AriseConfig config = AriseConfig.get();

		source.sendSuccess(() -> Component.translatable("arise.msg.info.header"), false);
		source.sendSuccess(() -> Component.translatable("arise.msg.info.level",
				progress.level(), progress.xp(), config.xpForNextLevel(progress.level())), false);
		source.sendSuccess(() -> Component.translatable("arise.msg.info.points",
				progress.unspentPoints()), false);

		for (Stat stat : Stat.SPENDABLE) {
			source.sendSuccess(() -> Component.translatable("arise.msg.info.stat",
					Component.translatable(stat.translationKey()),
					progress.stat(stat),
					config.cap(stat),
					String.format("%.2f", player.getAttributeValue(stat.attribute()))), false);
		}

		return progress.level();
	}

	private static int spend(CommandSourceStack source, Stat stat, int amount)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Component error = ProgressManager.spend(player, stat, amount);

		if (error != null) {
			source.sendFailure(error);
			return 0;
		}

		int total = ProgressManager.get(player).stat(stat);
		source.sendSuccess(() -> Component.translatable("arise.msg.spend.ok",
				amount, Component.translatable(stat.translationKey()), total), false);
		return total;
	}

	private static int addXp(CommandSourceStack source, int amount)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ProgressManager.addXp(player, amount);
		PlayerProgress progress = ProgressManager.get(player);
		source.sendSuccess(() -> Component.translatable("arise.msg.xp.added", amount, progress.level()), true);
		return amount;
	}

	private static int setLevel(CommandSourceStack source, int level)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ProgressManager.setLevel(player, level);
		PlayerProgress progress = ProgressManager.get(player);
		source.sendSuccess(() -> Component.translatable("arise.msg.level.set",
				progress.level(), progress.unspentPoints()), true);
		return progress.level();
	}

	private static int reset(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ProgressManager.reset(player);
		source.sendSuccess(() -> Component.translatable("arise.msg.reset"), true);
		return 1;
	}

	/** Le azioni restituiscono già il messaggio giusto: qui si inoltra e basta. */
	private static int playerAction(CommandSourceStack source, Function<ServerPlayer, Component> action)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Component feedback = action.apply(player);
		source.sendSuccess(() -> feedback, false);
		return 1;
	}

	private static int showSouls(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		long souls = ProgressManager.souls(player);
		source.sendSuccess(() -> Component.translatable("arise.msg.souls.balance", souls), false);
		return (int) Math.min(Integer.MAX_VALUE, souls);
	}

	private static int addSouls(CommandSourceStack source, int amount)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ProgressManager.addSouls(player, amount);
		long souls = ProgressManager.souls(player);
		source.sendSuccess(() -> Component.translatable("arise.msg.souls.added", amount, souls), true);
		return amount;
	}

	/**
	 * Avvia un cantiere. Funziona anche dalla console, dove non c'è un giocatore che lo chiede.
	 */
	private static int buildCity(CommandSourceStack source, City target) {
		Component feedback = CityManager.build(source.getServer(), source.getPlayer(), target);
		source.sendSuccess(() -> feedback, true);
		return 1;
	}

	/**
	 * Mette in coda tutte le città.
	 *
	 * <p>Entrano tutte in coda e si costruiscono <em>una per volta</em>: il budget di blocchi per
	 * battito vale per il cantiere aperto, non per ognuno dei cinque. Se il gioco scatta lo stesso,
	 * si abbassa {@code blocks_per_tick}.
	 */
	/**
	 * Il mondo pronto: tira su quello che manca e basta.
	 *
	 * <p>Diverso da {@code city build all}, che si lamenta di ogni città già esistente. Qui il
	 * silenzio su quelle che ci sono già è il comportamento giusto: si sta chiedendo un mondo
	 * pronto, non cinque costruzioni.
	 */
	/**
	 * Rimette le nove persone dietro i banconi della citta' piu' vicina.
	 *
	 * <p>Serve a due casi che capiteranno entrambi: una citta' costruita prima che il mercato
	 * esistesse, e una citta' in cui qualcuno ha fatto una sciocchezza con {@code /kill}.
	 */
	private static int repopulate(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		net.minecraft.server.level.ServerLevel level = player.level();
		CityConfig config = AriseConfig.get().cities();

		City nearest = null;
		double best = Double.MAX_VALUE;

		for (City candidate : City.values()) {
			double dx = player.getX() - config.centreX(candidate);
			double dz = player.getZ() - config.centreZ(candidate);
			double distance = dx * dx + dz * dz;

			if (distance < best) {
				best = distance;
				nearest = candidate;
			}
		}

		if (nearest == null || best > (double) config.size() * config.size()) {
			source.sendFailure(Component.translatable("arise.msg.city.not_here"));
			return 0;
		}

		int placed = NpcManager.populate(level, nearest, player.blockPosition().getY() - 1);
		City city = nearest;

		source.sendSuccess(() -> Component.translatable("arise.msg.city.npcs", placed, city.label()),
				true);
		return placed;
	}

	private static int setupWorld(CommandSourceStack source) {
		int started = CityManager.setup(source.getServer(), source.getPlayer());

		source.sendSuccess(() -> started == 0
				? Component.translatable("arise.msg.city.setup_done")
				: Component.translatable("arise.msg.city.setup", started), true);

		return started;
	}

	private static int buildAll(CommandSourceStack source) {
		for (City target : City.values()) {
			buildCity(source, target);
		}

		return City.values().length;
	}

	private static int openHub(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		CityEvents.openHub(source.getPlayerOrException());
		return 1;
	}

	/** I Gate restituiscono già il messaggio giusto: qui si inoltra e basta. */
	/** Un pezzo a caso per ogni tiro: e' la forma che avra' il bottino dei Gate. */
	private static int rollGear(CommandSourceStack source, Rank rank, int count)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		GearConfig config = AriseConfig.get().gear();

		for (int i = 0; i < count; i++) {
			GearManager.grant(player, GearRoll.rollAny(config, rank, player.level().getRandom()));
		}

		source.sendSuccess(() -> Component.translatable("arise.msg.gear.rolled", count, rank.label()), false);
		return count;
	}

	private static int listGear(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		Rank rank = GearManager.hunterRank(player);
		List<GearPiece> worn = GearManager.worn(player);

		source.sendSuccess(() -> Component.translatable("arise.msg.gear.header",
				rank.label(), worn.size(), GearManager.owned(player).size()), false);

		for (GearPiece piece : worn) {
			source.sendSuccess(() -> Component.translatable("arise.msg.gear.line",
					piece.slot().label(), piece.displayName(), piece.statSummary()), false);
		}

		return worn.size();
	}

	private static int clearGear(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		int count = GearManager.owned(player).size();

		GearManager.clear(player);
		source.sendSuccess(() -> Component.translatable("arise.msg.gear.cleared", count), false);
		return count;
	}

	/** L'assortimento in chat: serve a provare rotazione e prezzi senza aprire la schermata. */
	private static int listShop(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ShopStock stock = ShopManager.stock(player);
		long minutes = ShopManager.ticksToRotation(player) / 1200L;

		source.sendSuccess(() -> Component.translatable("arise.msg.shop.header",
				stock.offers().size(), ShopManager.refreshPrice(player), minutes), false);

		for (ShopOffer offer : stock.offers()) {
			source.sendSuccess(() -> Component.translatable("arise.msg.shop.line",
					offer.label(), offer.price()), false);
		}

		return stock.offers().size();
	}

	private static int listShadows(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ShadowArmy army = ShadowManager.army(player);
		int capacity = ShadowManager.capacity(player);

		source.sendSuccess(() -> Component.translatable("arise.msg.shadow.list_header",
				army.size(), capacity, ShadowManager.summonedCount(player)), false);

		ShadowConfig shadowConfig = AriseConfig.get().shadows();
		for (ShadowData shadow : army.shadows()) {
			source.sendSuccess(() -> Component.translatable("arise.msg.shadow.list_entry",
					shadow.rank(shadowConfig).label(), shadow.displayName(), shadow.level(),
					String.format("%.0f", shadow.maxHealth(shadowConfig)),
					String.format("%.1f", shadow.attackDamage(shadowConfig))), false);
		}

		return army.size();
	}

	private static int clearShadows(CommandSourceStack source)
			throws com.mojang.brigadier.exceptions.CommandSyntaxException {
		ServerPlayer player = source.getPlayerOrException();
		ShadowManager.clear(player);
		source.sendSuccess(() -> Component.translatable("arise.msg.shadow.cleared"), true);
		return 1;
	}
}
