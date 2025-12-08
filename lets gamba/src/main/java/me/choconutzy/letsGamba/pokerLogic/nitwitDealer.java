package me.choconutzy.letsGamba.pokerLogic;

import me.choconutzy.letsGamba.LetsGambaPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.entity.Villager.Profession;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

// Tipping Mechanic
import java.math.BigDecimal;
import java.util.Arrays;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import me.choconutzy.letsGamba.Economy.EconomyProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class nitwitDealer {

    // Constants
    private static final int WOOL_REQUIRED = 6;
    private static final double FIND_RADIUS = 10.0;
    private static final double ACTIVATION_DISTANCE = 3.0;

    // State
    private UUID dealerId;
    private Location center;
    private final List<Block> tableBlocks = new ArrayList<>();
    private boolean isSetupInProgress = false;
    private boolean waitingForHost = false;
    private BukkitTask proximityTask;

    // Reference back to the parent PokerTable
    private final PokerTable pokerTable;

    public nitwitDealer(PokerTable pokerTable) {
        this.pokerTable = pokerTable;
    }

// ---------- DEALER / NITWIT LOGIC ----------

    public boolean setupDealerAndTable(Player player) {
        isSetupInProgress = true;

        // 1. Find Green Wool nearby
        List<Block> woolBlocks = findNearbyGreenWool(player.getLocation());
        if (woolBlocks.size() < WOOL_REQUIRED) {
            player.sendMessage(ChatColor.RED + "Not enough Green Wool nearby!");
            player.sendMessage(ChatColor.GRAY + "You need a 3x2 Green Wool table.");
            isSetupInProgress = false;
            return false;
        }
        this.tableBlocks.clear();
        this.tableBlocks.addAll(woolBlocks);

        Location tableCenter = getCentroid(woolBlocks);

        // 2. Find Nitwit (Green Villager)
        Villager nitwit = findNearbyNitwit(player.getLocation());
        if (nitwit == null) {
            player.sendMessage(ChatColor.RED + "No Nitwit found nearby.");
            isSetupInProgress = false;
            return false;
        }

        // 3. Determine table bounds and orientation
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;
        int tableY = woolBlocks.get(0).getY();

        for (Block b : woolBlocks) {
            minX = Math.min(minX, b.getX());
            maxX = Math.max(maxX, b.getX());
            minZ = Math.min(minZ, b.getZ());
            maxZ = Math.max(maxZ, b.getZ());
        }

        int xLength = maxX - minX + 1;
        int zLength = maxZ - minZ + 1;

        if (!((xLength == 3 && zLength == 2) || (xLength == 2 && zLength == 3))) {
            player.sendMessage(ChatColor.RED + "Invalid table shape!");
            player.sendMessage(ChatColor.GRAY + "The table must be exactly 3x2 Green Wool blocks.");
            isSetupInProgress = false;
            return false;
        }

        // 4. Find the empty side of the table (strict air check - no chairs/blocks allowed)
        World world = tableCenter.getWorld();
        Location targetLoc = findEmptyTableSide(world, minX, maxX, minZ, maxZ, tableY, xLength, zLength);

        if (targetLoc == null) {
            player.sendMessage(ChatColor.RED + "Cannot set up dealer - no valid position found!");
            player.sendMessage(ChatColor.GRAY + "Make sure one 3-block side of the table is completely clear (air only, no chairs/blocks).");
            isSetupInProgress = false;
            return false;
        }

        // 5. Initialize Game Data
        this.dealerId = nitwit.getUniqueId();
        this.center = tableCenter;

        // 6. Check if Dealer is ALREADY there
        if (nitwit.getLocation().distance(targetLoc) < 1.5) {
            player.sendMessage(ChatColor.GREEN + "Dealer is already at the table.");

            targetLoc.setDirection(tableCenter.toVector().subtract(targetLoc.toVector()));
            targetLoc.setPitch(0);
            nitwit.teleport(targetLoc);
            nitwit.setAI(false);
            nitwit.setInvulnerable(true);
            nitwit.setGravity(true);

            isSetupInProgress = false;
            startProximityCheck(player);
            return true;
        }

        // 7. Start Dealer Movement Routine
        startDealerWalkingRoutine(player, nitwit, targetLoc, tableCenter);
        return true;
    }

    /**
     * Finds the empty 3-block side of the table by checking for STRICTLY air blocks.
     * This method does NOT use findSafeGroundPosition or isPositionSafe - it handles
     * everything directly to ensure no chairs/stairs/slabs/etc. are on the dealer's side.
     *
     * @return The dealer position on the empty side, or null if none found
     */
    private Location findEmptyTableSide(World world, int minX, int maxX, int minZ, int maxZ, int tableY, int xLength, int zLength) {

        if (xLength == 3 && zLength == 2) {
            // Table is 3 wide on X-axis, 2 deep on Z-axis
            double midX = (minX + maxX) / 2.0 + 0.5;

            // Check North side
            if (isTableSideCompletelyEmpty(world, minX, maxX, minZ - 1, tableY)) {
                // Verify there's solid floor BELOW the table level
                Block groundCheck = world.getBlockAt((int) Math.floor(midX), tableY - 1, minZ - 1);
                if (groundCheck.getType().isSolid() && !isDangerousBlock(groundCheck)) {
                    // Target Y is tableY (feet at same level as the wool block, standing on the floor)
                    return new Location(world, midX, tableY, minZ - 0.5);
                }
            }

            // Check South side
            if (isTableSideCompletelyEmpty(world, minX, maxX, maxZ + 1, tableY)) {
                Block groundCheck = world.getBlockAt((int) Math.floor(midX), tableY - 1, maxZ + 1);
                if (groundCheck.getType().isSolid() && !isDangerousBlock(groundCheck)) {
                    return new Location(world, midX, tableY, maxZ + 1.5);
                }
            }

        } else if (xLength == 2 && zLength == 3) {
            // Table is 2 wide on X-axis, 3 deep on Z-axis
            double midZ = (minZ + maxZ) / 2.0 + 0.5;

            // Check West side
            if (isTableSideCompletelyEmptyVertical(world, minX - 1, minZ, maxZ, tableY)) {
                Block groundCheck = world.getBlockAt(minX - 1, tableY - 1, (int) Math.floor(midZ));
                if (groundCheck.getType().isSolid() && !isDangerousBlock(groundCheck)) {
                    return new Location(world, minX - 0.5, tableY, midZ);
                }
            }

            // Check East side
            if (isTableSideCompletelyEmptyVertical(world, maxX + 1, minZ, maxZ, tableY)) {
                Block groundCheck = world.getBlockAt(maxX + 1, tableY - 1, (int) Math.floor(midZ));
                if (groundCheck.getType().isSolid() && !isDangerousBlock(groundCheck)) {
                    return new Location(world, maxX + 1.5, tableY, midZ);
                }
            }
        }

        return null;
    }

    /**
     * Checks if a horizontal row (along X-axis) at a specific Z is completely empty.
     * Uses STRICT air check - any non-air block (including stairs, slabs, shelves) will fail.
     *
     * @param world  The world
     * @param minX   Starting X coordinate
     * @param maxX   Ending X coordinate
     * @param z      The Z coordinate to check
     * @param tableY The Y level of the table
     * @return true only if ALL blocks at feet and head level are strictly AIR
     */
    private boolean isTableSideCompletelyEmpty(World world, int minX, int maxX, int z, int tableY) {
        for (int x = minX; x <= maxX; x++) {
            // Torso/Legs level - The actual level of the table (where a chair would be)
            // This MUST be air. If there is a chair here, this returns false.
            Block feetLevel = world.getBlockAt(x, tableY, z);
            if (!isStrictlyAir(feetLevel)) {
                return false;
            }

            // Head level - One block above table
            Block headLevel = world.getBlockAt(x, tableY + 1, z);
            if (!isStrictlyAir(headLevel)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks if a vertical column (along Z-axis) at a specific X is completely empty.
     * Uses STRICT air check - any non-air block (including stairs, slabs, shelves) will fail.
     *
     * @param world  The world
     * @param x      The X coordinate to check
     * @param minZ   Starting Z coordinate
     * @param maxZ   Ending Z coordinate
     * @param tableY The Y level of the table
     * @return true only if ALL blocks at feet and head level are strictly AIR
     */
    private boolean isTableSideCompletelyEmptyVertical(World world, int x, int minZ, int maxZ, int tableY) {
        for (int z = minZ; z <= maxZ; z++) {
            // Torso/Legs level - The actual level of the table (where a chair would be)
            Block feetLevel = world.getBlockAt(x, tableY, z);
            if (!isStrictlyAir(feetLevel)) {
                return false;
            }

            // Head level - One block above table
            Block headLevel = world.getBlockAt(x, tableY + 1, z);
            if (!isStrictlyAir(headLevel)) {
                return false;
            }
        }
        return true;
    }

    private void startDealerWalkingRoutine(Player player, Villager dealer, Location targetPos, Location lookAtTarget) {
        player.sendMessage(ChatColor.GREEN + "The Nitwit is walking to the dealer's seat...");
        dealer.setAI(true);
        dealer.setTarget(null);
        if (dealer instanceof Mob) ((Mob) dealer).getPathfinder().moveTo(targetPos);

        new BukkitRunnable() {
            int ticks = 0;
            final int MAX_WAIT_TICKS = 100;

            @Override
            public void run() {
                if (dealerId == null || !dealer.isValid()) {
                    isSetupInProgress = false;
                    this.cancel();
                    return;
                }

                double dist = dealer.getLocation().distance(targetPos);

                if (dist < 1.2 || ticks >= MAX_WAIT_TICKS) {
                    Location finalLoc = targetPos.clone();
                    Vector dir = lookAtTarget.clone().toVector().subtract(finalLoc.toVector());
                    finalLoc.setDirection(dir);
                    finalLoc.setPitch(0);

                    dealer.teleport(finalLoc);
                    dealer.setAI(false);
                    dealer.setInvulnerable(true);
                    dealer.setGravity(true);

                    isSetupInProgress = false;
                    startProximityCheck(player);
                    this.cancel();
                    return;
                }

                if (ticks % 20 == 0 && ticks < MAX_WAIT_TICKS) {
                    if (dealer instanceof Mob) ((Mob) dealer).getPathfinder().moveTo(targetPos);
                }
                ticks += 5;
            }
        }.runTaskTimer(LetsGambaPlugin.getInstance(), 0L, 5L);
    }

    private void startProximityCheck(Player initiator) {
        waitingForHost = true;
        initiator.sendMessage(ChatColor.GREEN + "The Dealer has arrived! Walk to the table to open it.");

        proximityTask = new BukkitRunnable() {
            int secondsWaited = 0;
            final int TIMEOUT = 45;

            @Override
            public void run() {
                if (dealerId == null || center == null) {
                    this.cancel();
                    waitingForHost = false;
                    return;
                }

                boolean playerClose = false;
                if (initiator.isOnline() && initiator.getWorld().equals(center.getWorld())) {
                    if (initiator.getLocation().distance(center) <= ACTIVATION_DISTANCE) {
                        playerClose = true;
                    }
                }

                if (playerClose) {
                    waitingForHost = false;

                    Bukkit.broadcastMessage(ChatColor.GREEN + "[Poker] A table is open at "
                            + ChatColor.YELLOW + center.getBlockX() + ", " + center.getBlockY() + ", " + center.getBlockZ()
                            + ChatColor.GREEN + "! Use " + ChatColor.GOLD + "/poker join" + ChatColor.GREEN + " to join the table.");

                    pokerTable.activateTable(center, tableBlocks, dealerId);
                    Bukkit.getScheduler().runTaskLater(LetsGambaPlugin.getInstance(), () -> {
                        pokerTable.join(initiator);
                    }, 5L);

                    this.cancel();
                    return;
                }

                secondsWaited++;

                if (secondsWaited >= TIMEOUT) {
                    if (initiator.isOnline()) {
                        initiator.sendMessage(ChatColor.RED + "Poker request timed out. You didn't approach the table.");
                    }
                    releaseDealer();
                    center = null;
                    waitingForHost = false;
                    this.cancel();
                }
            }
        }.runTaskTimer(LetsGambaPlugin.getInstance(), 0L, 20L);
    }

    public void releaseDealer() {
        if (proximityTask != null && !proximityTask.isCancelled()) {
            proximityTask.cancel();
        }
        waitingForHost = false;

        if (dealerId == null) return;

        Entity e = Bukkit.getEntity(dealerId);
        if (e instanceof Villager) {
            Villager v = (Villager) e;
            v.setAI(true);
            v.setInvulnerable(false);
        }
        dealerId = null;
    }

    private List<Block> findNearbyGreenWool(Location center) {
        List<Block> blocks = new ArrayList<>();
        int radius = 5;
        for (int x = -radius; x <= radius; x++) {
            for (int y = -2; y <= 3; y++) {
                for (int z = -radius; z <= radius; z++) {
                    Block b = center.getBlock().getRelative(x, y, z);
                    if (b.getType() == Material.GREEN_WOOL) {
                        blocks.add(b);
                    }
                }
            }
        }
        return blocks;
    }

    private Villager findNearbyNitwit(Location loc) {
        for (Entity e : loc.getWorld().getNearbyEntities(loc, FIND_RADIUS, FIND_RADIUS, FIND_RADIUS)) {
            if (e instanceof Villager) {
                Villager v = (Villager) e;
                if (v.getProfession() == Profession.NITWIT && v.isValid()) {
                    return v;
                }
            }
        }
        return null;
    }

    private Location getCentroid(List<Block> blocks) {
        if (blocks.isEmpty()) return null;
        double totalX = 0, totalY = 0, totalZ = 0;
        for (Block b : blocks) {
            totalX += b.getX() + 0.5;
            totalY += b.getY() + 0.5;
            totalZ += b.getZ() + 0.5;
        }
        return new Location(blocks.get(0).getWorld(), totalX / blocks.size(), totalY / blocks.size(), totalZ / blocks.size());
    }

// ---------- SAFETY AND GROUND DETECTION ----------

    /**
     * STRICT air check - returns true ONLY for air blocks.
     * This is used for table-side detection to reject ALL non-air blocks.
     */
    private boolean isStrictlyAir(Block block) {
        if (block == null) {
            return false;
        }
        Material type = block.getType();
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR;
    }

    /**
     * Finds a safe ground position for the dealer, preventing clipping.
     * NOTE: This is NOT used for table-side detection anymore.
     */
    private Location findSafeGroundPosition(Location startPos) {
        if (startPos == null || startPos.getWorld() == null) {
            return null;
        }

        World world = startPos.getWorld();
        int startY = startPos.getBlockY();

        for (int y = startY; y >= startY - 5; y--) {
            Location checkLoc = new Location(world, startPos.getX(), y, startPos.getZ());
            if (isValidGroundPosition(checkLoc)) {
                return checkLoc.clone().add(0, 1, 0);
            }
        }

        for (int y = startY + 1; y <= startY + 3; y++) {
            Location checkLoc = new Location(world, startPos.getX(), y, startPos.getZ());
            if (isValidGroundPosition(checkLoc)) {
                return checkLoc.clone().add(0, 1, 0);
            }
        }

        return null;
    }

    /**
     * Checks if a location is valid ground (solid block with air above).
     * NOTE: This is NOT used for table-side detection anymore.
     */
    private boolean isValidGroundPosition(Location loc) {
        Block groundBlock = loc.getBlock();
        Block aboveGround = loc.clone().add(0, 1, 0).getBlock();
        Block twoAboveGround = loc.clone().add(0, 2, 0).getBlock();

        if (!groundBlock.getType().isSolid()) {
            return false;
        }

        if (isDangerousBlock(groundBlock)) {
            return false;
        }

        if (!isPassable(aboveGround) || !isPassable(twoAboveGround)) {
            return false;
        }

        return true;
    }

    /**
     * Comprehensive safety check for the final dealer position.
     * NOTE: This is NOT used for table-side detection anymore.
     */
    private boolean isPositionSafe(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }

        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block ground = loc.clone().add(0, -1, 0).getBlock();

        if (!ground.getType().isSolid() || isDangerousBlock(ground)) {
            return false;
        }

        if (!isPassable(feet) || !isPassable(head)) {
            return false;
        }

        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a block is passable (air, plants, etc.).
     * NOTE: For table-side detection, use isStrictlyAir() instead.
     */
    private boolean isPassable(Block block) {
        Material type = block.getType();

        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
            return true;
        }

        if (type == Material.LAVA) {
            return false;
        }

        if (!type.isSolid()) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a block is dangerous for the dealer to stand on.
     */
    private boolean isDangerousBlock(Block block) {
        Material type = block.getType();

        return type == Material.LAVA ||
                type == Material.FIRE ||
                type == Material.SOUL_FIRE ||
                type == Material.MAGMA_BLOCK ||
                type == Material.CACTUS ||
                type == Material.SWEET_BERRY_BUSH ||
                type == Material.WITHER_ROSE ||
                type.name().contains("PRESSURE_PLATE") ||
                type.name().contains("TRIPWIRE");
    }

    // ---------- TIPPING LOGIC ----------

    public void openTippingMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.BLACK + "Tip the Dealer?");

        inv.setItem(2, createGuiItem(Material.IRON_NUGGET, ChatColor.YELLOW + "$10", "Click to tip 10"));
        inv.setItem(3, createGuiItem(Material.GOLD_NUGGET, ChatColor.GOLD + "$20", "Click to tip 20"));
        inv.setItem(4, createGuiItem(Material.EMERALD, ChatColor.GREEN + "$30", "Click to tip 30"));
        inv.setItem(6, createGuiItem(Material.NAME_TAG, ChatColor.LIGHT_PURPLE + "Custom Amount", "Click to type in chat"));

        player.openInventory(inv);
    }

    private ItemStack createGuiItem(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(Arrays.asList(lore));
            item.setItemMeta(meta);
        }
        return item;
    }

    public void processTip(Player player, double amount) {
        if (amount <= 0) return;

        EconomyProvider eco = LetsGambaPlugin.getInstance().getEconomyProvider();
        if (eco == null || !eco.isEnabled()) {
            player.sendMessage(ChatColor.RED + "Economy is disabled.");
            return;
        }

        BigDecimal tipAmount = BigDecimal.valueOf(amount);

        if (!eco.hasEnough(player.getUniqueId(), tipAmount)) {
            player.sendMessage(ChatColor.RED + "You don't have enough money to tip $" + amount + "!");
            return;
        }

        eco.subtract(player.getUniqueId(), tipAmount);

        // Confirmation message
        player.sendMessage(ChatColor.GREEN + "You tipped the dealer " + ChatColor.GOLD + "$" + amount + ChatColor.GREEN + "!");

        // Physical Reaction
        playDealerReaction();
    }

    private void playDealerReaction() {
        if (dealerId == null) return;
        Entity e = Bukkit.getEntity(dealerId);

        if (e instanceof Villager v) {
            Location loc = v.getLocation();

            // 🎉 ENHANCED HEARTS EFFECT - Burst of joy!
            // Spawn multiple waves of hearts with upward motion
            for (int i = 0; i < 5; i++) {
                v.getWorld().spawnParticle(
                        Particle.HEART,
                        loc.clone().add(0, 0.8, 0), // Slightly above villager's head
                        8, // More particles per burst
                        0.4, 0.2, 0.4, // Spread radius (X, Y, Z)
                        0.1, // Extra velocity (makes them float up)
                        null, // No data needed for hearts
                        true // Show to all players in visible range
                );

                // Add complimentary hearts that float higher
                v.getWorld().spawnParticle(
                        Particle.HEART,
                        loc.clone().add(0, 1.2, 0),
                        5,
                        0.3, 0.3, 0.3,
                        0.15,
                        null,
                        true
                );
            }

            // ✨ Optional: Add some sparkle effect around the hearts
            v.getWorld().spawnParticle(
                    Particle.END_ROD,
                    loc.clone().add(0, 1.0, 0),
                    10,
                    0.5, 0.5, 0.5,
                    0.05,
                    null,
                    true
            );

            // Sound: Happy Villager + XP pickup sound
            v.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            v.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

            // Movement: Small jump of joy
            v.setVelocity(new Vector(0, 0.3, 0));
        }
    }

    // ---------- GETTERS ----------

    public Location getCenter() {
        return center;
    }

    public UUID getDealerId() {
        return dealerId;
    }

    public List<Block> getTableBlocks() {
        return tableBlocks;
    }

    public boolean isSetupInProgress() {
        return isSetupInProgress;
    }

    public boolean isWaitingForHost() {
        return waitingForHost;
    }

    public boolean isLocationOverlapping(Location otherCenter) {
        if (this.center == null || otherCenter == null) return false;
        if (!this.center.getWorld().equals(otherCenter.getWorld())) return false;
        return this.center.distance(otherCenter) < 2.0;
    }
}
