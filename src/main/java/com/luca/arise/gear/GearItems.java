package com.luca.arise.gear;

import java.util.EnumMap;
import java.util.Map;

import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.DyedItemColor;

/**
 * Che aspetto ha un pezzo di equipaggiamento.
 *
 * <p>Nessuna texture nuova, ed e' una scelta e non una rinuncia. Vanilla ha gia' <em>due scale di
 * materiali</em> che il giocatore sa leggere da anni senza che nessuno gliele spieghi: cuoio →
 * rame → maglia → ferro → diamante → netherite per le armature, legno → rame → pietra → ferro →
 * diamante → netherite per le armi. Sono sei gradini per sei ranghi. Il rango di un pezzo si vede
 * da lontano, in mano e addosso, prima ancora di leggerne il nome.
 *
 * <p>Per gli accessori — anelli, collane, mantelli — vanilla non ha una scala, quindi lo sprite
 * dice <em>cosa</em> e' e il colore del nome dice quanto vale. Un pepita d'oro per l'anello, una
 * catena di ferro per il pendente: sono placeholder, ma sono placeholder che si distinguono a
 * colpo d'occhio in un inventario pieno, che e' l'unica cosa che deve fare uno sprite.
 *
 * <p>Il giorno in cui ci saranno immagini vere si cambia questa classe e nient'altro.
 */
public final class GearItems {

	/** Un gradino per rango, da E a S. */
	private static final Item[] HELMETS = {
			Items.LEATHER_HELMET, Items.COPPER_HELMET, Items.CHAINMAIL_HELMET,
			Items.IRON_HELMET, Items.DIAMOND_HELMET, Items.NETHERITE_HELMET};

	private static final Item[] CHESTPLATES = {
			Items.LEATHER_CHESTPLATE, Items.COPPER_CHESTPLATE, Items.CHAINMAIL_CHESTPLATE,
			Items.IRON_CHESTPLATE, Items.DIAMOND_CHESTPLATE, Items.NETHERITE_CHESTPLATE};

	private static final Item[] LEGGINGS = {
			Items.LEATHER_LEGGINGS, Items.COPPER_LEGGINGS, Items.CHAINMAIL_LEGGINGS,
			Items.IRON_LEGGINGS, Items.DIAMOND_LEGGINGS, Items.NETHERITE_LEGGINGS};

	private static final Item[] BOOTS = {
			Items.LEATHER_BOOTS, Items.COPPER_BOOTS, Items.CHAINMAIL_BOOTS,
			Items.IRON_BOOTS, Items.DIAMOND_BOOTS, Items.NETHERITE_BOOTS};

	private static final Item[] SWORDS = {
			Items.WOODEN_SWORD, Items.COPPER_SWORD, Items.STONE_SWORD,
			Items.IRON_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD};

	private static final Item[] AXES = {
			Items.WOODEN_AXE, Items.COPPER_AXE, Items.STONE_AXE,
			Items.IRON_AXE, Items.DIAMOND_AXE, Items.NETHERITE_AXE};

	/** Gli accessori non hanno una scala: uno sprite riconoscibile per tipo, sempre lo stesso. */
	private static final Map<GearBase, Item> ACCESSORIES = accessories();

	private GearItems() {
	}

	private static Map<GearBase, Item> accessories() {
		EnumMap<GearBase, Item> map = new EnumMap<>(GearBase.class);
		map.put(GearBase.GLOVES, Items.RABBIT_HIDE);
		map.put(GearBase.GAUNTLETS, Items.NETHERITE_SCRAP);
		map.put(GearBase.SASH, Items.LEATHER);
		map.put(GearBase.PAULDRONS, Items.SHULKER_SHELL);
		map.put(GearBase.CLOAK, Items.PHANTOM_MEMBRANE);
		map.put(GearBase.PENDANT, Items.IRON_CHAIN);
		map.put(GearBase.TALISMAN, Items.HEART_OF_THE_SEA);
		map.put(GearBase.EARRING, Items.AMETHYST_SHARD);
		map.put(GearBase.RING, Items.GOLD_NUGGET);
		return map;
	}

