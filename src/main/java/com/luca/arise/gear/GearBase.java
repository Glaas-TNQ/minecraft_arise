package com.luca.arise.gear;

import java.util.List;

import com.luca.arise.progress.Stat;
import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Il tipo base di un pezzo: cosa e', prima di sapere quanto vale.
 *
 * <p>Serve a due cose. La prima e' il nome — "Diadema del Corvo" si legge meglio di "Testa di
 * rango A". La seconda sono le <em>affinita'</em>: gli stivali tirano fuori agilita' e slancio, i
 * guanti forza e prontezza. Senza affinita' ogni pezzo sarebbe una manciata di numeri a caso e
 * nessuno slot avrebbe un carattere.
 *
 * <p>Le affinita' non sono una garanzia: pesano di piu' nell'estrazione, non la decidono.
 */
public enum GearBase implements StringRepresentable {

	HELM("helm", GearSlot.HEAD, Stat.ENDURANCE, Stat.VITALITY),
	DIADEM("diadem", GearSlot.HEAD, Stat.FORTUNE, Stat.HASTE),

	CUIRASS("cuirass", GearSlot.CHEST, Stat.VITALITY, Stat.ENDURANCE),
	JERKIN("jerkin", GearSlot.CHEST, Stat.ABSORPTION, Stat.RESOLVE),

	GREAVES("greaves", GearSlot.LEGS, Stat.ENDURANCE, Stat.TOUGHNESS),
	TROUSERS("trousers", GearSlot.LEGS, Stat.AGILITY, Stat.LEAP),

	BOOTS("boots", GearSlot.FEET, Stat.AGILITY, Stat.LEAP),
	SABATONS("sabatons", GearSlot.FEET, Stat.ENDURANCE, Stat.RESOLVE),

	GLOVES("gloves", GearSlot.HANDS, Stat.HASTE, Stat.STRENGTH),
	GAUNTLETS("gauntlets", GearSlot.HANDS, Stat.STRENGTH, Stat.TOUGHNESS),

	SASH("sash", GearSlot.BELT, Stat.VITALITY, Stat.ABSORPTION),
	PAULDRONS("pauldrons", GearSlot.SHOULDERS, Stat.TOUGHNESS, Stat.IMPACT),
	CLOAK("cloak", GearSlot.CLOAK, Stat.AGILITY, Stat.FORTUNE),
	PENDANT("pendant", GearSlot.NECKLACE, Stat.VITALITY, Stat.FORTUNE),
	TALISMAN("talisman", GearSlot.TALISMAN, Stat.ABSORPTION, Stat.REACH),
	EARRING("earring", GearSlot.EARRING, Stat.HASTE, Stat.FORTUNE),
	RING("ring", GearSlot.RING, Stat.STRENGTH, Stat.REACH),

	// Le armi: la mano e' uno slot come gli altri, e la lama vanilla che le rappresenta porta
	// con se' il proprio danno base. I nostri modificatori ci si sommano sopra.
	SWORD("sword", GearSlot.WEAPON, Stat.STRENGTH, Stat.HASTE),
	AXE("axe", GearSlot.WEAPON, Stat.STRENGTH, Stat.IMPACT);

	public static final Codec<GearBase> CODEC = StringRepresentable.fromEnum(GearBase::values);

	private static final List<GearBase> ALL = List.of(values());

	private final String name;
	private final GearSlot slot;
	private final List<Stat> affinities;

	GearBase(String name, GearSlot slot, Stat... affinities) {
		this.name = name;
		this.slot = slot;
		this.affinities = List.of(affinities);
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public GearSlot slot() {
		return slot;
	}

	public List<Stat> affinities() {
		return affinities;
	}

	public Component label() {
		return Component.translatable("arise.gear.base." + name);
	}

	/** Le basi che stanno in questo slot. Mai vuota: ogni slot ne ha almeno una. */
	public static List<GearBase> of(GearSlot slot) {
		return ALL.stream().filter(base -> base.slot == slot).toList();
	}
}
