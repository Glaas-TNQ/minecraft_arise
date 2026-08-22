package com.luca.arise.client;

import com.luca.arise.client.screen.AbyssShopScreen;
import com.luca.arise.client.screen.ArmyScreen;
import com.luca.arise.client.screen.HunterScreen;
import com.luca.arise.client.screen.StatusScreen;
import com.luca.arise.network.AriseActionPayload;
import com.luca.arise.network.ShopActionPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;

import org.lwjgl.glfw.GLFW;

public final class AriseKeyMappings {

	/**
	 * Apre la schermata di stato. Default K: non e' occupato da vanilla e sta vicino alle dita.
	 *
	 * <p>In 26.2 la categoria non e' piu' una stringa ma un {@link KeyMapping.Category}, e l'helper
	 * di Fabric si chiama {@code KeyMappingHelper} (era {@code KeyBindingHelper}).
	 */
	public static final KeyMapping OPEN_STATUS = register("status", GLFW.GLFW_KEY_K);

	/** Apre la schermata dell'esercito. */
	public static final KeyMapping OPEN_ARMY = register("army", GLFW.GLFW_KEY_J);

	/** Apre l'equipaggiamento del Cacciatore. N: libero in vanilla e vicino a J e K. */
	public static final KeyMapping OPEN_GEAR = register("gear", GLFW.GLFW_KEY_N);

	/** Apre l'Abyss Shop. Il negozio è una finestra del Sistema: si apre ovunque. */
	public static final KeyMapping OPEN_SHOP = register("shop", GLFW.GLFW_KEY_O);

	/** "Arise": estrae l'ombra dal cadavere più vicino. */
	public static final KeyMapping EXTRACT = register("extract", GLFW.GLFW_KEY_R);

	/** Evoca l'esercito. */
	public static final KeyMapping SUMMON = register("summon", GLFW.GLFW_KEY_G);

	/** Richiama l'esercito. */
	public static final KeyMapping RECALL = register("recall", GLFW.GLFW_KEY_H);

	/** Cicla fra aggressiva, difensiva e passiva. */
	public static final KeyMapping STANCE = register("stance", GLFW.GLFW_KEY_B);

	/** Le quattro abilità. Z X C V: vicine fra loro e libere in vanilla. */
	public static final KeyMapping ABILITY_1 = register("ability_1", GLFW.GLFW_KEY_Z);
	public static final KeyMapping ABILITY_2 = register("ability_2", GLFW.GLFW_KEY_X);
	public static final KeyMapping ABILITY_3 = register("ability_3", GLFW.GLFW_KEY_C);
	public static final KeyMapping ABILITY_4 = register("ability_4", GLFW.GLFW_KEY_V);

	private AriseKeyMappings() {
	}

	private static KeyMapping register(String name, int key) {
		return KeyMappingHelper.registerKeyMapping(
				new KeyMapping("key.arise." + name, InputConstants.Type.KEYSYM, key,
						KeyMapping.Category.GAMEPLAY));
	}

	public static void register() {
		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (client.player == null) {
				return;
			}

			// while, non if: se il tasto e' stato premuto piu' volte in un tick vanno consumate
			// tutte, altrimenti la pressione resta in coda e riparte al tick successivo.
			while (OPEN_STATUS.consumeClick()) {
				client.setScreenAndShow(new StatusScreen());
			}

			while (OPEN_ARMY.consumeClick()) {
				client.setScreenAndShow(new ArmyScreen());
			}

			while (OPEN_GEAR.consumeClick()) {
				client.setScreenAndShow(new HunterScreen());
			}

			while (OPEN_SHOP.consumeClick()) {
				// Il colpetto al server prima della schermata: il negozio si rigenera pigramente,
				// e senza questo si vedrebbe l'assortimento della rotazione scorsa.
				ClientPlayNetworking.send(ShopActionPayload.of(ShopActionPayload.Action.OPEN));
				client.setScreenAndShow(new AbyssShopScreen());
			}

			sendOnPress(EXTRACT, AriseActionPayload.Action.EXTRACT);
			sendOnPress(SUMMON, AriseActionPayload.Action.SUMMON);
			sendOnPress(RECALL, AriseActionPayload.Action.RECALL);
			sendOnPress(STANCE, AriseActionPayload.Action.STANCE);
			sendOnPress(ABILITY_1, AriseActionPayload.Action.ABILITY_1);
			sendOnPress(ABILITY_2, AriseActionPayload.Action.ABILITY_2);
			sendOnPress(ABILITY_3, AriseActionPayload.Action.ABILITY_3);
			sendOnPress(ABILITY_4, AriseActionPayload.Action.ABILITY_4);
		});
	}

	/** Il client manda solo l'intenzione: chi decide se si può fare è il server. */
	private static void sendOnPress(KeyMapping mapping, AriseActionPayload.Action action) {
		while (mapping.consumeClick()) {
			ClientPlayNetworking.send(new AriseActionPayload(action));
		}
	}
}
