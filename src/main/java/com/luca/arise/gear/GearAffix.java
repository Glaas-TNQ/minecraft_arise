package com.luca.arise.gear;

import java.util.List;

import com.luca.arise.progress.Stat;
import com.mojang.serialization.Codec;

import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;

/**
 * Il titolo di un pezzo: "del Lupo", "dell'Incudine".
 *
 * <p>Non e' solo un nome. L'affisso <em>garantisce</em> la sua statistica: un anello del Titano ha
 * di sicuro forza, qualunque cosa esca dagli altri tiri. Serve a poter cercare un pezzo invece di
 * subirlo — quando l'Abyss Shop mostrera' il suo assortimento, il nome dira' gia' a cosa serve.
 *
 * <p>Uno per statistica, incluse le quattro spendibili: cosi' ogni statistica ha un modo di essere
 * cercata di proposito.
 */
public enum GearAffix implements StringRepresentable {

	BEAR("bear", Stat.VITALITY),
	WOLF("wolf", Stat.AGILITY),
	TITAN("titan", Stat.STRENGTH),
	MOUNTAIN("mountain", Stat.ENDURANCE),
	ANVIL("anvil", Stat.TOUGHNESS),
	FALCON("falcon", Stat.HASTE),
	ANCHOR("anchor", Stat.RESOLVE),
	CRANE("crane", Stat.LEAP),
	SPEAR("spear", Stat.REACH),
	AEGIS("aegis", Stat.ABSORPTION),
	CROW("crow", Stat.FORTUNE),
	MAUL("maul", Stat.IMPACT);

	public static final Codec<GearAffix> CODEC = StringRepresentable.fromEnum(GearAffix::values);

	private static final List<GearAffix> ALL = List.of(values());

	private final String name;
	private final Stat stat;

	GearAffix(String name, Stat stat) {
		this.name = name;
		this.stat = stat;
	}

	@Override
	public String getSerializedName() {
		return name;
	}

	public Stat stat() {
		return stat;
	}

	public Component label() {
		return Component.translatable("arise.gear.affix." + name);
	}

	/** L'affisso che corrisponde a questa statistica. */
	public static GearAffix of(Stat stat) {
		for (GearAffix affix : ALL) {
			if (affix.stat == stat) {
				return affix;
			}
		}
		// Non raggiungibile finche' c'e' un affisso per statistica; meglio un pezzo brutto che un crash.
		return BEAR;
	}
}
