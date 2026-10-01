/*
 * BedWars1058 - A bed wars mini-game.
 * Copyright (C) 2021 Andrei Dascălu
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Contact e-mail: andrew.dascalu@gmail.com
 */

package com.andrei1058.bedwars.listeners;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.GameState;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.NextEvent;
import com.andrei1058.bedwars.api.arena.generator.IGenerator;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.arena.team.TeamColor;
import com.andrei1058.bedwars.api.configuration.ConfigPath;
import com.andrei1058.bedwars.api.events.player.PlayerBedBreakEvent;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.api.region.Region;
import com.andrei1058.bedwars.api.server.ServerType;
import com.andrei1058.bedwars.api.util.BlastProtectionUtil;
import com.andrei1058.bedwars.arena.Arena;
import com.andrei1058.bedwars.configuration.Sounds;
import com.andrei1058.bedwars.support.paper.TeleportManager;
import com.andrei1058.bedwars.popuptower.TowerEast;
import com.andrei1058.bedwars.popuptower.TowerNorth;
import com.andrei1058.bedwars.popuptower.TowerSouth;
import com.andrei1058.bedwars.popuptower.TowerWest;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Level;

import static com.andrei1058.bedwars.BedWars.*;
import static com.andrei1058.bedwars.api.language.Language.getMsg;

public class BreakPlace implements Listener {

    private static final List<Player> buildSession = new ArrayList<>();
    // BlockPlaceEvent#getHand() and PlayerInventory#setItemInOffHand() are not part of the 1.8 api
    // this plugin is compiled against, but they are needed for taking the pop-up tower item from
    // the hand which was used for placing it. They are resolved once and are null on 1.8 servers
    // where there is no off hand at all.
    private static final Method BLOCK_PLACE_GET_HAND = getMethodOrNull(BlockPlaceEvent.class, "getHand");
    private static final Method SET_ITEM_IN_OFF_HAND = getMethodOrNull(PlayerInventory.class, "setItemInOffHand", ItemStack.class);
    private static final Object OFF_HAND = getEnumValueOrNull("org.bukkit.inventory.EquipmentSlot", "OFF_HAND");
    private final boolean allowFireBreak;
    private final BlastProtectionUtil blastProtection;

    public BreakPlace() {
        allowFireBreak = config.getBoolean(ConfigPath.GENERAL_CONFIGURATION_ALLOW_FIRE_EXTINGUISH);
        blastProtection = new BlastProtectionUtil(nms, BedWars.getAPI());
    }

    @EventHandler
    public void onIceMelt(BlockFadeEvent e) {
        if (BedWars.getServerType() == ServerType.MULTIARENA) {
            if (Objects.requireNonNull(e.getBlock().getLocation().getWorld()).getName().equalsIgnoreCase(BedWars.getLobbyWorld())) {
                e.setCancelled(true);
                return;
            }
        }
        if (e.getBlock().getType() == Material.ICE) {
            if (Arena.getArenaByIdentifier(e.getBlock().getWorld().getName()) != null) e.setCancelled(true);
        }
    }

    @EventHandler
    public void onCactus(BlockPhysicsEvent e) {
        if (e.getBlock().getType() == Material.CACTUS) {
            if (Arena.getArenaByIdentifier(e.getBlock().getWorld().getName()) != null) e.setCancelled(true);
        }
    }


