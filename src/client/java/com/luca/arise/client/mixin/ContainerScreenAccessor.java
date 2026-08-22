package com.luca.arise.client.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Legge dove sta il pannello di una schermata da contenitore.
 *
 * <p>Motivazione (CLAUDE.md §8): il bottone che apre l'equipaggiamento va agganciato al bordo del
 * pannello dell'inventario, e quel bordo si <em>sposta</em> quando il giocatore apre il libro
 * delle ricette. {@code leftPos} e {@code topPos} sono protetti e non c'e' nessun accessore
 * pubblico ne' nessun evento di Fabric che li esponga: calcolarli come {@code (larghezza - 176) / 2}
 * darebbe la risposta giusta solo col libro chiuso, e il bottone finirebbe sotto le ricette.
 *
 * <p>E' un accessore e nient'altro: non intercetta niente, non cambia niente, non ha un corpo.
 */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {

	@Accessor("leftPos")
	int arise$leftPos();

	@Accessor("topPos")
	int arise$topPos();
}
