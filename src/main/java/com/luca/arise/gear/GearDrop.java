package com.luca.arise.gear;

import com.luca.arise.fx.AriseFx;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * Un pezzo che cade nel mondo.
 *
 * <p>E' il guadagno piu' concreto dell'aver trasformato l'equipaggiamento in oggetti veri. Prima
 * il bottino era una riga di chat — "hai ricevuto Anello del Titano" — cioe' il modo meno tattile
 * possibile di consegnare il premio di venti minuti di Gate. Adesso il pezzo salta fuori dal
 * nemico che l'ha lasciato, resta per terra con il suo anello di polvere colorata, e va raccolto.
 *
 * <p>Il pezzo raccolto finisce nell'inventario e da li' lo spazio dimensionale se lo prende da
 * solo entro un quarto di secondo (vedi {@link GearManager#sweep}): la raccolta e' un gesto, non
 * una faccenda da sistemare dopo.
 */
public final class GearDrop {

	private GearDrop() {
	}

	/** Lascia cadere un pezzo in un punto preciso. */
	public static ItemEntity drop(ServerLevel level, Vec3 position, GearPiece piece) {
		ItemStack stack = GearItems.stack(piece);
		ItemEntity entity = new ItemEntity(level, position.x, position.y + 0.5, position.z, stack);

		// Un piccolo balzo verso l'alto: senza, l'oggetto compare dentro il terreno e sembra gia'
		// vecchio invece che appena caduto.
		entity.setDeltaMovement(level.getRandom().nextGaussian() * 0.05,
				0.2 + level.getRandom().nextDouble() * 0.05,
				level.getRandom().nextGaussian() * 0.05);

		// Niente attesa prima di poterlo raccogliere: chi ha appena ucciso il boss lo raccoglie
		// camminandoci sopra, non aspettando due secondi in piedi.
		entity.setNoPickUpDelay();

		level.addFreshEntity(entity);
		AriseFx.gearDropped(level, position, piece.rank());

		return entity;
	}
}
