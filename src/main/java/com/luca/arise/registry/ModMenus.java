package com.luca.arise.registry;

import com.luca.arise.AriseMod;
import com.luca.arise.gear.GearManager;
import com.luca.arise.gear.HunterMenu;
import com.luca.arise.progress.Rank;
import com.luca.arise.workshop.MachineKind;
import com.luca.arise.workshop.MachineMenu;

import io.netty.buffer.ByteBuf;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * I menu della mod: quello del Cacciatore e quello dei macchinari.
 *
 * <p>E' un {@code ExtendedMenuType} e non un {@code MenuType} qualunque perche' il client deve
 * sapere una cosa che non puo' dedurre da solo: <strong>il rango</strong>. Da quello dipende quali
 * caselle sono aperte, e ricavarlo dal livello sul client vorrebbe dire fidarsi che la sua copia
 * dei numeri di bilanciamento sia identica a quella del server. Su un server dedicato non e' detto,
 * e il risultato sarebbero caselle disegnate aperte che poi rifiutano il pezzo.
 */
public final class ModMenus {

	/** Il rango viaggia come un numero: e' un enum di sei voci, non serve altro. */
	private static final StreamCodec<ByteBuf, Rank> RANK = ByteBufCodecs.VAR_INT.map(
			index -> Rank.values()[Math.clamp(index, 0, Rank.values().length - 1)], Rank::ordinal);

	public static final ExtendedMenuType<HunterMenu, Rank> HUNTER = Registry.register(
			BuiltInRegistries.MENU, AriseMod.id("hunter"),
			new ExtendedMenuType<>(HunterMenu::new, RANK));

	/**
	 * Il menu di un macchinario dell'Officina.
	 *
	 * <p>Stessa forma di quello del Cacciatore, e per lo stesso motivo: il client deve sapere
	 * <em>quale</em> dei quattro macchinari sta aprendo, e non puo' dedurlo dal menu. Da quello
	 * dipende quante caselle disegnare e dove. Il {@link MachineKind} viaggia con l'apertura.
	 */
	public static final ExtendedMenuType<MachineMenu, MachineKind> MACHINE = Registry.register(
			BuiltInRegistries.MENU, AriseMod.id("machine"),
			new ExtendedMenuType<>(MachineMenu::new, MachineKind.STREAM_CODEC));

	private ModMenus() {
	}

	/** Forza il caricamento della classe, e con essa la registrazione. */
	public static void init() {
	}

	/** Apre il menu del Cacciatore. Da chiamare solo lato server: e' li' che i menu nascono. */
	public static void open(ServerPlayer player) {
		player.openMenu(new Provider());
	}

	/**
	 * Chi costruisce il menu quando il giocatore lo apre.
	 *
	 * <p>Serve una classe invece di una lambda perche' deve rispondere a due domande: come si
	 * chiama la finestra, e che dato accompagna l'apertura.
	 */
	private record Provider() implements ExtendedMenuProvider<Rank> {

		@Override
		public Component getDisplayName() {
			return Component.translatable("arise.screen.hunter.title");
		}

		@Override
		public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
			return HunterMenu.forPlayer(containerId, inventory, (ServerPlayer) player);
		}

		@Override
		public Rank getScreenOpeningData(ServerPlayer player) {
			return GearManager.hunterRank(player);
		}
	}
}
