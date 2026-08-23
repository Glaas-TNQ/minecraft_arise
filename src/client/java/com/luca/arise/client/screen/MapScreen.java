package com.luca.arise.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.client.ui.Glyphs;
import com.luca.arise.map.MapProjection;
import com.luca.arise.network.MapPayload;
import com.luca.arise.network.MapPayload.CityMark;
import com.luca.arise.network.MapPayload.GateMark;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * La mappa del mondo: dove sono le città, dove si sono aperti i varchi, dove sei tu.
 *
 * <p>Il problema di questa mappa è la scala. Le città stanno a duecentomila blocchi, i varchi a
 * cento: nessuna inquadratura mostra bene entrambi. Quindi la mappa si apre <em>su di te</em>, a
 * una scala in cui i varchi attorno si leggono, e tutto quello che sta fuori — le città, sempre —
 * resta sul bordo come una freccia con la distanza. Rotella per allontanarsi, trascinare per
 * spostarsi, e un bottone che inquadra tutto per chi vuole vedere il mondo intero.
 *
 * <p>Non disegna il terreno. Sarebbe un'altra mod: qui contano i punti, non il paesaggio.
 */
public class MapScreen extends AriseScreen {

	private static final int PANEL_W = 420;
	private static final int PANEL_H = 270;

	/** La striscia in fondo al corpo dove stanno i bottoni. */
	private static final int CONTROLS = 24;

	/** Blocchi per pixel all'apertura: la finestra copre circa ottocento blocchi, tre varchi. */
	private static final double HOME_SCALE = 2.0;

	/** Quanto dentro dal bordo si fermano i segni di ciò che sta fuori. */
	private static final int EDGE = 7;

	/** Entro quanti pixel dal cursore un segno risponde. */
	private static final int HIT = 6;

	private static final double ZOOM_STEP = 1.25;

	private final MapPayload data;

	private MapProjection projection;

	private int viewX;
	private int viewY;
	private int viewW;
	private int viewH;

	private boolean dragging;
	private double lastMouseX;
	private double lastMouseY;

	public MapScreen(MapPayload data) {
		super(Component.translatable("arise.screen.map.title"), PANEL_W, PANEL_H);
		this.data = data;
	}

	// ---------------------------------------------------------------- impianto

	@Override
	protected void layout() {
		viewX = bodyLeft();
		viewY = bodyTop() + 4;
		viewW = bodyRight() - viewX;
		viewH = bodyBottom() - CONTROLS - viewY;

		// Se la finestra viene ridimensionata si tiene l'inquadratura: perdere il punto che si
		// stava guardando perche' si e' passati a schermo intero sarebbe irritante.
		double centreX = projection == null ? playerX() : projection.centreX();
		double centreZ = projection == null ? playerZ() : projection.centreZ();
		double scale = projection == null ? HOME_SCALE : projection.scale();
		projection = new MapProjection(viewW, viewH, centreX, centreZ, scale);

		int y = bodyBottom() - CONTROLS + 2;
		int x = bodyLeft();

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.map.me"), b -> home())
				.bounds(x, y, 70, 20).build());
		x += 74;
		addRenderableWidget(Button.builder(Component.translatable("arise.screen.map.all"), b -> fitAll())
				.bounds(x, y, 90, 20).build());

