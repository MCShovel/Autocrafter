package com.norcode.bukkit.autocrafter;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;

import net.h31ix.updater.Updater;
import net.h31ix.updater.Updater.UpdateResult;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Dropper;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.HashSet;

public class Autocrafter extends JavaPlugin implements Listener {
    private static EnumSet<BlockFace> sides = EnumSet.of(BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH);
    private static final int itemFrameEntityId = EntityType.ITEM_FRAME.getTypeId();
    private boolean usePermissions = true;
    private boolean worldWhitelist = true;
    private boolean recipeWhitelist = false;
    private static EnumSet<Material> filledBuckets = EnumSet.of(Material.MILK_BUCKET, Material.LAVA_BUCKET, Material.WATER_BUCKET);
    EnumSet<Material> recipeList = EnumSet.noneOf(Material.class);
    private String noPermissionMsg;
    private List<String> worldList = new ArrayList<String>();
    private Permission wildcardPerm = null;
    private Updater updater;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler(ignoreCancelled=true)
    public void onPlayerLogin(PlayerLoginEvent event) {
        final String playerName = event.getPlayer().getName();
        if (event.getPlayer().hasPermission("autocrafter.admin")) {
            getServer().getScheduler().runTaskLaterAsynchronously(this, new Runnable() {
                public void run() {
                    Player player = getServer().getPlayer(playerName);
                    if (player != null && player.isOnline()) {
                        String autoUpdate = getConfig().getString("auto-update").toLowerCase();
                        if (!autoUpdate.equals("false")) {
                            // notify only
                            UpdateResult result = updater.getResult();
                            switch (result) {
                            case SUCCESS:
                                player.sendMessage(ChatColor.GOLD + "[AutoCrafter] " + ChatColor.WHITE + "A new update has been downloaded and is will take effect when the server restarts.");
                                break;
                            case UPDATE_AVAILABLE:
                                player.sendMessage(ChatColor.GOLD + "[AutoCrafter] " + ChatColor.WHITE + "A new update is available at: http://dev.bukkit.org/server-mods/autocrafter/");
                            }
                        }
                    }
                }
            }, 20);
        }
    }
    public void loadConfig() {
        if (!getConfig().contains("auto-update")) {
            getConfig().set("auto-update",  "true");
            saveConfig();
        }
        String autoUpdate = getConfig().getString("auto-update").toLowerCase();
        if (autoUpdate.equals("true")) {
            updater = new Updater(this, "autocrafter", this.getFile(), Updater.UpdateType.DEFAULT, true);
        } else if (autoUpdate.equals("false")) {
            getLogger().info("Auto-updater is disabled. Not checking for updates.");
        } else {
            updater = new Updater(this, "autocrafter", this.getFile(), Updater.UpdateType.NO_DOWNLOAD, true);
        }
        this.usePermissions = getConfig().getBoolean("use-permissions", true);
        noPermissionMsg = getConfig().getString("messages.no-permission", null);
        // world list
        String listtype = getConfig().getString("world-selection", "whitelist").toLowerCase();
        if (listtype.equals("blacklist")) {
            this.worldWhitelist = false;
        } else {
            this.worldWhitelist = true;
        }
        this.worldList.clear();
        for (String wn: getConfig().getStringList("world-list")) {
            this.worldList.add(wn.toLowerCase());
        }
        // recipe list
        listtype = getConfig().getString("recipe-selection", "blacklist").toLowerCase();
        if (listtype.equals("whitelist")) {
            this.recipeWhitelist = true;
        } else {
            this.recipeWhitelist = false;
        }
        this.recipeList.clear();
        for (String r: getConfig().getStringList("recipe-list")) {
            this.recipeList.add(Material.valueOf(r));
        }
        wildcardPerm = getServer().getPluginManager().getPermission("autocrafter.create.*");
        if (wildcardPerm == null) {
            wildcardPerm = new Permission("autocrafter.create.*", PermissionDefault.FALSE);
            getServer().getPluginManager().addPermission(wildcardPerm);
        }
        Permission child = null;
        for (Material m: Material.values()) {
            if (recipeAllowed(new ItemStack(m))) {
                child = getServer().getPluginManager().getPermission("autocrafter.create." + m.name().toLowerCase());
                if (child == null) {
                    child = new Permission("autocrafter.create." + m.name().toLowerCase(), PermissionDefault.FALSE);
                    getServer().getPluginManager().addPermission(child);
                }
                child.addParent(wildcardPerm, true);
                child.recalculatePermissibles();
            }
        }
        wildcardPerm.recalculatePermissibles();
    }

