package com.luca.arise.progress;

import com.luca.arise.quest.QuestManager;
import com.luca.arise.quest.Unlock;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * La Pergamena del Rimpianto: restituisce tutti i punti statistica spesi.
 *
 * <p>Non c'era modo di tornare indietro. Un Cacciatore che a livello quaranta avesse messo trenta
 * punti in Agilita' — la statistica che sembra la piu' ovvia finche' non si scopre che il tetto e'
 * a cento perche' oltre il client sfonda le collisioni — non aveva davanti nessuna strada che non
 * fosse ricominciare il mondo. E' la lacuna che nelle mod RPG i giocatori segnalano piu' di
 * qualunque altra, e la ragione per cui smettono.
 *
 * <p><strong>Il livello, l'esperienza, i soul coin e l'esercito non si toccano.</strong> Torna
 * indietro solo cio' che era una decisione, ed e' esattamente la linea giusta: libero cio' che e'
 * tattico — la squadra si ricompone gratis, quante volte si vuole — costoso cio' che e' identita'.
 *
 * <p>Il costo sta nel comprarla, non nell'usarla. Trenta Monete d'Anima alla Cartoleria sono
 * tremila soul coin, cioe' cinque Gate di rango C: abbastanza perche' cambiare idea sia una
 * decisione, poco abbastanza perche' non sia una condanna. E il prezzo si legge dietro il bancone,
 * prima di pagarlo, invece di comparire in un messaggio a cose fatte.
 */
public final class RegretScrollItem extends Item {

	public RegretScrollItem(Properties properties) {
		super(properties);
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		Component locked = QuestManager.require(serverPlayer, Unlock.STATS);
		if (locked != null) {
			serverPlayer.sendSystemMessage(locked);
			return InteractionResult.CONSUME;
		}

		int returned = ProgressManager.respec(serverPlayer);

		// Niente da restituire, niente da consumare. Una pergamena bruciata a vuoto sarebbe il
		// modo piu' stupido di perdere trenta monete, e succederebbe per un clic distratto.
		if (returned <= 0) {
			serverPlayer.sendSystemMessage(
					Component.translatable("arise.msg.respec.nothing").withStyle(ChatFormatting.GRAY));
			return InteractionResult.CONSUME;
		}

		player.getItemInHand(hand).shrink(1);

		serverPlayer.sendSystemMessage(
				Component.translatable("arise.msg.respec.done", returned)
						.withStyle(ChatFormatting.AQUA));

		return InteractionResult.SUCCESS_SERVER;
	}

	/**
	 * Due righe, perche' un oggetto che azzera una scelta di cento livelli non puo' essere muto.
	 *
	 * <p>La seconda riga dice cosa <em>non</em> tocca, ed e' la piu' importante: chi legge
	 * "restituisce i punti" senza altro contesto ha tutto il diritto di temere che gli porti via
	 * anche il livello.
	 */
	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> out, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, out, flag);

		out.accept(Component.translatable("arise.item.regret_scroll.tooltip")
				.withStyle(ChatFormatting.AQUA));
		out.accept(Component.translatable("arise.item.regret_scroll.keeps")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
