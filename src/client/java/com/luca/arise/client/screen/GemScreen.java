package com.luca.arise.client.screen;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.GemConfig;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.PlayerGear;
import com.luca.arise.gem.Gem;
import com.luca.arise.network.GemActionPayload;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.registry.ModAttachments;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Incastonatura ed estrazione.
 *
 * <p>Una schermata sola invece di due: si sceglie il pezzo in alto e si lavora sotto. Separare
 * "scegli il pezzo" da "scegli la gemma" in due passaggi avrebbe voluto dire tenere in testa la
 * prima scelta mentre si fa la seconda, per un gesto che si ripete decine di volte.
 *
 * <p>La riga in alto dice sempre se il banco dell'Associazione e' a portata: estrarre una gemma
 * intatta si puo' solo li', e scoprirlo dopo aver premuto sarebbe una piccola presa in giro.
 */
public class GemScreen extends Screen {

	private static final int ROW_HEIGHT = 24;
	private static final int PANEL_WIDTH = 340;
	private static final int ACTION_WIDTH = 56;
	private static final int SMALL_WIDTH = 42;
	private static final int POUCH_ROWS = 5;

	private static final int COLOR_TITLE = 0xFFC77FE8;
	private static final int COLOR_TEXT = 0xFFE8F2FF;
	private static final int COLOR_DIM = 0xFF9BA8B8;
	private static final int COLOR_EMPTY = 0xFF6B7684;
	private static final int COLOR_SOULS = 0xFFFFD54F;
	private static final int COLOR_ROW = 0x40000000;

	private final Screen parent;

	private int pieceIndex;
	private int page;
	private int lastFingerprint = Integer.MIN_VALUE;

	public GemScreen(Screen parent) {
		super(Component.translatable("arise.screen.gem.title"));
		this.parent = parent;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	// ---------------------------------------------------------------- stato

	private PlayerGear gear() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerGear gear = player == null ? null : player.getAttached(ModAttachments.GEAR);
		return gear == null ? PlayerGear.EMPTY : gear;
	}

