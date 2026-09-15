package keystrokesmod.script.packet.serverbound;

import keystrokesmod.script.packet.clientbound.*;
import net.minecraft.network.Packet;
import net.minecraft.network.play.client.*;
import net.minecraft.network.play.server.*;

/** Packet conversion boundary used by event callbacks and client.sendPacket. */
public final class PacketHandler {
    private PacketHandler() { }
    public static CPacket convertServerBound(Packet value) {
        if (value == null) return null;
        if (value instanceof C01PacketChatMessage) return new C01((C01PacketChatMessage) value, (byte) 0);
        if (value instanceof C02PacketUseEntity) return new C02((C02PacketUseEntity) value);
        if (value instanceof C03PacketPlayer) return new C03((C03PacketPlayer) value, (byte)0,(byte)0,(byte)0,(byte)0,(byte)0,(byte)0);
        if (value instanceof C07PacketPlayerDigging) return new C07((C07PacketPlayerDigging) value);
        if (value instanceof C08PacketPlayerBlockPlacement) return new C08((C08PacketPlayerBlockPlacement) value);
        if (value instanceof C09PacketHeldItemChange) return new C09((C09PacketHeldItemChange) value, true);
        if (value instanceof C0APacketAnimation) return new C0A((C0APacketAnimation) value);
        if (value instanceof C0BPacketEntityAction) return new C0B((C0BPacketEntityAction) value);
        if (value instanceof C0DPacketCloseWindow) return new C0D((C0DPacketCloseWindow) value);
        if (value instanceof C0EPacketClickWindow) return new C0E((C0EPacketClickWindow) value);
        if (value instanceof C0FPacketConfirmTransaction) return new C0F((C0FPacketConfirmTransaction) value);
        if (value instanceof C10PacketCreativeInventoryAction) return new C10((C10PacketCreativeInventoryAction) value);
        if (value instanceof C13PacketPlayerAbilities) return new C13((C13PacketPlayerAbilities) value);
        if (value instanceof C16PacketClientStatus) return new C16((C16PacketClientStatus) value);
        return new CPacket(value);
    }
    public static SPacket convertClientBound(Packet value) {
        if (value == null) return null;
        if (value instanceof S02PacketChat) return new S02((S02PacketChat) value);
        if (value instanceof S04PacketEntityEquipment) return new S04((S04PacketEntityEquipment) value);
        if (value instanceof S06PacketUpdateHealth) return new S06((S06PacketUpdateHealth) value);
        if (value instanceof S08PacketPlayerPosLook) return new S08((S08PacketPlayerPosLook) value);
        if (value instanceof S0BPacketAnimation) return new S0B((S0BPacketAnimation) value);
        if (value instanceof S12PacketEntityVelocity) return new S12((S12PacketEntityVelocity) value);
        if (value instanceof S14PacketEntity) return new S14((S14PacketEntity) value);
        if (value instanceof S23PacketBlockChange) return new S23((S23PacketBlockChange) value, (byte) 0);
        if (value instanceof S25PacketBlockBreakAnim) return new S25((S25PacketBlockBreakAnim) value);
        if (value instanceof S27PacketExplosion) return new S27((S27PacketExplosion) value);
        if (value instanceof S29PacketSoundEffect) return new S29((S29PacketSoundEffect) value);
        if (value instanceof S2APacketParticles) return new S2A((S2APacketParticles) value);
        if (value instanceof S2FPacketSetSlot) return new S2F((S2FPacketSetSlot) value);
        if (value instanceof S3APacketTabComplete) return new S3A((S3APacketTabComplete) value, (byte) 0);
        if (value instanceof S3EPacketTeams) return new S3E((S3EPacketTeams) value);
        if (value instanceof S45PacketTitle) return new S45((S45PacketTitle) value);
        if (value instanceof S48PacketResourcePackSend) return new S48((S48PacketResourcePackSend) value);
        return new SPacket(value);
    }
    public static Packet convertCPacket(CPacket value) { return value == null ? null : value.packet; }
}
