package com.luca.arise.client;

import com.luca.arise.client.mixin.ContainerScreenAccessor;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.client.ui.Glyphs;
import com.luca.arise.network.AriseActionPayload;
import com.luca.arise.quest.PlayerQuests;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Il bottone che porta dall'inventario normale a quello del Cacciatore.
 *
 * <p>E' il punto di raccordo fra le due meta' dell'equipaggiamento. Elmo, corazza, gambali,
 * stivali e arma stanno nelle caselle di vanilla, dove sono sempre stati; guanti, anelli e
 * mantello stanno nelle nostre. Un tasto (N) le collega, ma un tasto va saputo: il bottone e'
 * quello che lo fa scoprire a chi apre l'inventario e basta.
 *
 * <p>Sta <em>accanto</em> al pannello dell'inventario e non dentro, perche' dentro non c'e' spazio
 * libero che non sia gia' di qualcos'altro. La posizione si ricalcola a ogni tick: il pannello si
 * sposta quando si apre il libro delle ricette, e un bottone fermo finirebbe sotto le ricette.
 */
public final class HunterButton {

	private static final int SIZE = 20;

	private HunterButton() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			if (!(screen instanceof InventoryScreen inventory)) {
				return;
			}

			GearButton button = new GearButton();
			place(button, inventory);

			Screens.getWidgets(screen).add(button);
			ScreenEvents.afterTick(screen).register(ticked -> place(button, inventory));
		});
	}

	private static void place(GearButton button, InventoryScreen screen) {
		ContainerScreenAccessor position = (ContainerScreenAccessor) screen;

		button.setX(position.arise$leftPos() + 176 + 3);
		button.setY(position.arise$topPos() + 4);
	}

	private static void open() {
		ClientPlayNetworking.send(new AriseActionPayload(AriseActionPayload.Action.OPEN_GEAR));
	}

	/**
	 * Un bottone senza scritta, con il rombo del Sistema disegnato sopra.
	 *
	 * <p>Venti pixel non reggono una parola in nessuna lingua, e un'icona in una texture
	 * significherebbe una texture: il rombo lo disegna gia' {@link Glyphs} per i ranghi.
	 */
	private static class GearButton extends Button {

		GearButton() {
			super(0, 0, SIZE, SIZE, Component.empty(), press -> open(), DEFAULT_NARRATION);
			setTooltip(Tooltip.create(Component.translatable("arise.screen.hunter.title")));
		}

		@Override
		protected void extractContents(GuiGraphicsExtractor graphics, int mouseX, int mouseY,
				float partialTick) {
			extractDefaultSprite(graphics);
			Glyphs.rankPip(graphics, getX() + 6, getY() + 6, 8, colour());
		}

		/** Spento finche' il Sistema non ha concesso l'equipaggiamento: il bottone dice anche questo. */
		private int colour() {
			LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
			PlayerQuests quests = player == null ? null : player.getAttached(ModAttachments.QUESTS);
			boolean unlocked = quests != null && quests.has(Unlock.GEAR);

			return unlocked ? AriseTheme.ACCENT : AriseTheme.DISABLED;
		}
	}
}
