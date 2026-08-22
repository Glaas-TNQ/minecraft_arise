package com.luca.arise.client;

import java.util.ArrayList;
import java.util.List;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.gear.GearItems;
import com.luca.arise.gear.GearPiece;
import com.luca.arise.gear.GearSlot;
import com.luca.arise.gear.PlayerGear;
import com.luca.arise.progress.PlayerProgress;
import com.luca.arise.progress.Rank;
import com.luca.arise.registry.ModAttachments;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/**
 * Cosa il giocatore ha addosso, visto dal client.
 *
 * <p>Serve solo a <em>disegnare</em>: la schermata di stato mostra quanto arriva
 * dall'equipaggiamento, e quella delle gemme elenca i pezzi incastonabili. Chi decide resta il
 * server (CLAUDE.md §4) — qui non si valida niente, si legge.
 *
 * <p>Le due meta' arrivano da due posti diversi e nessuno dei due e' un pacchetto nostro: le
 * caselle di vanilla sono nell'inventario del giocatore, che il client conosce gia'; le nostre
 * sono nell'attachment, che si sincronizza da solo.
 */
public final class ClientGear {

	private static final List<EquipmentSlot> VANILLA_SLOTS = List.of(
			EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
			EquipmentSlot.FEET, EquipmentSlot.MAINHAND);

	private ClientGear() {
	}

	public static PlayerGear gear(LocalPlayer player) {
		PlayerGear gear = player == null ? null : player.getAttached(ModAttachments.GEAR);
		return gear == null ? PlayerGear.EMPTY : gear;
	}

	public static Rank rank(LocalPlayer player) {
		PlayerProgress progress = player == null ? null : player.getAttached(ModAttachments.PROGRESS);
		return AriseConfig.get().hunterRank(progress == null ? 1 : progress.level());
	}

	/** I pezzi indossati: caselle di vanilla e caselle del Cacciatore aperte dal rango. */
	public static List<GearPiece> worn(LocalPlayer player) {
		List<GearPiece> pieces = new ArrayList<>();

		if (player == null) {
			return pieces;
		}

		for (EquipmentSlot slot : VANILLA_SLOTS) {
			GearPiece piece = GearItems.piece(player.getItemBySlot(slot));

			if (piece != null && piece.slot().vanillaSlot() == slot) {
				pieces.add(piece);
			}
		}

		PlayerGear gear = gear(player);
		Rank rank = rank(player);

		for (GearSlot slot : GearSlot.HUNTER) {
			int first = slot.firstIndex();

			for (int i = 0; i < slot.capacity(rank); i++) {
				GearPiece piece = GearItems.piece(gear.hunter().get(first + i));

				if (piece != null && piece.slot() == slot) {
					pieces.add(piece);
				}
			}
		}

		return pieces;
	}

	/** Tutti i pezzi posseduti: indossati, nelle caselle chiuse e nello spazio dimensionale. */
	public static List<GearPiece> owned(LocalPlayer player) {
		List<GearPiece> pieces = new ArrayList<>(worn(player));

		if (player == null) {
			return pieces;
		}

		for (ItemStack stack : gear(player).dimensional()) {
			GearPiece piece = GearItems.piece(stack);

			if (piece != null) {
				pieces.add(piece);
			}
		}

		return pieces;
	}
}
