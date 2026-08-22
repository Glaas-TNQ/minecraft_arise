package com.luca.arise.client.screen;

import java.util.List;

import com.luca.arise.city.City;
import com.luca.arise.network.CityTravelPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * Il terminale dell'Associazione: da qui si raggiunge ogni altra Associazione.
 *
 * <p>Elenca <em>tutte</em> le città, non solo quelle costruite. Vedere che Tokyo esiste ma non è
 * ancora sorta è un'informazione: dice che c'è, e che manca. Nascondere le città non ancora
 * costruite farebbe sembrare la mod più piccola di quello che è.
 */
public class CityHubScreen extends Screen {

	private static final int PANEL_WIDTH = 240;
	private static final int ROW_HEIGHT = 24;

	private static final int COLOR_TITLE = 0xFF4FC3F7;
	private static final int COLOR_DIM = 0xFF9BA8B8;

	private final List<City> available;

	public CityHubScreen(List<City> available) {
		super(Component.translatable("arise.screen.hub.title"));
		this.available = available;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	protected void init() {
		int left = (width - PANEL_WIDTH) / 2;
		int top = height / 2 - (City.values().length * ROW_HEIGHT) / 2;

		for (int i = 0; i < City.values().length; i++) {
			City city = City.values()[i];
			boolean ready = available.contains(city);

			Button button = Button.builder(
							ready
									? Component.translatable("arise.screen.hub.travel", city.label())
									: Component.translatable("arise.screen.hub.missing", city.label()),
							b -> ClientPlayNetworking.send(new CityTravelPayload(city)))
					.bounds(left, top + i * ROW_HEIGHT, PANEL_WIDTH, 20)
					.build();

			button.active = ready;
			addRenderableWidget(button);
		}

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.hub.close"),
						b -> onClose())
				.bounds(left + PANEL_WIDTH / 2 - 50, top + City.values().length * ROW_HEIGHT + 10, 100, 20)
				.build());
	}

	@Override
	public void onClose() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(null);
		}
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		int top = height / 2 - (City.values().length * ROW_HEIGHT) / 2;

		graphics.centeredText(font, title, width / 2, top - 28, COLOR_TITLE);
		graphics.centeredText(font, Component.translatable("arise.screen.hub.subtitle"),
				width / 2, top - 16, COLOR_DIM);
	}
}
