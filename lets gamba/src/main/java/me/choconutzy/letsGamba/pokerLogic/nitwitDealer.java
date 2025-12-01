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

        // 3. Calculate dealer positions on the 3-block sides only
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE, maxZ = Integer.MIN_VALUE;

        for (Block b : woolBlocks) {
            minX = Math.min(minX, b.getX());
            maxX = Math.max(maxX, b.getX());
            minZ = Math.min(minZ, b.getZ());
            maxZ = Math.max(maxZ, b.getZ());
        }

        double midX = (minX + maxX) / 2.0 + 0.5;
        double midZ = (minZ + maxZ) / 2.0 + 0.5;
        double tableY = tableCenter.getY();
        World world = tableCenter.getWorld();

        // Determine table orientation and create candidates for 3-block sides only
        List<Location> candidatePositions = new ArrayList<>();

        int xLength = maxX - minX + 1; // Number of blocks along X
        int zLength = maxZ - minZ + 1; // Number of blocks along Z

        if (xLength == 3 && zLength == 2) {
            // Table is 3 blocks along X-axis, 2 blocks along Z-axis
            // Dealer can only stand on the Z sides (North/South - the 3-block sides)
            candidatePositions.add(new Location(world, midX, tableY, minZ - 0.5)); // North side
            candidatePositions.add(new Location(world, midX, tableY, maxZ + 1.5)); // South side
        } else if (xLength == 2 && zLength == 3) {
            // Table is 2 blocks along X-axis, 3 blocks along Z-axis
            // Dealer can only stand on the X sides (West/East - the 3-block sides)
            candidatePositions.add(new Location(world, minX - 0.5, tableY, midZ)); // West side
            candidatePositions.add(new Location(world, maxX + 1.5, tableY, midZ)); // East side
        } else {
            // Invalid table shape
            player.sendMessage(ChatColor.RED + "Invalid table shape!");
            player.sendMessage(ChatColor.GRAY + "The table must be exactly 3x2 Green Wool blocks.");
            isSetupInProgress = false;
            return false;
        }

        // 4. Evaluate positions and find the best one
        Location targetLoc = findBestDealerPosition(candidatePositions, tableCenter);

        if (targetLoc == null) {
            player.sendMessage(ChatColor.RED + "Cannot set up dealer - no valid ground found on either side of the table!");
            player.sendMessage(ChatColor.GRAY + "Make sure there's solid ground next to the 3-block sides and no stairs blocking.");
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
     * Evaluates all candidate positions and returns the best one.
     * Evaluation is based solely on the table surroundings.
     * Priority: 1) Valid ground, 2) No stairs, 3) Most open space around position
     *
     * @param candidates    List of potential positions around the table (3-block sides only)
     * @param tableCenter   The center of the table (for facing direction)
     * @return The best valid location, or null if none found
     */
    private Location findBestDealerPosition(List<Location> candidates, Location tableCenter) {
        List<DealerPositionCandidate> validCandidates = new ArrayList<>();

        for (Location candidate : candidates) {
            Location groundPos = findSafeGroundPosition(candidate);

            if (groundPos == null) {
                continue;
            }

            if (!isPositionSafe(groundPos)) {
                continue;
            }

            boolean hasStairs = hasNeighboringStairs(groundPos, tableCenter);
            int openScore = calculateOpenSpaceScore(groundPos);

            validCandidates.add(new DealerPositionCandidate(groundPos, hasStairs, openScore));
        }

        if (validCandidates.isEmpty()) {
            return null;
        }

        // Sort candidates: no stairs first, then by open space score (higher is better)
        validCandidates.sort((a, b) -> {
            // First priority: positions without stairs
            if (a.hasStairs != b.hasStairs) {
                return a.hasStairs ? 1 : -1;
            }
            // Second priority: more open space (higher score is better)
            return Integer.compare(b.openScore, a.openScore);
        });

        return validCandidates.get(0).location;
    }

    /**
     * Calculates an "openness" score for a position based on surrounding blocks.
     * Higher score means more open/accessible space around the position.
     *
     * @param loc The location to evaluate
     * @return An integer score representing how open the area is
     */
    private int calculateOpenSpaceScore(Location loc) {
        int score = 0;
        Block centerBlock = loc.getBlock();

        // Check the immediate ring around the position (8 blocks horizontally)
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                if (x == 0 && z == 0) {
                    continue;
                }

                Block groundLevel = centerBlock.getRelative(x, -1, z);
                Block feetLevel = centerBlock.getRelative(x, 0, z);
                Block headLevel = centerBlock.getRelative(x, 1, z);

                // Award points for solid ground
                if (groundLevel.getType().isSolid() && !isDangerousBlock(groundLevel)) {
                    score += 2;
                }

                // Award points for passable space at feet and head level
                if (isPassable(feetLevel)) {
                    score += 1;
                }
                if (isPassable(headLevel)) {
                    score += 1;
                }

                // Penalize obstructions
                if (feetLevel.getType().isSolid()) {
                    score -= 2;
                }
                if (headLevel.getType().isSolid()) {
                    score -= 2;
                }
            }
        }

        // Bonus for the position itself being well-grounded
        Block directGround = centerBlock.getRelative(0, -1, 0);
        if (directGround.getType().isSolid() && !isDangerousBlock(directGround)) {
            score += 3;
        }

        return score;
    }

    /**
     * Helper class to store candidate position data for comparison.
     */
    private static class DealerPositionCandidate {
        final Location location;
        final boolean hasStairs;
        final int openScore;

        DealerPositionCandidate(Location location, boolean hasStairs, int openScore) {
            this.location = location;
            this.hasStairs = hasStairs;
            this.openScore = openScore;
        }
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

    private boolean hasNeighboringStairs(Location dealerLoc, Location faceTarget) {
        Vector direction = faceTarget.toVector().subtract(dealerLoc.toVector()).setY(0).normalize();
        Vector right = direction.clone().crossProduct(new Vector(0, 1, 0)).normalize();
        Vector left = right.clone().multiply(-1);

        Block rightBlock = dealerLoc.clone().add(right).getBlock();
        Block leftBlock = dealerLoc.clone().add(left).getBlock();

        return isStair(rightBlock) || isStair(leftBlock);
    }

    private boolean isStair(Block b) {
        return b != null && b.getType().name().endsWith("_STAIRS");
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

                    // Synchronize pokerTable and nitwitDealer (deal!)
                    pokerTable.activateTable(center, tableBlocks, dealerId);
                    // Call back to PokerTable to handle the join
                    Bukkit.getScheduler().runTaskLater(LetsGambaPlugin.getInstance(), () -> {
                        pokerTable.join(initiator);
                    }, 5L); // small delay ensures the table has finalized

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
     * Finds a safe ground position for the dealer, preventing clipping.
     * Searches up to 5 blocks down and 3 blocks up from the initial position.
     *
     * @param startPos The starting position to search from
     * @return A safe ground location, or null if none found
     */
    private Location findSafeGroundPosition(Location startPos) {
        if (startPos == null || startPos.getWorld() == null) {
            return null;
        }

        World world = startPos.getWorld();
        int startY = startPos.getBlockY();

        // Search downward first (up to 5 blocks down)
        for (int y = startY; y >= startY - 5; y--) {
            Location checkLoc = new Location(world, startPos.getX(), y, startPos.getZ());
            if (isValidGroundPosition(checkLoc)) {
                // Position entity on TOP of the solid block
                return checkLoc.clone().add(0, 1, 0);
            }
        }

        // If downward search failed, try upward (up to 3 blocks up)
        for (int y = startY + 1; y <= startY + 3; y++) {
            Location checkLoc = new Location(world, startPos.getX(), y, startPos.getZ());
            if (isValidGroundPosition(checkLoc)) {
                return checkLoc.clone().add(0, 1, 0);
            }
        }

        return null; // No valid ground found
    }

    /**
     * Checks if a location is valid ground (solid block with air above).
     *
     * @param loc The location to check (should be the potential ground block)
     * @return true if this is valid ground for the dealer
     */
    private boolean isValidGroundPosition(Location loc) {
        Block groundBlock = loc.getBlock();
        Block aboveGround = loc.clone().add(0, 1, 0).getBlock();
        Block twoAboveGround = loc.clone().add(0, 2, 0).getBlock();

        // Ground must be solid
        if (!groundBlock.getType().isSolid()) {
            return false;
        }

        // Can't be a dangerous block
        if (isDangerousBlock(groundBlock)) {
            return false;
        }

        // Must have at least 2 blocks of air above for the villager
        if (!isPassable(aboveGround) || !isPassable(twoAboveGround)) {
            return false;
        }

        return true;
    }

    /**
     * Comprehensive safety check for the final dealer position.
     *
     * @param loc The location to validate
     * @return true if the position is safe for the dealer
     */
    private boolean isPositionSafe(Location loc) {
        if (loc == null || loc.getWorld() == null) {
            return false;
        }

        Block feet = loc.getBlock();
        Block head = loc.clone().add(0, 1, 0).getBlock();
        Block ground = loc.clone().add(0, -1, 0).getBlock();

        // Must have solid ground below
        if (!ground.getType().isSolid() || isDangerousBlock(ground)) {
            return false;
        }

        // Must have passable space for feet and head
        if (!isPassable(feet) || !isPassable(head)) {
            return false;
        }

        // Check for suffocation risks
        if (feet.getType().isSolid() || head.getType().isSolid()) {
            return false;
        }

        return true;
    }

    /**
     * Checks if a block is passable (air, plants, etc.).
     *
     * @param block The block to check
     * @return true if an entity can pass through it
     */
    private boolean isPassable(Block block) {
        Material type = block.getType();

        // Air is always passable
        if (type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR) {
            return true;
        }

        // Dangerous fluids are not passable
        if (type == Material.LAVA) {
            return false;
        }

        // Non-solid blocks are generally passable
        if (!type.isSolid()) {
            return true;
        }

        return false;
    }

    /**
     * Checks if a block is dangerous for the dealer to stand on.
     *
     * @param block The block to check
     * @return true if the block is dangerous
     */
    private boolean isDangerousBlock(Block block) {
        Material type = block.getType();

        // Dangerous blocks that should never be used as ground
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
        Inventory inv = Bukkit.createInventory(null, 9, ChatColor.GREEN + "Tip the Dealer?");

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

            // Particles: Happy Green/Hearts
            v.getWorld().spawnParticle(Particle.HEART, loc.add(0, 0.5, 0), 2, 0.3, 0.3, 0.3);

            // Sound: Happy Villager + XP pickup sound
            v.getWorld().playSound(loc, Sound.ENTITY_VILLAGER_YES, 1.0f, 1.0f);
            v.getWorld().playSound(loc, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.5f);

            // Movement: Small jump of joy
            v.setVelocity(new Vector(0, 0.3, 0));

            // Look at the player who tipped? (Optional, requires passing player loc)
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
