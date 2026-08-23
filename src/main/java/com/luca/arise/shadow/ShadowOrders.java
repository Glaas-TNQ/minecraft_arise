package com.luca.arise.shadow;

import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Gli ordini in corso: dove stare, e chi uccidere.
 *
 * <p>La postura ({@link ShadowStance}) e' una <em>politica</em>: vale sempre, si sceglie una volta
 * e la si dimentica. Un ordine e' l'opposto — vale adesso, per questo scontro, e nel manhwa e' il
 * gesto che definisce il personaggio: <em>"Igris, uccidilo."</em> Con la sola postura l'esercito
 * non si comandava, si configurava.
 *
 * <p>Due ordini, indipendenti fra loro perche' si combinano bene:
 * <ul>
 *   <li>{@code focus} — tutte le ombre in campo puntano quell'entita' e non mollano finche' non
 *       cade. E' il fuoco concentrato: quattro ombre che picchiano quattro mob diversi perdono uno
 *       scontro che vincerebbero in dieci secondi se ne picchiassero uno alla volta;
 *   <li>{@code hold} — le ombre restano attorno a quel punto invece di seguire. Serve a tenere un
 *       corridoio, a non farsi seguire da un esercito mentre si scava, e a piazzare i maghi
 *       lontano dalla mischia.
 * </ul>
 *
 * <p>Non persistente: sia un'entita' bersaglio sia un punto in una dimensione sono cose che dopo un
 * riavvio non vogliono piu' dire niente. Sincronizzato invece si', perche' l'HUD deve poter dire
 * che un ordine e' in corso — un esercito che smette di seguirti senza spiegazione sembra rotto.
 */
public record ShadowOrders(Optional<UUID> focus, Optional<BlockPos> hold) {

	public static final Codec<ShadowOrders> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			UUIDUtil.CODEC.optionalFieldOf("focus").forGetter(ShadowOrders::focus),
			BlockPos.CODEC.optionalFieldOf("hold").forGetter(ShadowOrders::hold)
	).apply(instance, ShadowOrders::new));

	public static final StreamCodec<ByteBuf, ShadowOrders> STREAM_CODEC = ByteBufCodecs.fromCodec(CODEC);

	public static final ShadowOrders NONE = new ShadowOrders(Optional.empty(), Optional.empty());

	public boolean isHolding() {
		return hold.isPresent();
	}

	public boolean hasFocus() {
		return focus.isPresent();
	}

	public boolean isIdle() {
		return focus.isEmpty() && hold.isEmpty();
	}

	public ShadowOrders withFocus(UUID target) {
		return new ShadowOrders(Optional.ofNullable(target), hold);
	}

	public ShadowOrders withoutFocus() {
		return new ShadowOrders(Optional.empty(), hold);
	}

	public ShadowOrders withHold(BlockPos position) {
		return new ShadowOrders(focus, Optional.ofNullable(position));
	}

	public ShadowOrders withoutHold() {
		return new ShadowOrders(focus, Optional.empty());
	}

	/** Una riga per l'HUD, o {@code null} se non c'e' niente da dire. */
	public Component label() {
		if (hasFocus() && isHolding()) {
			return Component.translatable("arise.order.focus_hold");
		}

		if (hasFocus()) {
			return Component.translatable("arise.order.focus");
		}

		if (isHolding()) {
			return Component.translatable("arise.order.hold");
		}

		return null;
	}
}
