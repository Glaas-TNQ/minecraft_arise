package com.luca.arise.fx;

import com.luca.arise.AriseMod;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;

/**
 * I suoni della mod, registrati nel namespace {@code arise}.
 *
 * <p>Perché eventi propri e non le costanti di {@link net.minecraft.sounds.SoundEvents}: un evento
 * {@code arise:} ha un <em>sottotitolo tradotto</em> ("L'ombra si leva" invece di "Il Warden
 * emerge"), può essere richiamato con {@code /playsound} per provarlo, e il giorno in cui ci sarà
 * un file audio vero basta metterlo in {@code assets/arise/sounds/} e cambiare una riga di
 * {@code sounds.json} — nessuna riga di Java da toccare.
 *
 * <p>Nel frattempo {@code sounds.json} punta a file {@code .ogg} di Minecraft. Non è una
 * scorciatoia da nascondere: nessun asset generato, nessun file binario aggiunto al repo, e il
 * mixaggio (volume, tono, sovrapposizioni) è comunque tutto nostro e sta in {@link AriseFx}.
 */
public final class ModSounds {

	// ---- l'esercito d'ombra
	/** L'estrazione riesce: qualcosa emerge dal terreno. */
	public static final SoundEvent SHADOW_ARISE = create("shadow.arise");
	/** L'estrazione fallisce: il cadavere non risponde. */
	public static final SoundEvent SHADOW_REFUSED = create("shadow.refused");
	/** Evocazione. */
	public static final SoundEvent SHADOW_SUMMON = create("shadow.summon");
	/** Richiamo. */
	public static final SoundEvent SHADOW_RECALL = create("shadow.recall");
	/** Respiro d'ambiente di un'ombra evocata. */
	public static final SoundEvent SHADOW_AMBIENT = create("shadow.ambient");
	/** L'ombra incassa un colpo. */
	public static final SoundEvent SHADOW_HURT = create("shadow.hurt");
	/** L'ombra cade e torna nell'esercito. */
	public static final SoundEvent SHADOW_DEATH = create("shadow.death");

	// ---- il Sistema
	/** Salita di livello del giocatore. */
	public static final SoundEvent SYSTEM_LEVEL_UP = create("system.level_up");
	/** Un'ombra cambia rango. */
	public static final SoundEvent SYSTEM_RANK_UP = create("system.rank_up");

	// ---- i Gate
	public static final SoundEvent GATE_OPEN = create("gate.open");
	/** Battito lontano, mentre si esplora. */
	public static final SoundEvent GATE_AMBIENCE = create("gate.ambience");
	public static final SoundEvent GATE_BOSS = create("gate.boss");
	public static final SoundEvent GATE_CLEAR = create("gate.clear");

	// ---- le abilità
	/** Lancio generico, per le abilità che non ne hanno uno proprio. */
	public static final SoundEvent ABILITY_CAST = create("ability.cast");
	/** Dominio del Monarca. */
	public static final SoundEvent ABILITY_DOMAIN = create("ability.domain");
	/** Autorità del Sovrano. */
	public static final SoundEvent ABILITY_AUTHORITY = create("ability.authority");

	private ModSounds() {
	}

	private static SoundEvent create(String path) {
		Identifier id = AriseMod.id(path);
		return Registry.register(BuiltInRegistries.SOUND_EVENT, id, SoundEvent.createVariableRangeEvent(id));
	}

	/**
	 * Forza il caricamento della classe, e quindi le registrazioni.
	 *
	 * <p>Va chiamato dall'entrypoint: i registri di Minecraft si congelano subito dopo l'avvio, e
	 * una costante toccata per la prima volta a partita iniziata esploderebbe.
	 */
	public static void init() {
	}
}