    public void printInventoryContents(Inventory inv) {
        String s = "";
        for (int i=0;i<inv.getSize();i++) {
            ItemStack is = inv.getItem(i);
            if (is == null) {
                s += "null, ";
            } else {
                s += is + ", ";
            }
        }
        getLogger().info(s);
    }


    public boolean enabledInWorld(World w) {
        boolean enabled = ((worldWhitelist && worldList.contains(w.getName().toLowerCase())) || 
                (!worldWhitelist && !worldList.contains(w.getName().toLowerCase())));
        return enabled;
    }

    public boolean recipeAllowed(ItemStack stack) {
        boolean inList = recipeList.contains(stack.getType());
        return ((recipeWhitelist && inList) || (!recipeWhitelist && !inList));
    }

    @EventHandler(ignoreCancelled=true)
    public void onFrameInteract(PlayerInteractEntityEvent event) {
        if (!enabledInWorld(event.getPlayer().getWorld())) {
            return;
        }
        if (event.getRightClicked().getType().equals(EntityType.ITEM_FRAME)) {
            ItemFrame frame = (ItemFrame) event.getRightClicked();
            Block attached = frame.getLocation().getBlock().getRelative(frame.getAttachedFace());
            if (attached.getType().equals(Material.DROPPER)) {
                if (frame.getItem() == null || frame.getItem().getType().equals(Material.AIR)) {
                    ItemStack hand = event.getPlayer().getItemInHand();
                    if (!hasCraftPermission(event.getPlayer(), hand)) {
                        event.setCancelled(true);
                        if (noPermissionMsg != null) {
                            event.getPlayer().sendMessage(noPermissionMsg);
                        }
                    }
                }
            }
        }
    }

    private boolean hasCraftPermission(Player player, ItemStack hand) {
	if (!usePermissions) return true;
        String node ="autocrafter.create." + hand.getType().name().toLowerCase(); 
        boolean b = player.hasPermission(node);
        return b;
    }

    @EventHandler(ignoreCancelled=true)
    public void onDropperMove(InventoryMoveItemEvent event) {
        if (event.getSource().getHolder() instanceof Dropper) {
            final Dropper dropper = ((Dropper) event.getSource().getHolder());
            if (!enabledInWorld(dropper.getWorld())) return;
            ItemFrame frame = getAttachedFrame(dropper.getBlock());
            if (frame == null) {
        	return;
            }
            if (!recipeAllowed(getFrameItem(frame))) return;
            ItemStack result = null;
            Recipe recipe = null;
            if (frame != null && frame.getItem() != null && !frame.getItem().getType().equals(Material.AIR)) {
                if (event.getItem().equals(getFrameItem(frame))) {
                    return;
                }
                boolean crafted = false;
                ItemStack[] ingredients = null;
                for (Recipe r: getServer().getRecipesFor(getFrameItem(frame))) {
                    if (r instanceof ShapedRecipe || r instanceof ShapelessRecipe) {
                        ingredients = CraftAttempt.getIngredients(r).toArray(new ItemStack[0]);
                        Inventory clone = CraftAttempt.cloneInventory(this, dropper.getInventory());
                        //clone.addItem(event.getItem());
                        crafted = true;
                        for (ItemStack ing: ingredients) {
                            if (!CraftAttempt.removeItem(clone, ing.getType(), ing.getData().getData(), ing.getAmount())) {
                                crafted = false;
                                break;
                            }
                        }
                        if (crafted) {
                            recipe = r;
                            result = r.getResult().clone();
                            break;
                        }
                    }
                }
                if (!crafted) {
                    event.setCancelled(true);
                } else {
                    event.setItem(result);
                    final Recipe finalRecipe = recipe;
                    getServer().getScheduler().runTaskLater(this,  new Runnable() {
                        public void run () {
                            for (ItemStack ing: CraftAttempt.getIngredients(finalRecipe)) {
                                CraftAttempt.removeItem(dropper.getInventory(), ing.getType(), ing.getData().getData(), ing.getAmount());
                                if (filledBuckets.contains(ing.getType())) {
                                    dropper.getInventory().addItem(new ItemStack(Material.BUCKET));
                                }
                            }
                        }
                    }, 0);
                }
            }
        }
    }

