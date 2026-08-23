package com.luca.arise.client.screen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.client.ui.Glyphs;
import com.luca.arise.client.ui.ListPanel;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.config.ShadowConfig;
import com.luca.arise.network.ShadowActionPayload;
import com.luca.arise.registry.ModAttachments;
import com.luca.arise.shadow.ShadowDowntime;
import com.luca.arise.shadow.ShadowArmy;
import com.luca.arise.shadow.ShadowData;
import com.luca.arise.shadow.ShadowGrade;
import com.luca.arise.shadow.ShadowSquad;
import com.luca.arise.shadow.SummonedShadows;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * L'esercito d'ombra.
 *
 * <p>Era paginato, sei ombre per volta, con due bottoni per riga. Un esercito da cinquanta
 * significava nove pagine da sfogliare per trovare quella giusta. Poi e' diventato una lista che
 * scorre, con il dettaglio a fianco.
 *
 * <p>Restava il difetto vero: la lista mostrava tutto e non diceva <em>niente</em> su quali ombre
 * il tasto di evocazione avrebbe chiamato. Ora l'ordine predefinito della lista <strong>e'</strong>
 * l'ordine di chiamata — prima la squadra, poi le altre dalla piu' forte — e ogni riga porta il
 * numero di posto in squadra, l'archetipo e il grado. Quello che si vede e' quello che esce.
 */
public class ArmyScreen extends AriseScreen {

	private static final int PANEL_W = 460;
	private static final int PANEL_H = 226;
	private static final int LIST_W = 236;

	/**
	 * Come ordinare la lista.
	 *
	 * <p>{@link #CALL_UP} e' il predefinito e non e' un ordinamento come gli altri: e' la
	 * <em>risposta</em> alla domanda "chi esce se premo il tasto". Gli altri tre servono a cercare,
	 * ed e' l'unica cosa che servono a fare.
	 */
	private enum Sort {
		CALL_UP("call_up"),
		POWER("power"),
		LEVEL("level"),
		NAME("name");

		private final String key;

		Sort(String key) {
			this.key = key;
		}

		Component label() {
			return Component.translatable("arise.screen.army.sort." + key);
		}

		Sort next() {
			return values()[(ordinal() + 1) % values().length];
		}
	}

	private final ListPanel<ShadowData> list = new ListPanel<>(AriseTheme.ROW_HEIGHT);

	private UUID selectedId;
	private Sort sort = Sort.CALL_UP;

	private Button toggle;
	private Button squadButton;
	private Button details;
	private Button sortButton;

	public ArmyScreen() {
		super(Component.translatable("arise.screen.army.title"), PANEL_W, PANEL_H);
	}

	// ---------------------------------------------------------------- stato

