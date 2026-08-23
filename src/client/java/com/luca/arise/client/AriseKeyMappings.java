package com.luca.arise.client;

import java.util.function.Supplier;

import com.luca.arise.client.screen.AbyssShopScreen;
import com.luca.arise.client.screen.ArmyScreen;
import com.luca.arise.client.screen.StatusScreen;
import com.luca.arise.client.screen.QuestScreen;
import com.luca.arise.network.AriseActionPayload;
import com.luca.arise.network.ShopActionPayload;
import com.mojang.blaze3d.platform.InputConstants;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import com.luca.arise.quest.PlayerQuests;
import com.luca.arise.quest.Unlock;
import com.luca.arise.registry.ModAttachments;

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

	/** Apre gli incarichi del Sistema. */
	public static final KeyMapping OPEN_QUESTS = register("quests", GLFW.GLFW_KEY_P);

	/** Apre l'Abyss Shop. Il negozio è una finestra del Sistema: si apre ovunque. */
	public static final KeyMapping OPEN_SHOP = register("shop", GLFW.GLFW_KEY_O);

	/** La mappa del mondo: citta' e varchi aperti. M, che vanilla lascia libero. */
	public static final KeyMapping OPEN_MAP = register("map", GLFW.GLFW_KEY_M);

	/** "Arise": estrae l'ombra dal cadavere più vicino. */
	public static final KeyMapping EXTRACT = register("extract", GLFW.GLFW_KEY_R);

	/** Evoca l'esercito. */
	public static final KeyMapping SUMMON = register("summon", GLFW.GLFW_KEY_G);

	/** Richiama l'esercito. */
	public static final KeyMapping RECALL = register("recall", GLFW.GLFW_KEY_H);

	/** Cicla fra aggressiva, difensiva e passiva. */
	public static final KeyMapping STANCE = register("stance", GLFW.GLFW_KEY_B);

	/**
	 * "Uccidetelo": l'esercito punta cio' che si sta guardando.
	 *
	 * <p>Y e U sono liberi in vanilla, e stanno vicini fra loro e lontani dai tasti che si premono
	 * per sbaglio in mischia. Un ordine dato per sbaglio in postura passiva farebbe attaccare un
	 * esercito che non doveva muoversi.
	 */
	public static final KeyMapping ORDER_FOCUS = register("order_focus", GLFW.GLFW_KEY_Y);

	/** "Restate qui": l'esercito tiene la posizione. Ripremuto, torna a seguire. */
	public static final KeyMapping ORDER_HOLD = register("order_hold", GLFW.GLFW_KEY_U);

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
				open(client, Unlock.STATS, StatusScreen::new);
			}

			while (OPEN_QUESTS.consumeClick()) {
				open(client, Unlock.SYSTEM, QuestScreen::new);
			}

			while (OPEN_ARMY.consumeClick()) {
				open(client, Unlock.ARMY, ArmyScreen::new);
			}

			// L'equipaggiamento non e' piu' una schermata nostra ma un menu con delle caselle, e
			// un menu nasce sul server: il tasto puo' solo chiederlo. Il rifiuto per sistema non
			// ancora sbloccato arriva da li'.
			sendOnPress(OPEN_GEAR, AriseActionPayload.Action.OPEN_GEAR);

			// Anche la mappa nasce sul server: il tasto chiede, e la risposta e' la schermata.
			sendOnPress(OPEN_MAP, AriseActionPayload.Action.OPEN_MAP);

			while (OPEN_SHOP.consumeClick()) {
				// Il colpetto al server prima della schermata: il negozio si rigenera pigramente,
				// e senza questo si vedrebbe l'assortimento della rotazione scorsa.
				if (unlocked(client, Unlock.SHOP)) {
					ClientPlayNetworking.send(ShopActionPayload.of(ShopActionPayload.Action.OPEN));
				}

				open(client, Unlock.SHOP, AbyssShopScreen::new);
			}

			// Lo stesso tasto fa due cose: accovacciati guarda, in piedi estrae. E' l'unico modo
			// di dare un'anteprima senza chiedere un tredicesimo bind — e la posizione accovacciata
			// e' gia', in tutto il resto della mod, quella che significa "con calma".
			while (EXTRACT.consumeClick()) {
				ClientPlayNetworking.send(new AriseActionPayload(client.player.isShiftKeyDown()
						? AriseActionPayload.Action.SURVEY
						: AriseActionPayload.Action.EXTRACT));
			}

			sendOnPress(SUMMON, AriseActionPayload.Action.SUMMON);
			sendOnPress(RECALL, AriseActionPayload.Action.RECALL);
			sendOnPress(STANCE, AriseActionPayload.Action.STANCE);
			sendOnPress(ORDER_FOCUS, AriseActionPayload.Action.ORDER_FOCUS);
			sendOnPress(ORDER_HOLD, AriseActionPayload.Action.ORDER_HOLD);
			sendOnPress(ABILITY_1, AriseActionPayload.Action.ABILITY_1);
			sendOnPress(ABILITY_2, AriseActionPayload.Action.ABILITY_2);
			sendOnPress(ABILITY_3, AriseActionPayload.Action.ABILITY_3);
			sendOnPress(ABILITY_4, AriseActionPayload.Action.ABILITY_4);
		});
	}

	/**
	 * Apre una schermata solo se il Sistema ha gia' concesso quel sistema.
	 *
	 * <p>Il rifiuto e' una cortesia, non una difesa: un client modificato potrebbe aprire la
	 * schermata comunque, e non servirebbe a niente perche' ogni azione dentro passa dal server.
	 * Serve a non mostrare una finestra vuota di cui non si capisce il perche'.
	 */
	private static void open(Minecraft client, Unlock unlock, Supplier<Screen> screen) {
		if (unlocked(client, unlock)) {
			client.setScreenAndShow(screen.get());
			return;
		}

		if (client.player != null) {
			client.player.sendSystemMessage(unlock.refusal());
		}
	}

	private static boolean unlocked(Minecraft client, Unlock unlock) {
		PlayerQuests quests = client.player == null
				? null
				: client.player.getAttached(ModAttachments.QUESTS);

		return quests != null && quests.has(unlock);
	}

	/** Il client manda solo l'intenzione: chi decide se si può fare è il server. */
	private static void sendOnPress(KeyMapping mapping, AriseActionPayload.Action action) {
		while (mapping.consumeClick()) {
			ClientPlayNetworking.send(new AriseActionPayload(action));
		}
	}
}
