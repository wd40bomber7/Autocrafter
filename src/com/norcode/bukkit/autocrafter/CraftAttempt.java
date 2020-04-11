package com.norcode.bukkit.autocrafter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public class CraftAttempt {
    public static List<ItemStack> getIngredients(Recipe recipe) {
        List<ItemStack> ingredients = new ArrayList<ItemStack>();
        if (recipe instanceof ShapedRecipe) {
            ShapedRecipe sr = ((ShapedRecipe) recipe);
            Map<Character, ItemStack> map = sr.getIngredientMap();
            String[] shape = sr.getShape();
            ItemStack stack;
            for (String row: shape) {
                for (int i=0;i<row.length();i++) {
                    stack = map.get(row.charAt(i));
                    if (stack != null && stack.getAmount() > 0) {
                        ingredients.add(stack);
                    }
                }
            }
        } else if (recipe instanceof ShapelessRecipe) {
            for (ItemStack i: ((ShapelessRecipe) recipe).getIngredientList()) {
                if (i.getAmount() > 0) {
                    ingredients.add(i);
                }
            }
        }
        return ingredients;
    }

    public static Inventory cloneInventory(JavaPlugin plugin, Inventory inv) {
        Inventory inv2 = plugin.getServer().createInventory(null, inv.getSize());
        for (int i=0;i<inv.getSize();i++) {
            inv2.setItem(i, inv.getItem(i) == null ? null : inv.getItem(i).clone());
        }
        return inv2;
    }

    public static boolean removeItem(Inventory inv, ItemStack toRemove) {
        ItemStack s;
        int remd = 0;
        int qty = toRemove.getAmount();
        
        HashMap<Integer, Integer> map = new HashMap<Integer, Integer>();
        for (int i=0;i<inv.getSize();i++) {
            s = inv.getItem(i);
        
            if (s == null) {
        
                continue;
            }
            
            if (remd < qty) {
        
                if (s.isSimilar(toRemove)) {
                    int take = Math.min(s.getAmount(), (qty-remd));
                    map.put(i, take);
                    remd += take;
        
                    if (take == s.getAmount()) {
                        inv.setItem(i, null);
                    } else {
                        s.setAmount(s.getAmount() - take);
                    }
                }
            }
        }
        if (remd != qty) {
            return false;
        } else {
            return true;
        }
    }
}
