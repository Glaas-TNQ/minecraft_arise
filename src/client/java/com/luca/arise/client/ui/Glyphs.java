package com.luca.arise.client.ui;

import java.util.EnumMap;
import java.util.Map;

import com.luca.arise.gear.GearSlot;
import com.luca.arise.gem.GemType;
import com.luca.arise.shadow.ShadowArchetype;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Icone otto per otto, disegnate un pixel alla volta.
 *
 * <p>Una schermata con dodici slot tutti uguali si legge male: si finisce per rileggere i nomi
 * ogni volta invece di riconoscere la forma. Servivano delle icone, ma disegnarle come texture
 * avrebbe voluto dire diciassette file PNG da fare a mano — lo stesso problema che ha portato a
 * decidere che l'equipaggiamento non fosse fatto di item (design §8.1).
 *
 * <p>Quindi stanno qui, scritte come si vedono. Ogni {@code #} e' un pixel pieno, e disegnarle e'
 * una {@code fill} di un pixel per ogni {@code #}: al massimo sessantaquattro rettangoli per
 * icona, su schermate che ne mostrano una decina. Si modificano guardandole, che e' esattamente
 * quello che serve a un'icona di otto pixel.
 */
public final class Glyphs {

	private static final Map<GearSlot, String[]> SLOTS = new EnumMap<>(GearSlot.class);
	private static final Map<GemType, String[]> GEMS = new EnumMap<>(GemType.class);
	private static final Map<ShadowArchetype, String[]> ARCHETYPES = new EnumMap<>(ShadowArchetype.class);

	static {
		SLOTS.put(GearSlot.HEAD, new String[] {
				"        ",
				" ###### ",
				"##    ##",
				"# #  # #",
				"##    ##",
				"# #### #",
				" #    # ",
				"        " });

		SLOTS.put(GearSlot.CHEST, new String[] {
				"        ",
				"##    ##",
				"########",
				"# #### #",
				"# #### #",
				"# #### #",
				" ###### ",
				"        " });

		SLOTS.put(GearSlot.LEGS, new String[] {
				"        ",
				"########",
				"# #### #",
				"# #### #",
				"#  ##  #",
				"##    ##",
				"##    ##",
				"        " });

		SLOTS.put(GearSlot.FEET, new String[] {
				"        ",
				" ##     ",
				" ##     ",
				" ##     ",
				" ###    ",
				" #####  ",
				"####### ",
				"        " });

		SLOTS.put(GearSlot.HANDS, new String[] {
				"        ",
				"  # # # ",
				" ###### ",
				"####### ",
				"####### ",
				" ###### ",
				"  ####  ",
				"        " });

		SLOTS.put(GearSlot.BELT, new String[] {
				"        ",
				"        ",
				"########",
				"##    ##",
				"##  # ##",
				"########",
				"        ",
				"        " });

		SLOTS.put(GearSlot.SHOULDERS, new String[] {
				"        ",
				"  ####  ",
				" ###### ",
				"########",
				"##    ##",
				"#      #",
				"        ",
				"        " });

		SLOTS.put(GearSlot.CLOAK, new String[] {
				"        ",
				" ##  ## ",
				" ###### ",
				" ###### ",
				"  ####  ",
				"  ####  ",
				"   ##   ",
				"        " });

		SLOTS.put(GearSlot.NECKLACE, new String[] {
				"        ",
				" ##  ## ",
				"  #  #  ",
				"  #  #  ",
				"   ##   ",
				"  ####  ",
				"   ##   ",
				"        " });

		SLOTS.put(GearSlot.TALISMAN, new String[] {
				"        ",
				"   ##   ",
				"  ####  ",
				" ###### ",
				"  ####  ",
				"   ##   ",
				"        ",
				"        " });

		SLOTS.put(GearSlot.EARRING, new String[] {
				"        ",
				"   ##   ",
				"        ",
				"  ####  ",
				" ##  ## ",
				" ##  ## ",
				"  ####  ",
				"        " });

		SLOTS.put(GearSlot.RING, new String[] {
				"        ",
				"  ####  ",
				" ##  ## ",
				"##    ##",
				"##    ##",
				" ##  ## ",
				"  ####  ",
				"        " });

		// Le gemme si distinguono per il taglio, non per il colore: chi non distingue i colori
		// deve poterle riconoscere lo stesso.
		GEMS.put(GemType.HEMATITE, new String[] {
				"        ",
				"  ####  ",
				" ###### ",
				"########",
				"########",
				" ###### ",
				"  ####  ",
				"        " });

		GEMS.put(GemType.OBSIDIAN, new String[] {
				"        ",
				" ###### ",
				" ###### ",
				" ###### ",
				" ###### ",
				" ###### ",
				" ###### ",
				"        " });

		GEMS.put(GemType.SAPPHIRE, new String[] {
				"        ",
				"   ##   ",
				"  ####  ",
				" ###### ",
				"########",
				" ###### ",
				"   ##   ",
				"        " });

		GEMS.put(GemType.AMETHYST, new String[] {
				"        ",
				" ###### ",
				"########",
				" ###### ",
				"  ####  ",
				"   ##   ",
				"        ",
				"        " });

		GEMS.put(GemType.JASPER, new String[] {
				"        ",
				"   ##   ",
				" ###### ",
				"########",
				"########",
				" ###### ",
				"   ##   ",
				"        " });
	}

	static {
		// Scudo: la Guardia e' quella che si mette in mezzo.
		ARCHETYPES.put(ShadowArchetype.GUARD, new String[] {
				" ###### ",
				" ###### ",
				" ###### ",
				" ###### ",
				"  ####  ",
				"  ####  ",
				"   ##   ",
				"        " });

		// Zanne: la Bestia morde.
		ARCHETYPES.put(ShadowArchetype.BEAST, new String[] {
				"        ",
				"##    ##",
				"###  ###",
				"########",
				" ###### ",
				"  #  #  ",
				" ##  ## ",
				"        " });

		// Rombo su un'asta: il Mago colpisce da lontano.
		ARCHETYPES.put(ShadowArchetype.MAGE, new String[] {
				"   ##   ",
				"  ####  ",
				" ###### ",
				"  ####  ",
				"   ##   ",
				"   ##   ",
				"   ##   ",
				"   ##   " });

		// Blocco pieno con la base larga: il Colosso non si sposta.
		ARCHETYPES.put(ShadowArchetype.TANK, new String[] {
				"        ",
				"  ####  ",
				" ###### ",
				" ###### ",
				" ###### ",
				"########",
				"########",
				"        " });
	}

	private Glyphs() {
	}

	public static void slot(GuiGraphicsExtractor graphics, GearSlot slot, int x, int y, int color) {
		draw(graphics, SLOTS.get(slot), x, y, color);
	}

	public static void gem(GuiGraphicsExtractor graphics, GemType type, int x, int y, int color) {
		draw(graphics, GEMS.get(type), x, y, color);
	}

	/**
	 * Il segno dell'archetipo: scudo, zanne, asta, blocco.
	 *
	 * <p>E' l'unica cosa che in una lista di venti ombre si legge senza leggere. Il nome
	 * dell'archetipo sta nel dettaglio; nella riga basta la forma.
	 */
	public static void archetype(GuiGraphicsExtractor graphics, ShadowArchetype archetype,
			int x, int y, int color) {
		draw(graphics, ARCHETYPES.get(archetype), x, y, color);
	}

	/**
	 * Un rombo pieno: il segno del rango, accanto a ogni cosa che ne ha uno.
	 *
	 * <p>Non e' nella tabella perche' non e' un disegno, e' una forma calcolata — cosi' cambia
	 * misura senza che si debba ridisegnare niente.
	 */
	public static void rankPip(GuiGraphicsExtractor graphics, int x, int y, int size, int color) {
		int half = size / 2;

		for (int row = 0; row < size; row++) {
			int spread = half - Math.abs(row - half);
			graphics.fill(x + half - spread, y + row, x + half + spread + 1, y + row + 1, color);
		}
	}

	private static void draw(GuiGraphicsExtractor graphics, String[] rows, int x, int y, int color) {
		if (rows == null) {
			return;
		}

		for (int row = 0; row < rows.length; row++) {
			String line = rows[row];

			// Le corse orizzontali si disegnano in un colpo solo: una riga piena costa un
			// rettangolo invece di otto.
			int start = -1;
			for (int column = 0; column <= line.length(); column++) {
				boolean on = column < line.length() && line.charAt(column) != ' ';

				if (on && start < 0) {
					start = column;
				} else if (!on && start >= 0) {
					graphics.fill(x + start, y + row, x + column, y + row + 1, color);
					start = -1;
				}
			}
		}
	}
}
