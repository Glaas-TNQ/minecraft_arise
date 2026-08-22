package com.luca.arise.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.luca.arise.city.City;
import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.client.ui.Glyphs;
import com.luca.arise.client.ui.ListPanel;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.CityConfig;
import com.luca.arise.config.GemConfig;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.PlayerGear;
import com.luca.arise.gem.Gem;
import com.luca.arise.network.GemActionPayload;
import com.luca.arise.registry.ModAttachments;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Incastonatura ed estrazione.
 *
 * <p>Due liste affiancate: a sinistra i pezzi che hanno incastonature, a destra tutto quello che
 * puo' finirci dentro — prima le gemme gia' montate su quel pezzo, poi le incastonature ancora
 * vuote, poi la sacca.
 *
 * <p>I due bottoni in fondo cambiano mestiere in base a cosa e' selezionato a destra: su una gemma
 * montata dicono "Estrai" e "Rompi", su una gemma della sacca dicono "Incastona" e "Rompi". E'
 * un bottone in meno da guardare e nessuna ambiguita' su cosa agisce.
 *
 * <p>La riga di stato dice sempre se il banco dell'Associazione e' a portata, perche' estrarre una
 * gemma intatta si puo' solo li'. E' un <em>indizio</em> calcolato sul client, che conosce solo la
 * distanza dal centro pianificato di una citta': la verifica vera resta del server.
 */
public class GemScreen extends AriseScreen {

	private static final int PANEL_W = 440;
	private static final int PANEL_H = 230;
	private static final int LIST_W = 200;

	/** Una voce della colonna di destra: una gemma montata, un posto vuoto, o una gemma in sacca. */
	private record Slot(Gem gem, boolean socketed) {

		static Slot empty() {
			return new Slot(null, false);
		}
	}

	private final ListPanel<GearPiece> pieces = new ListPanel<>(AriseTheme.ROW_HEIGHT);
	private final ListPanel<Slot> slots = new ListPanel<>(AriseTheme.ROW_HEIGHT);

	private final Screen parent;

	private UUID selectedPieceId;
	private UUID selectedGemId;

	private Button primary;
	private Button secondary;

	public GemScreen(Screen parent) {
		super(Component.translatable("arise.screen.gem.title"), PANEL_W, PANEL_H);
		this.parent = parent;
	}

	// ---------------------------------------------------------------- stato