	private ShadowArmy army() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		ShadowArmy army = player == null ? null : player.getAttached(ModAttachments.ARMY);
		return army == null ? ShadowArmy.EMPTY : army;
	}

	private SummonedShadows summoned() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		SummonedShadows summoned = player == null ? null : player.getAttached(ModAttachments.SUMMONED);
		return summoned == null ? SummonedShadows.EMPTY : summoned;
	}

	private ShadowSquad squad() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		ShadowSquad squad = player == null ? null : player.getAttached(ModAttachments.SQUAD);
		return squad == null ? ShadowSquad.EMPTY : squad;
	}

	/**
	 * Quali ombre sono ancora a terra.
	 *
	 * <p>Arriva sincronizzata dal server, e deve: il client non ha modo di sapere quando un'ombra
	 * e' caduta fuori dalla sua vista, e senza questo dato la schermata mostrerebbe un bottone
	 * "evoca" che poi il server rifiuta senza spiegare.
	 */
	private ShadowDowntime downtime() {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		ShadowDowntime downtime = player == null ? null : player.getAttached(ModAttachments.DOWNTIME);
		return downtime == null ? ShadowDowntime.EMPTY : downtime;
	}

	/** Secondi che mancano perche' quest'ombra torni evocabile. Zero se e' pronta. */
	private long restingSeconds(UUID id) {
		if (minecraft == null || minecraft.level == null) {
			return 0L;
		}

		long ticks = downtime().remaining(id, minecraft.level.getGameTime());
		return ticks <= 0 ? 0L : Math.max(1L, ticks / 20L);
	}

	/**
	 * La lista nell'ordine scelto.
	 *
	 * <p>{@code CALL_UP} rifa' qui il ragionamento che il server fa in {@code ShadowManager.callUp}.
	 * E' una duplicazione, e vale la pena averla: l'alternativa sarebbe mandare l'ordine in rete a
	 * ogni frame per un dato che il client ha gia' tutto — squadra e potenza sono entrambe
	 * sincronizzate. Se le due divergessero, il costo sarebbe una lista in ordine leggermente
	 * diverso da quello reale, non un comando sbagliato: i comandi viaggiano per id.
	 */
	private List<ShadowData> sorted(ShadowConfig config) {
		List<ShadowData> shadows = new ArrayList<>(army().shadows());
		ShadowSquad squad = squad();

		switch (sort) {
			case CALL_UP -> shadows.sort(Comparator
					.comparingInt((ShadowData shadow) -> squad.contains(shadow.id())
							? squad.slotOf(shadow.id())
							: Integer.MAX_VALUE)
					.thenComparing(Comparator.comparingDouble(
							(ShadowData shadow) -> shadow.effectivePower(config)).reversed()));
			case POWER -> shadows.sort(Comparator.comparingDouble(
					(ShadowData shadow) -> shadow.effectivePower(config)).reversed());
			case LEVEL -> shadows.sort(Comparator.comparingInt(ShadowData::level).reversed());
			case NAME -> shadows.sort(Comparator.comparing(
					shadow -> shadow.displayName().getString(), String.CASE_INSENSITIVE_ORDER));
		}

		return shadows;
	}

	// ---------------------------------------------------------------- widget

	@Override
	protected void layout() {
		int left = bodyLeft();
		int top = bodyTop() + 18;
		int listHeight = bodyBottom() - top - 28;

		list.bounds(left, top, LIST_W, listHeight);

		sortButton = addRenderableWidget(Button.builder(Component.empty(), button -> {
			sort = sort.next();
			rebuildWidgets();
		}).bounds(left + LIST_W - 84, bodyTop() + 1, 84, 15).build());

		int detailLeft = left + LIST_W + 12;
		int detailRight = bodyRight();
		int buttonsY = bodyBottom() - 24;
		int third = (detailRight - detailLeft - 8) / 3;

		toggle = addRenderableWidget(Button.builder(Component.empty(), button -> toggle())
				.bounds(detailLeft, buttonsY, third, 20).build());
		squadButton = addRenderableWidget(Button.builder(Component.empty(), button -> squadToggle())
				.bounds(detailLeft + third + 4, buttonsY, third, 20).build());
		details = addRenderableWidget(Button.builder(
						Component.translatable("arise.screen.army.details"), button -> openDetails())
				.bounds(detailLeft + (third + 4) * 2, buttonsY, third, 20).build());
	}

	private void toggle() {
		ShadowData shadow = list.selected();
		if (shadow == null) {
			return;
		}

		ClientPlayNetworking.send(ShadowActionPayload.of(shadow.id(),
				summoned().contains(shadow.id())
						? ShadowActionPayload.Action.RECALL
						: ShadowActionPayload.Action.SUMMON));
	}

	private void squadToggle() {
		ShadowData shadow = list.selected();
		if (shadow != null) {
			ClientPlayNetworking.send(ShadowActionPayload.of(shadow.id(),
					ShadowActionPayload.Action.SQUAD));
		}
	}

	private void openDetails() {
		ShadowData shadow = list.selected();
		if (shadow != null && minecraft != null) {
			minecraft.setScreenAndShow(new ShadowDetailScreen(shadow.id(), this));
		}
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (list.mouseClicked(event.x(), event.y())) {
			ShadowData shadow = list.selected();
			selectedId = shadow == null ? null : shadow.id();
			return true;
		}

		return super.mouseClicked(event, doubleClick);
	}

	@Override
	public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
		if (list.mouseScrolled(mouseX, mouseY, scrollY)) {
			return true;
		}

		return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
	}

	// ---------------------------------------------------------------- disegno

	@Override
	protected Component status() {
		int cap = AriseConfig.get().shadows().maxSummoned();
		return Component.translatable("arise.screen.army.header", army().size(),
				summoned().ids().size(), cap, squad().size());
	}

	@Override
	protected Component hint() {
		return Component.translatable("arise.screen.army.hint");
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		ShadowConfig config = AriseConfig.get().shadows();

		list.items(sorted(config));
		if (selectedId != null) {
			list.selectFirst(shadow -> shadow.id().equals(selectedId));
		}

		sectionLabel(graphics, Component.translatable("arise.screen.army.roster"),
				bodyLeft(), bodyTop() + 5);

		sortButton.setMessage(Component.translatable("arise.screen.army.sort", sort.label()));

		if (list.isEmpty()) {
			graphics.text(font, Component.translatable("arise.screen.army.empty"),
					bodyLeft(), bodyTop() + 24, AriseTheme.DISABLED);
		} else {
			list.render(graphics, mouseX, mouseY, (g, shadow, x, y, width, selected, hovered) ->
					drawShadow(g, config, shadow, x, y, width));
		}

		drawDetail(graphics, config);
	}

	private void drawShadow(GuiGraphicsExtractor graphics, ShadowConfig config, ShadowData shadow,
			int x, int y, int width) {
		boolean out = summoned().contains(shadow.id());
		long resting = restingSeconds(shadow.id());
		int slot = squad().slotOf(shadow.id());

		// Il glifo dell'archetipo prende il colore dell'ombra: due informazioni nello stesso
		// disegno, e nessuna delle due costa una parola.
		Glyphs.archetype(graphics, shadow.archetype(), x + 5, y + 5, 0xFF000000 | shadow.color());

		graphics.text(font, shadow.displayName(), x + 18, y + 4,
				resting > 0 ? AriseTheme.DISABLED : out ? AriseTheme.ACCENT : AriseTheme.TEXT);
		graphics.text(font, Component.translatable("arise.screen.army.line", shadow.level(),
				String.format("%.0f", shadow.maxHealth(config)),
				String.format("%.1f", shadow.attackDamage(config))),
				x + 18, y + 14, AriseTheme.MUTED);

		ShadowGrade grade = shadow.grade(config);
		Component right = resting > 0
				? Component.translatable("arise.screen.army.resting_short", resting)
				: grade.label();
		int rightColor = resting > 0 ? AriseTheme.WARN : grade.color();
		graphics.text(font, right, x + width - font.width(right) - 6, y + 4, rightColor);

		// Il posto in squadra e il puntino di chi e' fuori stanno sulla stessa riga, a destra: e'
		// il posto in cui l'occhio scorre gia' per leggere il grado.
		int marker = x + width - 6;

		if (out) {
			graphics.fill(marker - 4, y + 15, marker, y + 19, AriseTheme.ACCENT);
			marker -= 8;
		}

		if (slot > 0) {
			Component tag = Component.translatable("arise.screen.army.slot", slot);
			graphics.text(font, tag, marker - font.width(tag), y + 14, AriseTheme.GOLD);
		}
	}

	private void drawDetail(GuiGraphicsExtractor graphics, ShadowConfig config) {
		int left = bodyLeft() + LIST_W + 12;
		int right = bodyRight();
		int y = bodyTop() + 18;

		ShadowData shadow = list.selected();

		toggle.visible = shadow != null;
		squadButton.visible = shadow != null;
		details.visible = shadow != null;

		if (shadow == null) {
			graphics.text(font, Component.translatable("arise.screen.army.pick"), left, y,
					AriseTheme.DISABLED);
			return;
		}

		boolean out = summoned().contains(shadow.id());
		boolean inSquad = squad().contains(shadow.id());
		long resting = restingSeconds(shadow.id());

		// Un'ombra a terra non si evoca, e il bottone lo dice invece di limitarsi a fallire: un
		// rifiuto senza spiegazione e' il modo piu' sicuro per far credere che sia un bug.
		toggle.active = out || resting == 0;
		toggle.setMessage(resting > 0
				? Component.translatable("arise.screen.army.resting", resting)
				: Component.translatable(out
						? "arise.screen.army.recall"
						: "arise.screen.army.summon"));

		squadButton.setMessage(Component.translatable(inSquad
				? "arise.screen.army.squad_out"
				: "arise.screen.army.squad_in"));
		squadButton.active = inSquad || squad().size() < config.maxSummoned();

		graphics.text(font, shadow.displayName(), left, y, AriseTheme.TEXT);
		y += 15;

		ShadowGrade grade = shadow.grade(config);
		int after = chip(graphics, grade.label(), left, y, grade.color());
		chip(graphics, shadow.archetype().label(), after + 4, y, shadow.archetype().color());
		y += 20;

		divider(graphics, left, right, y);
		y += 6;

		keyValue(graphics, Component.translatable("arise.screen.army.level"),
				Component.literal(String.valueOf(shadow.level())), left, right, y, AriseTheme.TEXT);
		y += 12;
		keyValue(graphics, Component.translatable("arise.screen.army.health"),
				Component.literal(String.format("%.0f", shadow.maxHealth(config))),
				left, right, y, AriseTheme.TEXT);
		y += 12;
		keyValue(graphics, Component.translatable("arise.screen.army.damage"),
				Component.literal(String.format("%.1f", shadow.attackDamage(config))),
				left, right, y, AriseTheme.TEXT);
		y += 12;
		keyValue(graphics, Component.translatable("arise.screen.army.rank"),
				shadow.rank(config).label(), left, right, y, shadow.rank(config).color());
		y += 16;

		// L'aura si mostra solo a chi ce l'ha. Una riga "aura: +0%" su venti ombre su venti
		// sarebbe rumore che nasconde l'unica volta in cui quel numero conta.
		if (grade.commands()) {
			keyValue(graphics, Component.translatable("arise.screen.army.aura"),
					Component.translatable("arise.screen.army.aura_value",
							String.format("%.0f", shadow.auraDamage(config) * 100.0),
							String.format("%.0f", shadow.auraHealth(config) * 100.0)),
					left, right, y, AriseTheme.GOLD);
			y += 16;
		}

		sectionLabel(graphics, Component.translatable(shadow.isMaxLevel(config)
				? "arise.screen.army.maxed"
				: "arise.screen.army.progress"), left, y);
		y += 11;

		long needed = shadow.xpForNextLevel(config);
		double fraction = shadow.isMaxLevel(config) || needed <= 0
				? 1.0
				: (double) shadow.xp() / needed;
		bar(graphics, left, y, right - left, 3, fraction, AriseTheme.ACCENT_DEEP);
	}
}
