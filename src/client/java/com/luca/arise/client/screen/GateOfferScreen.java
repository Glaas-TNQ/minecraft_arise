package com.luca.arise.client.screen;

import java.util.stream.Collectors;

import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.gate.GateOffer;
import com.luca.arise.network.GateActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * L'analisi di un varco, prima di attraversarlo.
 *
 * <p>Serve a rendere l'ingresso una <em>decisione</em>. Le informazioni qui dentro sono un impegno
 * del server: rango, composizione, chi ci abita, chi lo custodisce e quanto rende. Nessuna è
 * calcolata dal client — arrivano tutte nel pacchetto che risponde al click sul varco, e il
 * bottone "Entra" rimanda indietro soltanto "quel varco lì".
 */
public class GateOfferScreen extends AriseScreen {

	private static final int PANEL_W = 320;
	private static final int PANEL_H = 250;
	private static final int LABEL_W = 92;

	/** Sotto questa soglia il tempo residuo passa in rosso: il varco sta per chiudersi. */
	private static final int URGENT_TICKS = 600;

	private final int entityId;
	private final GateOffer offer;

	private int remainingTicks;

	public GateOfferScreen(int entityId, GateOffer offer, int remainingTicks) {
		super(Component.translatable("arise.screen.gate.title"), PANEL_W, PANEL_H);
		this.entityId = entityId;
		this.offer = offer;
		this.remainingTicks = remainingTicks;
	}

	@Override
	protected void layout() {
		int left = bodyLeft();
		int full = bodyRight() - left;
		int half = (full - 4) / 2;
		int buttonsTop = bodyBottom() - 46;

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.gate.enter"),
						button -> send(GateActionPayload.Action.ENTER))
				.bounds(left, buttonsTop, half, 20)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.gate.later"),
						button -> onClose())
				.bounds(left + half + 4, buttonsTop, half, 20)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.gate.dismiss"),
						button -> send(GateActionPayload.Action.DISMISS))
				.bounds(left, buttonsTop + 24, full, 20)
				.build());
	}

	private void send(GateActionPayload.Action action) {
		ClientPlayNetworking.send(new GateActionPayload(entityId, action));
		onClose();
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(null);
		}
	}

	/**
	 * Il conto alla rovescia scorre qui, non arriva dal server.
	 *
	 * <p>Un pacchetto al secondo solo per aggiornare un numero sarebbe uno spreco, e se il conto
	 * del client va fuori sincronia di qualche decimo non succede niente: il varco che scade lo
	 * decide comunque il server, e il pannello si chiude da solo quando arriva a zero.
	 */
	@Override
	public void tick() {
		super.tick();

		if (--remainingTicks <= 0) {
			onClose();
		}
	}

	/** L'accento del pannello e' il colore del rango: il varco si riconosce prima di leggerlo. */
	@Override
	protected int accent() {
		return offer.rank().color();
	}

	@Override
	protected Component status() {
		int seconds = Math.max(0, remainingTicks / 20);
		return Component.translatable("arise.screen.gate.closes_value", seconds / 60, seconds % 60);
	}

	@Override
	protected Component hint() {
		return Component.translatable("arise.screen.gate.hint");
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		int left = bodyLeft();
		int right = bodyRight();
		int available = right - left;
		int y = bodyTop() + 6;

		row(graphics, "arise.screen.gate.rank", offer.rank().label(), left, y, offer.rank().color());
		y += 13;
		row(graphics, "arise.screen.gate.theme", offer.theme().label(), left, y,
				0xFF000000 | offer.theme().color());
		y += 13;
		row(graphics, "arise.screen.gate.layout", Component.translatable("arise.screen.gate.layout_value",
				offer.mainRooms(), offer.halls(), offer.branches()), left, y, AriseTheme.TEXT);
		y += 13;
		row(graphics, "arise.screen.gate.boss", describe(offer.boss()), left, y, offer.rank().color());
		y += 18;

		divider(graphics, left, right, y);
		y += 7;

		sectionLabel(graphics, Component.translatable("arise.screen.gate.inhabitants"), left, y);
		y += 12;

		// A ranghi alti la tabella dei mob è lunga: mandarla a capo è l'unico modo perché resti
		// leggibile senza tagliarla, e tagliarla vorrebbe dire nascondere cosa c'è là dentro.
		Component mobs = Component.literal(offer.mobs().stream()
				.map(id -> describe(id).getString())
				.collect(Collectors.joining(", ")));
		graphics.textWithWordWrap(font, mobs, left, y, available, AriseTheme.TEXT);
		y += 11 * font.split(mobs, available).size() + 6;

		divider(graphics, left, right, y);
		y += 7;

		row(graphics, "arise.screen.gate.reward", Component.translatable("arise.screen.gate.reward_value",
				offer.xp(), offer.souls()), left, y, AriseTheme.GOLD);

		// Il tempo residuo e' gia' nella riga di stato in alto; qui si accende solo quando urge.
		if (remainingTicks < URGENT_TICKS) {
			y += 16;
			graphics.text(font, Component.translatable("arise.screen.gate.urgent"), left, y, AriseTheme.BAD);
		}
	}

	private void row(GuiGraphicsExtractor graphics, String key, Component value, int left, int y,
			int color) {
		graphics.text(font, Component.translatable(key), left, y, AriseTheme.MUTED);
		graphics.text(font, value, left + LABEL_W, y, color);
	}

	/** Il nome tradotto di una creatura, o il suo id se quella creatura non esiste da queste parti. */
	private static Component describe(Identifier id) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
				.map(EntityType::getDescription)
				.orElseGet(() -> Component.literal(id.getPath()));
	}
}