	private PlayerGear gear() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerGear gear = player == null ? null : player.getAttached(ModAttachments.GEAR);
		return gear == null ? PlayerGear.EMPTY : gear;
	}

	/** I pezzi con almeno un'incastonatura, indossati per primi. */
	private List<GearPiece> pieceList() {
		PlayerGear gear = gear();
		List<GearPiece> result = new ArrayList<>();

		gear.equipped().stream().filter(piece -> piece.sockets() > 0).forEach(result::add);
		gear.stash().stream().filter(piece -> piece.sockets() > 0).forEach(result::add);

		return result;
	}

	private List<Slot> slotList(GearPiece piece) {
		List<Slot> result = new ArrayList<>();

		if (piece != null) {
			piece.gems().forEach(gem -> result.add(new Slot(gem, true)));
			for (int i = 0; i < piece.freeSockets(); i++) {
				result.add(Slot.empty());
			}
		}

		gear().pouch().forEach(gem -> result.add(new Slot(gem, false)));
		return result;
	}

	/** Vero se il giocatore sembra essere dentro il perimetro di un'Associazione. */
	private boolean atBench() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		if (player == null) {
			return false;
		}

		CityConfig cities = AriseConfig.get().cities();
		double limit = (double) AriseConfig.get().gems().benchRadius();
		limit *= limit;

		for (City city : City.values()) {
			double dx = player.getX() - cities.centreX(city);
			double dz = player.getZ() - cities.centreZ(city);

			if (dx * dx + dz * dz <= limit) {
				return true;
			}
		}

		return false;
	}

	// ---------------------------------------------------------------- widget

	@Override
	protected void layout() {
		int left = bodyLeft();
		int top = bodyTop() + 16;
		int listHeight = bodyBottom() - top - 28;

		pieces.bounds(left, top, LIST_W, listHeight);

		int rightLeft = left + LIST_W + 12;
		int rightWidth = bodyRight() - rightLeft;
		slots.bounds(rightLeft, top, rightWidth, listHeight);

		int buttonsY = bodyBottom() - 24;
		int half = (rightWidth - 4) / 2;

		primary = addRenderableWidget(Button.builder(Component.empty(), button -> primary())
				.bounds(rightLeft, buttonsY, half, 20).build());
		secondary = addRenderableWidget(Button.builder(
						Component.translatable("arise.screen.gem.shatter"), button -> shatter())
				.bounds(rightLeft + half + 4, buttonsY, half, 20).build());

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.gem.back"),
						button -> back())
				.bounds(left, buttonsY, 74, 20).build());
	}

	private void back() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent == null ? new HunterScreen() : parent);
		}
	}

	private Slot selectedSlot() {
		return slots.selected();
	}

	private void primary() {
		Slot slot = selectedSlot();
		GearPiece piece = pieces.selected();

		if (slot == null || slot.gem() == null) {
			return;
		}

		if (slot.socketed()) {
			ClientPlayNetworking.send(GemActionPayload.of(slot.gem().id(),
					GemActionPayload.Action.EXTRACT));
			return;
		}

		if (piece != null) {
			ClientPlayNetworking.send(new GemActionPayload(slot.gem().id(), piece.id(),
					GemActionPayload.Action.SOCKET));
		}
	}

	private void shatter() {
		Slot slot = selectedSlot();
		if (slot != null && slot.gem() != null) {
			ClientPlayNetworking.send(GemActionPayload.of(slot.gem().id(),
					GemActionPayload.Action.SHATTER));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();

		if (pieces.mouseClicked(mouseX, mouseY)) {
			GearPiece piece = pieces.selected();
			selectedPieceId = piece == null ? null : piece.id();
			return true;
		}

		if (slots.mouseClicked(mouseX, mouseY)) {
			Slot slot = slots.selected();
			selectedGemId = slot == null || slot.gem() == null ? null : slot.gem().id();
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (pieces.mouseScrolled(mouseX, mouseY, scrollY) || slots.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---------------------------------------------------------------- disegno

	@Override
	protected int accent() {
		return AriseTheme.VIOLET;
	}

	@Override
	protected Component status() {
		return Component.translatable("arise.screen.gem.cost", souls(),
				(long) AriseConfig.get().gems().extractCost());
	}

	@Override
	protected Component hint() {
		return Component.translatable(atBench()
				? "arise.screen.gem.bench_here"
				: "arise.screen.gem.bench_far");
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		GemConfig config = AriseConfig.get().gems();

		pieces.items(pieceList());
		if (selectedPieceId != null) {
			pieces.selectFirst(piece -> piece.id().equals(selectedPieceId));
		}

		GearPiece piece = pieces.selected();
		slots.items(slotList(piece));
		if (selectedGemId != null) {
			slots.selectFirst(slot -> slot.gem() != null && slot.gem().id().equals(selectedGemId));
		}

		int left = bodyLeft();
		int rightLeft = left + LIST_W + 12;

		sectionLabel(graphics, Component.translatable("arise.screen.gem.pieces"), left, bodyTop() + 5);
		sectionLabel(graphics, Component.translatable("arise.screen.gem.pouch",
				gear().pouch().size(), config.pouchSize()), rightLeft, bodyTop() + 5);

		if (pieces.isEmpty()) {
			graphics.text(font, Component.translatable("arise.screen.gem.no_pieces"),
					left, bodyTop() + 22, AriseTheme.DISABLED);
		} else {
			pieces.render(graphics, mouseX, mouseY, this::drawPiece);
		}

		slots.render(graphics, mouseX, mouseY, (g, slot, x, y, width, selected, hovered) ->
				drawSlot(g, config, slot, x, y, width));

		updateButtons(piece);
	}

	private void updateButtons(GearPiece piece) {
		Slot slot = selectedSlot();
		boolean hasGem = slot != null && slot.gem() != null;

		primary.visible = hasGem;
		secondary.visible = hasGem;

		if (!hasGem) {
			return;
		}

		if (slot.socketed()) {
			primary.setMessage(Component.translatable("arise.screen.gem.extract"));
			primary.active = atBench() && souls() >= (long) AriseConfig.get().gems().extractCost();
		} else {
			primary.setMessage(Component.translatable("arise.screen.gem.socket"));
			primary.active = piece != null && piece.freeSockets() > 0;
		}
	}

	private void drawPiece(GuiGraphicsExtractor graphics, GearPiece piece, int x, int y, int width,
			boolean selected, boolean hovered) {
		Glyphs.slot(graphics, piece.slot(), x + 7, y + 5, piece.rank().color());
		graphics.text(font, piece.displayName(), x + 22, y + 4, AriseTheme.TEXT);
		graphics.text(font, Component.translatable("arise.screen.gem.sockets",
				piece.gems().size(), piece.sockets()), x + 22, y + 14, AriseTheme.MUTED);

		int dotX = x + width - 8 - piece.sockets() * 5;
		for (int i = 0; i < piece.sockets(); i++) {
			int color = i < piece.gems().size() ? AriseTheme.VIOLET : AriseTheme.LINE;
			graphics.fill(dotX + i * 5, y + 9, dotX + i * 5 + 3, y + 12, color);
		}
	}

	private void drawSlot(GuiGraphicsExtractor graphics, GemConfig config, Slot slot,
			int x, int y, int width) {
		if (slot.gem() == null) {
			graphics.outline(x + 6, y + 4, 10, 10, AriseTheme.LINE);
			graphics.text(font, Component.translatable("arise.screen.gem.free"),
					x + 22, y + 8, AriseTheme.DISABLED);
			return;
		}

		Gem gem = slot.gem();
		Glyphs.gem(graphics, gem.type(), x + 7, y + 5, gem.rank().color());
		graphics.text(font, gem.displayName(), x + 22, y + 4, AriseTheme.TEXT);
		graphics.text(font, gem.describe(config), x + 22, y + 14, AriseTheme.MUTED);

		// Chi e' montata e chi e' in sacca deve vedersi senza leggere: un puntino a destra.
		if (slot.socketed()) {
			graphics.fill(x + width - 8, y + 8, x + width - 4, y + 12, AriseTheme.VIOLET);
		}
	}
}
