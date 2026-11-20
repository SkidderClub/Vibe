package dev.vibe.movement;

import dev.vibe.combat.CombatRangeSupport;
import java.lang.reflect.Field;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraftforge.fml.relauncher.ReflectionHelper;

/** Adjust's Rewound motion offsets; local fall distance and collision stay vanilla. */
public final class AdjustCriticals {
    private static final Field REPORTED_Y = ReflectionHelper.findField(EntityPlayerSP.class, "lastReportedPosY", "field_175166_bJ");
    private boolean offsetApplied, reportedGround = true;
    private double serverFallDistance, lastServerY;
    public double offset;
    public boolean ground;

    public void reset() {
        offsetApplied = false;
        reportedGround = true;
        serverFallDistance = lastServerY = offset = 0;
    }

    public void prepare(final EntityPlayerSP player, final boolean jumping, final boolean breaking) {
        final double serverY;
        try {
            REPORTED_Y.setAccessible(true);
            serverY = REPORTED_Y.getDouble(player);
        } catch (IllegalAccessException failure) {
            throw new IllegalStateException("Rewound reported Y", failure);
        }

        if (reportedGround || player.isInWater() || player.isOnLadder() || CombatRangeSupport.isInWeb(player))
            serverFallDistance = 0;
        else if (lastServerY > serverY)
            serverFallDistance += lastServerY - serverY;

        lastServerY = serverY;
        offset = 0;
        ground = player.onGround;
        if (jumping || breaking) {
            offsetApplied = false;
            return;
        }

        final double predictedFall = serverY - player.getEntityBoundingBox().minY + serverFallDistance;
        ground = predictedFall >= 3.0D;
        final boolean applied = player.onGround && !offsetApplied && (serverFallDistance <= 0 || ground);
        if (applied)
            offset = 0.0301D;

        offsetApplied = applied;
    }

    public void afterWalking() {
        reportedGround = ground;
    }

    public boolean canShowCritical() {
        return !reportedGround && serverFallDistance > 0;
    }
}
