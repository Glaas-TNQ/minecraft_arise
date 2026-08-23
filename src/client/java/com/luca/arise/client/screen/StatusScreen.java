package com.luca.arise.client.screen;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.luca.arise.client.ui.AriseScreen;
import com.luca.arise.client.ui.AriseTheme;
import com.luca.arise.config.AriseConfig;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.client.ClientGear;
import com.luca.arise.gem.Gem;
import com.luca.arise.network.SpendPointPayload;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.Stat;
import com.luca.arise.progress.StatThreshold;
import com.luca.arise.registry.ModAttachments;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;

/**
 * Lo stato del Sistema: livello, punti, statistiche.
 *
 * <p>Non modifica nulla in locale. Ogni "+" manda un {@link SpendPointPayload} al server; quando il
 * server accetta, l'attachment sincronizzato torna indietro e la schermata si ridisegna da sola.
 * Se il server rifiuta, qui non cambia niente — che e' esattamente il comportamento voluto.
 *
 * <p>Due colonne: a sinistra le quattro statistiche spendibili, con la barra che dice quanto manca
 * al tetto; a destra tutto quello che arriva da fuori i punti — equipaggiamento e gemme — perche'
 * la domanda vera davanti a questa schermata non e' "quanto ho" ma "da dove viene".
 */
public class StatusScreen extends AriseScreen {

	private static final int PANEL_W = 400;
	private static final int PANEL_H = 234;
	private static final int LEFT_W = 220;
	/**
	 * Alto quanto le tre righe che ci stanno dentro: nome, cosa produce, barra.
	 *
	 * <p>Era 26 quando le righe erano due, 30 quando sono diventate tre. Adesso sono quattro — nome,
	 * cosa produce, barra, quanto manca alla prossima soglia — e vale la stessa cosa di allora: un
	 * valore che non tiene conto di cio' che disegna e' il modo piu' rapido di ritrovarsi la barra
	 * di una statistica appoggiata sul nome della prossima.
	 */
	private static final int ROW = 36;

	private final Map<Stat, Button> buttons = new EnumMap<>(Stat.class);

	/** La riga sotto il mouse in questo fotogramma. Si azzera a ogni disegno. */
	private Stat hovered;

	public StatusScreen() {
		super(Component.translatable("arise.screen.status.title"), PANEL_W, PANEL_H);
	}

	/** Quanto una statistica riceve da equipaggiamento e gemme indossate. */
	private double external(Stat stat) {
		double total = 0.0;

		for (GearPiece piece : ClientGear.worn(minecraft == null ? null : minecraft.player)) {
			total += piece.stats().getOrDefault(stat, 0.0);

			for (Gem gem : piece.gems()) {
				total += gem.stats().getOrDefault(stat, 0.0);
			}
		}

		return total;
	}

	@Override
	protected void layout() {
		buttons.clear();

		int y = bodyTop() + 22;
		for (Stat stat : Stat.SPENDABLE) {
			buttons.put(stat, addRenderableWidget(Button.builder(Component.literal("+"),
							button -> ClientPlayNetworking.send(new SpendPointPayload(stat, 1)))
					.bounds(bodyLeft() + LEFT_W - 34, y - 4, 18, 18).build()));
			y += ROW;
		}
	}

	@Override
	protected Component status() {
		PlayerProgress progress = progress();
		return Component.translatable("arise.screen.status.level", progress.level(),
				progress.xp(), AriseConfig.get().xpForNextLevel(progress.level()));
	}

	/** L'ultima soglia gia' superata, o zero: e' il punto da cui la barra riparte. */
	private static int previousStep(Stat stat, int points) {
		int previous = 0;

		for (StatThreshold threshold : StatThreshold.of(stat)) {
			if (points >= threshold.points()) {
				previous = threshold.points();
			}
		}

		return previous;
	}

	@Override
	protected Component hint() {
		return Component.translatable("arise.screen.status.hint", key("status"));
	}

