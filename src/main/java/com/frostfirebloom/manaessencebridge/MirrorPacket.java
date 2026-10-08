package com.frostfirebloom.manaessencebridge;

import com.frostfirebloom.manaessencebridge.client.MirrorScreen;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Состояние привязанного пула для окна Зеркала эссенции. Только сервер -> клиент:
 * open = true открывает окно, false - лишь обновляет уже открытое после покупки.
 * Цены приходят с сервера: конфиг у клиента в мультиплеере может быть другим.
 */
public class MirrorPacket {

    public static final class Offer {
        public final int tier;
        public final int buyCost;
        public final int sellPrice;

        public Offer(int tier, int buyCost, int sellPrice) {
            this.tier = tier;
            this.buyCost = buyCost;
            this.sellPrice = sellPrice;
        }
    }

    public final boolean open;
    public final int hand;
    public final String dim;
    public final BlockPos pos;
    public final int tier;
    public final int mana;
    public final int maxMana;
    /** Можно ли выкупать: приватный чужой пул продаёт только хозяину. */
    public final boolean mayBuy;
    /** Со сколькими пулами связан (сеть пулов). */
    public final int links;
    public final List<Offer> offers;

    public MirrorPacket(boolean open, int hand, String dim, BlockPos pos, int tier, int mana, int maxMana,
                        boolean mayBuy, int links, List<Offer> offers) {
        this.open = open;
        this.hand = hand;
        this.dim = dim;
        this.pos = pos;
        this.tier = tier;
        this.mana = mana;
        this.maxMana = maxMana;
        this.mayBuy = mayBuy;
        this.links = links;
        this.offers = offers;
    }

    public static void encode(MirrorPacket p, PacketBuffer buf) {
        buf.writeBoolean(p.open);
        buf.writeVarInt(p.hand);
        buf.writeString(p.dim);
        buf.writeBlockPos(p.pos);
        buf.writeVarInt(p.tier);
        buf.writeVarInt(p.mana);
        buf.writeVarInt(p.maxMana);
        buf.writeBoolean(p.mayBuy);
        buf.writeVarInt(p.links);
        buf.writeVarInt(p.offers.size());
        for (Offer o : p.offers) {
            buf.writeVarInt(o.tier);
            buf.writeVarInt(o.buyCost);
            buf.writeVarInt(o.sellPrice);
        }
    }

    public static MirrorPacket decode(PacketBuffer buf) {
        boolean open = buf.readBoolean();
        int hand = buf.readVarInt();
        String dim = buf.readString(32767);
        BlockPos pos = buf.readBlockPos();
        int tier = buf.readVarInt();
        int mana = buf.readVarInt();
        int maxMana = buf.readVarInt();
        boolean mayBuy = buf.readBoolean();
        int links = buf.readVarInt();
        int size = Math.min(buf.readVarInt(), 16);
        List<Offer> offers = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            offers.add(new Offer(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new MirrorPacket(open, hand, dim, pos, tier, mana, maxMana, mayBuy, links, offers);
    }

    public static void handle(MirrorPacket packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ctx.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> MirrorScreen.receive(packet)));
        ctx.setPacketHandled(true);
    }
}
