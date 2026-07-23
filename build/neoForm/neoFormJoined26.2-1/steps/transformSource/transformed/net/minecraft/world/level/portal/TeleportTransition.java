package net.minecraft.world.level.portal;

import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ClientboundLevelEventPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.phys.Vec3;

public record TeleportTransition(
    ServerLevel newLevel,
    Vec3 position,
    Vec3 deltaMovement,
    float yRot,
    float xRot,
    boolean missingRespawnBlock,
    boolean asPassenger,
    Set<Relative> relatives,
    TeleportTransition.PostTeleportTransition postTeleportTransition
) {
    public static final TeleportTransition.PostTeleportTransition DO_NOTHING = entity -> {};
    public static final TeleportTransition.PostTeleportTransition PLAY_PORTAL_SOUND = TeleportTransition::playPortalSound;
    public static final TeleportTransition.PostTeleportTransition PLACE_PORTAL_TICKET = TeleportTransition::placePortalTicket;

    public TeleportTransition(
        ServerLevel newLevel, Vec3 pos, Vec3 speed, float yRot, float xRot, TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        this(newLevel, pos, speed, yRot, xRot, Set.of(), postTeleportTransition);
    }

    public TeleportTransition(
        ServerLevel newLevel,
        Vec3 pos,
        Vec3 speed,
        float yRot,
        float xRot,
        Set<Relative> relatives,
        TeleportTransition.PostTeleportTransition postTeleportTransition
    ) {
        this(newLevel, pos, speed, yRot, xRot, false, false, relatives, postTeleportTransition);
    }

    private static void playPortalSound(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            player.connection.send(new ClientboundLevelEventPacket(1032, BlockPos.ZERO, 0, false));
        }
    }

    private static void placePortalTicket(Entity entity) {
        entity.placePortalTicket(BlockPos.containing(entity.position()));
    }

    public static TeleportTransition createDefault(ServerPlayer player, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        ServerLevel newLevel = player.level().getServer().findRespawnDimension();
        LevelData.RespawnData respawnData = newLevel.getRespawnData();
        return new TeleportTransition(
            newLevel,
            findAdjustedSharedSpawnPos(newLevel, player),
            Vec3.ZERO,
            respawnData.yaw(),
            respawnData.pitch(),
            false,
            false,
            Set.of(),
            postTeleportTransition
        );
    }

    public static TeleportTransition missingRespawnBlock(ServerPlayer player, TeleportTransition.PostTeleportTransition postTeleportTransition) {
        ServerLevel newLevel = player.level().getServer().findRespawnDimension();
        LevelData.RespawnData respawnData = newLevel.getRespawnData();
        return new TeleportTransition(
            newLevel,
            findAdjustedSharedSpawnPos(newLevel, player),
            Vec3.ZERO,
            respawnData.yaw(),
            respawnData.pitch(),
            true,
            false,
            Set.of(),
            postTeleportTransition
        );
    }

    private static Vec3 findAdjustedSharedSpawnPos(ServerLevel newLevel, Entity entity) {
        return Vec3.atBottomCenterOf(entity.adjustSpawnLocation(newLevel, newLevel.getRespawnData().pos()));
    }

    public TeleportTransition withRotation(float yRot, float xRot) {
        return new TeleportTransition(
            this.newLevel(),
            this.position(),
            this.deltaMovement(),
            yRot,
            xRot,
            this.missingRespawnBlock(),
            this.asPassenger(),
            this.relatives(),
            this.postTeleportTransition()
        );
    }

    public TeleportTransition withPosition(Vec3 position) {
        return new TeleportTransition(
            this.newLevel(),
            position,
            this.deltaMovement(),
            this.yRot(),
            this.xRot(),
            this.missingRespawnBlock(),
            this.asPassenger(),
            this.relatives(),
            this.postTeleportTransition()
        );
    }

    public TeleportTransition transitionAsPassenger() {
        return new TeleportTransition(
            this.newLevel(),
            this.position(),
            this.deltaMovement(),
            this.yRot(),
            this.xRot(),
            this.missingRespawnBlock(),
            true,
            this.relatives(),
            this.postTeleportTransition()
        );
    }

    @FunctionalInterface
    public interface PostTeleportTransition {
        void onTransition(final Entity entity);

        default TeleportTransition.PostTeleportTransition then(TeleportTransition.PostTeleportTransition postTeleportTransition) {
            return entity -> {
                this.onTransition(entity);
                postTeleportTransition.onTransition(entity);
            };
        }
    }
}