    @EventHandler(ignoreCancelled = true)
    public void onBurn(@NotNull BlockBurnEvent event) {
        IArena arena = Arena.getArenaByIdentifier(event.getBlock().getWorld().getName());
        if (arena == null) return;
        if (!arena.isAllowMapBreak()) {
            event.setCancelled(true);
            return;
        }
        if (arena.isTeamBed(event.getBlock().getLocation())){
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent e) {
        if (e.isCancelled()) return;

        //Prevent player from placing during the removal from the arena
        IArena arena = Arena.getArenaByIdentifier(e.getBlock().getWorld().getName());
        if (arena != null) {
            if (arena.getStatus() != GameState.playing) {
                e.setCancelled(true);
                return;
            }
            if (e.getItemInHand().getType().equals(nms.materialFireball()) && e.getBlockPlaced().getType().equals(Material.FIRE)) {
                e.setCancelled(true);
            }
        }
        Player p = e.getPlayer();
        IArena a = Arena.getArenaByPlayer(p);
        if (a != null) {
            if (a.isSpectator(p)) {
                e.setCancelled(true);
                return;
            }
            if (a.getRespawnSessions().containsKey(p)) {
                e.setCancelled(true);
                return;
            }
            if (a.getStatus() != GameState.playing) {
                e.setCancelled(true);
                return;
            }
            if (e.getBlockPlaced().getLocation().getBlockY() >= a.getConfig().getInt(ConfigPath.ARENA_CONFIGURATION_MAX_BUILD_Y)) {
                e.setCancelled(true);
                return;
            }

            for (Region r : a.getRegionsList()) {
                if (r.isInRegion(e.getBlock().getLocation()) && r.isProtected()) {
                    e.setCancelled(true);
                    p.sendMessage(getMsg(p, Messages.INTERACT_CANNOT_PLACE_BLOCK));
                    return;
                }
            }

            // prevent modifying wood if protected
            // issue #531
            if (e.getBlockPlaced().getType().toString().contains("STRIPPED_") && e.getBlock().getType().toString().contains("_WOOD")) {
                if (null != arena && !arena.isAllowMapBreak()) {
                    e.setCancelled(true);
                    return;
                }
            }

            a.addPlacedBlock(e.getBlock());
            if (e.getBlock().getType() == Material.TNT) {
                if (config.getBoolean(ConfigPath.GENERAL_TNT_AUTO_IGNITE)) {
                    e.getBlockPlaced().setType(Material.AIR);
                    TNTPrimed tnt = Objects.requireNonNull(e.getBlock().getLocation().getWorld()).spawn(e.getBlock().getLocation().add(0.5, 0, 0.5), TNTPrimed.class);
                    tnt.setFuseTicks(config.getInt(ConfigPath.GENERAL_TNT_FUSE_TICKS));
                    nms.setSource(tnt, p);
                    return;
                }
            } else if (BedWars.shop.getBoolean(ConfigPath.SHOP_SPECIAL_TOWER_ENABLE)) {
                if (e.getBlock().getType() == Material.valueOf(shop.getString(ConfigPath.SHOP_SPECIAL_TOWER_MATERIAL))) {

                    e.setCancelled(true);
                    Location loc = e.getBlock().getLocation();
                    IArena a1 = Arena.getArenaByPlayer(p);
                    TeamColor col = a1.getTeam(p).getColor();
                    // consume the tower item from the hand which was used for placing it
                    consumeTowerItem(e);
                    double rotation = (p.getLocation().getYaw() - 90.0F) % 360.0F;
                    if (rotation < 0.0D) {
                        rotation += 360.0D;
                    }
                    if (45.0D <= rotation && rotation < 135.0D) {
                        new TowerSouth(loc, e.getBlockPlaced(), col, p);
                    } else if (225.0D <= rotation && rotation < 315.0D) {
                        new TowerNorth(loc, e.getBlockPlaced(), col, p);
                    } else if (135.0D <= rotation && rotation < 225.0D) {
                        new TowerWest(loc, e.getBlockPlaced(), col, p);
                    } else if (0.0D <= rotation && rotation < 45.0D) {
                        new TowerEast(loc, e.getBlockPlaced(), col, p);
                    } else if (315.0D <= rotation && rotation < 360.0D) {
                        new TowerEast(loc, e.getBlockPlaced(), col, p);
                    }
                }
            }
            return;
        }
        if (BedWars.getServerType() == ServerType.MULTIARENA) {
            if (Objects.requireNonNull(e.getBlock().getLocation().getWorld()).getName().equalsIgnoreCase(BedWars.getLobbyWorld())) {
                if (!isBuildSession(p)) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (BedWars.getServerType() == ServerType.MULTIARENA
                && player.getWorld().getName().equalsIgnoreCase(BedWars.getLobbyWorld())) {
            if (isBuildSession(player)) return;
            if (event.getClickedBlock() != null) {
                // do not allow players to toggle trapdoors and fence gates in the lobby
                if (event.getAction() == Action.RIGHT_CLICK_BLOCK && isTrapdoorOrFenceGate(event.getClickedBlock().getType())) {
                    event.setCancelled(true);
                    return;
                }
                if (event.getClickedBlock().getRelative(BlockFace.UP).getType() == Material.FIRE) {
                    event.setCancelled(true);
                    //return;
                }
            }
        }
    }

    /**
     * Check if the given material is a trapdoor or a fence gate.
     * <p>
     * Names are compared as text because they changed between 1.8 and 1.13 (TRAP_DOOR/TRAPDOOR etc).
     *
     * @param type block material.
     * @return true if the block can be opened/closed by hand.
     */
    private static boolean isTrapdoorOrFenceGate(@NotNull Material type) {
        String name = type.toString();
        return name.contains("TRAPDOOR") || name.contains("TRAP_DOOR") || name.contains("FENCE_GATE");
    }

    /**
     * Protect the map from item uses which replace a block without firing a place or a break event.
     * <p>
     * The shop sells fishing rods and buckets, so players can fish a water bottle and use it on a dirt block
     * to turn it into mud. The same goes for an axe on a log (it becomes a stripped log), a shovel on a grass
     * block (it becomes a dirt path) or a hoe on dirt (it becomes farmland). Those blocks are not tracked by
     * {@link IArena#isBlockPlaced(Block)}, so the map could be damaged even if the arena map break is disabled.
     *
     * @param event player interaction event.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMapModifyInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        Player player = event.getPlayer();
        if (isBuildSession(player)) return;
        IArena arena = Arena.getArenaByPlayer(player);
        if (arena == null || arena.isAllowMapBreak()) return;
        // blocks placed by players are not part of the map, they can be modified
        if (arena.isBlockPlaced(block)) return;
        if (!isMapModifyingUse(event.getItem(), block.getType())) return;
        event.setCancelled(true);
        player.sendMessage(getMsg(player, Messages.INTERACT_CANNOT_BREAK_BLOCK));
    }

    /**
     * Protect the map from block conversions which are not caused by a place or a break event.
     * <p>
     * Paper calls {@link EntityChangeBlockEvent} for the water bottle to mud conversion with the player as the
     * entity, so refusing the event keeps the map block untouched and does not consume the water bottle.
     *
     * @param event block change event.
     */
    @EventHandler(ignoreCancelled = true)
    public void onMapConversion(@NotNull EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        Player player = (Player) event.getEntity();
        if (isBuildSession(player)) return;
        IArena arena = Arena.getArenaByPlayer(player);
        if (arena == null || arena.isAllowMapBreak()) return;
        if (arena.isBlockPlaced(event.getBlock())) return;
        event.setCancelled(true);
    }

    /**
     * Check if the given item use would replace the given block with another one.
     * <p>
     * Material names are compared as text because they changed between server versions and most of them
     * do not exist on the 1.8 api this plugin is compiled against.
     *
     * @param item    item used by the player, it may be null.
     * @param clicked type of the clicked block.
     * @return true if the interaction modifies the clicked block.
     */
    private static boolean isMapModifyingUse(ItemStack item, @NotNull Material clicked) {
        if (item == null || item.getType() == Material.AIR) return false;
        String held = item.getType().toString();
        String block = clicked.toString();
        boolean wood = block.endsWith("_LOG") || block.endsWith("_WOOD") || block.endsWith("_STEM")
                || block.endsWith("_HYPHAE") || block.equals("BAMBOO_BLOCK");
        // axes strip wood, scrape copper and remove the wax from it
        if (held.endsWith("_AXE")) return wood || block.contains("COPPER");
        // shovels turn grass and dirt into a dirt path and remove snow layers
        if (held.endsWith("_SHOVEL") || held.endsWith("_SPADE")) {
            return isDirtLike(block) || block.equals("DIRT_PATH") || block.equals("GRASS_PATH") || block.equals("SNOW");
        }
        // hoes turn dirt into farmland
        if (held.endsWith("_HOE")) return isDirtLike(block);
        // water bottles turn dirt, coarse dirt and rooted dirt into mud
        if (held.equals("POTION")) return isDirtLike(block);
        return false;
    }

    /**
     * Check if the given material name is a block which can be turned into mud, a dirt path or farmland.
     *
     * @param name material name.
     * @return true if the block is made of dirt.
     */
    private static boolean isDirtLike(@NotNull String name) {
        switch (name) {
            case "DIRT":
            case "COARSE_DIRT":
            case "ROOTED_DIRT":
            case "PODZOL":
            case "MYCELIUM":
            case "MUD":
            case "SOIL": // farmland before 1.13
            case "FARMLAND":
            case "GRASS": // grass block before 1.13, short grass after
            case "GRASS_BLOCK":
                return true;
            default:
                return false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreakMonitor(BlockBreakEvent event) {
        IArena a = Arena.getArenaByPlayer(event.getPlayer());
        if (a != null) {
            a.removePlacedBlock(event.getBlock());
        }
    }

    @EventHandler
    public void onBlockDrop(ItemSpawnEvent event) {
        //WHEAT_SEEDS AND BEDs
        IArena arena = Arena.getArenaByIdentifier(event.getEntity().getWorld().getName());
        if (arena == null) return;
        Material material = event.getEntity().getItemStack().getType();
        if (nms.isBed(material) || material.toString().equalsIgnoreCase("SEEDS") || material.toString().equalsIgnoreCase("WHEAT_SEEDS")) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent e) {
        if (e.isCancelled()) return;
        Player p = e.getPlayer();
        if (BedWars.getServerType() == ServerType.MULTIARENA) {
            if (Objects.requireNonNull(e.getBlock().getLocation().getWorld()).getName().equalsIgnoreCase(BedWars.getLobbyWorld())) {
                if (!isBuildSession(p)) {
                    e.setCancelled(true);
                    return;
                }
            }
        }
        IArena a = Arena.getArenaByPlayer(p);
        if (a != null) {
            if (!a.isPlayer(p)) {
                e.setCancelled(true);
                return;
            }
            if (a.getRespawnSessions().containsKey(p)) {
                e.setCancelled(true);
                return;
            }
            if (a.getStatus() != GameState.playing) {
                e.setCancelled(true);
                return;
            }

            // allow breaking of grass
            // drops are removed in another event
            switch (e.getBlock().getType().toString()) {
                case "LONG_GRASS":
                case "TALL_GRASS":
                case "TALL_SEAGRASS":
                case "SEAGRASS":
                case "SUGAR_CANE":
                case "SUGAR_CANE_BLOCK":
                case "GRASS_PATH":
                case "DOUBLE_PLANT":
                    if (e.isCancelled()) {
                        e.setCancelled(false);
                    }
                    return;
                case "FIRE":
                    if (allowFireBreak) {
                        e.setCancelled(false);
                        return;
                    }
                    break;
            }

            if (nms.isBed(e.getBlock().getType())) {
                for (ITeam t : a.getTeams()) {
                    for (int x = e.getBlock().getX() - 2; x < e.getBlock().getX() + 2; x++) {
                        for (int y = e.getBlock().getY() - 2; y < e.getBlock().getY() + 2; y++) {
                            for (int z = e.getBlock().getZ() - 2; z < e.getBlock().getZ() + 2; z++) {
                                if (t.getBed().getBlockX() == x && t.getBed().getBlockY() == y && t.getBed().getBlockZ() == z) {
                                    if (!t.isBedDestroyed()) {
                                        if (t.isMember(p)) {
                                            p.sendMessage(getMsg(p, Messages.INTERACT_CANNOT_BREAK_OWN_BED));
                                            e.setCancelled(true);
                                            if (e.getPlayer().getLocation().getBlock().getType().toString().contains("BED")) {
                                                TeleportManager.teleport(e.getPlayer(), e.getPlayer().getLocation().add(0, 0.5, 0));
                                            }
                                        } else {
                                            e.setCancelled(false);
                                            t.setBedDestroyed(true);
                                            PlayerBedBreakEvent breakEvent;
                                            Bukkit.getPluginManager().callEvent(breakEvent = new PlayerBedBreakEvent(e.getPlayer(), a.getTeam(p), t, a,
                                                    player -> {
                                                        if (t.isMember(player)) {
                                                            return getMsg(player, Messages.INTERACT_BED_DESTROY_CHAT_ANNOUNCEMENT_TO_VICTIM);
                                                        } else {
                                                            return getMsg(player, Messages.INTERACT_BED_DESTROY_CHAT_ANNOUNCEMENT);
                                                        }
                                                    },
                                                    player -> {
                                                        if (t.isMember(player)) {
                                                            return getMsg(player, Messages.INTERACT_BED_DESTROY_TITLE_ANNOUNCEMENT);
                                                        }
                                                        return null;
                                                    },
                                                    player -> {
                                                        if (t.isMember(player)) {
                                                            return getMsg(player, Messages.INTERACT_BED_DESTROY_SUBTITLE_ANNOUNCEMENT);
                                                        }
                                                        return null;
                                                    }));
                                            for (Player on : a.getWorld().getPlayers()) {
                                                if (breakEvent.getMessage() != null) {
                                                    on.sendMessage(breakEvent.getMessage().apply(on)
                                                            .replace("{TeamColor}", t.getColor().chat().toString())
                                                            .replace("{TeamName}", t.getDisplayName(Language.getPlayerLanguage(on)))
                                                            .replace("{PlayerColor}", a.getTeam(p).getColor().chat().toString())
                                                            .replace("{PlayerName}", p.getDisplayName())
                                                            .replace("{PlayerNameUnformatted}", p.getName()));
                                                }
                                                if (breakEvent.getTitle() != null && breakEvent.getSubTitle() != null) {
                                                    nms.sendTitle(on, breakEvent.getTitle().apply(on), breakEvent.getSubTitle().apply(on), 0, 40, 10);
                                                }
                                                if (t.isMember(on))
                                                    Sounds.playSound(ConfigPath.SOUNDS_BED_DESTROY_OWN, on);
                                                else Sounds.playSound(ConfigPath.SOUNDS_BED_DESTROY, on);
                                            }
                                        }
                                        return;
                                    }
                                }
                            }
                        }
                    }
                }
            }

            for (Region r : a.getRegionsList()) {
                if (r.isInRegion(e.getBlock().getLocation()) && r.isProtected()) {
                    e.setCancelled(true);
                    p.sendMessage(getMsg(p, Messages.INTERACT_CANNOT_BREAK_BLOCK));
                    return;
                }
            }

            if (!a.isAllowMapBreak()) {
                if (!a.isBlockPlaced(e.getBlock())) {
                    p.sendMessage(getMsg(p, Messages.INTERACT_CANNOT_BREAK_BLOCK));
                    e.setCancelled(true);
                }
            }
        }
    }

    /**
     * update game signs
     */
    @EventHandler
    public void onSignChange(SignChangeEvent e) {
        if (e == null) return;
        Player p = e.getPlayer();
        if (Objects.requireNonNull(e.getLine(0)).equalsIgnoreCase("[" + mainCmd + "]")) {
            File dir = new File(plugin.getDataFolder(), "/Arenas");
            boolean exists = false;
            if (dir.exists()) {
                for (File f : Objects.requireNonNull(dir.listFiles())) {
                    if (f.isFile()) {
                        if (f.getName().contains(".yml")) {
                            if (Objects.equals(e.getLine(1), f.getName().replace(".yml", ""))) {
                                exists = true;
                            }
                        }
                    }
                }
                List<String> s;
                if (signs.getYml().get("locations") == null) {
                    s = new ArrayList<>();
                } else {
                    s = new ArrayList<>(signs.getYml().getStringList("locations"));
                }
                if (exists) {
                    s.add(e.getLine(1) + "," + signs.stringLocationConfigFormat(e.getBlock().getLocation()));
                    signs.set("locations", s);
                }
                IArena a = Arena.getArenaByName(e.getLine(1));
                if (a != null) {
                    p.sendMessage("§a▪ §7Sign saved for arena: " + e.getLine(1));
                    a.addSign(e.getBlock().getLocation());
                    Sign b = (Sign) e.getBlock().getState();
                    int line = 0;
                    for (String string : BedWars.signs.getList("format")) {
                        e.setLine(line, string.replace("[on]", String.valueOf(a.getPlayers().size())).replace("[max]",
                                        String.valueOf(a.getMaxPlayers())).replace("[arena]", a.getDisplayName()).replace("[status]", a.getDisplayStatus(Language.getDefaultLanguage()))
                                .replace("[type]", String.valueOf(a.getMaxInTeam())));
                        line++;
                    }
                    b.update(true);
                }
            } else {
                p.sendMessage("§c▪ §7You didn't set any arena yet!");
            }
        }
    }

    @EventHandler
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (e.isCancelled()) return;
        if (BedWars.getServerType() == ServerType.MULTIARENA) {
            if (Objects.requireNonNull(e.getPlayer().getLocation().getWorld()).getName().equalsIgnoreCase(BedWars.getLobbyWorld())) {
                if (!isBuildSession(e.getPlayer())) {
                    e.setCancelled(true);
                }
            }
        }
        IArena a = Arena.getArenaByPlayer(e.getPlayer());
        if (a != null) {
            if (a.isSpectator(e.getPlayer()) || a.getStatus() != GameState.playing || a.getRespawnSessions().containsKey(e.getPlayer()))
                e.setCancelled(true);
        }
    }

    @EventHandler
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (e.isCancelled()) return;

        // Lobby protection in MULTIARENA
        if (BedWars.getServerType() == ServerType.MULTIARENA) {
            if (Objects.requireNonNull(e.getPlayer().getLocation().getWorld()).getName().equalsIgnoreCase(BedWars.getLobbyWorld())) {
                if (!isBuildSession(e.getPlayer())) {
                    e.setCancelled(true);
                }
            }
        }

        // Prevent player from placing during the removal from the arena
        IArena arena = Arena.getArenaByIdentifier(e.getBlockClicked().getWorld().getName());
        if (arena != null && arena.getStatus() != GameState.playing) {
            e.setCancelled(true);
            return;
        }

        Player p = e.getPlayer();
        IArena a = Arena.getArenaByPlayer(p);
        if (a != null) {
            // Restriction checks (spectator, respawning, not playing)
            if (isPlayerRestrictedInArena(a, p)) {
                e.setCancelled(true);
                return;
            }

            // Water placement target location
            Block waterBlock = e.getBlockClicked().getRelative(e.getBlockFace());
            Location waterLocation = waterBlock.getLocation();

            // Build height limit
            if (isAboveMaxBuildY(a, waterLocation)) {
                e.setCancelled(true);
                return;
            }

            // Protected areas around spawns/shops/upgrades/generators
            if (isProtectedLocation(a, waterLocation)) {
                e.setCancelled(true);
                p.sendMessage(getMsg(p, Messages.INTERACT_CANNOT_PLACE_BLOCK));
                return;
            }

            // Remove one empty bucket from player's hand after a short delay
            Bukkit.getScheduler().runTaskLater(plugin, () -> nms.minusAmount(e.getPlayer(), e.getItemStack(), 1), 3L);
        }
    }

    @EventHandler
    public void onBlow(@NotNull EntityExplodeEvent e) {
        if (e.isCancelled()) return;

        IArena a = Arena.getArenaByIdentifier(e.getLocation().getWorld().getName());
        if (a != null) {
            if (a.getStatus() == GameState.playing) {
                e.blockList().removeIf((b) -> blastProtection.isProtected(a, e.getLocation(), b, 0.3));
                return;
            }
            e.blockList().clear();
        }
    }

    @EventHandler
    public void onBlockExplode(@NotNull BlockExplodeEvent e) {
        if (e.isCancelled()) return;
        if (e.blockList().isEmpty()) return;

        IArena a = Arena.getArenaByIdentifier(e.blockList().get(0).getWorld().getName());
        if (a != null) {
            if (a.getNextEvent() != NextEvent.GAME_END) {
                e.blockList().removeIf((b) -> blastProtection.isProtected(a, e.getBlock().getLocation(), b, 0.3));
            }
        }
    }

    @EventHandler
    public void onPaintingRemove(HangingBreakByEntityEvent e) {
        IArena a = Arena.getArenaByIdentifier(e.getEntity().getWorld().getName());
        if (a == null) {
            if (BedWars.getServerType() == ServerType.SHARED) return;
            if (!BedWars.getLobbyWorld().equals(e.getEntity().getWorld().getName())) return;
        }
        if (e.getEntity().getType() == EntityType.PAINTING || e.getEntity().getType() == EntityType.ITEM_FRAME) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockCanBuildEvent(BlockCanBuildEvent e) {
        if (e.isBuildable()) return;
        IArena a = Arena.getArenaByIdentifier(e.getBlock().getWorld().getName());
        if (a != null) {
            boolean bed = false;
            for (ITeam t : a.getTeams()) {
                for (int x = e.getBlock().getX() - 1; x < e.getBlock().getX() + 1; x++) {
                    for (int z = e.getBlock().getZ() - 1; z < e.getBlock().getZ() + 1; z++) {

                        //Check bed block
                        if (t.getBed().getBlockX() == x && t.getBed().getBlockY() == e.getBlock().getY() && t.getBed().getBlockZ() == z) {
                            e.setBuildable(false);
                            bed = true;
                            break;
                        }
                    }
                }
                //Check bed hologram
                if (t.getBed().getBlockX() == e.getBlock().getX() && t.getBed().getBlockY() + 1 == e.getBlock().getY() && t.getBed().getBlockZ() == e.getBlock().getZ()) {
                    if (!bed) {
                        e.setBuildable(true);
                        break;
                    }
                }
            }
            //if (bed) return;
            /*Object[] players = e.getBlock().getWorld().getNearbyEntities(e.getBlock().getLocation(), 1, 1, 1).stream().filter(ee -> ee.getType() == EntityType.PLAYER).toArray();
            for (Object o : players) {
                Player p = (Player) o;
                if (a.isSpectator(p)) {
                    if (e.getBlock().getType() == Material.AIR) e.setBuildable(true);
                    return;
                }
            }**/
        }
    }

    //prevent farm breaking farm stuff
    @EventHandler
    public void soilChangeEntity(EntityChangeBlockEvent e) {
        if (e.getTo() == Material.DIRT) {
            if (e.getBlock().getType().toString().equals("FARMLAND") || e.getBlock().getType().toString().equals("SOIL")) {
                if ((Arena.getArenaByIdentifier(e.getBlock().getWorld().getName()) != null) || (e.getBlock().getWorld().getName().equals(BedWars.getLobbyWorld())))
                    e.setCancelled(true);
            }
        }
    }

    private boolean isPlayerRestrictedInArena(@NotNull IArena a, @NotNull Player p) {
        if (a.isSpectator(p)) return true;
        if (a.getRespawnSessions().containsKey(p)) return true;
        return a.getStatus() != GameState.playing;
    }

    private boolean isAboveMaxBuildY(@NotNull IArena a, @NotNull Location location) {
        try {
            return location.getBlockY() >= a.getConfig().getInt(ConfigPath.ARENA_CONFIGURATION_MAX_BUILD_Y);
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean isProtectedLocation(@NotNull IArena a, @NotNull Location location) {
        try {
            for (ITeam t : a.getTeams()) {
                if (t.getSpawn().distance(location) <= a.getConfig().getInt(ConfigPath.ARENA_SPAWN_PROTECTION)) return true;
                if (t.getShop().distance(location) <= a.getConfig().getInt(ConfigPath.ARENA_SHOP_PROTECTION)) return true;
                if (t.getTeamUpgrades().distance(location) <= a.getConfig().getInt(ConfigPath.ARENA_UPGRADES_PROTECTION)) return true;
                for (IGenerator o : t.getGenerators()) {
                    if (o.getLocation().distance(location) <= a.getConfig().getInt(ConfigPath.ARENA_GENERATOR_PROTECTION)) return true;
                }
            }
            for (IGenerator o : a.getOreGenerators()) {
                if (o.getLocation().distance(location) <= a.getConfig().getInt(ConfigPath.ARENA_GENERATOR_PROTECTION)) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    /**
     * Take one pop-up tower item from the hand which was used for placing it.
     * <p>
     * The item was always taken from the main hand, no matter which hand placed the block.
     * Because of that a tower item kept in the off hand was never consumed, so players were
     * able to place an infinite amount of pop-up towers.
     *
     * @param event pop-up tower placement event.
     */
    private void consumeTowerItem(@NotNull BlockPlaceEvent event) {
        Player player = event.getPlayer();
        PlayerInventory inventory = player.getInventory();
        // the item used for placing the block, on 1.9+ servers it may be the item from the off hand
        ItemStack usedItem = event.getItemInHand();
        if (usedItem == null || usedItem.getType() == Material.AIR || usedItem.getAmount() < 1) return;

        ItemStack remaining = null;
        if (usedItem.getAmount() > 1) {
            remaining = usedItem.clone();
            remaining.setAmount(usedItem.getAmount() - 1);
        }

        if (!isOffHandPlacement(event)) {
            inventory.setItemInHand(remaining);
            return;
        }

        if (SET_ITEM_IN_OFF_HAND != null) {
            try {
                //noinspection JavaReflectionMemberAccess
                SET_ITEM_IN_OFF_HAND.invoke(inventory, remaining);
                return;
            } catch (ReflectiveOperationException exception) {
                BedWars.plugin.getLogger().log(Level.WARNING, "Could not consume the pop-up tower item from the off hand!", exception);
            }
        }
        // last resort, the used item stack is a mirror of the item from the player inventory
        usedItem.setAmount(usedItem.getAmount() - 1);
    }

    /**
     * Check if the block was placed with the off hand.
     *
     * @param event block place event.
     * @return true if the off hand was used for placing the block.
     */
    private static boolean isOffHandPlacement(@NotNull BlockPlaceEvent event) {
        if (BLOCK_PLACE_GET_HAND == null || OFF_HAND == null) return false;
        try {
            return OFF_HAND.equals(BLOCK_PLACE_GET_HAND.invoke(event));
        } catch (ReflectiveOperationException exception) {
            return false;
        }
    }

    private static Method getMethodOrNull(@NotNull Class<?> holder, @NotNull String name, Class<?>... parameters) {
        try {
            return holder.getMethod(name, parameters);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static Object getEnumValueOrNull(@NotNull String className, @NotNull String constant) {
        try {
            return Class.forName(className).getField(constant).get(null);
        } catch (ReflectiveOperationException exception) {
            return null;
        }
    }

    public static boolean isBuildSession(Player p) {
        return buildSession.contains(p);
    }

    public static void addBuildSession(Player p) {
        buildSession.add(p);
    }

    public static void removeBuildSession(Player p) {
        buildSession.remove(p);
    }
}
