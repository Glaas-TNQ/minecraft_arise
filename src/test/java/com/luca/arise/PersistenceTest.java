package com.luca.arise;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.gson.JsonElement;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.quest.PlayerQuests;
import com.luca.arise.shadow.ShadowArmy;
import com.luca.arise.shadow.ShadowData;
import com.luca.arise.shadow.ShadowStance;
import com.luca.arise.workshop.Catalyst;
import com.luca.arise.workshop.LooseSoul;
import com.luca.arise.workshop.SoulTrait;
import com.luca.arise.progress.Rank;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * I codec che scrivono su disco.
 *
 * <p>Sono la cosa che, sbagliata, fa il danno peggiore di tutta la mod: non un errore, non un
 * crash — un giocatore che riapre il mondo e ha perso l'esercito. Un campo che non viene scritto
 * sparisce in silenzio al primo salvataggio, e nessuno se ne accorge finche' non e' troppo tardi
 * per rimediare.
 *
 * <p>Il giro completo — valore, JSON, valore — costa poche righe e li prende tutti.
 */
class PersistenceTest {

	@BeforeAll
	static void bootstrap() {
		GameBootstrap.ensure();
	}

	/** Scrive e rilegge, e pretende che torni identico. */
	private static <T> void roundTrip(Codec<T> codec, T value, String what) {
		JsonElement written = codec.encodeStart(JsonOps.INSTANCE, value)
				.getOrThrow(message -> new AssertionError(what + " non si scrive: " + message));

		T read = codec.parse(JsonOps.INSTANCE, written)
				.getOrThrow(message -> new AssertionError(what + " non si rilegge: " + message));

		assertEquals(value, read, what + ": il giro completo ha cambiato qualcosa");
	}

	@Test
	@DisplayName("un'anima sopravvive al salvataggio, tratti compresi")
	void looseSoulSurvives() {
		LooseSoul plain = LooseSoul.of(Identifier.withDefaultNamespace("blaze"), 20.0, 17.0);
		roundTrip(LooseSoul.CODEC, plain, "un'anima appena catturata");

		LooseSoul worked = plain.withLevel(9)
				.with(SoulTrait.ARDORE, 3)
				.with(SoulTrait.TENACIA, 3)
				.with(SoulTrait.FEROCIA, 3);

		roundTrip(LooseSoul.CODEC, worked, "un'anima fusa tre volte");
		assertEquals(3, worked.traits().size(), "i tre tratti devono esserci prima del giro");
	}

	@Test
	@DisplayName("un catalizzatore sopravvive al salvataggio")
	void catalystSurvives() {
		for (Rank grade : Rank.values()) {
			roundTrip(Catalyst.CODEC, new Catalyst(grade), "un catalizzatore " + grade);
		}
	}

	@Test
	@DisplayName("un'ombra sopravvive al salvataggio, nome e colore compresi")
	void shadowSurvives() {
		ShadowData plain = new ShadowData(UUID.randomUUID(),
				Identifier.withDefaultNamespace("wither_skeleton"), 1, 0L, 20.0, 11.0,
				Optional.empty(), ShadowData.DEFAULT_COLOR);

		roundTrip(ShadowData.CODEC, plain, "un'ombra appena estratta");

		ShadowData veteran = plain.withName("Igris").withColor(0xFF0000).withLevelUp().withLevelUp();
		roundTrip(ShadowData.CODEC, veteran, "un'ombra con nome e colore");

		assertEquals(Optional.of("Igris"), veteran.customName(), "il nome deve esserci");
		assertEquals(3, veteran.level(), "due promozioni da livello uno fanno tre");
	}

	@Test
	@DisplayName("un esercito intero sopravvive al salvataggio")
	void armySurvives() {
		ShadowArmy army = ShadowArmy.EMPTY;

		for (int i = 0; i < 12; i++) {
			army = army.with(new ShadowData(UUID.randomUUID(),
					Identifier.withDefaultNamespace("zombie"), i + 1, i * 7L,
					20.0 + i, 3.0 + i * 0.5, Optional.of("Ombra " + i), 0x4FC3F7));
		}

		roundTrip(ShadowArmy.CODEC, army, "un esercito di dodici");
		assertEquals(12, army.size(), "dodici ombre prima del giro");
	}

	@Test
	@DisplayName("la progressione e la catena degli incarichi sopravvivono al salvataggio")
	void playerStateSurvives() {
		roundTrip(PlayerProgress.CODEC, PlayerProgress.INITIAL, "un giocatore appena nato");
		roundTrip(PlayerQuests.CODEC, PlayerQuests.INITIAL, "la catena all'inizio");
		roundTrip(PlayerQuests.CODEC, new PlayerQuests(11, 5), "la catena a meta'");
		roundTrip(ShadowStance.CODEC, ShadowStance.AGGRESSIVE, "una postura");
	}

	@Test
	@DisplayName("i tratti e i ranghi si scrivono con un nome, non con un numero")
	void enumsAreWrittenByName() {
		// Un ordinale scritto su disco e' una bomba a orologeria: basta aggiungere una voce in
		// mezzo all'enum e tutti i salvataggi esistenti cambiano significato in silenzio.
		for (SoulTrait trait : SoulTrait.values()) {
			JsonElement written = SoulTrait.CODEC.encodeStart(JsonOps.INSTANCE, trait)
					.getOrThrow(message -> new AssertionError(message));

			assertEquals(trait.getSerializedName(), written.getAsString(),
					trait + " deve scriversi col suo nome");
		}

		for (Rank rank : Rank.values()) {
			JsonElement written = Rank.CODEC.encodeStart(JsonOps.INSTANCE, rank)
					.getOrThrow(message -> new AssertionError(message));

			assertEquals(rank.getSerializedName(), written.getAsString(),
					rank + " deve scriversi col suo nome");
		}
	}

	@Test
	@DisplayName("un'anima salvata prima che i tratti esistessero si rilegge lo stesso")
	void oldSoulsStillLoad() {
		// I campi opzionali col default non sono una comodita': sono la promessa che un mondo
		// aperto con la versione di ieri si apra anche con quella di oggi.
		String old = "{\"id\":[1,2,3,4],\"source_type\":\"minecraft:zombie\","
				+ "\"level\":3,\"health\":20.0,\"damage\":3.0}";

		LooseSoul read = LooseSoul.CODEC
				.parse(JsonOps.INSTANCE, com.google.gson.JsonParser.parseString(old))
				.getOrThrow(message -> new AssertionError("un'anima vecchia non si rilegge: " + message));

		assertEquals(3, read.level());
		assertEquals(List.of(), read.traits(), "senza tratti scritti, nessun tratto");
	}
}
