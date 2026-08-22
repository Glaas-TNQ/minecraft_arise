package com.luca.arise.client.screen;

import java.util.stream.Collectors;

import com.luca.arise.gate.GateOffer;
import com.luca.arise.network.GateActionPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
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
public class GateOfferScreen extends Screen {

	private static final int PANEL_WIDTH = 300;

	private static final int COLOR_TITLE = 0xFF4FC3F7;
	private static final int COLOR_TEXT = 0xFFE8F2FF;
	private static final int COLOR_DIM = 0xFF9BA8B8;
	private static final int COLOR_SOULS = 0xFFFFD54F;
	private static final int COLOR_LINE = 0xFF1F2C42;

	/** Sotto questa soglia il tempo residuo passa in rosso: il varco sta per chiudersi. */
	private static final int URGENT_TICKS = 600;
	private static final int COLOR_URGENT = 0xFFE86A6A;

	private final int entityId;
	private final GateOffer offer;

	private int remainingTicks;

	public GateOfferScreen(int entityId, GateOffer offer, int remainingTicks) {
		super(Component.translatable("arise.screen.gate.title"));
		this.entityId = entityId;
		this.offer = offer;
		this.remainingTicks = remainingTicks;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int left = (width - PANEL_WIDTH) / 2;
		int buttonsTop = height / 2 + 74;

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.gate.enter"),
						button -> send(GateActionPayload.Action.ENTER))
				.bounds(left, buttonsTop, 146, 20)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.gate.later"),
						button -> onClose())
				.bounds(left + PANEL_WIDTH - 146, buttonsTop, 146, 20)
				.build());

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.gate.dismiss"),
						button -> send(GateActionPayload.Action.DISMISS))
				.bounds(left + PANEL_WIDTH / 2 - 73, buttonsTop + 24, 146, 20)
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int left = (width - PANEL_WIDTH) / 2;
		int top = height / 2 - 96;

		graphics.centeredText(font, title, width / 2, top, COLOR_TITLE);
		graphics.horizontalLine(left, left + PANEL_WIDTH, top + 12, COLOR_LINE);

		int y = top + 22;

		graphics.text(font, Component.translatable("arise.screen.gate.rank"), left, y, COLOR_DIM);
		graphics.text(font, offer.rank().label(), left + 90, y, offer.rank().color());
		y += 14;

		graphics.text(font, Component.translatable("arise.screen.gate.theme"), left, y, COLOR_DIM);
		graphics.text(font, offer.theme().label(), left + 90, y, 0xFF000000 | offer.theme().color());
		y += 14;

		graphics.text(font, Component.translatable("arise.screen.gate.layout"), left, y, COLOR_DIM);
		graphics.text(font, Component.translatable("arise.screen.gate.layout_value",
				offer.mainRooms(), offer.halls(), offer.branches()), left + 90, y, COLOR_TEXT);
		y += 20;

		graphics.text(font, Component.translatable("arise.screen.gate.boss"), left, y, COLOR_DIM);
		graphics.text(font, describe(offer.boss()), left + 90, y, offer.rank().color());
		y += 20;

		graphics.text(font, Component.translatable("arise.screen.gate.inhabitants"), left, y, COLOR_DIM);
		y += 12;

		// A ranghi alti la tabella dei mob è lunga: mandarla a capo è l'unico modo perché resti
		// leggibile senza tagliarla, e tagliarla vorrebbe dire nascondere cosa c'è là dentro.
		Component mobs = Component.literal(offer.mobs().stream()
				.map(id -> describe(id).getString())
				.collect(Collectors.joining(", ")));
		graphics.textWithWordWrap(font, mobs, left + 8, y, PANEL_WIDTH - 8, COLOR_TEXT);
		y += 12 * (1 + font.split(mobs, PANEL_WIDTH - 8).size());

		graphics.horizontalLine(left, left + PANEL_WIDTH, y, COLOR_LINE);
		y += 8;

		graphics.text(font, Component.translatable("arise.screen.gate.reward"), left, y, COLOR_DIM);
		graphics.text(font, Component.translatable("arise.screen.gate.reward_value",
				offer.xp(), offer.souls()), left + 90, y, COLOR_SOULS);
		y += 14;

		int seconds = Math.max(0, remainingTicks / 20);
		graphics.text(font, Component.translatable("arise.screen.gate.closes"), left, y, COLOR_DIM);
		graphics.text(font, Component.translatable("arise.screen.gate.closes_value", seconds / 60, seconds % 60),
				left + 90, y, remainingTicks < URGENT_TICKS ? COLOR_URGENT : COLOR_TEXT);
	}

	/** Il nome tradotto di una creatura, o il suo id se quella creatura non esiste da queste parti. */
	private static Component describe(Identifier id) {
		return BuiltInRegistries.ENTITY_TYPE.getOptional(id)
				.map(EntityType::getDescription)
				.orElseGet(() -> Component.literal(id.getPath()));
	}
}