		int right = bodyRight();
		addRenderableWidget(Button.builder(Component.literal("+"),
						b -> projection.zoomAt(viewW / 2.0, viewH / 2.0, 1.0 / ZOOM_STEP))
				.bounds(right - 20, y, 20, 20).build());
		addRenderableWidget(Button.builder(Component.literal("−"),
						b -> projection.zoomAt(viewW / 2.0, viewH / 2.0, ZOOM_STEP))
				.bounds(right - 42, y, 20, 20).build());
	}

	/** Torna su di te, alla scala di casa. */
	private void home() {
		projection = new MapProjection(viewW, viewH, playerX(), playerZ(), HOME_SCALE);
	}

	/** Inquadra tutto: te, i varchi e le cinque città. */
	private void fitAll() {
		double minX = playerX();
		double maxX = playerX();
		double minZ = playerZ();
		double maxZ = playerZ();

		for (CityMark city : data.cities()) {
			minX = Math.min(minX, city.x());
			maxX = Math.max(maxX, city.x());
			minZ = Math.min(minZ, city.z());
			maxZ = Math.max(maxZ, city.z());
		}

		for (GateMark gate : data.gates()) {
			minX = Math.min(minX, gate.x());
			maxX = Math.max(maxX, gate.x());
			minZ = Math.min(minZ, gate.z());
			maxZ = Math.max(maxZ, gate.z());
		}

		projection.fit(minX, minZ, maxX, maxZ, 30);
	}

	@Override
	protected Component status() {
		int built = 0;
		for (CityMark city : data.cities()) {
			if (city.built()) {
				built++;
			}
		}

		return Component.translatable("arise.screen.map.status", data.gates().size(), built,
				data.cities().size());
	}

	@Override
	protected Component hint() {
		return Component.translatable("arise.screen.map.hint");
	}

	@Override
	protected int accent() {
		return AriseTheme.VIOLET;
	}

	// ---------------------------------------------------------------- disegno

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		graphics.fill(viewX, viewY, viewX + viewW, viewY + viewH, AriseTheme.PANEL_SOFT);
		graphics.enableScissor(viewX, viewY, viewX + viewW, viewY + viewH);

		grid(graphics);
		gates(graphics);
		cities(graphics);
		player(graphics);
		scaleBar(graphics);

		graphics.disableScissor();
		graphics.outline(viewX, viewY, viewW, viewH, AriseTheme.LINE);

		hover(graphics, mouseX, mouseY);
	}

	/** Il reticolo, con le coordinate sul bordo e gli assi dello spawn un po' più chiari. */
	private void grid(GuiGraphicsExtractor graphics) {
		int step = projection.gridStep();
		int stepPixels = (int) Math.round(step / projection.scale());
		boolean labels = stepPixels >= font.width("-200000") + 6;

		double firstX = Math.floor(projection.toWorldX(0) / step) * step;
		for (double wx = firstX; projection.toScreenX(wx) <= viewW; wx += step) {
			int sx = viewX + (int) Math.round(projection.toScreenX(wx));
			if (sx < viewX || sx >= viewX + viewW) {
				continue;
			}

			graphics.fill(sx, viewY, sx + 1, viewY + viewH, wx == 0 ? AriseTheme.LINE : AriseTheme.LINE_SOFT);
			if (labels) {
				graphics.text(font, Integer.toString((int) wx), sx + 2, viewY + 2, AriseTheme.DISABLED);
			}
		}

		double firstZ = Math.floor(projection.toWorldZ(0) / step) * step;
		for (double wz = firstZ; projection.toScreenY(wz) <= viewH; wz += step) {
			int sy = viewY + (int) Math.round(projection.toScreenY(wz));
			if (sy < viewY || sy >= viewY + viewH) {
				continue;
			}

			graphics.fill(viewX, sy, viewX + viewW, sy + 1, wz == 0 ? AriseTheme.LINE : AriseTheme.LINE_SOFT);
			if (labels) {
				graphics.text(font, Integer.toString((int) wz), viewX + 2, sy + 2, AriseTheme.DISABLED);
			}
		}
	}

	/** I varchi: un rombo del colore del rango. Spento se il varco dorme in un chunk scaricato. */
	private void gates(GuiGraphicsExtractor graphics) {
		for (GateMark gate : data.gates()) {
			double sx = projection.toScreenX(gate.x() + 0.5);
			double sy = projection.toScreenY(gate.z() + 0.5);
			int color = gate.awake() ? gate.rank().color() : AriseTheme.alpha(gate.rank().color(), 0x99);

			if (projection.onScreen(sx, sy, 0)) {
				Glyphs.rankPip(graphics, viewX + (int) Math.round(sx) - 3, viewY + (int) Math.round(sy) - 3,
						7, color);
			} else {
				double[] edge = projection.clampToEdge(sx, sy, EDGE);
				Glyphs.rankPip(graphics, viewX + (int) Math.round(edge[0]) - 2,
						viewY + (int) Math.round(edge[1]) - 2, 5, color);
			}
		}
	}

	/** Un'etichetta ancora da piazzare: dove vorrebbe stare, e cosa dice. */
	private record Label(int x, int y, int width, Component text, int color) {
	}

	/**
	 * Le città: un quadrato pieno del loro colore, vuoto se non sono ancora sorte, col nome.
	 *
	 * <p>I segni si disegnano subito, i nomi no: le cinque città stanno tutte sulla stessa riga del
	 * mondo (§8, `CityConfig.cityZ`), quindi a mappa intera i loro segni cadono vicinissimi, e i
	 * nomi scritti al volo si sarebbero scritti l'uno sopra l'altro. Si raccolgono le etichette e
	 * si piazzano tutte insieme in {@link #placeLabels}, che le impila quando si toccano.
	 */
	private void cities(GuiGraphicsExtractor graphics) {
		List<Label> labels = new ArrayList<>();

		for (CityMark city : data.cities()) {
			double sx = projection.toScreenX(city.x() + 0.5);
			double sy = projection.toScreenY(city.z() + 0.5);
			int color = city.built() ? city.city().color() : AriseTheme.alpha(city.city().color(), 0x99);
			Component name = city.city().label();

			if (projection.onScreen(sx, sy, 0)) {
				int x = viewX + (int) Math.round(sx);
				int y = viewY + (int) Math.round(sy);
				square(graphics, x, y, 4, color, city.built());
				labels.add(new Label(x - font.width(name) / 2, y + 6, font.width(name), name, AriseTheme.TEXT));
				continue;
			}

			// Fuori: sul bordo, con la distanza. E' l'unico modo in cui una citta' a duecentomila
			// blocchi puo' stare sulla stessa mappa del prato in cui ci si trova.
			double[] edge = projection.clampToEdge(sx, sy, EDGE);
			int x = viewX + (int) Math.round(edge[0]);
			int y = viewY + (int) Math.round(edge[1]);
			square(graphics, x, y, 3, color, city.built());

			Component tag = Component.translatable("arise.screen.map.far", name,
					distanceText(MapProjection.distance(playerX(), playerZ(), city.x(), city.z())));
			int tagW = font.width(tag);
			// Il testo rientra verso il centro: sul bordo destro va a sinistra del segno, in
			// basso va sopra, e cosi' via.
			int tx = edge[0] > viewW / 2.0 ? x - tagW - 6 : x + 6;
			int ty = edge[1] > viewH / 2.0 ? y - 10 : y + 4;
			tx = Math.clamp(tx, viewX + 2, viewX + viewW - tagW - 2);
			ty = Math.clamp(ty, viewY + 2, viewY + viewH - 10);
			labels.add(new Label(tx, ty, tagW, tag, AriseTheme.MUTED));
		}

		placeLabels(graphics, labels);
	}

	/**
	 * Disegna le etichette, spostando in basso quelle che si accavallerebbero.
	 *
	 * <p>Un'etichetta si confronta solo con quelle già piazzate prima di lei: bastano poche
	 * iterazioni, e con cinque città al più non è mai un problema di prestazioni.
	 */
	private void placeLabels(GuiGraphicsExtractor graphics, List<Label> labels) {
		int lineH = font.lineHeight + 2;
		List<Label> placed = new ArrayList<>();

		for (Label label : labels) {
			int y = label.y();
			boolean moved;
			do {
				moved = false;
				for (Label other : placed) {
					boolean overlapX = label.x() < other.x() + other.width() + 3
							&& label.x() + label.width() + 3 > other.x();
					boolean overlapY = y < other.y() + lineH && y + lineH > other.y();

					if (overlapX && overlapY) {
						y = other.y() + lineH;
						moved = true;
					}
				}
			} while (moved && y + lineH <= viewY + viewH);

			y = Math.min(y, viewY + viewH - lineH);
			Label resolved = new Label(label.x(), y, label.width(), label.text(), label.color());
			placed.add(resolved);
			graphics.text(font, resolved.text(), resolved.x(), resolved.y(), resolved.color());
		}
	}

	/** Tu: un punto chiaro con il verso in cui guardi. */
	private void player(GuiGraphicsExtractor graphics) {
		double sx = projection.toScreenX(playerX());
		double sy = projection.toScreenY(playerZ());

		if (!projection.onScreen(sx, sy, 0)) {
			double[] edge = projection.clampToEdge(sx, sy, EDGE);
			sx = edge[0];
			sy = edge[1];
		}

		int x = viewX + (int) Math.round(sx);
		int y = viewY + (int) Math.round(sy);

		// Imbardata di Minecraft: 0 e' sud (+Z), 90 e' ovest (-X).
		double yaw = Math.toRadians(playerYaw());
		double dirX = -Math.sin(yaw);
		double dirZ = Math.cos(yaw);

		for (int i = 2; i <= 8; i++) {
			int px = x + (int) Math.round(dirX * i);
			int py = y + (int) Math.round(dirZ * i);
			graphics.fill(px, py, px + 1, py + 1, AriseTheme.alpha(AriseTheme.TEXT, 0xB0));
		}

		graphics.fill(x - 2, y - 2, x + 3, y + 3, AriseTheme.TEXT);
		graphics.fill(x - 1, y - 1, x + 2, y + 2, AriseTheme.ACCENT);
	}

	/** La scala, in basso a destra: quanti blocchi e' una maglia del reticolo. */
	private void scaleBar(GuiGraphicsExtractor graphics) {
		int step = projection.gridStep();
		int pixels = (int) Math.round(step / projection.scale());
		Component label = Component.translatable("arise.screen.map.blocks", step);

		int x1 = viewX + viewW - 6;
		int x0 = x1 - pixels;
		int y = viewY + viewH - 8;

		graphics.fill(x0, y, x1, y + 1, AriseTheme.MUTED);
		graphics.fill(x0, y - 2, x0 + 1, y + 1, AriseTheme.MUTED);
		graphics.fill(x1 - 1, y - 2, x1, y + 1, AriseTheme.MUTED);
		graphics.text(font, label, x1 - font.width(label), y - 12, AriseTheme.MUTED);
	}

	private static void square(GuiGraphicsExtractor graphics, int x, int y, int half, int color, boolean filled) {
		if (filled) {
			graphics.fill(x - half, y - half, x + half + 1, y + half + 1, color);
		} else {
			graphics.outline(x - half, y - half, 2 * half + 1, 2 * half + 1, color);
		}
	}

	// ---------------------------------------------------------------- sotto il mouse

	/** Il segno più vicino al cursore, se ce n'è uno a tiro: il suo riquadro. */
	private void hover(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!inView(mouseX, mouseY)) {
			return;
		}

		double mx = mouseX - viewX;
		double my = mouseY - viewY;
		List<Component> lines = null;
		double best = HIT * HIT;

		for (GateMark gate : data.gates()) {
			double[] at = projection.clampToEdge(projection.toScreenX(gate.x() + 0.5),
					projection.toScreenY(gate.z() + 0.5), EDGE);
			double d = (at[0] - mx) * (at[0] - mx) + (at[1] - my) * (at[1] - my);

			if (d <= best) {
				best = d;
				lines = gateLines(gate);
			}
		}

		for (CityMark city : data.cities()) {
			double[] at = projection.clampToEdge(projection.toScreenX(city.x() + 0.5),
					projection.toScreenY(city.z() + 0.5), EDGE);
			double d = (at[0] - mx) * (at[0] - mx) + (at[1] - my) * (at[1] - my);

			if (d <= best) {
				best = d;
				lines = cityLines(city);
			}
		}

		double[] me = projection.clampToEdge(projection.toScreenX(playerX()),
				projection.toScreenY(playerZ()), EDGE);
		double dMe = (me[0] - mx) * (me[0] - mx) + (me[1] - my) * (me[1] - my);
		if (dMe <= best) {
			lines = List.of(Component.translatable("arise.screen.map.you"),
					Component.translatable("arise.screen.map.at", (int) Math.floor(playerX()),
							(int) Math.floor(playerZ())));
		}

		if (lines != null) {
			graphics.setTooltipForNextFrame(font, lines, Optional.empty(), mouseX, mouseY);
		}
	}

	private List<Component> gateLines(GateMark gate) {
		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable("arise.screen.map.gate", gate.rank().label(), gate.theme().label()));
		lines.add(Component.translatable("arise.screen.map.at3", gate.x(), gate.y(), gate.z()));
		lines.add(Component.translatable("arise.screen.map.distance",
				distanceText(MapProjection.distance(playerX(), playerZ(), gate.x() + 0.5, gate.z() + 0.5))));

		int seconds = Math.max(0, gate.remainingTicks() / 20);
		String clock = seconds / 60 + ":" + (seconds % 60 < 10 ? "0" : "") + seconds % 60;
		lines.add(gate.awake()
				? Component.translatable("arise.screen.map.remaining", clock)
				: Component.translatable("arise.screen.map.asleep", clock));

		return lines;
	}

	private List<Component> cityLines(CityMark city) {
		List<Component> lines = new ArrayList<>();
		lines.add(city.city().label());
		lines.add(Component.translatable("arise.screen.map.at", city.x(), city.z()));
		lines.add(Component.translatable("arise.screen.map.distance",
				distanceText(MapProjection.distance(playerX(), playerZ(), city.x() + 0.5, city.z() + 0.5))));
		lines.add(Component.translatable(city.built() ? "arise.screen.map.city_built" : "arise.screen.map.city_missing"));
		lines.add(Component.translatable("arise.screen.hub.landmark", city.city().landmark().label()));
		return lines;
	}

	/**
	 * Una distanza leggibile: blocchi fin sotto i mille, poi chilometri con un decimale.
	 *
	 * <p>Niente {@code String.format}: su una macchina italiana scrive la virgola, e qui il
	 * decimale si costruisce a mano proprio per non dipendere dalla lingua di sistema.
	 */
	static String distanceText(double blocks) {
		long rounded = Math.round(blocks);

		if (rounded < 1000) {
			return Long.toString(rounded);
		}

		long tenths = Math.round(blocks / 100.0);
		if (tenths >= 1000) {
			return (tenths / 10) + "k";
		}

		return (tenths / 10) + "." + (tenths % 10) + "k";
	}

	// ---------------------------------------------------------------- mouse

	private boolean inView(double x, double y) {
		return x >= viewX && x < viewX + viewW && y >= viewY && y < viewY + viewH;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (super.mouseClicked(event, doubleClick)) {
			return true;
		}

		if (event.button() == 0 && inView(event.x(), event.y())) {
			dragging = true;
			lastMouseX = event.x();
			lastMouseY = event.y();
			return true;
		}

		return false;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
		if (!dragging) {
			return super.mouseDragged(event, dragX, dragY);
		}

		// Lo spostamento si ricava dalle posizioni, non dai due numeri dell'evento: cosi' non
		// dipende da cosa esattamente quei due numeri vogliano dire in questa versione.
		projection.pan(event.x() - lastMouseX, event.y() - lastMouseY);
		lastMouseX = event.x();
		lastMouseY = event.y();
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = false;
		return super.mouseReleased(event);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (inView(mouseX, mouseY) && scrollY != 0) {
			projection.zoomAt(mouseX - viewX, mouseY - viewY, scrollY > 0 ? 1.0 / ZOOM_STEP : ZOOM_STEP);
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---------------------------------------------------------------- il giocatore

	private LocalPlayer me() {
		return minecraft != null ? minecraft.player : null;
	}

	private double playerX() {
		LocalPlayer player = me();
		return player == null ? 0.0 : player.getX();
	}

	private double playerZ() {
		LocalPlayer player = me();
		return player == null ? 0.0 : player.getZ();
	}

	private float playerYaw() {
		LocalPlayer player = me();
		return player == null ? 0.0F : player.getYRot();
	}
}
