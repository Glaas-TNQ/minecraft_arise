package com.luca.arise.gate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.fx.AriseFx;
import com.luca.arise.gear.GearManager;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.GearRoll;
import com.luca.arise.gem.Gem;
import com.luca.arise.gem.GemManager;
import com.luca.arise.gem.GemRoll;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModComponents;
import com.luca.arise.registry.ModItems;
import com.luca.arise.workshop.SoulItems;
import com.luca.arise.workshop.WorkshopManager;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

/**
 * Il Cubo dell'Abisso: la stessa scatola, due modi di aprirla.
 *
 * <p>Ogni varco chiuso ne lascia uno. Aprirlo <strong>normalmente</strong> lo apre benedetto;
 * aprirlo <strong>tenendo shift</strong> lo apre maledetto. Un cubo, un tasto di differenza, e una
 * decisione che si ripresenta a ogni run.
 *
 * <ul>
 *   <li><strong>Benedetto</strong>: una gemma del rango del varco, un catalizzatore, e delle
 *       Monete d'Anima. Tabella buona, prevedibile, tetto basso. Serve <em>adesso</em>.
 *   <li><strong>Maledetto</strong>: due pezzi di equipaggiamento di <em>un rango sopra</em>. Valore
 *       atteso piu' alto, ma differito — un pezzo sopra il proprio rango il Cacciatore non lo puo'
 *       indossare, e resta nello spazio dimensionale finche' non cresce abbastanza.
 * </ul>
 *
 * <p>Il maledetto non e' peggiore: e' peggiore <em>adesso</em> e migliore dopo. Chi conosce la mod
 * lo prende quasi sempre; chi e' al primo mondo prende il benedetto e ha ragione lui, perche' a
 * rango E due pezzi di rango D sono due oggetti da guardare per dieci livelli. <strong>Entrambe le
 * scelte sono giuste, in momenti diversi</strong>, che e' la definizione di una buona scelta
 * ricorrente.
 *
 * <p>Il cubo non contiene niente finche' non lo si apre: tira nel momento in cui viene aperto. Due
 * cubi dello stesso rango non danno la stessa cosa, e non c'e' modo di sbirciare dentro.
 *
 * <p><strong>Perche' due tasti e non una schermata.</strong> Una finestra con due bottoni avrebbe
 * detto meglio le due tabelle, e sarebbe stata la dodicesima schermata di una mod che ne ha gia'
 * undici. Lo shift che inverte il verso e' la convenzione che il Quartiere del Mercato ha gia'
 * stabilito — al Banco conia e riporta indietro, allo Sportello viaggia e apre il negozio, e con
 * un'anima in mano arruola — e una convenzione gia' imparata vale piu' di un'interfaccia nuova.
 */
public final class AbyssCubeItem extends Item {

	/** Quante Monete d'Anima escono dal benedetto, per gradino di rango. */
	private static final int COINS_PER_RANK = 4;

	/** Quanti pezzi lascia il maledetto. */
	private static final int CURSED_PIECES = 2;

	public AbyssCubeItem(Properties properties) {
		super(properties);
	}

	/** Un cubo di questo rango. */
	public static ItemStack of(Rank rank) {
		ItemStack stack = new ItemStack(ModItems.ABYSS_CUBE);
		stack.set(ModComponents.ABYSS_CUBE, new AbyssCube(rank));
		return stack;
	}

	/** Il rango di questo cubo, o E se qualcuno lo ha costruito senza. */
	public static Rank rankOf(ItemStack stack) {
		AbyssCube cube = stack.get(ModComponents.ABYSS_CUBE);
		return cube == null ? Rank.E : cube.rank();
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		ItemStack stack = player.getItemInHand(hand);
		Rank rank = rankOf(stack);
		boolean cursed = player.isShiftKeyDown();

		List<Component> lines = cursed
				? openCursed(serverPlayer, rank)
				: openBlessed(serverPlayer, rank);

		stack.shrink(1);

		serverPlayer.sendSystemMessage(Component.translatable(cursed
						? "arise.msg.cube.cursed"
						: "arise.msg.cube.blessed", rank.label())
				.withStyle(cursed ? ChatFormatting.DARK_PURPLE : ChatFormatting.GOLD));

		lines.forEach(serverPlayer::sendSystemMessage);
		AriseFx.cubeOpened(serverPlayer.level(), serverPlayer.position(), rank, cursed);

		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * La via benedetta: quello che serve stasera.
	 *
	 * <p>Una gemma, un catalizzatore e delle monete. Nessuno dei tre e' spettacolare, e tutti e tre
	 * sono cose di cui si ha sempre bisogno — le gemme perche' i tetti degli effetti sono lontani, i
	 * catalizzatori perche' l'unica altra fonte e' il Pozzo all'otto per cento, le monete perche' il
	 * mercato le mangia.
	 */
	private static List<Component> openBlessed(ServerPlayer player, Rank rank) {
		AriseConfig config = AriseConfig.get();
		RandomSource random = player.level().getRandom();
		List<Component> lines = new ArrayList<>();

		Gem gem = GemRoll.rollAny(config.gems(), config.gear(), rank, random);
		lines.add(GemManager.grant(player, gem));

		WorkshopManager.give(player, SoulItems.catalyst(rank, 1));
		WorkshopManager.give(player, ModItems.coins((rank.ordinal() + 1) * COINS_PER_RANK));

		return lines;
	}

	/**
	 * La via maledetta: due pezzi di un rango sopra.
	 *
	 * <p>Il sigillo non e' un meccanismo nuovo — e' quello che l'equipaggiamento fa gia'. Un pezzo
	 * sopra il rango del Cacciatore non entra in nessuna casella e resta nello spazio dimensionale,
	 * visibile, con scritto quale rango serve. E' letteralmente «sigillato, non ancora», scritto con
	 * codice che c'era.
	 *
	 * <p>Al rango S non c'e' un rango sopra: li' il maledetto da' due pezzi di rango S, che e' un
	 * ottimo affare, ed e' giusto — a quel punto il giocatore ha finito di aspettare.
	 */
	private static List<Component> openCursed(ServerPlayer player, Rank rank) {
		AriseConfig config = AriseConfig.get();
		RandomSource random = player.level().getRandom();
		Rank above = Rank.values()[Math.min(Rank.values().length - 1, rank.ordinal() + 1)];
		List<Component> lines = new ArrayList<>();

		for (int i = 0; i < CURSED_PIECES; i++) {
			GearPiece piece = GearRoll.rollAny(config.gear(), above, random);
			lines.add(GearManager.grant(player, piece));
		}

		return lines;
	}

	/**
	 * Tre righe, e la terza e' quella che serve.
	 *
	 * <p>Un oggetto che si apre in due modi diversi e' inutile se il secondo modo non e' scritto
	 * addosso: nessuno prova a tenere shift su un cubo per vedere che succede.
	 */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> out, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, out, flag);

		Rank rank = rankOf(stack);

		out.accept(Component.translatable("arise.gear.tooltip.rank", rank.label())
				.withStyle(style -> style.withColor(rank.color())));
		out.accept(Component.translatable("arise.item.abyss_cube.blessed")
				.withStyle(ChatFormatting.GOLD));
		out.accept(Component.translatable("arise.item.abyss_cube.cursed")
				.withStyle(ChatFormatting.DARK_PURPLE));
	}
}
