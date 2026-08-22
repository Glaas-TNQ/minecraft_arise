package com.luca.arise.registry;

import java.util.EnumMap;
import java.util.Map;
import java.util.Set;

import com.luca.arise.AriseMod;
import com.luca.arise.workshop.MachineBlock;
import com.luca.arise.workshop.MachineBlockEntity;
import com.luca.arise.workshop.MachineKind;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/**
 * I blocchi della mod: i quattro macchinari dell'Officina delle Anime.
 *
 * <p>Sono i primi blocchi veri di Arise, e arrivano dopo due sistemi interi costruiti senza —
 * l'equipaggiamento presta il corpo agli item vanilla, le ombre sono dati. Qui non si poteva:
 * un macchinario deve stare nel mondo, farsi vedere, farsi collegare a una tramoggia. Un
 * macchinario che fosse una voce di menu non sarebbe automazione, sarebbe un altro bottone.
 *
 * <p><strong>Nessuna texture nuova, comunque.</strong> I modelli sono cubi con facce prese dai
 * blocchi vanilla — sculk, ancora di rinascita, mattoni di ardesia — e stanno in
 * {@code assets/arise/models/block/}. Un blocco fatto cosi' si riconosce a colpo d'occhio e non
 * costa un solo file PNG. Il giorno in cui ci saranno texture vere si cambiano quei quattro JSON.
 *
 * <p>In 26.2 la registrazione passa da {@code Properties.setId(ResourceKey)}: senza, la
 * costruzione del blocco fallisce con un'eccezione invece di registrarsi in silenzio col nome
 * sbagliato. Vale anche per l'oggetto che lo piazza.
 */
public final class ModBlocks {

	private static final Map<MachineKind, Block> MACHINES = new EnumMap<>(MachineKind.class);

	public static final Block SOUL_LURE = machine(MachineKind.LURE);
	public static final Block SOUL_CRUCIBLE = machine(MachineKind.CRUCIBLE);
	public static final Block SHADOW_FORGE = machine(MachineKind.FORGE);
	public static final Block ABYSS_WELL = machine(MachineKind.WELL);

	/**
	 * Un tipo di block entity per tutti e quattro.
	 *
	 * <p>Il tipo elenca i blocchi che accetta, e {@code createTickerHelper} si appoggia proprio a
	 * questo elenco per rifiutare block entity che non sono nostre. Aggiungere un macchinario
	 * significa aggiungerlo qui, altrimenti il blocco si piazza ma non si apre mai.
	 */
	public static final net.minecraft.world.level.block.entity.BlockEntityType<MachineBlockEntity> MACHINE =
			Registry.register(BuiltInRegistries.BLOCK_ENTITY_TYPE, AriseMod.id("machine"),
					new net.minecraft.world.level.block.entity.BlockEntityType<>(
							MachineBlockEntity::new,
							Set.of(SOUL_LURE, SOUL_CRUCIBLE, SHADOW_FORGE, ABYSS_WELL)));

	private ModBlocks() {
	}

	/** Forza il caricamento della classe, e con essa la registrazione. */
	public static void init() {
	}

	/** Il blocco di un macchinario, per chi lo conosce solo come {@link MachineKind}. */
	public static Block of(MachineKind kind) {
		return MACHINES.get(kind);
	}

	private static Block machine(MachineKind kind) {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, AriseMod.id(kind.path()));
		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, AriseMod.id(kind.path()));

		// Duri quanto una fornace e resistenti come l'ossidiana debole: si rompono col piccone,
		// ma non li porta via un creeper di passaggio insieme a tutte le anime che contengono.
		BlockBehaviour.Properties properties = BlockBehaviour.Properties.of()
				.strength(3.5F, 12.0F)
				.requiresCorrectToolForDrops()
				.sound(SoundType.DEEPSLATE_BRICKS)
				// Luce sette: abbastanza da vedere l'officina di notte, non abbastanza da
				// trasformare quattro blocchi in un impianto di illuminazione.
				.lightLevel(state -> 7)
				.setId(blockKey);

		Block block = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				new MachineBlock(kind, properties));

		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(block, new Item.Properties()
						.useBlockDescriptionPrefix()
						.setId(itemKey)));

		MACHINES.put(kind, block);
		return block;
	}
}