	@Override
	protected void content(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		LocalPlayer player = minecraft != null ? minecraft.player : null;
		if (player == null) {
			return;
		}

		AriseConfig config = AriseConfig.get();
		PlayerProgress progress = progress();
		hovered = null;
		int left = bodyLeft();
		int valueRight = left + LEFT_W - 40;

		graphics.text(font, Component.translatable("arise.screen.status.points",
				progress.unspentPoints()), left, bodyTop() + 6,
				progress.unspentPoints() > 0 ? AriseTheme.GOLD : AriseTheme.DISABLED);

		int y = bodyTop() + 22;
		for (Stat stat : Stat.SPENDABLE) {
			int points = progress.stat(stat);
			int cap = config.cap(stat);

			// Lo stato del bottone si ricalcola a ogni frame dai dati sincronizzati: resta coerente
			// anche quando il server rifiuta o quando i punti arrivano da un livello.
			Button button = buttons.get(stat);
			if (button != null) {
				button.active = progress.unspentPoints() > 0 && points < cap;
			}

			graphics.text(font, Component.translatable(stat.translationKey()), left, y, AriseTheme.TEXT);

			Component amount = Component.literal(points + "/" + cap);
			graphics.text(font, amount, valueRight - font.width(amount), y, AriseTheme.MUTED);

			// La riga che mancava: cosa hanno prodotto i punti spesi, e cosa produrrebbe il
			// prossimo. Senza, la schermata diceva "Forza 12/400" — due numeri che non rispondono
			// alla sola domanda che si ha davanti a un bottone "+".
			double perPoint = config.perPoint(stat);
			Component gives = Component.translatable("arise.screen.status.gives",
					stat.format(points * perPoint), stat.effect());
			Component step = Component.translatable("arise.screen.status.per_point",
					stat.format(perPoint));

			graphics.text(font, gives, left, y + 10, AriseTheme.GOOD);
			graphics.text(font, step, valueRight - font.width(step), y + 10, AriseTheme.DISABLED);

			// La barra non punta piu' al tetto: punta alla prossima soglia, e dice cosa da'.
			//
			// Il tetto e' un numero che non succede: arrivare a quattrocento in Vitalita' non e' un
			// traguardo, e' la fine della statistica. Una soglia invece e' un traguardo vicino con
			// un nome, e vedere il lucchetto prima della chiave e' meta' del motivo per spendere
			// il punto. Quando non ne restano, la barra torna a misurare il tetto — perche' a quel
			// punto il tetto e' davvero l'unica cosa rimasta davanti.
			StatThreshold next = StatThreshold.next(stat, points);

			if (next == null) {
				bar(graphics, left, y + 21, LEFT_W - 40, 2, cap <= 0 ? 0.0 : (double) points / cap,
						AriseTheme.ACCENT_DEEP);
			} else {
				int previous = previousStep(stat, points);
				double span = Math.max(1, next.points() - previous);

				bar(graphics, left, y + 21, LEFT_W - 40, 2, (points - previous) / span,
						AriseTheme.VIOLET);

				Component toGo = Component.translatable("arise.screen.status.to_threshold",
						next.points() - points, next.label());
				graphics.text(font, toGo, left, y + 25, AriseTheme.VIOLET);
			}

			// Il riquadro al passaggio del mouse: la spiegazione lunga sta qui, dove non ruba
			// spazio a nessuno e la trova chiunque si fermi un istante sulla riga che sta per
			// premere.
			if (mouseX >= left && mouseX <= valueRight && mouseY >= y - 3 && mouseY <= y + 20) {
				hovered = stat;
			}

			y += ROW;
		}

		drawSources(graphics, player, mouseX, mouseY);

		if (hovered != null) {
			graphics.setComponentTooltipForNextFrame(font, explain(hovered, player), mouseX, mouseY);
		}
	}

	/**
	 * Cosa dice il riquadro di una statistica.
	 *
	 * <p>Quattro cose, nell'ordine in cui servono: che parametro del gioco e', cosa cambia in
	 * partita, quanto vale un punto, e quanto se ne ha adesso — diviso fra quello che si e' speso e
	 * quello che arriva dall'equipaggiamento. La domanda "a cosa serve l'agilita'" e quella "mi
	 * conviene spendere qui" hanno risposte diverse, e questa lista risponde a entrambe.
	 */
	private List<Component> explain(Stat stat, LocalPlayer player) {
		AriseConfig config = AriseConfig.get();
		double perPoint = config.perPoint(stat);
		double spent = progress().stat(stat) * perPoint;
		double bonus = external(stat);

		List<Component> lines = new ArrayList<>();
		lines.add(Component.translatable(stat.translationKey()).withStyle(ChatFormatting.WHITE));
		lines.add(stat.effect().copy().withStyle(ChatFormatting.AQUA));
		lines.add(stat.description().copy().withStyle(ChatFormatting.GRAY));
		lines.add(Component.empty());

		if (stat.spendable()) {
			lines.add(Component.translatable("arise.screen.status.tip_per_point",
					stat.format(perPoint)).withStyle(ChatFormatting.DARK_GRAY));
			lines.add(Component.translatable("arise.screen.status.tip_spent",
					stat.format(spent)).withStyle(ChatFormatting.DARK_GRAY));
		}

		if (bonus != 0.0) {
			lines.add(Component.translatable("arise.screen.status.tip_gear",
					stat.format(bonus)).withStyle(ChatFormatting.DARK_GRAY));
		}

		lines.add(Component.translatable("arise.screen.status.tip_total",
				String.format("%.2f", player.getAttributeValue(stat.attribute())))
				.withStyle(ChatFormatting.DARK_GRAY));

		return lines;
	}

	private void drawSources(GuiGraphicsExtractor graphics, LocalPlayer player, int mouseX, int mouseY) {
		int left = bodyLeft() + LEFT_W + 10;
		int right = bodyRight();
		int y = bodyTop() + 6;

		sectionLabel(graphics, Component.translatable("arise.screen.status.sources"), left, y);
		y += 13;

		// Tutte e dodici, non solo le spendibili: le altre otto esistono solo grazie
		// all'equipaggiamento, e questo e' l'unico posto dove si vedono.
		for (Stat stat : Stat.values()) {
			double bonus = external(stat);
			double total = player.getAttributeValue(stat.attribute());

			if (bonus == 0.0 && !stat.spendable()) {
				continue;
			}

			// Il nome della statistica e, di seguito e in grigio, il parametro del gioco che quel
			// numero e': "Forza · danno da mischia — 5,80" si legge senza doverlo imparare.
			keyValue(graphics, Component.translatable(stat.translationKey())
							.append(Component.literal("  ").append(stat.effect())
									.withStyle(ChatFormatting.DARK_GRAY)),
					Component.literal(String.format("%.2f", total)), left, right, y, AriseTheme.TEXT);

			if (mouseX >= left && mouseX <= right && mouseY >= y - 1 && mouseY <= y + 9) {
				hovered = stat;
			}

			if (bonus != 0.0) {
				Component from = Component.literal(stat.format(bonus));
				graphics.text(font, from, right - font.width(from) - 44, y, AriseTheme.GOOD);
			}

			y += 11;
		}
	}
}
