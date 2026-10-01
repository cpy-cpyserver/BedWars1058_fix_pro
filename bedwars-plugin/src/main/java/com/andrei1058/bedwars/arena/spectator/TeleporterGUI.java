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

package com.andrei1058.bedwars.arena.spectator;

import com.andrei1058.bedwars.BedWars;
import com.andrei1058.bedwars.api.arena.IArena;
import com.andrei1058.bedwars.api.arena.team.ITeam;
import com.andrei1058.bedwars.api.language.Language;
import com.andrei1058.bedwars.api.language.Messages;
import com.andrei1058.bedwars.arena.Arena;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.andrei1058.bedwars.BedWars.nms;
import static com.andrei1058.bedwars.api.language.Language.getList;
import static com.andrei1058.bedwars.api.language.Language.getMsg;

@SuppressWarnings("WeakerAccess")
public class TeleporterGUI {

    //Don't remove "_" because it's used as a separator somewhere
    public static final String NBT_SPECTATOR_TELEPORTER_GUI_HEAD = "spectatorTeleporterGUIhead_";

    private static HashMap<Player, Inventory> refresh = new HashMap<>();

    /**
     * Refresh the Teleporter GUI for a player
     */
    public static void refreshInv(Player p, Inventory inv) {
        if (p.getOpenInventory() == null) return;
        IArena arena = Arena.getArenaByPlayer(p);
        if (arena == null) {
            p.closeInventory();
            return;
        }
        List<Player> players = arena.getPlayers();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = i < players.size() ? createHead(players.get(i), p) : null;
            // a head is missing for players which are not in a team, for example while they are leaving
            inv.setItem(i, null == item ? new ItemStack(Material.AIR) : item);
        }
    }

    /**
     * Opens the Teleporter GUI to a Player
     */
    public static void openGUI(Player p) {
        IArena arena = Arena.getArenaByPlayer(p);
        if (arena == null) return;

        int playerCount = arena.getPlayers().size();
        int size = (playerCount % 9) == 0 ? playerCount : ((int) Math.ceil(playerCount / 9.0)) * 9;

        if (size > 54) {
            size = 54;
        }

        Inventory inv = Bukkit.createInventory(p, size, getMsg(p, Messages.ARENA_SPECTATOR_TELEPORTER_GUI_NAME));
        refreshInv(p, inv);
        refresh.put(p, inv);
        p.openInventory(inv);
    }

    /**
     * Get a HashMap of players with Teleporter GUI opened
     */
    public static HashMap<Player, Inventory> getRefresh() {
        return refresh;
    }

    /**
     * Refresh the Teleporter GUI for all players with it opened
     */
    public static void refreshAllGUIs() {
        for (Map.Entry<Player, Inventory> e : new HashMap<>(getRefresh()).entrySet()) {
            refreshInv(e.getKey(), e.getValue());
        }
    }

    /**
     * Create a player head
     *
     * @param targetPlayer the player to display.
     * @param GUIholder    the player which has the GUI open.
     * @return the head, or null if the given player is not in a team, for example while they are leaving the
     * arena. In that case the slot has to be left empty.
     */
    private static @Nullable ItemStack createHead(Player targetPlayer, Player GUIholder) {
        IArena currentArena = Arena.getArenaByPlayer(targetPlayer);
        if (null == currentArena) return null;
        ITeam targetPlayerTeam = currentArena.getTeam(targetPlayer);
        if (null == targetPlayerTeam) {
            // the player is leaving the arena, they are still in the players list but not in their team
            // anymore, so there is no team to display and no reason to keep them in the teleporter
            return null;
        }

        ItemStack i = nms.getPlayerHead(targetPlayer, null);
        ItemMeta im = i.getItemMeta();
        assert im != null;

        im.setDisplayName(getMsg(GUIholder, Messages.ARENA_SPECTATOR_TELEPORTER_GUI_HEAD_NAME)
                .replace("{vPrefix}", BedWars.getChatSupport().getPrefix(targetPlayer))
                .replace("{vSuffix}", BedWars.getChatSupport().getSuffix(targetPlayer))
                .replace("{team}", targetPlayerTeam.getDisplayName(Language.getPlayerLanguage(GUIholder)))
                .replace("{teamColor}", String.valueOf(targetPlayerTeam.getColor().chat()))
                .replace("{player}", targetPlayer.getDisplayName())
                .replace("{playername}", targetPlayer.getName()));
        List<String> lore = new ArrayList<>();
        String health = String.valueOf((int)targetPlayer.getHealth() * 100 / targetPlayer.getHealthScale());
        for (String s : getList(GUIholder, Messages.ARENA_SPECTATOR_TELEPORTER_GUI_HEAD_LORE)) {
            lore.add(s.replace("{health}", health).replace("{food}", String.valueOf(targetPlayer.getFoodLevel())));
        }
        im.setLore(lore);
        i.setItemMeta(im);
        return nms.addCustomData(i, NBT_SPECTATOR_TELEPORTER_GUI_HEAD + targetPlayer.getName());
    }

    /**
     * Remove a player from the refresh list and close gui
     */
    public static void closeGUI(Player p) {
        if (getRefresh().containsKey(p)) {
            refresh.remove(p);
            p.closeInventory();
        }
    }
}