	/** L'oggetto vanilla che fa da corpo a questo pezzo. */
	public static Item item(GearBase base, Rank rank) {
		int tier = rank.ordinal();

		return switch (base) {
			case HELM, DIADEM -> HELMETS[tier];
			case CUIRASS, JERKIN -> CHESTPLATES[tier];
			case GREAVES, TROUSERS -> LEGGINGS[tier];
			case BOOTS, SABATONS -> BOOTS[tier];
			case SWORD -> SWORDS[tier];
			case AXE -> AXES[tier];
			default -> ACCESSORIES.getOrDefault(base, Items.GOLD_NUGGET);
		};
	}

	/**
	 * L'oggetto vero e proprio: corpo vanilla, dato del Sistema addosso.
	 *
	 * <p>Tre componenti fanno il lavoro che altrove costerebbe una classe di item registrata:
	 * il nostro, che porta il pezzo; {@code ITEM_NAME}, che rinomina senza far sembrare l'oggetto
	 * ribattezzato all'incudine (e senza il corsivo che vanilla mette ai rinominati); e
	 * {@code UNBREAKABLE}, perche' un pezzo di rango A che si consuma e sparisce e' un modo
	 * gratuito di far arrabbiare qualcuno.
	 */
	public static ItemStack stack(GearPiece piece) {
		ItemStack stack = new ItemStack(item(piece.base(), piece.rank()));

		stack.set(ModComponents.GEAR_PIECE, piece);
		stack.set(DataComponents.ITEM_NAME, piece.displayName());
		stack.set(DataComponents.UNBREAKABLE, Unit.INSTANCE);

		// Due pezzi non si impilano mai davvero, perche' ognuno ha il suo UUID; il limite a uno
		// serve a non far vedere "64" sotto un anello nella casella del Cacciatore.
		stack.set(DataComponents.MAX_STACK_SIZE, 1);

		// Il cuoio e' l'unico materiale tingibile, e capita esattamente al rango piu' basso: i
		// pezzi E escono di un grigio-azzurro uniforme, che li fa leggere come dotazione di
		// ordinanza invece che come armatura da contadino.
		if (piece.rank() == Rank.E && piece.slot().vanillaSlot() != null
				&& piece.slot().vanillaSlot().isArmor()) {
			stack.set(DataComponents.DYED_COLOR, new DyedItemColor(Rank.E.color() & 0xFFFFFF));
		}

		// Il luccichio dice "questo non e' un pezzo qualunque", e sono due i casi in cui e' vero:
		// il rango piu' alto, e i pezzi unici — che di rango possono essere anche i piu' bassi.
		if (piece.rank() == Rank.S || piece.unique().isPresent()) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}

		return stack;
	}

	/** Il pezzo che questo oggetto porta, o {@code null} se e' un oggetto qualunque. */
	public static GearPiece piece(ItemStack stack) {
		return stack.isEmpty() ? null : stack.get(ModComponents.GEAR_PIECE);
	}

	public static boolean isGear(ItemStack stack) {
		return piece(stack) != null;
	}

	/**
	 * Rimpiazza il pezzo dentro un oggetto che esiste gia'.
	 *
	 * <p>Serve all'incastonatura. Si scrive sull'oggetto invece di ricostruirlo da zero perche'
	 * l'oggetto puo' portarsi dietro cose che il pezzo non conosce — un incantesimo, un nome
	 * cambiato all'incudine — e ricostruirlo le butterebbe via in silenzio.
	 */
	public static ItemStack withPiece(ItemStack stack, GearPiece piece) {
		stack.set(ModComponents.GEAR_PIECE, piece);
		stack.set(DataComponents.ITEM_NAME, piece.displayName());
		return stack;
	}
}
