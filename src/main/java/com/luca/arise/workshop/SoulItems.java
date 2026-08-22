package com.luca.arise.workshop;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModComponents;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Che aspetto hanno un'anima e un catalizzatore.
 *
 * <p>Stessa idea di {@code GearItems}, e per gli stessi motivi: nessuna texture nuova, corpo
 * prestato da un oggetto vanilla, dato vero nel componente. Qui la scala e' piu' corta perche' le
 * anime sono una cosa sola vista da vicino o da lontano — tre gradini bastano a dire "questa vale
 * la pena tenerla" senza sei sprite diversi da imparare.
 *
 * <ul>
 *   <li><strong>E-D</strong> lacrima di ghast: pallida, comune, appena luminosa;
 *   <li><strong>C-B</strong> frammento d'eco: gia' qualcosa che si conserva;
 *   <li><strong>A-S</strong> stella del Nether: si vede da dentro una cassa piena.
 * </ul>
 *
 * <p>I catalizzatori restano un unico sprite per tutti e sei i gradi: sono materiale di consumo, e
 * quello che conta e' leggerne il grado nel nome, non riconoscerli a colpo d'occhio.
 */
public final class SoulItems {

	private static final Item[] SOUL_BODIES = {
			Items.GHAST_TEAR, Items.GHAST_TEAR,
			Items.ECHO_SHARD, Items.ECHO_SHARD,
			Items.NETHER_STAR, Items.NETHER_STAR};

	private static final Item CATALYST_BODY = Items.PRISMARINE_CRYSTALS;

	private SoulItems() {
	}

	/**
	 * Il rango di un'anima, letto dalle soglie di config.
	 *
	 * <p>Lo stesso metro delle ombre, non un secondo: un'anima di zombie e un'ombra di zombie
	 * devono dire lo stesso rango, altrimenti arruolare farebbe cambiare colore alla riga.
	 */
	public static Rank rankOf(LooseSoul soul) {
		return soul.rank(AriseConfig.get().shadows().rankThresholds());
	}

	public static ItemStack stack(LooseSoul soul) {
		Rank rank = rankOf(soul);
		ItemStack stack = new ItemStack(SOUL_BODIES[rank.ordinal()]);

		stack.set(ModComponents.SOUL, soul);
		stack.set(DataComponents.ITEM_NAME, soul.displayName().copy().withStyle(rank.chatColor()));

		// Ogni anima ha il suo UUID, quindi due non si sommerebbero mai davvero: il limite a uno
		// serve solo a non far comparire un "64" fantasma sotto lo sprite.
		stack.set(DataComponents.MAX_STACK_SIZE, 1);

		if (rank.ordinal() >= Rank.A.ordinal()) {
			stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
		}

		return stack;
	}

	public static ItemStack catalyst(Rank grade, int count) {
		Catalyst catalyst = new Catalyst(grade);
		ItemStack stack = new ItemStack(CATALYST_BODY, count);

		stack.set(ModComponents.CATALYST, catalyst);
		stack.set(DataComponents.ITEM_NAME, catalyst.displayName());

		return stack;
	}

	/** L'anima che questo oggetto porta, oppure {@code null} se e' un oggetto qualunque. */
	public static LooseSoul soul(ItemStack stack) {
		return stack.isEmpty() ? null : stack.get(ModComponents.SOUL);
	}

	public static Catalyst catalystOf(ItemStack stack) {
		return stack.isEmpty() ? null : stack.get(ModComponents.CATALYST);
	}

	public static boolean isSoul(ItemStack stack) {
		return soul(stack) != null;
	}

	public static boolean isCatalyst(ItemStack stack) {
		return catalystOf(stack) != null;
	}

	/** Riscrive l'anima dentro un oggetto che esiste gia', nome compreso. */
	public static ItemStack withSoul(ItemStack stack, LooseSoul soul) {
		stack.set(ModComponents.SOUL, soul);
		stack.set(DataComponents.ITEM_NAME,
				soul.displayName().copy().withStyle(rankOf(soul).chatColor()));
		return stack;
	}
}
