package com.norcode.bukkit.autocrafter;

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
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.java.JavaPlugin;

import com.norcode.bukkit.autocrafter.CraftAttempt;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;

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
	private static boolean debug = false;
	private static Autocrafter instance;

    @Override
    public void onEnable() {
    	instance = this;
    	
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadConfig();
        getServer().getPluginManager().registerEvents(this, this);
    }

    public void loadConfig() {
		debug = getConfig().getBoolean("debug", false);
		debug("Loading Autocrafter Config...");
        if (!getConfig().contains("auto-update")) {
            getConfig().set("auto-update",  "true");
            saveConfig();
        }
		debug(" ... configuring auto-update");
        this.usePermissions = getConfig().getBoolean("use-permissions", true);
        noPermissionMsg = getConfig().getString("messages.no-permission", null);

        // world list
		debug(" ... checking world list");
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
		debug(" ... initializing recipes");
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
		debug(" ... setting wildcard permissions");
        wildcardPerm = getServer().getPluginManager().getPermission("autocrafter.create.*");
        if (wildcardPerm == null) {
            wildcardPerm = new Permission("autocrafter.create.*", PermissionDefault.OP);
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
		debug(" ... recalculating permissibles.");
		wildcardPerm.recalculatePermissibles();
		debug(" ... Done.");
		

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

	private static boolean sourceIsInitiator(InventoryMoveItemEvent event) {
		InventoryHolder sourceHolder = event.getSource().getHolder();
		InventoryHolder initiatorHolder = event.getInitiator().getHolder();
		if (sourceHolder instanceof Dropper) {
			if (initiatorHolder instanceof Dropper) {
				Location sLoc = ((Dropper) sourceHolder).getLocation();
				Location iLoc = ((Dropper) sourceHolder).getLocation();
				return sLoc.equals(iLoc);
			}
		}
		return false;
	}

    @EventHandler(ignoreCancelled=true)
    public void onDropperMove(InventoryMoveItemEvent event) {
    	InventoryHolder holder = event.getSource().getHolder();
        if (holder instanceof Dropper) {
    		if (!sourceIsInitiator(event)) {
    			return;
    		}
        	
        	ItemStack newResult = ExecuteAutomaticCraft((Dropper)holder, null, event);
        	if (newResult != null) {
        		event.setItem(newResult);
        	}
        }
    }

    public static void debug(String s) {
        if (debug) {
            instance.getLogger().info(s);
        }
    }

    private ItemStack getFrameItem(ItemFrame frame) {
		ItemStack stack = frame.getItem();
		
		if (stack == null) {
			debug("  Null item in frame.");
			return null;
		}
		
		if (!recipeAllowed(stack)) {
			debug("  Recipe was not allowed.");
			return null;
		}
		
		if (stack.getType().isAir()) {
			debug("  Item in frame is air");
			return null;	
		}
		
		if (stack.hasItemMeta()) {
			ItemMeta meta = stack.getItemMeta();
			if (meta instanceof Damageable) {
				((Damageable)meta).setDamage(0);
				stack = stack.clone();
				stack.setItemMeta(meta);
			}
		}
		
		return stack;
    }

    @EventHandler(ignoreCancelled=true)
    public void onDropperFire(BlockDispenseEvent event) {
    	Block dropperBlock = event.getBlock();
        if (!dropperBlock.getType().equals(Material.DROPPER)) {
        	return;
        }
        
        final Dropper dropper = ((Dropper) dropperBlock.getState());
    	ItemStack newResult = ExecuteAutomaticCraft(dropper, event.getItem(), event);
    	if (newResult != null) {
    		event.setItem(newResult);
    	}
    }
    
    public ItemStack ExecuteAutomaticCraft(Dropper dropper, ItemStack dropped, Cancellable event) {
    	debug("A dropper fired");
        if (!enabledInWorld(dropper.getWorld()))  {
        	debug("  Dropper was in a disabled world");
        	return null;
        }
        
        ItemFrame frame = getAttachedFrame(dropper.getBlock());
        if (frame == null) {
        	debug("  Dropper did not have a valid frame");
        	return null;
        }
        
        ItemStack frameItem = getFrameItem(frame);
        if (frameItem == null) {
        	debug("  Frame did not have a valid crafting item (may be empty, air, or not allowed)");
        	return null;
        }
        
        debug("  Final frame check " + (frame != null) + " " + (frame.getItem() != null) + " " + !frame.getItem().getType().equals(Material.AIR));
        ItemStack result = null;
        Recipe recipe = null;

        debug("  Attempting to craft " + frameItem);
        boolean crafted = false;
        ItemStack[] ingredients = null;
        for (Recipe r: getServer().getRecipesFor(frameItem)) {
            debug("  Checking recipe: " + r);
            if (r instanceof ShapedRecipe || r instanceof ShapelessRecipe) {
                ingredients = CraftAttempt.getIngredients(r).toArray(new ItemStack[0]);
                Inventory clone = CraftAttempt.cloneInventory(this, dropper.getInventory());
                
                if (dropped != null) {
                	clone.addItem(dropped);
                }
                
                crafted = true;
                for (ItemStack ing: ingredients) {
                	debug("  Recipe consuming " + ing.getType().name() + " in quantity " + ing.getAmount());
                    if (!CraftAttempt.removeItem(clone, ing)) {
                        debug("  Failed to find" + ing + " in inventory");

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
            return null;
        } else {
            final Recipe finalRecipe = recipe;
            getServer().getScheduler().runTaskLater(this,  new Runnable() {
                public void run () {
                    for (ItemStack ing: CraftAttempt.getIngredients(finalRecipe)) {
                        CraftAttempt.removeItem(dropper.getInventory(), ing);
                        if (filledBuckets.contains(ing.getType())) {
                            dropper.getInventory().addItem(new ItemStack(Material.BUCKET));
                        }
                    }
                }
            }, 0);
            return result;
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
            	debug("Dist: " + e.getLocation().distanceSquared(dropperLoc));
                if (e.getType().getTypeId() == itemFrameEntityId  && e.getLocation().distanceSquared(dropperLoc) < 0.4) {
                    return ((ItemFrame) e);
                }
            }
        }
        debug("Unable to find an attached itemframe");
        return null;
    }
}