    private ItemStack getFrameItem(ItemFrame frame) {
	ItemStack stack = frame.getItem();
	short max = stack.getType().getMaxDurability();
	if (max > 0 && stack.getDurability() != 0) {
	    stack = stack.clone();
	    stack.setDurability((short) 0);
	}
	return stack;
    }

    @EventHandler(ignoreCancelled=true)
    public void onDropperFire(BlockDispenseEvent event) {
        if (event.getBlock().getType().equals(Material.DROPPER)) {
            final Dropper dropper = ((Dropper) event.getBlock().getState());
            if (!enabledInWorld(dropper.getWorld())) return;
            ItemFrame frame = getAttachedFrame(event.getBlock());
            if (frame == null) return;
            if (!recipeAllowed(getFrameItem(frame))) return;
            ItemStack result = null;
            Recipe recipe = null;
            if (frame != null && frame.getItem() != null && !frame.getItem().getType().equals(Material.AIR)) {
                if (event.getItem().equals(getFrameItem(frame))) {
                    return;
                }
                boolean crafted = false;
                ItemStack[] ingredients = null;
                for (Recipe r: getServer().getRecipesFor(getFrameItem(frame))) {
                    if (r instanceof ShapedRecipe || r instanceof ShapelessRecipe) {
                        ingredients = CraftAttempt.getIngredients(r).toArray(new ItemStack[0]);
                        Inventory clone = CraftAttempt.cloneInventory(this, dropper.getInventory());
                        clone.addItem(event.getItem());
                        crafted = true;
                        for (ItemStack ing: ingredients) {
                            if (!CraftAttempt.removeItem(clone, ing.getType(), ing.getData().getData(), ing.getAmount())) {
                                crafted = false;
                                break;
                            }
                        }
                        if (crafted) {
                            recipe = r;
                            result = r.getResult().clone();
                            break;
                        }
                    }
                }
                if (!crafted) {
                    event.setCancelled(true);
                } else {
                    event.setItem(result);
                    final Recipe finalRecipe = recipe;
                    getServer().getScheduler().runTaskLater(this,  new Runnable() {
                        public void run () {
                            for (ItemStack ing: CraftAttempt.getIngredients(finalRecipe)) {
                                CraftAttempt.removeItem(dropper.getInventory(), ing.getType(), ing.getData().getData(), ing.getAmount());
                                if (filledBuckets.contains(ing.getType())) {
                                    dropper.getInventory().addItem(new ItemStack(Material.BUCKET));
                                }
                            }
                        }
                    }, 0);
                }
            }
        }
    }

    public ItemFrame getAttachedFrame(Block block) {
        Location dropperLoc = new Location(block.getWorld(), block.getX()+0.5, block.getY()+0.5, block.getZ()+0.5);
        Chunk thisChunk = block.getChunk();
        Chunk thatChunk = null;
        HashSet<Chunk> chunksToCheck = new HashSet<Chunk>();
        chunksToCheck.add(thisChunk);
        for (BlockFace side: sides) {
            thatChunk = block.getRelative(side).getChunk();
            if (!thisChunk.equals(thatChunk)) {
                chunksToCheck.add(thatChunk);
            }
        }
        for (Chunk c: chunksToCheck) {
            for (Entity e: c.getEntities()) {
                if (e.getType().getTypeId() == itemFrameEntityId  && e.getLocation().distanceSquared(dropperLoc) == 0.31640625) {
                    return ((ItemFrame) e);
                }
            }
        }
        return null;
    }
}
