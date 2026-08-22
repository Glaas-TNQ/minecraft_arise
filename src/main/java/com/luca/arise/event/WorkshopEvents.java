package com.luca.arise.event;

import com.luca.arise.config.AriseConfig;
import com.luca.arise.shadow.ShadowManager;
import com.luca.arise.workshop.LooseSoul;
import com.luca.arise.workshop.SoulItems;

import net.fabricmc.fabric.api.event.player.UseItemCallback;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;

/**
 * L'arruolamento: click destro su un'anima, e diventa un'ombra.
 *
 * <p>Un evento di Fabric e non un mixin, e nemmeno un item registrato con un suo
 * {@code use}: l'anima e' un componente su un oggetto vanilla, quindi non c'e' una classe di item
 * nostra su cui appendere il comportamento. {@link UseItemCallback} e' il gancio giusto e non
 * costa niente a chi non tiene un'anima in mano.
 *
 * <p>Serve tenere premuto lo shift. Un'anima e' anche un oggetto che si sposta di continuo fra
 * casse e macchinari, e un click distratto che la trasformi in un'ombra irreversibilmente sarebbe
 * un ottimo modo per far arrabbiare qualcuno.
 */
public final class WorkshopEvents {

	private WorkshopEvents() {
	}

	public static void register() {
		UseItemCallback.EVENT.register((player, level, hand) -> {
			ItemStack stack = player.getItemInHand(hand);

			if (level.isClientSide() || !player.isShiftKeyDown()
					|| !(player instanceof ServerPlayer server)) {
				return InteractionResult.PASS;
			}

			LooseSoul soul = SoulItems.soul(stack);
			if (soul == null || !AriseConfig.get().workshop().enabled()) {
				return InteractionResult.PASS;
			}

			int before = ShadowManager.army(server).size();
			server.sendSystemMessage(ShadowManager.enlist(server, soul));

			// L'anima si consuma solo se e' davvero entrata nell'esercito. Fidarsi del messaggio
			// per capirlo sarebbe fragile: qui si guarda il fatto, cioe' se l'esercito e' cresciuto.
			if (ShadowManager.army(server).size() > before) {
				stack.shrink(1);
				return InteractionResult.SUCCESS;
			}

			return InteractionResult.CONSUME;
		});
	}
}
