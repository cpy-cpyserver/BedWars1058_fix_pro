package com.andrei1058.bedwars.api.util;

import com.andrei1058.bedwars.api.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.server.VersionSupport;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.util.NumberConversions;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;

public class BlastProtectionUtil {

    /**
     * Offset applied to the point of view block in order to get the ray origins.
     */
    private static final double[] RAY_OFFSETS = {-0.73, 0, 0.73};

    /**
     * Amount of rays used for checking a block.
     */
    private static final int TOTAL_RAYS = RAY_OFFSETS.length * RAY_OFFSETS.length * RAY_OFFSETS.length;

    /**
     * Amount of unobstructed rays needed to consider a block exposed to an explosion.
     */
    private static final int MIN_UNOBSTRUCTED_RAYS = 6;

    private final VersionSupport versionSupport;
    private final BedWars api;

    public BlastProtectionUtil(VersionSupport versionSupport, BedWars api) {
        this.versionSupport = versionSupport;
        this.api = api;
    }


    /**
     * Check if block is protected by blast-proof glass or an unbreakable block from a point of view
     * <p>
     * if pov is null, block is checked by {@link VersionSupport#isGlass(Material)} and {@link IArena#isBlockPlaced(Block)} instead.
     * Otherwise, a ray tracing is performed to check whether there is any glass block or an unbreakable block.
     * <p>
     * If arena is null, then glass only be checked.
     *
     * @param arena Arena instance.
     * @param pov   the point of view.
     * @param block the block instance.
     * @param step  how frequent to check the ray (0.25 - 0.5 recommended).
     * @return whether there's unbreakable block between the pov and the block
     */
    public boolean isProtected(@NotNull IArena arena, Location pov, @NotNull Block block, double step) {

        if (arena.isProtected(block.getLocation()) || arena.isTeamBed(block.getLocation())) {
            return true;
        }

        boolean rayBlockedByGlass = api.getConfigs().getMainConfig().getBoolean(ConfigPath.GENERAL_TNT_RAY_BLOCKED_BY_GLASS);
        boolean allowMapBreak = arena.isAllowMapBreak();

        World world = block.getWorld();
        int targetX = block.getX();
        int targetY = block.getY();
        int targetZ = block.getZ();
        Vector povVector = pov.toVector().toBlockVector();

        int tracedRays = 0;
        int obstructedRays = 0;

        for (double xRayRadius : RAY_OFFSETS) {
            for (double yRayRadius : RAY_OFFSETS) {
                for (double zRayRadius : RAY_OFFSETS) {
                    tracedRays++;

                    boolean obstructed = isRayObstructed(world,
                            NumberConversions.floor(povVector.getX() + xRayRadius),
                            NumberConversions.floor(povVector.getY() + yRayRadius),
                            NumberConversions.floor(povVector.getZ() + zRayRadius),
                            targetX, targetY, targetZ, step, arena, rayBlockedByGlass, allowMapBreak);

                    if (obstructed) {
                        obstructedRays++;
                        // the amount of unobstructed rays can no longer reach the threshold
                        if (TOTAL_RAYS - obstructedRays < MIN_UNOBSTRUCTED_RAYS) {
                            return true;
                        }
                    } else if (tracedRays - obstructedRays >= MIN_UNOBSTRUCTED_RAYS) {
                        // enough rays are unobstructed, the block is exposed to the explosion
                        return false;
                    }
                }
            }
        }

        return TOTAL_RAYS - obstructedRays < MIN_UNOBSTRUCTED_RAYS;
    }

    /**
     * Trace the blocks between a ray origin and the target block.
     *
     * @param world            arena world.
     * @param originX          origin block x.
     * @param originY          origin block y.
     * @param originZ          origin block z.
     * @param targetX          target block x.
     * @param targetY          target block y.
     * @param targetZ          target block z.
     * @param step             how frequent to check the ray (0.25 - 0.5 recommended).
     * @param arena            arena instance.
     * @param rayBlockedByGlass whether glass blocks protect the target block.
     * @param allowMapBreak    whether the map can be destroyed.
     * @return true if the ray is obstructed by a protected block.
     */
    private boolean isRayObstructed(@NotNull World world, int originX, int originY, int originZ, int targetX, int targetY, int targetZ,
                                    double step, @NotNull IArena arena, boolean rayBlockedByGlass, boolean allowMapBreak) {
        double deltaX = targetX - originX;
        double deltaY = targetY - originY;
        double deltaZ = targetZ - originZ;
        double length = Math.sqrt(deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ);

        // the ray origin is in the target block, there is nothing to trace
        if (length == 0) return false;

        // same math as BlockRay, the ray is split into parts of the given step
        double multiple = 1 / (length / step);
        int parts = NumberConversions.ceil(length / step);
        double xOffset = originX + 0.5;
        double yOffset = originY + 0.5;
        double zOffset = originZ + 0.5;

        int lastX = originX;
        int lastY = originY;
        int lastZ = originZ;

        for (int consumed = 0; consumed <= parts; consumed++) {
            int currentX = NumberConversions.floor(xOffset + multiple * deltaX * consumed);
            int currentY = NumberConversions.floor(yOffset + multiple * deltaY * consumed);
            int currentZ = NumberConversions.floor(zOffset + multiple * deltaZ * consumed);

            // the block the ray is currently in
            if (isBlockObstructing(world, currentX, currentY, currentZ, arena, rayBlockedByGlass, allowMapBreak)) return true;
            // blocks the ray is crossing on its edges, previously fetched multiple times by BlockRay
            if (currentX != lastX && isBlockObstructing(world, lastX, currentY, currentZ, arena, rayBlockedByGlass, allowMapBreak)) return true;
            if (currentY != lastY && isBlockObstructing(world, currentX, lastY, currentZ, arena, rayBlockedByGlass, allowMapBreak)) return true;
            if (currentZ != lastZ && isBlockObstructing(world, currentX, currentY, lastZ, arena, rayBlockedByGlass, allowMapBreak)) return true;
            if (currentX != lastX && currentY != lastY && isBlockObstructing(world, lastX, lastY, currentZ, arena, rayBlockedByGlass, allowMapBreak)) return true;
            if (currentX != lastX && currentZ != lastZ && isBlockObstructing(world, lastX, currentY, lastZ, arena, rayBlockedByGlass, allowMapBreak)) return true;
            if (currentY != lastY && currentZ != lastZ && isBlockObstructing(world, currentX, lastY, lastZ, arena, rayBlockedByGlass, allowMapBreak)) return true;

            lastX = currentX;
            lastY = currentY;
            lastZ = currentZ;
        }

        return false;
    }

    /**
     * Check if the given block is not allowing the explosion to reach its target.
     *
     * @return true if the block is unbreakable or if it is a glass block when the glass protection is enabled.
     */
    private boolean isBlockObstructing(@NotNull World world, int x, int y, int z, @NotNull IArena arena, boolean rayBlockedByGlass, boolean allowMapBreak) {
        Block block = world.getBlockAt(x, y, z);
        Material type = block.getType();

        if (type == Material.AIR) return false;

        if (rayBlockedByGlass && versionSupport.isGlass(type)) {
            return true;
        }

        return !arena.isBlockPlaced(block) && !allowMapBreak;
    }
}