	private long souls() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		PlayerProgress progress = player == null ? null : player.getAttached(ModAttachments.PROGRESS);
		return progress == null ? 0L : progress.souls();
	}

	/** I pezzi che hanno almeno un'incastonatura, indossati per primi. */
	private List<GearPiece> pieces() {
		PlayerGear gear = gear();
		List<GearPiece> result = new ArrayList<>();

		gear.equipped().stream().filter(piece -> piece.sockets() > 0).forEach(result::add);
		gear.stash().stream().filter(piece -> piece.sockets() > 0).forEach(result::add);

		return result;
	}

	private GearPiece selected() {
		List<GearPiece> pieces = pieces();
		if (pieces.isEmpty()) {
			return null;
		}

		return pieces.get(Math.clamp(pieceIndex, 0, pieces.size() - 1));
	}

	private List<Gem> pouchPage() {
		List<Gem> pouch = gear().pouch();
		int from = Math.min(page * POUCH_ROWS, pouch.size());
		int to = Math.min(from + POUCH_ROWS, pouch.size());
		return pouch.subList(from, to);
	}

	private int pageCount() {
		return Math.max(1, (gear().pouch().size() + POUCH_ROWS - 1) / POUCH_ROWS);
	}

	// ---------------------------------------------------------------- widget

	@Override
	protected void init() {
		List<GearPiece> pieces = pieces();
		pieceIndex = pieces.isEmpty() ? 0 : Math.clamp(pieceIndex, 0, pieces.size() - 1);
		page = Math.clamp(page, 0, pageCount() - 1);

		int left = (width - PANEL_WIDTH) / 2;
		int top = topOfPanel();
		GearPiece piece = selected();

		if (pieces.size() > 1) {
			addRenderableWidget(Button.builder(Component.literal("<"), button -> cyclePiece(-1))
					.bounds(left + PANEL_WIDTH - 48, top - 24, 20, 20).build());
			addRenderableWidget(Button.builder(Component.literal(">"), button -> cyclePiece(1))
					.bounds(left + PANEL_WIDTH - 24, top - 24, 20, 20).build());
		}

		int row = 0;

		if (piece != null) {
			for (Gem gem : piece.gems()) {
				int y = top + row * ROW_HEIGHT - 2;

				addRenderableWidget(Button.builder(Component.translatable("arise.screen.gem.extract"),
								button -> send(GemActionPayload.of(gem.id(), GemActionPayload.Action.EXTRACT)))
						.bounds(left + PANEL_WIDTH - ACTION_WIDTH - SMALL_WIDTH - 4, y, ACTION_WIDTH, 20)
						.build());

				addRenderableWidget(Button.builder(Component.translatable("arise.screen.gem.shatter"),
								button -> send(GemActionPayload.of(gem.id(), GemActionPayload.Action.SHATTER)))
						.bounds(left + PANEL_WIDTH - SMALL_WIDTH, y, SMALL_WIDTH, 20)
						.build());

				row++;
			}

			row += piece.freeSockets();
		}

		int pouchTop = top + (row + 1) * ROW_HEIGHT;
		int index = 0;

		for (Gem gem : pouchPage()) {
			int y = pouchTop + index * ROW_HEIGHT - 2;

			Button socket = Button.builder(Component.translatable("arise.screen.gem.socket"),
							button -> send(new GemActionPayload(gem.id(),
									piece == null ? gem.id() : piece.id(), GemActionPayload.Action.SOCKET)))
					.bounds(left + PANEL_WIDTH - ACTION_WIDTH - SMALL_WIDTH - 4, y, ACTION_WIDTH, 20)
					.build();
			socket.active = piece != null && piece.freeSockets() > 0;
			addRenderableWidget(socket);

			addRenderableWidget(Button.builder(Component.translatable("arise.screen.gem.shatter"),
							button -> send(GemActionPayload.of(gem.id(), GemActionPayload.Action.SHATTER)))
					.bounds(left + PANEL_WIDTH - SMALL_WIDTH, y, SMALL_WIDTH, 20)
					.build());

			index++;
		}

		int navY = pouchTop + POUCH_ROWS * ROW_HEIGHT + 4;

		if (pageCount() > 1) {
			Button previous = Button.builder(Component.literal("<"), button -> turnTo(page - 1))
					.bounds(left, navY, 20, 20).build();
			previous.active = page > 0;
			addRenderableWidget(previous);

			Button next = Button.builder(Component.literal(">"), button -> turnTo(page + 1))
					.bounds(left + 24, navY, 20, 20).build();
			next.active = page < pageCount() - 1;
			addRenderableWidget(next);
		}

		addRenderableWidget(Button.builder(Component.translatable("arise.screen.gem.back"),
						button -> back())
				.bounds(left + PANEL_WIDTH - 80, navY, 80, 20).build());
	}

	@Override
	public void tick() {
		super.tick();

		PlayerGear gear = gear();
		int fingerprint = gear.equipped().hashCode() * 31 + gear.stash().hashCode() * 7
				+ gear.pouch().hashCode() + pieceIndex * 13 + page;

		if (fingerprint != lastFingerprint) {
			lastFingerprint = fingerprint;
			rebuildWidgets();
		}
	}

	private void cyclePiece(int delta) {
		int count = pieces().size();
		if (count > 0) {
			pieceIndex = Math.floorMod(pieceIndex + delta, count);
			rebuildWidgets();
		}
	}

	private void turnTo(int newPage) {
		page = Math.clamp(newPage, 0, pageCount() - 1);
		rebuildWidgets();
	}

	private void send(GemActionPayload payload) {
		ClientPlayNetworking.send(payload);
	}

	private void back() {
		if (minecraft != null) {
			minecraft.setScreenAndShow(parent == null ? new HunterScreen() : parent);
		}
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (scrollY != 0 && pageCount() > 1) {
			turnTo(page + (scrollY > 0 ? -1 : 1));
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	private int topOfPanel() {
		return height / 2 - 70;
	}

	// ---------------------------------------------------------------- disegno

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractRenderState(graphics, mouseX, mouseY, partialTick);

		GemConfig config = AriseConfig.get().gems();
		int left = (width - PANEL_WIDTH) / 2;
		int top = topOfPanel();
		GearPiece piece = selected();

		graphics.centeredText(font, title, width / 2, top - 46, COLOR_TITLE);
		graphics.centeredText(font, Component.translatable("arise.screen.gem.cost",
				souls(), (long) config.extractCost()), width / 2, top - 34, COLOR_SOULS);

		if (piece == null) {
			graphics.centeredText(font, Component.translatable("arise.screen.gem.no_pieces"),
					width / 2, top - 20, COLOR_DIM);
		} else {
			graphics.text(font, piece.displayName(), left, top - 20, COLOR_TEXT);
			graphics.text(font, Component.translatable("arise.screen.gem.sockets",
					piece.gems().size(), piece.sockets()), left + 4, top - 8, COLOR_DIM);
		}

		int row = 0;

		if (piece != null) {
			for (Gem gem : piece.gems()) {
				drawRow(graphics, left, top + row * ROW_HEIGHT, gem.displayName(),
						gem.describe(config), gem.rank().color());
				row++;
			}

			for (int i = 0; i < piece.freeSockets(); i++) {
				graphics.fill(left, top + row * ROW_HEIGHT - 3,
						left + PANEL_WIDTH, top + row * ROW_HEIGHT + ROW_HEIGHT - 7, COLOR_ROW);
				graphics.text(font, Component.translatable("arise.screen.gem.free"),
						left + 9, top + row * ROW_HEIGHT + 2, COLOR_EMPTY);
				row++;
			}
		}

		int pouchTop = top + (row + 1) * ROW_HEIGHT;
		graphics.text(font, Component.translatable("arise.screen.gem.pouch",
				gear().pouch().size(), config.pouchSize()), left, pouchTop - 12, COLOR_DIM);

		int index = 0;
		for (Gem gem : pouchPage()) {
			drawRow(graphics, left, pouchTop + index * ROW_HEIGHT, gem.displayName(),
					gem.describe(config), gem.rank().color());
			index++;
		}
	}

	private void drawRow(GuiGraphicsExtractor graphics, int left, int y, Component name,
			Component detail, int color) {
		graphics.fill(left, y - 3, left + PANEL_WIDTH, y + ROW_HEIGHT - 7, COLOR_ROW);
		graphics.fill(left + 2, y - 1, left + 5, y + 14, color);
		graphics.text(font, name, left + 9, y, COLOR_TEXT);
		graphics.text(font, detail, left + 9 + font.width(name) + 10, y, COLOR_DIM);
	}
}
