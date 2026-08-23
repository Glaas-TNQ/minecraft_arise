package com.luca.arise.workshop;

import java.util.function.Consumer;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

/**
 * Un Progetto, e la spiegazione di cosa farsene.
 *
 * <p>Era un oggetto muto: lo si riceveva da un incarico, finiva nell'inventario, e non c'era una
 * riga in tutto il gioco che dicesse che <strong>e' un ingrediente</strong>. Un giocatore che lo
 * guardava vedeva un nome — "Progetto: Richiamo d'Anime" — e nessun modo di scoprire che quel
 * disegno va messo al centro di un banco da lavoro insieme ad altri otto blocchi. La catena degli
 * incarichi, intanto, gli chiedeva di far girare il macchinario.
 *
 * <p>Adesso il Progetto dice tre cose, in tre righe: cosa fa la macchina che descrive, con cosa si
 * costruisce, e che il disegno si consuma nel farlo. Il resto lo dice il libro delle ricette, che
 * si apre da solo quando l'incarico consegna il Progetto (vedi {@code QuestManager.unlockRecipe}).
 */
public class BlueprintItem extends Item {

	private final MachineKind kind;

	public BlueprintItem(Properties properties, MachineKind kind) {
		super(properties);
		this.kind = kind;
	}

	public MachineKind kind() {
		return kind;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
			Consumer<Component> out, TooltipFlag flag) {
		super.appendHoverText(stack, context, display, out, flag);

		out.accept(kind.hint().copy().withStyle(ChatFormatting.GRAY));
		out.accept(Component.translatable("arise.blueprint.recipe", kind.label())
				.withStyle(ChatFormatting.AQUA));
		out.accept(Component.translatable("arise.blueprint.consumed")
				.withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.ITALIC));
	}
}
