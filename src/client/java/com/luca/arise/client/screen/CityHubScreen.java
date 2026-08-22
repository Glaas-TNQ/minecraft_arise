package com.luca.arise.client.screen;

import java.util.List;

import com.luca.arise.city.City;
import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.CityConfig;
import com.luca.arise.network.CityTravelPayload;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Il terminale dell'Associazione: da qui si raggiunge ogni altra Associazione.
 *
 * <p>Elenca <em>tutte</em> le città, non solo quelle costruite. Vedere che Tokyo esiste ma non è
 * ancora sorta è un'informazione: dice che c'è, e che manca. Nascondere le città non ancora
 * costruite farebbe sembrare la mod più piccola di quello che è.
 */
public class CityHubScreen extends AriseScreen {

	private static final int PANEL_W = 300;
	private static final int PANEL_H = 190;
	private static final int ROW = 26;

	private final List<City> available;

	public CityHubScreen(List<City> available) {
		super(Component.translatable("arise.screen.hub.title"), PANEL_W, PANEL_H);
		this.available = available;
	}

	@Override
	protected void layout() {
		int left = bodyLeft();
		int y = bodyTop() + 16;
		// Il bottone lascia libera la colonna di destra: le coordinate ci vanno accanto, non sopra.
		int buttonWidth = bodyRight() - left - 96;

		for (City city : City.values()) {
			boolean ready = available.contains(city);

			Button button = Button.builder(city.label(),
							b -> ClientPlayNetworking.send(new CityTravelPayload(city)))
					.bounds(left, y, buttonWidth, 20)
					.build();

			button.active = ready;
			addRenderableWidget(button);

			y += ROW;
		}
	}

	@Override
	protected Component status() {
		return Component.translatable("arise.screen.hub.subtitle");
	}

	@Override
	protected Component hint() {
		return Component.translatable("arise.screen.hub.hint");
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		CityConfig config = AriseConfig.get().cities();
		int right = bodyRight();
		int y = bodyTop() + 22;

		// Le coordinate accanto al nome: sono l'unico modo di sapere dove si sta andando, e per una
		// città non ancora sorta sono anche il posto dove andrà a finire.
		for (City city : City.values()) {
			boolean ready = available.contains(city);
			Component where = ready
					? Component.translatable("arise.screen.hub.at",
							config.centreX(city), config.centreZ(city))
					: Component.translatable("arise.screen.hub.missing_short");

			graphics.text(font, where, right - font.width(where), y + 6,
					ready ? AriseTheme.MUTED : AriseTheme.DISABLED);

			y += ROW;
		}
	}
}
