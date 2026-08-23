package com.luca.arise.registry;

import java.util.EnumMap;
import java.util.Map;

import java.util.function.Function;

import com.luca.arise.AriseMod;
import com.luca.arise.city.AssociationSealItem;
import com.luca.arise.gate.AbyssCompassItem;
import com.luca.arise.progress.RegretScrollItem;
import com.luca.arise.workshop.BlueprintItem;
import com.luca.arise.workshop.MachineKind;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;

/**
 * I due oggetti che il mercato ha reso necessari.
 *
 * <p>Sono i primi item <em>registrati</em> della mod, dopo due sistemi interi costruiti prestando
 * il corpo agli oggetti vanilla. La regola non e' cambiata — resta quella di {@code GearItems} — e
 * questi due sono l'eccezione che la conferma, perche' entrambi devono fare qualcosa che un
 * componente su un oggetto vanilla non puo' fare:
 *
 * <ul>
 *   <li>la <strong>Moneta d'Anima</strong> deve stare dentro un {@code ItemCost}, e la finestra di
 *       scambio confronta l'<em>oggetto</em>, non i suoi componenti: una moneta appoggiata su un
 *       lingotto d'oro sarebbe comprabile con un lingotto d'oro qualunque;
 *   <li>un <strong>Progetto</strong> deve comparire in una ricetta da banco, e gli ingredienti
 *       vanilla non sanno guardare dentro un componente: un Progetto appoggiato su un foglio di
 *       carta si costruirebbe con un foglio di carta.
 * </ul>
 *
 * <p>Le texture restano prestate: i modelli in {@code assets/arise/models/item/} puntano a sprite
 * vanilla. Un oggetto registrato non obbliga a disegnare niente.
 */
public final class ModItems {

	/** I Progetti, uno per macchinario. Consumati dalla ricetta che aprono. */
	private static final Map<MachineKind, Item> BLUEPRINTS = new EnumMap<>(MachineKind.class);

	/**
	 * La Moneta d'Anima: i soul coin resi trasportabili.
	 *
	 * <p>Si conia al Banco dell'Abisso e li' si riporta indietro. Non e' un giro a vuoto: e' cio'
	 * che rende il denaro <em>perdibile</em>. Un soul coin sta al sicuro dentro il Sistema; una
	 * moneta sta nello zaino, e se muori in un Gate resta li'.
	 */
	public static final Item SOUL_COIN = register("soul_coin", Rarity.UNCOMMON, 64);

	public static final Item BLUEPRINT_LURE = blueprint(MachineKind.LURE);
	public static final Item BLUEPRINT_CRUCIBLE = blueprint(MachineKind.CRUCIBLE);
	public static final Item BLUEPRINT_FORGE = blueprint(MachineKind.FORGE);
	public static final Item BLUEPRINT_WELL = blueprint(MachineKind.WELL);

	/**
	 * La Bussola dell'Abisso: punta al varco piu' vicino invece che a un nord fisso. La logica sta
	 * in {@link AbyssCompassItem}; qui e' solo la registrazione, come ogni altro item della mod.
	 */
	public static final Item ABYSS_COMPASS = register("abyss_compass", Rarity.UNCOMMON, 1, AbyssCompassItem::new);

	/**
	 * Il Sigillo dell'Associazione: apre la rete di viaggio ovunque ci si trovi.
	 *
	 * <p>Raro e in copia unica, come la Bussola: non e' un oggetto da accumulare, e' una chiave.
	 * Lo consegna l'incarico che concede il viaggio fra le citta'. Vedi {@link AssociationSealItem}.
	 */
	public static final Item ASSOCIATION_SEAL =
			register("association_seal", Rarity.RARE, 1, AssociationSealItem::new);

	/**
	 * La Pergamena del Rimpianto: restituisce i punti statistica spesi.
	 *
	 * <p>Impilabile fino a quattro, a differenza del Sigillo e della Bussola, perche' non e' una
	 * chiave: e' un consumabile, e chi ne compra tre lo fa apposta. Vedi {@link RegretScrollItem}.
	 */
	public static final Item REGRET_SCROLL =
			register("regret_scroll", Rarity.RARE, 4, RegretScrollItem::new);

	private ModItems() {
	}

	/** Forza il caricamento della classe, e con essa la registrazione. */
	public static void init() {
	}

	/** Il Progetto di un macchinario. */
	public static Item blueprintOf(MachineKind kind) {
		return BLUEPRINTS.get(kind);
	}

	/** Il Progetto che questo id nomina, per le ricompense degli incarichi. {@code null} se non c'e'. */
	public static Item byPath(String path) {
		return BuiltInRegistries.ITEM.getOptional(AriseMod.id(path)).orElse(null);
	}

	public static ItemStack coins(int count) {
		return new ItemStack(SOUL_COIN, count);
	}

	private static Item blueprint(MachineKind kind) {
		// Rari, e non per vanita': un Progetto e' l'unica cosa che sta fra un giocatore e un
		// macchinario, quindi deve saltare all'occhio in un baule pieno di roba.
		Item item = register("blueprint_" + kind.path(), Rarity.RARE, 16,
				properties -> new BlueprintItem(properties, kind));
		BLUEPRINTS.put(kind, item);
		return item;
	}

	private static Item register(String path, Rarity rarity, int stackSize) {
		return register(path, rarity, stackSize, Item::new);
	}

	private static Item register(String path, Rarity rarity, int stackSize,
			Function<Item.Properties, Item> factory) {
		ResourceKey<Item> key = ResourceKey.create(Registries.ITEM, AriseMod.id(path));

		return Registry.register(BuiltInRegistries.ITEM, key,
				factory.apply(new Item.Properties()
						.rarity(rarity)
						.stacksTo(stackSize)
						.setId(key)));
	}
}
